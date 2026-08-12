# Handbuch-Editor

Ein zweisprachiges Redaktionswerkzeug für `docs/handbuch_de.json` und `docs/handbuch_en.json`.
JSON, Dateinamen und Blocktyp-Kürzel bleiben verborgen; bearbeitet werden Kapitel, Absätze,
Aufzählungen und Bilder – immer beide Sprachen zugleich.

## Starten

```
tools/handbuch-editor.py
```

Beim ersten Aufruf entsteht unter `~/.venvs/handbuch-editor` eine eigene Python-Umgebung mit
PySide6, reportlab und pypdfium2; das System-Python bleibt unangetastet. `--neu-aufsetzen` wirft
die Umgebung weg und baut sie neu. Ohne das Startskript geht auch
`PYTHONPATH=tools python3 -m handbuch_editor`, dann müssen die drei Pakete allerdings selbst
bereitstehen.

## Warum beide Sprachen zusammen

Im Modell gibt es nur **einen** Baum von Blöcken; jeder Block trägt seinen deutschen und seinen
englischen Text. Ein Block, der nur in einer Sprache existiert, lässt sich gar nicht erst
herstellen. Verschieben, Einfügen und Löschen wirken deshalb zwangsläufig auf beide Dateien.

Damit die Zuordnung auch ein Umbauen übersteht, trägt jeder Block ein unscheinbares Feld `"id"`.
`docs/build_manual.py` überliest es; für den Editor ist es der Faden, an dem er einen verschobenen
Absatz in der anderen Sprache wiederfindet.

Beim Öffnen gleicht der Editor beide Dateien ab und meldet, was er zurechtgerückt hat. Geschrieben
wird dabei nichts – erst beim Speichern.

## Werkzeugleiste

| Knopf | was er tut |
|---|---|
| 💾 Speichern | schreibt beide JSON-Dateien (Strg+S) |
| 🔍 Prüfen | leere Übersetzungen, fehlende Bilder, offene `<b>`-Auszeichnung, doppelte Kennungen (Strg+P) |
| 🖼 Bilder erzeugen | startet `tools/screenshots.py` für den gewählten Sprachsatz |
| 📘 / 📗 / 📚 | startet `docs/build_manual.py` und öffnet das fertige PDF |

Die Ausgabe der gestarteten Skripte läuft in einem Logfenster mit. Sie brauchen dafür das
System-Python mit `reportlab` (PDF) und `Pillow`/`tkinter` (Aufnahmehelfer) – der Editor ruft es
selbst auf, nicht seine eigene Umgebung.

## Vorschau

Rechts steht das gewählte Kapitel als **echtes PDF** – gesetzt mit derselben Bibliothek und
denselben Stilen wie das fertige Handbuch, nur ohne Titelseite und Inhaltsverzeichnis. Ein
Kapitel braucht dafür rund eine Sekunde; gebaut wird im Hintergrund, das Fenster bleibt
bedienbar. Nach jeder abgeschlossenen Änderung zieht die Vorschau nach; wer das nicht will,
nimmt den Haken bei „laufend aktualisieren“ weg und setzt von Hand neu.

Dafür lässt sich `docs/build_manual.py` seit diesem Werkzeug **importieren**: `erzeuge()` nimmt
den Inhalt einer Sprachdatei als Dict entgegen, dazu wahlweise eine Auswahl von Abschnitten. So
zeigt die Vorschau den ungesicherten Stand, und die Stile stehen weiterhin nur an einer Stelle.

## Was neben dem Bild steht

Bei „Bild mit Text“ entscheidet der Generator anhand einer Höhenschätzung, welche Absätze
**neben** das Bild passen und welche **darunter** rutschen. Der Editor rechnet mit derselben
Funktion (`aufteilung_der_inhalte`) und tönt die Blockkarten entsprechend – hell für „neben“,
dunkler für „darunter“, dazwischen für geteilte Blöcke, jeweils mit Vermerk in der Kopfzeile.
Wird ein Absatz länger, zieht die Tönung sofort nach.

Kapitel- und Abschnittsüberschriften sind ebenfalls abgesetzt, damit die Grenzen im langen
Kapitel ins Auge fallen.

