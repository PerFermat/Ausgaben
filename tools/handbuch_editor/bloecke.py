# -*- coding: utf-8 -*-
"""Die Blockkarten: je ein Baustein des Handbuchs mit beiden Sprachen nebeneinander.

Was beide Sprachen teilen – Blocktyp, Bilddatei, Breite – steht oben in der Karte. Was sich
unterscheidet, steht darunter, je nach Einstellung nebeneinander oder in zwei Reitern. Anders
herum ginge es auch, aber dann müsste der Redakteur jede Bildzuordnung zweimal machen und könnte
sie zweimal verschieden machen.
"""
from __future__ import annotations

from PySide6.QtCore import QMimeData, Qt
from PySide6.QtGui import QColor, QDrag, QFont, QPainter, QPalette, QPen
from PySide6.QtWidgets import (QDoubleSpinBox, QFrame, QHBoxLayout, QLabel, QLineEdit, QMenu,
                               QPlainTextEdit, QPushButton, QSizePolicy, QTabWidget, QToolButton,
                               QVBoxLayout, QWidget)

from . import thema, vorschau
from .bilder import Bildauswahl
from .modell import Bild, Block, Typ, Zweisprachig

MIME = "application/x-handbuch-block"

#: Luft zwischen den Blockkarten – dort wird beim Ziehen die Einfügemarke gezeichnet.
ABSTAND = 10
SPRACHEN = (("de", "Deutsch"), ("en", "English"))

#: Wie tief eine Blockkarte getönt wird. Der Wert ist die Beimischung zur Fensterfarbe in
#: Prozent – so bleibt die Staffelung in hellem wie in dunklem Theme sichtbar.
TOENUNG = {
    "kapitel": 26,          # Kapitelüberschrift: die kräftigste Marke im Kapitel
    "abschnitt": 14,        # Abschnittsüberschrift
    "neben": 8,             # steht im PDF neben dem Bild
    "geteilt": 14,
    "darunter": 20,         # rutscht im PDF unter das Bild
    "": 0,
}

LAGE_TEXT = {
    "neben": "steht neben dem Bild",
    "geteilt": "beginnt neben dem Bild und läuft darunter weiter",
    "darunter": "rutscht unter das Bild",
}


def toenung(widget: QWidget, stufe: str) -> str:
    """Die Hintergrundfarbe einer Blockkarte, aus der Palette gemischt.

    Gerechnet statt fest gewählt: im hellen Theme wird abgedunkelt, im dunklen aufgehellt – die
    Staffelung bleibt in beiden gleich gut lesbar.
    """
    anteil = TOENUNG.get(stufe, 0)
    if not anteil:
        return ""
    palette = widget.palette()
    grund = palette.color(QPalette.Window)
    ziel = palette.color(QPalette.Text)
    gemischt = QColor(
        round(grund.red() + (ziel.red() - grund.red()) * anteil / 100),
        round(grund.green() + (ziel.green() - grund.green()) * anteil / 100),
        round(grund.blue() + (ziel.blue() - grund.blue()) * anteil / 100))
    return f"#blockkarte {{ background-color: {gemischt.name()}; }}"


# ------------------------------------------------------------------ kleine Bausteine
class Textfeld(QPlainTextEdit):
    """Mehrzeiliges Feld, das seine Änderung erst beim Verlassen ins Modell schreibt.

    Pro Tastendruck einen Undo-Schritt zu erzeugen wäre unbenutzbar; ein Schritt je bearbeitetem
    Feld trifft das, was ein Mensch rückgängig machen will.
    """

    def __init__(self, wert: str, uebernehmen, fenster, beschreibung: str, zeilen: int = 4):
        super().__init__(wert)
        self._uebernehmen = uebernehmen
        self._fenster = fenster
        self._beschreibung = beschreibung
        self._stand = wert
        self.setTabChangesFocus(True)
        rand = self.fontMetrics().lineSpacing()
        self.setMinimumHeight(rand * zeilen + 12)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Minimum)

    def focusInEvent(self, ereignis):
        self._vorher = self._fenster.zustand()
        super().focusInEvent(ereignis)

    def focusOutEvent(self, ereignis):
        super().focusOutEvent(ereignis)
        neu = self.toPlainText()
        if neu != self._stand:
            self._stand = neu
            self._uebernehmen(neu)
            self._fenster.aenderung(self._beschreibung, getattr(self, "_vorher", None))


