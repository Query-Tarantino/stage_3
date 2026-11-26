package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.PartitionGroupConfig;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.spi.properties.ClusterProperty;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hazelcast member embedded in every worker (SPEC §4.2): it joins the members of {@code TARANTINO_HAZELCAST} over
 * TCP/IP without multicast, announces {@code TARANTINO_ADVERTISE_HOST} and {@code TARANTINO_HAZELCAST_PORT}, keeps one
 * synchronous backup of each queue on another node, and is removed from the cluster after
 * {@code TARANTINO_HAZELCAST_FAILURE_SECONDS} without a heartbeat. Heartbeats go every second, so that many seconds are
 * that many missed heartbeats. Hazelcast's own shutdown hook is off: the worker's hook stops the roles first, so the
 * indexer can finish the batch it is writing.
 */
public final class HazelcastMembers {

    private static final String HEARTBEAT_INTERVAL_SECONDS = "1";
    private static final int QUEUE_BACKUPS = 1;
    private static final List<String> QUEUES = List.of(MessagingContracts.BOOKS_TO_DOWNLOAD, MessagingContracts.BOOKS_TO_INDEX);

    private HazelcastMembers() {
    }

    /** Starts this worker's member in the background (SPEC §2.4). */
    public static EmbeddedMember start(TarantinoConfig tarantino) {
        return EmbeddedMember.start(config(tarantino));
    }

    static Config config(TarantinoConfig tarantino) {
        Config config = new Config().setClusterName(tarantino.hazelcastCluster())
                .setProperty(HazelcastContracts.LOGGING_TYPE, HazelcastContracts.SLF4J)
                .setProperty(ClusterProperty.SHUTDOWNHOOK_ENABLED.getName(), Boolean.FALSE.toString())
                .setProperty(ClusterProperty.HEARTBEAT_INTERVAL_SECONDS.getName(), HEARTBEAT_INTERVAL_SECONDS)
                .setProperty(ClusterProperty.MAX_NO_HEARTBEAT_SECONDS.getName(), String.valueOf(tarantino.hazelcastFailure().toSeconds()));
        config.getNetworkConfig()
                .setPort(tarantino.hazelcastPort())
                .setPortAutoIncrement(false)
                .setPublicAddress(tarantino.advertisedHost() + ":" + tarantino.hazelcastPort());
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).setMembers(new ArrayList<>(tarantino.hazelcastMembers()));
        config.getPartitionGroupConfig().setEnabled(true).setGroupType(PartitionGroupConfig.MemberGroupType.HOST_AWARE);
        QUEUES.forEach(queue -> config.addQueueConfig(new QueueConfig(queue).setBackupCount(QUEUE_BACKUPS)));
        return config;
    }
}
