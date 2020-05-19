package com.cw.Utils;

import java.io.BufferedInputStream;

public class CWNodeClientPacketReceiver implements Runnable{
	
	private BufferedInputStream buffRecv;
	private CWNodeClientCallback asyncCallback;
	
	public CWNodeClientPacketReceiver(BufferedInputStream buffRecv, CWNodeClientCallback asyncCallback) {
		this.buffRecv = buffRecv;
		this.asyncCallback = asyncCallback;
	}

	@Override
	public void run() {
		try {
			while(true) {
				CWConnProtocol receivedm = Utils.receiveData(buffRecv);
				this.asyncCallback.receivedData(receivedm);
			}
		} catch (Exception e) {
			this.asyncCallback.connectionFailure(e);
		}
	}
	
}
