package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.client.HazelcastClientOfflineException;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.startup.WorkerThreads;

import java.util.concurrent.CompletableFuture;

/**
 * The Hazelcast member of a worker, which starts on its own thread so the worker opens its HTTP port first (SPEC §2.4).
 * Until it runs, its broker is not connected and its queues throw, so {@code /health} and the routes answer {@code 503};
 * if it cannot start, the worker exits.
 */
public final class EmbeddedMember {

    private static final String STARTING_THREAD = "hazelcast-member";

    private final CompletableFuture<HazelcastInstance> member = new CompletableFuture<>();

    private EmbeddedMember() {
    }

    static EmbeddedMember start(Config config) {
        EmbeddedMember embedded = new EmbeddedMember();
        WorkerThreads.start(STARTING_THREAD, () -> embedded.member.complete(Hazelcast.newHazelcastInstance(config)));
        return embedded;
    }

    public Broker broker() {
        return new HazelcastBroker(this::instance, this::running);
    }

    /** Leaves the cluster now if the member runs, or as soon as it has started. */
    public void shutdown() {
        member.thenAccept(HazelcastInstance::shutdown);
    }

    private HazelcastInstance instance() {
        HazelcastInstance started = member.getNow(null);
        if (started == null) {
            throw new HazelcastClientOfflineException();
        }
        return started;
    }

    private boolean running() {
        HazelcastInstance started = member.getNow(null);
        return started != null && started.getLifecycleService().isRunning();
    }
}
