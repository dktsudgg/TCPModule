package main.com.cw.node;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONObject;

import main.com.cw.Utils.CWNodeClientPacketReceiver;
import main.com.cw.Utils.CWConnProtocol;
import main.com.cw.Utils.CWNodeClientCallback;
import main.com.cw.Utils.ProtocolVal;
import main.com.cw.Utils.Utils;

public class CWNodeClient {	// Blocking IO 소켓을 사용하는 클라이언트.. 
	
	// 동기 / 비동기 모드를 설정하는 구분변수를 하나 두고 구현해두면 좋을 것 같음.. 비동기 모드일 경우에는 스레드 돌리고, 동기모드일 경우에는 그냥..
	// 객체 한번 만들어지면 이후에는 그 객체에서 모드 설정 변경은 불가능하도록..!
	enum ClientMode {
		MODE_NOT_SET_YET(0),
		MODE_SYNC(1),
		MODE_ASYNC(2);
		
		private final int value;
	    private ClientMode(int value) {
	        this.value = value;
	    }

	    public byte getValue() {
	        return (byte)value;
	    }

	    public static ProtocolVal convert(byte value) {
	        return ProtocolVal.values()[value];
	    }
	}
	
	private ClientMode mode;
	
	private CWNodeClientCallback asyncCallback;	// 비동기 모드에서만 사용.
	private Thread asyncDataReceiveThread;		// 비동기 모드에서만 사용.
	
	String targetNodeIp;
	int targetNodePort;
	
	Socket socket;
	BufferedOutputStream buffSend;
	BufferedInputStream buffRecv;

	public CWNodeClient(String targetNodeIp, int targetNodePort) {
		
		this.mode = ClientMode.MODE_NOT_SET_YET;
		
		this.targetNodeIp = targetNodeIp;
		this.targetNodePort = targetNodePort;
	}
	
	private void connect(ClientMode mode) throws UnknownHostException, IOException {
		this.mode = mode;
		
		this.socket = new Socket(this.targetNodeIp, this.targetNodePort);
		this.buffSend = new BufferedOutputStream(this.socket.getOutputStream());
		this.buffRecv = new BufferedInputStream(this.socket.getInputStream());
	}
	
	public CWNodeClient connectSyncMode() throws UnknownHostException, IOException {
		
		connect(ClientMode.MODE_SYNC);
		
		return this;
	}
	
	public CWNodeClient connectAsyncMode(CWNodeClientCallback callback){

		this.asyncCallback = callback;
		
		try {
			connect(ClientMode.MODE_ASYNC);
		} catch (IOException e) {
			this.asyncCallback.connectionFailure(e);
		}
		
		Runnable r = new CWNodeClientPacketReceiver(this.buffRecv, this.asyncCallback);
		asyncDataReceiveThread = new Thread( r );
		asyncDataReceiveThread.start();
		
		return this;
	}
	
