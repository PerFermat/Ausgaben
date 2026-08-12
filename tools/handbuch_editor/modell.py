# -*- coding: utf-8 -*-
"""Das sprachneutrale Datenmodell des Handbuchs.

Es gibt nur *einen* Baum von Blöcken; jeder Block trägt beide Sprachen. Ein Block, der nur in
einer Sprache existiert, lässt sich hier gar nicht erst bilden – das ist die tragende Zusicherung
des Editors. Die Aufteilung auf zwei JSON-Dateien passiert erst beim Schreiben.
"""
from __future__ import annotations

import dataclasses
import enum
import itertools
from typing import Iterator

SPRACHEN = ("de", "en")


# ------------------------------------------------------------------ Zweisprachiger Text
@dataclasses.dataclass
class Zweisprachig:
    """Ein Text in beiden Sprachen. Nie None – Fehlendes ist der leere String."""

    de: str = ""
    en: str = ""

    def hol(self, sprache: str) -> str:
        return self.de if sprache == "de" else self.en

    def setz(self, sprache: str, wert: str) -> None:
        if sprache == "de":
            self.de = wert
        else:
            self.en = wert

    def leer(self) -> bool:
        return not self.de.strip() and not self.en.strip()

    def kopie(self) -> "Zweisprachig":
        return Zweisprachig(self.de, self.en)


# ------------------------------------------------------------------ Blocktypen
class Typ(enum.Enum):
    """Blocktyp mit dem JSON-Kürzel und dem Namen, den der Redakteur sieht.

    Der Wert ist (kuerzel, klartext, vermehrbar). «vermehrbar» heißt: darf über «+ Block»
    eingefügt werden. Symboltabelle und Codebeispiel kennt der Generator nur einmal – ein zweites
    Vorkommen fiele im PDF stillschweigend unter den Tisch.
    """

    KAPITEL = ("h1", "Kapitelüberschrift", True)
    ABSCHNITT = ("h2", "Abschnittsüberschrift", True)
    ABSATZ = ("p", "Absatz", True)
    AUFZAEHLUNG = ("bullets", "Aufzählung", True)
    BILDER = ("shot_row", "Bilder", True)
    BILD_MIT_TEXT = ("text_with_single_shot", "Bild mit Text", True)
    BILDER_MIT_TEXT = ("text_with_shot_row", "Bilder mit Text", True)
    AUSSCHNITT_MIT_TEXT = ("text_with_pic", "Ausschnitt mit Text", True)
    SEITENUMBRUCH = ("pagebreak", "Seitenumbruch", True)
    SYMBOLTABELLE = ("symbols_table", "Symboltabelle", False)
    CODEBEISPIEL = ("code", "Codebeispiel", False)

    def __init__(self, kuerzel: str, klartext: str, vermehrbar: bool):
        self.kuerzel = kuerzel
        self.klartext = klartext
        self.vermehrbar = vermehrbar

    @property
    def hat_text(self) -> bool:
        return self in (Typ.KAPITEL, Typ.ABSCHNITT, Typ.ABSATZ)

    @property
    def hat_punkte(self) -> bool:
        return self is Typ.AUFZAEHLUNG

    @property
    def hat_kinder(self) -> bool:
        return self in (Typ.BILD_MIT_TEXT, Typ.BILDER_MIT_TEXT, Typ.AUSSCHNITT_MIT_TEXT)

    @property
    def hat_bilder(self) -> bool:
        return self in (Typ.BILDER, Typ.BILD_MIT_TEXT, Typ.BILDER_MIT_TEXT,
                        Typ.AUSSCHNITT_MIT_TEXT)

    @property
    def einzelbild(self) -> bool:
        """Genau ein Bild – Nachschieben weiterer Bilder ergäbe im PDF nichts."""
        return self in (Typ.BILD_MIT_TEXT, Typ.AUSSCHNITT_MIT_TEXT)

    @classmethod
    def aus_kuerzel(cls, kuerzel: str) -> "Typ":
        for typ in cls:
            if typ.kuerzel == kuerzel:
                return typ
        raise KeyError(kuerzel)

    @classmethod
    def bekannt(cls, kuerzel: str) -> bool:
        return any(typ.kuerzel == kuerzel for typ in cls)


