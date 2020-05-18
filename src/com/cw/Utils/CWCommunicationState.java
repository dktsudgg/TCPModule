package com.cw.Utils;

public enum CWCommunicationState {
	CONNECTED(0),
	NOT_CONNECTED(1)
	;
	
	private final int value;
    private CWCommunicationState(int value) {
        this.value = value;
    }

    public byte getValue() {
        return (byte)value;
    }

    public static CWCommunicationState convert(byte value) {
        return CWCommunicationState.values()[value];
    }
}
