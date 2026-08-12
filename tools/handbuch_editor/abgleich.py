# -*- coding: utf-8 -*-
"""Paart die deutschen und englischen Blöcke und hält fest, was dabei zurechtgerückt wurde.

Die deutsche Datei führt. Zu jedem deutschen Block wird der englische gesucht – erst über die
Kennung (die findet ihn auch, wenn er verschoben oder in einen Bildblock eingewickelt wurde),
dann über die Stelle unter den Geschwistern, zuletzt über die Bildnamen. Erst wenn er nirgends
auftaucht, entsteht ein leerer englischer Block. Ein englischer Block ohne deutsches Gegenstück
fällt weg.

Der Abgleich fasst nichts auf der Platte an – er arbeitet auf dem eingelesenen Stand.
"""
from __future__ import annotations

import dataclasses

from .modell import Bild, Block, Kennungen, Typ, Zweisprachig


class UnbekannterTyp(Exception):
    """Ein Blocktyp, den weder Editor noch Generator kennen. Öffnen wird abgelehnt."""


@dataclasses.dataclass
class Bericht:
    """Was der Abgleich getan hat – Zahlen für die Meldung, Zeilen für die Einzelheiten."""

    ergaenzt: list[str] = dataclasses.field(default_factory=list)
    entfernt: list[str] = dataclasses.field(default_factory=list)
    typ_berichtigt: list[str] = dataclasses.field(default_factory=list)
    bilder: list[str] = dataclasses.field(default_factory=list)

    def leer(self) -> bool:
        return not (self.ergaenzt or self.entfernt or self.typ_berichtigt or self.bilder)

    def zeilen(self) -> list[str]:
        """Die Zusammenfassung, wie sie im Dialog steht."""
        zusammen = []
        for anzahl, einzahl, mehrzahl in (
                (len(self.ergaenzt), "fehlender Block ergänzt", "fehlende Blöcke ergänzt"),
                (len(self.entfernt), "überzähliger Block entfernt", "überzählige Blöcke entfernt"),
                (len(self.typ_berichtigt), "Blocktyp berichtigt", "Blocktypen berichtigt"),
                (len(self.bilder), "Bild abgeglichen", "Bilder abgeglichen")):
            if anzahl:
                zusammen.append(f"{anzahl} {einzahl if anzahl == 1 else mehrzahl}")
        return zusammen


def _kurz(rohblock) -> str:
    """Eine Zeile, an der der Redakteur den Block wiedererkennt."""
    if rohblock.text:
        text = rohblock.text
    elif rohblock.punkte:
        text = rohblock.punkte[0]
    elif rohblock.bilder:
        text = ", ".join(b.name for b in rohblock.bilder)
    else:
        text = ""
    text = text.replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
    if len(text) > 70:
        text = text[:67] + "…"
    return f"{rohblock.kuerzel}: {text}" if text else rohblock.kuerzel


def verschmelze(roh_de: list, roh_en: list) -> tuple[list[Block], Bericht]:
    """Aus beiden Rohbaumseiten einen zweisprachigen Baum machen."""
    bericht = Bericht()
    kennungen = Kennungen()
    verbraucht: set[int] = set()

    alle_en = [b for wurzel in roh_en for b in wurzel.durchlaufen()]
    nach_kennung: dict[str, object] = {}
    for block in alle_en:
        if block.kennung and block.kennung not in nach_kennung:
            nach_kennung[block.kennung] = block
    nach_bild: dict[tuple, object] = {}
    for block in alle_en:
        signatur = tuple(bild.name for bild in block.bilder)
        if signatur and signatur not in nach_bild:
            nach_bild[signatur] = block

    def frei(kandidat) -> bool:
        return kandidat is not None and id(kandidat) not in verbraucht

    def partner(de_block, geschwister_en: list, stelle: int):
        if de_block.kennung:
            kandidat = nach_kennung.get(de_block.kennung)
            if frei(kandidat):
                return kandidat
        if stelle < len(geschwister_en):
            kandidat = geschwister_en[stelle]
            if frei(kandidat) and kandidat.kuerzel == de_block.kuerzel:
                return kandidat
        signatur = tuple(bild.name for bild in de_block.bilder)
        if signatur:
            kandidat = nach_bild.get(signatur)
            if frei(kandidat) and kandidat.kuerzel == de_block.kuerzel:
                return kandidat
        return None

    def bauen(liste_de: list, liste_en: list) -> list[Block]:
        gebaut = []
        for stelle, de_block in enumerate(liste_de):
            if not Typ.bekannt(de_block.kuerzel):
                raise UnbekannterTyp(de_block.kuerzel)
            en_block = partner(de_block, liste_en, stelle)
            if en_block is None:
                bericht.ergaenzt.append(_kurz(de_block))
            else:
                verbraucht.add(id(en_block))
                if en_block.kuerzel != de_block.kuerzel:
                    bericht.typ_berichtigt.append(_kurz(de_block))
            gebaut.append(_block(de_block, en_block, kennungen, bericht, bauen))
        return gebaut

    bloecke = bauen(roh_de, roh_en)

    for block in alle_en:
        if id(block) not in verbraucht:
            bericht.entfernt.append(_kurz(block))

    return bloecke, bericht


def _block(de_block, en_block, kennungen: Kennungen, bericht: Bericht, bauen) -> Block:
    """Einen deutschen und einen (womöglich fehlenden) englischen Rohblock zusammenlegen."""
    typ = Typ.aus_kuerzel(de_block.kuerzel)
    kennung = de_block.kennung or (en_block.kennung if en_block else None)
    if not kennung or kennung in kennungen.vergeben:
        kennung = kennungen.naechste()
    else:
        kennungen.merken(kennung)

    block = Block(kennung=kennung, typ=typ)

    if typ.hat_text:
        block.text = Zweisprachig(de_block.text, en_block.text if en_block else "")

    if typ.hat_punkte:
        punkte_en = en_block.punkte if en_block else []
        for stelle, punkt in enumerate(de_block.punkte):
            block.punkte.append(Zweisprachig(punkt, punkte_en[stelle] if stelle < len(punkte_en) else ""))

    if typ.hat_bilder:
        block.breite = de_block.breite
        bilder_en = en_block.bilder if en_block else []
        if en_block is not None and en_block.breite != de_block.breite:
            bericht.bilder.append(f"Breite {en_block.breite} → {de_block.breite}")
        for stelle, bild in enumerate(de_block.bilder):
            gegenstueck = bilder_en[stelle] if stelle < len(bilder_en) else None
            if gegenstueck is not None and gegenstueck.name != bild.name:
                bericht.bilder.append(f"{gegenstueck.name} → {bild.name}")
            block.bilder.append(Bild(
                name=bild.name,
                unterschrift=Zweisprachig(bild.unterschrift,
                                          gegenstueck.unterschrift if gegenstueck else ""),
                ausschnitt=bild.ausschnitt))

    if typ.hat_kinder:
        block.kinder = bauen(de_block.kinder, en_block.kinder if en_block else [])

    return block
