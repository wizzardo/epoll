Epoll
=========

Event-based socket server, which uses [epoll]

```java
        EpollServer server = new EpollServer(8080) {
            @Override
            protected IOThread createIOThread() {
                return new IOThread() {
                    byte[] buffer = new byte[1024];
                    byte[] data = ("HTTP/1.1 200 OK\r\n" +
                            "Connection: Keep-Alive\r\n" +
                            "Content-Length: 11\r\n" +
                            "Content-Type: text/html;charset=UTF-8\r\n" +
                            "\r\n" +
                            "it's alive!").getBytes();

                    @Override
                    public void onConnect(Connection connection) {
                        System.out.println("new connection!");
                    }

                    @Override
                    public void onDisconnect(Connection connection) {
                        System.out.println("we lost him =(");
                    }

                    @Override
                    public void onRead(Connection connection) {
                        try {
                            int r = connection.read(buffer, 0, buffer.length);
                            System.out.println("request: " + new String(buffer, 0, r));
                            connection.write(data);
                        } catch (IOException e) {
                            connection.close();
                        }
                    }
                };
            }
        };
        server.setIoThreadsCount(4);
        server.start();
```

Notes
=========
to build you will need to install libssl-dev

to build 32x version on 64x OS you will need to install libc6-dev-i386 libssl-dev:i386

Download
=========
[bintray]


[epoll]:http://en.wikipedia.org/wiki/Epoll
[bintray]:https://bintray.com/wizzardo/maven/epoll/

Building the project for aarach64 ( mac m1 machines)
If you happen to run the epoll (wizzardo) on a mac m1 machine on docker based on linux images
you will need to build the project on a docker itself. As you can't build the epoll on mac machines
because of the fact that sys/epoll.h is not availble on Mac. 

I have created a dockerfile which can let you do that. 

