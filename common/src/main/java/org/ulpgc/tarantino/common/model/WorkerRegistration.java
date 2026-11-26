package org.ulpgc.tarantino.common.model;

import java.util.List;
import java.util.Map;

/**
 * What a worker writes about itself when it registers (SPEC §4.3): where it runs and how other processes reach it, its
 * design, thread counts and commit, and the roles of its first assignment.
 */
public record WorkerRegistration(WorkerIdentity identity, String host, int port, String design, Map<String, Integer> threads,
                                 String commit, List<String> roles) {

    public static final String HTTP_THREADS = "http";
    public static final String DOWNLOAD_THREADS = "downloads";
    public static final String INDEX_THREADS = "index";
}
