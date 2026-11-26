package org.ulpgc.tarantino.worker;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;

/**
 * Starts the one deployable of SPEC §2.2, a {@code worker}, with the configuration of its environment (SPEC §4.7). A
 * worker stopped in order stops its roles first; one that is killed leaves its work to the leases.
 */
public class Main {

    public static void main(String[] args) {
        Worker worker = Worker.start(TarantinoConfig.fromEnvironment(TarantinoService.WORKER));
        Runtime.getRuntime().addShutdownHook(new Thread(worker::close));
    }
}
