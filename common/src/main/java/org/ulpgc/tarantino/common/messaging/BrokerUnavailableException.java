package org.ulpgc.tarantino.common.messaging;

import java.io.Serial;

/** Hazelcast does not answer; the HTTP handlers turn it into {@code 503} (SPEC §6.6). */
public class BrokerUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
