# Projektbeschreibung

## Ausgaben

Dies ist eine Android-App (Java), die als mobile Ergänzung zu KMyMoney entwickelt wird. Sie ermöglicht es, Bargeld-Ausgaben, -Einnahmen und Umbuchungen unterwegs direkt auf dem Smartphone oder einer Wear-OS-Uhr zu erfassen, anstatt diese später manuell in KMyMoney nachzutragen.


## Warum diese App für KMyMoney-Nutzer interessant sein könnte

* Mobile Erweiterung für KMyMoney
* Bargeldausgaben unterwegs sofort erfassen
* Keine zusätzliche Cloud oder Herstellerkonto erforderlich
* Vollständig offline nutzbar
* Wear-OS-App mit Spracheingabe
* Splitbuchungen und Umbuchungen
* Biometrischer Schutz
* Nahtlose KMyMoney-Integration über .kmy-Dateien oder CSV-Import
* KMyMoney-Integration über gemeinsam genutzte WebDAV-Ordner
* Keine Werbung
* Open Source


## Screenshots

<p>
  <img src="screenshots/Hauptbildschirm.png" width="150">
  <img src="screenshots/Kontenmenü.png" width="150">
  <img src="screenshots/Ausgaben.png" width="150">
  <img src="screenshots/Grafik.png" width="150">
  <img src="screenshots/Einstellungen.png" width="150">
  <img src="screenshots/UhranlagemitAlias.png" width="150">
</p>


## Funktionen

### Buchungen erfassen
- Typ-Umschalter **Ausgabe / Umbuchung / Einnahme**, Betrag, Geldempfänger, Konto (Auswahl aus
  vorhandenen Konten), Notiz und Datum (heute vorbelegt, „Heute"-Schnellwahl).
- **Splitbuchungen**: mehrere Kategorien mit Teilbeträgen. Bei einer Kategorie sind Gesamt- und
  Kategoriebetrag gekoppelt; bei mehreren muss die Summe dem Gesamtbetrag entsprechen. Teilbeträge
  dürfen negativ sein.
- **Umbuchung** (Kontotransfer): zwei Konten (Von/Nach) + optionaler Zahlungsempfänger; legt eine
  verknüpfte Buchung in beiden Konten an.
- **Ort** (nur bei in der App angelegten Ausgaben/Einnahmen): wählbar mit dem Standardort des Kontos
  vorbelegt; bestimmt, welchem Bargeld-Ort die Buchung ihre Ort-Bewegung gutschreibt (siehe „Orte/Bestände").
  Bei importierten Buchungen wird kein Ort-Feld gezeigt.
- **GPS-Koordinaten in der Notiz**: bei einer *neuen* Buchung erscheinen die aktuellen Koordinaten
  bereits während der Eingabe im Notizfeld als `GPS: lat, lon` (sichtbar und editierbar; der übrige
  Notiztext bleibt erhalten, während man tippt wird nicht überschrieben). Nur mit erteilter Standort-
  Berechtigung und rein lokal – die Position wird nicht an einen externen Dienst gesendet. Ohne
  Berechtigung/Position bleibt das Feld leer; bestehende Buchungen werden nicht angefasst.
- **Sprach-Schnellerfassung**: langer Druck auf **„Neue Buchung"** öffnet die Spracheingabe. Sagt man
  z. B. „Frisör 20 €", wird die zuletzt passende Buchung als Vorlage geöffnet (Empfänger, Konto,
  Kategorie(n), Notiz, Buchungsart) – mit heutigem Datum und dem gesprochenen Betrag. Die Empfängersuche
  ist unscharf (findet „Frisör Frank" auch bei „Friseur").
- **Nur den Betrag erfassen (Standort-Auflösung)**: sagt man nur einen Betrag (oder tippt ihn über das
  **Ziffern-Symbol** unten still ein), sucht die App am aktuellen Standort (100 m) eine passende Vorlage –
  in den bestehenden Buchungen und im Alias-Verzeichnis (Reihenfolge: bevorzugte Aliase → Buchungen →
  übrige Aliase) – und übernimmt deren Daten. Aliase erhalten ihren Standort automatisch, wenn sie aus
  einer Buchung gelernt werden. Ohne Treffer wird nur der Betrag übernommen.
- **Alias-Namen (gelernte Zuordnungen)**: ändert man beim Speichern den per Sprache erkannten (oder beim
  Bearbeiten den bestehenden) Empfänger, fragt die App, ob sie sich die Zuordnung als Alias merken soll.
  Dabei wird der **Kontext der Buchung mitgespeichert** – Buchungsart, Konto und Kategorie(n) bzw.
  Von-/Bis-Konto bei Umbuchungen. Die hinterlegte **Buchungsart** bestimmt am Phone den Typ der neuen
  Buchung (in der Wear-App gilt die per Knopf gewählte Art). Kommt derselbe gesprochene Begriff später erneut, wird der Alias gefunden (gleiche unscharfe
  Logik wie die Buchungssuche) und die neue Buchung mit richtigem Empfänger **und** den hinterlegten
  Konto-/Kategorie-Daten vorbelegt – auch für die Wear-Erfassung. So deckt ein Alias jede Buchungsart ab.
  Damit lässt sich die Tabelle auch bewusst als **Alias/Abkürzung** nutzen: „Mama 100 €" kann automatisch
  auf den realen Namen samt Konto/Kategorie gebucht werden.
- **Suchreihenfolge**: zuerst die als **„bevorzugt"** markierten Aliase, dann die bestehenden Buchungen,
  erst danach die übrigen Aliase. „Bevorzugt" ist eine Eigenschaft des einzelnen Alias (im Formular
  einstellbar, in der Liste mit ★ markiert).
