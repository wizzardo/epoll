package com.wizzardo.epoll.sized;

import com.wizzardo.epoll.Connection;
import com.wizzardo.tools.io.BlockSizeType;
import com.wizzardo.tools.io.SizedBlockInputStream;
import com.wizzardo.tools.io.SizedBlockOutputStream;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public class SizedDataServerTest {

    @Test
    public void test() throws InterruptedException, IOException {
        SizedDataServer server = new SizedDataServer(2) {
            @Override
            public void handleData(Connection connection, byte[] data) {
                System.out.println(new String(data));
                try {
                    write(connection, new ReadableByteArrayWithSize(new String(data).toUpperCase().getBytes()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        server.bind(8080);
        server.start();

        Socket socket = new Socket("localhost", 8080);
        String string = "some test data";
        byte[] data = string.getBytes();

        SizedBlockOutputStream out = new SizedBlockOutputStream(socket.getOutputStream(), BlockSizeType.INTEGER);
        out.setBlockLength(data.length);
        out.write(data);
        out.flush();

        SizedBlockInputStream in = new SizedBlockInputStream(socket.getInputStream(),BlockSizeType.INTEGER);
        Assert.assertTrue(in.hasNext());
        in.read(data);
        Assert.assertEquals(string.toUpperCase(), new String(data));

//        Thread.sleep(5 * 60 * 1000);
        Thread.sleep(1 * 1000);

        server.stopServer();
    }
}
