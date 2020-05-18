package com.cw.Utils;

import java.io.UnsupportedEncodingException;

public interface CWNodeClientCallback {
	void connectionFailure(Object obj);
	void receivedData(Object obj);	
}
