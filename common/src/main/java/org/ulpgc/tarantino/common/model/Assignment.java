package org.ulpgc.tarantino.common.model;

import java.util.List;

/** The roles a worker must run and how many assignments it has had, as its heartbeat reads them (SPEC §13.2). */
public record Assignment(List<String> roles, int number) {
}