# ------------------------------------------------------------------ Bild
@dataclasses.dataclass
class Bild:
    """Ein Bild. Dateiname und Breite gelten für beide Sprachen, die Unterschrift nicht.

    «name» ist der bloße Dateiname – die Datei selbst liegt sprachgetrennt unter
    screenshots/<lang>/. Dass der Name geteilt ist und der Pfad erst beim Schreiben entsteht, hält
    die beiden Sprachfassungen zwangsläufig beim selben Bild.

    «ausschnitt» sagt nichts über den Ort, sondern über die Art: ein beschnittener Ausschnitt wie
    das Export-Symbol steht im PDF in voller Breite unter dem Text, ein Bildschirmfoto daneben.

    Ältere Dateien führen bei Ausschnitten einen Pfad ab der Repo-Wurzel (docs/img/…). Solche
    Namen bleiben unangetastet stehen, damit ein alter Stand nicht stillschweigend kaputtgeht.
    """

    name: str = ""
    unterschrift: Zweisprachig = dataclasses.field(default_factory=Zweisprachig)
    ausschnitt: bool = False

    def kopie(self) -> "Bild":
        return Bild(self.name, self.unterschrift.kopie(), self.ausschnitt)

    def eigener_pfad(self) -> bool:
        """Trägt der Name schon ein Verzeichnis? Dann stammt er aus einem älteren Stand."""
        return "/" in self.name

    def pfad(self, sprache: str) -> str:
        """Der Pfad ab der Repo-Wurzel, so wie er in die JSON gehört."""
        if self.eigener_pfad():
            return self.name
        return f"screenshots/{sprache}/{self.name}"


# ------------------------------------------------------------------ Block
@dataclasses.dataclass
class Block:
    """Ein Baustein des Handbuchs – immer zweisprachig.

    Die Kennung wandert in beide JSON-Dateien. Nur an ihr erkennt der Abgleich später wieder,
    dass zwei Blöcke zusammengehören, auch wenn einer verschoben oder in einen Bildblock
    eingewickelt wurde.
    """

    kennung: str
    typ: Typ
    text: Zweisprachig = dataclasses.field(default_factory=Zweisprachig)
    punkte: list[Zweisprachig] = dataclasses.field(default_factory=list)
    bilder: list[Bild] = dataclasses.field(default_factory=list)
    #: Die Bildbreite in cm. Sie gehört an den Block, nicht an das einzelne Bild: auch eine Reihe
    #: hat nur eine – der Generator setzt alle Bilder einer Reihe gleich breit. None heißt Vorgabe.
    breite: float | None = None
    kinder: list["Block"] = dataclasses.field(default_factory=list)

    # -------------------------------------------------------------- Ansicht
    def ueberschrift(self) -> str:
        """Kurzfassung für Baum, Trefferliste und Prüfbericht."""
        if self.typ.hat_text:
            return self.text.de or self.text.en or "(ohne Text)"
        if self.typ.hat_punkte:
            erster = self.punkte[0].de if self.punkte else ""
            return erster or "(leere Aufzählung)"
        if self.typ.hat_bilder:
            return ", ".join(b.name for b in self.bilder) or "(ohne Bild)"
        return self.typ.klartext

    def durchlaufen(self) -> Iterator["Block"]:
        """Dieser Block und alle Kinder, in Dokumentreihenfolge."""
        yield self
        for kind in self.kinder:
            yield from kind.durchlaufen()

    def kopie(self) -> "Block":
        return Block(self.kennung, self.typ, self.text.kopie(),
                     [p.kopie() for p in self.punkte],
                     [b.kopie() for b in self.bilder],
                     self.breite,
                     [k.kopie() for k in self.kinder])

    #: Vorgabebreite in cm je Blocktyp, wenn in der Datei keine steht.
    VORGABE_BREITE = {"shot_row": 6.0, "text_with_shot_row": 6.0,
                      "text_with_single_shot": 6.0, "text_with_pic": 14.0}

    def breite_oder_vorgabe(self) -> float:
        if self.breite is not None:
            return self.breite
        return Block.VORGABE_BREITE.get(self.typ.kuerzel, 6.0)

    def leeren_text_setzen(self) -> None:
        """Wirft alles weg, was zum jetzigen Typ nicht passt (nach einem Typwechsel)."""
        if not self.typ.hat_text:
            self.text = Zweisprachig()
        if not self.typ.hat_punkte:
            self.punkte = []
        if not self.typ.hat_bilder:
            self.bilder = []
        if not self.typ.hat_kinder:
            self.kinder = []


