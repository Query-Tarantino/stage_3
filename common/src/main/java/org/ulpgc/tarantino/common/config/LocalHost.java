package org.ulpgc.tarantino.common.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;

/** The defaults of {@code TARANTINO_NODE} and {@code TARANTINO_ADVERTISE_HOST}: this computer's name and address (SPEC §4.7). */
final class LocalHost {

    private static final String HOSTNAME = "HOSTNAME";

    private LocalHost() {
    }

    /** {@code HOSTNAME}, which Docker sets to the container's name, or else the host name of this computer. */
    static String name(Map<String, String> environment) {
        String hostname = environment.get(HOSTNAME);
        if (hostname != null && !hostname.isBlank()) {
            return hostname.strip();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new InvalidConfigurationException("TARANTINO_NODE", "has no default, because this host has no name");
        }
    }

    /** The first IPv4 address this process's host name resolves to. */
    static String address() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            return Arrays.stream(InetAddress.getAllByName(hostName))
                    .filter(Inet4Address.class::isInstance)
                    .map(InetAddress::getHostAddress)
                    .findFirst()
                    .orElseThrow(() -> new InvalidConfigurationException("TARANTINO_ADVERTISE_HOST", "has no default, because " + hostName + " has no IPv4 address"));
        } catch (UnknownHostException e) {
            throw new InvalidConfigurationException("TARANTINO_ADVERTISE_HOST", "has no default, because this host's name does not resolve");
        }
    }
}
