package org.ulpgc.tarantino.worker;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;

/** Starts the one deployable of SPEC §2.2, a {@code worker}, with the configuration of its environment (SPEC §4.7). */
public class Main {

    public static void main(String[] args) {
        Worker.start(TarantinoConfig.fromEnvironment(TarantinoService.WORKER));
    }
}