class Zeilenfeld(QLineEdit):
    """Einzeiliges Gegenstück zu Textfeld – für Überschriften und Bildunterschriften."""

    def __init__(self, wert: str, uebernehmen, fenster, beschreibung: str):
        super().__init__(wert)
        self._uebernehmen = uebernehmen
        self._fenster = fenster
        self._beschreibung = beschreibung
        self._stand = wert

    def focusInEvent(self, ereignis):
        self._vorher = self._fenster.zustand()
        super().focusInEvent(ereignis)

    def focusOutEvent(self, ereignis):
        super().focusOutEvent(ereignis)
        if self.text() != self._stand:
            self._stand = self.text()
            self._uebernehmen(self._stand)
            self._fenster.aenderung(self._beschreibung, getattr(self, "_vorher", None))


def beschriftet(text: str, widget: QWidget) -> QWidget:
    huelle = QWidget()
    aufbau = QVBoxLayout(huelle)
    aufbau.setContentsMargins(0, 0, 0, 0)
    aufbau.setSpacing(2)
    marke = QLabel(text)
    marke.setStyleSheet("color: palette(mid);")
    aufbau.addWidget(marke)
    aufbau.addWidget(widget)
    return huelle


def sprachpaar(felder, beschriftung: str = "") -> QWidget:
    """Zwei Sprachfassungen desselben Textes – nebeneinander oder in Reitern.

    Die eine Stelle, an der die Anordnung entschieden wird. Untereinander gibt es bewusst nicht:
    zwei Fassungen desselben Satzes vergleicht man nebeneinander oder gar nicht.

    felder – die beiden fertigen Eingabefelder in der Reihenfolge von SPRACHEN.
    """
    if thema.sprachen_nebeneinander():
        huelle = QWidget()
        aufbau = QHBoxLayout(huelle)
        aufbau.setContentsMargins(0, 0, 0, 0)
        for (_sprache, name), feld in zip(SPRACHEN, felder):
            aufbau.addWidget(beschriftet(f"{beschriftung} {name}".strip(), feld), 1)
        return huelle

    reiter = QTabWidget()
    for (_sprache, name), feld in zip(SPRACHEN, felder):
        reiter.addTab(feld, name)
    if beschriftung:
        return beschriftet(beschriftung, reiter)
    return reiter


