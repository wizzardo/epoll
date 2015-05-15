package com.wizzardo.epoll;

/**
 * Created by wizzardo on 15.05.15.
 */
public class SslConfig {
    protected final String certFile;
    protected final String keyFile;

    public SslConfig(String certFile, String keyFile) {
        if (certFile == null || certFile.isEmpty())
            throw new IllegalArgumentException("certFile must not be empty");
        if (keyFile == null || keyFile.isEmpty())
            throw new IllegalArgumentException("certFile must not be empty");

        this.certFile = certFile;
        this.keyFile = keyFile;
    }

    public String getCertFile() {
        return certFile;
    }

    public String getKeyFile() {
        return keyFile;
    }
}
