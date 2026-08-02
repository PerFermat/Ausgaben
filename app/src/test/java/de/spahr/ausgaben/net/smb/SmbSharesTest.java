package de.spahr.ausgaben.net.smb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Prüft das selbst kodierte DCERPC/NDR-Format für {@code NetShareEnumAll} ohne echten Server. */
public class SmbSharesTest {

    @Test
    public void bindPduHasExpectedHeaderAndSyntaxes() {
        byte[] bind = SmbShares.buildBind(1);
        assertEquals(72, bind.length);
        assertEquals(5, bind[0]);            // Version 5.0
        assertEquals(0, bind[1]);
        assertEquals(0x0b, bind[2]);         // ptype = bind
        assertEquals(0x03, bind[3]);         // erstes + letztes Fragment
        assertEquals(0x10, bind[4]);         // Little Endian
        ByteBuffer b = ByteBuffer.wrap(bind).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(72, b.getShort(8));     // frag_length
        assertEquals(1, b.getInt(12));       // call id
        assertEquals(1, bind[24]);           // ein Kontext
        // srvsvc-UUID 4b324fc8-1670-01d3-1278-5a47bf6ee188 (little endian) ab 32, danach Version 3.0.
        // Ein einziges falsches Byte lehnt der Server mit „abstract syntax not supported" ab.
        byte[] srvsvc = {(byte) 0xc8, 0x4f, 0x32, 0x4b, 0x70, 0x16, (byte) 0xd3, 0x01,
                0x12, 0x78, 0x5a, 0x47, (byte) 0xbf, 0x6e, (byte) 0xe1, (byte) 0x88};
        for (int i = 0; i < srvsvc.length; i++) {
            assertEquals("UUID-Byte " + i, srvsvc[i], bind[32 + i]);
        }
        assertEquals(3, b.getShort(48));
        // NDR-Transfersyntax ab 52, Version 2.0
        assertEquals(0x04, bind[52]);
        assertEquals(2, b.getShort(68));
    }

    @Test
    public void enumRequestCarriesServerNameAndOpnum() {
        byte[] req = SmbShares.buildEnumRequest(2, "nas");
        ByteBuffer b = ByteBuffer.wrap(req).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, req[2]);                       // ptype = request
        assertEquals(req.length, b.getShort(8));       // frag_length passt zur Puffergröße
        assertEquals(2, b.getInt(12));                 // call id
        assertEquals(15, b.getShort(22));              // opnum NetShareEnumAll
        assertEquals(6, b.getInt(28));                 // max_count: "\\nas" + NUL
        assertEquals(0, b.getInt(32));                 // offset
        assertEquals(6, b.getInt(36));                 // actual_count
        String name = new String(req, 40, 12, StandardCharsets.UTF_16LE);
        assertEquals("\\\\nas\0", name);
        // Danach: Level 1, Union 1, Container-Zeiger, EntriesRead 0, Buffer NULL, MaxLen, ResumeHandle
        int tail = req.length - 28;
        assertEquals(1, b.getInt(tail));
        assertEquals(1, b.getInt(tail + 4));
        assertEquals(0, b.getInt(tail + 12));
        assertEquals(0xFFFFFFFF, b.getInt(req.length - 8));
        assertEquals(0, b.getInt(req.length - 4));
    }

    @Test
    public void parsesDiskSharesAndSkipsAdminAndIpc() throws Exception {
        byte[] stub = response(new String[]{"Dokumente", "Finanzen", "ADMIN$", "IPC$", "Drucker"},
                new int[]{0, 0, 0x80000000, 0x80000003, 1});
        List<SmbShares.Entry> entries = SmbShares.parseEnumResponse(stub);
        assertEquals(5, entries.size());
        assertEquals("Dokumente", entries.get(0).name);
        assertEquals(0, entries.get(0).type);
        assertEquals("Drucker", entries.get(4).name);
        assertEquals(1, entries.get(4).type);
    }

    @Test
    public void parsesOddLengthNamesWithPadding() throws Exception {
        byte[] stub = response(new String[]{"abc", "abcd"}, new int[]{0, 0});
        List<SmbShares.Entry> entries = SmbShares.parseEnumResponse(stub);
        assertEquals(2, entries.size());
        assertEquals("abc", entries.get(0).name);
        assertEquals("abcd", entries.get(1).name);
    }

    @Test
    public void emptyContainerYieldsNoShares() throws Exception {
        ByteBuffer b = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(1).putInt(1).putInt(0);   // Level, Union, Container-Zeiger = NULL
        assertTrue(SmbShares.parseEnumResponse(b.array()).isEmpty());
    }

    @Test
    public void truncatedResponseFailsInsteadOfLooping() {
        byte[] full;
        try {
            full = response(new String[]{"Dokumente", "Finanzen"}, new int[]{0, 0});
        } catch (Exception e) {
            fail("Testdaten konnten nicht gebaut werden");
            return;
        }
        byte[] cut = new byte[full.length - 24];   // Schlussteil + halber Freigabename fehlen
        System.arraycopy(full, 0, cut, 0, cut.length);
        try {
            SmbShares.parseEnumResponse(cut);
            fail("Eine abgeschnittene Antwort muss einen Fehler melden");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unvollständig"));
        }
    }

    /** Baut eine NDR-Antwort mit den angegebenen Freigaben (ohne Beschreibungstexte). */
    private static byte[] response(String[] names, int[] types) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer head = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        head.putInt(1);                 // Level
        head.putInt(1);                 // Union-Auswahl
        head.putInt(0x00020000);        // Zeiger auf den Container
        head.putInt(names.length);      // EntriesRead
        head.putInt(0x00020004);        // Zeiger auf das Array
        head.putInt(names.length);      // max_count
        out.write(head.array());
        for (int i = 0; i < names.length; i++) {
            ByteBuffer e = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            e.putInt(0x00020008 + i * 8);   // Zeiger auf den Namen
            e.putInt(types[i]);
            e.putInt(0);                    // keine Beschreibung
            out.write(e.array());
        }
        for (String name : names) {
            byte[] chars = (name + "\0").getBytes(StandardCharsets.UTF_16LE);
            int pad = (4 - (chars.length % 4)) % 4;
            ByteBuffer s = ByteBuffer.allocate(12 + chars.length + pad).order(ByteOrder.LITTLE_ENDIAN);
            s.putInt(name.length() + 1);
            s.putInt(0);
            s.putInt(name.length() + 1);
            s.put(chars);
            out.write(s.array());
        }
        ByteBuffer tail = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        tail.putInt(names.length);   // TotalEntries
        tail.putInt(0);              // ResumeHandle = NULL
        tail.putInt(0);              // WERR_OK
        out.write(tail.array());
        return out.toByteArray();
    }
}
