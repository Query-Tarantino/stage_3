package org.ulpgc.tarantino.common.roles;

/**
 * A role a worker starts and stops by assignment, without a restart (SPEC §13.3). {@code start()} and {@code stop()}
 * return at once: the role does its slow part on its own thread, and {@code state()} reports {@code STARTING} or
 * {@code STOPPING} until it is done. They may be called again on the same instance, and once the role is
 * {@code STOPPED} no thread of it is left.
 */
public interface Role {

    /** {@code crawler}, {@code indexer} or {@code query}. */
    String name();

    void start();

    void stop();

    RoleState state();
}
