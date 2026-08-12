# -*- coding: utf-8 -*-
"""Schreibt eine Sprachfassung des Handbuchs zurück in ihre JSON-Datei.

Nicht json.dump: die vorhandenen Dateien haben einen gewachsenen Stil – Bilddefinitionen und
Symbolzeilen stehen einzeilig, alles andere ausgerückt. Würde der Editor sie umformatieren, wäre
jeder git-Diff eine Wand aus Rauschen und die echten Änderungen darin nicht mehr zu finden.
"""
from __future__ import annotations

import json

from .modell import Block, Handbuch, KOPFTEXTE, KOPFTEXTE_SPAET, Typ


def _w(wert) -> str:
    """Ein einzelner JSON-Wert, so wie ihn die vorhandenen Dateien schreiben."""
    return json.dumps(wert, ensure_ascii=False)


def _reihe(werte: list) -> str:
    return "[" + ", ".join(_w(wert) for wert in werte) + "]"


def _objekt(paare: list[tuple[str, object]]) -> str:
    return "{" + ", ".join(f"{_w(name)}: {_w(wert)}" for name, wert in paare) + "}"


def _bild_paare(bild, sprache: str, breite: float | None) -> list[tuple[str, object]]:
    paare: list[tuple[str, object]] = [
        ("relpath", bild.pfad(sprache)) if bild.ausschnitt else ("fname", bild.name),
        ("caption", bild.unterschrift.hol(sprache)),
    ]
    if breite is not None:
        paare.append(("width", breite))
    return paare


def _mit_kommas(teile: list[list[str]]) -> list[str]:
    """Mehrere mehrzeilige Einträge zu einer Folge verbinden – Komma hinter jedem außer dem letzten."""
    zeilen: list[str] = []
    for nummer, teil in enumerate(teile):
        if nummer != len(teile) - 1:
            teil = teil[:-1] + [teil[-1] + ","]
        zeilen.extend(teil)
    return zeilen


def _blockzeilen(block: Block, sprache: str, einzug: int) -> list[str]:
    """Die Zeilen eines Blockobjekts, ohne abschließendes Komma."""
    e = " " * einzug
    ei = " " * (einzug + 2)
    eintraege: list[list[str]] = [
        [f'{ei}"type": {_w(block.typ.kuerzel)}'],
        [f'{ei}"id": {_w(block.kennung)}'],
    ]

    if block.typ.hat_text:
        eintraege.append([f'{ei}"text": {_w(block.text.hol(sprache))}'])
    if block.typ.hat_punkte:
        inhalt = [[f'{ei}  {_w(punkt.hol(sprache))}'] for punkt in block.punkte]
        eintraege.append([ei + '"items": ['] + _mit_kommas(inhalt) + [ei + "]"])
    if block.typ.hat_kinder:
        inhalt = [_blockzeilen(kind, sprache, einzug + 4) for kind in block.kinder]
        eintraege.append([ei + '"content": ['] + _mit_kommas(inhalt) + [ei + "]"])
    if block.typ is Typ.BILD_MIT_TEXT and block.bilder:
        paare = _bild_paare(block.bilder[0], sprache, block.breite)
        eintraege.append([f'{ei}"shot": {_objekt(paare)}'])
    elif block.typ is Typ.AUSSCHNITT_MIT_TEXT and block.bilder:
        paare = _bild_paare(block.bilder[0], sprache, block.breite)
        eintraege.append([f'{ei}"pic": {_objekt(paare)}'])
    elif block.typ in (Typ.BILDER, Typ.BILDER_MIT_TEXT):
        # Bei einer Reihe gilt die Breite für alle Bilder – sie steht deshalb am Block.
        inhalt = [[f"{ei}  {_objekt(_bild_paare(bild, sprache, None))}"] for bild in block.bilder]
        eintraege.append([ei + '"shots": ['] + _mit_kommas(inhalt) + [ei + "]"])
        if block.breite is not None:
            eintraege.append([f'{ei}"width": {_w(block.breite)}'])

    return [e + "{"] + _mit_kommas(eintraege) + [e + "}"]


def als_text_block(block: Block, sprache: str) -> str:
    """Ein einzelner Block als JSON – für die Vorschau und die Aufteilungsrechnung."""
    return "\n".join(_blockzeilen(block, sprache, 0))


def als_text(handbuch: Handbuch, sprache: str) -> str:
    """Die vollständige JSON-Datei einer Sprache als Text."""
    zeilen = ["{"]
    for schluessel in KOPFTEXTE:
        zeilen.append(f'  {_w(schluessel)}: {_w(handbuch.kopf.text(schluessel).hol(sprache))},')
    zeilen.append('  "table_headers": '
                  + _reihe([k.hol(sprache) for k in handbuch.kopf.tabellenkoepfe]) + ",")
    for schluessel in KOPFTEXTE_SPAET:
        zeilen.append(f'  {_w(schluessel)}: {_w(handbuch.kopf.text(schluessel).hol(sprache))},')

    zeilen.append('  "symbols": [')
    zeilen.extend(_mit_kommas([["    " + _reihe([zeile.zeichen.hol(sprache),
                                                 zeile.name.hol(sprache),
                                                 zeile.bedeutung.hol(sprache)])]
                               for zeile in handbuch.kopf.symbole]))
    zeilen.append("  ],")

    zeilen.append('  "sections": [')
    zeilen.extend(_mit_kommas([_blockzeilen(block, sprache, 4)
                               for block in handbuch.bloecke]))
    zeilen.append("  ]")
    zeilen.append("}")
    return "\n".join(zeilen) + "\n"


def speichere(handbuch: Handbuch, pfad_de: str, pfad_en: str) -> None:
    """Beide Dateien schreiben – erst beide erzeugen, dann ablegen.

    So bleibt kein halb geschriebenes Paar zurück, wenn beim Erzeugen etwas schiefgeht.
    """
    inhalte = {pfad_de: als_text(handbuch, "de"), pfad_en: als_text(handbuch, "en")}
    for pfad, inhalt in inhalte.items():
        with open(pfad, "w", encoding="utf-8") as datei:
            datei.write(inhalt)
