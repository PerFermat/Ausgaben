# Qualitätsprüfung der Änderungen seit v1.11

Stand: 2026-09-01 · Grundlage: `git diff v1.11..HEAD` (49 Commits, 165 Dateien,
+31.349/−4.930; davon ca. 10.000 Zeilen Produktivcode in `app/src/main`).

Geprüfte Schwerpunkte: Wertpapier-Buchungen (`SecurityTx*`), PDF-Abrechnungs-Erkennung
(`statement/`, `pdf/`), Profile (`ProfileManager`, `ProfileSettingsActivity`),
Backup/Restore, KMy-Export/Import.

Nicht geändert und daher nicht geprüft: `sync/`, `net/` (WebDAV/SMB-Transport), `wear/`.

`./gradlew :app:testFullDebugUnitTest` läuft grün durch (Exit 0).
`values/strings.xml` und `values-de/strings.xml` sind deckungsgleich (keine fehlenden
oder verwaisten Schlüssel).

---

## Die zehn wichtigsten Probleme

| # | Schwere | Datei | Kurzfassung |
|---|---------|-------|-------------|
| 1 | Kritisch | `settings/SettingsStore.java:160` | Server-Passwort geht beim Update 1.11→1.12 verloren |
| 2 | Kritisch | `db/DepotRepository.java:60` | Depot-Reimport löscht Kategoriezeilen offener Bewegungen |
| 3 | Kritisch | `db/Repository.java:1476` | Depot löschen lässt verwaiste `pending`-Bewegungen zurück |
| 4 | Hoch | `ui/SecurityTxEditActivity.java` | Kein `onSaveInstanceState`: Rotation macht die Maske unbenutzbar |
| 5 | Hoch | `ui/SecurityTxEditActivity.java:316` | Doppelklick auf „Speichern" legt die Buchung zweimal an |
| 6 | Hoch | `statement/StatementTemplate.java:374` | Nachlernen verliert Spalten-/n-te-Zahl-Regeln |
| 7 | Hoch | `ui/SecurityTxEditActivity.java:1779` | PDF-Auslese auf dem Bedienfaden → ANR |
| 8 | Hoch | `settings/ProfileManager.java:257` | Profilwechsel öffnet die Datenbank blockierend → ANR |
| 9 | Hoch | `backup/BackupStore.java:96` | v1.11-Sicherung einspielen verliert die globalen Einstellungen |
| 10 | Hoch | `ui/ProfileSettingsActivity.java` (14 Stellen) | Nackte Threads mit Activity-Referenz, keine Lebenszyklusprüfung |

---

## 1. Kritisch — Server-Passwort geht beim Update verloren

**Kategorie:** Bug (Datenverlust) · **Datei:** `settings/SettingsStore.java:160`,
`settings/ProfileManager.java:136`

In v1.11 lag das Passwort verschlüsselt unter dem **unpräfixierten** Schlüssel
`nextcloud_password`:

```java
// v1.11
public String getPassword() { return secret.getString(KEY_PASSWORD, ""); }
```

Ab 1.12 wird profilpräfixiert gelesen:

```java
public String getPassword() { return secret.getString(pk(KEY_PASSWORD), ""); }
// pk("nextcloud_password") -> "p_<profil>_nextcloud_password"
```

`copyLegacySettingsUnderPrefix` zieht aber ausschließlich die Klartext-Prefs um; für das
Passwort verweist der Kommentar auf `migratePlaintextPassword()` — und die greift nur,
wenn das Passwort noch **unverschlüsselt** in `ausgaben_settings` steht. Genau das hat
v1.11 bereits aufgeräumt (`prefs.edit().remove(KEY_PASSWORD)`).

**Folge:** Jede Installation mit eingerichteter Nextcloud-/WebDAV-/SMB-Quelle verliert
beim Update das Passwort. `hasNextcloudConfig()` wird `false`, Sync und Export scheitern
mit Auth-Fehler. Der Nutzer sieht keinen Hinweis auf die Ursache.

**Lösung:** in `copyLegacySettingsUnderPrefix` die Secret-Prefs mit umziehen:

```java
SharedPreferences secret = SettingsStore.secretPrefs(app);
if (secret.contains("nextcloud_password")) {
    secret.edit()
          .putString(prefix + "nextcloud_password",
                     secret.getString("nextcloud_password", ""))
          .remove("nextcloud_password")
          .apply();
}
```

---

*Nachtrag (behoben):* Die Migration einer Bestandsinstallation zieht das Server-Passwort mit unter
das Profil-Präfix (`ProfileManager.copyLegacySettingsUnderPrefix`, dort auch der Zweig für die
verschlüsselte Ablage). Test: `ProfileManagerTest`.

## 2. Kritisch — Depot-Reimport löscht die Kategoriezeilen offener Bewegungen

**Kategorie:** Bug (stiller Datenverlust) · **Datei:** `db/DepotRepository.java:60-61`

```java
securityDao.deleteSplitsOf(depot);   // DELETE ... WHERE tx_id IN (SELECT id FROM security_tx WHERE depot = :depot)
securityDao.deleteTx(depot);         // DELETE FROM security_tx WHERE depot = :depot AND pending = 0
```

`deleteTx` schont bewusst die noch nicht exportierten Bewegungen; `deleteSplitsOf` kennt
das `pending`-Kriterium nicht. Eine in der App erfasste, noch nicht exportierte Bewegung
überlebt den Reimport also als Zeile, **verliert aber alle `security_tx_split`-Zeilen**.

Beim nächsten Export läuft sie in `KmyExporter.addCategorySplits` auf `parts.isEmpty()`
und wird übersprungen; die zugehörige Geldbuchung ist über `BookingDao:208` zugleich aus
`getUnexported()` ausgeschlossen. Buchung und Bewegung verschwinden dauerhaft und
unbemerkt aus dem Export.

**Lösung:** Bedingung angleichen —

```sql
DELETE FROM security_tx_split WHERE tx_id IN
    (SELECT id FROM security_tx WHERE depot = :depot AND pending = 0)
```

---

*Nachtrag (behoben):* `SecurityDao.deleteImportedTx` und `deleteImportedSplitsOf` gehören zusammen —
beide lassen `pending`-Zeilen stehen, und der Javadoc sagt das an beiden Stellen. Vorher blieb die
Bewegung, ihre Kategoriezeilen verschwanden. Test: `DepotReimportPendingTest`.

## 3. Kritisch — Depot löschen lässt verwaiste Bewegungen zurück

**Kategorie:** Bug · **Datei:** `db/Repository.java:1475-1479`

```java
securityDao.deleteSplitsOf(name);
securityDao.deleteTx(name);        // wieder nur pending = 0
securityDao.deleteSecurities(name);
securityDao.deletePrices(name);
accountDao.deleteByName(name);
```

Wertpapier, Kurse und Kontozeile sind weg, die `pending`-Bewegungen bleiben.
`getPendingTx()` findet sie beim Export weiterhin, `KmyExporter.securitySplits` findet
weder Depot noch Wertpapier und meldet sie bei **jedem** Export als übersprungen — ein
Zustand, aus dem der Nutzer nicht mehr herauskommt.

Zusätzlich läuft der Block ohne `db.runInTransaction(...)`, anders als alle anderen
mehrschrittigen Operationen der Klasse. Ein Abbruch in der Mitte hinterlässt ein halb
gelöschtes Depot.

**Lösung:** eigene, bedingungslose DAO-Abfrage `deleteAllTx(depot)` für den Löschpfad
(`deleteTx` bleibt für den Reimport) und den Block in `runInTransaction` fassen.

---

*Nachtrag (behoben):* Beim Löschen eines Depots gehen Bewegungen und ihre Kategoriezeilen in
<b>einer</b> Transaktion mit. Test: `DepotDeleteTest`.

## 4. Hoch — `SecurityTxEditActivity` überlebt keine Bildschirmdrehung

**Kategorie:** Bug (Lebenszyklus) · **Datei:** `ui/SecurityTxEditActivity.java`

Die Klasse hat kein `onSaveInstanceState` und im Manifest kein `configChanges`. Der
gesamte fachliche Zustand liegt in Instanzfeldern: `dateKnown`, `actionKnown`, `userSet`,
`typedFields`, `dateTyped`, `chosenDateLabel`, `chosenDateRule`, `lastComputed`,
`conflict`, `savedStatementTag`, `dupBooked`. `onCreate` liest danach nur die
Intent-Extras neu.

**Folgen nach einer Drehung:**

- Das Datumsfeld bekommt seinen Text über die View-State-Wiederherstellung zurück,
  `dateKnown` bleibt aber `false` → `save()` bricht mit „Datum fehlt" ab, obwohl das
  Datum sichtbar im Feld steht. Für den Nutzer nicht auflösbar.
- `typedFields`/`dateTyped` sind leer → `offerToLearn` bricht ab, das Lernen der
  Bank-Vorlage unterbleibt lautlos. Das ist der Kern des neuen Features.
- `chosenDateRule` ist weg → der gelernte Datumsanker fehlt oder ist falsch.

**Lösung:** `onSaveInstanceState`/`onRestoreInstanceState` für diese Flags (Enum-Mengen
als `String[]`, die Regel als Ordinal-Tripel), oder Umzug in ein `ViewModel` mit
`SavedStateHandle`.

Gleiches Muster, kleinere Wirkung: `StatementBatchActivity.editing` (Index wird nicht
gesichert → `applyCorrection` verwirft die Korrektur wortlos) und
`StatementRulesActivity` (Testdatei und alle ungespeicherten Regeländerungen weg).

**Zusatzbefund:** `StatementRulesActivity` inflatiert `item_statement_field.xml` und
`item_statement_anchor.xml` mehrfach in dieselbe Hierarchie. Alle Kinder tragen
identische IDs, und Androids `saveHierarchyState` schlüsselt über die View-ID. Bei mehr
als einer Ankerzeile bekommen nach der Drehung **alle** gleichnamigen Felder den zuletzt
gesicherten Wert — Anker und Beträge werden vertauscht. Abhilfe:
`view.setSaveFromParentEnabled(false)` an den wiederholten Zeilen.

---

*Nachtrag (behoben):* `onSaveInstanceState`/`restoreState` sichern, was die Views selbst nicht
tragen — Datum, Merker, Mengen, Datumsregel. Test: `SecurityTxRotationTest`. Dieser Fix hat später
Punkt 35 in seinem schwersten Teil miterledigt (siehe dort).

## 5. Hoch — Doppelklick auf „Speichern" legt die Buchung zweimal an

**Kategorie:** Bug (Race) · **Datei:** `ui/SecurityTxEditActivity.java:316`, `save()` ab 1144

```java
btnSave.setOnClickListener(v -> save());
```

Kein Guard-Flag, kein `setEnabled(false)`. Das Speichern läuft asynchron
(`repository.saveManualSecurityTx`), `finish()` passiert erst später in `offerToLearn`
oder nach den Lerndialogen — die Maske bleibt sichtbar offen.

