# Ausgaben

Android-App (Java) zum Tracken von Bargeld-**Ausgaben** und -**Einnahmen**. Buchungen werden
lokal gespeichert, als CSV exportiert und per WebDAV auf eine **Nextcloud** hochgeladen. Die
CSV-Dateien lassen sich anschließend in **kMyMoney** importieren.

## Funktionen

- Erfassung mit Umschalter *Ausgabe/Einnahme*, Betrag, Geldempfänger, Konto, Notiz und Datum
  (heute vorbelegt). Empfänger/Konto sind frei eingebbar mit Vorschlägen aus bisherigen Werten.
- Buchungsliste mit Farbkennzeichnung (Ausgabe rot / Einnahme grün) und „exportiert"-Markierung.
- **Kurzer Druck** auf eine Buchung dupliziert sie ins Formular, **langer Druck** öffnet
  Bearbeiten/Löschen.
- **Filtern** nach Empfänger, Konto oder Betrag.
- **Export** auf Nextcloud: pro Konto eine Datei `<Konto>-<Zeitstempel>.csv`; jede Buchung wird
  nur einmal exportiert (erst nach erfolgreichem Upload als exportiert markiert).
- **Kompletter Export** aller Buchungen über die Einstellungen.
- **Import** von kMyMoney-Ledger-CSV (Konto aus `Kontentyp:`-Zeile, ISO-Datum, Vorzeichen → Typ,
  Notiz-Spalte).
- **Auswertung** (Tag/Woche/Monat/Jahr) mit Gesamtbetrag und Balkendiagramm.
- **Einstellungen**: Nextcloud-Zugang (App-Passwort verschlüsselt), Standardkonto,
  Hell-/Dunkel-Design, Datenbank-Backup.

## CSV-Format (Export)

Deutsch: Spaltentrenner `;`, Dezimaltrennzeichen `,`, Datum `TT.MM.JJJJ`, UTF-8, CRLF.

```
Datum;Empfänger;Konto;Typ;Betrag;Notiz
29.06.2026;Metzgerei;Bargeld;Ausgabe;-7,30;Mittagessen
```

## Technik

- Java, Gradle 8.9 / AGP 8.5.2, `minSdk 26`, `compileSdk 34`.
- [Room](https://developer.android.com/training/data-storage/room) (SQLite), OkHttp (WebDAV-PUT),
  [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart),
  [androidx.security](https://developer.android.com/jetpack/androidx/releases/security) (verschlüsselte Prefs).

## Bauen

```bash
./gradlew assembleDebug
```

Das Android-SDK wird über `local.properties` (`sdk.dir=…`) gefunden – diese Datei ist nicht
eingecheckt und muss lokal vorhanden sein (legt Android Studio automatisch an).

## Nextcloud einrichten

In den Einstellungen Basis-URL, Benutzername und ein **App-Passwort**
(Nextcloud → Einstellungen → Sicherheit → App-Passwort) eintragen, optional einen Zielordner.
Die Dateien landen unter `<URL>/remote.php/dav/files/<user>/<Ordner>/`.
