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