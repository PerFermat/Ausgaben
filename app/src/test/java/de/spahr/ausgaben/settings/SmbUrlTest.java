package de.spahr.ausgaben.settings;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Prüft das Zerlegen einer SMB-Adresse in Host, Freigabe, Basisordner und Port. */
public class SmbUrlTest {

    @Test
    public void splitsHostShareAndBase() {
        assertArrayEquals(new String[]{"nas", "public", "", ""},
                SettingsStore.parseSmb("smb://nas/public"));
        assertArrayEquals(new String[]{"nas", "public", "Ausgaben/2026", ""},
                SettingsStore.parseSmb("smb://nas/public/Ausgaben/2026"));
        assertArrayEquals(new String[]{"nas", "public", "", ""},
                SettingsStore.parseSmb("//nas/public/"));
        assertArrayEquals(new String[]{"nas", "public", "", ""},
                SettingsStore.parseSmb(" nas/public "));
        assertArrayEquals(new String[]{"", "", "", ""}, SettingsStore.parseSmb(null));
    }

    @Test
    public void readsPortFromAddress() {
        assertArrayEquals(new String[]{"nas", "public", "", "7777"},
                SettingsStore.parseSmb("smb://nas:7777/public"));
        assertArrayEquals(new String[]{"192.168.178.54", "public", "kmy", "445"},
                SettingsStore.parseSmb("smb://192.168.178.54:445/public/kmy"));
        assertArrayEquals(new String[]{"nas", "", "", "7777"},
                SettingsStore.parseSmb("smb://nas:7777"));
    }

    @Test
    public void ipv6AddressKeepsItsBrackets() {
        assertArrayEquals(new String[]{"[fe80::1]", "public", "", "7777"},
                SettingsStore.parseSmb("smb://[fe80::1]:7777/public"));
        assertArrayEquals(new String[]{"[fe80::1]", "public", "", ""},
                SettingsStore.parseSmb("smb://[fe80::1]/public"));
    }

    @Test
    public void unusablePortStaysPartOfTheHost() {
        // Sonst verschwände ein Tippfehler still und der Zugriff liefe auf dem Standardport.
        assertArrayEquals(new String[]{"nas:abc", "public", "", ""},
                SettingsStore.parseSmb("smb://nas:abc/public"));
        assertArrayEquals(new String[]{"nas:0", "public", "", ""},
                SettingsStore.parseSmb("smb://nas:0/public"));
        assertArrayEquals(new String[]{"nas:99999", "public", "", ""},
                SettingsStore.parseSmb("smb://nas:99999/public"));
        assertArrayEquals(new String[]{"nas:", "public", "", ""},
                SettingsStore.parseSmb("smb://nas:/public"));
    }

    /**
     * Der Port lässt sich nachträglich korrigieren, ohne dass Freigabe oder Basisordner leiden – das
     * passiert, wenn die Verbindung nur über den Standardport zustande kam.
     */
    @Test
    public void portCanBeCorrectedInPlace() {
        assertEquals("smb://nas/public/kmy", SettingsStore.withPort("smb://nas:7777/public/kmy", 0));
        assertEquals("smb://nas/public/kmy", SettingsStore.withPort("smb://nas:7777/public/kmy", 445));
        assertEquals("smb://nas:8445/public", SettingsStore.withPort("smb://nas/public", 8445));
        assertEquals("smb://nas:8445/public", SettingsStore.withPort("smb://nas:7777/public", 8445));
        assertEquals("smb://nas", SettingsStore.withPort("smb://nas:7777", 0));
        // Ohne brauchbaren Host bleibt die Eingabe unangetastet.
        assertEquals("", SettingsStore.withPort("", 445));
    }
}
