package main.com.cw.Utils;

import com.google.gson.JsonObject;
import main.com.cw.node.CWNode;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class IdleProofHandler extends ChannelDuplexHandler {

    private ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues;
    CWCommunicationCallback callback;

    public IdleProofHandler(ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues, CWCommunicationCallback callback){
        this.channelWriteQueues = channelWriteQueues;
        this.callback = callback;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        super.userEventTriggered(ctx, evt);
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;

            // 일정시간동안 송신하는 메세지가 없다는 IdleState 이벤트를 받았을 경우에 PING 전송,
            // 일정시간동안 수신하는 메세지가 없다는 IdleState 이벤트가 발생할 경우 연결 종료하는 로직..

            // 수신자 핸들러쪽에는 PING 메세지를 받았을 때, PONG 메세지를 전송하게끔 되어있기 때문에
            // READER_IDLE 상태로 빠졌다면, 종단간 메세지 송수신이 불가능한 상황이라는 것을 확신할 수 있음.(자연스럽게 연결 끊김)

            if (e.state() == IdleState.READER_IDLE) {
                ctx.close();
            }
            else if (e.state() == IdleState.WRITER_IDLE) {
                // ping
                System.out.println("Ping !!");
//                JSONObject jsonPing = new JSONObject();
//                jsonPing.put("msg", "ping");
                JsonObject jsonPing = new JsonObject();
                jsonPing.addProperty("msg", "ping");

                CWConnProtocol packet = new CWConnProtocol(
                    ProtocolVal.SEND_PINGPONG
                    , jsonPing.toString().getBytes("UTF-8")
                );
                CWNode.sendData(ctx.channel(), packet, callback, channelWriteQueues);
            }

        }
    }
}
