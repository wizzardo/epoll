package com.wizzardo.epoll;

import com.wizzardo.tools.io.FileTools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author: wizzardo
 * Date: 3/15/14
 */
class Utils {
    public static int readInt(byte[] b, int offset) {
//        if (b.length - offset < 4 || offset < 0)
//            throw new IndexOutOfBoundsException("byte[].length = " + b.length + ", but offset = " + offset + " and we need 4 bytes");

        return ((b[offset] & 0xff) << 24) + ((b[offset + 1] & 0xff) << 16) + ((b[offset + 2] & 0xff) << 8) + ((b[offset + 3] & 0xff));
    }

    public static int readShort(byte[] b, int offset) {
//        if (b.length - offset < 2 || offset < 0)
//            throw new IndexOutOfBoundsException("byte[].length = " + b.length + ", but offset = " + offset + " and we need 2 bytes");

        return ((b[offset] & 0xff) << 8) + ((b[offset + 1] & 0xff));
    }

    static void loadLib(String name) throws IOException {
        String arch = System.getProperty("os.arch");
        name = name + (arch.contains("64") ? "_x64" : "_x32") + ".so";
        // have to use a stream
        InputStream in = EpollCore.class.getResourceAsStream("/" + name);

        File fileOut;
        if (in == null) {
            File file = new File(name);
            if (file.exists())
                in = new FileInputStream(file);
            else
                in = new FileInputStream(new File("build/" + name));
        }
        fileOut = File.createTempFile(name, "lib");
        FileTools.bytes(fileOut, in);
        System.load(fileOut.toString());
        fileOut.deleteOnExit();
    }
}
