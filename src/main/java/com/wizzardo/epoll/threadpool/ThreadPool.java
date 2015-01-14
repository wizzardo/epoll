package com.wizzardo.epoll.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author: moxa
 * Date: 4/13/13
 */
public class ThreadPool {

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
    private int threadsCount;
    private String threadNamePrefix;

    public ThreadPool(int threads) {
        this("ThreadPoolWorker", threads);
    }

    public ThreadPool(String name, int threads) {
        threadsCount = threads;
        threadNamePrefix = name;
        startThreads(threads, 0);
    }

    private void startThreads(int count, int offset) {
        for (int i = 0; i < count; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            queue.take().run();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            t.setDaemon(true);
            t.setName(threadNamePrefix + "-" + (i + offset));
            t.start();
        }
    }

    public void add(Runnable r) {
        queue.add(r);
    }

    public void increaseThreadsCountTo(int newThreadsCount) {
        if (newThreadsCount < threadsCount)
            throw new IllegalArgumentException("new threads count cannot be smaller than old value: " + newThreadsCount + " < " + threadsCount);
        startThreads(newThreadsCount - threadsCount, threadsCount);
        threadsCount = newThreadsCount;
    }

    public int getThreadsCount() {
        return threadsCount;
    }
}
