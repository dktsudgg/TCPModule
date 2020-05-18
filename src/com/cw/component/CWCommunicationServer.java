package com.cw.component;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.SystemPropertyUtil;

import com.cw.Utils.CWCommunicationCallback;
import com.cw.Utils.CWCommunicationHandler;
import com.cw.Utils.CWConnProtocol;
import com.cw.Utils.IpPort;
import com.cw.Utils.Utils;
import com.cw.codec.CWConnProtocolDecoder;
import com.cw.codec.CWConnProtocolEncoder;

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
    	
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // (1)
        
//        int DEFAULT_WORKERGROUP_THREADS = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", Runtime.getRuntime().availableProcessors() * 2));
        int DEFAULT_WORKERGROUP_THREADS = 16;
        EventLoopGroup workerGroup = new NioEventLoopGroup( DEFAULT_WORKERGROUP_THREADS );
        
        try {
            ServerBootstrap b = new ServerBootstrap(); // (2)
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class) // (3)
             .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3*1000)	// 커넥션 타임아웃 설정.. 3초
             .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
//                	 ch.pipeline().addLast(new ReadTimeoutHandler(30));	// Read 타임아웃 설정.. 30초
                	 ch.pipeline().addLast(new CWConnProtocolEncoder());
                     ch.pipeline().addLast(new CWConnProtocolDecoder());
                     ch.pipeline().addLast(new CWCommunicationHandler(myip, myport, getSessions(), getChannelWriteQueues(), callback ));
                 }
             })
             .option(ChannelOption.SO_BACKLOG, 128)          // (5)
             .childOption(ChannelOption.SO_KEEPALIVE, true) // (6)
             .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
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
