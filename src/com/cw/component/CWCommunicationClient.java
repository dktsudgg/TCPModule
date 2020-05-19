package com.cw.component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import com.cw.Utils.*;
import io.netty.handler.timeout.IdleStateHandler;
import org.json.JSONObject;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import com.cw.codec.CWConnProtocolDecoder;
import com.cw.codec.CWConnProtocolEncoder;
import com.cw.node.CWNode;

public class CWCommunicationClient implements Runnable{
    
    private ConcurrentHashMap<String, IpPort> sessions;
    private ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues;
    
    private String myip;
    private int myport;
    
    private String otherNodeHost;
    private int otherNodePort;
    
    private CWCommunicationCallback callback;
    
    public CWCommunicationClient(String myip, int myport
    		, ConcurrentHashMap<String, IpPort> sessions, ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues, String otherNodeHost, int otherNodePort) {
    	this.myip = myip;
    	this.myport = myport;
    	this.sessions = sessions;
    	this.channelWriteQueues = channelWriteQueues;
    	this.otherNodeHost = otherNodeHost;
    	this.otherNodePort = otherNodePort;
    }
    
    public ConcurrentHashMap<String, IpPort> getSessions(){
    	return this.sessions;
    }
    
    public ConcurrentHashMap<String, LinkedBlockingDeque> getChannelWriteQueues(){
    	return this.channelWriteQueues;
    }
    
    public void setCallback(CWCommunicationCallback callback) {
    	this.callback = callback;
    }
    
    private void sendData(CWConnProtocol data) {
    	
    	IpPort targetIpPort = this.sessions.get(otherNodeHost+":"+otherNodePort);
		Channel channel = targetIpPort != null ? targetIpPort.getChannel() : null;
    	
		CWNode.sendData(channel, data, this.callback, this.channelWriteQueues);
    	
    }

	@Override
	public void run() {
		String host = otherNodeHost;
        int port = otherNodePort;

        while(true) {
        	
        	if(this.sessions.get(host+":"+port) == null || this.sessions.get(host+":"+port).getChannel() == null) {
        		
        		int DEFAULT_WORKERGROUP_THREADS = 16;
	        	EventLoopGroup workerGroup = new NioEventLoopGroup(DEFAULT_WORKERGROUP_THREADS);
	        	
		        try {
		            Bootstrap b = new Bootstrap(); // (1)
		            b.group(workerGroup); // (2)
		            b.channel(NioSocketChannel.class); // (3)
		            b.option(ChannelOption.SO_KEEPALIVE, true); // (4)
		            b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3*1000);	// 커넥션 타임아웃 설정.. 3초
		            b.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024));
		            b.handler(new ChannelInitializer<SocketChannel>() {
		                @Override
		                public void initChannel(SocketChannel ch) throws Exception {
							// ch.pipeline().addLast(new ReadTimeoutHandler(10));	// 이 핸들러 사용하면 Read 타임아웃 시 바로 연결끊음.. 10초..
							// 12초동안 수신하는 메세지가 없는지 감지, 5초동안 송신하는 메세지가 없는지 감지
							ch.pipeline().addLast(new IdleStateHandler(12, 5, 0));
							ch.pipeline().addLast(new IdleProofHandler( getChannelWriteQueues(), callback )); // 일정 시간 송/수신 메세지가 없을 시에 처리로직 핸들러..

		                	ch.pipeline().addLast(new CWConnProtocolEncoder());
		                	ch.pipeline().addLast(new CWConnProtocolDecoder());
		                	ch.pipeline().addLast(new CWCommunicationHandler(myip, myport, getSessions(), getChannelWriteQueues(), callback ));
		
		                }
		            });
		
		            // Start the client.
		            ChannelFuture channelFuture = b.connect(host, port).sync(); // (5)
		
		            channelFuture.addListener(new ChannelFutureListener() {
						
						@Override
						public void operationComplete(ChannelFuture arg0) throws Exception {
							Channel channel = channelFuture.channel();
				            
				            // HELLO 패킷 전송.. 
				            JSONObject jo = new JSONObject();
				            jo.put("msg", "hello");
				            jo.put("ip", myip);
				            jo.put("port", myport);
				            
				        	CWConnProtocol hello_data = new CWConnProtocol(
				        		ProtocolVal.SEND_HELLO
								, jo.toString().getBytes("UTF-8")
							);
				        	
				        	CWNode.sendData(channel, hello_data, callback, channelWriteQueues);
				        	//
						}
					});
		            
		
		            // Wait until the connection is closed.
		        	channelFuture.channel().closeFuture().sync();
		        } 
		        catch(Exception e) {
		        	
		        }
		        finally {
		            workerGroup.shutdownGracefully();
//		            System.out.println("client connection end..");
		        }
	        
        	}
        	else {
//        		Utils.log(this.myip, this.myport, "...");
        		
        		// 헬스체크기능은 서버소스쪽에 ReadTimeoutHandler를 등록 안하면 필요없음.... 서버쪽에 KEEP_ALIVE설정해두면 충분하기때문임. 이걸로도 연결 끊김 감지 가능.
//        		Utils.log(this.myip, this.myport, "health check.. pingpong");
//        		try {
//	        		JSONObject jo = new JSONObject();
//		            jo.put("msg", "ping");
//	        		CWConnProtocol ping = new CWConnProtocol(
//			        	ProtocolVal.SEND_PINGPONG
//						, jo.toString().getBytes("UTF-8")
//					);
//	        		this.sessions.get(host+":"+port).getChannel().writeAndFlush(ping);
//	        	} catch (UnsupportedEncodingException e) {
//					e.printStackTrace();
//				}
        		
        	}
	        
	        try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        
        }
	}
	
	public static void main(String[] args) throws Exception {
		String host="localhost";
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8898;    // My Port !!!!!!!!!!!!!!!!!!!!!!
        }
		CWCommunicationClient cli = new CWCommunicationClient(host, port
				, new ConcurrentHashMap<String, IpPort>(), new ConcurrentHashMap<String, LinkedBlockingDeque>(), "localhost", 8890);
		
		Thread th = new Thread(cli);
		th.start();
		
		Thread.sleep(3000);
		
		JSONObject jo = new JSONObject();
        jo.put("msg", "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test...");
    	CWConnProtocol test_data = new CWConnProtocol(
    		ProtocolVal.TEST
			, jo.toString().getBytes("UTF-8")
		);
    	
    	int count=0;
    	while(count<10000) {
    		count = count+1;
    		cli.sendData(test_data);
    		
//    		Thread.sleep(1);
    	}
    	
    	th.join();
		
    }
    
}
