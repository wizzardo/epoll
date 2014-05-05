package com.wizzardo.epoll.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author: moxa
 * Date: 4/13/13
 */
public class ThreadPool {

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();

    public ThreadPool(int threads) {
        this("ThreadPoolWorker", threads);
    }

    public ThreadPool(String name, int threads) {
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            queue.take().run();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            });
            t.setDaemon(true);
            t.setName(name + "-" + i);
            t.start();
        }
    }

    public void add(Runnable r) {
        queue.add(r);
    }

}
