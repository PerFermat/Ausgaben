# Warum diese App für KMyMoney-User interessant sein könnte

- Bargeldausgaben unterwegs erfassen
- Synchronisation mit KMyMoney über .kmy oder CSV
- Vollständig offline nutzbar
- Nextcloud oder beliebiger WebDAV-Server
- Wear-OS-App mit Spracheingabe
- Splitbuchungen und Umbuchungen
- Biometrischer Schutz
- Keine Werbung, keine Hersteller-Cloud

# Ausgaben

Android-App (Java) zum Tracken von Bargeld-**Ausgaben**, -**Einnahmen** und **Umbuchungen** über mehrere Konten.
Buchungen werden lokal (Room/SQLite) gespeichert und lassen sich per **WebDAV** synchronisieren
– beispielsweise mit **Nextcloud**, aber auch mit anderen WebDAV-kompatiblen Diensten.
Der Datenaustausch erfolgt entweder als **kMyMoney-CSV** oder direkt in eine **kMyMoney-`.kmy`-Datei**.


## Funktionen

### Buchungen erfassen
- Typ-Umschalter **Ausgabe / Umbuchung / Einnahme**, Betrag, Geldempfänger, Konto (Auswahl aus
  vorhandenen Konten), Notiz und Datum (heute vorbelegt, „Heute"-Schnellwahl).
- **Splitbuchungen**: mehrere Kategorien mit Teilbeträgen. Bei einer Kategorie sind Gesamt- und
  Kategoriebetrag gekoppelt; bei mehreren muss die Summe dem Gesamtbetrag entsprechen. Teilbeträge
  dürfen negativ sein.
- **Umbuchung** (Kontotransfer): zwei Konten (Von/Nach) + optionaler Zahlungsempfänger; legt eine
  verknüpfte Buchung in beiden Konten an.
- **GPS-Koordinaten in der Notiz**: bei einer *neuen* Buchung erscheinen die aktuellen Koordinaten
  bereits während der Eingabe im Notizfeld als `GPS: lat, lon` (sichtbar und editierbar; der übrige
  Notiztext bleibt erhalten, während man tippt wird nicht überschrieben). Nur mit erteilter Standort-
  Berechtigung und rein lokal – die Position wird nicht an einen externen Dienst gesendet. Ohne
  Berechtigung/Position bleibt das Feld leer; bestehende Buchungen werden nicht angefasst.
- **Sprach-Schnellerfassung**: langer Druck auf **„Neue Buchung"** öffnet die Spracheingabe. Sagt man
  z. B. „Frisör 20 €", wird die zuletzt passende Buchung als Vorlage geöffnet (Empfänger, Konto,
  Kategorie(n), Notiz, Buchungsart) – mit heutigem Datum und dem gesprochenen Betrag. Die Empfängersuche
  ist unscharf (findet „Frisör Frank" auch bei „Friseur").
- **Gelernte Namenskorrekturen**: wird ein gesprochener Empfänger in den Buchungen nicht gefunden und man
  ändert ihn beim Speichern, fragt die App, ob sie sich die Korrektur merken soll. Kommt derselbe (falsch
  erkannte) Name später erneut, wird zuerst in den Buchungen und dann in den gemerkten Korrekturen
  gesucht (gleiche unscharfe Logik) und der richtige Empfänger automatisch übernommen – auch für die
  Wear-Erfassung. Dieselbe Zuordnungstabelle lässt sich auch bewusst als **Alias/Abkürzung** nutzen, nicht nur zur
  Fehlerkorrektur: Ein Eintrag kann einen beliebigen gesprochenen Begriff auf einen ganz anderen Empfänger
  abbilden. Sagt man z. B. „Mama 100 €", kann bei passendem Tabelleneintrag automatisch der reale Name der
  Mutter als Empfänger eingesetzt werden.

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
- **Server-Typ** wählbar: **Nextcloud** (Pfadschema `…/remote.php/dav/files/<user>/`) oder ein
  **generischer WebDAV-Server** (dann ist die eingetragene Basis-URL bereits die DAV-Wurzel). Protokoll
  ist in beiden Fällen WebDAV mit HTTP-Basic-Auth.
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

## Wear OS (Sprach-Schnellerfassung)

Ein zusätzliches Modul `:wear` erlaubt das Erfassen einer Bargeldausgabe per Sprache direkt auf einer
Wear-OS-Uhr („Frisör 20 Euro"). Die Uhr nimmt nur den Text auf; die eigentliche Verarbeitung (derselbe
Parser wie am Phone) und die Buchungsanlage passieren auf dem Smartphone.

