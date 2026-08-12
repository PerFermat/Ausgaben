# -*- coding: utf-8 -*-
"""Der Arbeitsstand als Sicherungskopie – außerhalb des Projekts.

Die Dateien unter docs/ rührt der Editor nur an, wenn jemand «Speichern» drückt. Alles andere
wäre unhöflich: das Projekt ist ein git-Arbeitsverzeichnis, und ein Diff, den man nicht selbst
ausgelöst hat, kostet mehr Zeit als er spart. Der laufende Stand liegt deshalb im Cache und wird
beim nächsten Start zur Wiederherstellung angeboten.
"""
from __future__ import annotations

import json
import logging
import os
import time

protokoll = logging.getLogger(__name__)

ORDNER = os.path.join(os.path.expanduser("~"), ".cache", "handbuch-editor")
ENTWURF = os.path.join(ORDNER, "entwurf.json")


def ablegen(stand: tuple[str, str], quelle: str) -> None:
    """Den Stand beider Sprachen wegschreiben. Fehler hier dürfen die Arbeit nicht stören."""
    try:
        os.makedirs(ORDNER, exist_ok=True)
        vorlaeufig = ENTWURF + ".neu"
        with open(vorlaeufig, "w", encoding="utf-8") as datei:
            json.dump({"zeit": time.time(), "quelle": quelle,
                       "de": stand[0], "en": stand[1]}, datei, ensure_ascii=False)
        os.replace(vorlaeufig, ENTWURF)          # erst umbenennen, wenn alles drinsteht
    except OSError:
        protokoll.exception("Sicherungskopie ließ sich nicht ablegen")


def holen(quelle: str) -> tuple[tuple[str, str], float] | None:
    """Ein liegengebliebener Entwurf zu diesem Projekt – oder None."""
    if not os.path.isfile(ENTWURF):
        return None
    try:
        with open(ENTWURF, encoding="utf-8") as datei:
            inhalt = json.load(datei)
    except (OSError, ValueError):
        protokoll.exception("Sicherungskopie ließ sich nicht lesen")
        return None
    if inhalt.get("quelle") != quelle:
        return None
    return ((inhalt["de"], inhalt["en"]), inhalt.get("zeit", 0.0))


def wegwerfen() -> None:
    """Nach dem Speichern oder dem Verwerfen – der Entwurf hat sich erledigt."""
    try:
        os.remove(ENTWURF)
    except OSError:
        pass


def alter(zeit: float) -> str:
    """«vor 3 Minuten» – für die Rückfrage beim Start."""
    verstrichen = max(0, time.time() - zeit)
    if verstrichen < 90:
        return "vor weniger als einer Minute"
    if verstrichen < 3600:
        return f"vor {round(verstrichen / 60)} Minuten"
    if verstrichen < 86400:
        return f"vor {round(verstrichen / 3600)} Stunden"
    return time.strftime("am %d.%m.%Y um %H:%M", time.localtime(zeit))
