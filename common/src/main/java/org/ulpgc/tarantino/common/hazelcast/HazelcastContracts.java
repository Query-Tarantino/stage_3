package org.ulpgc.tarantino.common.hazelcast;

/** The cluster of SPEC §4.2; the names of its queues and topic are in {@code MessagingContracts}. */
public final class HazelcastContracts {

    public static final String DEFAULT_CLUSTER = "tarantino";
    static final String LOGGING_TYPE = "hazelcast.logging.type";
    static final String SLF4J = "slf4j";

    private HazelcastContracts() {
    }
}
