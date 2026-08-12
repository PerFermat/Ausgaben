# -*- coding: utf-8 -*-
"""Startpunkt des Handbuch-Editors.

Aufgerufen wird er über tools/handbuch-editor.py – das richtet die Umgebung ein und legt den
Suchpfad zurecht. Von Hand geht auch:  PYTHONPATH=tools python3 -m handbuch_editor
"""
from __future__ import annotations

import argparse
import logging
import os
import sys
import traceback

from PySide6.QtWidgets import QApplication, QMessageBox

from . import thema
from .abgleich import UnbekannterTyp
from .fenster import Hauptfenster

PROJEKT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def protokoll_einrichten() -> str:
    ordner = os.path.join(os.path.expanduser("~"), ".cache", "handbuch-editor")
    os.makedirs(ordner, exist_ok=True)
    pfad = os.path.join(ordner, "editor.log")
    logging.basicConfig(filename=pfad, level=logging.INFO, encoding="utf-8",
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    return pfad


def fehlerhaken(fenster) -> None:
    """Unerwartete Ausnahmen gehören in einen Dialog, nicht in eine Konsole, die niemand sieht."""

    def haken(art, wert, spur):
        text = "".join(traceback.format_exception(art, wert, spur))
        logging.getLogger(__name__).error("unerwarteter Fehler\n%s", text)
        kasten = QMessageBox(QMessageBox.Critical, "Unerwarteter Fehler",
                             f"{art.__name__}: {wert}", parent=fenster)
        kasten.setDetailedText(text)
        kasten.exec()

    sys.excepthook = haken


def main(argumente: list[str] | None = None) -> int:
    zerleger = argparse.ArgumentParser(description="Zweisprachiger Editor für die Handbücher.")
    zerleger.add_argument("--projekt", default=PROJEKT,
                          help="Projektverzeichnis (Vorgabe: dieses Repository)")
    gewaehlt = zerleger.parse_args(argumente)

    pfad = protokoll_einrichten()
    logging.getLogger(__name__).info("Start, Projekt %s", gewaehlt.projekt)

    anwendung = QApplication(sys.argv[:1])
    anwendung.setApplicationName("Handbuch-Editor")
    thema.merke_ausgangsstil()
    thema.anwenden(thema.gewaehlt())
    try:
        fenster = Hauptfenster(gewaehlt.projekt)
    except UnbekannterTyp as fehler:
        QMessageBox.critical(None, "Unbekannter Blocktyp",
                             f"Die deutsche Handbuchdatei enthält den Blocktyp «{fehler}», den "
                             f"weder Editor noch Generator kennen. Bearbeiten würde ihn "
                             f"verlieren – deshalb bleibt die Datei zu.")
        return 1
    except (OSError, ValueError) as fehler:
        QMessageBox.critical(None, "Öffnen misslungen", f"{fehler}\n\nEinzelheiten in {pfad}")
        logging.getLogger(__name__).exception("Öffnen misslungen")
        return 1

    fehlerhaken(fenster)
    fenster.show()
    return anwendung.exec()


if __name__ == "__main__":
    sys.exit(main())