	public void disconnect() {
		switch( this.mode ) {
		case MODE_NOT_SET_YET:
			System.out.println("Connection is not set yet..");
			break;
		case MODE_SYNC:
		case MODE_ASYNC:
			try {
//				this.buffSend.close();
//				this.buffRecv.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			this.buffSend = null;
			this.buffRecv = null;
			this.socket = null;
			
			break;
		}
		
	}
	
	public void sendDataWithOutReturn(ProtocolVal protocol, String jsonStr) throws IOException {
		Utils.sendData(buffSend, protocol, jsonStr.toString());
	}
	
	public CWConnProtocol sendDataWithReturn(ProtocolVal protocol, String jsonStr) throws IOException {
		sendDataWithOutReturn(protocol, jsonStr);
		return Utils.receiveData(buffRecv);
	}
	
	public CWConnProtocol send(ProtocolVal protocol, String jsonStr) throws IOException {
		
		CWConnProtocol returnData = null;
		
		switch( mode ) {
		
		case MODE_SYNC:		// 동기 모드..
			returnData = sendDataWithReturn(protocol, jsonStr);
			break;
			
		case MODE_ASYNC:	// 비동기 모드..
			sendDataWithOutReturn(protocol, jsonStr);
			break;
		}
		
		return returnData;
	}
	
	public static void main(String[] args) {
		
		String targetNodeIp = "192.168.0.140";
		int targetNodePort = 8891;
		
		// 클라이언트를 동기 모드로 작성하는 경우 테스트
//		System.out.println(" - SYNC CWNodeClient Start !");
//		testSyncModeClient( targetNodeIp, targetNodePort );
//		System.out.println(" - SYNC CWNodeClient END...");
		
		// 클라이언트를 비동기 모드로 작성하는 경우 테스트
		System.out.println(" - ASYNC CWNodeClient Start !");
		testAsyncModeClient( targetNodeIp, targetNodePort );
		System.out.println(" - ASYNC CWNodeClient END...");
		
		System.out.println("\n");
		System.out.println(" - All CWNodeClients END...");
	}
	
	public static void testSyncModeClient(String targetNodeIp, int targetNodePort) {
		
		try {
			// 1. 클라이언트 생성 및 연결 호출..
			CWNodeClient client = new CWNodeClient(
				targetNodeIp
				, targetNodePort
			)
			.connectSyncMode();	// 동기 모드 !
			
			for(int i=0; i<100000; i++) {
				// 2. TEST 패킷 만들어서 노드에 전송 및 결과 리턴받기.
				JSONObject jo = new JSONObject();
				jo.put("msg", "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test...");
				CWConnProtocol packet = client.send(ProtocolVal.TEST, jo.toString());
				
				// 3. 노드로부터 받아온 결과 내용 출력
				String receivedPacketLog = "\n" + "<<<----- RECEIVED PACKET ----->>>";
				receivedPacketLog += "\n" + "Protocol : " + packet.getProtocol();
				receivedPacketLog += "\n" + "Data : " + new String(packet.getData(), 0, packet.getData().length, "UTF-8");
				receivedPacketLog += "\n" + "<<<--------------------------->>>\n";
				System.out.println(receivedPacketLog);
			}
			
			Thread.sleep(10000);
			
			client.disconnect();
			
			Thread.sleep(1000);
			
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public static void testAsyncModeClient(String targetNodeIp, int targetNodePort) {
		
		try {
			// 1. 클라이언트 생성 및 연결 호출, 콜백함수 작성..
			final CWNodeClient client = new CWNodeClient(
				targetNodeIp
				, targetNodePort
			);
			client.connectAsyncMode(new CWNodeClientCallback() {
				
				@Override
				public void receivedData(Object obj) {
					try {
						CWConnProtocol packet = (CWConnProtocol) obj;
						
						// 3. 노드로부터 받아온 결과 내용 출력
						String receivedPacketLog = "\n" + "<<<----- RECEIVED PACKET ----->>>";
						receivedPacketLog += "\n" + "Protocol : " + packet.getProtocol();
						receivedPacketLog += "\n" + "Data : " + new String(packet.getData(), 0, packet.getData().length, "UTF-8");
						receivedPacketLog += "\n" + "<<<--------------------------->>>\n";
						System.out.println(receivedPacketLog);

						// if server send PING, then client send PONG.
						if(packet.getProtocol().equals(ProtocolVal.SEND_PINGPONG)){
							JSONObject jsonPing = new JSONObject();
							jsonPing.put("msg", "pong");

							client.send(ProtocolVal.ACK_PINGPONG, jsonPing.toString());
						}

					} catch(UnsupportedEncodingException ue) {
						
					} catch (IOException e) {
						e.printStackTrace();
					}

				}
				
				@Override
				public void connectionFailure(Object obj) {
					Exception e = (Exception) obj;
					e.printStackTrace();
					
				}
			});
			
			// 2. TEST 패킷 만들어서 노드에 전송.. 결과는 콜백으로 받음..
			int i=0;
			for(i=0; i<1; i++) {
				JSONObject jo = new JSONObject();
				jo.put("msg", i+"test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
		        		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test...");
				
				CWConnProtocol packet = client.send(ProtocolVal.TEST, jo.toString());
				
//				Thread.sleep(1);
			}
			
			System.out.println("i="+i);
			
			Thread.sleep(1000000);
			
			client.disconnect();
			
			Thread.sleep(1000);
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}
