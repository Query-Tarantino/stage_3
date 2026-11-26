package org.ulpgc.tarantino.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.WorkerRegistration;
import org.ulpgc.tarantino.common.mongo.WorkerRegistry;
import org.ulpgc.tarantino.common.roles.Role;
import org.ulpgc.tarantino.common.roles.RoleNames;
import org.ulpgc.tarantino.common.roles.RoleState;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * One “Beat as a worker” (SPEC §5.1 and §13.2): it reports the roles that are {@code ACTIVE}, and registers the worker
 * again if its document is gone. A beat that fails is tried again on the next one.
 */
final class RoleHeartbeat implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleHeartbeat.class);

    private final WorkerRegistry registry;
    private final WorkerRegistration registration;
    private final Collection<Role> roles;

    RoleHeartbeat(WorkerRegistry registry, WorkerRegistration registration, Collection<Role> roles) {
        this.registry = registry;
        this.registration = registration;
        this.roles = roles;
    }

    @Override
    public void run() {
        try {
            if (registry.beat(registration.identity().workerId(), activeRoles()).isEmpty()) {
                registry.register(registration);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Could not beat as worker {}: {}", registration.identity().workerId(), e.getMessage());
        }
    }

    private List<String> activeRoles() {
        return roles.stream()
                .filter(role -> role.state() == RoleState.ACTIVE)
                .map(Role::name)
                .sorted(Comparator.comparingInt(RoleNames.ALL::indexOf))
                .toList();
    }
}
