# -*- coding: utf-8 -*-
"""Die Kapitelvorschau: echtes PDF, nicht nachgebaut.

Gesetzt wird mit derselben Bibliothek und denselben Stilen wie das fertige Handbuch – der Editor
ruft dazu docs/build_manual.py auf. Ein Nachbau in HTML wäre schneller gewesen und hätte gerade
das verschwiegen, was man vorher sehen will: wo die Seite umbricht und was neben dem Bild landet.
"""
from __future__ import annotations

import importlib.util
import json
import logging
import os
import tempfile
import threading

from PySide6.QtCore import QObject, Qt, QThread, Signal
from PySide6.QtGui import QImage, QPixmap
from PySide6.QtWidgets import (QCheckBox, QHBoxLayout, QLabel, QPushButton, QScrollArea,
                               QVBoxLayout, QWidget)

from . import schreiben

protokoll = logging.getLogger(__name__)

#: build_manual.py hält Zustand in Modulvariablen. Zwei Bauten gleichzeitig kämen sich in die
#: Quere, deshalb darf immer nur einer laufen.
_baustelle = threading.Lock()

_bibliothek = None


def generator(repo: str):
    """docs/build_manual.py als Modul – über den Pfad, denn docs/ ist kein Paket."""
    global _bibliothek
    if _bibliothek is None:
        pfad = os.path.join(repo, "docs", "build_manual.py")
        beschreibung = importlib.util.spec_from_file_location("build_manual", pfad)
        _bibliothek = importlib.util.module_from_spec(beschreibung)
        beschreibung.loader.exec_module(_bibliothek)
    return _bibliothek


def aufteilung(repo: str, block) -> list[str]:
    """Für die Kinder eines Bildblocks: «neben», «darunter» oder «geteilt».

    Gerechnet wird mit der Schätzung aus build_manual.py – dieselbe Zahl, die beim Setzen
    entscheidet. Fällt der Aufruf aus, gilt vorsichtshalber alles als «neben».
    """
    try:
        inhalt = json.loads(schreiben.als_text_block(block, "de"))["content"]
        return generator(repo).aufteilung_der_inhalte(inhalt)
    except Exception:                                     # noqa: BLE001 – Anzeige, nicht Inhalt
        protokoll.exception("Aufteilung ließ sich nicht bestimmen")
        return ["neben"] * len(block.kinder)


# ------------------------------------------------------------------ Bauen im Hintergrund
class Bauauftrag(QObject):
    """Baut ein Kapitel als PDF und liefert die Seiten als Bilder."""

    fertig = Signal(list)
    misslungen = Signal(str)

    def __init__(self, repo: str, daten: dict, abschnitte: list, breite: int):
        super().__init__()
        self.repo, self.daten, self.abschnitte, self.breite = repo, daten, abschnitte, breite

    def laufen(self) -> None:
        try:
            with _baustelle:
                seiten = self._bauen()
        except Exception as fehler:                       # noqa: BLE001 – Vorschau darf scheitern
            protokoll.exception("Vorschau misslungen")
            self.misslungen.emit(str(fehler))
            return
        self.fertig.emit(seiten)

    def _bauen(self) -> list[QImage]:
        import pypdfium2

        with tempfile.TemporaryDirectory() as ordner:
            pfad = os.path.join(ordner, "vorschau.pdf")
            generator(self.repo).erzeuge(self.daten, "de", pfad,
                                         abschnitte=self.abschnitte, vorschau=True)
            seiten = []
            dokument = pypdfium2.PdfDocument(pfad)
            try:
                for nummer in range(len(dokument)):
                    seite = dokument[nummer]
                    masstab = self.breite / seite.get_width()
                    bild = seite.render(scale=max(0.5, min(masstab, 2.0))).to_pil()
                    daten = bild.convert("RGBA").tobytes("raw", "RGBA")
                    seiten.append(QImage(daten, bild.width, bild.height,
                                         QImage.Format_RGBA8888).copy())
            finally:
                dokument.close()
            return seiten


