package org.ulpgc.tarantino.common.model;

/** Who did a piece of work: the worker, {@code <node>-w<k>}, and the node it runs on (SPEC §4.8 and §13.1). */
public record WorkerIdentity(String workerId, String node) {
}