- **Uhr**: ein Screen „Buchung erfassen" mit drei Typ-Knöpfen (Einnahme grün, Umbuchung gelb, Ausgabe
  rot) + Wear-Tile („Widget"). Nach der Typwahl startet die Sprache; der erkannte Text wird 10 Sekunden
  mit „Abbrechen" angezeigt und – falls nicht abgebrochen – ohne weitere Aktion verarbeitet. Der Typ
  wird mitübertragen und auf dem Phone erzwungen. Ist das Phone offline, steht unter den Knöpfen
  „x Buchungen noch nicht übertragen".
- **Offline & Sync**: Die Übertragung läuft **vollautomatisch** über die Wear Data Layer API
  (`MessageClient`). Ist das Phone nicht erreichbar, bleibt der Eintrag PENDING und wird bei
  Wiederverbindung automatisch erneut gesendet – ohne weitere Nutzeraktion auf Uhr oder Phone.
- **Kein Verlust / keine Dopplung**: Jeder Eintrag hat eine eindeutige ID. Das Phone verarbeitet jede ID
  nur einmal und bestätigt den Empfang (ACK); erst danach entfernt die Uhr den Eintrag.

**Datenfluss:**

```
Uhr (Sprache)
  → lokale Speicherung (PENDING, id/text/timestamp)
  → Übertragung  MessageClient  /expense/new  {id,text,timestamp}
  → Smartphone  WearableListenerService
  → Parser (VoiceInput) + Buchung anlegen (Repository, Dedup per id)
  → ACK  /expense/ack  {id}
  → Uhr: Eintrag SYNCED/entfernt  → Synchronisation abgeschlossen
```

Voraussetzung: Phone- und Wear-App haben dieselbe `applicationId` **und** dieselbe Signatur (für den
Release-Build dieselbe `keystore.properties`; im Debug ohnehin derselbe Debug-Key).

## CSV-Format (Export)

Deutsch: Spaltentrenner `;`, Dezimaltrennzeichen `,`, Datum `TT.MM.JJJJ`, UTF-8, CRLF. Splitbuchungen
werden je Kategorie als eigene Zeile geschrieben.

```
Datum;Empfänger;Konto;Typ;Betrag;Notiz;Kategorie
29.06.2026;Metzgerei;Bargeld;Ausgabe;-7,30;Mittagessen;Lebensmittel
```

## Technik

- Java, Gradle 8.9 / AGP 8.7.3, `minSdk 26` (`:app`) bzw. `minSdk 30` (`:wear`), `compileSdk 34`.
- Module: `:app` (Phone) und `:wear` (Wear OS).
- [Room](https://developer.android.com/training/data-storage/room) (SQLite, DB-Version 6), OkHttp
  (WebDAV), [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart),
  [androidx.security](https://developer.android.com/jetpack/androidx/releases/security)
  (verschlüsselte Prefs), [androidx.biometric](https://developer.android.com/jetpack/androidx/releases/biometric),
  [play-services-wearable](https://developer.android.com/training/wearables/data/data-layer) (Data Layer)
  und [androidx.wear.tiles](https://developer.android.com/training/wearables/tiles) (Tile).

## Bauen

```bash
./gradlew assembleDebug
```

Das Android-SDK wird über `local.properties` (`sdk.dir=…`) gefunden – diese Datei ist nicht
eingecheckt und muss lokal vorhanden sein (legt Android Studio automatisch an). Für einen signierten
Release-Build wird `keystore.properties` benötigt (ebenfalls nicht eingecheckt); fehlt sie, entsteht
ein unsigniertes Release.

## Nextcloud / WebDAV einrichten

In den Einstellungen den **Server-Typ** wählen, dann Basis-URL, Benutzername und Passwort eintragen,
optional Ziel-/Importordner bzw. im `.kmy`-Modus den Pfad zur `.kmy`-Datei.

- **Nextcloud**: Basis-URL = Server (z. B. `https://cloud.example.com`) und ein **App-Passwort**
  (Nextcloud → Einstellungen → Sicherheit → App-Passwort). Die Dateien landen unter
  `<URL>/remote.php/dav/files/<user>/<Ordner>/`.
- **WebDAV (generisch)**: Basis-URL = vollständige DAV-Wurzel (z. B.
  `https://host/dav/`); der Ordner wird direkt daran angehängt. Auth per HTTP-Basic.
