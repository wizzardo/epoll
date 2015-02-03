package com.wizzardo.epoll;


import com.wizzardo.epoll.threadpool.ThreadPool;
import com.wizzardo.tools.http.HttpClient;
import com.wizzardo.tools.misc.Stopwatch;
import com.wizzardo.tools.security.MD5;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Enumeration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author: wizzardo
 * Date: 1/4/14
 */
public class EpollServerTest {

    @Test
    public void startStopTest() throws InterruptedException {
        int port = 9091;
        EpollServer server = new EpollServer(port);

        server.start();

        Thread.sleep(500);

        server.stopEpoll();

        Thread.sleep(510);

        String connectionRefuse = null;
        try {
            new Socket("localhost", port);
        } catch (IOException e) {
            connectionRefuse = e.getMessage();
        }
        Assert.assertEquals("Connection refused", connectionRefuse);
    }

    @Test
    public void echoTest() throws InterruptedException {
        int port = 9090;
        EpollServer server = new EpollServer(port) {
            @Override
            protected IOThread createIOThread(int number, int divider) {
                return new IOThread(number, divider) {
                    @Override
                    public void onRead(Connection connection) {
                        try {
                            byte[] b = new byte[1024];
                            int r = connection.read(b, 0, b.length, this);
                            connection.write(b, 0, r, this);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
            }
        };

        server.start();

        try {
            Socket s = new Socket("localhost", port);
            OutputStream out = s.getOutputStream();
            out.write("hello world!".getBytes());

            InputStream in = s.getInputStream();
            byte[] b = new byte[1024];
            int r = in.read(b);

            Assert.assertEquals("hello world!", new String(b, 0, r));
        } catch (IOException e) {
            e.printStackTrace();
            assert e == null;
        }
        server.stopEpoll();
    }

    static class BufferedConnection extends Connection {
        final byte[] buffer = new byte[128];
        volatile int count;

        public BufferedConnection(int fd, int ip, int port) {
            super(fd, ip, port);
        }
    }

//    @Test
    public void httpTest() throws InterruptedException {
        int port = 8084;
        final ThreadPool pool = new ThreadPool(2);
        EpollServer<BufferedConnection> server = new EpollServer<BufferedConnection>(port) {

            @Override
            protected BufferedConnection createConnection(int fd, int ip, int port) {
                return new BufferedConnection(fd, ip, port);
            }

            final byte[] data = "HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: 5\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nololo".getBytes();
//                        byte[] response = "HTTP/1.1 200 OK\r\nConnection: Close\r\nContent-Length: 5\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nololo".getBytes();
//            ReadableByteBuffer response = new ReadableByteBuffer(new ByteBufferWrapper(data));

            @Override
            protected IOThread<BufferedConnection> createIOThread(int number, int divider) {
                return new IOThread<BufferedConnection>(number, divider) {

                    @Override
                    public void onRead(final BufferedConnection connection) {
//                        try {
//                            int r = connection.read(b, 0, b.length, this);
//                            System.out.println("read: " + r);
//                            System.out.println(new String(b, 0, r));
//                            connection.write(response.copy());
                        final IOThread that = this;
                        pool.add(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    connection.count += connection.read(connection.buffer, connection.count, connection.buffer.length - connection.count, that);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                if (connection.count == 40) {
                                    connection.count = 0;
                                    connection.write(data, that);
                                }
                            }
                        });
                    }
                };
            }
        };
        server.setIoThreadsCount(1);

        server.start();

        Thread.sleep(25 * 60 * 1000);

        server.stopEpoll();
    }

    @Test
    public void maxEventsTest() throws InterruptedException {
        final int port = 9092;
        final AtomicInteger connections = new AtomicInteger();
        EpollServer server = new EpollServer(null, port, 200) {

            @Override
            protected IOThread createIOThread(int number, int divider) {
                return new IOThread(number, divider) {

                    @Override
                    public void onRead(Connection connection) {
                        try {
                            byte[] b = new byte[32];
                            int r = connection.read(b, 0, b.length, this);
//                            System.out.println("read: " + new String(b, 0, r));
                            connection.write(b, 0, r, this);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConnect(Connection connection) {
                        System.out.println("onConnect " + connections.incrementAndGet());
                    }

                    @Override
                    public void onDisconnect(Connection connection) {
                        System.out.println("onDisconnect " + connections.decrementAndGet());
                    }
                };
            }
        };
        server.setIoThreadsCount(1);
        server.start();

        final AtomicLong total = new AtomicLong(0);
        long time = System.currentTimeMillis();

        int threads = 100;
        final int n = 10000;
        final CountDownLatch latch = new CountDownLatch(threads);
        final AtomicInteger counter = new AtomicInteger();

        for (int j = 0; j < threads; j++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket s = new Socket("localhost", port);
                        OutputStream out = s.getOutputStream();
                        InputStream in = s.getInputStream();
                        byte[] b = new byte[1024];
                        byte[] hello = "hello world!".getBytes();
                        for (int i = 0; i < n; i++) {
//                            System.out.println("write");
                            out.write(hello);
                            out.flush();
//                            System.out.println("wait for response");

                            int r = in.read(b);
                            total.addAndGet(r);
//                            System.out.println("get response: " + new String(b, 0, r));

                            Assert.assertEquals("hello world!", new String(b, 0, r));
                        }
                        s.close();
                        counter.incrementAndGet();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }
            }).start();
            Thread.sleep(10);

        }
        latch.await();
        Assert.assertEquals(threads, counter.get());
        Assert.assertEquals(0, connections.get());
        System.out.println("total bytes were sent: " + total.get() * 2);
        time = System.currentTimeMillis() - time;
        System.out.println("for " + time + "ms");
        System.out.println(total.get() * 1000.0 / time / 1024.0 / 1024.0);
        server.stopEpoll();
    }

