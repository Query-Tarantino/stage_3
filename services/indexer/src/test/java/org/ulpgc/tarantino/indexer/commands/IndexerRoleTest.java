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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexerRoleTest {

    private static final Instant ENQUEUED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final Duration POLL = Duration.ofMillis(5);
    private static final Duration WAIT = Duration.ofSeconds(5);

    private final List<FakeQueue> queues = new CopyOnWriteArrayList<>();
    private final List<Thread> threads = new CopyOnWriteArrayList<>();

    @Test
    void startsAndStopsTwiceInOneProcessWithANewQueueHandleEachTime() {
        IndexerRole role = role(books -> {
        });

        for (int life = 0; life < 2; life++) {
            role.start();
            assertEquals(RoleState.ACTIVE, role.state());
            FakeQueue queue = queues.getLast();
            assertTrue(within(() -> queue.polls.get() > 0));
            role.stop();
            assertTrue(within(() -> role.state() == RoleState.STOPPED));
        }

        assertEquals(2, queues.size());
        queues.forEach(queue -> assertTrue(queue.closed.get()));
        threads.forEach(thread -> assertTrue(within(() -> !thread.isAlive())));
    }

    @Test
    void stopReturnsAtOnceAndTheRoleStopsAfterTheBatchItHasOpen() throws InterruptedException {
        CountDownLatch batchStarted = new CountDownLatch(1);
        CountDownLatch finishBatch = new CountDownLatch(1);
        List<Map<Integer, Instant>> indexed = new CopyOnWriteArrayList<>();
        IndexerRole role = role(books -> {
            batchStarted.countDown();
            await(finishBatch);
            indexed.add(books);
        }, new BookTask(1342, ENQUEUED_AT));
        role.start();
        assertTrue(batchStarted.await(WAIT.toSeconds(), TimeUnit.SECONDS));

        role.stop();
        assertEquals(RoleState.STOPPING, role.state());
        role.start();
        assertEquals(RoleState.STOPPING, role.state());

        finishBatch.countDown();
        assertTrue(within(() -> role.state() == RoleState.STOPPED));
        assertEquals(List.of(Map.of(1342, ENQUEUED_AT)), indexed);
        assertEquals(1, queues.getFirst().acknowledged.get());
        assertEquals(1, queues.size());
    }

    @Test
    void staysStoppedWhenItsBatchLoopCannotBeBuilt() {
        IndexerRole role = new IndexerRole(() -> {
            throw new IllegalStateException("No stopwords.txt");
        }, Thread.ofPlatform()::start);

        assertThrows(IllegalStateException.class, role::start);
        assertEquals(RoleState.STOPPED, role.state());
    }

    private IndexerRole role(Consumer<Map<Integer, Instant>> index, BookTask... tasks) {
        return new IndexerRole(() -> {
            FakeQueue queue = new FakeQueue(queues.isEmpty() ? List.of(tasks) : List.of());
            queues.add(queue);
            return new IndexWorker(new BatchCollector(queue, 1, POLL, Clock.systemUTC()), index);
        }, loop -> {
            Thread thread = Thread.ofPlatform().start(loop);
            threads.add(thread);
            return thread;
        });
    }

    private static boolean within(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(WAIT);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            pause();
        }
        return condition.getAsBoolean();
    }

    private static void pause() {
        try {
            Thread.sleep(POLL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class FakeQueue implements IndexQueue {

        private final ConcurrentLinkedQueue<BookTask> tasks;
        private final AtomicInteger polls = new AtomicInteger();
        private final AtomicInteger acknowledged = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        private FakeQueue(List<BookTask> tasks) {
            this.tasks = new ConcurrentLinkedQueue<>(tasks);
        }

        @Override
        public Optional<BookTask> next(Duration wait) throws InterruptedException {
            assertFalse(closed.get());
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

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
