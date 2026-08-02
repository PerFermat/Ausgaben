package de.spahr.ausgaben.net.smb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Prüft Subnetz-Berechnung und NetBIOS-Auswertung der Serversuche (ohne Netzwerk). */
public class SmbDiscoveryTest {

    @Test
    public void listsAllHostsOfSlash24WithoutOwnAddress() {
        List<String> hosts = SmbDiscovery.subnetAddresses("192.168.178.42", 24);
        assertEquals(253, hosts.size());          // 254 Hosts minus die eigene Adresse
        assertEquals("192.168.178.1", hosts.get(0));
        assertEquals("192.168.178.254", hosts.get(hosts.size() - 1));
        assertTrue(!hosts.contains("192.168.178.42"));
        assertTrue(!hosts.contains("192.168.178.0"));
        assertTrue(!hosts.contains("192.168.178.255"));
    }

    @Test
    public void smallSubnetIsHandled() {
        List<String> hosts = SmbDiscovery.subnetAddresses("10.0.0.5", 29);
        assertEquals(5, hosts.size());
        assertEquals("10.0.0.1", hosts.get(0));
        assertEquals("10.0.0.6", hosts.get(hosts.size() - 1));
    }

    @Test
    public void hugeOrInvalidSubnetsAreSkipped() {
        assertTrue(SmbDiscovery.subnetAddresses("10.0.0.5", 8).isEmpty());
        assertTrue(SmbDiscovery.subnetAddresses("fe80::1", 64).isEmpty());
        assertTrue(SmbDiscovery.subnetAddresses("kein.host", 24).isEmpty());
        assertTrue(SmbDiscovery.subnetAddresses(null, 24).isEmpty());
    }

    @Test
    public void nodeStatusQueryAsksForAllNames() {
        byte[] q = SmbDiscovery.nodeStatusQuery();
        assertEquals(50, q.length);
        assertEquals(1, q[5]);              // genau eine Frage
        assertEquals(0x20, q[12]);          // kodierter Name mit 32 Zeichen
        assertEquals('C', q[13]);           // '*' = 0x2A → 'C','K'
        assertEquals('K', q[14]);
        assertEquals('A', q[15]);           // Rest mit 0x00 aufgefüllt → 'A','A'
        assertEquals(0x21, q[47]);          // Typ NBSTAT
        assertEquals(0x01, q[49]);          // Klasse IN
    }

    @Test
    public void prefersFileServerNameFromNodeStatus() {
        byte[] reply = nodeStatusReply(new String[]{"WORKGROUP", "SYNOLOGY", "SYNOLOGY"},
                new int[]{0x00, 0x00, 0x20}, new boolean[]{true, false, false});
        assertEquals("SYNOLOGY", SmbDiscovery.parseNodeStatus(reply, reply.length));
    }

    @Test
    public void fallsBackToFirstUniqueNameAndIgnoresGroups() {
        byte[] reply = nodeStatusReply(new String[]{"WORKGROUP", "FRITZNAS"},
                new int[]{0x00, 0x00}, new boolean[]{true, false});
        assertEquals("FRITZNAS", SmbDiscovery.parseNodeStatus(reply, reply.length));
        byte[] onlyGroup = nodeStatusReply(new String[]{"WORKGROUP"}, new int[]{0x00},
                new boolean[]{true});
        assertNull(SmbDiscovery.parseNodeStatus(onlyGroup, onlyGroup.length));
    }

    @Test
    public void shortReplyIsIgnored() {
        assertNull(SmbDiscovery.parseNodeStatus(new byte[20], 20));
    }

    /** Baut eine Node-Status-Antwort nach RFC 1002 mit den angegebenen Namen. */
    private static byte[] nodeStatusReply(String[] names, int[] suffixes, boolean[] group) {
        int head = 12 + 34 + 10;
        byte[] out = new byte[head + 1 + names.length * 18];
        out[head] = (byte) names.length;
        int p = head + 1;
        for (int i = 0; i < names.length; i++, p += 18) {
            byte[] raw = String.format("%-15s", names[i]).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(raw, 0, out, p, 15);
            out[p + 15] = (byte) suffixes[i];
            out[p + 16] = (byte) (group[i] ? 0x80 : 0x04);
        }
        return out;
    }
}
