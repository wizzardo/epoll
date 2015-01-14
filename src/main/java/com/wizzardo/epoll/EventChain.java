package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 5/27/14
 */
class EventChain<T extends Connection> {
    public boolean isNextBefore(long now) {
        if (first == null)
            return false;

        return first.time < now;
    }

    public T poll() {
        Item<T> i = first;
        first = first.next;
        return i.connection;
    }

    private static class Item<T extends Connection> {
        final T connection;
        final long time;
        Item<T> next;

        Item(T connection, long time) {
            this.connection = connection;
            this.time = time;
        }
    }

    private Item<T> first;
    private Item<T> last;

    public void add(T connection, long time) {
        Item<T> i = new Item<T>(connection, time);
        if (first == null)
            first = i;
        if (last != null)
            last.next = i;
        last = i;
    }
}
