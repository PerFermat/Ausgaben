package de.spahr.ausgaben.net;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Der Datei-Browser holt Ordner und Dateien über {@link RemoteStorage#listEntries}. Backends ohne
 * eigene Umsetzung (WebDAV) sollen sich dabei genauso verhalten wie zuvor mit den zwei Einzelaufrufen.
 */
public class RemoteEntriesTest {

    /** Minimales Backend, das nur mitzählt, wie oft gelistet wurde. */
    private static final class Stub implements RemoteStorage {
        int folderCalls;
        int fileCalls;

        @Override
        public List<String> listFolders(String folder) {
            folderCalls++;
            return Arrays.asList("Backup", "Archiv");
        }

        @Override
        public List<String> listFiles(String folder, String ext) {
            fileCalls++;
            return Arrays.asList("haushalt." + ext);
        }

        @Override
        public void uploadText(String folder, String fileName, String content) {
        }

        @Override
        public void uploadBytes(String folder, String fileName, byte[] content) {
        }

        @Override
        public String downloadText(String folder, String fileName) {
            return "";
        }

        @Override
        public byte[] downloadBytes(String folder, String fileName) {
            return new byte[0];
        }

        @Override
        public void testConnection() {
        }
    }

    @Test
    public void defaultCombinesBothListings() throws IOException {
        Stub stub = new Stub();
        RemoteStorage.Entries entries = stub.listEntries("Finanzen", "kmy");
        assertEquals(Arrays.asList("Backup", "Archiv"), entries.folders);
        assertEquals(Arrays.asList("haushalt.kmy"), entries.files);
        assertEquals(1, stub.folderCalls);
        assertEquals(1, stub.fileCalls);
    }
}
