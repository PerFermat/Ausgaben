# -*- coding: utf-8 -*-
"""Prüfungen für Modell, Abgleich, Schreiber und Validierung – ohne Qt.

Aufruf aus dem Projektverzeichnis:

    python3 -m unittest discover -s tools/handbuch_editor/tests -t tools
"""
from __future__ import annotations

import json
import os
import re
import tempfile
import unittest

from handbuch_editor import abgleich, laden, pruefung, schreiben
from handbuch_editor.modell import Typ

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))


def ohne_kennungen(daten):
    """Dieselben Daten ohne die Felder, die der Editor erst vergibt."""
    if isinstance(daten, dict):
        return {k: ohne_kennungen(v) for k, v in daten.items() if k != "id"}
    if isinstance(daten, list):
        return [ohne_kennungen(v) for v in daten]
    return daten


def minimal(sections: list) -> dict:
    """Eine vollständige, aber winzige Handbuchdatei."""
    kopf = {"doc_title": "T", "doc_author": "A", "version_text": "V", "date_text": "D",
            "toc_title": "I", "footer_text": "F", "page_text": "S %d", "placeholder_no_image": "-",
            "table_headers": ["Symbol", "Name", "Bedeutung"], "table_note": "N",
            "code_example": "C", "symbols": [["☰", "Menü", "oben links"]]}
    kopf["sections"] = sections
    return kopf


def lade_aus(de: dict, en: dict):
    with tempfile.TemporaryDirectory() as ordner:
        pfade = []
        for name, daten in (("de", de), ("en", en)):
            pfad = os.path.join(ordner, f"handbuch_{name}.json")
            with open(pfad, "w", encoding="utf-8") as datei:
                json.dump(daten, datei, ensure_ascii=False)
            pfade.append(pfad)
        return laden.lade(*pfade)


class Rundlauf(unittest.TestCase):
    """Öffnen und sofort speichern darf die echten Handbücher nicht antasten."""

    def setUp(self):
        self.handbuch, self.bericht = laden.lade(*laden.pfade(REPO))

    def test_die_echten_dateien_sind_deckungsgleich(self):
        self.assertTrue(self.bericht.leer(), self.bericht.zeilen())

    def test_inhalt_bleibt_bis_auf_die_kennungen_gleich(self):
        for pfad, sprache in zip(laden.pfade(REPO), ("de", "en")):
            with open(pfad, encoding="utf-8") as datei:
                alt = json.load(datei)
            neu = json.loads(schreiben.als_text(self.handbuch, sprache))
            self.assertEqual(ohne_kennungen(alt), ohne_kennungen(neu), pfad)

    def test_speichern_formatiert_nichts_um(self):
        """Der git-Diff nach dem Speichern zeigt nur echte Änderungen, kein Umbrechen."""
        def inhaltszeilen(text: str) -> list[str]:
            return [z.strip().rstrip(",") for z in text.splitlines()
                    if z.strip() and not re.match(r'^"id": ', z.strip())]

        for pfad, sprache in zip(laden.pfade(REPO), ("de", "en")):
            with open(pfad, encoding="utf-8") as datei:
                alt = inhaltszeilen(datei.read())
            neu = inhaltszeilen(schreiben.als_text(self.handbuch, sprache))
            self.assertEqual(alt, neu, pfad)


class SchreiberStil(unittest.TestCase):

    def test_bilddefinitionen_und_symbolzeilen_bleiben_einzeilig(self):
        handbuch, _ = laden.lade(*laden.pfade(REPO))
        text = schreiben.als_text(handbuch, "de")
        self.assertIn('"shot": {"fname": "Kontobuchungen.png", "caption": ', text)
        self.assertIn('"table_headers": ["Symbol", "Name", "Bedeutung / Funktion"],', text)
        for zeile in text.splitlines():
            if zeile.strip().startswith('["☰"'):
                self.assertTrue(zeile.strip().endswith('."],'), zeile)
                break
        else:
            self.fail("keine einzeilige Symbolzeile gefunden")

    def test_ausgabe_ist_gueltiges_json(self):
        handbuch, _ = laden.lade(*laden.pfade(REPO))
        for sprache in ("de", "en"):
            json.loads(schreiben.als_text(handbuch, sprache))


