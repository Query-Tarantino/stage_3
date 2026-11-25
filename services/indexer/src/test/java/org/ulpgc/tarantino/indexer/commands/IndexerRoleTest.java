package org.ulpgc.tarantino.indexer.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.roles.RoleState;
import org.ulpgc.tarantino.indexer.ports.IndexQueue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexerRoleTest {

    private static final Instant ENQUEUED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final Duration POLL = Duration.ofMillis(5);
    private static final long WAIT_SECONDS = 5;

    private final FakeQueue queue = new FakeQueue();
    private final List<Thread> threads = new CopyOnWriteArrayList<>();

    @Test
    void startsAndStopsTwiceInOneProcessLeavingNoThreadBehind() throws InterruptedException {
        IndexerRole role = role(books -> {
        });

        for (int round = 0; round < 2; round++) {
            int pollsBefore = queue.polls.get();
            role.start();
            assertEquals(RoleState.ACTIVE, role.state());
            awaitPollsAfter(pollsBefore);
            role.stop();
            assertEquals(RoleState.STOPPED, role.state());
        }

        assertEquals(2, threads.size());
        threads.forEach(thread -> assertFalse(thread.isAlive()));
    }

    @Test
    void stopsOnlyAfterTheBatchItHasOpen() throws InterruptedException {
        CountDownLatch batchStarted = new CountDownLatch(1);
        CountDownLatch finishBatch = new CountDownLatch(1);
        List<Map<Integer, Instant>> indexed = new CopyOnWriteArrayList<>();
        IndexerRole role = role(books -> {
            batchStarted.countDown();
            await(finishBatch);
            indexed.add(books);
        });
        queue.tasks.add(new BookTask(1342, ENQUEUED_AT));
        role.start();
        assertTrue(batchStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));

        Thread stopping = Thread.ofPlatform().start(role::stop);
        stopping.join(Duration.ofMillis(100));
        assertTrue(stopping.isAlive());
        assertEquals(RoleState.STOPPING, role.state());

        finishBatch.countDown();
        stopping.join();
        assertEquals(List.of(Map.of(1342, ENQUEUED_AT)), indexed);
        assertEquals(1, queue.acknowledged.get());
        assertEquals(RoleState.STOPPED, role.state());
    }

    private IndexerRole role(Consumer<Map<Integer, Instant>> index) {
        return new IndexerRole(() -> new IndexWorker(new BatchCollector(queue, 1, POLL, Clock.systemUTC()), index), loop -> {
            Thread thread = Thread.ofPlatform().start(loop);
            threads.add(thread);
            return thread;
        });
    }

    private void awaitPollsAfter(int polls) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(WAIT_SECONDS);
        while (queue.polls.get() <= polls && Instant.now().isBefore(deadline)) {
            Thread.sleep(POLL);
        }
        assertTrue(queue.polls.get() > polls);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class FakeQueue implements IndexQueue {

        private final ConcurrentLinkedQueue<BookTask> tasks = new ConcurrentLinkedQueue<>();
        private final AtomicInteger polls = new AtomicInteger();
        private final AtomicInteger acknowledged = new AtomicInteger();

        @Override
        public Optional<BookTask> next(Duration wait) throws InterruptedException {
            polls.incrementAndGet();
            BookTask task = tasks.poll();
            if (task == null) {
                Thread.sleep(POLL);
            }
            return Optional.ofNullable(task);
        }

        @Override
        public List<BookTask> drain(int max) {
            List<BookTask> drained = new ArrayList<>();
            while (drained.size() < max && !tasks.isEmpty()) {
                drained.add(tasks.poll());
            }
            return drained;
        }

        @Override
        public void acknowledge() {
            acknowledged.incrementAndGet();
        }
    }
}
