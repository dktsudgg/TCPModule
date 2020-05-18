package com.cw.Utils;

public interface CWCommunicationCallback {
	
	// TODO:: 콜백이 동작하게 된 이유에 대한 상태코드를 기획하고, enum클래스 같은 걸로 정의하여 해당 상태코드도 같이 리턴해주면 개발자 입장에서 처리로직 작성하기 좋을 것 같다..
	
	void sendDataFailure(Object Obj, CWConnProtocol data);	// 주로 아직 연결되지 않았을 때 호출해줄 일이 많을 것 같음.. 또는 너무 빠르게 빈번한 데이터전송 요청이 많을 경우..
	void connectionFailure(Object obj);
}
