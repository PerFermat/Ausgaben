# -*- coding: utf-8 -*-
"""Liest die beiden JSON-Dateien ein und macht daraus ein zweisprachiges Handbuch.

Hier steht nur, wie die JSON aussieht. Wer zu wem gehört, entscheidet abgleich.py.
"""
from __future__ import annotations

import dataclasses
import json
import os

from . import abgleich
from .modell import Handbuch, Kopf, KOPFTEXTE, KOPFTEXTE_SPAET, Symbolzeile, Zweisprachig


@dataclasses.dataclass
class Rohbild:
    name: str = ""
    unterschrift: str = ""
    ausschnitt: bool = False


@dataclasses.dataclass
class Rohblock:
    """Ein Block einer einzelnen Sprachdatei, noch ungepaart."""

    kuerzel: str
    kennung: str | None = None
    text: str = ""
    punkte: list[str] = dataclasses.field(default_factory=list)
    bilder: list[Rohbild] = dataclasses.field(default_factory=list)
    breite: float | None = None
    kinder: list["Rohblock"] = dataclasses.field(default_factory=list)
    pfad: tuple[int, ...] = ()

    def durchlaufen(self):
        yield self
        for kind in self.kinder:
            yield from kind.durchlaufen()


def _rohbild(eintrag: dict, ausschnitt: bool) -> Rohbild:
    name = eintrag.get("relpath" if ausschnitt else "fname", "")
    if ausschnitt and name.startswith("screenshots/"):
        name = name.rsplit("/", 1)[-1]      # der Sprachordner steckt im Pfad, nicht im Namen
    return Rohbild(name=name, unterschrift=eintrag.get("caption", ""), ausschnitt=ausschnitt)


def _rohblock(eintrag: dict, pfad: tuple[int, ...]) -> Rohblock:
    kuerzel = eintrag.get("type", "")
    block = Rohblock(kuerzel=kuerzel, kennung=eintrag.get("id"), pfad=pfad)
    block.text = eintrag.get("text", "")
    block.punkte = list(eintrag.get("items", []))
    # Die Breite steht je nach Blockart im Bild oder am Block – im Modell immer am Block.
    if "shot" in eintrag:
        block.bilder = [_rohbild(eintrag["shot"], False)]
        block.breite = eintrag["shot"].get("width")
    if "pic" in eintrag:
        block.bilder = [_rohbild(eintrag["pic"], True)]
        block.breite = eintrag["pic"].get("width")
    if "shots" in eintrag:
        block.bilder = [_rohbild(s, False) for s in eintrag["shots"]]
        block.breite = eintrag.get("width")
    for nummer, kind in enumerate(eintrag.get("content", [])):
        block.kinder.append(_rohblock(kind, pfad + (nummer,)))
    return block


def rohbloecke(daten: dict) -> list[Rohblock]:
    return [_rohblock(e, (nummer,)) for nummer, e in enumerate(daten.get("sections", []))]


def _kopf(de: dict, en: dict) -> Kopf:
    kopf = Kopf()
    for schluessel in KOPFTEXTE + KOPFTEXTE_SPAET:
        kopf.texte[schluessel] = Zweisprachig(de.get(schluessel, ""), en.get(schluessel, ""))
    koepfe_de = de.get("table_headers", [])
    koepfe_en = en.get("table_headers", [])
    for nummer in range(max(len(koepfe_de), len(koepfe_en))):
        kopf.tabellenkoepfe.append(Zweisprachig(
            koepfe_de[nummer] if nummer < len(koepfe_de) else "",
            koepfe_en[nummer] if nummer < len(koepfe_en) else ""))
    zeilen_de = de.get("symbols", [])
    zeilen_en = en.get("symbols", [])
    for nummer in range(max(len(zeilen_de), len(zeilen_en))):
        links = zeilen_de[nummer] if nummer < len(zeilen_de) else ["", "", ""]
        rechts = zeilen_en[nummer] if nummer < len(zeilen_en) else ["", "", ""]
        links = list(links) + [""] * (3 - len(links))
        rechts = list(rechts) + [""] * (3 - len(rechts))
        kopf.symbole.append(Symbolzeile(Zweisprachig(links[0], rechts[0]),
                                        Zweisprachig(links[1], rechts[1]),
                                        Zweisprachig(links[2], rechts[2])))
    return kopf


def aus_daten(de: dict, en: dict) -> tuple[Handbuch, abgleich.Bericht]:
    """Aus zwei eingelesenen JSON-Bäumen ein Handbuch samt Abgleichsbericht bauen."""
    handbuch = Handbuch(kopf=_kopf(de, en))
    handbuch.bloecke, bericht = abgleich.verschmelze(rohbloecke(de), rohbloecke(en))
    return handbuch, bericht


def lade(pfad_de: str, pfad_en: str) -> tuple[Handbuch, abgleich.Bericht]:
    """Beide Dateien einlesen, paaren, ein Handbuch samt Abgleichsbericht zurückgeben."""
    with open(pfad_de, encoding="utf-8") as datei:
        de = json.load(datei)
    with open(pfad_en, encoding="utf-8") as datei:
        en = json.load(datei)
    return aus_daten(de, en)


def pfade(repo: str) -> tuple[str, str]:
    """Die beiden Handbuchdateien im Projekt."""
    return (os.path.join(repo, "docs", "handbuch_de.json"),
            os.path.join(repo, "docs", "handbuch_en.json"))