class Abgleich(unittest.TestCase):

    def test_verschobener_block_wird_ueber_die_kennung_wiedergefunden(self):
        de = minimal([
            {"type": "h1", "id": "b-0001", "text": "Kapitel"},
            {"type": "text_with_single_shot", "id": "b-0002",
             "content": [{"type": "p", "id": "b-0003", "text": "Der Absatz"}],
             "shot": {"fname": "a.png", "caption": "Bild", "width": 6.0}},
        ])
        # Im Englischen steht derselbe Absatz noch außerhalb des Bildblocks.
        en = minimal([
            {"type": "h1", "id": "b-0001", "text": "Chapter"},
            {"type": "p", "id": "b-0003", "text": "The paragraph"},
            {"type": "text_with_single_shot", "id": "b-0002", "content": [],
             "shot": {"fname": "a.png", "caption": "Picture", "width": 6.0}},
        ])
        handbuch, bericht = lade_aus(de, en)
        absatz = handbuch.finde("b-0003")
        self.assertEqual(absatz.text.en, "The paragraph")
        self.assertEqual(absatz.text.de, "Der Absatz")
        self.assertTrue(bericht.leer(), bericht.zeilen())

    def test_fehlender_block_wird_leer_ergaenzt(self):
        de = minimal([{"type": "h1", "text": "Kapitel"}, {"type": "p", "text": "Neu"}])
        en = minimal([{"type": "h1", "text": "Chapter"}])
        handbuch, bericht = lade_aus(de, en)
        self.assertEqual(len(handbuch.bloecke), 2)
        self.assertEqual(handbuch.bloecke[1].text.de, "Neu")
        self.assertEqual(handbuch.bloecke[1].text.en, "")
        self.assertEqual(bericht.zeilen(), ["1 fehlender Block ergänzt"])

    def test_ueberzaehliger_englischer_block_faellt_weg(self):
        de = minimal([{"type": "h1", "text": "Kapitel"}])
        en = minimal([{"type": "h1", "text": "Chapter"}, {"type": "p", "text": "left over"}])
        handbuch, bericht = lade_aus(de, en)
        self.assertEqual(len(handbuch.bloecke), 1)
        self.assertEqual(bericht.zeilen(), ["1 überzähliger Block entfernt"])

    def test_falscher_blocktyp_wird_nach_dem_deutschen_berichtigt(self):
        de = minimal([{"type": "h2", "id": "b-0009", "text": "Überschrift"}])
        en = minimal([{"type": "p", "id": "b-0009", "text": "Heading"}])
        handbuch, bericht = lade_aus(de, en)
        self.assertIs(handbuch.bloecke[0].typ, Typ.ABSCHNITT)
        self.assertEqual(handbuch.bloecke[0].text.en, "Heading")
        self.assertEqual(bericht.zeilen(), ["1 Blocktyp berichtigt"])

    def test_bilddefinition_gilt_fuer_beide_sprachen(self):
        de = minimal([{"type": "shot_row", "id": "b-1",
                       "shots": [{"fname": "neu.png", "caption": "Neues Bild"}]}])
        en = minimal([{"type": "shot_row", "id": "b-1",
                       "shots": [{"fname": "alt.png", "caption": "Old picture"}]}])
        handbuch, bericht = lade_aus(de, en)
        bild = handbuch.bloecke[0].bilder[0]
        self.assertEqual(bild.name, "neu.png")
        self.assertEqual(bild.unterschrift.de, "Neues Bild")
        self.assertEqual(bild.unterschrift.en, "Old picture")
        self.assertEqual(bericht.zeilen(), ["1 Bild abgeglichen"])

    def test_unbekannter_blocktyp_wird_nicht_stillschweigend_verschluckt(self):
        de = minimal([{"type": "hinweis", "text": "?"}])
        with self.assertRaises(abgleich.UnbekannterTyp):
            lade_aus(de, minimal([]))

    def test_doppelte_kennung_wird_neu_vergeben(self):
        de = minimal([{"type": "p", "id": "b-0001", "text": "eins"},
                      {"type": "p", "id": "b-0001", "text": "zwei"}])
        handbuch, _ = lade_aus(de, de)
        kennungen = [b.kennung for b in handbuch.bloecke]
        self.assertEqual(len(set(kennungen)), 2, kennungen)


