package com.cw.node;

import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import com.cw.Utils.CWCommunicationCallback;
import com.cw.Utils.CWConnProtocol;
import com.cw.Utils.IpPort;
import com.cw.component.CWCommunicationClient;
import com.cw.component.CWCommunicationServer;

import io.netty.channel.Channel;

public class CWNode {
	
//	public static Integer testIntVal = 0;	// for test..
//	public static boolean testRun = true;
	
	// 클라이언트가 아닌 서버 노드들의 연결 정보를 담는 변수. HELLO 프로토콜을 거친 세션만 이 변수에 기록되도록 개발함..
	private ConcurrentHashMap<String, IpPort> sessions;
	
	// 각 연결된 세션별 데이터 전송 큐를 제공하기 위한 변수.
	// 데이터 전송 작업은 [ send메소드 -> 큐 -> Netty Write 큐 ] 흐름을 가지도록 개발함..
	// Netty 코어단에서도 read/write 이벤트큐를 제공하지만 추가적으로 write큐를 덧댄 이유는 Netty write큐가 꽉찼을 경우, 전송해야할 데이터가 임시적으로 거처할 공간이 필요했기 때문..(채널이 isWritable==false 인 경우)
	private ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues;
	
	private String myip;
	private int myport;
	
	private CWCommunicationServer server;
	private Vector<CWCommunicationClient> clientList;
	
	private CWCommunicationCallback clientCallback;
	
	private Thread node_server;
	private Vector<Thread> node_client_list;
	
	public CWNode(String myhost, int myport, CWCommunicationCallback clientCallback) {
		this.myip = myhost;
		this.myport = myport;
		
		this.clientCallback = clientCallback;
		
		this.sessions = new ConcurrentHashMap<String, IpPort>();
		this.channelWriteQueues = new ConcurrentHashMap<String, LinkedBlockingDeque>();
		
		this.server = new CWCommunicationServer(this.myip, this.myport, this.sessions, this.channelWriteQueues, this.clientCallback);
		
		this.clientList = new Vector<CWCommunicationClient>();
		this.node_client_list = new Vector<Thread>();
	}
	
	public void addClient(String otherNodeHost, int otherNodePort) {
		CWCommunicationClient newCli = new CWCommunicationClient(this.myip, this.myport, this.sessions, this.channelWriteQueues, otherNodeHost, otherNodePort);
		
		newCli.setCallback(this.clientCallback);
		this.clientList.add( new CWCommunicationClient(this.myip, this.myport, this.sessions, this.channelWriteQueues, otherNodeHost, otherNodePort) );
		
		Thread newCliWorker = new Thread(newCli);
		newCliWorker.start();
		
		this.node_client_list.add(newCliWorker);
	}
	
	public void sendToConnectedNode(String otherNodeHost, int otherNodePort, CWConnProtocol data) {
		
		IpPort targetIpPort = this.sessions.get(otherNodeHost+":"+otherNodePort);
		Channel channel = targetIpPort != null ? targetIpPort.getChannel() : null;
		
		if(targetIpPort != null || channel != null)
			sendData(channel, data, this.clientCallback, this.channelWriteQueues);
		else {
			if(this.clientCallback != null) {
				String msg = "Channel is null.. maybe not connected yet";
				clientCallback.sendDataFailure(msg, data);
			}
		}
	}
	public static void sendData(Channel channel, CWConnProtocol data, CWCommunicationCallback clientCallback, ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues) {
    	
		if(channel != null) {
			
			LinkedBlockingDeque targetChannelQueue = channelWriteQueues.get(channel.id().toString());
			if(targetChannelQueue != null) {
				
				targetChannelQueue.add(data);
								
				execSendWriteQueue(channel, targetChannelQueue, clientCallback);
				
			}
			else {
				if(clientCallback != null) {
					String msg = "targetChannelQueue is null.. targetChannelQueue is removed by GC because of memory leak Or maybe not connected yet";
					clientCallback.sendDataFailure(msg, data);
				}
			}
			
    	}
		else {
			if(clientCallback != null) {
				String msg = "Channel is null.. maybe not connected yet";
				clientCallback.sendDataFailure(msg, data);
			}
		}
    	
    	
    }
	
