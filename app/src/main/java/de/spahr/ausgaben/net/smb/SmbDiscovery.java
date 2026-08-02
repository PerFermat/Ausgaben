package de.spahr.ausgaben.net.smb;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Sucht SMB-Server im lokalen Netz. Drei Quellen laufen parallel, weil kein Verfahren allein alle
 * Geräte findet: mDNS ({@code _smb._tcp}, z. B. Synology/macOS/Samba), eine NetBIOS-Namensabfrage per
 * Broadcast (ältere NAS und Windows-Freigaben) und ein Verbindungsversuch auf Port 445 für jede Adresse
 * des eigenen Subnetzes. Treffer werden über den Host zusammengeführt und einzeln gemeldet, damit die
 * Liste schon während der Suche wächst.
 */
public final class SmbDiscovery {

    /**
     * Ein gefundener Server: Anzeigename (kann gleich dem Host sein), Host/IP zum Verbinden und – sofern
     * der Server ihn per NetBIOS nennt – seine Arbeitsgruppe bzw. Domäne (sonst leer).
     */
    public static final class Server {
        public final String name;
        public final String host;
        public final String workgroup;

        public Server(String name, String host, String workgroup) {
            this.name = name;
            this.host = host;
            this.workgroup = workgroup == null ? "" : workgroup;
        }
    }

    public interface Listener {
        /** Neuer Treffer oder besserer Name für einen bekannten Host (Zusammenführung über {@code host}). */
        void onServer(Server server);

        void onFinished();
    }

    private static final int SMB_PORT = 445;
    private static final int NETBIOS_PORT = 137;
    /** Obergrenze der gesamten Suche; danach wird gemeldet, was da ist. */
    private static final long TOTAL_TIMEOUT_MS = 6000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, String> found = new ConcurrentHashMap<>();
    private final Map<String, String> workgroups = new ConcurrentHashMap<>();
    private final Context context;
    private volatile boolean cancelled;
    private ExecutorService pool;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener nsdListener;

    public SmbDiscovery(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Startet die Suche; {@code listener} wird immer auf dem Main-Thread aufgerufen. */
    public void start(final Listener listener) {
        cancelled = false;
        found.clear();
        workgroups.clear();
        pool = Executors.newFixedThreadPool(48);
        startMdns(listener);
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + TOTAL_TIMEOUT_MS;
            try {
                netbiosBroadcast(listener);
                sweepSubnets(listener);
                pool.shutdown();
                long left = deadline - System.currentTimeMillis();
                pool.awaitTermination(Math.max(left, 0), TimeUnit.MILLISECONDS);
                left = deadline - System.currentTimeMillis();
                if (left > 0) {
                    Thread.sleep(left);   // mDNS-Antworten trudeln oft später ein
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                // Einzelne Quellen dürfen scheitern (kein WLAN, Broadcast gesperrt …).
            } finally {
                stopMdns();
                pool.shutdownNow();
                if (!cancelled) {
                    main.post(listener::onFinished);
                }
            }
        }, "smb-discovery").start();
    }

    /** Bricht die laufende Suche ab; danach kommen keine Rückmeldungen mehr. */
    public void cancel() {
        cancelled = true;
        stopMdns();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    /**
     * Meldet einen Treffer. Ein echter Name gewinnt gegen einen zuvor gemeldeten reinen IP-Namen, und eine
     * nachgereichte Arbeitsgruppe ergänzt einen schon gemeldeten Server – gemeldet wird nur, wenn dabei
     * wirklich etwas Neues dazukommt.
     */
    private void publish(Listener listener, String host, String name, String workgroup) {
        if (cancelled || host == null || host.isEmpty()) {
            return;
        }
        String label = name == null || name.trim().isEmpty() ? host : name.trim();
        String wg = workgroup == null ? "" : workgroup.trim();
        String knownName = found.get(host);
        String knownWg = workgroups.containsKey(host) ? workgroups.get(host) : "";
        boolean betterName = knownName == null || (knownName.equals(host) && !label.equals(host));
        boolean betterGroup = !wg.isEmpty() && !wg.equals(knownWg);
        if (!betterName && !betterGroup) {
            return;
        }
        if (betterName) {
            found.put(host, label);
        } else {
            label = knownName;
        }
        if (betterGroup) {
            workgroups.put(host, wg);
        } else {
            wg = knownWg;
        }
        final String outName = label;
        final String outGroup = wg;
        main.post(() -> listener.onServer(new Server(outName, host, outGroup)));
    }

    // ------------------------------------------------------------------ mDNS

    private void startMdns(final Listener listener) {
        try {
            nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
            if (nsdManager == null) {
                return;
            }
            nsdListener = new NsdManager.DiscoveryListener() {
                @Override
                public void onDiscoveryStarted(String serviceType) {
                }

                @Override
                public void onServiceFound(NsdServiceInfo info) {
                    resolve(info, listener);
                }

                @Override
                public void onServiceLost(NsdServiceInfo info) {
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                }
            };
            nsdManager.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, nsdListener);
        } catch (Exception ignored) {
            nsdManager = null;
        }
    }

