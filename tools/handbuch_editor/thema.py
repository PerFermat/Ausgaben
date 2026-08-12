# -*- coding: utf-8 -*-
"""Helles und dunkles Erscheinungsbild.

Vorgabe ist «System»: der Editor übernimmt, was der Schreibtisch vorgibt, und wechselt mit, wenn
der dort abends umschaltet. Wer das nicht will, nagelt hell oder dunkel fest.

Die Seiten der Vorschau bleiben in jedem Fall weiß – sie zeigen das gedruckte Handbuch, und das
ist nun einmal weiß. Sie umzufärben hieße, etwas anderes zu zeigen als das, was hinterher da ist.
"""
from __future__ import annotations

from PySide6.QtCore import QSettings, Qt
from PySide6.QtGui import QColor, QPalette
from PySide6.QtWidgets import QApplication

#: Die drei Möglichkeiten samt Beschriftung im Menü.
STUFEN = (("system", "Wie das System"), ("hell", "Hell"), ("dunkel", "Dunkel"))


def einstellungen() -> QSettings:
    return QSettings("Ausgaben", "Handbuch-Editor")


def gewaehlt() -> str:
    wert = einstellungen().value("thema", "system")
    return wert if wert in dict(STUFEN) else "system"


def merken(stufe: str) -> None:
    einstellungen().setValue("thema", stufe)


def sprachen_nebeneinander() -> bool:
    """Stehen Deutsch und Englisch nebeneinander (sonst in Reitern)?"""
    return einstellungen().value("sprachen_nebeneinander", True, type=bool)


def sprachanordnung_merken(nebeneinander: bool) -> None:
    einstellungen().setValue("sprachen_nebeneinander", nebeneinander)


def _dunkle_palette() -> QPalette:
    palette = QPalette()
    grund = QColor(0x2b, 0x2b, 0x2b)
    flaeche = QColor(0x35, 0x35, 0x35)
    schrift = QColor(0xe6, 0xe6, 0xe6)
    for rolle, farbe in ((QPalette.Window, grund), (QPalette.Base, flaeche),
                         (QPalette.AlternateBase, grund), (QPalette.Button, flaeche),
                         (QPalette.ToolTipBase, flaeche), (QPalette.Text, schrift),
                         (QPalette.WindowText, schrift), (QPalette.ButtonText, schrift),
                         (QPalette.ToolTipText, schrift), (QPalette.BrightText, Qt.red),
                         (QPalette.Highlight, QColor(0x2e, 0x7d, 0x32)),
                         (QPalette.HighlightedText, Qt.white),
                         (QPalette.Mid, QColor(0x8a, 0x8a, 0x8a))):
        palette.setColor(rolle, farbe)
    palette.setColor(QPalette.Disabled, QPalette.Text, QColor(0x80, 0x80, 0x80))
    palette.setColor(QPalette.Disabled, QPalette.ButtonText, QColor(0x80, 0x80, 0x80))
    return palette


def anwenden(stufe: str) -> None:
    """Das gewählte Erscheinungsbild auf die ganze Anwendung legen."""
    anwendung = QApplication.instance()
    if anwendung is None:
        return
    if stufe == "system":
        anwendung.setStyle(_ausgangsstil())
        anwendung.setPalette(anwendung.style().standardPalette())
        return
    anwendung.setStyle("Fusion")
    if stufe == "dunkel":
        anwendung.setPalette(_dunkle_palette())
    else:
        anwendung.setPalette(QApplication.style().standardPalette())


_stil = [None]


def merke_ausgangsstil() -> None:
    """Gleich nach dem Start aufrufen, bevor irgendein Thema gesetzt wurde."""
    anwendung = QApplication.instance()
    if anwendung is not None and _stil[0] is None:
        _stil[0] = anwendung.style().objectName()


def _ausgangsstil() -> str:
    """Der Stil, mit dem Qt gestartet ist – dorthin führt «Wie das System» zurück."""
    merke_ausgangsstil()
    return _stil[0] or "Fusion"
