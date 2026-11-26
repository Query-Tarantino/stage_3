package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.ulpgc.tarantino.common.http.Health;
import org.ulpgc.tarantino.common.messaging.BatchAnnouncements;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The queues and the topic of SPEC §4.2 in Hazelcast. Each queue and the topic are looked up on each use, so it can be
 * built before the client connects or the member starts.
 */
public final class HazelcastBroker implements Broker {

    private final Supplier<HazelcastInstance> instance;
    private final BooleanSupplier connected;

    /** {@code instance} gives the client or member on each use, and throws while there is none yet. */
    public HazelcastBroker(Supplier<HazelcastInstance> instance, BooleanSupplier connected) {
        this.instance = instance;
        this.connected = connected;
    }

    public static HazelcastBroker of(HazelcastConnection connection) {
        return new HazelcastBroker(connection::client, connection::connected);
    }

    @Override
    public BookTasks queue(String name) {
        return new BookTaskQueue(instance.get(), name);
    }

    @Override
    public BatchAnnouncements announcements() {
        return new BatchPublishedTopic(instance.get());
    }

    @Override
    public int size(String queue) {
        return instance.get().getQueue(queue).size();
    }

    @Override
    public boolean connected() {
        return connected.getAsBoolean();
    }

    @Override
    public String name() {
        return Health.HAZELCAST;
    }

    @Override
    public void close() {
        instance.get().shutdown();
    }
}
