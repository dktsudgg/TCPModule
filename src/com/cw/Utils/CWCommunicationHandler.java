package com.cw.Utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.cw.component.CWCommunicationServer;
import com.cw.node.CWNode;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.stream.ChunkedStream;
import io.netty.util.ReferenceCountUtil;

public class CWCommunicationHandler extends ChannelInboundHandlerAdapter { // (1)
	
	private static Object mutex = new Object();	// 스레드 동기화에 사용하기 위한 뮤텍스 객체..

	private ConcurrentHashMap<String, IpPort> sessions;
	private ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues;
	
	private String myip;
	private int myport;
	CWCommunicationCallback callback;
	
	public CWCommunicationHandler(String myip, int myport
			, ConcurrentHashMap<String, IpPort> sessions, ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues, CWCommunicationCallback callback) {
		this.myip = myip;
		this.myport = myport;
		this.sessions = sessions;
		this.channelWriteQueues = channelWriteQueues;
		this.callback = callback;
	}

	/* (non-Javadoc)
	 * @see io.netty.channel.ChannelInboundHandlerAdapter#channelActive(io.netty.channel.ChannelHandlerContext)
	 */
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);

		Utils.log(this.myip, this.myport
				, "채널 액티브:: " + ctx.channel().localAddress().toString() + " - " + ctx.channel().remoteAddress().toString() + "\n");
		
		this.channelWriteQueues.remove(ctx.channel().id().toString());
		this.channelWriteQueues.put(ctx.channel().id().toString(), new LinkedBlockingDeque());
	}

	/* (non-Javadoc)
	 * @see io.netty.channel.ChannelInboundHandlerAdapter#channelInactive(io.netty.channel.ChannelHandlerContext)
	 */
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);

		closeInfo(ctx);
		String msg = "채널 끊겼음:: " + ctx.channel().localAddress().toString() + " - " + ctx.channel().remoteAddress().toString() + "\n";
		Utils.log(this.myip, this.myport, msg);
		
		this.channelWriteQueues.remove(ctx.channel().id().toString());
		
		if(this.callback != null) {
			this.callback.connectionFailure(msg);
		}
		
		Enumeration<IpPort> iterator = this.sessions.elements();
		while(iterator.hasMoreElements()) {
			IpPort ipport = iterator.nextElement();
			if(ipport.getChannel() != null && ipport.getChannel().equals(ctx.channel())) {
				ipport.setChannel(null);
				break;
			}
		}
		
	}
	
	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		Channel channel = ctx.channel();
		LinkedBlockingDeque targetChannelQueue = channelWriteQueues.get(channel.id().toString());
		
