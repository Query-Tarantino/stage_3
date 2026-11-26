package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.PartitionGroupConfig;
import com.hazelcast.spi.properties.ClusterProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.model.BookTask;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastMembersTest {

    private static final String LOOPBACK = "127.0.0.1";
    private static final BookTask TASK = new BookTask(1342, Instant.parse("2026-11-25T10:00:00Z"));
    private static final Duration POLL = Duration.ofSeconds(10);

    private final String cluster = "test-" + UUID.randomUUID();
    private final List<EmbeddedMember> members = new ArrayList<>();

    @AfterEach
    void shutDown() {
        members.forEach(EmbeddedMember::shutdown);
    }

    @Test
    void configuresTheMemberOfAWorkerAsTheSpecSays() throws IOException {
        int port = freePort();
        Config config = HazelcastMembers.config(worker(port, List.of(LOOPBACK + ":" + port, "10.0.0.12:5701"), "n1"));

        assertEquals(cluster, config.getClusterName());
        assertEquals(port, config.getNetworkConfig().getPort());
        assertFalse(config.getNetworkConfig().isPortAutoIncrement());
        assertEquals(LOOPBACK + ":" + port, config.getNetworkConfig().getPublicAddress());
        assertFalse(config.getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
        assertEquals(List.of(LOOPBACK + ":" + port, "10.0.0.12:5701"), config.getNetworkConfig().getJoin().getTcpIpConfig().getMembers());
        assertEquals(PartitionGroupConfig.MemberGroupType.HOST_AWARE, config.getPartitionGroupConfig().getGroupType());
        assertTrue(config.getPartitionGroupConfig().isEnabled());
        assertEquals("7", config.getProperty(ClusterProperty.MAX_NO_HEARTBEAT_SECONDS.getName()));
        assertEquals("1", config.getProperty(ClusterProperty.HEARTBEAT_INTERVAL_SECONDS.getName()));
        assertEquals("false", config.getProperty(ClusterProperty.SHUTDOWNHOOK_ENABLED.getName()));
        assertEquals(1, config.getQueueConfig(MessagingContracts.BOOKS_TO_DOWNLOAD).getBackupCount());
        assertEquals(1, config.getQueueConfig(MessagingContracts.BOOKS_TO_INDEX).getBackupCount());
    }

    @Test
    void twoWorkersStartInTheBackgroundJoinOneClusterAndShareItsQueues() throws Exception {
        int first = freePort();
        int second = freePort();
        List<String> addresses = List.of(LOOPBACK + ":" + first, LOOPBACK + ":" + second);
        EmbeddedMember n1 = started(worker(first, addresses, "n1"));
        EmbeddedMember n2 = started(worker(second, addresses, "n2"));

        Broker offering = n1.broker();
        Broker taking = n2.broker();
        assertTrue(within(() -> offering.connected() && taking.connected()));
        try (BookTasks downloads = offering.queue(MessagingContracts.BOOKS_TO_DOWNLOAD);
             BookTasks received = taking.queue(MessagingContracts.BOOKS_TO_DOWNLOAD)) {
            downloads.offer(TASK);
            assertEquals(Optional.of(TASK), received.poll(POLL));
        }
        n2.shutdown();
        assertTrue(within(() -> !taking.connected()));
    }

    private static boolean within(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(POLL);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofMillis(50));
        }
        return condition.getAsBoolean();
    }

    private EmbeddedMember started(TarantinoConfig worker) {
        EmbeddedMember member = HazelcastMembers.start(worker);
        members.add(member);
        return member;
    }

    private TarantinoConfig worker(int port, List<String> addresses, String node) {
        return TarantinoConfig.from(Map.of(
                "TARANTINO_NODE", node,
                "TARANTINO_ADVERTISE_HOST", LOOPBACK,
                "TARANTINO_HAZELCAST_PORT", String.valueOf(port),
                "TARANTINO_HAZELCAST", String.join(",", addresses),
                "TARANTINO_HAZELCAST_CLUSTER", cluster,
                "TARANTINO_HAZELCAST_FAILURE_SECONDS", "7"), TarantinoService.WORKER);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