class Bildbreite(unittest.TestCase):
    """Die Breite gehört an den Block – auch eine Reihe hat nur eine."""

    def test_reihe_traegt_ihre_breite_am_block(self):
        de = minimal([{"type": "shot_row", "id": "b-1", "width": 3.5,
                       "shots": [{"fname": "a.png", "caption": "A"},
                                 {"fname": "b.png", "caption": "B"}]}])
        handbuch, _ = lade_aus(de, de)
        self.assertEqual(handbuch.bloecke[0].breite, 3.5)
        geschrieben = json.loads(schreiben.als_text(handbuch, "de"))["sections"][0]
        self.assertEqual(geschrieben["width"], 3.5)
        self.assertNotIn("width", geschrieben["shots"][0])

    def test_reihe_ohne_breite_bleibt_ohne(self):
        """Sonst wüchse beim ersten Speichern in jede Bildreihe eine Zeile."""
        de = minimal([{"type": "shot_row", "id": "b-1",
                       "shots": [{"fname": "a.png", "caption": "A"}]}])
        handbuch, _ = lade_aus(de, de)
        self.assertIsNone(handbuch.bloecke[0].breite)
        self.assertEqual(handbuch.bloecke[0].breite_oder_vorgabe(), 6.0)
        self.assertNotIn("width", json.loads(schreiben.als_text(handbuch, "de"))["sections"][0])

    def test_einzelbild_behaelt_die_breite_im_bild(self):
        de = minimal([{"type": "text_with_single_shot", "id": "b-1", "content": [],
                       "shot": {"fname": "a.png", "caption": "A", "width": 4.0}}])
        handbuch, _ = lade_aus(de, de)
        self.assertEqual(handbuch.bloecke[0].breite, 4.0)
        geschrieben = json.loads(schreiben.als_text(handbuch, "de"))["sections"][0]
        self.assertEqual(geschrieben["shot"]["width"], 4.0)
        self.assertNotIn("width", geschrieben)


class Seitenumbruch(unittest.TestCase):

    def test_er_uebersteht_laden_und_schreiben(self):
        de = minimal([{"type": "p", "id": "b-1", "text": "davor"},
                      {"type": "pagebreak", "id": "b-2"},
                      {"type": "p", "id": "b-3", "text": "danach"}])
        handbuch, bericht = lade_aus(de, de)
        self.assertIs(handbuch.bloecke[1].typ, Typ.SEITENUMBRUCH)
        self.assertTrue(bericht.leer())
        geschrieben = json.loads(schreiben.als_text(handbuch, "de"))["sections"][1]
        self.assertEqual(geschrieben, {"type": "pagebreak", "id": "b-2"})

    def test_er_darf_eingefuegt_werden(self):
        self.assertTrue(Typ.SEITENUMBRUCH.vermehrbar)
        self.assertFalse(Typ.SEITENUMBRUCH.hat_text)
        self.assertFalse(Typ.SEITENUMBRUCH.hat_bilder)


class Pruefung(unittest.TestCase):

    def test_die_echten_handbuecher_melden_nur_die_fehlenden_bilder(self):
        handbuch, _ = laden.lade(*laden.pfade(REPO))
        fehler = [b for b in pruefung.pruefe(handbuch, REPO) if b.schwere == "fehler"]
        self.assertTrue(all("Bilddatei fehlt" in b.text for b in fehler),
                        [str(b) for b in fehler])

    def test_leere_uebersetzung_und_offene_auszeichnung_werden_gemeldet(self):
        de = minimal([{"type": "p", "id": "b-1", "text": "Text mit <b>offen"}])
        en = minimal([{"type": "p", "id": "b-1", "text": ""}])
        handbuch, _ = lade_aus(de, en)
        texte = [b.text for b in pruefung.pruefe(handbuch, REPO)]
        self.assertTrue(any("ohne Text (Englisch)" in t for t in texte), texte)
        self.assertTrue(any("<b> nicht geschlossen" in t for t in texte), texte)

    def test_fehlendes_bild_wird_gemeldet(self):
        de = minimal([{"type": "shot_row", "id": "b-1",
                       "shots": [{"fname": "gibtsnicht.png", "caption": "x"}]}])
        handbuch, _ = lade_aus(de, de)
        texte = [b.text for b in pruefung.pruefe(handbuch, REPO)]
        self.assertTrue(any("gibtsnicht.png" in t for t in texte), texte)

    def test_paarige_auszeichnung_ist_in_ordnung(self):
        self.assertIsNone(pruefung._offene_auszeichnung("ganz <b>normal</b> und <i>kursiv</i>"))
        self.assertIsNone(pruefung._offene_auszeichnung("Zeile<br/>Umbruch"))
        self.assertEqual(pruefung._offene_auszeichnung("<b>offen"), "b")


