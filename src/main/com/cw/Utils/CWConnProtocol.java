package main.com.cw.Utils;

public class CWConnProtocol {
	
	private short header;
	private ProtocolVal protocol;
	private byte[] data;
	
	public CWConnProtocol(short header, ProtocolVal protocol, byte[] data) {
		this.header = header;
		this.protocol = protocol;
		this.data = data;
	}
	
	public CWConnProtocol(ProtocolVal protocol, byte[] data) {
		this.header = (short) data.length;
		this.protocol = protocol;
		this.data = data;
	}

	/**
	 * @return the header
	 */
	public short getHeader() {
		return header;
	}

	/**
	 * @param header the header to set
	 */
	public void setHeader(short header) {
		this.header = header;
	}

	/**
	 * @return the protocol
	 */
	public ProtocolVal getProtocol() {
		return protocol;
	}

	/**
	 * @param protocol the protocol to set
	 */
	public void setProtocol(ProtocolVal protocol) {
		this.protocol = protocol;
	}

	/**
	 * @return the data
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * @param data the data to set
	 */
	public void setData(byte[] data) {
		this.data = data;
	}
	
	
	
}