    private void resolve(NsdServiceInfo info, final Listener listener) {
        try {
            nsdManager.resolveService(info, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                }

                @Override
                public void onServiceResolved(NsdServiceInfo resolved) {
                    InetAddress addr = resolved.getHost();
                    if (addr != null) {
                        // Bonjour nennt keine Arbeitsgruppe – die liefert bei Bedarf NetBIOS nach.
                        publish(listener, addr.getHostAddress(), resolved.getServiceName(), "");
                    }
                }
            });
        } catch (Exception ignored) {
            // Auf manchen Geräten ist nur ein Resolve gleichzeitig erlaubt.
        }
    }

    private void stopMdns() {
        try {
            if (nsdManager != null && nsdListener != null) {
                nsdManager.stopServiceDiscovery(nsdListener);
            }
        } catch (Exception ignored) {
            // War schon gestoppt.
        } finally {
            nsdListener = null;
        }
    }

    // -------------------------------------------------------------- NetBIOS

    /** Namensabfrage per Broadcast; Antworten liefern Host und Namen in einem Rutsch. */
    private void netbiosBroadcast(final Listener listener) {
        List<InetAddress> targets = new ArrayList<>();
        for (InterfaceAddress ia : localIpv4()) {
            InetAddress b = ia.getBroadcast();
            if (b != null) {
                targets.add(b);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(300);
            byte[] query = nodeStatusQuery();
            for (InetAddress t : targets) {
                socket.send(new DatagramPacket(query, query.length, t, NETBIOS_PORT));
            }
            byte[] buf = new byte[1024];
            long until = System.currentTimeMillis() + 1500;
            while (!cancelled && System.currentTimeMillis() < until) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(p);
                } catch (IOException e) {
                    continue;   // Zeitüberschreitung: weiter warten, bis das Fenster zu ist
                }
                String name = parseNodeStatus(p.getData(), p.getLength());
                if (name != null) {
                    publish(listener, p.getAddress().getHostAddress(), name,
                            parseWorkgroup(p.getData(), p.getLength()));
                }
            }
        } catch (Exception ignored) {
            // Broadcast ist in manchen Netzen gesperrt – dann trägt der Port-Scan die Suche.
        }
    }

    /**
     * Gezielte Namensabfrage an einen Host; liefert {@code {Name, Arbeitsgruppe}} oder {@code null}, wenn
     * er nicht per NetBIOS antwortet.
     */
    private String[] netbiosName(InetAddress host) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(400);
            byte[] query = nodeStatusQuery();
            socket.send(new DatagramPacket(query, query.length, host, NETBIOS_PORT));
            byte[] buf = new byte[1024];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            String name = parseNodeStatus(p.getData(), p.getLength());
            return name == null ? null
                    : new String[]{name, parseWorkgroup(p.getData(), p.getLength())};
        } catch (Exception e) {
            return null;
        }
    }

    /** Node-Status-Anfrage („*") nach RFC 1002 – liefert die Namensliste des Gegenübers. */
    static byte[] nodeStatusQuery() {
        byte[] raw = new byte[16];
        raw[0] = '*';
        byte[] out = new byte[50];
        out[0] = 0x12;   // Transaktions-Id (beliebig)
        out[1] = 0x34;
        out[2] = 0x00;   // Flags: Anfrage, Broadcast aus
        out[3] = 0x00;
        out[5] = 0x01;   // eine Frage
        out[12] = 0x20;  // Länge des kodierten Namens
        for (int i = 0; i < 16; i++) {
            out[13 + i * 2] = (byte) ('A' + ((raw[i] >> 4) & 0x0F));
            out[14 + i * 2] = (byte) ('A' + (raw[i] & 0x0F));
        }
        out[45] = 0x00;  // Namensende
        out[47] = 0x21;  // Typ NBSTAT
        out[49] = 0x01;  // Klasse IN
        return out;
    }

    /**
     * Liest aus einer Node-Status-Antwort den Rechnernamen: bevorzugt den Eintrag mit Suffix
     * {@code 0x20} (Dateiserver), sonst den ersten eindeutigen Namen. {@code null}, wenn die Antwort
     * keinen brauchbaren Namen enthält.
     */
    static String parseNodeStatus(byte[] data, int length) {
        // Kopf (12) + kodierter Name (34) + Typ/Klasse/TTL/Länge (10) + Anzahl (1)
        int p = 12 + 34 + 10;
        if (length < p + 1) {
            return null;
        }
        int count = data[p] & 0xFF;
        p++;
        String fallback = null;
        for (int i = 0; i < count && p + 18 <= length; i++, p += 18) {
            String name = new String(data, p, 15, StandardCharsets.US_ASCII).trim();
            int suffix = data[p + 15] & 0xFF;
            boolean group = (data[p + 16] & 0x80) != 0;
            if (name.isEmpty() || group) {
                continue;
            }
            if (suffix == 0x20) {
                return name;
            }
            if (fallback == null && suffix == 0x00) {
                fallback = name;
            }
        }
        return fallback;
    }

    /**
     * Die Arbeitsgruppe (bzw. Domäne) aus einer Node-Status-Antwort: der erste Gruppeneintrag mit Suffix
     * {@code 0x00}. Leer, wenn der Server keine nennt.
     */
    static String parseWorkgroup(byte[] data, int length) {
        int p = 12 + 34 + 10;
        if (length < p + 1) {
            return "";
        }
        int count = data[p] & 0xFF;
        p++;
        for (int i = 0; i < count && p + 18 <= length; i++, p += 18) {
            String name = new String(data, p, 15, StandardCharsets.US_ASCII).trim();
            int suffix = data[p + 15] & 0xFF;
            boolean group = (data[p + 16] & 0x80) != 0;
            if (group && suffix == 0x00 && !name.isEmpty()) {
                return name;
            }
        }
        return "";
    }

    // ------------------------------------------------------------- Port 445

    /** Prüft jede Adresse des eigenen Subnetzes auf einen offenen SMB-Port. */
    private void sweepSubnets(final Listener listener) {
        for (InterfaceAddress ia : localIpv4()) {
            for (final String ip : subnetAddresses(ia.getAddress().getHostAddress(),
                    ia.getNetworkPrefixLength())) {
                if (cancelled) {
                    return;
                }
                pool.submit(() -> {
                    if (cancelled) {
                        return;
                    }
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(ip, SMB_PORT), 400);
                    } catch (Exception e) {
                        return;   // dort läuft kein SMB
                    }
                    String[] netbios = null;
                    try {
                        netbios = netbiosName(InetAddress.getByName(ip));
                    } catch (Exception ignored) {
                        // Kein NetBIOS: dann bleibt es bei der IP als Anzeigename.
                    }
                    publish(listener, ip, netbios == null ? found.get(ip) : netbios[0],
                            netbios == null ? "" : netbios[1]);
                });
            }
        }
    }

    /**
     * Alle Host-Adressen des Subnetzes von {@code ip} ohne Netz-, Broadcast- und eigene Adresse.
     * Größere Netze als /22 werden übersprungen – ein Scan über tausende Adressen dauert zu lange.
     */
    static List<String> subnetAddresses(String ip, int prefixLength) {
        List<String> out = new ArrayList<>();
        if (ip == null || prefixLength < 22 || prefixLength > 30 || ip.indexOf(':') >= 0) {
            return out;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return out;
        }
        long addr = 0;
        try {
            for (String part : parts) {
                int v = Integer.parseInt(part);
                if (v < 0 || v > 255) {
                    return out;
                }
                addr = (addr << 8) | v;
            }
        } catch (NumberFormatException e) {
            return out;
        }
        long mask = 0xFFFFFFFFL << (32 - prefixLength) & 0xFFFFFFFFL;
        long network = addr & mask;
        long broadcast = network | (~mask & 0xFFFFFFFFL);
        for (long a = network + 1; a < broadcast; a++) {
            if (a != addr) {
                out.add(((a >> 24) & 0xFF) + "." + ((a >> 16) & 0xFF) + "."
                        + ((a >> 8) & 0xFF) + "." + (a & 0xFF));
            }
        }
        return out;
    }

    /** IPv4-Adressen der aktiven, nicht-lokalen Schnittstellen (in der Regel genau das WLAN). */
    private static List<InterfaceAddress> localIpv4() {
        List<InterfaceAddress> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a != null && a.getAddress().length == 4 && !a.isLoopbackAddress()) {
                        out.add(ia);
                    }
                }
            }
        } catch (Exception ignored) {
            // Ohne Netz gibt es nichts zu durchsuchen.
        }
        return Collections.unmodifiableList(out);
    }
}
