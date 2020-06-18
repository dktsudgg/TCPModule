package test.com.cw.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.UnsupportedEncodingException;

import com.google.gson.JsonObject;
import main.com.cw.Utils.CWCommunicationCallback;
import main.com.cw.Utils.CWConnProtocol;
import main.com.cw.Utils.ProtocolVal;
import main.com.cw.Utils.Utils;
import main.com.cw.codec.CWConnProtocolDecoder;
import main.com.cw.codec.CWConnProtocolEncoder;
import main.com.cw.node.CWNode;
import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

// TDD ..
// 단위 테스트를 위한 클래스(JUnit).. https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/
class CWUnitTest {
	
	/*
	 * 
	 * 디코더(CWConnP2PProtocolDecoder): ByteBuf -> CWConnP2PProtocol
	 * 인코더(CWConnP2PProtocolEncoder): CWConnP2PProtocol -> ByteBuf
	 * 
	 * 구현해야 할 테스트 목록
	 * 1. 디코더 동작 테스트
	 * 2. 인코더 동작 테스트
	 * 3. 디코더와 인코더 혼용 동작 테스트
	 * 4. ...
	 * 
	 * 
	 */

	// 디코더 동작 테스트..
	@Test
	void testDecoder() throws UnsupportedEncodingException {
		//fail("Not yet implemented");\
		System.out.println("- 디코더 동작 테스트.. START");
		
		EmbeddedChannel channel = new EmbeddedChannel(new CWConnProtocolEncoder(), new CWConnProtocolDecoder());
		
		// 테스트 데이터
//		JSONObject data = new JSONObject();
//		data.put("msg", "test test test");
		JsonObject data = new JsonObject();
		data.addProperty("msg", "test test test");

		// 테스트 데이터를 기반으로 만든 테스트 프로토콜 ..(ByteBuf)
		byte[] bytes = Utils.getPacket(ProtocolVal.TEST, data.toString().getBytes("UTF-8"));
        ByteBuf bytebuf = PooledByteBufAllocator.DEFAULT.heapBuffer(1);
        bytebuf.writeBytes(bytes);
        
        // 디코더에 삽입
        channel.writeInbound( bytebuf );
        // 디코더에서 결과 읽어오기.. 널체크 / 넣었던 거랑 같은지 체크
        CWConnProtocol returnCwProto = (CWConnProtocol) channel.readInbound();
        assertNotNull(returnCwProto);
        
        // 확인..
        assertEquals(returnCwProto.getHeader(), 24);
        assertEquals(returnCwProto.getProtocol(), ProtocolVal.TEST);
        assertEquals(
        	new String(returnCwProto.getData(), "UTF-8")
        	, new String("{\"msg\":\"test test test\"}")
        );
        
        System.out.println("- 디코더 동작 테스트.. END");
	}
	
	// 인코더 동작 테스트..
	@Test
	void testEncoder() throws UnsupportedEncodingException {
		//fail("Not yet implemented");
		System.out.println("- 인코더 동작 테스트.. START");
		
		EmbeddedChannel channel = new EmbeddedChannel(new CWConnProtocolEncoder(), new CWConnProtocolDecoder());
		
		// 테스트 데이터
//		JSONObject data = new JSONObject();
//		data.put("msg", "test test test");
		JsonObject data = new JsonObject();
		data.addProperty("msg", "test test test");

		// 테스트 데이터를 기반으로 만든 테스트 프로토콜 ..(CWConnP2PProtocol)
		CWConnProtocol cwproto = new CWConnProtocol(
			ProtocolVal.TEST
			, data.toString().getBytes("UTF-8")
		);
		
		// 인코더에 삽입
        channel.writeOutbound(cwproto);
        // 인코더에서 결과 읽어오기.. 널체크 / 넣었던 거랑 같은지 체크
        ByteBuf returnByteBuf = (ByteBuf) channel.readOutbound();
        assertNotNull(returnByteBuf);
        
        byte[] resultBytes = new byte[returnByteBuf.readableBytes()];
        returnByteBuf.readBytes(resultBytes);
        
        // 원천 테스트데이터와 최종 결과로 나온 데이터 비교.. 같아야함.
        assertEquals(
        	java.util.Arrays.equals(
        		resultBytes
        		, Utils.getPacket(ProtocolVal.TEST, data.toString().getBytes("UTF-8"))
        	)
        	, true
        );
        
        System.out.println("- 인코더 동작 테스트.. END");
	}
	