- Einstellbar unter **Einstellungen → Alias-Namen**: die Nachfrage lässt sich abschalten (bestehende
  Aliase werden weiterhin angewandt, es kommen nur keine neuen Nachfragen hinzu), und über
  **„Alias-Namen verwalten"** lassen sich alle Aliase mit allen Feldern manuell anlegen, ändern und löschen.

### Übersicht & Auswertung
- Mehrere **Konten** über eine Navigationsschublade; Liste und Saldo je Konto oder „Alle Konten".
- Buchungsliste mit Farbkennzeichnung (negativ rot / positiv grün), „exportiert"-Markierung sowie
  Kennzeichnung von Split- (`Split`) und Umbuchungen (`→`/`←` Gegenkonto/Empfänger).
- **Orte/Bestände** pro Konto (wo liegt das Bargeld physisch): jeder Ort führt ein eigenes
  Bewegungs-Journal; sein Saldo ist die Summe seiner Bewegungen. Eine in der App angelegte Buchung
  erzeugt automatisch eine Ort-Bewegung auf dem **Standardort** des Kontos (spätere Betrags-/Lösch-
  Änderungen hängen datierte Ausgleichs-Bewegungen an, die Historie bleibt erhalten). **„ohne Ort"** ist
  der berechnete Rest (Kontosaldo − Summe der Orte), sodass die Summe der Ort-Salden stets dem Kontosaldo
  entspricht. In den Beständen lassen sich Ort-Bewegungen einzeln anlegen/bearbeiten/löschen sowie zwischen
  Orten umbuchen (z. B. um eine importierte Buchung einem Ort zuzuordnen); ein Kassensturz setzt den Saldo
  eines Orts. Importierte Buchungen tragen keine Ort-Verknüpfung.
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

### Sprachen
- **Deutsch und Englisch** sind fest eingebaut und in den Einstellungen (ganz oben) umschaltbar –
  Phone **und** Uhr (die Uhr übernimmt die am Phone gewählte Sprache automatisch).
- Alle Texte liegen in einer **Datenbank-Tabelle** (beim ersten Start aus dem Programm mit DE/EN
  gefüllt). Über **„Vorlage exportieren"** wird eine JSON-Struktur mit allen Schlüsseln (inkl. der
  Uhr-Texte) als lokale Datei gespeichert; diese lässt sich manuell in einer weiteren Sprache befüllen
  und über **„Sprache hochladen"** einlesen. Danach ist die neue Sprache auswählbar und gilt auch für
  die Uhr. Fehlt eine Übersetzung, wird auf Englisch bzw. den eingebauten Text zurückgegriffen.

