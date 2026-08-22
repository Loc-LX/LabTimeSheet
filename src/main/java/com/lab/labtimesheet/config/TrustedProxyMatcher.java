package com.lab.labtimesheet.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/** Matches a servlet socket address against an operator-provided numeric IPv4/IPv6 network allow-list. */
final class TrustedProxyMatcher {
    private final List<Network> networks;

    private TrustedProxyMatcher(List<Network> networks) {
        this.networks = List.copyOf(networks);
    }

    /**
     * Parses a comma-separated numeric CIDR list without performing DNS lookups.
     *
     * @param value configured proxy networks
     * @return immutable matcher
     * @throws IllegalStateException when the list is absent or malformed
     */
    static TrustedProxyMatcher parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Trusted proxy CIDRs are required");
        }
        List<Network> networks = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(TrustedProxyMatcher::parseNetwork)
                .toList();
        if (networks.isEmpty()) {
            throw new IllegalStateException("Trusted proxy CIDRs are required");
        }
        return new TrustedProxyMatcher(networks);
    }

    /**
     * Tests one raw servlet peer address against the allow-list.
     *
     * @param address raw socket address before forwarded-header adaptation
     * @return whether the peer belongs to a configured network
     */
    boolean matches(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            byte[] candidate = numericAddress(address);
            return networks.stream().anyMatch(network -> network.contains(candidate));
        } catch (IllegalStateException failure) {
            return false;
        }
    }

    private static Network parseNetwork(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length > 2 || parts[0].isBlank()) {
            throw new IllegalStateException("Trusted proxy CIDR is malformed");
        }
        byte[] address = numericAddress(parts[0]);
        int bits = address.length * 8;
        if (parts.length == 2) {
            try {
                bits = Integer.parseInt(parts[1]);
            } catch (NumberFormatException failure) {
                throw new IllegalStateException("Trusted proxy CIDR is malformed", failure);
            }
            if (bits < 0 || bits > address.length * 8) {
                throw new IllegalStateException("Trusted proxy CIDR is malformed");
            }
        }
        return new Network(address, bits);
    }

    private static byte[] numericAddress(String value) {
        if (!value.matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalStateException("Trusted proxy address must be numeric");
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException failure) {
            throw new IllegalStateException("Trusted proxy address is malformed", failure);
        }
    }

    private record Network(byte[] address, int prefixBits) {
        private Network {
            address = address.clone();
        }

        private boolean contains(byte[] candidate) {
            if (candidate.length != address.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            if (!Arrays.equals(Arrays.copyOf(address, fullBytes), Arrays.copyOf(candidate, fullBytes))) {
                return false;
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (candidate[fullBytes] & mask);
        }
    }
}
