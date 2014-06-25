package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableByteArray;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: wizzardo
 * Date: 5/5/14
 */
public class EpollClientTest {
    @Test
    public void simpleTest() throws UnknownHostException {
        EpollCore epoll = new EpollCore<Connection>() {

            @Override
            public void onRead(Connection connection) {
                System.out.println("onRead " + connection);
                byte[] b = new byte[1024];
                int r = 0;

                try {
                    r = connection.read(b, 0, b.length);
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                System.out.println(new String(b, 0, r));
            }
        };

        epoll.start();
        Connection connection = epoll.connect("localhost", 8082);
        try {
            epoll.write(connection, new ReadableByteArray(("GET /1 HTTP/1.1\r\n" +
                    "Host: localhost:8082\r\n" +
                    "Connection: Close\r\n" +
                    "\r\n").getBytes()));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    static class BenchmarkConnection extends Connection {
        int read = 0;

        public BenchmarkConnection(int fd, int ip, int port) {
            super(fd, ip, port);
        }
    }

    //    @Test
    public void benchamrk() throws IOException, InterruptedException {
//        final byte[] request = ("GET /grails2_4/ HTTP/1.1\r\n" +
        final byte[] request = ("GET / HTTP/1.1\r\n" +
                "Host: localhost:8084\r\n" +
                "Connection: Keep-Alive\r\n" +
//                "Connection: Close\r\n" +
                "\r\n").getBytes();
        final AtomicInteger counter = new AtomicInteger();

        final byte[] b = new byte[1024];
        final EpollCore<BenchmarkConnection> epoll = new EpollCore<BenchmarkConnection>() {
            @Override
            protected BenchmarkConnection createConnection(int fd, int ip, int port) {
                return new BenchmarkConnection(fd, ip, port);
            }

            @Override
            public void onRead(BenchmarkConnection connection) {
//                System.out.println("onRead " + connection);
                int r = 0;

                try {
                    r = connection.read(b, 0, b.length);
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
//                System.out.println(new String(b, 0, r));
//                System.out.println("length: "+r);
                connection.read += r;
//                if (connection.read >= 170) {
                if (connection.read >= 113) {
                    counter.incrementAndGet();
                    connection.read = 0;
                    connection.write(request);
//                    connection.epoll.close(connection);
                }

            }
        };

        epoll.start();
        int c = 64;
        for (int i = 0; i < c; i++) {
            Connection connection = epoll.connect("localhost", 8084);
            connection.write(request);
        }
//        for (int i = 0; i < c; i++) {
//            Thread t = new Thread() {
//                @Override
//                public void run() {
//                    while (true) {
//                        try {
//                            Connection connection = epoll.connect("localhost", 8084);
//                            connection.write(request);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            };
//            t.setDaemon(true);
//            t.start();
//        }

        int time = 10;
        Thread.sleep(time * 1000);
        System.out.println("total requests: " + counter.get());
        System.out.println("rps: " + counter.get() * 1f / time);
    }
}