## Sicherungskopie

Alle zwei Minuten legt der Editor den Arbeitsstand nach `~/.cache/handbuch-editor/entwurf.json`.
Die Dateien unter `docs/` rührt er nur an, wenn Sie 💾 drücken. Bricht etwas ab, bietet der
nächste Start den gefundenen Stand zur Übernahme an; nach dem Speichern ist der Entwurf erledigt.

## Erscheinungsbild

Unter „🌗 Ansicht“ stehen **Wie das System**, **Hell** und **Dunkel**. Die Wahl merkt sich der
Editor. Die Seiten der Vorschau bleiben in jedem Fall weiß – sie zeigen das gedruckte Handbuch.

Im selben Menü liegt die **Sprachanordnung**: „Sprachen nebeneinander“ (Vorgabe) oder „Sprachen
in Reitern“. Sie gilt überall gleich – Überschriften, Absätze, Aufzählungspunkte,
Bildunterschriften und Dokumentenkopf. Untereinander gibt es bewusst nicht: zwei Fassungen
desselben Satzes vergleicht man nebeneinander oder gar nicht. Bei offener Vorschau und langen
Absätzen sind die Reiter die bessere Wahl, sonst bleibt je Sprache wenig Breite.

## Blocktypen

Angeboten werden nur die Typen, die `build_manual.py` auch setzt: Kapitelüberschrift,
Abschnittsüberschrift, Absatz, Aufzählung, Bilder, Bild mit Text, Bilder mit Text, Ausschnitt mit
Text, Seitenumbruch. Symboltabelle und Codebeispiel gibt es je einmal; ihre Inhalte stehen im
**Dokumentenkopf**, dem ersten Eintrag im Baum links.

Der **Seitenumbruch** ist ein Block ohne Inhalt: ab ihm geht es im PDF auf einer neuen Seite
weiter. Vor jeder Kapitelüberschrift kommt ohnehin einer – dieser ist für die Stellen, an denen
der Satz sonst ungünstig trennt.

## Bildbreite

Jeder Bildblock hat **eine** Breite, auch eine Reihe: der Generator setzt alle Bilder einer Reihe
gleich groß. Das Feld steht deshalb oben am Block, nicht am einzelnen Bild. Passen die Bilder bei
der gewählten Breite nicht nebeneinander, verkleinert der Satz sie von allein – die Angabe ist
eine Obergrenze. Vorgabe sind 6 cm, bei „Ausschnitt mit Text“ 14 cm; solange nichts eingestellt
ist, steht in der Datei auch nichts.

## Bilder

Alle Bilder liegen sprachgetrennt unter `screenshots/de/` und `screenshots/en/` – gleicher
Dateiname, verschiedener Inhalt. Der Auswahldialog zeigt beide Fassungen nebeneinander; fehlt
eine, sieht man es dort und nicht erst im PDF.

Im Modell trägt ein Bild nur den **Dateinamen**; der Pfad entsteht erst beim Schreiben, je Sprache.
Deshalb können die beiden Sprachfassungen gar nicht auf verschiedene Bilder zeigen. Das gilt auch
für Ausschnitte („Ausschnitt mit Text“) – sie unterscheiden sich nur darin, dass sie im PDF in
voller Breite unter dem Text stehen statt daneben. Ältere Stände mit einem Pfad in der JSON
(`docs/img/…`) werden weiter gelesen und unverändert zurückgeschrieben.

`tools/screenshots.py` führt alle diese Bilder, Ausschnitte eingeschlossen – zuschneiden kann das
Aufnahmefenster ja auch.

## Verschieben

Blöcke lassen sich am Griff `⠿` ziehen; ein Strich zeigt, wohin der Block beim Loslassen fällt.
Der Scrollstand bleibt dabei, wo er war. Beim Wechsel in ein anderes Kapitel fängt die Ansicht
oben an.

## Tests

```
python3 -m unittest discover -s tools/handbuch_editor/tests -t tools
```

Sie laufen ohne Qt und prüfen unter anderem, dass Öffnen und Speichern die Handbuchdateien nicht
umformatiert.