	public static void execSendWriteQueue(Channel channel, LinkedBlockingDeque targetChannelQueue, CWCommunicationCallback clientCallback) {
		boolean wrote = false;
		CWConnProtocol queueData = null;
		
		int tick = 0;

//		ChannelFuture f;
		
		while(channel.isWritable() && targetChannelQueue != null && targetChannelQueue.isEmpty()==false ) {
			tick++;
			
//			System.out.println("큐 사이즈:: " + targetChannelQueue.size());
			queueData = (CWConnProtocol) targetChannelQueue.poll();
			
			try {
				
				if(queueData != null) {
					channel.write(queueData);
//					f = channel.write(queueData);
//					f.addListener(new ChannelFutureListener() {
//						@Override
//						public void operationComplete(ChannelFuture future) throws Exception {
//							if(clientCallback != null) {
//								clientCallback.sendDataSuccess(future);
//							}
//						}
//
//					});

					wrote = true;
				}
				
				if(wrote == true && tick % 10 == 0) {
					channel.flush();
					tick = 0;
				}
				
			} catch(Exception e) {
				if(clientCallback != null) {
					String msg = "Channel is null.. maybe not connected yet";
					clientCallback.sendDataFailure(msg, queueData);
				}
			} finally {
//				ReferenceCountUtil.release(queueData);
			}
			
		}
		if(wrote) {
			channel.flush();
		}
	}
	
	public void start() throws InterruptedException, Exception {
		if(this.server != null) {
			node_server = new Thread(this.server);
			
			node_server.start();
		}
		else {
			throw new Exception();
		}
	}
	
	public boolean isServerThreadAlive() {
		return this.node_server.isAlive();
	}
	
	public boolean isAllClientThreadAlive() {
		Iterator i = this.node_client_list.iterator();
		
		while(i.hasNext()) {
			Thread th = (Thread) i.next();
			if(th.isAlive() == false)
				return false;
		}
		return true;
	}
	
