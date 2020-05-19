package main.com.cw.Utils;

import io.netty.channel.Channel;

public class IpPort {
    String ip;
    int port;
    Channel channel;

    public IpPort(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

	@Override
	public String toString() {
		return ip+":"+port;
	}

	@Override
	public boolean equals(Object obj) {
		IpPort o = (IpPort) obj;
		
		try {
			if(ip.equals(o.getIp()) && port == o.getPort()) {
				return true;
			}
			else {
				return false;
			}
		} catch(Exception e) {
			return false;
		}
		
	}
    
	
}
