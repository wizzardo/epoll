package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 1/6/14
 */
public class Connection {
    final int fd;
    private int ip, port;
    private String ipString;
    private long lastEvent;

    public Connection(int fd, int ip, int port) {
        this.fd = fd;
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        if (ipString == null)
            ipString = getIp(ip);
        return ipString;
    }

    private String getIp(int ip) {
        StringBuilder sb = new StringBuilder();
        sb.append((ip >> 24) + (ip < 0 ? 256 : 0)).append(".");
        sb.append((ip & 16777215) >> 16).append(".");
        sb.append((ip & 65535) >> 8).append(".");
        sb.append(ip & 255);
        return sb.toString();
    }

    public int getPort() {
        return port;
    }

    void setLastEvent(long lastEvent) {
        this.lastEvent = lastEvent;
    }

    long getLastEvent(){
        return lastEvent;
    }
}
