package org.ulpgc.tarantino.indexer.commands;

import org.ulpgc.tarantino.common.roles.Role;
import org.ulpgc.tarantino.common.roles.RoleNames;
import org.ulpgc.tarantino.common.roles.RoleState;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The role {@code indexer} (SPEC §13.3): {@code start()} starts a new batch loop on its own thread, and {@code stop()}
 * lets it finish the batch it has open and waits for its thread to end. It can start again in the same process.
 */
public class IndexerRole implements Role {

    private final Supplier<IndexWorker> workers;
    private final Function<Runnable, Thread> threads;
    private volatile RoleState state = RoleState.STOPPED;
    private IndexWorker worker;
    private Thread thread;

    /** {@code workers} gives a new batch loop for each start, and {@code threads} starts it on its own thread. */
    public IndexerRole(Supplier<IndexWorker> workers, Function<Runnable, Thread> threads) {
        this.workers = workers;
        this.threads = threads;
    }

    @Override
    public String name() {
        return RoleNames.INDEXER;
    }

    @Override
    public synchronized void start() {
        if (state != RoleState.STOPPED) {
            return;
        }
        state = RoleState.STARTING;
        worker = workers.get();
        thread = threads.apply(worker);
        state = RoleState.ACTIVE;
    }

    @Override
    public synchronized void stop() {
        if (state != RoleState.ACTIVE) {
            return;
        }
        state = RoleState.STOPPING;
        worker.stop();
        awaitEnd(thread);
        state = RoleState.STOPPED;
    }

    @Override
    public RoleState state() {
        return state;
    }

    private static void awaitEnd(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
