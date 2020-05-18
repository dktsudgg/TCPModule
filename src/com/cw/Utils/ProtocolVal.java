package com.cw.Utils;

public enum ProtocolVal {
    TEST(0),			// 테스트
    TEST_ACK(1),
    
    SEND_HELLO(2),		// 다른 노드와의 p2p설정을 위한 프로토콜.. 노드간 클러스터 구성에 사용(노드에 접근하는 일반 클라이언트는 사용X)
    ACK_HELLO(3),
    ACK_HELLO_END(4),
    
    SEND_PINGPONG(5),	// 핑퐁
    ACK_PINGPONG(6),
    
    SEND_RETRANS(7),	// 재전송
    ACK_RETRANS(8)
	;

	private final int value;
    private ProtocolVal(int value) {
        this.value = value;
    }

    public byte getValue() {
        return (byte)value;
    }

    public static ProtocolVal convert(byte value) {
        return ProtocolVal.values()[value];
    }
}
