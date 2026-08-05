package de.spahr.ausgaben.net;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Tests für die Pfadhelfer der serverseitigen Ordnerangaben. */
public class RemotePathTest {

    @Test
    public void folderOf_everythingBeforeTheLastSlash() {
        assertEquals("KMyMoney", RemotePath.folderOf("KMyMoney/gdyx.kmy"));
        assertEquals("a/b", RemotePath.folderOf("a/b/c.kmy"));
    }

    @Test
    public void folderOf_emptyWithoutFolder() {
        assertEquals("", RemotePath.folderOf("gdyx.kmy"));
        assertEquals("", RemotePath.folderOf(null));
    }

    @Test
    public void fileOf_everythingAfterTheLastSlash() {
        assertEquals("gdyx.kmy", RemotePath.fileOf("KMyMoney/gdyx.kmy"));
        assertEquals("gdyx.kmy", RemotePath.fileOf("gdyx.kmy"));
    }

    @Test
    public void join_noDoubleSlashes() {
        assertEquals("KMyMoney/Belege", RemotePath.join("KMyMoney", "Belege"));
        assertEquals("KMyMoney/Belege", RemotePath.join("KMyMoney/", "/Belege"));
    }

    @Test
    public void join_emptyPartsFallAway() {
        // Liegt die .kmy in der Wurzel, ist der Ordnerteil leer – dann bleibt nur „Belege".
        assertEquals("Belege", RemotePath.join("", "Belege"));
        assertEquals("KMyMoney", RemotePath.join("KMyMoney", ""));
        assertEquals("", RemotePath.join(null, null));
    }
}
