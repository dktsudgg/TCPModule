package main.com.cw.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import main.com.cw.Utils.CWConnProtocol;
import main.com.cw.Utils.ProtocolVal;

public class CWConnProtocolEncoder extends MessageToByteEncoder<CWConnProtocol>{
	
	// 패킷 형태
	// HEADER	: 2바이트.. DATA필드의 바이트길이를 나타냄
	// PROTOCOL	: 1바이트.. 사용할 프로토콜의 가짓수가 0~127 이하라는 가정 하에 1바이트로 설정함.. 가짓수가 더 필요하다면 좀 더 늘려서 사용하도록 수정하기..
	// DATA		: 가변길이 바이트.. 비즈니스 로직 페이로드..

	@Override
	protected void encode(ChannelHandlerContext ctx, CWConnProtocol msg, ByteBuf out) throws Exception {
		// TODO Auto-generated method stub
		
//		System.out.println("----- encoded Packet to send -----");
//		System.out.println("----- header : "+msg.getHeader()+" -----");
//		System.out.println("----- protocol : "+msg.getProtocol()+" -----");
//		System.out.println("----- data : "+new String(msg.getData(), 0, msg.getData().length, "UTF-8")+" -----");
		
		ProtocolVal protocol = msg.getProtocol();
	    
	    /*ByteBuf data = ctx.alloc().buffer( msg.getBytes("UTF-8").length );
	    data.writeBytes(msg.getBytes("UTF-8"));*/
	    
	    short header = (short)(msg.getData().length);
	    
	    ByteBuf packet = ctx.alloc().buffer(header + 2 + 1);
	    packet.writeShort(header);
	    packet.writeByte(protocol.getValue());
	    packet.writeBytes(msg.getData());
	    
	    out.writeBytes(packet);
	    
	    packet.release();
	}

}