**Folge:** Bewegung **und** Gegenbuchung werden doppelt angelegt. Beim zweiten Durchlauf
ist `pendingStatement` bereits `null`, die Belegkopie hängt also nur an der ersten
Buchung.

`StatementBatchActivity` macht es mit dem Feld `saving` bereits richtig — das Muster
gehört auch hierher.

---

*Nachtrag (behoben):* Der `saving`-Riegel in `save()`, und der Speichern-Knopf wird gesperrt, sobald
geschrieben ist. Test: `SecurityTxSaveGuardTest`.

## 6. Hoch — Nachlernen verliert die Spalten- und n-te-Zahl-Regeln

**Kategorie:** Bug · **Datei:** `statement/StatementTemplate.java:374-375`

```java
merged.put(field, anchors.size() == old.anchors.size() ? old
        : new AnchorRule(anchors, old.direction, old.sum, old.currency));
```

Der 4-Argument-Konstruktor (`AnchorRule:133`) setzt `position = LAST`, `nth = 1`,
`lineDistance = 0`. Eine gelernte Spaltenregel (`Position.COLUMN`) oder eine
„2. Zahl von rechts"-Regel fällt bei der ersten Ergänzung um einen Anker still auf den
Regelfall zurück und liest ab dann die falsche Zahl aus dem Dokument.

`mergedOver` hat das Problem nicht, weil es ganze Regeln übernimmt.

**Lösung:**

```java
: new AnchorRule(anchors, old.direction, old.sum, old.currency,
                 old.position, old.nth, old.lineDistance);
```

---

*Nachtrag (behoben):* `StatementTemplate.mergedOver`/`appendedTo` behalten Spalten- und
n-te-Zahl-Regeln. Getestet in den Lerner-Tests dieser Runde.

## 7. Hoch — PDF-Auslese auf dem Bedienfaden (ANR)

**Kategorie:** Performanceproblem · **Datei:** `ui/SecurityTxEditActivity.java:1779`

`statementDates()` ruft beim ersten Tipp aufs Datumsfeld `readStatementText()` →
`liesAbrechnung()` → `PdfTextExtractor.read(...)` bzw. `Files.readAllBytes(...)` — alles
synchron im Click-Handler. Direkt danach folgt `StatementScan.dates(text)`, das für jede
Zeile eine Probe-Regel baut und damit das ganze Dokument erneut durchläuft (O(L²), siehe
Punkt 12).

**Folge:** mehrsekündiges Einfrieren bis ANR bei mehrseitigen Abrechnungen — genau das,
was der Kommentar an Zeile 226 vermeiden will. Der dortige Schutz greift nur über
`offerToLearn`, das beim Tipp aufs Datum noch nicht gelaufen ist.

Gleiches Muster: `StatementImport.start` kopiert die PDF-Datei (`SingleReceipt.stage`)
und schreibt den Textcache im `getAllSecurities`-Callback, also auf dem Main-Thread;
`StatementBatchActivity.saveAll` hängt je Entwurf eine Datei an, bei 20 Abrechnungen
alle auf dem UI-Thread.

**Lösung:** das PDF beim Öffnen der Maske einmal auf `repository.executor()` einlesen,
Datei-Operationen in den Executor verschieben, Datumsdialog erst nach Vorliegen zeigen.

---

*Nachtrag (behoben):* Das Einlesen läuft über `repository.executor()`; der Dialog kommt danach auf
dem Bedienfaden, mit `isFinishing()/isDestroyed()`-Prüfung davor.

## 8. Hoch — Profilwechsel blockiert den Hauptthread

**Kategorie:** Performanceproblem · **Datei:** `settings/ProfileManager.java:257-267`

`switchTo` wird im Dialog-Callback auf dem UI-Thread gerufen
(`ProfileSettingsActivity:427`, `:713`). Es ruft `LocaleManager.init(context)`, das mit
`f.get()` blockierend wartet, bis `AppDatabase.getInstance` die neue Datei geöffnet,
migriert und über `seed()` mit sämtlichen Übersetzungen befüllt hat. Bei einem frisch
angelegten Profil ist das ein kompletter Schema-Aufbau plus Seeding, nach einem Restore
zusätzlich die Migrationskette. Danach folgt noch `BalanceSync.publish(context)`.

**Verwandt (Hoch):** `AppDatabase.closeInstance()` wird von `switchTo`,
`BackupStore.restoreProfileData` und dem Profil-Vergleich in `getInstance` gerufen,
während lebende `Repository`-Instanzen die DAOs aus dem Konstruktor festhalten
(`Repository:56`). Eine laufende Aufgabe auf `Repository.executor` — etwa ein
`replaceDepotImport` in `runInTransaction` — trifft dann auf eine geschlossene Instanz:
`IllegalStateException` auf dem Executor-Thread, ohne Handler.

Der Schutz `importRunning` in `ProfileSettingsActivity:68` hängt selbst an einem
Instanzfeld ohne Persistenz — nach einer Drehung während des Imports ist er weg und der
Absturz, den er verhindern soll, wieder möglich.

**Lösung:** `switchTo` asynchron ausführen und die Activity erst im Callback neu starten;
vor `close()` den Executor drainen oder DAOs lazy über `getInstance()` auflösen.

---

*Nachtrag (behoben):* `ProfileManager.switchTo` schließt die Datenbank und lässt den Aufrufer den
Stack zurücksetzen, statt im Vordergrund zu arbeiten.

## 9. Hoch — v1.11-Sicherung einspielen verliert die globalen Einstellungen

**Kategorie:** Bug (Datenverlust) · **Datei:** `backup/BackupStore.java:96-119`

`restoreProfileSettings` präfixiert **jeden** Schlüssel des Archivs mit `p_<aktiv>_`.
Eine v1.11-Sicherung enthält aber alle Schlüssel unpräfixiert — darunter die heute
globalen: `language`, `night_mode`, `font_size`, Kategoriefarben, App-Sperre,
`receipt_enabled`, `smb_known_hosts`. Die landen unter einem Präfix, das niemand liest.
Ebenso werden die Dateien `receipts` und `widget_selection` präfixiert und damit
unlesbar.

**Lösung:** über `content.format`/`content.scope` verzweigen; für Altarchive nur die
bekannten Profil-Schlüssel präfixieren, den Rest unpräfixiert zurückschreiben.

---

*Nachtrag (behoben):* `BackupStore.restoreProfileSettings` unterscheidet über
`content.format < FORMAT_PROFILES`, welche Schlüssel unters Profil-Präfix gehören und welche global
bleiben. Test: `BackupLegacyRestoreTest`.

## 10. Hoch — 14 nackte Threads mit Activity-Referenz

**Kategorie:** Risiko (Speicherleck, Absturz) · **Datei:** `ui/ProfileSettingsActivity.java`
Zeilen 740, 778, 842, 895, 955, 983, 1032, 1079, 1091, 1195, 1219, 1248, 1311, 1356

Alle Netz- und Import-Operationen laufen als `new Thread(() -> …)` mit `runOnUiThread`
und impliziter `this`-Referenz. Keine Abbruchmöglichkeit, in `onDestroy` (nur
`smbWizard.stopDiscovery()`) nicht berücksichtigt. SMB/WebDAV-Timeouts liegen im
zweistelligen Sekundenbereich.

**Folge:** Activity-Leak über die gesamte Timeout-Dauer; `Toast`-, Dialog- und
`View`-Zugriffe (`finishImport` fasst `importProgress`/`importStatusText` an) auf
zerstörte Instanzen; bei `startBatchImport` zusätzlich DB-Schreibvorgänge, die eine
geschlossene Verbindung treffen können.

Dasselbe Muster ohne Lebenszyklusprüfung: `StatementImport.open`/`openAll`
(`AppDialog(...).show()` nach der Zerstörung → `WindowManager$BadTokenException`) und
`StatementRulesActivity.useTestStatement`.

`SecurityTxEditActivity.offerToLearn:1345` setzt den Guard vorbildlich — er fehlt nur
überall sonst.

**Lösung:** `repository.executor()` statt roher Threads; ein gemeinsamer
Guard-Wrapper für Repository-Callbacks in `LocalizedActivity`.

---

## Weitere Funde

### Bugs und Risiken

*Nachtrag (behoben):* Die Fäden laufen über den Executor des Repositories beziehungsweise über
Regler mit `imVordergrund`-Prüfung (`BackupRestoreController`, `SyncFieldsController`).

**11. Hoch — Negative Gebühr bei Anleihe-Verkauf.** `statement/bank/IngReader.java:140`:
`into.feeCents = Math.abs(into.netCents - kurswert) - stueckzinsen;`. Beim Kauf gilt
`netto = kurswert + stückzinsen + kosten`, die Rechnung stimmt. Beim Verkauf gilt
`netto = kurswert + stückzinsen − kosten`, also `|netto − kurswert| = stückzinsen − kosten`;
nach Abzug von `stueckzinsen` kommt `−kosten` heraus. Vorzeichen an der Aktion
festmachen und das Ergebnis auf `>= 0` prüfen.

*Nachtrag (behoben):* `IngReader.nichtNegativ` — eine Gebühr kann nicht negativ sein; bei
Stückzinsen kam sonst ein Minus heraus. Test: `IngReaderTest`.

**12. Hoch — Quadratische Laufzeit der Erkennung.** `StatementScan.values`/`dates` bauen
für **jede** Zeile eine Probe-Regel und rufen `probe.read(text)`, das seinerseits alle
Zeilen durchläuft → O(L²); in den Tabellenzweigen kommt `columnRuleFor` mit bis zu sechs
weiteren Vollscans dazu → O(L²·W). Verstärkend: `PdfText.Line.text()` baut den Zeilentext
bei jedem Aufruf neu auf, `AnchorRule.afterAnchor` legt bei jedem Aufruf
`line.toLowerCase()` neu an, und `AnchorRule.matchedAnchor` wird in
`StatementRulesActivity:664` bei **jeder Tastatureingabe** gerufen.
Abhilfe: `Line.text()` und dessen Kleinschreibung im unveränderlichen `Line`-Objekt
einmalig cachen; Kandidatenlisten in einem Durchlauf aufbauen.

*Nachtrag (behoben):* `StatementScan.values`/`dates` bauen ihre Kandidatenliste in einem Durchgang
statt je Zeile neu.

