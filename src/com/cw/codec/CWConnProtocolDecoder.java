package com.cw.codec;

import java.util.List;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import com.cw.Utils.CWConnProtocol;
import com.cw.Utils.ProtocolVal;

public class CWConnProtocolDecoder extends ByteToMessageDecoder{

	// 패킷 형태
	// HEADER	: 2바이트.. DATA필드의 바이트길이를 나타냄
	// PROTOCOL	: 1바이트.. 사용할 프로토콜의 가짓수가 0~127 이하라는 가정 하에 1바이트로 설정함.. 가짓수가 더 필요하다면 좀 더 늘려서 사용하도록 수정하기..
	// DATA		: 가변길이 바이트.. 비즈니스 로직 페이로드..
	
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		
		// TODO Auto-generated method stub
//		System.out.println("1.Readable bytes size : "+in.readableBytes());
		if (in.readableBytes() < 2) {
			in.resetReaderIndex();
            return; 
        }
		
		short header = in.readShort();
//		System.out.println("Reading two bytes for header is done. ");
		
//		System.out.println("2.Readable bytes size : "+in.readableBytes());
		if(in.readableBytes() <= header){
			in.resetReaderIndex();
			return;
		}
		
		byte protocol = in.readByte();
//		System.out.println("Reading one bytes for protocol is done. ");
//		System.out.println("3.Readable bytes size : "+in.readableBytes());
		ByteBuf data = in.readBytes(header);
//		System.out.println("Reading bytes for data is done. ");
//		System.out.println("4.Readable bytes size : "+in.readableBytes());
		
		// ByteBuf to byte[]
		byte[] bytes = new byte[data.readableBytes()];
		data.readBytes(bytes);
		
//		// 만약 받아온 데이터가 JSON으로 파싱에 실패한다면 이것은 내가 만든 프로토콜로 구성된것이 아닌 이상한 패킷인 것..
//		String receivedDataJsonStr;
//		try {
//			receivedDataJsonStr = new String(bytes, 0, bytes.length, "UTF-8");
//			new JSONObject(receivedDataJsonStr);
//		} catch (UnsupportedEncodingException e) {
//			System.out.println("UnsupportedEncodingException..");
//			System.out.println("Packet Clear with 'in.readBytes(in.readableBytes());'");
//			in.readBytes(in.readableBytes());
//			return ;
//		} catch(JSONException je) {
//			System.out.println("JSONException..");
//			System.out.println("Packet Clear with 'in.readBytes(in.readableBytes());'");
//			in.readBytes(in.readableBytes());
//			return ;
//		}
		
		CWConnProtocol pObject = new CWConnProtocol(/*header, */ProtocolVal.convert(protocol), bytes);
		
        out.add(pObject);
        
        data.release();
        in.discardReadBytes();
        bytes = null;
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		
//		cause.printStackTrace();
//		System.out.println("Decoder exceptionCaught..");
		
		super.exceptionCaught(ctx, cause);
	}

}
