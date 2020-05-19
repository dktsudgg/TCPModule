package main.com.cw.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

public class Utils {
	
	public static void log(String ip, int port , String content) {
		System.out.println("["+ip+":"+port+"] "+content);
	}
	
	public static byte[] getPacket(/*short header, */ProtocolVal protocal, byte[] strbytes){
    	short header = (short) strbytes.length;
		byte[] packet = new byte[2 + 1 + header];
		packet[0] = (byte) (header >> 8 & 0xff);
		packet[1] = (byte) ((header) & 0xff);
		packet[2] = protocal.getValue();
		for (int i = 0; i < header; i++) {
			packet[i + 2 + 1] = strbytes[i];
		}
		return packet;
	}
	
	public static void sendData(BufferedOutputStream buffSend, ProtocolVal protocol, String dataStr) throws IOException{
		
		// short header = (short) ((short) "asdf".getBytes("UTF-8").length);
		// byte protocol = 2; 
		// short to bytes
		byte[] strbytes = dataStr.getBytes("UTF-8");
		byte[] packet = Utils.getPacket(/*header, */protocol, strbytes);
//		System.out.print(byteArrayToHex(packet));
		buffSend.write( packet );
		buffSend.flush();
		
	}
	
	public static String byteArrayToHex(byte[] a) {
	    StringBuilder sb = new StringBuilder();
	    for(final byte b: a)
	        sb.append(String.format("%02x", b&0xff));
	    return sb.toString();
	}
	
	public static CWConnProtocol receiveData(BufferedInputStream buffRecv) throws IOException{
		
		int i = 0, j = 0;
		byte[] header_bytes = new byte[2];
		while (true) {
			if (i == 2)
				break;
			j = buffRecv.read(header_bytes, i, 2 - i);
			i += j;
		}
		byte[] protocal = new byte[1];
		while (true) {
			int a = buffRecv.read(protocal, 0, 1);
			if (a == 1)
				break;
		}

		short header = (short) (((header_bytes[0] & 0xff) << 8) | (header_bytes[1] & 0xff));
		i = 0;
		j = 0;
		byte[] contents_bytes = new byte[header];
		while (true) {
			if (i == header)
				break;
			j = buffRecv.read(contents_bytes, i, header - i);
			i += j;
		}
		//String str = new String(contents_bytes, 0, header); // No Problem..
		//String str = new String(contents_bytes, 0, header, "UTF-8");
		//ByteBuf data =  Unpooled.wrappedBuffer(str.getBytes("UTF-8"));
		// ByteBuf to byte[]
		//byte[] bytes = new byte[data.readableBytes()];
		//data.readBytes(bytes);
		
		return new CWConnProtocol(/*header, */ProtocolVal.convert(protocal[0]), contents_bytes);
		
		
	}
	
}
