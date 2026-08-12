# -*- coding: utf-8 -*-
"""Bilder aussuchen, statt Dateinamen zu tippen.

Der Dialog zeigt den Bestand aus screenshots/de und screenshots/en nebeneinander. Beide Sprachen
teilen sich den Dateinamen, haben aber je ein eigenes Bild – fehlt eines davon, fällt es hier
sofort auf und nicht erst im fertigen PDF.
"""
from __future__ import annotations

import os

from PySide6.QtCore import Qt
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (QDialog, QDialogButtonBox, QHBoxLayout, QLabel, QListWidget,
                               QListWidgetItem, QVBoxLayout, QWidget)

VORSCHAU_HOEHE = 320


def bildpfad(repo: str, name: str, sprache: str) -> str:
    """Wo die Datei liegt. Alle Bilder wohnen sprachgetrennt unter screenshots/<lang>/.

    Trägt der Name schon ein Verzeichnis, stammt er aus einem älteren Stand und gilt ab der
    Repo-Wurzel.
    """
    if "/" in name:
        return os.path.join(repo, name.replace("/", os.sep))
    return os.path.join(repo, "screenshots", sprache, name)


def bestand(repo: str, sprache: str) -> list[str]:
    ordner = os.path.join(repo, "screenshots", sprache)
    if not os.path.isdir(ordner):
        return []
    return sorted(d for d in os.listdir(ordner) if d.lower().endswith((".png", ".jpg", ".jpeg")))


def _masse(pfad: str) -> str:
    if not os.path.isfile(pfad):
        return "fehlt"
    bild = QPixmap(pfad)
    groesse = os.path.getsize(pfad) / 1024
    return f"{bild.width()}×{bild.height()} · {groesse:.0f} kB"


class Vorschau(QWidget):
    """Ein Bild mit Beschriftung darunter; zeigt einen grauen Kasten, wenn es die Datei nicht gibt."""

    def __init__(self, titel: str):
        super().__init__()
        aufbau = QVBoxLayout(self)
        aufbau.setContentsMargins(0, 0, 0, 0)
        self.titel = QLabel(titel)
        self.titel.setAlignment(Qt.AlignHCenter)
        self.flaeche = QLabel()
        self.flaeche.setAlignment(Qt.AlignCenter)
        self.flaeche.setMinimumSize(180, VORSCHAU_HOEHE)
        self.flaeche.setStyleSheet("border: 1px solid palette(mid); background: palette(base);")
        self.masse = QLabel("")
        self.masse.setAlignment(Qt.AlignHCenter)
        for teil in (self.titel, self.flaeche, self.masse):
            aufbau.addWidget(teil)

    def zeige(self, pfad: str) -> None:
        if pfad and os.path.isfile(pfad):
            bild = QPixmap(pfad).scaledToHeight(VORSCHAU_HOEHE, Qt.SmoothTransformation)
            self.flaeche.setPixmap(bild)
        else:
            self.flaeche.setPixmap(QPixmap())
            self.flaeche.setText("Kein Bild")
        self.masse.setText(_masse(pfad) if pfad else "")


class Bildauswahl(QDialog):
    """Liste des Bestands links, beide Sprachfassungen rechts."""

    def __init__(self, eltern, repo: str, vorbelegt: str = "", benutzt: set[str] | None = None):
        super().__init__(eltern)
        self.setWindowTitle("Bild auswählen")
        self.repo = repo
        benutzt = benutzt or set()

        self.liste = QListWidget()
        self.liste.setMinimumWidth(280)
        namen = sorted(set(bestand(repo, "de")) | set(bestand(repo, "en")))
        for name in namen:
            eintrag = QListWidgetItem(name if name in benutzt else f"{name}   (noch unbenutzt)")
            eintrag.setData(Qt.UserRole, name)
            self.liste.addItem(eintrag)
            if name == vorbelegt:
                self.liste.setCurrentItem(eintrag)

        self.deutsch = Vorschau("Deutsch")
        self.englisch = Vorschau("English")

        knoepfe = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        knoepfe.accepted.connect(self.accept)
        knoepfe.rejected.connect(self.reject)

        rechts = QHBoxLayout()
        rechts.addWidget(self.deutsch)
        rechts.addWidget(self.englisch)

        oben = QHBoxLayout()
        oben.addWidget(self.liste)
        oben.addLayout(rechts, 1)

        aufbau = QVBoxLayout(self)
        aufbau.addLayout(oben)
        aufbau.addWidget(knoepfe)

        self.liste.currentItemChanged.connect(self._zeigen)
        self.liste.itemDoubleClicked.connect(lambda _: self.accept())
        self._zeigen(self.liste.currentItem(), None)

    def _zeigen(self, eintrag, _alt) -> None:
        name = eintrag.data(Qt.UserRole) if eintrag else ""
        self.deutsch.zeige(bildpfad(self.repo, name, "de") if name else "")
        self.englisch.zeige(bildpfad(self.repo, name, "en") if name else "")

    def gewaehlt(self) -> str:
        eintrag = self.liste.currentItem()
        return eintrag.data(Qt.UserRole) if eintrag else ""