### Sicherheit & Einstellungen
- Optionale **App-Sperre** per Biometrie/Geräte-Anmeldung (Fingerabdruck, Gesicht, PIN, Muster,
  Passwort) – Authentifizierung beim Start bzw. bei Rückkehr aus dem Hintergrund.
- Einstellungen: Sprache, **Währungskennzeichen** (Standard; wird beim `.kmy`-Import je Konto aus der
  Datei übernommen), Nextcloud-Zugang (App-Passwort verschlüsselt), Export-Modus (CSV/`.kmy`),
  Standardkonto, Orte je Konto, Alias-Namen, Hell-/Dunkel-Design, Datenbank-Backup/Restore, Konto löschen.

## Wear OS (Sprach-Schnellerfassung)

Ein zusätzliches Modul `:wear` erlaubt das Erfassen einer Bargeldausgabe per Sprache direkt auf einer
Wear-OS-Uhr („Frisör 20 Euro"). Die Uhr nimmt nur den Text auf; die eigentliche Verarbeitung (derselbe
Parser wie am Phone) und die Buchungsanlage passieren auf dem Smartphone.

- **Uhr**: ein Screen „Buchung erfassen" mit drei Typ-Knöpfen (Einnahme grün, Umbuchung gelb, Ausgabe
  rot) + Wear-Tile („Widget"). Nach der Typwahl startet die Sprache; der erkannte Text wird 10 Sekunden
  mit „Abbrechen" angezeigt und – falls nicht abgebrochen – ohne weitere Aktion verarbeitet. Der Typ
  wird mitübertragen und auf dem Phone erzwungen. Ist das Phone offline, steht unter den Knöpfen
  „x Buchungen noch nicht übertragen".
- **Stille Zifferneingabe**: auf dem „Sprich jetzt"-Schirm gibt es unten ein **Ziffern-Symbol** →
  Zahlenblock (0–9 + Komma, Rücktaste, Enter, oben die eingegebene Zahl). So lässt sich eine Buchung
  **lautlos** erfassen; der Betrag wird am Phone über den aktuellen Standort aufgelöst (siehe „Nur den
  Betrag erfassen").
- **Offline & Sync**: Die Übertragung läuft **vollautomatisch** über die Wear Data Layer API als
  **DataItem** (`DataClient`). Ist das Phone nicht erreichbar, bleibt der Eintrag PENDING; der Data Layer
  stellt ihn bei Wiederverbindung automatisch zu (ohne dass die Uhr wach bleiben oder pollen muss). Das
  Phone verarbeitet ihn und löscht den DataItem – das bestätigt der Uhr die Übertragung.
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
- [Room](https://developer.android.com/training/data-storage/room) (SQLite, DB-Version 13), OkHttp
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

## Sync-Ziel einrichten (Nextcloud / WebDAV / SMB)

In den Einstellungen den **Server-Typ** wählen, dann Basis-URL/Freigabe, Benutzername und Passwort
eintragen, optional Ziel-/Importordner bzw. im `.kmy`-Modus den Pfad zur `.kmy`-Datei. Ein Button
**„Verbindung testen"** prüft die Zugangsdaten; im `.kmy`-Modus listet **„.kmy auswählen"** die
`.kmy`-Dateien der Freigabe/des Ordners zur Auswahl. Ohne konfiguriertes Sync-Ziel wird lokal in einen per
SAF gewählten Ordner exportiert.

- **Nextcloud**: Basis-URL = Server (z. B. `https://cloud.example.com`) und ein **App-Passwort**
  (Nextcloud → Einstellungen → Sicherheit → App-Passwort). Die Dateien landen unter
  `<URL>/remote.php/dav/files/<user>/<Ordner>/`.
- **WebDAV (generisch)**: Basis-URL = vollständige DAV-Wurzel (z. B.
  `https://host/dav/`); der Ordner wird direkt daran angehängt. Auth per HTTP-Basic.
- **SMB/Samba**: „URL" = `smb://Host/Freigabe` (optional mit Basis-Unterordner); Ordner- und
  `.kmy`-Pfade sind relativ zur Freigabe. Benutzer/Passwort wie üblich – **leerer Benutzer = Gast**;
  eine Windows-Domäne als `DOMÄNE\Benutzer`. SMB2/3.