	// 디코더와 인코더 혼용 동작 테스트..
	// 원본 데이터를를 디코더에 넣고 돌린 결과를 다시 인코더에 넣어서 나온 값을 원본 바이트와 비교..
	@Test
	void testDecoderAndEncoder() throws UnsupportedEncodingException {
		System.out.println("- 디코더와 인코더 혼용 동작 테스트.. START");
		System.out.println("\t원천데이터를 디코더에 넣고, 결과값을 다시 인코더에 넣어서 원천데이터와 값을 비교하는 테스트.. 같아야함");
		
		EmbeddedChannel channel = new EmbeddedChannel(new CWConnProtocolEncoder(), new CWConnProtocolDecoder());
		
		// 테스트 데이터
//		JSONObject data = new JSONObject();
//		data.put("msg", "test test test");
		JsonObject data = new JsonObject();
		data.addProperty("msg", "test test test");

		// 테스트 데이터를 기반으로 만든 테스트 프로토콜 ..(ByteBuf)
		byte[] bytes = Utils.getPacket(ProtocolVal.TEST, data.toString().getBytes("UTF-8"));
		ByteBuf bytebuf = PooledByteBufAllocator.DEFAULT.heapBuffer(1);
		bytebuf.writeBytes(bytes);
		
		// 디코더에 삽입
		channel.writeInbound( bytebuf );
		// 디코더에서 결과 읽어오기.. 널체크 / 넣었던 거랑 같은지 체크
		CWConnProtocol returnCwProto = (CWConnProtocol) channel.readInbound();
		
		// 인코더에 삽입
        channel.writeOutbound(returnCwProto);
        // 인코더에서 결과 읽어오기.. 널체크 / 넣었던 거랑 같은지 체크
        ByteBuf returnByteBuf = (ByteBuf) channel.readOutbound();
        assertNotNull(returnByteBuf);
        
        byte[] resultBytes = new byte[returnByteBuf.readableBytes()];
        returnByteBuf.readBytes(resultBytes);
        
        // 원천 테스트데이터와 최종 결과로 나온 데이터 비교.. 같아야함.
        assertEquals(
        	java.util.Arrays.equals(resultBytes, bytes)
        	, true
        );
		
		System.out.println("- 디코더와 인코더 혼용 동작 테스트.. END");
	}
	
	@Test
	void testPeerConn() throws InterruptedException, UnsupportedEncodingException {
		// TODO:: 여러개의 CWNode를 띄워서 테스트하는 코드 작성..
	}

	@Test
	void testReal() throws Exception {

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
		cwnode.addConnector("192.168.0.140", 8890);

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
    	int count = 0;
    	while(count < 1000 /*&& testRun==true*/) {
    		try {
	    		count++;

//	    		JSONObject jo1 = new JSONObject();
				JsonObject jo1 = new JsonObject();
	            jo1.addProperty("msg", "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test...");
	    		CWConnProtocol test_data1 = new CWConnProtocol(
	    	    		ProtocolVal.TEST
	    				, jo1.toString().getBytes("UTF-8")
	    			);

	    		System.out.println("send -- ");
	    		cwnode.sendToConnectedNode("192.168.0.140", 8890, test_data1);

				JsonObject jo2 = new JsonObject();
	            jo2.addProperty("msg", "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test"
	            		+ "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test...");
	    		CWConnProtocol test_data2 = new CWConnProtocol(
	    	    		ProtocolVal.TEST
	    				, jo2.toString().getBytes("UTF-8")
	    			);

	    		System.out.println("send -- ");
	    		cwnode.sendToConnectedNode("192.168.0.140", 8890, test_data2);

	    		Thread.sleep(1);

	    	} catch (Exception e) {
				e.printStackTrace();
//				CWNode.errorCnt = CWNode.errorCnt + 1;
			}
    	}
    	System.out.println("sendThread is Done......................................................................................");

		cwnode.block();
//		cwnode2.block();

	}

}
