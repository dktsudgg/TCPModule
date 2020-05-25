package main.com.cw.component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import io.netty.util.internal.SystemPropertyUtil;
import main.com.cw.Utils.*;
import main.com.cw.codec.CWConnProtocolDecoder;
import main.com.cw.codec.CWConnProtocolEncoder;

public class CWCommunicationServer implements Runnable{
	
	// HELLO 프로토콜로 인사를 마친 노드가 이 변수에 기록됨.. HELLO 프로토콜을 거치지 않는 CWNodeClient같은 애들은 이 변수에 기록되지 않음.
	// 즉 클러스터의 멤버는 이 변수에 기록하고, 그냥 일반 클라이언트는 이 변수에 기록하지 않음.
	private ConcurrentHashMap<String, IpPort> sessions;
	private ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues;
	
	private String myip;
	private int myport;
	
	CWCommunicationCallback callback;
    
    public CWCommunicationServer(String host, int port
    		, ConcurrentHashMap<String, IpPort> sessions, ConcurrentHashMap<String, LinkedBlockingDeque> channelWriteQueues, CWCommunicationCallback callback) {
    	this.myip = host;
        this.myport = port;
        
        this.sessions = sessions;
        this.channelWriteQueues = channelWriteQueues;
        
        this.callback = callback;
    }
    
    public ConcurrentHashMap<String, IpPort> getSessions(){
    	return this.sessions;
    }
    
    public ConcurrentHashMap<String, LinkedBlockingDeque> getChannelWriteQueues(){
    	return this.channelWriteQueues;
    }
    
    @Override
    public void run() {

        Class channelClass = NioServerSocketChannel.class;
        EventLoopGroup bossGroup = null;
        EventLoopGroup workerGroup = null;
    	
//        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // (1)
//        EventLoopGroup bossGroup = new EpollEventLoopGroup(1); // (1)
        
        int DEFAULT_WORKERGROUP_THREADS = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", Runtime.getRuntime().availableProcessors() * 2));
//        int DEFAULT_WORKERGROUP_THREADS = 16;

//        EventLoopGroup workerGroup = new NioEventLoopGroup( DEFAULT_WORKERGROUP_THREADS );
//        EventLoopGroup workerGroup = new EpollEventLoopGroup( DEFAULT_WORKERGROUP_THREADS );

        if (OSValidator.isUnix()) {
            bossGroup = new EpollEventLoopGroup(1 );
            workerGroup = new EpollEventLoopGroup(DEFAULT_WORKERGROUP_THREADS );
            channelClass =  EpollServerSocketChannel.class;
        } else {
            bossGroup = new NioEventLoopGroup( 1 );
            workerGroup = new NioEventLoopGroup( DEFAULT_WORKERGROUP_THREADS );
        }

        try {
            ServerBootstrap b = new ServerBootstrap(); // (2)
            b.group(bossGroup, workerGroup)
             .channel( channelClass ) // (3)
//             .channel(EpollServerSocketChannel.class)
             .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3*1000)	// 커넥션 타임아웃 설정.. 3초
             .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     // 방법 1. 일정 시간동안 통신 없을 시 무조건 끊는 방법
                     // ch.pipeline().addLast(new ReadTimeoutHandler(10));	// 이 핸들러 사용하면 Read 타임아웃 시 바로 연결끊음.. 10초..

//                     // 방법 2. 일정 시간동안 통신 없을 시 dummy패킷을 보내서 연결의 정상 여부를 확인하는 방법.
//                     // 12초동안 수신하는 메세지가 없는지 감지 / 5초동안 송신하는 메세지가 없는지 감지하는 핸들러
                     ch.pipeline().addLast(new IdleStateHandler(12, 5, 0));
//                     // 일정 시간 송/수신 메세지가 없을 시 IdleStateHandler로부터 발생하는 이벤트 처리로직 핸들러..
                     ch.pipeline().addLast(new IdleProofHandler( getChannelWriteQueues(), callback ));

                	 ch.pipeline().addLast(new CWConnProtocolEncoder());
                     ch.pipeline().addLast(new CWConnProtocolDecoder());
                     ch.pipeline().addLast(new CWCommunicationHandler(myip, myport, getSessions(), getChannelWriteQueues(), callback ));

                 }
             })
             .option(ChannelOption.SO_BACKLOG, 128)          // (5).. 동시에 N개만큼의 클라이언트 연결 요청을 받아들이겠다는 의미.. https://groups.google.com/forum/#!topic/netty-ko/TA9wek1m8Ss
//             .childOption(ChannelOption.SO_RCVBUF, 10485760)
//             .childOption(ChannelOption.SO_SNDBUF, 10485760)
             .option(ChannelOption.SO_REUSEADDR, true)

             .childOption(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator())
             .childOption(ChannelOption.SO_KEEPALIVE, true) // (6)
             .childOption(ChannelOption.TCP_NODELAY, true)  // 네이글 알고리즘 off
             .childOption(ChannelOption.SO_REUSEADDR, true)
             .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
             .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
             .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            ;

            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(myport).sync(); // (7)

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync();
        }
        catch(Exception e) {
        	System.out.println("Server is Dead...");
        }
        finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
    
    public static void main(String[] args) throws Exception {
    	String host="localhost";
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8890;    // My Port !!!!!!!!!!!!!!!!!!!!!!
        }
        
        System.out.println("NettyTCPSample Server started.. port is "+port);

        new CWCommunicationServer(host, port, new ConcurrentHashMap<String, IpPort>(), new ConcurrentHashMap<String, LinkedBlockingDeque>(), new CWCommunicationCallback() {
			
			@Override
			public void connectionFailure(Object obj) {
				
				System.out.println("connectionFailure callback..!");
			}
			
			@Override
			public void sendDataFailure(Object obj, CWConnProtocol data) {
				String msg = (String) obj;
				System.out.println("sendData request is Failed ::"+msg);
			}

        }).run();
    }
}
