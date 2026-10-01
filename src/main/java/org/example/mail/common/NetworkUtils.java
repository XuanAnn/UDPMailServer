package org.example.mail.common;

import java.net.*;
import java.util.*;

public final class NetworkUtils {

    private NetworkUtils() {}

    /**
     * Gets the primary LAN IPv4 address of this machine.
     * Uses OS routing table lookup via unconnected/connected UDP probe (no packets sent),
     * followed by interface enumeration and local host fallback.
     */
    public static String getLocalIPv4Address() {
        // 1. Try OS routing table lookup via dummy UDP connection (purely local, no packets sent)
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            InetAddress localAddr = socket.getLocalAddress();
            if (localAddr instanceof Inet4Address && !localAddr.isLoopbackAddress() && !localAddr.isAnyLocalAddress()) {
                return localAddr.getHostAddress();
            }
        } catch (Exception ignored) {}

        // 2. Enumerate network interfaces, filtering out loopback and virtual adapters
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            List<String> candidates = new ArrayList<>();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                String name = (iface.getName() + " " + iface.getDisplayName()).toLowerCase();
                boolean isVirtual = name.contains("vmware") || name.contains("virtual") || name.contains("vbox") || name.contains("hyper-v");

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (addr.isSiteLocalAddress() && !isVirtual) {
                            return ip; // Best candidate: physical LAN IP
                        }
                        if (!isVirtual) {
                            candidates.add(0, ip);
                        } else {
                            candidates.add(ip);
                        }
                    }
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        } catch (Exception ignored) {}

        // 3. Fallback to InetAddress.getLocalHost()
        try {
            InetAddress local = InetAddress.getLocalHost();
            if (local instanceof Inet4Address && !local.isLoopbackAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }

    /**
     * Returns a list of all active IPv4 addresses on physical and virtual interfaces.
     */
    public static List<String> getAllLocalIPv4Addresses() {
        Set<String> result = new LinkedHashSet<>();
        result.add(getLocalIPv4Address());
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        result.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(result);
    }
}