# ------------------------------------------------------------------ Anzeige
class Vorschauflaeche(QWidget):
    """Die Seiten des gewählten Kapitels, untereinander."""

    def __init__(self, fenster):
        super().__init__()
        self.fenster = fenster
        self._faden: QThread | None = None
        self._auftrag: Bauauftrag | None = None
        self._nachholen = False

        aufbau = QVBoxLayout(self)
        kopf = QHBoxLayout()
        self.mitlaufen = QCheckBox("laufend aktualisieren")
        self.mitlaufen.setChecked(True)
        self.mitlaufen.setToolTip("Nach jeder abgeschlossenen Änderung neu setzen")
        knopf = QPushButton("Jetzt neu setzen")
        knopf.clicked.connect(lambda: self.anfordern(sofort=True))
        kopf.addWidget(self.mitlaufen)
        kopf.addWidget(knopf)
        kopf.addStretch(1)
        aufbau.addLayout(kopf)

        self.meldung = QLabel("Noch nichts gesetzt.")
        self.meldung.setStyleSheet("color: palette(mid);")
        aufbau.addWidget(self.meldung)

        self.rolle = QScrollArea()
        self.rolle.setWidgetResizable(True)
        self.seitenflaeche = QWidget()
        self.seiten = QVBoxLayout(self.seitenflaeche)
        self.seiten.setAlignment(Qt.AlignTop | Qt.AlignHCenter)
        self.rolle.setWidget(self.seitenflaeche)
        aufbau.addWidget(self.rolle, 1)

    # ---------------------------------------------------------- Auftrag
    def anfordern(self, sofort: bool = False) -> None:
        """Neu setzen – oder vormerken, wenn gerade noch gebaut wird."""
        if not sofort and not self.mitlaufen.isChecked():
            return
        if not self.isVisible():
            return
        if self._faden is not None:
            self._nachholen = True
            return

        bereich = self.fenster.aktueller_bereich()
        if bereich is None:
            return
        self.meldung.setText("wird gesetzt …")

        daten = json.loads(schreiben.als_text(self.fenster.handbuch, "de"))
        abschnitte = daten["sections"][bereich[0]:bereich[1]]
        breite = max(360, self.rolle.viewport().width() - 30)

        self._auftrag = Bauauftrag(self.fenster.repo, daten, abschnitte, breite)
        self._faden = QThread(self)
        self._auftrag.moveToThread(self._faden)
        self._faden.started.connect(self._auftrag.laufen)
        self._auftrag.fertig.connect(self._zeigen)
        self._auftrag.misslungen.connect(self._gescheitert)
        self._auftrag.fertig.connect(self._aufraeumen)
        self._auftrag.misslungen.connect(self._aufraeumen)
        self._faden.start()

    def beenden(self) -> None:
        """Vor dem Schließen: auf den laufenden Bau warten.

        Sonst stirbt der Faden mit dem Fenster – Qt beschwert sich zu Recht, und im schlimmsten
        Fall stürzt das Programm beim Beenden ab.
        """
        self._nachholen = False
        if self._faden is not None:
            self._faden.quit()
            self._faden.wait(10_000)
            self._faden = None
            self._auftrag = None

    def _aufraeumen(self, *_egal) -> None:
        if self._faden is not None:
            self._faden.quit()
            self._faden.wait()
            self._faden = None
        self._auftrag = None
        if self._nachholen:
            self._nachholen = False
            self.anfordern()

    def _gescheitert(self, text: str) -> None:
        self.meldung.setText(f"Vorschau misslungen: {text}")

    def _zeigen(self, seiten: list) -> None:
        while self.seiten.count():
            teil = self.seiten.takeAt(0)
            if teil.widget():
                teil.widget().deleteLater()
        for nummer, bild in enumerate(seiten, start=1):
            marke = QLabel()
            marke.setPixmap(QPixmap.fromImage(bild))
            marke.setStyleSheet("border: 1px solid palette(mid); background: white;")
            self.seiten.addWidget(marke, alignment=Qt.AlignHCenter)
            zaehler = QLabel(f"Seite {nummer} von {len(seiten)}")
            zaehler.setStyleSheet("color: palette(mid);")
            self.seiten.addWidget(zaehler, alignment=Qt.AlignHCenter)
        self.meldung.setText(f"{len(seiten)} Seite{'n' if len(seiten) != 1 else ''} · "
                             f"so steht es im deutschen Handbuch")