class Generator(unittest.TestCase):
    """docs/build_manual.py muss sich importieren lassen, ohne loszubauen."""

    @classmethod
    def setUpClass(cls):
        import importlib.util
        pfad = os.path.join(REPO, "docs", "build_manual.py")
        beschreibung = importlib.util.spec_from_file_location("build_manual_test", pfad)
        cls.bm = importlib.util.module_from_spec(beschreibung)
        beschreibung.loader.exec_module(cls.bm)          # baut nichts – sonst schlägt das fehl

    def test_kurzer_text_steht_neben_dem_bild(self):
        inhalt = [{"type": "p", "text": "Kurz."}]
        self.assertEqual(self.bm.aufteilung_der_inhalte(inhalt), ["neben"])

    def test_langer_text_schiebt_den_naechsten_unter_das_bild(self):
        inhalt = [{"type": "p", "text": "W" * 2000}, {"type": "p", "text": "Danach."}]
        self.assertEqual(self.bm.aufteilung_der_inhalte(inhalt), ["neben", "darunter"])

    def test_ein_block_kann_geteilt_sein(self):
        inhalt = [{"type": "bullets", "items": ["Punkt " + "x" * 400 for _ in range(4)]}]
        self.assertEqual(self.bm.aufteilung_der_inhalte(inhalt), ["geteilt"])

    def test_seitenumbruch_und_reihenbreite_bauen_durch(self):
        """Beide Neuerungen müssen den Generator erreichen, nicht nur die JSON."""
        daten = self.bm.lade_sprache("de")
        abschnitte = [{"type": "p", "text": "davor"},
                      {"type": "pagebreak"},
                      {"type": "shot_row", "width": 3.5,
                       "shots": [{"fname": "Aliase.png", "caption": "A"}]}]
        with tempfile.TemporaryDirectory() as ordner:
            ziel = os.path.join(ordner, "probe.pdf")
            self.bm.erzeuge(daten, "de", ziel, abschnitte=abschnitte, vorschau=True)
            self.assertTrue(os.path.getsize(ziel) > 0)
            with open(ziel, "rb") as datei:
                self.assertGreaterEqual(datei.read().count(b"/Type /Page\n"), 2,
                                        "der Seitenumbruch hat keine zweite Seite erzeugt")

    def test_die_echten_bildbloecke_lassen_sich_aufteilen(self):
        daten = self.bm.lade_sprache("de")
        geteilt = 0
        for block in daten["sections"]:
            if block["type"] == "text_with_single_shot":
                lagen = self.bm.aufteilung_der_inhalte(block["content"])
                self.assertEqual(len(lagen), len(block["content"]))
                geteilt += bool(set(lagen) - {"neben"})
        self.assertGreater(geteilt, 0, "kein einziger Block läuft unter das Bild – verdächtig")


class Sicherungskopie(unittest.TestCase):

    def setUp(self):
        from handbuch_editor import sicherung
        self.sicherung = sicherung
        self.ordner = tempfile.TemporaryDirectory()
        self._alt = sicherung.ENTWURF
        sicherung.ENTWURF = os.path.join(self.ordner.name, "entwurf.json")
        sicherung.ORDNER = self.ordner.name

    def tearDown(self):
        self.sicherung.ENTWURF = self._alt
        self.ordner.cleanup()

    def test_ablegen_holen_wegwerfen(self):
        self.assertIsNone(self.sicherung.holen("/pfad"))
        self.sicherung.ablegen(("de-Stand", "en-Stand"), "/pfad")
        stand, zeit = self.sicherung.holen("/pfad")
        self.assertEqual(stand, ("de-Stand", "en-Stand"))
        self.assertGreater(zeit, 0)
        self.sicherung.wegwerfen()
        self.assertIsNone(self.sicherung.holen("/pfad"))

    def test_entwurf_eines_anderen_projekts_wird_nicht_angeboten(self):
        self.sicherung.ablegen(("a", "b"), "/anderes/projekt")
        self.assertIsNone(self.sicherung.holen("/pfad"))


if __name__ == "__main__":
    unittest.main()
