package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.PartitionGroupConfig;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.properties.ClusterProperty;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hazelcast member embedded in every worker (SPEC §4.2): it joins the members of {@code TARANTINO_HAZELCAST} over
 * TCP/IP without multicast, announces {@code TARANTINO_ADVERTISE_HOST} and {@code TARANTINO_HAZELCAST_PORT}, keeps one
 * synchronous backup of each queue on another node, and is removed from the cluster after
 * {@code TARANTINO_HAZELCAST_FAILURE_SECONDS} without a heartbeat. Heartbeats go every second, so that many seconds are
 * that many missed heartbeats.
 */
public final class HazelcastMembers {

    private static final String LOGGING = "hazelcast.logging.type";
    private static final String SLF4J = "slf4j";
    private static final String HEARTBEAT_INTERVAL_SECONDS = "1";
    private static final int QUEUE_BACKUPS = 1;
    private static final List<String> QUEUES = List.of(MessagingContracts.BOOKS_TO_DOWNLOAD, MessagingContracts.BOOKS_TO_INDEX);

    private HazelcastMembers() {
    }

    public static HazelcastInstance start(TarantinoConfig tarantino) {
        return Hazelcast.newHazelcastInstance(config(tarantino));
    }

    /** The queues and the topic through this member, which is connected while it runs. */
    public static Broker broker(HazelcastInstance member) {
        return new HazelcastBroker(member, () -> member.getLifecycleService().isRunning());
    }

    static Config config(TarantinoConfig tarantino) {
        Config config = new Config().setClusterName(tarantino.hazelcastCluster())
                .setProperty(LOGGING, SLF4J)
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
