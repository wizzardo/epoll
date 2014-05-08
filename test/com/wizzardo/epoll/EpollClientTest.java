package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableByteArray;
import org.junit.Test;

import java.io.IOException;

/**
 * @author: wizzardo
 * Date: 5/5/14
 */
public class EpollClientTest {
    @Test
    public void simpleTest() {
        EpollCore epoll = new EpollCore() {
            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                System.out.println("createConnection: " + fd + " " + ip + " " + port);
                Connection c = new Connection(this, fd, ip, port);
                putConnection(c);
                return c;
            }

            @Override
            public void onRead(Connection connection) {
                System.out.println("onRead " + connection);
                byte[] b = new byte[1024];
                int r = 0;

                try {
                    r = read(connection, b, 0, b.length);
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                System.out.println(new String(b, 0, r));
            }

            @Override
            public void onWrite(Connection connection) {
                System.out.println("onWrite " + connection);
//                stopWriting(connection);
//                try {
//                    write(connection, new ReadableByteArray(("GET /1 HTTP/1.1\r\n" +
//                            "Host: localhost:8082\r\n" +
//                            "\r\n").getBytes()));
//                } catch (IOException e) {
//                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                }
            }

            @Override
            public void onConnect(Connection connection) {
                System.out.println("onConnect " + connection);
            }

            @Override
            public void onDisconnect(Connection connection) {
                System.out.println("onDisconnect " + connection);
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
}
