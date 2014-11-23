package com.wizzardo.epoll.sized;

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
        int port = 8080;
        SizedDataServer server = new SizedDataServer(port) {
            @Override
            public void handleData(SizedDataServerConnection connection, byte[] data) {
                System.out.println(new String(data));
                connection.write(new ReadableByteArrayWithSize(new String(data).toUpperCase().getBytes()), this);
            }
        };
        server.start();

        Socket socket = new Socket("localhost", port);
        String string = "some test data";
        byte[] data = string.getBytes();

        SizedBlockOutputStream out = new SizedBlockOutputStream(socket.getOutputStream(), BlockSizeType.INTEGER);
        out.setBlockLength(data.length);
        out.write(data);
        out.flush();

        SizedBlockInputStream in = new SizedBlockInputStream(socket.getInputStream(), BlockSizeType.INTEGER);
        Assert.assertTrue(in.hasNext());
        in.read(data);
        Assert.assertEquals(string.toUpperCase(), new String(data));

//        Thread.sleep(5 * 60 * 1000);
        Thread.sleep(1 * 1000);

        server.stopEpoll();
    }
}
