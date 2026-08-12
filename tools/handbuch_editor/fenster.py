# -*- coding: utf-8 -*-
"""Das Hauptfenster: Kapitelbaum links, Blockkarten in der Mitte, Werkzeuge oben.

Rückgängig läuft über Momentaufnahmen des ganzen Handbuchs. Bei 161 Blöcken kostet das nichts,
und es erspart ein Dutzend Kommandoklassen, die jede für sich falsch sein könnten – gespeichert
wird ohnehin immer das Ganze.
"""
from __future__ import annotations

import json
import logging
import os

from PySide6.QtCore import QTimer, Qt
from PySide6.QtGui import QAction, QActionGroup, QKeySequence, QUndoCommand, QUndoStack
from PySide6.QtWidgets import (QCheckBox, QComboBox, QDockWidget, QHBoxLayout, QHeaderView,
                               QLabel, QLineEdit, QListWidget, QListWidgetItem, QMainWindow,
                               QMessageBox, QPushButton, QScrollArea, QSplitter, QTableWidget,
                               QTableWidgetItem, QMenu, QToolButton, QTreeWidget, QTreeWidgetItem,
                               QVBoxLayout, QWidget)

from . import abgleich, laden, pruefung, schreiben, sicherung, thema, vorschau, werkzeuge
from .bloecke import Blockliste, SPRACHEN, Textfeld, Zeilenfeld, sprachpaar
from .modell import KOPF_KLARTEXT, KOPFTEXTE, KOPFTEXTE_SPAET, Symbolzeile, Typ

protokoll = logging.getLogger(__name__)


class Momentaufnahme(QUndoCommand):
    """Ein Bearbeitungsschritt als Vorher/Nachher-Paar des gesamten Handbuchs."""

    def __init__(self, fenster: "Hauptfenster", beschreibung: str,
                 vorher: tuple[str, str], nachher: tuple[str, str]):
        super().__init__(beschreibung)
        self.fenster = fenster
        self.vorher = vorher
        self.nachher = nachher
        self._erstes_redo = True

    def redo(self) -> None:
        if self._erstes_redo:          # beim Einreihen steht das Modell schon auf «nachher»
            self._erstes_redo = False
            return
        self.fenster.zustand_setzen(self.nachher)

    def undo(self) -> None:
        self.fenster.zustand_setzen(self.vorher)


