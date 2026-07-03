# Ausgaben

Android-App (Java) zum Tracken von Bargeld-**Ausgaben**, -**Einnahmen** und **Umbuchungen** über
mehrere Konten. Buchungen werden lokal (Room/SQLite) gespeichert und lassen sich mit **Nextcloud**
synchronisieren – entweder als **kMyMoney-CSV** oder direkt in eine **kMyMoney-`.kmy`-Datei**.

## Funktionen

### Buchungen erfassen
- Typ-Umschalter **Ausgabe / Umbuchung / Einnahme**, Betrag, Geldempfänger, Konto (Auswahl aus
  vorhandenen Konten), Notiz und Datum (heute vorbelegt, „Heute"-Schnellwahl).
- **Splitbuchungen**: mehrere Kategorien mit Teilbeträgen. Bei einer Kategorie sind Gesamt- und
  Kategoriebetrag gekoppelt; bei mehreren muss die Summe dem Gesamtbetrag entsprechen. Teilbeträge
  dürfen negativ sein.
- **Umbuchung** (Kontotransfer): zwei Konten (Von/Nach) + optionaler Zahlungsempfänger; legt eine
  verknüpfte Buchung in beiden Konten an.
- **Sprach-Schnellerfassung**: langer Druck auf **„Neue Buchung"** öffnet die Spracheingabe. Sagt man
  z. B. „Frisör 20 €", wird die zuletzt passende Buchung als Vorlage geöffnet (Empfänger, Konto,
  Kategorie(n), Notiz, Buchungsart) – mit heutigem Datum und dem gesprochenen Betrag. Die Empfängersuche
  ist unscharf (findet „Frisör Frank" auch bei „Friseur").

### Übersicht & Auswertung
- Mehrere **Konten** über eine Navigationsschublade; Liste und Saldo je Konto oder „Alle Konten".
- Buchungsliste mit Farbkennzeichnung (negativ rot / positiv grün), „exportiert"-Markierung sowie
  Kennzeichnung von Split- (`Split`) und Umbuchungen (`→`/`←` Gegenkonto/Empfänger).
- **Orte/Bestände** pro Konto (wo liegt das Bargeld physisch): Ort-Salden, Umbuchen zwischen Orten,
  Kassensturz; der Standardort ist der Rest-Topf des Kontos.
- **Filter** nach Empfänger, Kategorie (als Baum) und Betrag. Bei Kategorie-Filter zeigt eine
  Splitbuchung nur den Teilbetrag der gewählten Kategorie.
- **Auswertung** (Tag/Woche/Monat/Jahr) als Balken- + Linien-Diagramm mit Konto-, Orts- und
  Gesamt-Sichten; per Fingergeste zoombar (horizontal = Balkenanzahl, vertikal = Y-Achse).

### Synchronisierung
- **CSV-Export** auf Nextcloud: pro Konto eine Datei `<Konto>-<Zeitstempel>.csv`; jede Buchung wird nur
  einmal exportiert (erst nach erfolgreichem Upload als exportiert markiert). Kompletter Export über die
  Einstellungen.
- **kMyMoney-`.kmy`-Modus**: schreibt neue Buchungen direkt in die `.kmy` (gzip-XML) und importiert
  Konten/Buchungen daraus – inkl. Splitbuchungen und Umbuchungen. Import ersetzt je Konto die bereits
  exportierten Buchungen.
- **CSV-Import** von kMyMoney-Ledger-CSV (Konto aus `Kontentyp:`-Zeile, ISO-Datum, Vorzeichen → Typ).

### Sicherheit & Einstellungen
- Optionale **App-Sperre** per Biometrie/Geräte-Anmeldung (Fingerabdruck, Gesicht, PIN, Muster,
  Passwort) – Authentifizierung beim Start bzw. bei Rückkehr aus dem Hintergrund.
- Einstellungen: Nextcloud-Zugang (App-Passwort verschlüsselt), Export-Modus (CSV/`.kmy`),
  Standardkonto, Orte je Konto, Hell-/Dunkel-Design, Datenbank-Backup/Restore, Konto löschen.

## CSV-Format (Export)

Deutsch: Spaltentrenner `;`, Dezimaltrennzeichen `,`, Datum `TT.MM.JJJJ`, UTF-8, CRLF. Splitbuchungen
werden je Kategorie als eigene Zeile geschrieben.

```
Datum;Empfänger;Konto;Typ;Betrag;Notiz;Kategorie
29.06.2026;Metzgerei;Bargeld;Ausgabe;-7,30;Mittagessen;Lebensmittel
```

## Technik

- Java, Gradle 8.9 / AGP 8.7.3, `minSdk 26`, `compileSdk 34`.
- [Room](https://developer.android.com/training/data-storage/room) (SQLite, DB-Version 6), OkHttp
  (WebDAV), [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart),
  [androidx.security](https://developer.android.com/jetpack/androidx/releases/security)
  (verschlüsselte Prefs), [androidx.biometric](https://developer.android.com/jetpack/androidx/releases/biometric).

## Bauen

```bash
./gradlew assembleDebug
```

Das Android-SDK wird über `local.properties` (`sdk.dir=…`) gefunden – diese Datei ist nicht
eingecheckt und muss lokal vorhanden sein (legt Android Studio automatisch an). Für einen signierten
Release-Build wird `keystore.properties` benötigt (ebenfalls nicht eingecheckt); fehlt sie, entsteht
ein unsigniertes Release.

## Nextcloud einrichten

In den Einstellungen Basis-URL, Benutzername und ein **App-Passwort**
(Nextcloud → Einstellungen → Sicherheit → App-Passwort) eintragen, optional Ziel-/Importordner bzw.
im `.kmy`-Modus den Pfad zur `.kmy`-Datei. Die Dateien landen unter
`<URL>/remote.php/dav/files/<user>/<Ordner>/`.
