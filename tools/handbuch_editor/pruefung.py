# -*- coding: utf-8 -*-
"""Prüft ein Handbuch auf alles, was im PDF erst auffiele, wenn es zu spät ist.

Die Strukturgleichheit beider Sprachen steht durch das Datenmodell schon fest; hier geht es um
das, was ein Mensch übersehen kann: leere Übersetzungen, Bilder, die es nicht gibt, und offene
HTML-Auszeichnung – reportlab bricht bei einem nicht geschlossenen <b> mitten im Bau ab.
"""
from __future__ import annotations

import dataclasses
import os
import re

from .modell import Block, Handbuch, Typ

#: Auszeichnungen, die reportlab in den Texten versteht und die paarig stehen müssen.
MARKIERUNGEN = ("b", "i", "u", "font", "sub", "super", "br")

SPRACHNAME = {"de": "Deutsch", "en": "Englisch"}

#: Bilder, die im Handbuch nichts zu suchen haben und trotzdem im Ordner liegen dürfen: die
#: Titelseite zieht build_manual.py selbst heran, die Promo-Bilder gehören zum Store-Eintrag.
KEIN_HANDBUCHBILD = ("Handbuch Titelseite.png", "Promo-")


@dataclasses.dataclass
class Befund:
    """Ein einzelner Fund. «kennung» erlaubt der Oberfläche den Sprung zum Block."""

    schwere: str          # "fehler" oder "hinweis"
    text: str
    kennung: str | None = None

    def __str__(self) -> str:
        return f"{'Fehler' if self.schwere == 'fehler' else 'Hinweis'}: {self.text}"


def _offene_auszeichnung(text: str) -> str | None:
    """Gibt die erste Auszeichnung zurück, die nicht paarig steht."""
    offen: list[str] = []
    for treffer in re.finditer(r"<\s*(/?)\s*([a-zA-Z]+)[^>]*?(/?)\s*>", text):
        schluss, name, leer = treffer.group(1), treffer.group(2).lower(), treffer.group(3)
        if name not in MARKIERUNGEN or name == "br" or leer:
            continue
        if schluss:
            if not offen or offen[-1] != name:
                return name
            offen.pop()
        else:
            offen.append(name)
    return offen[-1] if offen else None


def _bildpfad(repo: str, bild, sprache: str) -> str:
    return os.path.join(repo, bild.pfad(sprache).replace("/", os.sep))


def _block_pruefen(block: Block, repo: str, befunde: list[Befund]) -> None:
    marke = block.ueberschrift()

    if block.typ.hat_text:
        for sprache in ("de", "en"):
            if not block.text.hol(sprache).strip():
                befunde.append(Befund("fehler",
                                      f"{block.typ.klartext} ohne Text ({SPRACHNAME[sprache]})",
                                      block.kennung))

    for nummer, punkt in enumerate(block.punkte, start=1):
        for sprache in ("de", "en"):
            if not punkt.hol(sprache).strip():
                befunde.append(Befund("fehler",
                                      f"Aufzählung «{marke}»: Punkt {nummer} ohne Text "
                                      f"({SPRACHNAME[sprache]})", block.kennung))

    for bild in block.bilder:
        if not bild.name:
            befunde.append(Befund("fehler", f"Bildblock «{marke}» ohne Bilddatei", block.kennung))
            continue
        for sprache in ("de", "en"):
            if not bild.unterschrift.hol(sprache).strip():
                befunde.append(Befund("fehler",
                                      f"«{bild.name}» ohne Bildunterschrift "
                                      f"({SPRACHNAME[sprache]})", block.kennung))
            if bild.eigener_pfad() and sprache == "en":
                continue          # ein Pfad aus einem älteren Stand gilt nur einmal
            if not os.path.isfile(_bildpfad(repo, bild, sprache)):
                befunde.append(Befund("fehler",
                                      f"Bilddatei fehlt: {_bildpfad('', bild, sprache).lstrip('/')}",
                                      block.kennung))

    if block.typ.hat_bilder and not block.bilder:
        befunde.append(Befund("fehler", f"{block.typ.klartext} ohne Bild", block.kennung))
    if block.typ.einzelbild and len(block.bilder) > 1:
        befunde.append(Befund("hinweis",
                              f"{block.typ.klartext} zeigt im PDF nur das erste von "
                              f"{len(block.bilder)} Bildern", block.kennung))

    for sprache in ("de", "en"):
        texte = [block.text.hol(sprache)] + [p.hol(sprache) for p in block.punkte]
        texte += [b.unterschrift.hol(sprache) for b in block.bilder]
        for text in texte:
            name = _offene_auszeichnung(text)
            if name:
                befunde.append(Befund("fehler",
                                      f"«{marke}»: <{name}> nicht geschlossen "
                                      f"({SPRACHNAME[sprache]})", block.kennung))
                break


