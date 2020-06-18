package main.com.cw.node;

import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import main.com.cw.Utils.CWCommunicationCallback;
import main.com.cw.Utils.CWConnProtocol;
import main.com.cw.Utils.IpPort;
import main.com.cw.component.CWCommunicationClient;
import main.com.cw.component.CWCommunicationServer;

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
	
	public void addConnector(String otherNodeHost, int otherNodePort) {
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

				// 데이터 송수신 방법 1
				targetChannelQueue.add(data);
				execSendWriteQueue(channel, targetChannelQueue, clientCallback);

				// 데이터 송수신 방법 2
//				execSendWriteData(channel, data, targetChannelQueue, clientCallback);
				
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

    public static void execSendWriteData(Channel channel, CWConnProtocol data, LinkedBlockingDeque targetChannelQueue, CWCommunicationCallback clientCallback){
		boolean wrote = false;
		CWConnProtocol queueData = null;
		int tick = 0;

		// 채널이 writable하다면 먼저 기존에 큐에 존재하던 데이터 전부 쏴준 다음에 내꺼 쏘기..
		// 중간에 isWritable하지 않아진다면 내가 보내려던거 큐에 넣어주고 종료..
		while(channel.isWritable() ){

			if(targetChannelQueue != null && targetChannelQueue.isEmpty()==false){
				queueData = (CWConnProtocol) targetChannelQueue.poll();
				try {

					if(queueData != null) {
						channel.write(queueData);
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
			else if(targetChannelQueue != null && targetChannelQueue.isEmpty()==true){
				try {
					channel.write(data);
					wrote = true;

					if(tick % 10 == 0) {
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

				break;
			}
		}
		if(wrote) {
			channel.flush();
		}
	}
	
	public static void execSendWriteQueue(Channel channel, LinkedBlockingDeque targetChannelQueue, CWCommunicationCallback clientCallback) {
		boolean wrote = false;
		CWConnProtocol queueData = null;
		
		int tick = 0;
		
		while(channel.isWritable() && targetChannelQueue != null && targetChannelQueue.isEmpty()==false ) {
			tick++;

			queueData = (CWConnProtocol) targetChannelQueue.poll();
			
			try {
				
				if(queueData != null) {
					channel.write(queueData);
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
            FileReader reader = new FileReader(resource);
            properties.load(reader);

            System.out.println("CWNode Start..");

            String nodeIp = properties.getProperty("ip");							// 노드의 IP 값 불러옴
            int nodePort = Integer.parseInt( properties.getProperty("port") );		// 노드의 PORT 값 불러옴
            String targetNodeListStr = properties.getProperty("targetNodeList");	// 바라볼 노드의 IP:PORT 리스트를 불러옴

            String[] targetNodeList = null;
            if(targetNodeListStr != null && targetNodeListStr.equals("")==false ) {
            	targetNodeListStr = targetNodeListStr.replaceAll(" ", "");	// 공백 제거..
            	targetNodeList = targetNodeListStr.split(",");
            }

            System.out.println("Node IP : " + nodeIp);
            System.out.println("Node PORT : " + nodePort);

            // Node instance..
    		CWNode cwnode = new CWNode(nodeIp, nodePort, new CWCommunicationCallback() {

    			@Override
    			public void connectionFailure(Object obj) {

    				System.out.println("connectionFailure callback..!");
    			}

    			@Override
    			public void sendDataFailure(Object obj, CWConnProtocol data) {
    				String msg = (String) obj;
    				System.out.println("sendData request is Failed ::"+msg);
    			}

			});

    		// targetNodes Setting..
    		if(targetNodeList != null) {
    			for(int i=0; i < targetNodeList.length; i++) {
    				String[] targetNodeIpAndPort = targetNodeList[i].split(":");
    				String targetNodeIp = targetNodeIpAndPort[0];
    				int targetNodePort = Integer.parseInt( targetNodeIpAndPort[1] );

    				cwnode.addConnector(targetNodeIp, targetNodePort);

    				System.out.println("TargetNode-" + targetNodeIp + ":" + targetNodePort + " is enrolled..");
    			}

    		}

    		System.out.println("");

    		// start Node..
    		cwnode.start();

    		// blocking Main Thread not to finish java main function scope..
    		cwnode.block();
    		
        } catch (IOException e) {
        	System.out.println(resource+" 파일을 로드할 수 없습니다. 해당 경로에 설정파일을 작성하시거나, 경로를 확인 부탁드립니다!");
            e.printStackTrace();
        }
		
	}

}