//		while(channel.isWritable() && targetChannelQueue.isEmpty()==false ) {
//			CWConnProtocol queueData =  (CWConnProtocol) targetChannelQueue.poll();
//			channel.writeAndFlush(queueData);
//		}
		CWNode.execSendWriteQueue(channel, targetChannelQueue, this.callback);
		
		super.channelWritabilityChanged(ctx);
	}

	public void closeInfo(ChannelHandlerContext ctx){
		ctx.close();//ctx.disconnect();
	}
	
	private boolean checkWritabilityUntilPossible(Channel channel) {
		return channel.isWritable();
	}
	
	private void messageLog(CWConnProtocol msg) {
		try {
			String receivedPacketLog = "\n" + "<<<----- RECEIVED PACKET ----->>>\n";
			receivedPacketLog += "Header : " + msg.getHeader();
			receivedPacketLog += "\n" + "Protocol : " + msg.getProtocol();
			receivedPacketLog += "\n" + "Data : " + new String(msg.getData(), 0, msg.getData().length, "UTF-8");
			receivedPacketLog += "\n" + "<<<--------------------------->>>\n";
			Utils.log(this.myip, this.myport, receivedPacketLog);
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		
		CWConnProtocol receivedm = (CWConnProtocol) msg;

//		//	어떤 메세지왔는지 로그..
//		messageLog(receivedm);

		try{
			
			ChannelFuture f;
			CWConnProtocol temp;
			
			switch(receivedm.getProtocol()){
			
			case SEND_HELLO:	// HELLO 프로토콜 요청 받을 경우.. 1. HELLO 전송, 2. 생성된 연결을 저장..			
				// 1
				JSONObject jo = new JSONObject();
				jo.put("msg", "hello too");
				jo.put("ip", this.myip);
				jo.put("port", this.myport);
				
				CWConnProtocol helloToo = new CWConnProtocol(
					ProtocolVal.ACK_HELLO
					, jo.toString().getBytes("UTF-8")
				);
				
				CWNode.sendData(ctx.channel(), helloToo, callback, channelWriteQueues);
				ReferenceCountUtil.release(helloToo);
				
				// 이후 로직은 RECV_HELLO와 같으므로 break 걸지 않음..
				break;
				
			case ACK_HELLO:	// HELLO 프로토콜 응답 받을경우.. 생성된 연결을 저장..	
			case ACK_HELLO_END:
					
				// 2
				String ip = new JSONObject( new String(receivedm.getData(), 0, receivedm.getData().length, "UTF-8") ).getString("ip");
				int port = new JSONObject( new String(receivedm.getData(), 0, receivedm.getData().length, "UTF-8") ).getInt("port");
				
				synchronized(mutex){
					if(
						this.sessions.get(ip+":"+port) != null
						&& this.sessions.get(ip+":"+port).getChannel() != null
						&& this.sessions.get(ip+":"+port).getChannel().isWritable()
						&& this.sessions.get(ip+":"+port).getChannel().isActive()
					) {
						Utils.log(this.myip, this.myport, ip+":"+port+" is already in.. so diconnect this channel.. ");
						Utils.log(this.myip, this.myport
								, ctx.channel().localAddress().toString() + " - " + ctx.channel().remoteAddress().toString());
						closeInfo(ctx);
					}
					else {
						IpPort newIpPort = new IpPort(ip, port);
						newIpPort.setChannel( ctx.channel() );
						
						this.sessions.put(ip+":"+port, newIpPort);
						
						Utils.log(this.myip, this.myport, ip+":"+port+" Node inserted !!");
						// 참고:: "로컬 어드레스:포트"가 노드 서버의 IP:PORT라면 외부로부터 원격접속을 받은것임..
						Utils.log(this.myip, this.myport
							, newIpPort.getChannel().localAddress().toString() + " - " + newIpPort.getChannel().remoteAddress().toString());
						
						if( receivedm.getProtocol().equals(ProtocolVal.ACK_HELLO) ) {
							JSONObject ack_hello_end = new JSONObject();
							ack_hello_end.put("ip", this.myip);
							ack_hello_end.put("port", this.myport);
					    	CWConnProtocol ack_hello_end_data = new CWConnProtocol(
					    		ProtocolVal.ACK_HELLO_END
								, ack_hello_end.toString().getBytes("UTF-8")
							);
					    	
					    	CWNode.sendData(ctx.channel(), ack_hello_end_data, callback, channelWriteQueues);
					    	ReferenceCountUtil.release(ack_hello_end_data);
						}
						
					}
				}
				
				break;	////
				
			case SEND_PINGPONG:
				// pong
				JSONObject jsonPong = new JSONObject();
				jsonPong.put("msg", "pong");
				
				CWConnProtocol pong = new CWConnProtocol(
					ProtocolVal.ACK_PINGPONG
					, jsonPong.toString().getBytes("UTF-8")
				);
				
				CWNode.sendData(ctx.channel(), pong, callback, channelWriteQueues);
				ReferenceCountUtil.release(pong);
				break;	////
				
			case ACK_PINGPONG:
				Utils.log(this.myip, this.myport, new String(receivedm.getData(), 0, receivedm.getData().length, "UTF-8"));
				break;	////
				
			case TEST:	// TEST 프로토콜..
				// 1. 다른 노드들에게 데이터 전파..(다른노드로부터 이 패킷을 전달받은 것이라면, 그 노드에게는 전파하지 않음.)
				// 2. 나한테 이 패킷을 전달한 애 한테 ACK 전송.. 
				
				// TEST메세지 표준출력..
				messageLog(receivedm);
				
				JSONObject jo_test = new JSONObject(new String(receivedm.getData(), 0, receivedm.getData().length, "UTF-8"));
				
				// 1
				Enumeration<IpPort> iterator = this.sessions.elements();
				while(iterator.hasMoreElements()) {	// 연결된 노드들에게 전파..
					IpPort ipport = iterator.nextElement();
					
					// 이 패킷을 내게 전달한 노드에게는 전파하지 않는 조건문.. 노드 구성 그래프 형태에서 사이클이 존재하는 경우에는 경우에 따라 무한대로 전파될 수 있음을 주의.. 이미 받은 메세지인지 확인해서 전파 안하는 로직 추가해야됨.. !!!!!!!!!!!!
					if(
						ipport.getChannel() != null 
						&&
						false == ipport.getChannel().id().equals( ctx.channel().id() )
					) 
					{
						CWConnProtocol returnTestData = new CWConnProtocol(
							ProtocolVal.TEST
							, jo_test.toString().getBytes("UTF-8")
						);
						
						CWNode.sendData(ipport.getChannel(), returnTestData, callback, channelWriteQueues);
						ReferenceCountUtil.release(returnTestData);
					}
					
				}
				
				// 2
				CWConnProtocol returnTestACK = new CWConnProtocol(
					ProtocolVal.TEST_ACK
					, jo_test.toString().getBytes("UTF-8")
				);
				
				CWNode.sendData(ctx.channel(), returnTestACK, callback, channelWriteQueues);
				ReferenceCountUtil.release(returnTestACK);
				
				break;	////
			case TEST_ACK:
				// TEST_ACK메세지 표준출력..
				messageLog(receivedm);
					
				break;
				
			default :
				// 이 핸들러에서 처리로직이 작성되지 않은 프로토콜임.. 다음 핸들러에게 전달되도록 fire시킴..
				ctx.fireChannelRead(msg);
				Utils.log(this.myip, this.myport, "다음 핸들러로 패킷 넘김 : "+receivedm.getProtocol());
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
	        ReferenceCountUtil.release(msg); 
	    }
		
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // (4)
		// Close the connection when an exception is raised.
		cause.printStackTrace();
		
		System.out.println("CWCommunicationHandler exceptionCaught..");
		System.out.println(cause.getMessage());
		
		ctx.close();
	}
}