package test.com.cw.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.UnsupportedEncodingException;

import main.com.cw.Utils.CWConnProtocol;
import main.com.cw.Utils.ProtocolVal;
import main.com.cw.Utils.Utils;
import main.com.cw.codec.CWConnProtocolDecoder;
import main.com.cw.codec.CWConnProtocolEncoder;
import org.json.JSONObject;
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
		JSONObject data = new JSONObject();
		data.put("msg", "test test test");
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
		JSONObject data = new JSONObject();
		data.put("msg", "test test test");
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
		JSONObject data = new JSONObject();
		data.put("msg", "test test test");
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

}
