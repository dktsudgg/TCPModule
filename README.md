# TCPModule
> Java TCP Module for developing CS/P2P logic using Netty FrameWork.

Making TCP Application has usully two difficulties.

The first is defining and parsing TCP Message boundary logic, Secondary is handling endian problem.

This project is for understanding these two agenda and providing the sample codes. 

![](p2p_achi.png)
![](cs_achi.png)

## Run

```sh
# 1. make property file like this. location is config/node.properties..
# my ip
ip=192.168.0.4
# my port
port=8890
# other node {ip:port} list.
targetNodeList=192.168.0.140:8891,192.168.0.4:8892
```

```sh
# 2. make Runnable jar file with "CWNode" class in com.cw.node package and run.
java -jar tcpmodule-{version}.jar
```

## Usage example

1. Server Node example
```java
// Server Node example..
// You have to use correct {your_ip}:{your_port} that others can detect on the internet.
// This server node example will access infinitely to make connection with Node 192.168.0.140:8891 until success.
String myip = {your_ip};
int myport = {your_port};
CWNode cwnode1 = new CWNode(myip, myport, new CWCommunicationCallback() {
			
   @Override
   public void connectionFailure(Object obj) {

      System.out.println("cwnode1's connectionFailure callback..!");
   }

   @Override
   public void sendDataFailure(Object obj, CWConnProtocol data) {

      String msg = (String) obj;
      System.out.println("cwnode sendData request is Failed ::"+msg);
   }
});
cwnode1.addClient("192.168.0.140", 8891); // you can add more target node as much as you want.

cwnode1.start();

// If you want to blocking until my server down, you can use block() method.
cwnode1.block();
```

## Development setup

you have to install java runtime.

## Release History

* 0.0.1
    * Work in progress.. 
    
I will add two features big/little endian logic and injecting your own Netty handler logic in later version.

## Meta

dktsudgg – dktsudgg@gmail.com

Distributed under the Apache License 2.0. See ``LICENSE`` for more information.

[https://github.com/dktsudgg/TCPModule](https://github.com/dktsudgg/TCPModule)