**13. Hoch — `parseDate` ignoriert Resttext.** `util/TextValues.java:229-237`:
`SimpleDateFormat.parse(String)` parst nur das Präfix. `"11.6.2022-01:30:01"` wird als
11.06.2022 gelesen — genau der Fall, den der Javadoc an Zeile 87 ausschließt. Schwerer
wiegt: `SLASH_DATE` ist mit `^…$` verankert, `"03/08/2026 12:00"` matcht also nicht, die
Mehrdeutigkeitssperre in `toUnambiguousDateMillis` greift nicht, und `toDateMillis`
liefert über `MM/dd/yyyy` den 8. März statt zu verweigern. Datumstoken mit angehängter
Uhrzeit sind auf Abrechnungen die Regel („Schlusstag/-Zeit").
Abhilfe: mit `ParsePosition` parsen und verwerfen, wenn `pos.getIndex() != s.length()`.

*Nachtrag (behoben):* `parseDate` liest über `ParsePosition` und verlangt, dass das ganze Token
verbraucht ist. Test: `TextValuesTest.einDatumMitAngehaengtemTextIstKeinDatum` und
`dieMehrdeutigkeitssperreLaesstSichNichtMitUhrzeitUmgehen`.

**14. Hoch — `parseDate` als Exception-Schleuder.** Dieselbe Methode legt pro Muster ein
neues `SimpleDateFormat` an und lässt es scheitern — elf `ParseException`-Objekte samt
Stacktrace für jedes Nicht-Datum. Aufgerufen wird das je Token in drei
Gruppierungslängen (`AnchorRule.dateStartingAt`), je Wort jeder Zeile
(`TemplateLearner.datenIn`) und je Zeile (`StatementScan.dates`). Bei 20.000 Wörtern
sind das grob 10⁶ geworfene Exceptions pro Durchgang. Muster in `ThreadLocal` halten,
billigen Vorfilter-Regex vorschalten.

*Nachtrag (mit 13 erledigt):* Dieselbe Umstellung auf `ParsePosition` spart die `ParseException` je
Muster — bei einem mehrseitigen Beleg sonst Hunderttausende, nur um verworfen zu werden. Steht so im
Kommentar der Methode.

**15. Hoch — Zip-Slip beim Wiederherstellen.** Die Profil-ID stammt aus dem
Zip-Eintragsnamen (`BackupArchive:153`) und geht ungeprüft in
`ProfileManager.dbFileNameFor` → `"ausgaben_" + id.replace("-","") + ".db"` →
`getDatabasePath(...)`. Ein Eintrag `db/../../shared_prefs/x.db` schreibt außerhalb des
`databases`-Verzeichnisses. Analog nimmt `restoreAllSettings` den Prefs-Dateinamen direkt
aus dem Archiv und reicht ihn an `getSharedPreferences`. `FullBackupRestoreFlow`
akzeptiert bewusst `*/*`. Eintragsnamen gegen `[A-Za-z0-9_-]+` validieren.

*Nachtrag (behoben, zwei Ebenen):* `BackupArchive.geprueft` prüft das Namensstück zwischen Ordner
und Endung und bricht das Einlesen mit `IOException` ab, statt einen Teil davon auszuführen — ein
beschädigtes oder gebasteltes Archiv soll nicht halb eingespielt werden. Darüber hinaus schreibt
`BackupStore` nur noch Einstellungsdateien, deren Name auf der bekannten Liste steht
(`bekannteEinstellungsdatei`): ein formal einwandfreier Name wie `fremde_datei` käme sonst als neue
Datei im App-Verzeichnis an. Unbekannte Namen werden dort **übergangen und nicht abgelehnt**, damit
eine Sicherung aus einer neueren Fassung weiterhin einspielbar bleibt. Tests:
`BackupArchiveTest.eintragMitPfadangabeBrichtDasEinlesenAb` (samt Gegenstück
`gewoehnlicheNamenGehenWeiterhinDurch`, damit die Prüfung nicht einfach alles ablehnt) und
`BackupUnknownPrefsFileTest`. Beide Ebenen einzeln gegengeprüft: ohne die jeweilige Änderung sind
genau die zugehörigen Tests rot.

**16. Hoch — `TemplateLearner.subsetSummingTo` ist Θ(n³).** Vollständige Enumeration
aller 2er- und 3er-Teilmengen ohne Beschneidung (`TemplateLearner:669-697`). Bei n = 2000
Zeilen mit brauchbarer letzter Zahl sind das ~1,3·10⁹ Rekursionsschritte — praktisch ein
Hänger, ausgelöst durch einen Sammelbeleg oder eine Jahressteuerbescheinigung, bei der
der gesuchte Betrag als Summe gar nicht vorkommt. n deckeln; für `size = 2` eine Hashmap
über `target − value` (O(n)).

*Nachtrag nach der Messung (behoben):* Die Größenordnung stimmt, die Einstufung „Hoch"
war zu hoch gegriffen. Gemessen an einem Beleg mit 900 auswertbaren Zeilen brauchte die
alte Fassung 1,1 Sekunden — spürbar, aber kein Hänger; erst bei 2500 Zeilen sind es 28
Sekunden. Ein realistischer Beleg bleibt darunter. Gelöst ist es jetzt über aufsteigende
Sortierung mit Präfixsummen-Schranken (0,3 s bei 2500 Zeilen), festgehalten in
`LearnerPerformanceTest`.

**17. Hoch — Zwei Parser für dieselbe Maske.**
`SecurityTxEditActivity.number()` benutzt `Double.parseDouble(raw.replace(',', '.'))`,
während `money()` durch `AmountExpression.toCents` geht (versteht `+`, `-`, `*`). Tippt
der Nutzer über die Rechentastatur `10*3` in „Anzahl" und drückt direkt auf Speichern,
liefert `number()` `null` → pauschales „Beträge fehlen", obwohl sichtbar ein Wert im Feld
steht. In `duplicateCandidate` und `expectation` fällt die Stückzahl still weg, wodurch
Doppelungsprüfung und Lern-Nachprüfung falsche Ergebnisse liefern. Denselben Parser
verwenden und `setError` am Feld statt eines Sammel-Toasts zeigen.

*Nachtrag (behoben):* `number()` geht jetzt durch `AmountExpression.evaluate` — `evaluate` und nicht
`toCents`, denn eine Stückzahl hat mehr Nachkommastellen als Geld und darf nicht auf Cent gerundet
werden. Dazu markiert `save()` das Feld, an dem es hakt (Brutto, Netto, Anzahl), statt nur „Beträge
fehlen" zu melden; die Markierung fällt weg, sobald jemand in das Feld tippt. Test:
`SecurityTxNumberParserTest` — beide Hälften einzeln gegengeprüft.

**18. Hoch — `clearAll` löscht vor dem Schließen.** `ProfileManager:427-433` löscht die
Datenbankdateien und ruft erst danach `AppDatabase.closeInstance()`. Das `close()`
schreibt WAL-Inhalt zurück und legt die Datei wieder an — der Werksreset hinterlässt je
nach Timing eine Rest-Datenbank, `-wal`/`-shm` werden nicht erneut entfernt.
`closeInstance()` gehört als erste Anweisung nach oben.

*Nachtrag (behoben):* Erst `AppDatabase.closeInstance()`, dann löschen — sonst schreibt das spätere
`close()` den WAL-Puffer zurück und die eben gelöschte Datenbank steht wieder da. Der Grund steht als
Kommentar an der Stelle.

**19. Mittel — Cent-Rundung über `double`.** `AnchorRule.readCents:329`:
`Math.round(value * 100.0)`. `TextValues.toCents` rundet bewusst mit
`BigDecimal.HALF_UP`; hier wird derselbe Betrag über `double` geführt. `2.675` ist als
`double` `2.67499999999999982` → **267 statt 268 Cent**. Bei `sum`-Regeln addieren sich
mehrere solcher Fehler. `TemplateCheck.gleich:267` rechnet genauso, die Selbstprüfung
sieht den Fehler also nicht.

*Nachtrag (behoben):* Gerechnet wird über `TextValues.centsOf` mit `BigDecimal`. Test:
`TextValuesTest.centsOfRundetWieAufDemBeleg` und `centsOfUndToCentsStimmenUeberein` — der zweite hält
fest, dass Auslese und Selbstprüfung dieselbe Zahl bekommen; vorher machten beide denselben Fehler
und die Probe merkte nichts.

**20. Mittel — Jede 3-Buchstaben-Endung gilt als Währung.**
`TextValues.toBigDecimal:113`: `isCurrencyCode` prüft nur „drei Großbuchstaben". Damit
wird `"1437STK"` zu `1437` und `"2019DEC"` zu `2019`. Diese Pseudo-Zahlen landen als
`numberTokens` in `AnchorRule.numberAt`/`lastNumber` — genau das, was der Javadoc an
Zeile 84 verhindern will. Asymmetrisch außerdem: ein vorangestelltes `"EUR158,73"` wird
nicht abgeschält. Gegen eine Liste realer Kürzel prüfen.

*Nachtrag (behoben):* Neue `util/Currencies` mit der ISO-4217-Liste (plus abgelöster Kürzel
wie DEM/ATS für alte Belege). Alle drei Prüfstellen - `TextValues`, `TemplateLearner`,
`AnchorRule` - gehen jetzt darüber; das vorangestellte Kürzel (`EUR158,73`) wird zusätzlich
abgeschält. Test: `TextValuesTest.einStueckzusatzIstKeineWaehrung`.

**21. Mittel — SEDOL/CUSIP-Fehlerkennungen.** Die SEDOL-Spezifikation schließt die Vokale
A, E, I, O, U aus; `Sedol.value:50` lässt A–Z zu und `CANDIDATE` erlaubt
`[0-9A-Z]{6}[0-9]`. Damit besteht jede 7-stellige Referenznummer die Prüfung mit
p ≈ 1/10; dasselbe gilt für CUSIP mit `[0-9A-Z]{8}[0-9]` und den auf Kontoauszügen
üblichen neunstelligen Auftragsnummern. Über `StatementScan.isin` wird ein solcher
Treffer in `StatementTemplates.rememberSecurity` dauerhaft einem Wertpapier zugeordnet.
(Die Prüfziffer-Algorithmen selbst sind bei allen dreien korrekt — nachgerechnet mit
US0378331005 und 037833100.)
Abhilfe: `CANDIDATE` auf `[B-DF-HJ-NP-TV-Z0-9]{6}[0-9]` einschränken, Vokale in `value`
ablehnen.

*Nachtrag (teilweise behoben):* SEDOL nimmt jetzt nur noch das spezifizierte Alphabet ohne
Vokale - im Muster wie in `value`. Für **CUSIP bleibt es offen**: eine rein numerische CUSIP
ist gültig (037833100 = Apple), eine neunstellige Auftragsnummer sieht genauso aus, und über
das Muster ist beides nicht zu trennen. Entschärfend wirkt nur, dass `single()` bei mehreren
Treffern gar keinen nimmt. Wer das schließen will, braucht Kontext (etwa das Wort CUSIP in
derselben Zeile), nicht ein engeres Muster.

**22. Hoch — Vorzeichen einer Kategoriezeile geht verloren.** (Der ursprüngliche Befund
lautete anders und war falsch; siehe unten.)

Eine Kategoriezeile darf gegen die Richtung ihrer Rolle laufen — Zinsertrag 100 €,
Kapitalertragsteuer −20 €, Gutschrift 80 €. Die Erfassungsmaske rechnet genau so:
`SplitRowController.parseCents` geht durch `AmountExpression` und nimmt `-20` an,
`isValid()` summiert vorzeichenbehaftet und erklärt die Eingabe für stimmig. Beim
Speichern machte `SecurityTxEditActivity.splitsOf` daraus `Math.abs(part.cents)` — in der
Datenbank standen danach 100 und 20, Summe 120 statt 80. Die Maske sagte „passt", abgelegt
wurde etwas anderes.

Dasselbe `Math.abs` an vier Stellen: Erfassungsmaske, `StatementDraft` (Stapelweg),
`KmyImporter.fillOrigin` (Einlesen aus der `.kmy`) und `KmyExporter.addCategorySplits`.
Dazu ein Klassenkommentar an `SecurityTxSplit`, der ausdrücklich „immer positiv"
behauptete.

**Kein Erbstück:** `SecurityTxSplit` gibt es in `v1.11` nicht — die Aufteilung einer
Depotbewegung in mehrere Kategoriezeilen ist neu seit 1.11. Das ältere Geschwisterteil
`BookingSplit` gab es dagegen schon, und sein Klassenkommentar sagt seit jeher:
*„Teilbeträge sind vorzeichenbehaftet (auch negativ erlaubt)"*. Der neue Code hat also
nicht einen bestehenden Fehler geerbt, sondern von der eigenen, dokumentierten Konvention
abweichend neu gebaut — bei unveränderter Maske, die weiter vorzeichenbehaftet rechnet.

*Behoben:* Alle vier `Math.abs` entfernt bzw. ersetzt; im Importer durch die Gegenrechnung
zum Export (Ertragsteil durch sein Vorzeichen teilen statt einebnen), damit eine
gutgeschriebene Steuer beim Einlesen nicht zum Abzug wird. Klassenkommentar an
`SecurityTxSplit` richtiggestellt. Test:
`KmySecurityExportTest.eineAbzugszeileBehaeltIhrVorzeichenInJederReihenfolge`, geprüft in
**beiden Reihenfolgen** — daran hängt alles: die letzte Zeile bekommt ihr Vorzeichen
ohnehin über den `rest`, der Fehler zeigte sich nur, wenn die gegenläufige Zeile davor
stand. Die Gegenprobe bestätigt das: mit `Math.abs` fällt genau die zweite Zusicherung.

*Bestandsdaten:* nicht wiederherstellbar. Wo das Vorzeichen bereits weggeschrieben wurde,
ist es weg; die Änderung wirkt ab jetzt.

*Nachgeprüft — der Regelweg ist unberührt:* Die Rechnung Netto = Brutto − Steuer liegt in
`SecurityAmounts:208` (`netCents = grossCents + s * feeCents`, `s = -1`), und diese Datei ist
ebenso unverändert wie `SplitRowController`. In der Steuerliste müssen die Werte deshalb
weiterhin **positiv** sein: `isValid()` verlangt, dass die Zeilen ihr Summenfeld treffen und
dass dieses `> 0` ist. Negative Zeilen sind nicht beliebig erlaubt, sondern nur dort möglich,
wo die Summe trotzdem stimmt. Bei positiven Werten ist `Math.abs(2600) == 2600` — die Änderung
ist dort wirkungslos. Festgehalten in
`KmySecurityExportTest.diePositiveSteuerWirdWeiterhinVomBruttoAbgezogen` (ohne Gegenprobe: der
Test sichert einen unveränderten Zustand, keine Korrektur) und
`SecurityTxSplitSignTest`.

*Absichtlich absolut geblieben:* `SecurityTxEditActivity.lernbareTeile` und `partAmounts`. Der
Lerner sucht den Betrag im Text der Abrechnung, und dort steht die Kapitalertragsteuer als
positive Zahl unter ihrer Beschriftung. `TemplateCheck.compareParts` vergleicht ebenfalls
absolut gegen absolut, `TemplateLearner.learnParts:220`/`Teile.rest:158` rechnen auf beiden
Seiten absolut — konsistent, kein Folgefehler.

*Rundlauf:* Wer die gemischte Form eingibt (Brutto 80, Ertragstopf 100 und −20), findet die
Bewegung nach Export und nächstem Depot-Import in der **Regelform** wieder: Brutto 100, Steuer
20 im eigenen Topf, Netto 80. `KmyImporter.fillOrigin` ordnet die Zeilen nach dem Kontotyp ein,
nicht nach dem Feld, in dem sie eingetippt wurden. Wirtschaftlich dasselbe; festgehalten in
`KmySecurityExportTest.dieGemischteFormKommtAlsRegelformZurueck`.

*Ursprünglicher Befund (falsch):* „Kategorie-Splits mit umgekehrtem Vorzeichen — übersteigt
die Summe der Teilbeträge `total`, wird `rest` negativ und der letzte Split bekommt das
Gegenvorzeichen." Genau das ist gewollt und der Mechanismus, der die Steuerzeile korrekt
schreibt. Der daraufhin eingebaute Wächter (Abweichung > ein Cent je Zeile → Bewegung
auslassen) hätte jede Zinsbuchung aus dem Export geworfen und ist samt seiner beiden Tests
und der Meldung `skip_parts_mismatch` wieder entfernt. **Merkposten für die Methodik:** Die
Gegenprobe fängt so etwas nicht — sie prüft, ob der Test den Code trifft, nicht ob die
Annahme dahinter stimmt.

**23. Mittel — Wertpapier-Export prüft die Währung der Gegenkonten nicht.**
`buildTransactions` ruft für jedes Gegenkonto `currencyClash(...)`; `securitySplits:696`
tut das nicht. Kategorie- und Wertpapierkonto können in anderer Währung geführt sein, der
Split wird dann mit `price="1/1"` geschrieben — betragsmäßig falsch in der `.kmy`.

*Nachtrag (behoben):* `securitySplits` prüft jetzt das Depot, `addCategorySplits` jede Kategorie
— beide gegen die Währung des Verrechnungskontos, in der die Transaktion geschrieben wird. Test:
`KmySecurityExportTest.eineKategorieInFremderWaehrungWirdNichtStillGeschrieben`.

**24. Mittel — Tagesfenster in Gerätezeitzone.** `SecurityTx.sameDay:174`,
`Repository.findSecurityTx:891`, `DepotRepository.existsAlready:329` bilden die
Tagesgrenzen mit `Calendar.getInstance()` in der aktuellen Zone, `tx.date` stammt aber
aus `KmyImporter.parseDate` (Datum ohne Uhrzeit, zur damaligen Zone gebildet). Nach einem
Zeitzonenwechsel oder an DST-Grenzen rutscht die Bewegung in den Nachbartag:
`deleteSecurityBooking` löscht dann die Buchung ohne die Bewegung — genau der Zustand,
den der Kommentar dort ausschließen will.

*Nachtrag (versucht, zurückgenommen — der Vorschlag des Befunds ist falsch):* Der naheliegende Weg,
den Tag zonenunabhängig zu bestimmen (jeder Zeitstempel gehört zu der UTC-Mitternacht, die ihm am
nächsten liegt), ist an zwei bestehenden Tests gescheitert:
`SecurityTxSameMovementTest.andereUhrzeitAmSelbenTagAendertNichts` und
`SecurityTxMatchTest.dieUhrzeitStoertNicht`. Und zwar zu Recht — die verglichenen Werte sind **nicht
beide** reine Daten. `booking.createdAt` trägt eine echte Uhrzeit; eine Buchung um 23:00 Ortszeit
liegt in Deutschland auf dem Vortag in UTC und wäre damit nicht mehr derselbe Tag wie die
zugehörige Bewegung. Der häufige Fall wäre kaputtgegangen, um den seltenen zu retten.

Der Widerspruch ist nicht auflösbar, solange ein Datum als Zeitstempel abgelegt ist: dieselbe Zahl
soll einmal als „Kalendertag, egal wo" und einmal als „Zeitpunkt in der Ortszeit" gelesen werden.
Bleibende Möglichkeit wäre, reine Daten künftig auf die **lokale Mittagszeit** statt auf Mitternacht
zu setzen (Migration für den Bestand). Dann verschiebt ein Zonenwechsel von bis zu zwölf Stunden den
Tag nicht mehr, und der Vergleich darf lokal bleiben.

*Entscheidung (2026-09-02):* Der **lokale Tag** ist die gewollte Bedeutung — die Zahl meint den
Kalendertag, wie er beim Erfassen vor Ort galt. Die Mittagszeit-Umstellung wird **nicht** gemacht:
Der Fall tritt nur bei einem Wechsel der Gerätezeitzone oder beim Einlesen einer anderswo
geschriebenen KMyMoney-Datei ein, und eine Migration über den echten Bestand wiegt schwerer als der
Fehler, den sie verhindert. Der Punkt bleibt als **bekannte Einschränkung** stehen; wer ihn später
angeht, findet oben, warum der naheliegende Weg nicht trägt.

**25. Mittel — `StatementTemplates.clearAll` löscht profilübergreifend.**
`prefs.edit().clear().apply()` ignoriert den `profilePrefix` und leert die gesamte Datei,
also auch Vorlagen und ISIN-Zuordnungen aller anderen Profile. Nur `keyTemplates()` und
`keyIsins()` entfernen.

*Nachtrag (behoben):* Nur noch `keyTemplates()` und `keyIsins()`.

**26. Mittel — `StatementTemplates.saveAll(List)` löscht die Vorlagen aller Depots.**
Anders als `saveAll(String, List)` liest die Methode den Bestand nicht ein. Der Javadoc
sagt „Ersetzt alles im Depot `""`", tatsächlich ersetzt sie alles. Derzeit nur von Tests
gerufen, als öffentliche API aber eine Falle — auf `saveAll("", templates)` delegieren.

*Nachtrag (behoben):* Delegiert auf `saveAll("", templates)`. Beides festgehalten in
`StatementTemplatesScopeTest`.

**27. Mittel — Restore prüft die Schemaversion nicht.** `writeDatabaseFileRaw`
überschreibt bedingungslos. Kommt die Sicherung aus einer neueren App (Room-Version > 49),
scheitert Room beim nächsten Zugriff mit `IllegalStateException` — die bisherigen Daten
sind zu dem Zeitpunkt bereits weg. Der `versionCode` steht im Manifest
(`BackupArchive:111`), wird aber nirgends ausgewertet.

*Nachtrag (behoben):* `BackupStore.pruefeSchema` liest den Stand **aus der Datei** — `PRAGMA
user_version` steht im SQLite-Kopf an Byte 60 — und bricht ab, bevor irgendetwas überschrieben wird.
Nicht aus dem Manifest: der `versionCode` dort beschreibt nur die schreibende App, und alte
Sicherungen führen ihn gar nicht; die Datei selbst weiß es immer. Ein *niedrigerer* Stand geht
weiterhin durch, dafür gibt es die Migrationen. Beim Einspielen aller Profile werden erst alle
Datenbanken geprüft und dann die erste angefasst, sonst stünde nach dem Abbruch ein Teil der Profile
auf der Sicherung und der Rest auf dem alten Stand. Verglichen wird gegen den laufenden Stand
(`getOpenHelper().getReadableDatabase().getVersion()`) statt gegen eine zweite Konstante, die
irgendwann auseinanderläuft. Test: `BackupSchemaVersionTest`, gegengeprüft.

**28. Mittel — Sicherung komplett im Speicher.** `FullBackupRestoreFlow:378` →
`BackupCrypto.decrypt` → `BackupArchive.read`: das Archiv liegt mehrfach parallel als
`byte[]` im Heap, dazu jede Profil-Datenbank einzeln. Bei mehreren Profilen ein
OOM-Kandidat; gegen eine Zip-Bombe gibt es keine Grenze. Ebenso lädt
`PdfTextExtractor.read:54` mit `PDDocument.load(in)` ohne `MemoryUsageSetting` das ganze
Dokument in den Speicher — pdfbox-android bietet die Scratch-File-Variante.

*Nachtrag (die Grenzen sind da, der Umbau nicht):* `BackupArchive.read` zählt beim Entpacken mit und
bricht bei 256 MB ab. Ein ZIP sagt nicht vorab, wie groß sein Inhalt wird; ein paar Kilobyte können
sich zu Gigabyte entfalten, und die Datei kommt über einen Dateiwähler herein, der jeden Typ annimmt.
`PdfTextExtractor` lädt jetzt über `MemoryUsageSetting.setupMixed(16 MB)` mit dem Cache-Ordner der App
als Ablage — ausdrücklich dieser, weil `java.io.tmpdir` auf Android nicht verlässlich auf ein
beschreibbares Verzeichnis zeigt. Tests: `BackupArchiveTest.einUnplausibelGrosserInhaltBrichtDasEinlesenAb`
(ein Gigabyte Nullbytes, komprimiert rund ein Megabyte), gegengeprüft.

**Offen bleibt der eigentliche Umbau:** Das Archiv liegt weiterhin mehrfach als `byte[]` im Heap
(gelesen → entschlüsselt → entpackt). Das strömend zu machen, hieße `BackupCrypto` und
`BackupArchive` auf Ströme umzustellen; die Grenze oben verhindert den unbegrenzten Fall, den
mehrfachen Abzug behebt sie nicht.

**29. Mittel — Export-Bestätigung ist nicht atomar.** `KmyExportCoordinator:147-159`
schreibt `markExported`, `markTxExported` und ein zweites `markExported` als drei
getrennte Vorgänge nach dem Upload. Bricht es dazwischen ab, bleibt die Bewegung
`pending` (nächster Export schreibt sie doppelt) oder die Buchung ungemarkt — und die
kommt wegen `BookingDao:208` nie mehr durch. In eine `runInTransaction` fassen.

*Nachtrag (behoben):* Die gesamte Nachbereitung läuft über das neue `Repository.inTransaction`.
**Ohne Test:** ein Abbruch mitten in der Kette lässt sich in einem Unit-Test nicht herbeiführen,
ohne die Stelle eigens dafür aufzubohren.

**30. Mittel — `tabelle`-Merker bleibt stehen.** `TemplateLearner.forDate:709/746` setzt
`tabelle` nur im Zweig `above != null` und nie zurück. Findet eine spätere Zeile eine
eigene Beschriftung, bleibt `tabelle == true` von der früheren Fundstelle stehen; nach
der Schleife gewinnt dann `columnDate(...)` und die gefundene `SAME_LINE`-Regel wird
verworfen.

*Nachtrag (behoben):* Der Merker wird im Zweig mit eigener Beschriftung zurückgesetzt. Test:
`LearnerDateFlagTest`.

**31. Mittel — `SingleReceipt.attach` nullt `stagedPath` zu früh.**
`StatementBatchActivity.saveAll:413-417` setzt `d.stagedPath = null`, bevor
`saveManualSecurityTxBatch` bestätigt hat. Schlägt die Transaktion fehl, ist die
Zuordnung zur abgelegten Datei verloren und `discardStaged` räumt nichts mehr weg:
verwaiste PDF-Kopien, Buchungen ohne Beleg. (`saveManualSecurityTxBatch` kennt derzeit
überhaupt keinen Fehlerpfad, nur `onDone`.)

*Nachtrag (behoben, Reihenfolge umgedreht):* `SingleReceipt` ist in `plan` und `attach` geteilt.
`plan` vergibt den endgültigen Namen und baut die Notiz mit dem Beleg-Tag, **bewegt aber nichts**;
`attach` verschiebt die Datei und meldet sie zum Hochladen an und läuft erst, wenn die Transaktion
durch ist. Scheitert das Speichern, liegen die vorläufigen Kopien noch da, kein Upload ist
angelaufen, und der Stapel steht unverändert auf dem Schirm. Dafür hat `saveManualTxBatch` jetzt
einen `onError`-Zweig — ohne ihn bliebe die Liste hängen, weil der Speichern-Knopf schon gesperrt
ist und ohne Rückmeldung nie wieder frei wird. Dieselbe Umstellung in
`SecurityTxEditActivity.save()`. Ein misslungenes Verschieben nach erfolgreichem Buchen wird gesagt
(`statement_batch_receipt_failed`/`statement_receipt_failed`) statt verschluckt. Test:
`SingleReceiptOrderTest`, gegengeprüft mit einer Fassung, die wie früher schon beim Planen ablegt —
dann ist `planenBewegtNochNichts` rot.

**32. Mittel — Fehleingabe als Löschung.** `ProfileSettingsActivity.parsePercent:560`
macht aus einem unlesbaren oder außerhalb 0–100 liegenden Steuersatz stillschweigend `0`
und speichert das. Wer „26.375" mit falschem Dezimalzeichen tippt, hat die Vorbelegung
danach kommentarlos abgeschaltet.

*Nachtrag (behoben):* Die Auswertung ist als `TextValues.percentOrNull` aus der Maske heraus (dort
war sie nicht prüfbar — das Layout lässt sich unter Robolectric nicht aufbauen) und hat jetzt drei
Ausgänge statt zwei: leer oder eine getippte `0` ergibt 0 und schaltet die Vorbelegung ab, ein
gültiger Satz ergibt sich selbst, **unlesbar oder außerhalb ergibt `null`** — und dann bleibt der
gespeicherte Wert stehen. Beim ausdrücklichen „Fertig" meldet sich das Feld zusätzlich mit einem
Fehler und die Maske bleibt offen; auf den Nebenwegen (SMB-Assistent, Konten-Import) wird der Wert
nur nicht angefasst, damit man an einer Nebensächlichkeit nicht hängenbleibt. Test:
`TextValuesTest`, gegengeprüft.

**33. Mittel — Falsche Fehlerdiagnose.** `ProfileSettingsActivity:1252` fängt
`catch (Exception e)` um `BackupCrypto.decrypt` und meldet immer
„Passwort falsch" — auch bei beschädigtem Archiv, IO-Fehler oder OOM.

*Nachtrag (behoben):* Nur noch `javax.crypto.BadPaddingException` gilt als falsches Passwort — GCM
prüft beim Entschlüsseln seinen Authentifizierungs-Tag, und der schlägt genau dann fehl, wenn der
Schlüssel nicht passt. Alles andere geht durch `postRestoreError` und zeigt den echten Grund. An
beiden Stellen, `BackupRestoreController` und `FullBackupRestoreFlow`. Test:
`BackupCryptoTest.einFalschesPasswortIstAmAusnahmetypZuErkennen` samt Gegenstück
`einBeschaedigtesArchivIstKeinPasswortfehler` — ohne das zweite hinge die Unterscheidung in der Luft.

**34. Mittel — Zurück-Taste verwirft Stapelkorrekturen.**
`SecurityTxEditActivity.returnToList` gibt im `batchMode` nur über „Übernehmen" ein
Ergebnis zurück; Toolbar-Pfeil und Systemzurück rufen schlicht `finish()` ohne
`setResult` und ohne Rückfrage — obwohl der Javadoc an Zeile 1225 ausdrücklich verspricht,
dass „das Erreichte nicht verloren" geht. `OnBackPressedCallback` registrieren.

*Nachtrag (behoben):* Beide Wege gehen über das neue `zurueck()`: im Stapel zurück in die Liste
samt Ergebnis, sonst wie bisher `finish()`. Der Systemzurück läuft über einen
`OnBackPressedCallback`, damit es nicht vom Griff des Nutzers abhängt, ob seine Korrekturen
ankommen. Test: `SecurityTxBatchBackTest` (Systemzurück, Leistenpfeil und der Gegenfall ohne
Stapel), gegengeprüft.

**35. Mittel — Dialoge überleben die Rotation nicht.** `showDatePicker`, `showCalendar`,
`learnFrom`, `verifyLearned`, `showKmyPick`, `showFolderPick`, `showCsvPick`,
`askBackupOptions`, `askBackupPassword`, `pickAnchor` — alle über
`new AppDialog(this).show()` ohne `DialogFragment`. Bei `learnFrom`/`verifyLearned` geht
mit dem Dialog der einzige `finish()`-Pfad verloren (`setOnCancelListener`); die Maske
bleibt nach bereits gespeicherter Buchung offen stehen, erneutes Speichern legt die
Bewegung ein zweites Mal an (siehe Punkt 5).

*Nachtrag — nachgemessen, teils überholt, umgesetzt:*

**Die zweite Buchung ist nicht (mehr) auslösbar.** Nachgestellt: speichern, drehen, erneut tippen.
Der Speichern-Knopf bleibt gesperrt, und `save()` steigt zusätzlich bei `if (saving) return;` aus.
Grund: `onSaveInstanceState` gibt es in dieser Maske **erst seit dem Fix zu Punkt 4**; `git show
HEAD:…` liefert die Methode nicht. Zum Zeitpunkt des Reviews stimmte der Befund, ein anderer Fix hat
ihn miterledigt. Festgehalten in `SecurityTxLearnRotationTest.einZweiterTippLegtNichtsEinZweitesMalAn`.

**Umgestellt sind alle zehn.** Über den neuen `HostedDialog` (ein `DialogFragment`, das den Dialog
nach der Drehung von der *neuen* Maske neu bauen lässt, statt Rückrufe festzuhalten, die die Drehung
nicht überleben): `showDatePicker`/`showCalendar`, `learnFrom`, `verifyLearned`, `showKmyPick`,
`showFolderPick`, `showCsvPick` (in drei Masken), `askBackupOptions` (in zwei), `askBackupPassword`
(an zwei Stellen) und `pickAnchor`.

**Drei Dialoge lassen sich nicht aus einem `Bundle` wiederherstellen** — dort ist bei rund einem
Megabyte Schluss, und was sie brauchen, ist größer oder gar nicht serialisierbar:

- *Lern-Rückfrage und Nachprüfung* hängen an einem gelesenen PDF und den daraus gelernten Regeln.
  Gelöst über eine **Wiederaufnahme**: Die Maske merkt sich die leichten Angaben, liest die
  Abrechnung nach der Drehung erneut ein und stellt die Frage wieder hin. Vorher blieb sie als
  Sackgasse stehen — ausgefüllt, mit gesperrtem Knopf, ohne die Rückfrage, für die sie noch offen
  war; die Bank-Vorlage war verloren. Test:
  `SecurityTxLearnRotationTest.nachDerDrehungStehtDieLernRueckfrageWiederDa`, gegengeprüft.
- *Passwortabfrage beim Einspielen* hält das ganze Archiv. Nach einer Drehung ist es weg; der Dialog
  verschwindet dann und sagt, dass die Datei neu zu wählen ist (`restore_pick_again`) — statt ein
  Passwortfeld anzubieten, hinter dem nichts mehr steht.
- *Ankerauswahl der Regelseite* hängt an der eingelesenen Testabrechnung. Ebenso: Der Dialog bleibt
  nach der Drehung aus, ein erneuter Tipp aufs Feld stellt ihn her.

**Ohne Test:** die Dialoge in `ProfileSettingsActivity`, `OnboardingActivity`, `SettingsActivity`,
`MainActivity` und `StatementRulesActivity`. Deren Layouts lassen sich unter Robolectric nicht
aufbauen. Beim Umstellen ist dabei eine Falle sichtbar geworden, die es vorher nicht gab: Schließt
eine Maske ihren Regler nicht an `buildDialog` an, bleibt der Dialog **wortlos** aus. Genau das ist
im `BackupRestoreControllerTest` passiert, dessen Testmaske den Anschluss zunächst nicht hatte — der
Test ist jetzt so gebaut wie die echten Masken und hält die Anforderung fest.

**36. Mittel — SharedPreferences-IO auf dem UI-Thread.**
`StatementRulesActivity.load`/`saveAll`, `ProfileSettingsActivity` (PlacesStore an fünf
Stellen). Dazu: `AppDatabase.getInstance:704` ruft bei **jedem** Aufruf
`ProfileManager.currentDbFileName`, das die Profil-JSON neu parst — und
`DepotRepository:579` legt pro Kennzahl-Abfrage einen neuen `SettingsStore` an, was
jedes Mal Keystore-Zugriff und `EncryptedSharedPreferences.create` bedeutet.

*Nachtrag (teilweise behoben):* Die beiden teuren Stellen sind weg.
`SettingsStore.secretPrefs` baut die verschlüsselte Datei nur noch **einmal je Prozess** auf — sie
hängt am Gerät, nicht am Profil, und damit kostet `new SettingsStore(…)` überall keinen
Keystore-Zugriff mehr, nicht nur an der einen genannten Stelle. `ProfileManager.currentDbFileName`
spart den JSON-Parse und nimmt die beiden Prefs-Werte selbst als Schlüssel; die Abfrage bleibt also,
nur das Bauen der Profil-Objekte entfällt. Dadurch wirkt auch eine Änderung sofort, die an der
Klasse vorbeigeht — etwa das Einspielen einer Alle-Profile-Sicherung, das die Prefs-Datei direkt
überschreibt. Genau das prüft `ProfileDbFileNameCacheTest`, mit Gegenprobe gegen einen naiven
Zwischenspeicher.

*Nachtrag zum Rest (nachgemessen, nichts zu tun):* Das Prefs-IO in `StatementRulesActivity.load`/
`saveAll` und im `PlacesStore` ist gemessen worden, nicht geschätzt. 500 Orte lesen: **0,3 ms**, einen
Ort hinzufügen: **0,7 ms**, die Vorlagen eines Depots lesen: **0,2 ms**. Geschrieben wird ohnehin
durchweg mit `apply()`, der Plattenzugriff läuft also schon nebenher; auf dem Bedienfaden bleibt nur
das Auf- und Abbauen eines kleinen JSON. Das auf einen Hintergrundfaden zu schieben hieße, den
UI-Code auf Rückrufe umzubauen — für zehntel Millisekunden. **Wird nicht gemacht.**

**37. Mittel — Kein Schutz gegen doppelten Import.** `ProfileSettingsActivity:262`
(„Fertig") und `importAccounts:879` starten bei jedem Klick einen neuen Thread und einen
neuen Import auf dieselbe DB.

*Nachtrag (behoben):* `importAccounts` beginnt mit `blockIfImporting()` — derselbe Riegel, der schon
„Fertig" und Zurück abfängt. **Ohne Test:** die Maske lässt sich unter Robolectric nicht aufbauen
(`activity_profile_settings` scheitert am `AnimationScaleListDrawable`), und der Riegel ist ohne sie
nicht zu erreichen.

*(38 entfällt — war kein Fehler, siehe „Ausdrücklich geprüft und in Ordnung". Die übrigen
Nummern bleiben, damit Verweise gültig bleiben.)*

*(39 entfällt — vom Entwickler als gewollt bestätigt: Wer ein Profil kopiert, will eine
arbeitsfähige Kopie, und dazu gehört die Datenquelle samt Zugang.)*

**39. Niedrig — Passwort wandert beim Profil-Kopieren mit.**
`ProfileManager.copySettingsFrom:378` kopiert die `secretPrefs` kommentarlos ins neue
Profil, während `default_account` und `account_group` bewusst ausgenommen werden. Im UI
zumindest erwähnen.

**40. Niedrig — Doppelte Negation.** `TextValues:108`: `negative ^= s.charAt(0) == '-'`
nach der Klammer-Erkennung macht aus `"(-5,00)"` ein **positives** 5,00.

*Nachtrag (behoben):* Aus `^=` wird `|=`, an beiden Stellen (vorangestelltes und nachgestelltes
Vorzeichen). Klammer und Minus sind zwei **Schreibweisen** für „negativ", keine zwei Rechenzeichen —
sie heben einander nicht auf. Test: `TextValuesTest.klammerUndMinusHebenSichNichtAuf` samt der
Gegenprobe, dass Positives positiv bleibt.

**41. Niedrig — `",--"` mit Punkt-Tausendertrennung.** `TextValues:121`: `"1.242.--"`
wird zu `"1.242.00"` und liefert `null`. Die `--`-Endung erst nach der
Trennzeichen-Normalisierung ersetzen.

*Nachtrag (behoben, anders als vorgeschlagen):* Steht die Endung da, ist das Zeichen davor der
Dezimaltrenner — und alles links davon ist Tausendertrennung, gleich welches Zeichen. Genau das wird
jetzt ausgenutzt, statt die Ersetzung zu verschieben. Die strenge Lesart bleibt: `1.234.567` geht
weiterhin **nicht** als Betrag durch, sonst käme jede Belegnummer als Zahl an. Test:
`ohneCentInBeidenSchreibweisen` und `belegnummernGehenWeiterhinNichtDurch`.

**42. Niedrig — Indizes aus dem kleingeschriebenen String.** `AnchorRule.afterAnchor:494`
sucht in `line.toLowerCase(Locale.ROOT)` und gibt einen Index zurück, den
`afterAnchorText`, `targets` und `anchorSpan` auf dem **Original** verwenden.
`toLowerCase` ist nicht längenerhaltend (`'İ'` U+0130 → zwei Zeichen). Zusätzlich rechnet
`anchorSpan:657` mit `anchor.trim().length()`, während `afterAnchor` die ungetrimmte
Länge benutzt hat. `regionMatches(true, …)` auf dem Original verwenden.

*Nachtrag (bereits behoben):* Steht schon so im Code — `afterAnchor` sucht mit
`regionMatches(true, …)` im Original und trimmt die Beschriftung auf dieselbe Länge, mit der
`anchorSpan` zurückrechnet. Die Änderung kam in dieser Runde mit den Fundstellen davor, war aber
nicht vermerkt. `git show HEAD:…` zeigt dort noch das alte `toLowerCase`.

**43. Niedrig — `startsWithAnchor` tut nicht, was es heißt.** Der Kommentar sagt „Ob die
Zeile mit genau dieser Beschriftung beginnt", `afterAnchor` findet den Anker aber an
jeder Wortgrenze. `hits()` und damit `StatementTemplate.score` zählen deshalb auch
Treffer mitten im Fließtext, was die Vorlagenauswahl in `StatementTemplates.byScore`
zugunsten der nachsichtigsten Vorlage verschiebt.

*Nachtrag (Name berichtigt, Verhalten bewusst nicht):* Die Methode heißt jetzt `containsAnchor` und
sagt damit, was sie tut. Das Verhalten bleibt — und zwar mit Begründung: `hits()` dokumentiert
ausdrücklich, dass „nach demselben Kriterium gezählt wird, nach dem später gelesen wird", und gelesen
wird über `afterAnchor` an jeder Wortgrenze. Auf den Zeilenanfang einzuschränken hieße, die
Vorlagenauswahl nach einem anderen Maßstab zu treffen als das Lesen danach — und schlösse jede
Beschriftung aus, die in einer Tabellenzeile hinter anderem Text steht. Falsch war der Name, nicht
die Suche.

**44. Niedrig — Persistierbare URI-Rechte fehlen.**
`StatementRulesActivity.tryLauncher`: die per `OpenDocument` erhaltene `testUri` wird
ohne `takePersistableUriPermission` gehalten und in `showPdf` weitergereicht. Nach einem
Prozesstod → `SecurityException`, die zu „receipt_error" verallgemeinert wird.

*Nachtrag (behoben):* `takePersistableUriPermission` beim Entgegennehmen. Scheitert das Festhalten,
geht es ohne weiter und wird protokolliert: Das Recht für diesen Lauf besteht bereits, und nicht
jeder Dateianbieter gibt ein dauerhaftes heraus — daran soll die Prüfung nicht scheitern.

**45. Niedrig — `openOutputStream` kann `null` sein.**
`ProfileSettingsActivity.doBackup:1198`: die NPE landet im `catch` und zeigt dem Nutzer
„Sicherung fehlgeschlagen: null". `readText:1139` prüft `is != null` in der Schleife
statt davor.

*Nachtrag (behoben):* Beide Stellen prüfen jetzt vorher und werfen eine `IOException` mit einem Satz,
der etwas sagt (`backup_target_unwritable`/`backup_source_unreadable`). Bei `readText` war es mehr als
Kosmetik: Die Prüfung in der Schleife lieferte bei `null` eine **leere Zeichenkette** zurück, statt zu
melden, dass die Datei nicht lesbar war.

**46. Niedrig — `compareParts` vergleicht signiert gegen absolut.**
`TemplateCheck:155`: `erwartet` summiert vorzeichenbehaftet, `gelesen` summiert
`Part.cents`, die durch `Math.abs` gegangen sind → falsche `Kind.PARTS`-Beschwerde.

*Nachtrag — kein Fehler:* Nachgesehen, woher `soll` kommt: aus
`SecurityTxEditActivity.partAmounts`, und das legt jeden Wert durch `Math.abs`. Beide Seiten sind
also absolut, das Summieren „vorzeichenbehaftet" ist an dieser Stelle wirkungslos. Der Befund entstand
aus dem Lesen der Schleife ohne ihren Aufrufer. Dass `partAmounts` absolut liefert, ist Absicht: Der
Lerner sucht den Betrag im Text der Abrechnung, und dort steht die Steuer positiv unter ihrer
Beschriftung.

**47. Niedrig — `hashCode`-Kollisionen.** `AnchorRule:774`:
`… * 31 + lineDistance + (sum ? 1 : 0)` — `lineDistance=1, sum=false` und
`lineDistance=0, sum=true` liefern denselben Wert, obwohl `equals` unterscheidet.

*Nachtrag (behoben):* `lineDistance` und `sum` bekommen jede ihre eigene Runde statt am Ende addiert
zu werden.

---

### Wartbarkeit und Code-Smells

**48. Hoch — Rund 400 dreifach gepflegte Zeilen.** `ProfileSettingsActivity`,
`OnboardingActivity` und in Teilen `SettingsActivity` teilen sich praktisch identischen
Code: `setupServerType`/`labelForServerType`/`applyServerTypeHints`,
`onDestroy`/`onSmbConfigured`, `browseKmyAt`/`showKmyPick`/`browseFolderAt`/
`showFolderPick` sowie den kompletten Sicherungs-/Wiederherstellungsblock (`readBytes`,
`doRestore`, `askBackupPassword`, `openRestore`, `pickProfileFromBackup`,
`chooseRestoreScope`, `confirmAndRestore`, `applyRestore`, `postRestoreDone`,
`postRestoreError`).

Jeder oben genannte Fehler — Threads ohne Guard, fehlende Null-Prüfung, Passwort im
Klartext — muss an zwei bis drei Stellen behoben werden. Die Divergenz hat schon
begonnen: `ProfileSettingsActivity` hat `askBackupOptions`, `OnboardingActivity` nicht.
Ein gemeinsamer `BackupRestoreController` und `RemoteBrowseController` — das Muster gibt
es mit `SmbWizardController` bereits im Haus.

*Nachtrag (behoben):* Der Wiederherstellungsblock liegt jetzt einmal in
`ui/BackupRestoreController`, die Server-Felder samt Verbindungsprobe und beiden
Ordner-Browsern einmal in `ui/SyncFieldsController`, `readBytes` einmal in
`util/UriBytes` (drei Fundstellen). Zusammen verlieren die beiden Masken je rund 400
Zeilen (1563 → 1160 bzw. 1170 → 767). Die fehlende Lebenszyklus-Prüfung ist dabei an
einer Stelle für alle nachgezogen.

Für `SyncFieldsController` gibt es **keinen** Test: weder `activity_onboarding` noch
`activity_profile_settings` lässt sich unter Robolectric aufbauen (`ClassNotFoundException:
AnimationScaleListDrawable`), und die Felder liegen in beiden Layouts direkt statt in
einem gemeinsamen `<include>`. Abgesichert ist die Verschiebung nur durch Suite und Build.

**49. Mittel — Dreifach dupliziertes Aufbau-Wissen für Bewegungen.**
`SecurityTxEditActivity.save` + `buildBooking`, `StatementDraft.toTx` + `toBooking` und
`duplicateCandidate` implementieren dieselben Vorzeichen- und Dividendenregeln je
dreimal. Die Kommentare verweisen wechselseitig aufeinander („dieselben Regeln wie in
`SecurityTxEditActivity`") — genau das Zeichen dafür, dass die Regel in eine eigene
Klasse gehört.

*Nachtrag (behoben):* Die Regeln stehen jetzt in `SecurityTx.applyAmounts` und
`SecurityTx.toMoneyBooking` — auf der Entität, deren Felder sie beschreiben. Alle drei Stellen rufen
sie; `StatementDraft.toBooking` ist auf zwei Zeilen geschrumpft. Mitgenommen: Die Zeichenketten
`"buy"`/`"sell"`/`"dividend"` standen in **vier** Klassen nebeneinander und stehen jetzt einmal auf
`SecurityTx`. Test: `SecurityTxRulesTest` — beide Vorzeichenfälle, die Dividende ohne Stücke und ohne
Gebühr, und die Falle, dass der Buchungsbetrag **nicht** `netCents` ist (das trägt bei Kauf und
Verkauf den Bruttobetrag, bewegt wird der Gesamtbetrag).

**50. Mittel — Überlange Methoden.** `ProfileSettingsActivity.onCreate` (142 Zeilen, ~35
`findViewById`/Listener), `showAccountsDialog` (87), `SecurityTxEditActivity.onCreate`
(98), `save` (84), `offerToLearn` (108), `learnFrom` (75),
`StatementRulesActivity.FieldForm`-Konstruktor (97). Dazu zwei Activities über 1500
Zeilen.

*Nachtrag (teils behoben — mit Zahlen, nicht mit Gefühl):* Vor dem Aufteilen nachgemessen; einige
Methoden waren durch die vorigen Umbauten inzwischen länger, nicht kürzer.

| Methode | vorher | jetzt |
|---|---|---|
| `ProfileSettingsActivity.onCreate` | 148 | **24** (fünf `setup…`-Blöcke) |
| `SecurityTxEditActivity.save` | 97 | **18** (+18 Zusammenbauen, +41 Schreiben) |
| `SecurityTxEditActivity.onCreate` | 111 | **31** (sechs Blöcke) |
| `offerToLearn` | 108 | **34** (schon durch den Dialog-Umbau) |
| `showAccountsDialog` | 87 | **36** (+ Zeilenaufbau, Knopflogik, Schließen/Öffnen je eigen) |
| `FieldForm`-Konstruktor | 99 | **62** (+40 `regelUebernehmen`) |

Der `FieldForm`-Konstruktor bleibt bei 62 Zeilen: Er belegt lauter `final`-Felder, und die müssen
dort zugewiesen werden. Herausgelöst ist alles danach.

Die Aufteilungen sind reine Verschiebungen: gleiche Reihenfolge, gleiche Aufrufe. Bei `save()` ist
dabei eine Zusicherung dazugekommen — der Buchungsbetrag kommt jetzt aus `tx.netCents` statt noch
einmal aus dem Feld, womit „Buchungsbetrag == netCents" von selbst gilt (siehe Fund A).

**Was dabei schiefging, und was daraus folgt:** Beim Aufteilen von `ProfileSettingsActivity.onCreate`
habe ich den zu ersetzenden Bereich anhand einer Textmarke abgegrenzt statt anhand der Klammertiefe
und dabei rund 400 Zeilen mitgelöscht — elf Methoden. Der Compiler hat es sofort gemeldet;
wiederhergestellt wurden sie aus `git show HEAD:…`, abzüglich der drei, die in dieser Runde in den
`SyncFieldsController` gewandert waren. Nachgeprüft über einen Abgleich der Methodenliste gegen HEAD
(jede Abweichung ist erklärbar) und über die Suche nach jeder Änderung dieser Runde in der Datei. Was
diese Prüfung **nicht** ausschließt: eine Änderung *innerhalb* einer der wiederhergestellten Methoden,
die es in dieser Runde gegeben hätte. Nach Durchsicht der Runde gab es keine. Die zweite Aufteilung
grenzt den Bereich über die Klammertiefe ab.

**51. Mittel — Magische Zahlen.** `Math.abs(h.shares) < 1e-6` als „Position geschlossen"
zweimal wörtlich in `DepotActivity`; Textgrößen `14f`/`16f`/`11f` und Paddings `24`/`20`
direkt im Code; `MAX_STELLE = 6` in `StatementRulesActivity` unabhängig von
`MoneyFormat.SHARE_DECIMALS = 6`; `EPS = 1e-9` in `SecurityAmounts` neben `1e-6`
anderswo.

*Nachtrag (behoben — und ein Fund dabei):* `1e-6` heißt jetzt `SecurityAmounts.SHARE_EPSILON` und
steht an einer Stelle statt an vieren. Der Wert ist keine beliebige kleine Zahl: `SHARE_DECIMALS` ist
6, alles darunter ist gar nicht darstellbar. `SecurityAmountsTest` hält den Zusammenhang fest — wer
die Nachkommastellen erhöht, wird an die Schwelle erinnert.

Bei `MAX_STELLE` war die Sache anders als aufgeschrieben. Es gibt die Zahl zweimal, mit
**verschiedenen Werten**: 3 im `TemplateLearner`, 6 in `StatementRulesActivity` — und der Kommentar
dort behauptete „so weit sucht auch der Lerner". Das stimmte nicht. Gleichgezogen gehören sie
trotzdem nicht: Der Lerner hört früh auf, weil jede weitere Stelle die Aussicht erhöht, dass
irgendeine Zahl der Zeile zufällig passt; wer die Stelle selbst wählt, weiß dagegen, was er meint.
Berichtigt ist deshalb der Kommentar, nicht der Wert.

`EPS = 1e-9` bleibt: Das ist die Schwelle für „Division durch null", nicht für „gleiche Stückzahl" —
zwei verschiedene Fragen, die nur zufällig beide klein sind. Steht jetzt so im Javadoc.

**52. Niedrig — Toter Code.** `TemplateLearner.hasDate`, `StatementScan.containsAny`,
`LocalizedActivity.appliedAccentColor` (wird gesetzt, nie gelesen).

*Nachtrag (behoben):* Alle drei entfernt.

**53. Niedrig — Doppelter Aufruf.** `StatementRulesActivity:394` und `:401` rufen beide
`attachCategories(categoryAdapter)`; der Adapter wird doppelt angehängt und das Feld über
`anhaengen` zweimal geleert und wiederhergestellt.

*Nachtrag (behoben):* Der Aufruf steht jetzt **einmal**, und zwar hinter den `setText`-Aufrufen.
Davor war er nötig gewesen, weil das Anhängen des Adapters das Feld leert und wiederherstellt — der
eben gesetzte Text wurde so zweimal durchgereicht.

**54. Niedrig — Deprecated `onBackPressed`.** `ProfileSettingsActivity:277` überschreibt
`onBackPressed` (deprecated seit API 33), während `StatementBatchActivity` korrekt
`getOnBackPressedDispatcher()` benutzt. Mit `android:enableOnBackInvokedCallback` würde
der Import-Schutz stillschweigend übersprungen.

*Nachtrag (behoben):* `OnBackPressedCallback` statt der überschriebenen Methode — dieselbe Form wie in
den anderen Masken.

**55. Niedrig — Toter Javadoc-Verweis.** `ProfileSettingsActivity:1372` verlinkt
`{@link #finishFromProfileMask()}` — die Methode heißt `finishToMainActivity`.

*Nachtrag (erledigt):* Der Verweis steht nicht mehr im Code; er saß in einer der Methoden, die in
dieser Runde in den `BackupRestoreController` gewandert sind.

**56. Niedrig — `String.matches` in der inneren Schleife.** `AnchorRule.currencyOf:546`
und `TemplateLearner.isCurrencyCode:929` kompilieren pro Aufruf ein Pattern;
`currencyFits` ruft das je Zeile und Regel.

*Nachtrag (bereits behoben):* Beide Stellen arbeiten inzwischen ohne `matches` —
`AnchorRule.waehrungskuerzel` prüft zeichenweise, `TemplateLearner.isCurrencyCode` schlägt in
`Currencies` nach. Kam in dieser Runde mit den Punkten 20/21, war hier nicht vermerkt.

**57. Niedrig — Zwei Implementierungen für denselben Zweck.**
`StatementRulesActivity.fileName` fragt mit `query(uri, null, …)` alle Spalten ab,
`StatementImport.displayName` macht es mit expliziter Projektion richtig.

*Nachtrag (behoben):* Beide gehen über das neue `DocumentName.of` mit ausdrücklicher Spaltenauswahl.
Das ist nicht nur Aufräumen: Ein `query(uri, null, …)` lässt sich jeden Anbieter **alle** Spalten
zusammenstellen — bei einem Dokument in der Cloud kann das einen Netzzugriff bedeuten, für einen
einzigen Dateinamen.

**58. Niedrig — `finish()` vor `startActivity`.**
`SecurityTxEditActivity.openRules:1571` — funktioniert, unterdrückt aber die
Übergangsanimation; die Methode stammt aus einem Dialog-Callback ohne
`isFinishing()`-Prüfung.

*Nachtrag (behoben):* Erst `startActivity`, dann `finish()` — und die `isFinishing()`-Prüfung davor,
weil der Aufruf aus einem Dialog-Knopf kommt und die Maske inzwischen weg sein kann.

---

### Sicherheit

Neben Punkt 15 (Zip-Slip):

**59. Mittel — Wiederherstellungs-Passwort im Klartext-`String`.**
`ProfileSettingsActivity.backupPassword:130` bleibt nach abgeschlossener Sicherung im
Feld stehen (`doBackup` setzt es nicht zurück) und liegt damit über die restliche
Lebensdauer der Activity im Heap. `char[]` verwenden und nach `BackupStore.createProfile`
überschreiben. Es gibt außerdem keine Mindestlänge.

*Nachtrag (behoben, mit einer Einschränkung):* Das Passwort wird in `doBackup` aus dem Feld genommen,
bevor der Faden startet — es hängt danach nur noch am laufenden Vorgang und nicht mehr an der Maske.
Dieselbe Stelle gab es in `SettingsActivity` (Alle-Profile-Sicherung), dort war sie ebenso; beide sind
geändert. Dazu eine Mindestlänge von acht Zeichen (`BackupCrypto.MIN_PASSWORD_LENGTH`, an einer Stelle
statt zweimal in zwei Masken); ein leeres Feld bleibt erlaubt, das heißt „ohne Verschlüsselung".

**Nicht gemacht:** die `char[]`-Kette durch `BackupStore` und `BackupCrypto`. Sie wäre die
gründlichere Lösung, berührt aber die Krypto-Schnittstelle und deren Tests, und der Gewinn ist
begrenzt — das Feld der Maske hält den Text ohnehin als `Editable`. Eigene Entscheidung, nicht
nebenbei.

**60. Niedrig — Fallback auf unverschlüsselte Prefs.**
`SettingsStore.createSecretPrefs` weicht bei defektem Keystore auf
`SECRET_PREFS + "_fallback"` im Klartext aus. Bewusst so entschieden (Kommentar), sollte
dem Nutzer aber angezeigt werden.

*Nachtrag (behoben):* `SettingsStore.isSecretStorageUnencrypted` merkt sich den Rückfall; beide
Masken, die das Server-Passwort abfragen, schreiben es als Fehlertext ans Feld
(`settings_secret_fallback`). Der Rückfall selbst bleibt — ein defekter Schlüsselspeicher soll die App
nicht unbrauchbar machen —, aber er ist nicht mehr unsichtbar.

Nicht beanstandet: kein Klartext-Logging von Zugangsdaten gefunden; alle Room-Abfragen
sind parametrisiert (keine String-Konkatenation in `@Query`); keine neuen Berechtigungen
im Manifest; alle neuen Activities `exported="false"`.

---

## Nachträglich gefunden (beim Gerätetest)

**A. Hoch — `netCents` war bei Kauf und Verkauf nicht das bewegte Geld.** Gefunden vom Entwickler auf
dem Gerät, ausgehend von der Frage, was Brutto und Netto bei einem Verkauf bedeuten.

`SecurityTxMatch` vergleicht `SecurityTx.netCents` gegen den Betrag der Geldbuchung und dokumentiert
das Feld ausdrücklich als „das Geld, das aufs Konto geht". Gespeichert wurde dort bei Kauf und
Verkauf aber noch einmal der **Bruttobetrag** (`netCents = dividend ? net : gross`) — in der
Erfassungsmaske wie im Importer. Bei jeder Bewegung **mit Gebühr** wichen die beiden Zahlen
voneinander ab: Die Buchung trägt beim Kauf Brutto + Gebühr, beim Verkauf Brutto − Gebühr. Die
Zuordnung scheiterte damit, und `deleteSecurityBooking` löschte die Buchung ohne ihre Bewegung —
genau der Zustand, den der Kommentar dort ausschließt.

Ohne Gebühr fällt es nicht auf; deshalb stand es so lange.

*Behoben:* `netCents` trägt jetzt bei jeder Art das bewegte Geld (`SecurityTx.moneyOf`), in Maske und
Importer. Migration `49→50` rechnet den vorhandenen Bestand aus `amount_cents` und `fee_cents` nach —
deterministisch, geraten wird nichts; Dividenden und Ein-/Ausbuchungen bleiben unberührt. Der Export
ist nicht betroffen: `KmyExporter` rechnet den Geldbetrag ohnehin selbst aus Brutto und Gebühr.

Tests: `SecurityTxRulesTest.netCentsIstDerBetragDerGeldbuchung` und `NetCentsMigrationTest` (samt der
Probe, dass ein zweiter Durchlauf nichts weiterverschiebt). **Ohne Test** bleibt die Verdrahtung: dass
die Migration in `addMigrations` steht und die Datenbankversion erhöht wurde, prüft kein Unit-Test.

*Nicht gemacht:* Die Einstellung „Dividenden brutto anzeigen" bleibt, wie sie heißt — für Dividenden.
Die Depot-Kennzahlen rechnen Verkäufe weiterhin brutto. Das wäre eine eigene Entscheidung samt
Umbenennung und Handbuch.

---

## Ausdrücklich geprüft und in Ordnung

- **Room-Migrationen 45→49 sind schemakonform.** Spalten abgezählt: `security_tx` 15,
  `security_tx_split` 7 (inkl. `index_security_tx_split_tx_id`), `security.isin`,
  `language.defaultCurrency/numberFormat` mit passenden Defaults. Kein
  `fallbackToDestructiveMigration`, alle Migrationen registriert (inzwischen 49 — siehe den Fund zu
  `net_cents` unten).
- **Prüfziffern ISIN, CUSIP, SEDOL** rechnen korrekt (Referenzwerte nachgerechnet); nur
  der zulässige Zeichensatz ist zu weit (Punkt 21).
- **Kein ReDoS.** Keine geschachtelten Quantoren in einem der neuen Pattern.
- **`SimpleDateFormat` wird nirgends statisch geteilt** — pro Aufruf neu angelegt, also
  threadsicher (nur teuer, Punkt 14).
- **Keine unverschlossenen Streams oder `PDDocument`** — durchgehend try-with-resources.
- **`StatementDraft`** ist als `Parcelable` sauber, `writeToParcel` und Konstruktor sind
  Feld für Feld symmetrisch.
- **`SecurityTxEditActivity.checkDuplicate`** behandelt die Race der veralteten
  DB-Antwort korrekt über `lastDupKey` — ein gutes Muster, das an den anderen Stellen
  fehlt.
- **`SecurityAmounts`** ist als reine, testbare Rechenklasse ohne Android-Bezug
  vorbildlich geschnitten; die Division-durch-Null-Fälle sind über `EPS` und die
  `taxRate`-Grenzen abgesichert.
- **Die 0-Cent-Kategoriezeilen aus `MIGRATION_47_48` gehören so.** Ursprünglich als Punkt 38
  aufgeschrieben (`AND (CASE … END) <> 0` ergänzen) — das wäre falsch gewesen. Die Erfassungsmaske
  übernimmt die Kategorien der letzten Buchung desselben Wertpapiers. Zu Jahresbeginn ist eine
  Dividende wegen des Freistellungsauftrags noch nicht steuerpflichtig; die Steuerzeile steht dann
  mit 0 da. Bliebe sie weg, müsste man sie bei der ersten steuerpflichtigen Dividende des Jahres von
  Hand wieder anlegen — so ist sie schon da und wird nur noch gefüllt. Die Zeile ist die
  Vorbelegung, nicht ein Rest.
- **Übersetzungen** vollständig, keine verwaisten Schlüssel.
- **Unit-Tests** laufen grün; die Testabdeckung der neuen Erkennungslogik ist mit ~40
  neuen Testklassen bemerkenswert gut.

---

## Gesamteinschätzung

Der neue Code ist deutlich besser als der Auftrag vermuten lässt. Die Kommentare
begründen Entscheidungen statt sie zu wiederholen, `SecurityAmounts`, `StatementDraft`
und die Migrationskette sind sauber gearbeitet, und die Testabdeckung der
Erkennungslogik ist überdurchschnittlich.

Die Schwächen liegen konzentriert an drei Stellen:

1. **Die `pending`-Asymmetrie in den Löschpfaden** (Punkte 2, 3) — ein einzelnes
   vergessenes `AND pending = 0` mit stillem Datenverlust als Folge.
2. **Der Android-Lebenszyklus in den drei neuen großen Activities** (Punkte 4, 5, 8, 10,
   35) — nackte Threads, kein `onSaveInstanceState`, keine Guards. Das ist der klassische
   blinde Fleck, wenn Code gegen den Happy Path entwickelt wird.
3. **Die Migration der Profil-Präfixe** (Punkte 1, 9) — die Umstellung auf Profile war
   der größte Eingriff, und ihre Rückwärtskompatibilität ist nicht zu Ende gedacht.

**Punkt 1 ist ein Release-Blocker:** jeder bestehende Nutzer mit Serveranbindung verliert
beim Update sein Passwort.

### Vorgeschlagene Reihenfolge

1. Punkt 1 (Passwort-Migration) — vor der Veröffentlichung von 1.12.
2. Punkte 2, 3 (`pending`-Asymmetrie) — beides Einzeiler, beide mit Datenverlust.
3. Punkt 9 (Altsicherungen) und Punkt 18 (`clearAll`-Reihenfolge).
4. Punkte 4, 5 (Zustandsverlust und Doppelspeichern in der Erfassungsmaske).
5. Punkte 6, 11, 13, 19 (fachliche Erkennungsfehler; alle gut über Unit-Tests
   absicherbar, die Infrastruktur dafür steht bereits).
6. Punkte 7, 12, 14, 16 (Laufzeit und ANR).
7. Punkt 48 (Zusammenführen der dreifach gepflegten Backup-Logik) — danach, weil es die
   Fixes aus 1–6 sonst dreimal verlangt.
