package com.wizzardo.epoll;


import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: wizzardo
 * Date: 1/4/14
 */
public class EpollServerTest {

    @Test
    public void startStopTest() throws InterruptedException {
        int port = 9091;
        EpollServer server = new EpollServer(port) {
        };

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
            public void onRead(Connection connection) {
                try {
                    byte[] b = new byte[1024];
                    int r = read(connection, b, 0, b.length);
                    int w = 0;
                    while (w < r) {
                        w += write(connection, b, w, r - w);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
        }
        server.stopEpoll();
    }

    @Test
    public void httpTest() throws InterruptedException {
        int port = 8084;
        EpollServer server = new EpollServer(port) {

            byte[] response = "HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: 5\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nololo".getBytes();
//            byte[] response = "HTTP/1.1 200 OK\r\nConnection: Close\r\nContent-Length: 5\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nololo".getBytes();

            @Override
            public void onRead(Connection connection) {
                try {
                    byte[] b = new byte[1024];
                    int r = read(connection, b, 0, b.length);
//                    System.out.println(new String(b,0,r));
                       connection.write(response);
//                    close(connection);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        server.start();

        Thread.sleep(25 * 60 * 1000);

        server.stopEpoll();
    }

    @Test
    public void maxEventsTest() throws InterruptedException {
        final int port = 9092;
        EpollServer server = new EpollServer(null, port, 2) {

            @Override
            public void onRead(Connection connection) {
                try {
                    byte[] b = new byte[1024];
                    int r = read(connection, b, 0, b.length);
                    int w = 0;
                    while (w < r) {
                        w += write(connection, b, w, r - w);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        server.start();

        final AtomicLong total = new AtomicLong(0);
        long time = System.currentTimeMillis();

        int threads = 512;
        final int n = 1000;
        final CountDownLatch latch = new CountDownLatch(threads);


        for (int j = 0; j < threads; j++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket s = new Socket("localhost", port);
                        OutputStream out = s.getOutputStream();
                        InputStream in = s.getInputStream();
                        byte[] b = new byte[1024];
                        for (int i = 0; i < n; i++) {
                            out.write("hello world!".getBytes());

                            int r = in.read(b);
                            total.addAndGet(r);

                            Assert.assertEquals("hello world!", new String(b, 0, r));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }
            }).start();

        }
        latch.await();
        System.out.println("total bytes were sent: " + total.get() * 2);
        time = System.currentTimeMillis() - time;
        System.out.println("for " + time + "ms");
        System.out.println(total.get() * 1000.0 / time / 1024.0 / 1024.0);
        server.stopEpoll();
    }

    @Test
    public void hostBindTest() throws InterruptedException {
        int port = 9090;
//        String host = "192.168.0.131";
        String host = "192.168.1.144";
        EpollServer server = new EpollServer(host, port) {

            @Override
            public void onRead(Connection connection) {
                try {
                    byte[] b = new byte[1024];
                    int r = read(connection, b, 0, b.length);
                    int w = 0;
                    while (w < r) {
                        w += write(connection, b, w, r - w);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
}
