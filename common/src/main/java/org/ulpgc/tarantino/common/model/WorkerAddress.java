package org.ulpgc.tarantino.common.model;

/** An alive worker of the registry and the address of its HTTP port (SPEC §4.3). */
public record WorkerAddress(String workerId, String node, String host, int port) {
}
