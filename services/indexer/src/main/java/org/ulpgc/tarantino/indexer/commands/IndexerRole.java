package org.ulpgc.tarantino.indexer.commands;

import org.ulpgc.tarantino.common.roles.Role;
import org.ulpgc.tarantino.common.roles.RoleNames;
import org.ulpgc.tarantino.common.roles.RoleState;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The role {@code indexer} (SPEC §13.3): {@code start()} starts a new batch loop on its own thread, and {@code stop()}
 * asks it to finish the batch it has open; the loop's thread makes the role {@code STOPPED} when it returns, and only
 * then can the role start again.
 */
public class IndexerRole implements Role {

    private final Supplier<IndexWorker> workers;
    private final Function<Runnable, Thread> threads;
    private volatile RoleState state = RoleState.STOPPED;
    private IndexWorker worker;

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
        try {
            IndexWorker life = workers.get();
            worker = life;
            state = RoleState.ACTIVE;
            threads.apply(() -> runUntilStopped(life));
        } catch (RuntimeException e) {
            state = RoleState.STOPPED;
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        if (state != RoleState.ACTIVE) {
            return;
        }
        state = RoleState.STOPPING;
        worker.stop();
    }

    @Override
    public RoleState state() {
        return state;
    }

    private void runUntilStopped(IndexWorker life) {
        try {
            life.run();
        } finally {
            state = RoleState.STOPPED;
        }
    }
}
