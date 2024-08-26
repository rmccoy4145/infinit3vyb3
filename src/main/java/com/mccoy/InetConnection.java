package com.mccoy;

import java.net.*;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

public class InetConnection {
    private static final Logger LOG = Logger.getLogger(InetConnection.class.getName());

    private InetAddress inetAddress;
    private NetworkInterface networkInterface;

    public InetConnection() throws SocketException {
        this.setInet();
    }

    public void setInet() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            // Skip loopback and down interfaces
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            // Check if the interface supports multicast
            if (networkInterface.supportsMulticast()) {
                this.networkInterface = networkInterface;
                List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();

                for(InterfaceAddress address : addresses) {
                    InetAddress inetAddress = address.getAddress();
                    // Skip IPv6 loopback address (::1) if you prefer an IPv4 address
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        this.inetAddress = inetAddress;
                        return;
                    }
                }
            }
        }

        // If no suitable interface is found
        if (this.inetAddress == null || this.inetAddress.isLoopbackAddress()) {
            LOG.severe("No valid network interface found");
        }
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public NetworkInterface getNetworkInterface() {
        return networkInterface;
    }
}