# ------------------------------------------------------------------ Dokumentenkopf
class Kopfseite(QWidget):
    """Titel, Fußzeile, Codebeispiel und die Symboltabelle – alles, was nicht in «sections» steht."""

    def __init__(self, fenster: "Hauptfenster"):
        super().__init__()
        self.fenster = fenster
        kopf = fenster.handbuch.kopf
        aufbau = QVBoxLayout(self)

        for schluessel in KOPFTEXTE + KOPFTEXTE_SPAET:
            wert = kopf.text(schluessel)
            if schluessel == "code_example":
                felder = [Textfeld(wert.hol(sprache),
                                   lambda neu, s=sprache, w=wert: w.setz(s, neu),
                                   fenster, "Dokumentenkopf geändert", zeilen=3)
                          for sprache, _name in SPRACHEN]
            else:
                felder = [Zeilenfeld(wert.hol(sprache),
                                     lambda neu, s=sprache, w=wert: w.setz(s, neu),
                                     fenster, "Dokumentenkopf geändert")
                          for sprache, _name in SPRACHEN]
            aufbau.addWidget(sprachpaar(felder, KOPF_KLARTEXT[schluessel]))

        aufbau.addWidget(QLabel("<b>Spaltenköpfe der Symboltabelle</b>"))
        koepfe = QHBoxLayout()
        for nummer, spalte in enumerate(kopf.tabellenkoepfe, start=1):
            felder = [Zeilenfeld(spalte.hol(sprache),
                                 lambda neu, s=sprache, w=spalte: w.setz(s, neu),
                                 fenster, "Spaltenkopf geändert")
                      for sprache, _name in SPRACHEN]
            koepfe.addWidget(sprachpaar(felder, f"Spalte {nummer}"), 1)
        aufbau.addLayout(koepfe)

        aufbau.addWidget(QLabel("<b>Symboltabelle</b>"))
        self.tabelle = QTableWidget(len(kopf.symbole), 6)
        self.tabelle.setHorizontalHeaderLabels(
            ["Symbol (DE)", "Name (DE)", "Bedeutung (DE)",
             "Symbol (EN)", "Name (EN)", "Bedeutung (EN)"])
        self.tabelle.horizontalHeader().setSectionResizeMode(2, QHeaderView.Stretch)
        self.tabelle.horizontalHeader().setSectionResizeMode(5, QHeaderView.Stretch)
        self._tabelle_fuellen()
        self.tabelle.cellChanged.connect(self._zelle_geaendert)
        aufbau.addWidget(self.tabelle, 1)

        knoepfe = QHBoxLayout()
        hinzu = QPushButton("+ Zeile")
        hinzu.clicked.connect(self._zeile_anhaengen)
        weg = QPushButton("Zeile löschen")
        weg.clicked.connect(self._zeile_loeschen)
        knoepfe.addWidget(hinzu)
        knoepfe.addWidget(weg)
        knoepfe.addStretch(1)
        aufbau.addLayout(knoepfe)

    def _tabelle_fuellen(self) -> None:
        self._fuellt = True
        self.tabelle.setRowCount(len(self.fenster.handbuch.kopf.symbole))
        for nummer, zeile in enumerate(self.fenster.handbuch.kopf.symbole):
            werte = [zeile.zeichen.de, zeile.name.de, zeile.bedeutung.de,
                     zeile.zeichen.en, zeile.name.en, zeile.bedeutung.en]
            for spalte, wert in enumerate(werte):
                self.tabelle.setItem(nummer, spalte, QTableWidgetItem(wert))
        self._fuellt = False

    def _zelle_geaendert(self, zeile: int, spalte: int) -> None:
        if getattr(self, "_fuellt", False):
            return
        vorher = self.fenster.zustand()
        eintrag = self.fenster.handbuch.kopf.symbole[zeile]
        felder = [(eintrag.zeichen, "de"), (eintrag.name, "de"), (eintrag.bedeutung, "de"),
                  (eintrag.zeichen, "en"), (eintrag.name, "en"), (eintrag.bedeutung, "en")]
        feld, sprache = felder[spalte]
        feld.setz(sprache, self.tabelle.item(zeile, spalte).text())
        self.fenster.aenderung("Symboltabelle geändert", vorher)

    def _zeile_anhaengen(self) -> None:
        vorher = self.fenster.zustand()
        self.fenster.handbuch.kopf.symbole.append(Symbolzeile())
        self.fenster.aenderung("Symbolzeile hinzugefügt", vorher)
        self._tabelle_fuellen()

    def _zeile_loeschen(self) -> None:
        zeile = self.tabelle.currentRow()
        if zeile < 0:
            return
        vorher = self.fenster.zustand()
        del self.fenster.handbuch.kopf.symbole[zeile]
        self.fenster.aenderung("Symbolzeile gelöscht", vorher)
        self._tabelle_fuellen()


