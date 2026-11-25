package org.ulpgc.tarantino.common.roles;

/**
 * A role a worker starts and stops by assignment, without a restart (SPEC §13.3). {@code start()} and {@code stop()}
 * may be called again on the same instance, and {@code stop()} leaves no thread of the role behind.
 */
public interface Role {

    /** {@code crawler}, {@code indexer} or {@code query}. */
    String name();

    void start();

    void stop();

    RoleState state();
}
