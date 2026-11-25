package org.ulpgc.tarantino.common.roles;

/** The states of a role in a running worker, which every {@code role} line carries (SPEC §4.8 and §13.3). */
public enum RoleState {
    STOPPED,
    STARTING,
    ACTIVE,
    STOPPING
}