	public void block() throws InterruptedException {
		if(this.node_server != null && this.node_server.isAlive()) {
			this.node_server.join();
		}
		
		Iterator<Thread> iterator = node_client_list.iterator();
		while(iterator.hasNext()) {
			Thread th = iterator.next();
			if(th != null && th.isAlive()) {
				th.join();
			}
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		String resource = "config/node.properties";
        Properties properties = new Properties();
        
        try {
//            FileReader reader = new FileReader(resource);
//            properties.load(reader);
//
//            System.out.println("CWNode Start..");
//
//            String nodeIp = properties.getProperty("ip");							// 노드의 IP 값 불러옴
//            int nodePort = Integer.parseInt( properties.getProperty("port") );		// 노드의 PORT 값 불러옴
//            String targetNodeListStr = properties.getProperty("targetNodeList");	// 바라볼 노드의 IP:PORT 리스트를 불러옴
//
//            String[] targetNodeList = null;
//            if(targetNodeListStr != null && targetNodeListStr.equals("")==false ) {
//            	targetNodeListStr = targetNodeListStr.replaceAll(" ", "");	// 공백 제거..
//            	targetNodeList = targetNodeListStr.split(",");
//            }
//
//            System.out.println("Node IP : " + nodeIp);
//            System.out.println("Node PORT : " + nodePort);
//
//            // Node instance..
//    		CWNode cwnode = new CWNode(nodeIp, nodePort, new CWCommunicationCallback() {
//
//    			@Override
//    			public void connectionFailure(Object obj) {
//
//    				System.out.println("connectionFailure callback..!");
//    			}
//
//    			@Override
//    			public void sendDataFailure(Object obj, CWConnProtocol data) {
//    				String msg = (String) obj;
//    				System.out.println("sendData request is Failed ::"+msg);
//    			}
//
//			});
//
//    		// targetNodes Setting..
//    		if(targetNodeList != null) {
//    			for(int i=0; i < targetNodeList.length; i++) {
//    				String[] targetNodeIpAndPort = targetNodeList[i].split(":");
//    				String targetNodeIp = targetNodeIpAndPort[0];
//    				int targetNodePort = Integer.parseInt( targetNodeIpAndPort[1] );
//
//    				cwnode.addClient(targetNodeIp, targetNodePort);
//
//    				System.out.println("TargetNode-" + targetNodeIp + ":" + targetNodePort + " is enrolled..");
//    			}
//
//    		}
//
//    		System.out.println("");
//
//    		// start Node..
//    		cwnode.start();
//
//    		// blocking Main Thread not to finish java main function scope..
//    		cwnode.block();
    		
    		test(null);
    		
        } catch (IOException e) {
        	System.out.println(resource+" 파일을 로드할 수 없습니다. 해당 경로에 설정파일을 작성하시거나, 경로를 확인 부탁드립니다!");
            e.printStackTrace();
        }
		
	}

	public static void test(String[] args) throws Exception {

		System.out.println("CWNode Test Mode Start..");
		
		String myLocalIp = "192.168.0.4";
		
		// TODO 
		
		// sessions 키값 변경.. ip:port -> 고유이름 으로.. 만약 이미 해당 고유이름으로 값이 존재할 경우에는 디나이 처리까지.. 이미 ip:port기준으로는 구현 되어있으니까 적절히 수정.
		// sendDataFailure 콜백에 고유이름 넘겨줌..
		
		// 2. 재전송 요청 및 재전송 응답 기능.. 연결 끊겼을 경우에 못보낸 메세지를 연결 후 다시 보내는 기능까지 생각해야함.. 큐?? .. 테스트는 Read 타임아웃 줄여서 재연결 횟수 많아지게 한 뒤에 테스트작업하면 될듯..
		
		// 결론 ::모든 데이터 전송 요청은 각 연결별로 하나씩 존재하는 어떤큐에 쓰도록 함.. CWNode쪽에 ConcurrentHashMap을 사용하여 채널id값-큐 형태로 관리하자.. 채널 끊길때 삭제시켜주는점 고려
		// ㄴ 채널이 isActive이고 isWritable하면 큐에서 전부 꺼내서 전송처리하는로직까지 수행.
		// 인바운드핸들러의 channelWritabilityChanged에서 모든 세션들 중에 해당채널의 세션을 찾고, isWritable==true일때 큐에서 전부 꺼내서 전송처리하는로직까지 수행.
		
		CWNode cwnode = new CWNode(myLocalIp, 8890, new CWCommunicationCallback() {
			
			@Override
			public void connectionFailure(Object obj) {
				
				System.out.println("cwnode1's connectionFailure callback..!");
			}
			
			@Override
			public void sendDataFailure(Object obj, CWConnProtocol data) {
				
				String msg = (String) obj;
				System.out.println("cwnode sendData request is Failed ::"+msg);
			}

		});
//		cwnode.addClient(myLocalIp, 8892);
		cwnode.addClient("192.168.0.140", 8891);
		
//		CWNode cwnode2 = new CWNode(myLocalIp, 8892, new CWCommunicationCallback() {
//
//			@Override
//			public void connectionFailure(Object obj) {
//
//				System.out.println("cwnode2's connectionFailure callback..!");
//			}
//
//			@Override
//			public void sendDataFailure(Object obj, CWConnProtocol data) {
//				String msg = (String) obj;
//				System.out.println("cwnode2 sendData request is Failed ::"+msg);
//			}
//		});
//		cwnode2.addClient("192.168.0.140", 8891);
//		cwnode2.addClient(myLocalIp, 8890);
		
		cwnode.start();
//		cwnode2.start();
		
		Thread.sleep(5000);
		
		// Node1 -> 140 에게 TEST 패킷 전송.. 
//    	int count = 0;
//    	while(count < 20000 && testRun==true) {
//    		try {
//	    		count++;
//	    		
//	    		JSONObject jo1 = new JSONObject();
//	            jo1.put("msg", "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test...");
//	    		CWConnProtocol test_data1 = new CWConnProtocol(
//	    	    		ProtocolVal.TEST
//	    				, jo1.toString().getBytes("UTF-8")
//	    			);
//	    		
//	    		System.out.println("send -- ");
//	    		cwnode.sendToConnectedNode("192.168.0.140", 8891, test_data1);
//	    		
//	    		JSONObject jo2 = new JSONObject();
//	            jo2.put("msg", "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
//	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test...");
//	    		CWConnProtocol test_data2 = new CWConnProtocol(
//	    	    		ProtocolVal.TEST
//	    				, jo2.toString().getBytes("UTF-8")
//	    			);
//	    		
//	    		System.out.println("send -- ");
//	    		cwnode.sendToConnectedNode("192.168.0.140", 8891, test_data2);
//	    		
//	    		Thread.sleep(1);
//    		
//	    	} catch (Exception e) {
//				e.printStackTrace();
//				CWNode.errorCnt = CWNode.errorCnt + 1;
//			}
//    	}
//    	System.out.println("sendThread is Done......................................................................................");
		
		cwnode.block();
//		cwnode2.block();
		
	}

}
