package org.ulpgc.tarantino.common.roles;

import java.util.List;

/** The names of the three roles of a worker, which assignments, logs and the registry use (SPEC §13). */
public final class RoleNames {

    public static final String CRAWLER = "crawler";
    public static final String INDEXER = "indexer";
    public static final String QUERY = "query";
    public static final List<String> ALL = List.of(CRAWLER, INDEXER, QUERY);

    private RoleNames() {
    }
}