# ------------------------------------------------------------------ Kapitelbaum
class Kapitelbaum(QTreeWidget):
    """Der Baum links. Ein Kapitel zu ziehen bewegt alles, was darunter hängt.

    Der Baum ist nur die Sicht; das Modell bleibt die flache Blockfolge. Verschoben wird deshalb
    ein ganzer Bereich – von der Überschrift bis zur nächsten gleichen Ranges.
    """

    def __init__(self, fenster: "Hauptfenster"):
        super().__init__()
        self.fenster = fenster
        self.setHeaderHidden(True)
        self.setMinimumWidth(280)
        self.setDragDropMode(QTreeWidget.InternalMove)

    def dropEvent(self, ereignis):
        quelle = self.currentItem()
        ziel = self.itemAt(ereignis.position().toPoint())
        if quelle is None or ziel is None or quelle is ziel:
            return
        art_q = quelle.data(0, Qt.UserRole)
        art_z = ziel.data(0, Qt.UserRole)
        if not art_q or not art_z or art_q[0] == "kopf" or art_z[0] == "kopf":
            return
        self.fenster.bereich_verschieben(art_q, art_z)
        ereignis.setDropAction(Qt.IgnoreAction)     # das Modell hat schon umsortiert
        ereignis.accept()


# ------------------------------------------------------------------ Hauptfenster
class Hauptfenster(QMainWindow):

    def __init__(self, repo: str):
        super().__init__()
        self.repo = repo
        self.pfad_de, self.pfad_en = laden.pfade(repo)
        self.stapel = QUndoStack(self)
        self.stapel.cleanChanged.connect(lambda _: self._titel())
        self.gewaehlte_kennung: str | None = None

        self.handbuch, bericht = laden.lade(self.pfad_de, self.pfad_en)

        self._bauen()
        self.neu_zeichnen()
        self._titel()

        if not bericht.leer():
            self._bericht_zeigen(bericht)
        self._entwurf_anbieten()

        self.uhr = QTimer(self)
        self.uhr.timeout.connect(self._sichern)
        self.uhr.start(120_000)                  # alle zwei Minuten

    # ---------------------------------------------------------- Sicherungskopie
    def _sichern(self) -> None:
        if not self.stapel.isClean():
            sicherung.ablegen(self.zustand(), self.repo)

    def _entwurf_anbieten(self) -> None:
        """Ein liegengebliebener Stand vom letzten Mal – übernehmen oder verwerfen."""
        gefunden = sicherung.holen(self.repo)
        if gefunden is None:
            return
        stand, zeit = gefunden
        if stand == self.zustand():
            sicherung.wegwerfen()                # deckungsgleich, nichts zu retten
            return
        antwort = QMessageBox.question(
            self, "Liegengebliebener Stand",
            f"Vom letzten Mal liegt ein ungesicherter Stand vor ({sicherung.alter(zeit)}).\n"
            f"Übernehmen? «Nein» verwirft ihn; die Dateien in docs/ sind unberührt.")
        if antwort != QMessageBox.Yes:
            sicherung.wegwerfen()
            return
        vorher = self.zustand()
        self.zustand_setzen(stand)
        self.stapel.push(Momentaufnahme(self, "Stand wiederhergestellt", vorher, stand))
        self._titel()

    # ---------------------------------------------------------- Aufbau
    def _bauen(self) -> None:
        self.baum = Kapitelbaum(self)
        self.baum.currentItemChanged.connect(self._baum_gewaehlt)

        self.flaeche = QScrollArea()
        self.flaeche.setWidgetResizable(True)

        teiler = QSplitter()
        teiler.addWidget(self.baum)
        teiler.addWidget(self.flaeche)
        teiler.setStretchFactor(1, 1)
        self.setCentralWidget(teiler)

        self._werkzeugleiste()
        self._vorschauleiste()
        self._suchleiste()
        self._befundleiste()
        self.resize(1600, 900)

    def _vorschauleiste(self) -> None:
        kasten = QDockWidget("Vorschau (Deutsch)", self)
        kasten.setAllowedAreas(Qt.RightDockWidgetArea | Qt.LeftDockWidgetArea)
        self.vorschau = vorschau.Vorschauflaeche(self)
        kasten.setWidget(self.vorschau)
        kasten.visibilityChanged.connect(
            lambda sichtbar: self.vorschau.anfordern() if sichtbar else None)
        self.addDockWidget(Qt.RightDockWidgetArea, kasten)
        kasten.resize(520, kasten.height())
        self.vorschaukasten = kasten

    def _werkzeugleiste(self) -> None:
        leiste = self.addToolBar("Werkzeuge")
        leiste.setMovable(False)

        def tat(text: str, kurz, ruf) -> QAction:
            aktion = QAction(text, self)
            if kurz:
                aktion.setShortcut(QKeySequence(kurz))
            aktion.triggered.connect(ruf)
            leiste.addAction(aktion)
            return aktion

        tat("💾 Speichern", "Ctrl+S", self.speichern)
        tat("🔍 Prüfen", "Ctrl+P", self.pruefen)
        leiste.addSeparator()
        tat("🖼 Bilder erzeugen", None, self.bilder_erzeugen)
        tat("📘 Deutsches Handbuch", None, lambda: self.handbuch_erzeugen(["de"]))
        tat("📗 Englisches Handbuch", None, lambda: self.handbuch_erzeugen(["en"]))
        tat("📚 Beide Handbücher", None, lambda: self.handbuch_erzeugen(["de", "en"]))
        leiste.addSeparator()

        self._themamenue(leiste)
        leiste.addSeparator()

        zurueck = self.stapel.createUndoAction(self, "↶ Rückgängig")
        zurueck.setShortcut(QKeySequence.Undo)
        vor = self.stapel.createRedoAction(self, "↷ Wiederholen")
        vor.setShortcut(QKeySequence.Redo)
        leiste.addAction(zurueck)
        leiste.addAction(vor)

    def _themamenue(self, leiste) -> None:
        """Erscheinungsbild: System, hell oder dunkel – die Wahl überlebt den Neustart."""
        knopf = QToolButton()
        knopf.setText("🌗 Ansicht")
        knopf.setPopupMode(QToolButton.InstantPopup)
        menue = QMenu(knopf)
        gruppe = QActionGroup(self)
        gruppe.setExclusive(True)
        for stufe, name in thema.STUFEN:
            aktion = QAction(name, self, checkable=True)
            aktion.setChecked(stufe == thema.gewaehlt())
            aktion.triggered.connect(lambda _an, s=stufe: self._thema_setzen(s))
            gruppe.addAction(aktion)
            menue.addAction(aktion)

        menue.addSeparator()
        anordnung = QActionGroup(self)
        anordnung.setExclusive(True)
        for nebeneinander, name in ((True, "Sprachen nebeneinander"),
                                    (False, "Sprachen in Reitern")):
            aktion = QAction(name, self, checkable=True)
            aktion.setChecked(nebeneinander == thema.sprachen_nebeneinander())
            aktion.triggered.connect(lambda _an, n=nebeneinander: self._anordnung_setzen(n))
            anordnung.addAction(aktion)
            menue.addAction(aktion)

        knopf.setMenu(menue)
        leiste.addWidget(knopf)

    def _anordnung_setzen(self, nebeneinander: bool) -> None:
        thema.sprachanordnung_merken(nebeneinander)
        self.neu_zeichnen()          # die Felder werden neu gebaut, die Anordnung liegt in ihnen

    def _thema_setzen(self, stufe: str) -> None:
        thema.merken(stufe)
        thema.anwenden(stufe)
        self.neu_zeichnen()          # die Tönung der Blockkarten mischt aus der Palette

    def _suchleiste(self) -> None:
        kasten = QDockWidget("Suche", self)
        kasten.setAllowedAreas(Qt.RightDockWidgetArea | Qt.LeftDockWidgetArea)
        inhalt = QWidget()
        aufbau = QVBoxLayout(inhalt)

        zeile = QHBoxLayout()
        self.suchfeld = QLineEdit()
        self.suchfeld.setPlaceholderText("Suchen …")
        self.suchfeld.returnPressed.connect(self.suchen)
        self.sprachwahl = QComboBox()
        self.sprachwahl.addItems(["beide Sprachen", "nur Deutsch", "nur Englisch"])
        zeile.addWidget(self.suchfeld, 1)
        zeile.addWidget(self.sprachwahl)
        aufbau.addLayout(zeile)

        self.nur_kapitel = QCheckBox("nur im aktuellen Kapitel")
        aufbau.addWidget(self.nur_kapitel)

        self.treffer = QListWidget()
        self.treffer.itemDoubleClicked.connect(self._treffer_anspringen)
        aufbau.addWidget(self.treffer, 1)

        kasten.setWidget(inhalt)
        self.addDockWidget(Qt.RightDockWidgetArea, kasten)
        self.suchkasten = kasten

    def _befundleiste(self) -> None:
        kasten = QDockWidget("Prüfung", self)
        self.befunde = QListWidget()
        self.befunde.itemDoubleClicked.connect(self._treffer_anspringen)
        kasten.setWidget(self.befunde)
        self.addDockWidget(Qt.BottomDockWidgetArea, kasten)
        kasten.hide()
        self.befundkasten = kasten

    # ---------------------------------------------------------- Zustand und Rückgängig
    def zustand(self) -> tuple[str, str]:
        """Eine Momentaufnahme des ganzen Handbuchs."""
        return (schreiben.als_text(self.handbuch, "de"), schreiben.als_text(self.handbuch, "en"))

    def zustand_setzen(self, stand: tuple[str, str]) -> None:
        de, en = (json.loads(teil) for teil in stand)
        self.handbuch, _ = laden.aus_daten(de, en)
        self.neu_zeichnen()
        self._titel()

    def aenderung(self, beschreibung: str, vorher: tuple[str, str] | None) -> None:
        """Einen abgeschlossenen Bearbeitungsschritt einreihen."""
        if vorher is None:
            return
        nachher = self.zustand()
        if vorher == nachher:
            return
        self.stapel.push(Momentaufnahme(self, beschreibung, vorher, nachher))
        self._titel()
        mitte = self.flaeche.widget()
        if isinstance(mitte, Blockliste):
            mitte.lagen_auffrischen()       # längerer Text schiebt den nächsten unter das Bild
        self.vorschau.anfordern()

    def blockliste_von(self, block):
        try:
            return self.handbuch.elternliste(block)
        except ValueError:
            return None

    def nachfragen(self, text: str) -> bool:
        return QMessageBox.question(self, "Nachfrage", text) == QMessageBox.Yes

    # ---------------------------------------------------------- Anzeige
    def _kapitel(self) -> list[tuple[int, int]]:
        """Die Bereiche (von, bis) je Kapitelüberschrift; Vorspann ohne h1 zählt als eigenes."""
        grenzen = [nummer for nummer, block in enumerate(self.handbuch.bloecke)
                   if block.typ is Typ.KAPITEL]
        if not grenzen or grenzen[0] != 0:
            grenzen.insert(0, 0)
        bereiche = []
        for stelle, anfang in enumerate(grenzen):
            ende = grenzen[stelle + 1] if stelle + 1 < len(grenzen) else len(self.handbuch.bloecke)
            bereiche.append((anfang, ende))
        return bereiche

    def neu_zeichnen(self) -> None:
        """Baum und Mittelteil neu aufbauen – nach jeder Änderung an der Struktur.

        Der Scrollstand wird gemerkt und wiederhergestellt: wer weit unten im Kapitel einen Block
        verschiebt, will dort weiterarbeiten und nicht wieder oben anfangen.
        """
        self._scrollstand = self.flaeche.verticalScrollBar().value()
        merker = self.baum.currentItem()
        gewaehlt = merker.data(0, Qt.UserRole) if merker else ("kapitel", 0)

        self.baum.blockSignals(True)
        self.baum.clear()
        kopf = QTreeWidgetItem(["Dokumentenkopf"])
        kopf.setData(0, Qt.UserRole, ("kopf", 0))
        self.baum.addTopLevelItem(kopf)

        for nummer, (anfang, ende) in enumerate(self._kapitel()):
            erster = self.handbuch.bloecke[anfang] if anfang < len(self.handbuch.bloecke) else None
            titel = erster.text.de if erster and erster.typ is Typ.KAPITEL else "(Vorspann)"
            eintrag = QTreeWidgetItem([titel or "(ohne Titel)"])
            eintrag.setData(0, Qt.UserRole, ("kapitel", nummer))
            self.baum.addTopLevelItem(eintrag)
            for stelle in range(anfang, ende):
                block = self.handbuch.bloecke[stelle]
                if block.typ is Typ.ABSCHNITT:
                    unter = QTreeWidgetItem([block.text.de or "(ohne Titel)"])
                    unter.setData(0, Qt.UserRole, ("block", block.kennung))
                    eintrag.addChild(unter)
        self.baum.expandAll()
        self.baum.blockSignals(False)

        self._auswahl_setzen(gewaehlt)

    def _auswahl_setzen(self, gewaehlt) -> None:
        """Baumeintrag und Mittelteil zugleich setzen.

        Die Signale des Baums bleiben dabei stumm: sonst löste setCurrentItem einen zweiten
        Aufbau aus, und der hielte das Kapitel für unverändert – der Scrollstand des vorigen
        Kapitels stünde dann im neuen.
        """
        art, wert = gewaehlt if gewaehlt else ("kapitel", 0)
        self.baum.blockSignals(True)
        try:
            if art == "kopf":
                self.baum.setCurrentItem(self.baum.topLevelItem(0))
            else:
                nummer = wert if art == "kapitel" else self._kapitel_von(wert)
                nummer = min(max(nummer, 0), max(0, self.baum.topLevelItemCount() - 2))
                self.baum.setCurrentItem(self.baum.topLevelItem(nummer + 1))
        finally:
            self.baum.blockSignals(False)

        if art == "kopf":
            self._zeige_kopf()
        else:
            self._zeige_kapitel(nummer)

    def _kapitel_von(self, kennung: str) -> int:
        for nummer, (anfang, ende) in enumerate(self._kapitel()):
            for stelle in range(anfang, ende):
                if any(b.kennung == kennung for b in self.handbuch.bloecke[stelle].durchlaufen()):
                    return nummer
        return 0

    def _bereich_von(self, art) -> tuple[int, int] | None:
        """Welcher Ausschnitt der Blockfolge hängt an diesem Baumeintrag?"""
        kind, wert = art
        bereiche = self._kapitel()
        if kind == "kapitel":
            return bereiche[wert] if wert < len(bereiche) else None
        for anfang, ende in bereiche:
            for stelle in range(anfang, ende):
                if self.handbuch.bloecke[stelle].kennung != wert:
                    continue
                schluss = ende
                for weiter in range(stelle + 1, ende):
                    if self.handbuch.bloecke[weiter].typ in (Typ.ABSCHNITT, Typ.KAPITEL):
                        schluss = weiter
                        break
                return (stelle, schluss)
        return None

    def bereich_verschieben(self, quelle, ziel) -> None:
        """Ein Kapitel oder einen Abschnitt samt Inhalt vor einen anderen setzen."""
        von = self._bereich_von(quelle)
        nach = self._bereich_von(ziel)
        if von is None or nach is None or von == nach:
            return
        anfang, ende = von
        if anfang <= nach[0] < ende:
            return                                   # nicht in sich selbst hinein

        vorher = self.zustand()
        stuecke = self.handbuch.bloecke[anfang:ende]
        del self.handbuch.bloecke[anfang:ende]
        einfuegen = nach[0] - (ende - anfang) if nach[0] > anfang else nach[0]
        self.handbuch.bloecke[einfuegen:einfuegen] = stuecke
        self.aenderung("Kapitel verschoben" if quelle[0] == "kapitel" else "Abschnitt verschoben",
                       vorher)
        self.neu_zeichnen()

    def _baum_gewaehlt(self, eintrag, _alt) -> None:
        if eintrag is None:
            return
        art, wert = eintrag.data(0, Qt.UserRole)
        if art == "kopf":
            self._zeige_kopf()
        elif art == "kapitel":
            self._zeige_kapitel(wert)
        else:
            self._zeige_kapitel(self._kapitel_von(wert))

    def _zeige_kopf(self) -> None:
        self._bereich = None            # der Dokumentenkopf hat keine eigenen Seiten
        self.flaeche.setWidget(Kopfseite(self))

    def _zeige_kapitel(self, nummer: int) -> None:
        bereiche = self._kapitel()
        if not bereiche:
            self.flaeche.setWidget(QWidget())
            self._bereich = None
            return
        nummer = min(max(nummer, 0), len(bereiche) - 1)
        gleiches_kapitel = self._bereich == bereiche[nummer] if hasattr(self, "_bereich") else False
        self._bereich = bereiche[nummer]
        self.flaeche.setWidget(Blockliste(self, self.handbuch.bloecke, bereiche[nummer]))
        # Dasselbe Kapitel: an der Stelle bleiben. Ein anderes: oben anfangen – sonst steht man
        # mitten im neuen Kapitel, nur weil der Balken zufällig eine Höhe behalten hat.
        self._scrollstand_setzen(getattr(self, "_scrollstand", 0) if gleiches_kapitel else 0)
        if hasattr(self, "vorschau"):
            self.vorschau.anfordern()

    def _scrollstand_setzen(self, stand: int) -> None:
        """Erst wenn die Karten ihre Höhe kennen, lässt sich der Balken setzen."""
        def setzen():
            balken = self.flaeche.verticalScrollBar()
            balken.setValue(min(stand, balken.maximum()))

        QTimer.singleShot(0, setzen)

    def aktueller_bereich(self) -> tuple[int, int] | None:
        """Welcher Ausschnitt der Blockfolge steht gerade in der Mitte? Für die Vorschau."""
        return getattr(self, "_bereich", None)

    def _titel(self) -> None:
        stern = "" if self.stapel.isClean() else " *"
        self.setWindowTitle(f"Handbuch-Editor – {os.path.basename(self.repo)}{stern}")

    # ---------------------------------------------------------- Werkzeuge
    def speichern(self) -> None:
        try:
            schreiben.speichere(self.handbuch, self.pfad_de, self.pfad_en)
        except OSError as fehler:
            QMessageBox.critical(self, "Speichern misslungen", str(fehler))
            return
        self.stapel.setClean()
        sicherung.wegwerfen()
        self._titel()
        self.statusBar().showMessage("Gespeichert.", 4000)
        protokoll.info("gespeichert")

    def pruefen(self) -> None:
        befunde = pruefung.pruefe(self.handbuch, self.repo)
        self.befunde.clear()
        for befund in befunde:
            eintrag = QListWidgetItem(str(befund))
            if befund.kennung:
                eintrag.setData(Qt.UserRole, ("block", befund.kennung))
            self.befunde.addItem(eintrag)
        if not befunde:
            self.befunde.addItem("Nichts zu beanstanden.")
        self.befundkasten.show()

    def _treffer_anspringen(self, eintrag) -> None:
        ziel = eintrag.data(Qt.UserRole)
        if ziel:
            self._auswahl_setzen(("block", ziel[1]))

    def suchen(self) -> None:
        begriff = self.suchfeld.text().strip().lower()
        self.treffer.clear()
        if not begriff:
            return
        sprachen = {"beide Sprachen": ("de", "en"), "nur Deutsch": ("de",),
                    "nur Englisch": ("en",)}[self.sprachwahl.currentText()]

        stellen = range(len(self.handbuch.bloecke))
        if self.nur_kapitel.isChecked():
            eintrag = self.baum.currentItem()
            art, wert = eintrag.data(0, Qt.UserRole) if eintrag else ("kapitel", 0)
            nummer = wert if art == "kapitel" else self._kapitel_von(wert)
            bereiche = self._kapitel()
            if bereiche:
                anfang, ende = bereiche[min(nummer, len(bereiche) - 1)]
                stellen = range(anfang, ende)

        for stelle in stellen:
            for block in self.handbuch.bloecke[stelle].durchlaufen():
                for sprache in sprachen:
                    texte = [block.text.hol(sprache)]
                    texte += [p.hol(sprache) for p in block.punkte]
                    texte += [b.unterschrift.hol(sprache) for b in block.bilder]
                    if any(begriff in text.lower() for text in texte):
                        zeile = QListWidgetItem(f"{block.typ.klartext}: {block.ueberschrift()}")
                        zeile.setData(Qt.UserRole, ("block", block.kennung))
                        self.treffer.addItem(zeile)
                        break
        if self.treffer.count() == 0:
            self.treffer.addItem("Kein Treffer.")

    def _erst_speichern(self) -> bool:
        if self.stapel.isClean():
            return True
        antwort = QMessageBox.question(self, "Ungesicherte Änderungen",
                                       "Vor dem Erzeugen speichern?",
                                       QMessageBox.Yes | QMessageBox.No | QMessageBox.Cancel)
        if antwort == QMessageBox.Cancel:
            return False
        if antwort == QMessageBox.Yes:
            self.speichern()
        return True

    def handbuch_erzeugen(self, sprachen: list[str]) -> None:
        if not self._erst_speichern():
            return
        fenster = werkzeuge.Logfenster(self, "Handbuch erzeugen")
        fenster.fertig.connect(lambda stand: self._pdfs_zeigen(stand, sprachen))
        fenster.show()
        fenster.starte([werkzeuge.handbuch_befehl(self.repo, s) for s in sprachen], self.repo)

    def _pdfs_zeigen(self, stand: int, sprachen: list[str]) -> None:
        if stand != 0:
            return
        for sprache in sprachen:
            werkzeuge.oeffne_datei(werkzeuge.pdf_pfad(self.repo, sprache))

    def bilder_erzeugen(self) -> None:
        if not self._erst_speichern():
            return
        sprache = "de" if QMessageBox.question(
            self, "Bilder erzeugen", "Deutschen Satz aufnehmen?\n(Nein = englischer Satz)"
        ) == QMessageBox.Yes else "en"
        fenster = werkzeuge.Logfenster(self, "Bilder erzeugen")
        fenster.show()
        fenster.starte([werkzeuge.bilder_befehl(self.repo, sprache)], self.repo)

    def _bericht_zeigen(self, bericht: abgleich.Bericht) -> None:
        einzeln = (bericht.ergaenzt + bericht.entfernt + bericht.typ_berichtigt + bericht.bilder)
        kasten = QMessageBox(self)
        kasten.setWindowTitle("Struktur abgeglichen")
        kasten.setText("\n".join(bericht.zeilen()))
        kasten.setInformativeText("Geschrieben wird erst beim Speichern.")
        kasten.setDetailedText("\n".join(einzeln))
        kasten.exec()

    # ---------------------------------------------------------- Schließen
    def closeEvent(self, ereignis):
        if not self.stapel.isClean():
            antwort = QMessageBox.question(self, "Ungesicherte Änderungen",
                                           "Änderungen vor dem Beenden speichern?",
                                           QMessageBox.Yes | QMessageBox.No | QMessageBox.Cancel)
            if antwort == QMessageBox.Cancel:
                ereignis.ignore()
                return
            if antwort == QMessageBox.Yes:
                self.speichern()
            else:
                self._sichern()          # verworfen wird nicht: der Entwurf bleibt liegen
        self.uhr.stop()
        self.vorschau.beenden()
        ereignis.accept()