    private String getLocalIp() throws UnknownHostException, SocketException {
        System.out.println("Your Host addr: " + InetAddress.getLocalHost().getHostAddress());  // often returns "127.0.0.1"
        Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
        for (; n.hasMoreElements(); ) {
            NetworkInterface e = n.nextElement();

            Enumeration<InetAddress> a = e.getInetAddresses();
            for (; a.hasMoreElements(); ) {
                InetAddress addr = a.nextElement();
                if (addr.getAddress().length == 4 && !addr.getHostAddress().startsWith("127"))
                    return addr.getHostAddress();
            }
        }
        return null;
    }

    @Test
    public void hostBindTest() throws InterruptedException, UnknownHostException, SocketException {
        int port = 9090;
        String host = getLocalIp();
//        String host = "192.168.1.144";
        EpollServer server = new EpollServer(host, port) {
            @Override
            protected IOThread createIOThread(int number, int divider) {
                return new IOThread(number, divider) {

                    @Override
                    public void onRead(Connection connection) {
                        try {
                            byte[] b = new byte[1024];
                            int r = connection.read(b, 0, b.length, this);
                            connection.write(b, 0, r, this);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
            }
        };

        server.start();

        String message = null;
        try {
            new Socket("localhost", port);
        } catch (IOException e) {
            message = e.getMessage();
        }
        Assert.assertEquals("Connection refused", message);

        try {
            Socket s = new Socket(host, port);
            OutputStream out = s.getOutputStream();
            out.write("hello world!".getBytes());

            InputStream in = s.getInputStream();
            byte[] b = new byte[1024];
            int r = in.read(b);

            Assert.assertEquals("hello world!", new String(b, 0, r));
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.stopEpoll();
    }

    @Test
    public void testWriteEvents() throws IOException, InterruptedException {
        int port = 9090;
        String host = "localhost";

        final byte[] data = new byte[10 * 1024 * 1024];
        new Random().nextBytes(data);
        String md5 = MD5.getMD5AsString(data);

        EpollServer server = new EpollServer(host, port) {
            @Override
            protected IOThread createIOThread(int number, int divider) {
                return new IOThread(number, divider) {

                    @Override
                    public void onConnect(Connection connection) {
                        connection.write(data, this);
                    }
                };
            }

            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                return new Connection(fd, ip, port);
            }
        };

        server.start();

        byte[] receive = new byte[10 * 1024 * 1024];
        int offset = 0;
        int r;
        Socket socket = new Socket(host, port);
        InputStream in = socket.getInputStream();
        Thread.sleep(1000);
        while ((r = in.read(receive, offset, receive.length - offset)) != -1) {
            offset += r;
//            System.out.println("read: "+r+"\tremaining: "+(receive.length - offset));

            if (receive.length - offset == 0)
                break;
        }
        Assert.assertEquals(md5, MD5.getMD5AsString(receive));
        Assert.assertEquals(0, in.available());
        socket.close();
        server.stopEpoll();
    }

    @Test
    public void testConnects() {
        int port = 9090;
        EpollServer server = new EpollServer(port) {

            byte[] data = "HTTP/1.1 200 OK\r\nConnection: Close\r\nContent-Length: 2\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nok".getBytes();

            @Override
            protected IOThread createIOThread(int number, int divider) {
                return new IOThread(number, divider) {

                    byte[] b = new byte[1024];

                    @Override
                    public void onRead(Connection connection) {
                        try {
                            int r = connection.read(b, 0, b.length, this);
                            System.out.println(new String(b, 0, r));
//                            connection.write(response.copy());
                            connection.write(data, this);
                            System.out.println("write response");
                            connection.close();
                            System.out.println("close");
                        } catch (IOException e) {
                            e.printStackTrace();
                            assert e == null;
                        }
                    }
                };
            }
        };
        server.setIoThreadsCount(4);
        server.start();


        int i = 0;
        int n = 10000;
        Stopwatch stopwatch = new Stopwatch("time");
        try {
            while (true) {
//                Assert.assertEquals("ok", HttpClient.createRequest("http://localhost:8080")
                Assert.assertEquals("ok", HttpClient.createRequest("http://localhost:9090")
                        .header("Connection", "Close")
                        .get().asString());
                i++;
                if (i == n)
                    break;
            }
        } catch (Exception e) {
            System.out.println(i);
            e.printStackTrace();
        }
        server.stopEpoll();

        assert i == n;
        System.out.println(stopwatch);

    }

    @Test
    public void testAsyncWriteEvent() {
        int port = 9090;
        final AtomicReference<Connection> connectionRef = new AtomicReference<Connection>();
        final AtomicInteger onWrite = new AtomicInteger();
        EpollServer server = new EpollServer(port) {
            @Override
            protected IOThread createIOThread(int number, int divider) {
                return new IOThread(number, divider) {
                    @Override
                    public void onConnect(Connection connection) {
                        connectionRef.set(connection);
                    }

                    @Override
                    public void onWrite(Connection connection) {
                        onWrite.incrementAndGet();
                        super.onWrite(connection);
                    }
                };
            }
        };

        server.start();

        try {
            int pause = 20;
            Socket s = new Socket("localhost", port);
            Thread.sleep(pause);
            Assert.assertNotNull(connectionRef.get());
            connectionRef.get().enableOnWriteEvent();
            Thread.sleep(pause);
            Assert.assertEquals(1, onWrite.get());

            connectionRef.get().disableOnWriteEvent();
            Thread.sleep(pause);
            connectionRef.get().enableOnWriteEvent();
            Thread.sleep(pause);
            Assert.assertEquals(2, onWrite.get());

        } catch (IOException e) {
            e.printStackTrace();
            assert e == null;
        } catch (InterruptedException e) {
            e.printStackTrace();
            assert e == null;
        }
        server.stopEpoll();
    }
}