# ------------------------------------------------------------------ Aufzählung
class Punkteliste(QWidget):
    """Listeneditor für eine Aufzählung – je Punkt beide Sprachen, in der gewählten Anordnung."""

    def __init__(self, block: Block, fenster):
        super().__init__()
        self.block = block
        self.fenster = fenster
        self.aufbau = QVBoxLayout(self)
        self.aufbau.setContentsMargins(0, 0, 0, 0)
        self._zeichnen()

    def _zeichnen(self) -> None:
        while self.aufbau.count():
            teil = self.aufbau.takeAt(0)
            if teil.widget():
                teil.widget().deleteLater()

        for nummer, punkt in enumerate(self.block.punkte):
            self.aufbau.addWidget(self._punkt(nummer, punkt))

        knopf = QPushButton("+ Punkt")
        knopf.clicked.connect(self._anhaengen)
        self.aufbau.addWidget(knopf, alignment=Qt.AlignLeft)

    def _punkt(self, nummer: int, punkt: Zweisprachig) -> QWidget:
        rahmen = QFrame()
        rahmen.setFrameShape(QFrame.StyledPanel)
        aussen = QHBoxLayout(rahmen)

        felder = [Textfeld(punkt.hol(sprache),
                           lambda wert, s=sprache, p=punkt: p.setz(s, wert),
                           self.fenster, "Aufzählungspunkt geändert", zeilen=2)
                  for sprache, _name in SPRACHEN]
        aussen.addWidget(sprachpaar(felder), 1)

        knoepfe = QVBoxLayout()
        for zeichen, hinweis, tat in (("▲", "nach oben", lambda: self._schieben(nummer, -1)),
                                      ("▼", "nach unten", lambda: self._schieben(nummer, +1)),
                                      ("🗑", "löschen", lambda: self._loeschen(nummer))):
            knopf = QToolButton()
            knopf.setText(zeichen)
            knopf.setToolTip(hinweis)
            knopf.clicked.connect(tat)
            knoepfe.addWidget(knopf)
        knoepfe.addStretch(1)
        aussen.addLayout(knoepfe)
        return rahmen

    def _anhaengen(self) -> None:
        vorher = self.fenster.zustand()
        self.block.punkte.append(Zweisprachig())
        self.fenster.aenderung("Punkt hinzugefügt", vorher)
        self._zeichnen()

    def _schieben(self, nummer: int, richtung: int) -> None:
        ziel = nummer + richtung
        if not 0 <= ziel < len(self.block.punkte):
            return
        vorher = self.fenster.zustand()
        punkte = self.block.punkte
        punkte[nummer], punkte[ziel] = punkte[ziel], punkte[nummer]
        self.fenster.aenderung("Punkt verschoben", vorher)
        self._zeichnen()

    def _loeschen(self, nummer: int) -> None:
        vorher = self.fenster.zustand()
        del self.block.punkte[nummer]
        self.fenster.aenderung("Punkt gelöscht", vorher)
        self._zeichnen()


# ------------------------------------------------------------------ Bilder
class Bildzeile(QWidget):
    """Ein Bild: Dateiname geteilt, Unterschrift je Sprache. Die Breite steht am Block."""

    def __init__(self, block: Block, bild: Bild, fenster, mit_knoepfen: bool):
        super().__init__()
        self.block, self.bild, self.fenster = block, bild, fenster
        rahmen = QVBoxLayout(self)
        rahmen.setContentsMargins(0, 0, 0, 0)

        kopf = QHBoxLayout()
        self.name = QLabel(bild.name or "(kein Bild)")
        self.name.setFont(QFont("monospace"))
        kopf.addWidget(self.name, 1)

        waehlen = QPushButton("Bild auswählen …")
        waehlen.clicked.connect(self._waehlen)
        kopf.addWidget(waehlen)

        if mit_knoepfen:
            for zeichen, hinweis, tat in (("▲", "nach oben", lambda: self._schieben(-1)),
                                          ("▼", "nach unten", lambda: self._schieben(+1)),
                                          ("🗑", "entfernen", self._loeschen)):
                knopf = QToolButton()
                knopf.setText(zeichen)
                knopf.setToolTip(hinweis)
                knopf.clicked.connect(tat)
                kopf.addWidget(knopf)
        rahmen.addLayout(kopf)

        felder = [Zeilenfeld(bild.unterschrift.hol(sprache),
                             lambda wert, s=sprache: bild.unterschrift.setz(s, wert),
                             fenster, "Bildunterschrift geändert")
                  for sprache, _name in SPRACHEN]
        rahmen.addWidget(sprachpaar(felder, "Bildunterschrift"))

    def _waehlen(self) -> None:
        benutzt = {b.name for block in self.fenster.handbuch.alle_bloecke() for b in block.bilder}
        dialog = Bildauswahl(self, self.fenster.repo, self.bild.name, benutzt)
        if dialog.exec() and dialog.gewaehlt():
            vorher = self.fenster.zustand()
            self.bild.name = dialog.gewaehlt()
            self.fenster.aenderung("Bild gewechselt", vorher)
            self.name.setText(self.bild.name)

    def _schieben(self, richtung: int) -> None:
        stelle = self.block.bilder.index(self.bild)
        ziel = stelle + richtung
        if not 0 <= ziel < len(self.block.bilder):
            return
        vorher = self.fenster.zustand()
        bilder = self.block.bilder
        bilder[stelle], bilder[ziel] = bilder[ziel], bilder[stelle]
        self.fenster.aenderung("Bild verschoben", vorher)
        self.fenster.neu_zeichnen()

    def _loeschen(self) -> None:
        vorher = self.fenster.zustand()
        self.block.bilder.remove(self.bild)
        self.fenster.aenderung("Bild entfernt", vorher)
        self.fenster.neu_zeichnen()