def _kopf_pruefen(handbuch: Handbuch, befunde: list[Befund]) -> None:
    for schluessel, wert in handbuch.kopf.texte.items():
        for sprache in ("de", "en"):
            if not wert.hol(sprache).strip():
                befunde.append(Befund("fehler",
                                      f"Dokumentenkopf «{schluessel}» ohne Text "
                                      f"({SPRACHNAME[sprache]})"))
    for nummer, zeile in enumerate(handbuch.kopf.symbole, start=1):
        for feldname, feld in (("Zeichen", zeile.zeichen), ("Name", zeile.name),
                               ("Bedeutung", zeile.bedeutung)):
            for sprache in ("de", "en"):
                if not feld.hol(sprache).strip():
                    befunde.append(Befund("fehler",
                                          f"Symboltabelle, Zeile {nummer}: {feldname} leer "
                                          f"({SPRACHNAME[sprache]})"))


def _bestand_pruefen(handbuch: Handbuch, repo: str, befunde: list[Befund]) -> None:
    """Bilddateien, die es gibt, aber niemand benutzt."""
    benutzt = {bild.name for block in handbuch.alle_bloecke() for bild in block.bilder
               if not bild.eigener_pfad()}
    for sprache in ("de", "en"):
        ordner = os.path.join(repo, "screenshots", sprache)
        if not os.path.isdir(ordner):
            continue
        for datei in sorted(os.listdir(ordner)):
            if datei.startswith(KEIN_HANDBUCHBILD) or datei in KEIN_HANDBUCHBILD:
                continue
            if datei.lower().endswith((".png", ".jpg", ".jpeg")) and datei not in benutzt:
                befunde.append(Befund("hinweis",
                                      f"screenshots/{sprache}/{datei} kommt im Handbuch nicht vor"))


def pruefe(handbuch: Handbuch, repo: str) -> list[Befund]:
    """Alle Prüfungen; Fehler zuerst, danach die Hinweise."""
    befunde: list[Befund] = []
    _kopf_pruefen(handbuch, befunde)

    gesehen: set[str] = set()
    for block in handbuch.alle_bloecke():
        if block.kennung in gesehen:
            befunde.append(Befund("fehler", f"Kennung {block.kennung} kommt mehrfach vor",
                                  block.kennung))
        gesehen.add(block.kennung)
        _block_pruefen(block, repo, befunde)

    for typ in (Typ.SYMBOLTABELLE, Typ.CODEBEISPIEL):
        anzahl = sum(1 for b in handbuch.alle_bloecke() if b.typ is typ)
        if anzahl > 1:
            befunde.append(Befund("fehler", f"{typ.klartext} kommt {anzahl}-mal vor; der "
                                            f"Generator kennt sie nur einmal"))

    _bestand_pruefen(handbuch, repo, befunde)
    return sorted(befunde, key=lambda b: 0 if b.schwere == "fehler" else 1)
