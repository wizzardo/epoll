package com.wizzardo.epoll;


import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author: wizzardo
 * Date: 1/4/14
 */
public class EpollServerTest {

    @Test
    public void startStopTest() throws InterruptedException {
        EpollServer server = new EpollServer() {

            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                return new Connection(fd, ip, port);
            }

            @Override
            public void readyToRead(Connection connection) {
            }

            @Override
            public void readyToWrite(Connection connection) {
            }

            @Override
            public void onOpenConnection(Connection connection) {
            }

            @Override
            public void onCloseConnection(Connection connection) {
            }
        };
        int port = 9091;

        server.bind(port);
        server.start();

        Thread.sleep(500);

        server.stopServer();

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
        EpollServer server = new EpollServer() {

            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                return new Connection(fd, ip, port);
            }

            @Override
            public void readyToRead(Connection connection) {
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

            @Override
            public void readyToWrite(Connection connection) {
            }

            @Override
            public void onOpenConnection(Connection connection) {
            }

            @Override
            public void onCloseConnection(Connection connection) {
            }
        };
        int port = 9090;

        server.bind(port);
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
        server.stopServer();
    }

}