# ------------------------------------------------------------------ Blockkarte
class Blockkarte(QFrame):
    """Ein Block, so wie der Redakteur ihn sieht."""

    def __init__(self, block: Block, liste: "Blockliste", fenster, lage: str = ""):
        super().__init__()
        self.block, self.liste, self.fenster = block, liste, fenster
        self.lage = lage
        self.setFrameShape(QFrame.StyledPanel)
        self.setObjectName("blockkarte")
        stufe = lage or {Typ.KAPITEL: "kapitel", Typ.ABSCHNITT: "abschnitt"}.get(block.typ, "")
        self.setStyleSheet(toenung(self, stufe))
        aufbau = QVBoxLayout(self)

        aufbau.addLayout(self._kopfzeile())

        if block.typ.hat_bilder:
            aufbau.addLayout(self._breitenzeile())
            for bild in block.bilder:
                aufbau.addWidget(Bildzeile(block, bild, fenster,
                                           mit_knoepfen=not block.typ.einzelbild))
            if not block.typ.einzelbild:
                knopf = QPushButton("+ Bild")
                knopf.clicked.connect(self._bild_anhaengen)
                aufbau.addWidget(knopf, alignment=Qt.AlignLeft)

        if block.typ.hat_text:
            felder = [Textfeld(block.text.hol(sprache),
                               lambda wert, s=sprache: block.text.setz(s, wert),
                               fenster, f"{block.typ.klartext} geändert",
                               zeilen=2 if block.typ is not Typ.ABSATZ else 5)
                      for sprache, _name in SPRACHEN]
            aufbau.addWidget(sprachpaar(felder))

        if block.typ.hat_punkte:
            aufbau.addWidget(Punkteliste(block, fenster))

        self.unterliste: Blockliste | None = None
        if block.typ.hat_kinder:
            if block.typ is Typ.BILD_MIT_TEXT:
                # Nur hier fließt Text seitlich am Bild vorbei; bei den anderen steht er darüber.
                lagen = vorschau.aufteilung(fenster.repo, block)
                titel = "Text am Bild – die Tönung zeigt, was neben das Bild passt"
            else:
                lagen = None
                titel = "Text über dem Bild"
            self.unterliste = Blockliste(fenster, block.kinder, None, titel=titel, lagen=lagen)
            aufbau.addWidget(self.unterliste)

        if block.typ is Typ.SEITENUMBRUCH:
            strich = QFrame()
            strich.setFrameShape(QFrame.HLine)
            aufbau.addWidget(strich)
            aufbau.addWidget(self._verweis("Ab hier geht es im PDF auf einer neuen Seite weiter."))

        if block.typ is Typ.SYMBOLTABELLE:
            aufbau.addWidget(self._verweis("Die Symboltabelle selbst wird im Dokumentenkopf "
                                           "bearbeitet."))
        if block.typ is Typ.CODEBEISPIEL:
            aufbau.addWidget(self._verweis("Der Text des Codebeispiels steht im Dokumentenkopf."))

    def _breitenzeile(self) -> QHBoxLayout:
        """Eine Breite für den ganzen Block – bei einer Reihe gilt sie für jedes Bild darin."""
        zeile = QHBoxLayout()
        mehrere = len(self.block.bilder) > 1 or not self.block.typ.einzelbild
        zeile.addWidget(QLabel("Breite je Bild:" if mehrere else "Breite:"))

        self.breite = QDoubleSpinBox()
        self.breite.setRange(1.0, 17.0)
        self.breite.setSingleStep(0.5)
        self.breite.setSuffix(" cm")
        self.breite.setValue(self.block.breite_oder_vorgabe())
        self.breite.editingFinished.connect(self._breite_uebernehmen)
        zeile.addWidget(self.breite)

        if mehrere:
            hinweis = QLabel("– mehr als nebeneinander passt, verkleinert der Satz von allein")
            hinweis.setStyleSheet("color: palette(mid);")
            zeile.addWidget(hinweis)
        zeile.addStretch(1)
        return zeile

    def _breite_uebernehmen(self) -> None:
        neu = self.breite.value()
        if neu == self.block.breite:
            return
        vorher = self.fenster.zustand()
        self.block.breite = neu
        self.fenster.aenderung("Bildbreite geändert", vorher)

    def lage_setzen(self, lage: str) -> None:
        """Tönung und Vermerk auffrischen, ohne die Karte neu zu bauen."""
        if lage == self.lage:
            return
        self.lage = lage
        self.setStyleSheet(toenung(self, lage))
        self.vermerk.setText("· " + LAGE_TEXT.get(lage, ""))
        self.vermerk.setVisible(lage in LAGE_TEXT)

    def lagen_auffrischen(self) -> None:
        """Nach einer Textänderung neu rechnen, wo der Text im PDF landet."""
        if self.block.typ is Typ.BILD_MIT_TEXT and self.unterliste is not None:
            self.unterliste.lagen_setzen(vorschau.aufteilung(self.fenster.repo, self.block))

    def _verweis(self, text: str) -> QLabel:
        marke = QLabel(text)
        marke.setWordWrap(True)
        marke.setStyleSheet("color: palette(mid);")
        return marke

    def _kopfzeile(self) -> QHBoxLayout:
        zeile = QHBoxLayout()
        griff = QLabel("⠿")
        griff.setToolTip("Ziehen zum Verschieben")
        griff.setCursor(Qt.OpenHandCursor)
        griff.mousePressEvent = self._ziehen_starten
        zeile.addWidget(griff)

        name = QLabel(self.block.typ.klartext)
        name.setStyleSheet("font-weight: bold;")
        zeile.addWidget(name)

        self.vermerk = QLabel("· " + LAGE_TEXT.get(self.lage, ""))
        self.vermerk.setStyleSheet("color: palette(mid); font-style: italic;")
        self.vermerk.setToolTip("Gerechnet mit derselben Schätzung, nach der build_manual.py "
                                "den Text neben das Bild setzt.")
        self.vermerk.setVisible(self.lage in LAGE_TEXT)
        zeile.addWidget(self.vermerk)
        zeile.addStretch(1)

        for zeichen, hinweis, tat in (("▲", "nach oben", lambda: self.liste.schieben(self.block, -1)),
                                      ("▼", "nach unten", lambda: self.liste.schieben(self.block, +1)),
                                      ("🗑", "Block löschen", lambda: self.liste.loeschen(self.block))):
            knopf = QToolButton()
            knopf.setText(zeichen)
            knopf.setToolTip(hinweis)
            knopf.clicked.connect(tat)
            zeile.addWidget(knopf)
        return zeile

    def _ziehen_starten(self, ereignis) -> None:
        if ereignis.button() != Qt.LeftButton:
            return
        daten = QMimeData()
        daten.setData(MIME, self.block.kennung.encode("utf-8"))
        zug = QDrag(self)
        zug.setMimeData(daten)
        zug.exec(Qt.MoveAction)

    def _bild_anhaengen(self) -> None:
        vorher = self.fenster.zustand()
        self.block.bilder.append(Bild(unterschrift=Zweisprachig()))
        self.fenster.aenderung("Bild hinzugefügt", vorher)
        self.fenster.neu_zeichnen()