# ------------------------------------------------------------------ Dokumentenkopf
#: Die freien Texte oben in der JSON, in genau dieser Reihenfolge.
KOPFTEXTE = ("doc_title", "doc_author", "version_text", "date_text", "toc_title",
             "footer_text", "page_text", "placeholder_no_image")
#: Zwei weitere Texte, die zwischen Tabellenkopf und Symbolen stehen.
KOPFTEXTE_SPAET = ("table_note", "code_example")

#: Beschriftungen für die Oberfläche.
KOPF_KLARTEXT = {
    "doc_title": "Titel",
    "doc_author": "Autor",
    "version_text": "Version",
    "date_text": "Stand",
    "toc_title": "Überschrift des Inhaltsverzeichnisses",
    "footer_text": "Fußzeile",
    "page_text": "Seitenzahl (%d wird ersetzt)",
    "placeholder_no_image": "Text im Platzhalter für fehlende Bilder",
    "table_note": "Hinweis unter der Symboltabelle",
    "code_example": "Codebeispiel",
}


@dataclasses.dataclass
class Symbolzeile:
    """Eine Zeile der Symboltabelle. Auch das Zeichen ist übersetzbar – «exportiert»/«exported»."""

    zeichen: Zweisprachig = dataclasses.field(default_factory=Zweisprachig)
    name: Zweisprachig = dataclasses.field(default_factory=Zweisprachig)
    bedeutung: Zweisprachig = dataclasses.field(default_factory=Zweisprachig)

    def kopie(self) -> "Symbolzeile":
        return Symbolzeile(self.zeichen.kopie(), self.name.kopie(), self.bedeutung.kopie())


@dataclasses.dataclass
class Kopf:
    """Alles, was neben «sections» in der JSON steht."""

    texte: dict[str, Zweisprachig] = dataclasses.field(default_factory=dict)
    tabellenkoepfe: list[Zweisprachig] = dataclasses.field(default_factory=list)
    symbole: list[Symbolzeile] = dataclasses.field(default_factory=list)

    def text(self, schluessel: str) -> Zweisprachig:
        return self.texte.setdefault(schluessel, Zweisprachig())


# ------------------------------------------------------------------ Handbuch
@dataclasses.dataclass
class Handbuch:
    """Das ganze Werk: Kopf und die flache Folge der Blöcke."""

    kopf: Kopf = dataclasses.field(default_factory=Kopf)
    bloecke: list[Block] = dataclasses.field(default_factory=list)

    def alle_bloecke(self) -> Iterator[Block]:
        for block in self.bloecke:
            yield from block.durchlaufen()

    def finde(self, kennung: str) -> Block | None:
        for block in self.alle_bloecke():
            if block.kennung == kennung:
                return block
        return None

    def elternliste(self, block: Block) -> list[Block]:
        """Die Liste, in der dieser Block steckt – oberste Ebene oder das «content» eines Bildblocks."""
        if block in self.bloecke:
            return self.bloecke
        for anderer in self.alle_bloecke():
            if block in anderer.kinder:
                return anderer.kinder
        raise ValueError(f"Block {block.kennung} gehört nicht zu diesem Handbuch")

    def neue_kennung(self) -> str:
        return Kennungen(self).naechste()


class Kennungen:
    """Vergibt fortlaufende Kennungen der Form b-0042, ohne je eine zu wiederholen."""

    def __init__(self, handbuch: Handbuch | None = None):
        self.vergeben: set[str] = set()
        if handbuch is not None:
            self.vergeben = {b.kennung for b in handbuch.alle_bloecke() if b.kennung}
        self._zaehler = itertools.count(1)

    def merken(self, kennung: str) -> None:
        self.vergeben.add(kennung)

    def naechste(self) -> str:
        for nummer in self._zaehler:
            kennung = f"b-{nummer:04d}"
            if kennung not in self.vergeben:
                self.vergeben.add(kennung)
                return kennung
        raise RuntimeError("unerreichbar")