# ------------------------------------------------------------------ Blockliste
class Blockliste(QWidget):
    """Eine Folge von Blockkarten – ein Kapitel oder der Text neben einem Bild.

    «bereich» grenzt den sichtbaren Ausschnitt der Gesamtliste ein; das Modell bleibt die flache
    Folge, damit ein Block über Kapitelgrenzen hinweg wandern kann.
    """

    def __init__(self, fenster, liste: list[Block], bereich: tuple[int, int] | None,
                 titel: str = "", lagen: list[str] | None = None):
        super().__init__()
        self.fenster = fenster
        self.liste = liste
        self.bereich = bereich
        self.lagen = lagen
        self.setAcceptDrops(True)
        self._marke: int | None = None       # Höhe der Einfügemarke beim Ziehen
        self.aufbau = QVBoxLayout(self)
        self.aufbau.setContentsMargins(4, 4, 4, 4)
        self.aufbau.setSpacing(ABSTAND)
        if titel:
            marke = QLabel(titel)
            marke.setStyleSheet("color: palette(mid);")
            self.aufbau.addWidget(marke)
        self._zeichnen()

    # ---------------------------------------------------------- Aufbau
    def _stellen(self) -> range:
        if self.bereich is None:
            return range(len(self.liste))
        return range(self.bereich[0], min(self.bereich[1], len(self.liste)))

    def _zeichnen(self) -> None:
        while self.aufbau.count():
            teil = self.aufbau.takeAt(0)
            if teil.widget():
                teil.widget().deleteLater()
        self.karten: list[tuple[int, Blockkarte]] = []
        for stelle in self._stellen():
            lage = self.lagen[stelle] if self.lagen and stelle < len(self.lagen) else ""
            karte = Blockkarte(self.liste[stelle], self, self.fenster, lage)
            self.karten.append((stelle, karte))
            self.aufbau.addWidget(karte)
        knopf = QPushButton("+ Block")
        knopf.clicked.connect(self._menue)
        self.aufbau.addWidget(knopf, alignment=Qt.AlignLeft)
        self.aufbau.addStretch(1)

    def lagen_setzen(self, lagen: list[str]) -> None:
        """Die frisch gerechnete Aufteilung an die Karten weiterreichen."""
        self.lagen = lagen
        for stelle, karte in self.karten:
            karte.lage_setzen(lagen[stelle] if stelle < len(lagen) else "")

    def lagen_auffrischen(self) -> None:
        for _stelle, karte in self.karten:
            karte.lagen_auffrischen()

    # ---------------------------------------------------------- Bearbeiten
    def _menue(self) -> None:
        menue = QMenu(self)
        for typ in Typ:
            if not typ.vermehrbar:
                continue
            if self.bereich is None and typ in (Typ.KAPITEL, Typ.BILDER, Typ.BILD_MIT_TEXT,
                                                Typ.BILDER_MIT_TEXT, Typ.AUSSCHNITT_MIT_TEXT,
                                                Typ.SEITENUMBRUCH):
                continue        # neben einem Bild stehen nur Text, Überschrift und Aufzählung
            menue.addAction(typ.klartext, lambda t=typ: self.einfuegen(t))
        menue.exec(self.sender().mapToGlobal(self.sender().rect().bottomLeft()))

    def einfuegen(self, typ: Typ) -> None:
        vorher = self.fenster.zustand()
        block = Block(kennung=self.fenster.handbuch.neue_kennung(), typ=typ)
        if typ.hat_bilder:
            block.bilder.append(Bild(ausschnitt=typ is Typ.AUSSCHNITT_MIT_TEXT))
            if typ.einzelbild:
                block.breite = Block.VORGABE_BREITE[typ.kuerzel]
        stellen = list(self._stellen())
        ziel = (stellen[-1] + 1) if stellen else (self.bereich[0] if self.bereich else 0)
        self.liste.insert(ziel, block)
        self.fenster.aenderung(f"{typ.klartext} eingefügt", vorher)
        self.fenster.neu_zeichnen()

    def schieben(self, block: Block, richtung: int) -> None:
        stelle = self.liste.index(block)
        ziel = stelle + richtung
        erlaubt = list(self._stellen())
        if ziel not in erlaubt:
            return
        vorher = self.fenster.zustand()
        self.liste.insert(ziel, self.liste.pop(stelle))
        self.fenster.aenderung("Block verschoben", vorher)
        self.fenster.neu_zeichnen()

    def loeschen(self, block: Block) -> None:
        if not self.fenster.nachfragen(f"«{block.ueberschrift()}» in beiden Sprachen löschen?"):
            return
        vorher = self.fenster.zustand()
        self.liste.remove(block)
        self.fenster.aenderung("Block gelöscht", vorher)
        self.fenster.neu_zeichnen()

    # ---------------------------------------------------------- Ziehen und Ablegen
    def dragEnterEvent(self, ereignis):
        if ereignis.mimeData().hasFormat(MIME):
            ereignis.acceptProposedAction()

    def dragMoveEvent(self, ereignis):
        if not ereignis.mimeData().hasFormat(MIME):
            return
        self._marke_setzen(self._stelle_unter(ereignis.position().toPoint().y()))
        ereignis.acceptProposedAction()

    def dragLeaveEvent(self, _ereignis):
        self._marke_setzen(None)

    def _marke_setzen(self, stelle: int | None) -> None:
        """Die Höhe merken, auf der der Strich gezeichnet wird – None löscht ihn."""
        hoehe = None if stelle is None else self._hoehe_der_stelle(stelle)
        if hoehe != self._marke:
            self._marke = hoehe
            self.update()

    def _hoehe_der_stelle(self, stelle: int) -> int:
        """Wo landet der Block, wenn man jetzt loslässt?"""
        for eigene, karte in self.karten:
            if eigene == stelle:
                return karte.y() - ABSTAND // 2
        if self.karten:
            letzte = self.karten[-1][1]
            return letzte.y() + letzte.height() + ABSTAND // 2
        return ABSTAND

    def paintEvent(self, ereignis):
        super().paintEvent(ereignis)
        if self._marke is None:
            return
        stift = QPainter(self)
        farbe = self.palette().color(QPalette.Highlight)
        stift.setPen(QPen(farbe, 3))
        stift.drawLine(4, self._marke, self.width() - 4, self._marke)
        # Zwei kleine Backen an den Enden – so ist der Strich auch vor unruhigem Grund zu sehen.
        stift.setPen(QPen(farbe, 3))
        stift.drawLine(4, self._marke - 4, 4, self._marke + 4)
        stift.drawLine(self.width() - 4, self._marke - 4, self.width() - 4, self._marke + 4)

    def dropEvent(self, ereignis):
        self._marke_setzen(None)
        kennung = bytes(ereignis.mimeData().data(MIME)).decode("utf-8")
        block = self.fenster.handbuch.finde(kennung)
        if block is None:
            return
        quelle = self.fenster.blockliste_von(block)
        if quelle is None:
            return
        ziel = self._stelle_unter(ereignis.position().toPoint().y())

        vorher = self.fenster.zustand()
        alte_stelle = quelle.index(block)
        quelle.remove(block)
        if quelle is self.liste and alte_stelle < ziel:
            ziel -= 1
        self.liste.insert(max(0, min(ziel, len(self.liste))), block)
        self.fenster.aenderung("Block verschoben", vorher)
        self.fenster.neu_zeichnen()
        ereignis.acceptProposedAction()

    def _stelle_unter(self, y: int) -> int:
        """Vor welchen Block gehört ein Wurf auf dieser Höhe?"""
        for stelle, karte in self.karten:
            if y < karte.y() + karte.height() / 2:
                return stelle
        if self.karten:
            return self.karten[-1][0] + 1
        return self.bereich[0] if self.bereich else 0
