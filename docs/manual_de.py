# -*- coding: utf-8 -*-
"""Erzeugt das deutsche Benutzerhandbuch für Ausgaben als PDF."""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm, mm
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, PageBreak,
                                Table, TableStyle, Image, KeepTogether)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.utils import ImageReader

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(REPO, "screenshots")
OUT = os.path.join(REPO, "docs", "Handbuch-Ausgaben-de.pdf")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

# --- Schriften mit voller Unicode-Abdeckung (€, →, ←, ★, ☰, ⋮, ↑) ---
pdfmetrics.registerFont(TTFont("DejaVu", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DejaVu-Bold", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"))
pdfmetrics.registerFont(TTFont("DejaVu-Oblique", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Oblique.ttf"))
pdfmetrics.registerFontFamily("DejaVu", normal="DejaVu", bold="DejaVu-Bold", italic="DejaVu-Oblique")

GREEN = colors.HexColor("#2e7d32")
GREY = colors.HexColor("#555555")
LIGHT = colors.HexColor("#eef3ee")

styles = getSampleStyleSheet()
def S(name, **kw):
    base = kw.pop("parent", styles["Normal"])
    kw.setdefault("fontName", "DejaVu")
    return ParagraphStyle(name, parent=base, **kw)

st_title   = S("t",  fontName="DejaVu-Bold", fontSize=26, leading=30, textColor=GREEN, spaceAfter=6)
st_sub     = S("s",  fontSize=13, leading=17, textColor=GREY, spaceAfter=2)
st_h1      = S("h1", fontName="DejaVu-Bold", fontSize=16, leading=20, textColor=GREEN,
               spaceBefore=16, spaceAfter=6)
st_h2      = S("h2", fontName="DejaVu-Bold", fontSize=12.5, leading=16, textColor=colors.HexColor("#1b4d1e"),
               spaceBefore=10, spaceAfter=3)
st_body    = S("b",  fontSize=10, leading=14.5, spaceAfter=5, alignment=TA_LEFT)
st_bullet  = S("bu", fontSize=10, leading=14.5, leftIndent=14, bulletIndent=2, spaceAfter=2)
st_cell    = S("c",  fontSize=9, leading=12)
st_cellb   = S("cb", fontName="DejaVu-Bold", fontSize=9, leading=12)
st_sym     = S("sy", fontName="DejaVu-Bold", fontSize=13, leading=14, alignment=TA_CENTER)
st_cap     = S("cap", fontSize=8.5, leading=11, textColor=GREY, alignment=TA_CENTER, spaceBefore=3)
st_note    = S("n",  fontSize=9, leading=13, textColor=GREY)

story = []
_first_h1 = [True]
def h1(t):
    # Jedes Kapitel beginnt auf einer neuen Seite (keine verwaisten Überschriften am Seitenende).
    if _first_h1[0]:
        _first_h1[0] = False
    else:
        story.append(PageBreak())
    story.append(Paragraph(t, st_h1))
def h2(t): story.append(Paragraph(t, st_h2))
def p(t):  story.append(Paragraph(t, st_body))
def bullets(items):
    for it in items:
        story.append(Paragraph(it, st_bullet, bulletText="•"))
    story.append(Spacer(1, 4))
def gap(h=6): story.append(Spacer(1, h))

def shot(fname, caption, width=6.0*cm):
    path = os.path.join(SHOTS, fname)
    ir = ImageReader(path)
    iw, ih = ir.getSize()
    img = Image(path, width=width, height=width*ih/iw)
    tbl = Table([[img],[Paragraph(caption, st_cap)]], colWidths=[width])
    tbl.setStyle(TableStyle([("ALIGN",(0,0),(-1,-1),"CENTER")]))
    tbl.hAlign = "CENTER"
    story.append(KeepTogether([tbl]))
    gap(6)

def pic(relpath, caption, width=14*cm):
    path = os.path.join(REPO, relpath)
    ir = ImageReader(path); iw, ih = ir.getSize()
    img = Image(path, width=width, height=width*ih/iw)
    img.hAlign = "CENTER"
    box = Table([[img],[Paragraph(caption, st_cap)]], colWidths=[width])
    box.setStyle(TableStyle([("ALIGN",(0,0),(-1,-1),"CENTER")]))
    box.hAlign = "CENTER"
    story.append(KeepTogether([box]))
    gap(6)

def shot_row(items, max_width=6.0*cm, total_width=17*cm, hgap=0.4*cm):
    """Mehrere Screenshots nebeneinander (statt untereinander) – je mit eigener Bildunterschrift.
    Jedes Bild bekommt höchstens max_width; reicht der Platz nicht, wird gemeinsam verkleinert."""
    n = len(items)
    cell_w = min(max_width, (total_width - hgap * (n - 1)) / n)
    row_cells, col_widths = [], []
    for i, (fname, caption) in enumerate(items):
        path = os.path.join(SHOTS, fname)
        ir = ImageReader(path); iw, ih = ir.getSize()
        img = Image(path, width=cell_w, height=cell_w * ih / iw)
        cell = Table([[img], [Paragraph(caption, st_cap)]], colWidths=[cell_w])
        cell.setStyle(TableStyle([("ALIGN", (0, 0), (-1, -1), "CENTER")]))
        row_cells.append(cell)
        col_widths.append(cell_w)
        if i < n - 1:
            row_cells.append("")
            col_widths.append(hgap)
    row = Table([row_cells], colWidths=col_widths)
    row.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0), ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 0), ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
    ]))
    row.hAlign = "CENTER"
    story.append(KeepTogether([row]))
    gap(6)

# Guillemets im Quelltext -> deutsche Anfuehrungszeichen im PDF (kollisionssicher gegen ASCII-").
_RLParagraph = Paragraph
def Paragraph(_t, *a, **k):
    if isinstance(_t, str):
        _t = _t.replace('\u00ab', '\u201e').replace('\u00bb', '\u201c')
    return _RLParagraph(_t, *a, **k)

# ---------------------------------------------------------------- Titelseite
# Randloses Vollbild (v3): die Grafik enthält bereits den kompletten Titeltext; nur Version/Stand
# werden per Canvas in den dafür vorgesehenen gestrichelten Rahmen gezeichnet (siehe cover_page()
# weiter unten). Seite 1 bleibt im Flowable-Sinn leer, direkt weiter zu Kapitel 1.
COVER_PATH_DE = os.path.join(SHOTS, "Handbuch Titelseite-de.png")
story.append(PageBreak())

# ---------------------------------------------------------------- 1 Einführung
h1("1. Was ist Ausgaben?")
p("<b>Ausgaben</b> ist eine mobile Ergänzung zur Desktop-Finanzsoftware "
  "<b>KMyMoney</b>. Damit erfassen Sie Bargeld-Ausgaben, -Einnahmen und Umbuchungen "
  "unterwegs direkt auf dem Smartphone oder einer Wear-OS-Uhr und übertragen sie später "
  "nach KMyMoney – statt alles von Hand nachzutragen.")
p("Die App arbeitet vollständig offline und ohne Herstellerkonto: Der Datenaustausch mit "
  "KMyMoney läuft über einen von Ihnen bereitgestellten Ordner (Nextcloud, WebDAV oder SMB/Samba). "
  "Es gibt keine Werbung und kein Tracking.")
h2("Grundbegriffe")
bullets([
  "<b>Buchung</b>: ein einzelner Geldvorgang (Ausgabe, Einnahme oder Umbuchung).",
  "<b>Konto</b>: z. B. «Bargeld», «Girokonto». Jede Buchung gehört zu einem Konto.",
  "<b>Ort (Bestand)</b>: <i>wo</i> das Bargeld eines Kontos physisch liegt (Geldbeutel, Spardose …).",
  "<b>Kategorie</b>: wofür das Geld ausgegeben/eingenommen wurde (z. B. «Lebensmittel»).",
  "<b>Alias</b>: eine gelernte Zuordnung «gesprochener Begriff → richtiger Empfänger» samt Konto/Kategorie.",
  "<b>.kmy</b>: das Dateiformat von KMyMoney, in das exportiert und aus dem importiert wird.",
])

# ---------------------------------------------------------------- 2 Erste Schritte
h1("2. Erste Schritte")
h2("Willkommen-Assistent beim ersten Start")
p("Solange noch <b>kein Konto</b> angelegt ist, erscheint beim Start der App automatisch ein schlanker "
  "<b>Willkommen-Assistent</b>. Er bündelt die wichtigsten Startpunkte an einer Stelle und lässt sich "
  "nur automatisch aufrufen (kein Menüeintrag).")
shot_row([
    ("Willkommen_1.png", "Sprache, Format und Synchronisierung"),
    ("Willkommen_2.png", "«Konten importieren», «Weitere Einstellungen», «Fertig»"),
])
bullets([
  "<b>Sprache</b> wählen (wirkt sofort).",
  "<b>Import-/Export-Format</b>: CSV oder KMyMoney-Datei (.kmy).",
  "<b>Synchronisierung</b>: Server-Typ, URL, Benutzer, Passwort – mit <b>«Verbindung testen»</b>; im "
  ".kmy-Modus zusätzlich <b>«.kmy auswählen»</b>.",
  "<b>«Konten importieren»</b>: derselbe Auswahldialog wie bei «Konto hinzufügen», direkt hier im "
  "Assistenten.",
  "<b>«Weitere Einstellungen»</b> öffnet die vollständigen Einstellungen; <b>«Fertig»</b> schließt den "
  "Assistenten.",
])
p("<b>Wichtig – zuerst ein Konto:</b> Legen Sie mindestens ein <b>Konto</b> an – im Assistenten über "
  "<b>«Konten importieren»</b> oder später in der Kontenschublade (☰ oben links) über "
  "<b>«Konto hinzufügen»</b>. Das <b>Standardkonto</b> und die davon abhängigen Optionen (z. B. die Orte je "
  "Konto) lassen sich erst festlegen, <b>wenn bereits Konten vorhanden sind</b>.")
p("Alle Einstellungen im Detail – Sprache, Zahlenformat, Währung, Server-Verbindung, Export-Modus, "
  "Standardkonto, Alias-Namen, App-Sperre, Sicherung u. v. m. – erklärt das Kapitel <b>«Einstellungen»</b> "
  "am Ende des Handbuchs (Kapitel 16). Erreichbar sind sie jederzeit über das <b>Menü ⋮</b> oben rechts, "
  "Eintrag <b>«Einstellungen»</b> ganz unten.")

# ---------------------------------------------------------------- 3 Hauptbildschirm
h1("3. Der Hauptbildschirm")
p("Der Hauptbildschirm zeigt oben die grüne Titelleiste, darunter die <b>Saldenzeile</b> und dann "
  "die Liste der Buchungen. Unten rechts liegen die Aktionsknöpfe.")
shot("Kontobuchungen.png", "Hauptbildschirm mit Titelleiste, Saldenzeile und Buchungsliste")
h2("Saldenzeile (antippen zum Durchschalten)")
p("Die farbige Zeile unter der Titelleiste zeigt einen Saldo. Durch <b>Antippen</b> schalten Sie "
  "der Reihe nach durch:")
bullets([
  "<b>Konto</b>: Saldo des gerade gewählten Kontos (nur wenn ein einzelnes Konto gewählt ist).",
  "<b>Gesamt</b>: Summe aller Konten.",
  "<b>Gesamtvermögen</b>: alle Konten + aktueller Depotwert (nur wenn ein Depot importiert wurde).",
  "<b>Orte</b>: Salden der einzelnen Bargeld-Orte des Kontos.",
  "<b>Gefiltert</b>: Summe der aktuell gefilterten Buchungen (nur bei aktivem Filter).",
])
p("Grüner Betrag = positiv/Guthaben, roter Betrag = negativ.")
h2("Homescreen-Widget")
p("Für den Startbildschirm gibt es vier wählbare Widgets, die den Saldo des <b>Standardorts</b> "
  "(Standardkonto → Standardort) zeigen: <b>klein</b> (2×1, nur Saldo), <b>mittel</b> (4×2, Saldo + drei "
  "Schnellaktionen: Buchung, Sprache, Betrag), <b>groß</b> (4×4, Saldo-Kopf mit Aktualisieren, die letzten "
  "Buchungen und die Aktionsleiste) und <b>Typ</b> (4×2, wie auf der Uhr: drei farbige Knöpfe Einnahme/"
  "Umbuchung/Ausgabe als Symbol-Knöpfe + grauer Wechsel-Knopf, darunter der Saldo). Ein Tippen auf den Saldo "
  "öffnet die App; die Knöpfe des mittleren/großen Widgets starten «Neue Buchung», die Spracheingabe, die "
  "Betrag-only-Erfassung bzw. die Bestände. Beim <b>Typ-Widget</b> startet ein Typ-Knopf <b>sofort die "
  "Spracherkennung</b> und legt die Buchung direkt an – die App wird dabei nicht geöffnet (nur der "
  "System-Sprachdialog erscheint); der graue <b>Wechsel-Knopf</b> schaltet das gewählte Konto bzw. dessen "
  "Orte durch (Saldo und Ziel der neuen Buchung folgen der Auswahl). Das Widget aktualisiert sich beim Öffnen "
  "der App und in regelmäßigen Abständen.")
shot("Widget.png", "Alle vier Widget-Größen: mittel, Typ, groß und klein", width=6.0*cm)

# ---------------------------------------------------------------- 4 Symbol-Referenz
h1("4. Symbol- und Bedienelement-Referenz")
p("Diese Übersicht erklärt jedes Symbol der App und was es tut.")

sym_rows = [
  ("☰", "Menü / Kontenschublade", "Oben links. Öffnet die seitliche Schublade mit allen Konten, Depots und «Konto hinzufügen»."),
  ("←", "Zurück", "Oben links in Unterseiten. Kehrt zum vorherigen Bildschirm zurück."),
  ("⬇", "Export / Synchronisieren", "Titelleisten-Symbol (Pfeil nach unten, Download-Stil). Startet den Abgleich: schreibt neue Buchungen in die .kmy bzw. lädt sie zum Sync-Ziel."),
  ("▽", "Filter", "Trichter-Symbol. Öffnet den Filter nach Empfänger, Kategorie und Betrag."),
  ("▮▮", "Auswertung", "Balkendiagramm-Symbol. Öffnet die grafische Auswertung."),
  ("⋮", "Weitere Menüpunkte", "Drei Punkte (Überlauf). Enthält «Kategorien», «Bestände», «Budget», «Geplante Buchungen» und – <b>ganz unten</b> – «Einstellungen»."),
  ("✚", "Neue Buchung", "Runder Knopf unten rechts. Kurz tippen: neue Buchung erfassen. <b>Langer Druck: Spracheingabe.</b>"),
  ("⊞", "Stille Betragserfassung", "Ziffernblock-Symbol (nur bei eingeschaltetem Standort). Nur den Betrag eintippen; der Empfänger wird über den Standort ermittelt."),
  ("↑", "Nach oben", "Kleiner Pfeil. Springt an den Anfang der Buchungsliste."),
  ("★", "Bevorzugter Alias", "Markiert in der Alias-Liste einen bevorzugten Alias (wird vor Buchungen berücksichtigt)."),
  ("→", "Alias-Zuordnung", "In der Alias-Liste: «gesprochener Begriff → richtiger Empfänger»."),
  ("→ / ←", "Umbuchung", "In der Buchungsliste: → ausgehende, ← eingehende Umbuchung (zeigt Gegenkonto/Empfänger)."),
  ("Split", "Splitbuchung", "Kennzeichnet eine Buchung, die auf mehrere Kategorien aufgeteilt ist."),
  ("exportiert", "Exportiert", "Kleiner grüner Hinweis: diese Buchung wurde bereits exportiert."),
  ("● rot/grün", "Betragsfarbe", "Roter Betrag = Ausgabe/negativ, grüner Betrag = Einnahme/positiv."),
]
data = [[Paragraph("Symbol", st_cellb), Paragraph("Name", st_cellb), Paragraph("Bedeutung / Funktion", st_cellb)]]
for sym, name, desc in sym_rows:
    data.append([Paragraph(sym, st_sym), Paragraph(name, st_cellb), Paragraph(desc, st_cell)])
tbl = Table(data, colWidths=[2.1*cm, 3.3*cm, 10.4*cm], repeatRows=1)
tbl.setStyle(TableStyle([
    ("BACKGROUND",(0,0),(-1,0),GREEN),
    ("TEXTCOLOR",(0,0),(-1,0),colors.white),
    ("FONTNAME",(0,0),(-1,0),"DejaVu-Bold"),
    ("VALIGN",(0,0),(-1,-1),"MIDDLE"),
    ("ALIGN",(0,0),(0,-1),"CENTER"),
    ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, LIGHT]),
    ("GRID",(0,0),(-1,-1),0.4,colors.HexColor("#cccccc")),
    ("TOPPADDING",(0,0),(-1,-1),4),
    ("BOTTOMPADDING",(0,0),(-1,-1),4),
]))
story.append(tbl)
story.append(Paragraph("Hinweis: Die Symbole in der Titelleiste stammen aus dem Material-Design-Satz; "
    "oben ist ihre Bedeutung beschrieben. Je nach Platz erscheinen manche Punkte im «⋮»-Überlaufmenü.",
    st_note))

# ---------------------------------------------------------------- 5 Konten
h1("5. Konten und die Kontenschublade")
shot("Kontenmenü.png", "Kontenschublade mit Konten, Depot und «Konto hinzufügen»", width=6.0*cm)
bullets([
  "Öffnen über das ☰-Symbol oben links oder durch Wischen vom linken Rand.",
  "<b>«Alle Konten»</b> zeigt alle Buchungen zusammen; ein einzelnes Konto filtert die Liste darauf.",
  "<b>Langer Druck auf ein Konto</b>: dieses Konto aus der .kmy importieren/aktualisieren.",
  "<b>Langer Druck auf «Alle Konten»</b>: importiert <b>alle</b> vorhandenen Konten neu (Komplett-Import).",
  "<b>Depots</b> erscheinen unten als «… (Depot)»: kurzer Tipp öffnet die Depot-Ansicht, langer Tipp aktualisiert das Depot.",
  "<b>«Konto hinzufügen»</b> (unten): lädt die .kmy und bietet die enthaltenen Konten zur Auswahl.",
])

# ---------------------------------------------------------------- 6 Buchung erfassen
h1("6. Eine Buchung erfassen")
p("Tippen Sie auf den runden <b>✚</b>-Knopf. Es öffnet sich der Buchungs-Editor.")
shot("Buchung Empfänger.png", "Buchungs-Editor mit Typ-Umschalter, Betrag, Empfänger und Konto")
h2("Rechnen im Betragsfeld")
p("Das <b>Betragsfeld</b> nimmt statt der System-Tastatur eine kleine <b>Rechnung</b> entgegen (z. B. "
  "<i>10+20*3</i> oder <i>30-5</i>) – praktisch zum Teilen einer Rechnung oder Verrechnen mehrerer Posten.")
h2("Felder und Bedienelemente")
bullets([
  "<b>Typ-Umschalter</b>: <font color='#b00020'>Ausgabe</font> · <b>Umbuchung</b> · "
  "<font color='#2e7d32'>Einnahme</font>. Bestimmt die Art der Buchung.",
  "<b>Betrag</b>, <b>Geldempfänger</b>, <b>Konto</b> (Auswahl aus vorhandenen Konten).",
  "<b>Datum</b>: mit heute vorbelegt; der Knopf <b>«Heute»</b> setzt schnell das aktuelle Datum.",
  "<b>Notiz</b>: freier Text. Bei eingeschaltetem Standort steht hier automatisch «GPS: lat, lon».",
  "<b>Ort</b> (nur bei in der App angelegten Ausgaben/Einnahmen): welchem Bargeld-Ort die Buchung zugerechnet wird.",
  "<b>«exportiert»-Schalter</b>: markiert, ob die Buchung schon exportiert wurde.",
])
h2("Speichern")
bullets([
  "<b>Als neu speichern</b>: legt eine neue Buchung an.",
  "<b>Aktualisieren</b>: speichert Änderungen an einer bestehenden Buchung.",
  "<b>Löschen</b>: entfernt die Buchung.",
])
p("<b>Datum-Rückfrage beim Kopieren:</b> Öffnen Sie eine bestehende Buchung, lassen das Datum "
  "unverändert und speichern sie «als neu», fragt die App, ob das alte Datum oder heute gelten soll.")
h2("Splitbuchung (mehrere Kategorien)")
p("Im Abschnitt «Split-Kategorien» lassen sich mehrere Kategorien mit Teilbeträgen erfassen. Bei einer "
  "Kategorie sind Gesamt- und Kategoriebetrag gekoppelt; bei mehreren muss die Summe dem Gesamtbetrag "
  "entsprechen. Teilbeträge dürfen negativ sein.")
h2("Umbuchung")
p("Wählen Sie den Typ «Umbuchung», dann Von- und Nach-Konto sowie optional einen Empfänger. Es entsteht "
  "eine verknüpfte Buchung in beiden Konten (in der Liste mit → bzw. ← markiert). Zusätzlich können Sie "
  "einen <b>Von-Ort</b> und einen <b>Nach-Ort</b> wählen; dann wird das jeweilige Ortsjournal mitgeführt "
  "(Von-Konto −Betrag, Nach-Konto +Betrag). Bearbeiten oder Löschen der Umbuchung rollt die Ort-Bewegungen "
  "wieder zurück.")
shot("Buchung Umbuchung.png", "Detailansicht einer Umbuchung mit Kontostand vorher/nachher", width=6.0*cm)

# ---------------------------------------------------------------- 7 Sprache & Standort
h1("7. Spracheingabe und Standort-Erfassung")
h2("Spracheingabe")
p("<b>Langer Druck</b> auf den ✚-Knopf öffnet die Spracheingabe. Sagen Sie z. B. «Frisör 20 €»: Die App "
  "sucht eine passende Vorlage (Empfänger, Konto, Kategorie, Buchungsart) und öffnet sie mit heutigem "
  "Datum und dem gesprochenen Betrag. Die Empfängersuche findet auch ähnlich geschriebene Namen.")
h2("Nächstgelegener Empfänger")
p("Gibt es mehrere gleichnamige Empfänger (z. B. «REWE Ort 1» und «REWE Ort 2») und ist der Standort "
  "bekannt, wählt die App den <b>geografisch nächstgelegenen</b> – sowohl bei bestehenden Buchungen als "
  "auch bei Aliasen mit hinterlegten Koordinaten.")
h2("Nur den Betrag erfassen (Ziffernblock ⊞)")
p("Bei eingeschaltetem Standort erscheint unten das Ziffernblock-Symbol. Tippen Sie nur einen Betrag ein: "
  "Die App sucht an Ihrem aktuellen Standort eine passende Vorlage und zeigt den gefundenen Empfänger "
  "bereits vor dem Speichern an.")
shot("Buchung Betrag - Automatischer Empfänger anhand GPS.png",
     "Betrag-only-Erfassung: der Empfänger wird über den Standort gefunden", width=6.0*cm)
h2("Belegfotos")
p("Im Buchungseditor können Sie über den Knopf <b>«Beleg»</b> ein Foto des Belegs <b>aufnehmen</b> oder eines "
  "<b>aus der Galerie</b> wählen (ein Foto je Buchung; abschaltbar unter <i>Einstellungen → Belegfotos</i>). "
  "Das Bild wird privat auf dem Handy gespeichert und <b>im Hintergrund</b> in den Ordner «Belege», darunter je "
  "Jahr ein Unterordner, Ihres konfigurierten Sync-Ordners (WebDAV/Nextcloud oder SMB) hochgeladen – offline "
  "aufgenommene Fotos folgen beim nächsten App-Start mit Verbindung.")
p("Der Verweis auf das Bild wird – wie die GPS-Koordinaten – als Kürzel <b>«BELEG: …»</b> in der Notiz "
  "hinterlegt. Da die Notiz beim Export/Import mit KMyMoney erhalten bleibt, findet die App das Foto <b>auch "
  "nach einem Neu-Import</b> wieder. Im Editor zeigt das Notizfeld nur den freien Text; <b>GPS und Beleg stehen "
  "als zwei eigene, nicht änderbare Zeilen darunter</b> – je mit Symbol: das Karten-Symbol öffnet den Standort, "
  "das Beleg-Symbol das Foto (Ansicht) bzw. die Kamera/Galerie-Auswahl (Bearbeiten).")
h2("Alias-Namen (gelernte Zuordnungen)")
p("Ändern Sie beim Speichern den erkannten Empfänger, fragt die App, ob sie sich die Zuordnung als Alias "
  "merken soll – samt Konto, Kategorie und <b>Ort</b> (bei Umbuchungen Von-/Nach-Konto und Von-/Nach-Ort). "
  "So wird «Mama 100 €» künftig automatisch auf den echten Namen mit passendem Konto/Ort gebucht. Die "
  "Kategorie- und Ort-Felder im Alias-Formular verhalten sich wie im Buchungseditor (gruppierte Kategorie-"
  "Auswahl; ein Ortsfeld nur bei Konten mit Orten). Reihenfolge der Auflösung: <b>bevorzugte Aliase (★) → "
  "Buchungen → übrige Aliase</b>.")
p("Ein Alias kann <b>beliebig viele Standorte</b> führen (z. B. mehrere Filialen). Im Alias-Formular fügt "
  "«Koordinate hinzufügen» eine Zeile mit Breite/Länge und «Karte öffnen» hinzu; der Minus-Knopf entfernt eine "
  "Koordinate wieder. Beim Auffinden (Betrag-only/Sprache) passt der Alias, wenn der aktuelle Standort "
  "<b>einer beliebigen</b> seiner Koordinaten nahe ist. Wird ein Alias erneut aus einer Buchung an einem "
  "anderen Ort gelernt, wird die Koordinate <b>ergänzt</b> (nicht überschrieben).")
shot("Aliase.png", "Verwaltung der Alias-Namen: gesprochener Begriff → echter Empfänger", width=6.0*cm)

# ---------------------------------------------------------------- 8 Liste & Filter
h1("8. Buchungsliste und Filter")
bullets([
  "<b>Kurzer Druck</b> auf eine Buchung öffnet sie als reine Ansicht (gleicher Aufbau wie der Editor, aber "
  "ohne Änderungsmöglichkeit): eine große farbige Überschrift zeigt die Art (Einnahme = grün, Umbuchung = "
  "gelb, Ausgabe = rot), unten stehen der Kontostand vor und nach der Buchung. <b>Langer Druck</b> öffnet "
  "sie zum Bearbeiten.",
  "In der Liste wird die Notiz auf maximal zwei Zeilen gekürzt; im Editor umfasst das Notizfeld vier Zeilen.",
  "<b>Herunterziehen</b> (in einer Konto- oder Depot-Ansicht): in der <b>kmy-Variante</b> wird die "
  ".kmy-Datei neu eingelesen und genau dieses Konto bzw. Depot aktualisiert. In der <b>CSV-Variante</b> ist "
  "das nicht verfügbar – dort aktualisiert man ein Konto über das Kontenmenü (langer Druck auf das Konto in "
  "der Schublade).",
  "Markierungen: «Split», → / ← (Umbuchung), «exportiert»; Farben rot/grün.",
  "<b>Filter</b> (Trichter ▽): mit einem <b>Suchfeld</b>, das <b>Empfänger, Notiz und Kategorie</b> "
  "durchsucht (Teilstring, Groß-/Kleinschreibung egal – so finden Sie auch eine Buchung über ihre Notiz); "
  "dazu Kategorie (als Baum), Betrag (Schieberegler) und <b>Datum von–bis</b> (Schieberegler in "
  "Monatsschritten; ein taggenaues Datum geben Sie direkt im Feld ein). Der Filter wirkt auf Liste und "
  "Auswertung. Bei Kategorie-Filter zeigt eine Splitbuchung nur den Teilbetrag der gewählten Kategorie.",
  "<b>Rückgängig</b>: Nach dem Löschen einer Buchung erscheint unten kurz «Rückgängig» – ein Tipp legt sie "
  "wieder an. Sie erhält dabei eine neue interne Nummer; im Ort-Journal bleiben Löschung und Wiederanlage "
  "als Bewegungen sichtbar (die Historie bleibt erhalten, der Saldo stimmt). Bei Umbuchungen gibt es kein "
  "Rückgängig.",
  "<b>Langdruck auf das App-Symbol</b> (Startbildschirm): «Neue Buchung» öffnet den Editor direkt.",
])
shot("Buchungen Filtern.png", "Filter nach Suchtext, Kategorie, Betrag und Zeitraum", width=6.0*cm)

# ---------------------------------------------------------------- 9 Auswertung
h1("9. Auswertung")
shot("Buchungen Auswertung.png", "Auswertung als Balken- und Liniendiagramm", width=9.5*cm)
bullets([
  "Zeiträume: Tag / Woche / Monat / Jahr.",
  "Sichten: einzelnes Konto, Ort oder Gesamt.",
  "<b>Zoomen per Fingergeste</b>: waagerecht = Anzahl der Balken, senkrecht = Y-Achse.",
  "Geschlossene Konten zählen nur in der Gesamtsicht (historischer Saldo).",
])

# ---------------------------------------------------------------- 10 Budget
h1("10. Wofür geht mein Geld?")
p("Die Seite <b>Kategorien</b> (Menü <b>⋮ → Kategorien</b>) zeigt die <b>Ausgaben je Kategorie</b> – als "
  "Kreisdiagramm und darunter als Liste, <b>absteigend</b> nach Betrag. Sie ergänzt die Auswertung "
  "(Kapitel 9), die nur den zeitlichen Verlauf je Konto/Ort zeigt.")
shot("Kategorien Auswertung.png", "Kategorien-Kreisdiagramm: «Wofür geht mein Geld?»", width=6.0*cm)
bullets([
  "Umschalter <b>Monat/Jahr</b>: laufender Monat bzw. laufendes Jahr.",
  "In beiden Sichten blättern Sie – wie im Budget – per <b>Wischgeste</b> durch die Zeiträume "
  "(nach rechts = zurück, nach links = vor); die Kopfzeile zeigt mittig fett den angezeigten "
  "Monat bzw. das Jahr, links und rechts grau den vorigen bzw. nächsten (Tippen blättert ebenfalls). "
  "Vor den ersten Buchungszeitraum und hinter den aktuellen wird nicht geblättert.",
  "Das Diagramm nutzt die <b>volle Breite</b> und höchstens die <b>halbe Bildschirmhöhe</b>; die Liste "
  "darunter scrollt für sich, das Diagramm bleibt dabei sichtbar.",
  "Die Segmente sind unbeschriftet; ein <b>Tipp</b> zeigt in der Mitte Kategorie und Betrag, ohne Auswahl "
  "steht dort die Gesamtsumme. Die Liste nennt zusätzlich den <b>Anteil in Prozent</b>.",
  "<b>Splitbuchungen</b> zählen über ihre Teilbeträge (nicht doppelt), <b>Umbuchungen</b> bleiben außen vor "
  "– dieselbe Datenbasis wie die Budget-Seite.",
  "Die Auswertung gilt über <b>alle Konten</b> und zeigt nur Kategorien mit Ausgaben.",
])
h2("Geplante Buchungen einbeziehen (Kalender-Symbol)")
p("Der <b>Kalender-Schalter</b> oben rechts rechnet die im Zeitraum <b>fälligen geplanten Auszahlungen</b> "
  "(Kapitel 8) zu den bereits getätigten hinzu. Dann gilt:")
bullets([
  "Die <b>Summe je Kategorie</b> steigt um den geplanten Anteil; die Liste sortiert nach der neuen Summe.",
  "Im Diagramm erscheint der <b>bereits gezahlte</b> Anteil in der kräftigen Farbe der Kategorie, der "
  "<b>geplante</b> Anteil direkt daneben im <b>selben, helleren</b> Ton – beide bilden <b>einen "
  "durchgehenden Kreisausschnitt</b> ohne Trennstrich dazwischen.",
  "Zeilen mit geplantem Anteil lassen sich <b>aufklappen</b> (Pfeil rechts) und zeigen «Bereits gezahlt» "
  "und «Geplant» getrennt.",
  "Mit eingeschaltetem Schalter blättert der Slider auch in <b>zukünftige</b> Monate/Jahre, die dann nur "
  "geplante Werte zeigen – bis zum letzten geplanten Termin.",
])
h2("Farben festlegen (Paletten-Symbol)")
p("Jede Kategorie hat eine <b>feste Farbe</b> – unabhängig davon, auf welchem Listenplatz sie gerade steht. "
  "Das <b>Paletten-Symbol</b> oben rechts öffnet die Farbliste: Ein Tipp auf eine Kategorie öffnet die "
  "Auswahl; «Standard» setzt die automatische Farbe wieder ein. Die Zuordnung gilt sofort im Diagramm und "
  "in der Liste.")

h1("11. Budget")
p("Die <b>Budget</b>-Seite (Menü <b>⋮ → Budget</b>) stellt je Kategorie das <b>Ist</b> dem <b>Soll</b> "
  "gegenüber. Sie beantwortet die Frage: «Liege ich im Plan?»")
shot("Budget.png", "Budget-Seite: Ist/Soll je Kategorie, mit Fortschrittsbalken", width=6.0*cm)
h2("Woher kommt das Soll?")
bullets([
  "<b>Aus KMyMoney importieren</b> (Knopf in den Einstellungen): enthält Ihre .kmy eine Budgetplanung, "
  "werden deren Werte als Soll übernommen – <b>monatsgenau</b> (monatliche bzw. monatweise Budgets werden "
  "je Monat gespeichert). Solche Soll-Werte sind <b>nicht editierbar</b>.",
  "<b>Aus dem Verlauf berechnen</b>: Summe aller bisherigen Jahre ÷ Anzahl der Jahre mit Daten. Automatisch, "
  "wenn der Schalter «Budget app-intern berechnen» aktiv ist, sonst per Knopf. Diese Soll-Werte lassen sich "
  "durch <b>langen Druck auf eine Zeile</b> ändern.",
])
p("Ob eine Kategorie <b>Einnahme oder Ausgabe</b> ist, wird zuverlässig aus dem KMyMoney-Typ der .kmy-Datei "
  "bestimmt (nicht aus dem Vorzeichen einzelner Buchungen). Eine Erstattung mindert daher die Ausgabekategorie, "
  "ohne sie zur Einnahme zu machen; ein rechnerisch negativer Ist wird auf 0 gesetzt.")
h2("Anzeige")
bullets([
  "Umschalter <b>Jahr / Monat</b>. Die Jahressicht summiert die Monate; die Monatssicht zeigt das Soll des "
  "angezeigten Monats gegen die Ist-Werte desselben Monats. In der Monatssicht blättert man per "
  "<b>Wischgeste</b> (oder Tippen auf den grauen Vor-/Folgemonat in der Kopfzeile) durch die Monate.",
  "Umschalter <b>nur Hauptkategorien / mit Unterkategorien</b> (Hauptkategorie = Summe über ihre "
  "Unterkategorien).",
  "<b>Einnahmen</b> zuerst, dann <b>Ausgaben</b> (große Überschriften); Hauptkategorien fett, "
  "Unterkategorien eingerückt. Rechts steht «Ist / Soll».",
  "Unter jeder Kategorie ein dünner <b>Balken</b>: <b>grün</b>, wenn man im Plan liegt, <b>rot</b>, wenn "
  "daneben (Breite = verbrauchter Anteil des Solls). Statt eines rein linearen Zeitvergleichs lernt die App "
  "aus der <b>Zahlungshistorie</b> das typische Timing jeder Kategorie: eine <b>einmalige</b> Ausgabe (z. B. "
  "eine am Monatsanfang gekaufte Monatskarte) ist bereits grün, sobald sie im Budget liegt, während "
  "<b>regelmäßige</b> Ausgaben (Lebensmittel) weiter am Zeitanteil gemessen werden. Ohne Historie gilt der "
  "lineare Vergleich (bei Einnahmen umgekehrt).",
])

# ---------------------------------------------------------------- 11 Geplante Buchungen
h1("12. Geplante Buchungen")
p("Die Seite <b>Geplante Buchungen</b> (Menü <b>⋮ → Geplante Buchungen</b>, nur im <b>.kmy-Modus</b> "
  "sichtbar) importiert die in KMyMoney angelegten Daueraufträge/Planungen und zeigt sie als <b>eine "
  "chronologische Liste</b> nach Fälligkeit. Die Planungen werden <b>nicht</b> beim normalen Konto-Import "
  "aktualisiert, sondern nur, wenn Sie auf dieser Seite die <b>Wischgeste nach unten</b> ausführen – dabei "
  "erscheint der gewohnte <b>gelbe Fortschrittsbanner</b>.")
shot("Geplante Buchungen.png", "Geplante Buchungen: chronologische Liste mit Saldo-Streifen", width=6.0*cm)
bullets([
  "Jede wiederkehrende Planung wird in ihre <b>Einzeltermine aufgefaltet</b> (z. B. ein wöchentlicher "
  "Bäcker erscheint mehrfach) – <b>ab der gespeicherten nächsten Fälligkeit</b> bis <b>höchstens 2 Jahre</b> "
  "in die Zukunft.",
  "Nur <b>aktive</b> Planungen; Termine <b>ohne Datum</b> werden ausgelassen, ein <b>Enddatum</b> (bzw. eine "
  "begrenzte Anzahl Buchungen in KMyMoney) begrenzt die Vorschau.",
  "Vor jeder Zeile ein farbiger <b>Strich</b>: <b>grün</b> = Einzahlung, <b>rot</b> = Auszahlung, "
  "<b>gelb</b> = Umbuchung. Das <b>Datum</b> steht in einer eigenen Spalte vorne, daneben der <b>Name</b> mit "
  "dem <b>Zahlungsempfänger</b> in kleinerer, grauer Schrift dahinter und darunter die <b>Kategorie</b> "
  "(bei mehreren Kategorien <b>«Splitbuchung»</b>), dann eine <b>Konto-Spalte</b> und rechts der Betrag. Bei "
  "einer <b>Umbuchung</b> zeigt die Konto-Spalte <b>beide Konten untereinander</b> (statt einer Kategorie).",
  "Die <b>Buchungsart</b> wird streng aus KMyMoney übernommen: ein <b>Aktien-/ETF-Kauf</b> (Sparplan) ist "
  "eine <b>Umbuchung</b> vom Geldkonto ins Wertpapier – nicht eine Ausgabe.",
  "Ein <b>Tipp auf eine Zeile</b> öffnet die Buchung in der <b>gewohnten Detail-Maske</b> (schreibgeschützt, "
  "genau wie eine echte Kontobewegung – inkl. Kategorien einer Splitbuchung und Kontostand vorher/nachher).",
  "<b>Jetzt buchen</b>: Ein <b>langer Druck</b> auf eine Zeile öffnet den Buchungs-Editor als <b>neue "
  "Buchung</b>, vorbefüllt aus der Planung (Datum = Fälligkeit, Betrag, Empfänger, Konto, Kategorien bzw. "
  "Von/Nach). Sie prüfen, ändern bei Bedarf und speichern mit «Neue Buchung» – die Buchung landet im Konto "
  "und (bei gewähltem Ort) in den Beständen. Die Planung selbst bleibt unberührt; sie stammt aus KMyMoney "
  "und wird von der App nicht zurückgeschrieben. Kurzer Tipp = ansehen, langer Druck = buchen – wie in der "
  "Buchungsliste.",
])
h2("Erinnerung an fällige Buchungen")
p("Auf Wunsch meldet sich die App einmal täglich, wenn <b>heute</b> etwas fällig ist («3 geplante Buchungen "
  "heute fällig»); ein Tipp auf die Meldung öffnet diese Seite. Der Schalter <b>«Erinnerung an fällige "
  "Buchungen»</b> steht in den Einstellungen und ist <b>standardmäßig aus</b>; beim Einschalten fragt die App "
  "einmalig die Benachrichtigungs-Berechtigung. Gibt es nichts Fälliges, kommt auch keine Meldung.")
h2("Saldo-Streifen (durchschaltbar)")
p("Oben – wie in der normalen Kontoansicht – ein <b>Saldo-Streifen</b> (Bezeichnung links, Betrag rechts), "
  "der sich immer auf den <b>aktuell gesetzten Filter</b> bezieht. Jeder <b>Tipp</b> schaltet die Anzeige weiter:")
bullets([
  "<b>Überschuss</b> bzw. <b>Fehlbetrag</b> (Standard) = Summe Einzahlungen − Summe Rechnungen; es wird nur "
  "der zutreffende Zustand angezeigt (grün = Überschuss, rot = Fehlbetrag).",
  "<b>Summe Einzahlungen</b> (grün).",
  "<b>Summe Rechnungen</b> (rot).",
  "<b>Umbuchungen</b> (gelb) = Summe aller geplanten Umbuchungen.",
])
p("Umbuchungen zählen <b>nicht</b> in Einzahlungen oder Rechnungen (sie verschieben nur Geld zwischen eigenen "
  "Konten) und haben deshalb eine eigene Zeile.")
h2("Filter")
p("Über das <b>Filter-Symbol</b> oben rechts lässt sich die Liste (und damit auch der Saldo-Streifen und die "
  "Grafik) eingrenzen nach:")
bullets([
  "<b>Buchungsart</b>: Einzahlungen, Rechnungen, Umbuchungen (einzeln an-/abwählbar).",
  "<b>Konto</b>: Einzelauswahl aus einer Liste aller Konten (oder «Alle Konten»).",
  "<b>Name</b>: Suchtext, der in <b>Name oder Zahlungsempfänger</b> gesucht wird.",
  "<b>Zeitraum</b>: Schieberegler (monatsgenau) oder direkte, taggenaue Datumseingabe.",
])
h2("Grafik")
p("Das <b>Grafik-Symbol</b> oben rechts öffnet eine Auswertung, die <b>genauso aussieht wie die Grafik der "
  "Kontoansicht</b>: grüne/rote <b>Balken</b> für Einzahlungen bzw. Rechnungen und eine <b>Entwicklungslinie</b> "
  "des gewählten Kontos bzw. des Gesamtvermögens. Wählbar sind die <b>Sicht</b> (Gesamt oder einzelnes Konto) "
  "und die <b>Zeiteinheit</b> Tag/Woche/Monat. Die Linie <b>startet am Anfang des Betrachtungszeitraums "
  "(= aktueller Tag) bei 0</b> und lässt sich – wie die Konto-Grafik – <b>zoomen und verschieben</b>; sie "
  "beginnt jedoch ganz links (heute) und läuft nach rechts in die Zukunft. Die Grafik übernimmt den "
  "eingestellten Listenfilter. Bei einer <b>Umbuchung</b> sinkt der Betrag beim <b>Quellkonto</b> (rot) und "
  "steigt beim <b>Zielkonto</b> (grün) – die Richtung («Zahlen an» bzw. «von») wird aus KMyMoney übernommen.")
p("Wer weit nach unten scrollt, kann über den eingeblendeten <b>Nach-oben-Knopf</b> (unten links) direkt an "
  "den Listenanfang springen.")

# ---------------------------------------------------------------- 12 Bestände / Depot
h1("13. Bestände (Orte) und Depot")
h2("Bestände / Orte – was ist das?")
p("Ein <b>Ort</b> beschreibt, <b>wo</b> das Bargeld eines Kontos physisch liegt – z. B. Geldbeutel, "
  "Spardose, Portokasse oder ein Umschlag. Ein Konto «Bargeld» lässt sich damit in mehrere reale "
  "Aufbewahrungsorte aufteilen. <b>Diese Funktion gibt es in KMyMoney nicht</b>; sie ist eine Ergänzung "
  "dieser App für die Bargeld-Praxis.")
p("Orte sind dabei <b>nicht auf Bargeld-Konten beschränkt</b> – sie lassen sich für <b>jedes beliebige "
  "Konto</b> verwenden. In den folgenden Beispielen bleiben wir aber beim Bargeld.")
p("Erreichbar über <b>⋮ → Bestände</b>. Jeder Ort führt ein eigenes <b>Bewegungs-Journal</b>; sein Saldo "
  "ist die Summe seiner Bewegungen. Die Summe aller Ort-Salden entspricht dabei immer dem Kontosaldo: "
  "<b>«ohne Ort»</b> ist der automatisch berechnete Rest (Kontosaldo − Summe der übrigen Orte). So bleibt "
  "das Konto stimmig, egal wie viele Orte Sie anlegen.")
shot("Bestände.png", "Bestände: Anlage-/Verbindlichkeitskonten und Depot mit Orten je Konto", width=6.0*cm)
bullets([
  "Eine in der App angelegte Buchung erzeugt automatisch eine Bewegung auf dem <b>Standardort</b> des Kontos.",
  "Ort-Bewegungen lassen sich einzeln <b>anlegen, ändern und löschen</b>.",
  "Geld lässt sich <b>zwischen Orten umbuchen</b> (z. B. um eine importierte Buchung einem Ort zuzuordnen "
  "oder um Bargeld vom Umschlag in den Geldbeutel zu verschieben).",
  "Spätere Betrags- oder Lösch-Änderungen hängen datierte Ausgleichs-Bewegungen an – die Historie bleibt erhalten.",
])
h2("Ort bei alten (importierten) Buchungen")
p("Eine <b>exportierte und wieder importierte</b> Buchung verliert ihre Ort-Verknüpfung – das ist so "
  "gewollt. Öffnen Sie solch eine Buchung trotzdem <b>zum Bearbeiten</b> (langer Druck), wird das "
  "<b>Ort-Feld angezeigt</b>, sobald das Konto überhaupt Orte hat. So lässt sich ein Ort auswählen. Was "
  "damit passiert, hängt davon ab, welchen Knopf Sie drücken:")
bullets([
  "<b>«Neue Buchung»</b> (duplizieren): Es entsteht eine <b>neue</b> Buchung – der gewählte Ort wird "
  "<b>gespeichert</b> und in den Beständen als Bewegung angelegt. Das ist der Weg, einer alten Buchung "
  "nachträglich einen Ort zu geben.",
  "<b>«Buchung ändern»</b> bei einer Buchung, die <b>vorher keinen Ort</b> hatte und <b>bereits exportiert</b> "
  "ist: Der eingegebene Ort wird <b>ignoriert</b>, das Ort-Journal bleibt unberührt.",
  "<b>«Buchung ändern»</b> bei einer Buchung, die <b>vorher einen Ort</b> hatte: Der neue Ort wird "
  "<b>gespeichert</b> und per Ausgleichs-Bewegung nachgezogen (alter Ort −Betrag, neuer Ort +Betrag).",
])
p("In der reinen <b>Ansicht</b> (kurzer Tipp) bleibt das Ort-Feld bei importierten Buchungen wie bisher "
  "ausgeblendet. Bei <b>Umbuchungen</b> gilt dasselbe – dort gibt es Von-Ort und Nach-Ort, und die Bewegung "
  "wird auf <b>beiden Konten</b> angelegt.")
h2("Kassensturz auf Orts-Ebene")
p("Ein besonderer Vorteil: Der <b>Kassensturz</b> (Soll-Ist-Abgleich) lässt sich <b>pro Ort</b> "
  "durchführen. Sie zählen nur das Bargeld <i>eines</i> Ortes – etwa den Geldbeutel –, tragen den "
  "gezählten Betrag ein, und die App bucht automatisch die Differenz als Ausgleichs-Bewegung. Sie müssen "
  "also nicht das gesamte Konto auf einmal abstimmen; das <b>vereinfacht den Abgleich erheblich</b>. "
  "Importierte Buchungen tragen keine Ort-Verknüpfung.")
h2("Anlage- und Verbindlichkeitskonten")
p("Konten werden anhand ihres KMyMoney-Typs in <b>Anlagekonten</b>, <b>Verbindlichkeitskonten</b> "
  "(Kredite, Kreditkarten) und <b>Depots</b> gruppiert – mit farbcodierten Überschriften (Anlage = grün, "
  "Verbindlichkeit = rot, Depot = blau; helle Darstellung → hellere Farbe/schwarze Schrift, dunkle "
  "Darstellung → dunklere Farbe/weiße Schrift) sowohl in der Kontenschublade als auch in den Beständen. "
  "In den Beständen zeigt jede Kategorie-Überschrift rechtsbündig ihre <b>Kategoriesumme</b>; das Depot "
  "zählt als eine Zeile (Depotwert) zum <b>Gesamt</b> mit (in neutralem Grau/Schwarz). Die Einteilung wird "
  "beim Import aus der .kmy übernommen (nach einem erneuten Import sichtbar).")
h2("Depot (Wertpapiere)")
p("Nach dem Import eines KMyMoney-Investmentkontos erscheint das <b>Depot</b> in der Kontenschublade. Die "
  "Depot-Ansicht ist genau wie eine Konto-Ansicht aufgebaut – gleiche <b>Menüleiste</b> (Hamburger-Menü links, "
  "Depotname als Titel, rechts dieselben Menüpunkte wie bei einem normalen Konto) sowie Schublade und Filter – "
  "und zeigt je Wertpapier <b>Stückzahl × Kurs = aktueller Wert</b>; vollständig verkaufte Papiere werden "
  "ausgeblendet. Die <b>Saldenzeile schaltet per Tipp</b> durch Depotwert → Käufe → Verkäufe (falls "
  "vorhanden) → Dividenden (falls vorhanden) → <b>Nettoeinsatz</b> (Käufe − Verkäufe − Dividenden) → "
  "<b>Gewinn/Verlust</b> (farbig, mit Prozent). Der <b>Depot-Filter</b> grenzt die Wertpapiere nach Name "
  "und Wert (Schieberegler) ein. Wer weit nach unten scrollt, springt über den eingeblendeten "
  "<b>Nach-oben-Knopf</b> (unten links) direkt an den Anfang. Ein Tipp auf ein Wertpapier öffnet seine <b>Bewegungen im Vollbild</b> "
  "(Käufe grün, Verkäufe rot, Dividenden neutral) mit denselben Kennzahlen für dieses Papier und einem "
  "<b>Filter nach Käufen/Verkäufen/Dividenden</b> sowie einem Datums-Slider (Startdatum = erster Kauf). Der "
  "Depotwert wird getrennt geführt und erscheint zusätzlich als «Gesamtvermögen» in der Saldenzeile des "
  "Hauptbildschirms.")
p("Bei <b>Einbuchungen</b> und <b>Ausbuchungen</b> kennt KMyMoney nur die Stückzahl, keinen Geldwert. Ein "
  "<b>langer Klick</b> auf eine solche Zeile öffnet einen Dialog, um den Wert manuell einzutragen (über "
  "dieselbe Rechentastatur wie sonst im Betragsfeld) – er zählt danach wie ein Kauf bzw. Verkauf zum "
  "Einstandspreis und Gewinn/Verlust und übersteht auch einen erneuten Depot-Import.")
shot("Depot.png", "Depot-Ansicht: Wertpapiere mit Stückzahl, Kurs und aktuellem Wert", width=6.0*cm)
shot_row([
    ("Depot Buchungen.png", "Bewegungen eines Wertpapiers: Käufe, Verkäufe und Dividenden"),
    ("Depot Filtern.png", "Depot-Bewegungen filtern (Käufe/Verkäufe/Dividenden, Zeitraum)"),
])
p("Das Menü <b>«Auswertung»</b> öffnet ein <b>Kreisdiagramm</b> der Wertpapiere – im Design der Kategorien-"
  "Seite (durchgehender Ring, feste Farbe je Wertpapier, höchstens halbe Bildschirmhöhe, darunter eine eigene "
  "scrollbare Liste). Die Segmente sind unbeschriftet; ein Tipp zeigt in der Mitte <b>Name und Betrag</b> des "
  "Papiers, ohne Auswahl steht dort «Gesamt». Grafik und Liste sind <b>immer</b> absteigend nach dem "
  "aktuellen Wert des Zeitraums sortiert.")
shot("Depot Auswertung.png", "Depot-Auswertung: Symbol-Umschalter, Zeitraumfilter und Kreisdiagramm",
     width=6.0*cm)
p("Oben wählt ein <b>Symbol-Umschalter</b> die Ansicht: <b>Aktueller Wert</b> (Standard, €-Symbol) · "
  "<b>Netto-Einzahlungen</b> (Pfeil) · <b>Summe Dividenden</b> (%-Symbol) · <b>Gewinn/Verlust</b> "
  "(Trendpfeil). Darunter grenzt ein <b>Zeitraumfilter</b> die Auswertung ein – ein monatlicher "
  "Bereichsslider (erste Depot-Buchung bis heute) sowie zwei Datumsfelder <b>Von/Bis</b> für ein beliebiges "
  "Datum. Jede Änderung an Ansicht oder Zeitraum aktualisiert Grafik und Liste sofort.")
bullets([
  "<b>Aktueller Wert:</b> heutiger Wert (aktueller Kurs) der im Zeitraum <b>aufgebauten Positionen</b> – "
  "also der im Zeitraum gekauften, noch gehaltenen Stücke. Bei vollem Zeitraum entspricht das dem aktuellen "
  "Depotwert (im Zeitraum wieder verkaufte Stücke zählen nicht negativ).",
  "<b>Netto-Einzahlungen:</b> Käufe − Verkäufe − Dividenden innerhalb des Zeitraums.",
  "<b>Summe Dividenden:</b> alle im Zeitraum erhaltenen Dividenden (brutto/netto laut Einstellung).",
  "<b>Gewinn/Verlust:</b> Aktueller Wert − Netto-Einzahlungen, je Wertpapier als €-Betrag und als "
  "%-Rendite bezogen auf den Einstandspreis (Käufe) – in der Liste grün/rot gefärbt. Die "
  "Kreisdiagramm-Segmente behalten dabei die normale Wertpapierfarbe.",
])
p("Im <b>Menüstreifen</b> blendet ein Symbol <b>komplett verkaufte Wertpapiere</b> ein oder aus (Standard: "
  "ausgeblendet). Maßgeblich ist der Netto-Bestand <b>am Ende des gewählten Zeitraums</b> – ein Verkauf "
  "danach zählt also noch nicht als «komplett verkauft».")
p("Der <b>Export</b> im Depot-Menü läuft direkt im Depot (ohne Wechsel zur Bargeld-Ansicht).")
p("<b>Dividenden brutto/netto:</b> In den Einstellungen wählbar, ob Dividenden brutto (deklarierte Dividende) "
  "oder netto (gutgeschriebenes Geld nach Steuer) angezeigt und in der Saldenzeile (Dividenden, Nettoeinsatz, "
  "Gewinn/Verlust) verrechnet werden. Der Netto-Wert wird beim Depot-Import erfasst – für bestehende Daten ist "
  "einmalig ein Depot-Neuimport nötig.")

# ---------------------------------------------------------------- 11 Synchronisieren durchführen
h1("14. Synchronisieren: Export und Import durchführen")
p("Der eigentliche Abgleich mit KMyMoney läuft über <b>ein einziges Symbol</b> in der Titelleiste des "
  "Hauptbildschirms – das <b>Export-/Sync-Symbol</b> (Pfeil nach unten).")
pic("docs/img/export_button.png", "Das Export-/Sync-Symbol in der Titelleiste (rot umkreist)", width=14*cm)
h2("Buchungen exportieren")
p("Tippen Sie auf das Export-Symbol ⬇. Die App lädt die aktuelle .kmy vom Sync-Ziel, fügt Ihre neuen "
  "Buchungen ein, legt zuvor eine Sicherung an (siehe unten) und schreibt die Datei zurück. Jede Buchung "
  "wird nur <b>einmal</b> exportiert und danach als «exportiert» markiert. Im CSV-Modus wird stattdessen je "
  "Konto eine CSV-Datei hochgeladen.")
h2("Konten (neu) importieren")
p("Der Import erfolgt gezielt über die Kontenschublade (langer Druck):")
bullets([
  "<b>Langer Druck auf ein Konto</b>: importiert/aktualisiert genau dieses Konto aus der .kmy.",
  "<b>Langer Druck auf «Alle Konten»</b>: importiert <b>alle</b> vorhandenen Konten neu.",
  "<b>«Konto hinzufügen»</b>: holt ein noch nicht vorhandenes Konto aus der .kmy. Im Auswahldialog lassen "
  "sich <b>mehrere Konten (und Depots) auf einmal</b> anhaken; <b>bereits importierte Konten (auch "
  "geschlossene) und Depots werden ausgeblendet</b>.",
  "<b>Langer Druck auf das Depot</b> (oder Herunterziehen in der Depot-Ansicht): aktualisiert Wertpapiere "
  "und Kurse – ebenfalls im Hintergrund mit dem gelben Fortschrittsbanner.",
])
p("<b>Wertpapierkäufe und -verkäufe</b> (Geld vom Konto ins Wertpapier bzw. zurück) werden beim Import als "
  "<b>Umbuchung</b> ins bzw. aus dem Wertpapier eingelesen. So zählt nicht der ganze Kaufbetrag fälschlich "
  "als «Bankgebühren». Die eigentliche kleine <b>Gebühr</b> (oder Steuer), die in der Transaktion steckt, "
  "erscheint <b>nicht</b> in der Buchungsliste und ändert keine Salden – sie wird aber in den "
  "<b>Kategorien-Auswertungen</b> (Kapitel «Wofür geht mein Geld?» und Budget) korrekt als kleine Ausgabe "
  "geführt. <b>Dividenden</b> bleiben normale Einnahmen. Damit alte, bereits importierte Käufe umgestellt "
  "werden, das betroffene Konto einmal neu importieren.")
p("Ein Import ersetzt je Konto die bereits exportierten Buchungen (keine Dubletten). Der Import läuft "
  "<b>im Hintergrund</b> – die Oberfläche bleibt bedienbar; oben in der Buchungsliste zeigt ein <b>gelber "
  "Banner</b> («Konto wird importiert …») mit wanderndem Verlauf, Status und Prozentanzeige den Fortschritt "
  "und verschwindet am Ende. Eine Meldung kommt <b>nur bei einem Fehler</b>. Im <b>CSV-Modus mit "
  "SMB/WebDAV</b> öffnet sich beim Hinzufügen bzw. beim langen Druck ein <b>navigierbarer Ordner-Browser</b> "
  "(Unterordner 📁 + CSV-Dateien, «..» zurück); die gewählte CSV wird aus dem aktuellen Ordner importiert. "
  "Die Schublade bleibt beim langen Druck geöffnet.")
h2("Was der Fortschritt im Banner bedeutet")
p("Die Prozentanzeige bewegt sich während des gesamten Imports stetig voran (Herunterladen, Aufbereiten, "
  "Buchungen lesen, Speichern) statt in großen Sprüngen.")
p("Damit der Import zügig bleibt, liest die App die Datei <b>einmal für alle gewählten Konten</b> und "
  "speichert alles in einem Zug. Kurse, Budgets und geplante Buchungen stehen in der .kmy erst "
  "<b>hinter</b> den Buchungen – die App springt dorthin, statt für jeden dieser Bereiche noch einmal "
  "durch das gesamte Hauptbuch zu laufen.")
h2("Sicherung vor jedem Export")
p("Vor jedem Rückschreiben legt die App automatisch eine <b>zeitgestempelte Sicherung</b> der .kmy an "
  "(z. B. <i>datei.kmy.bak-JJJJMMTT-HHMMSS</i>). Die Sicherungen landen im <b>Unterordner «Backup»</b> neben "
  "dem Original; der Ordner wird beim ersten Mal automatisch angelegt. So haben Sie stets einen Fallback, "
  "falls mit der Datei einmal etwas nicht stimmt, und der eigentliche Ordner bleibt übersichtlich.")
h2("Schutz vor Überschreiben")
p("Zwischen dem Herunterladen der .kmy und dem Rückschreiben vergehen einige Sekunden. Arbeitet in dieser "
  "Zeit jemand am Rechner in KMyMoney, dürfen dessen Änderungen nicht verloren gehen. Die App merkt sich "
  "deshalb beim Herunterladen den <b>Stand der Datei</b> und schreibt nur, wenn er unverändert ist.")
p("Wurde die Datei zwischenzeitlich geändert, <b>bricht der Export ab und schreibt nichts</b>; die Buchungen "
  "bleiben unexportiert und können nach einem erneuten Import ohne Verlust nochmals exportiert werden. "
  "Liefert der Server keinen Stand, wird wie bisher ungeprüft geschrieben – der Export scheitert daran nicht.")

# ---------------------------------------------------------------- 12 Sync einrichten
# ---------------------------------------------------------------- 15 Wear OS
h1("15. Wear OS (Uhr)")
shot("Promo-UhranlagemitAlias.png", "Erfassung auf der Uhr", width=6.0*cm)
p("Mit der Uhren-App erfassen Sie eine Ausgabe per Sprache direkt am Handgelenk. Die Uhr nimmt nur den "
  "Text auf; die Verarbeitung und das Anlegen der Buchung passieren auf dem Handy (derselbe Parser).")
bullets([
  "<b>Drei Typ-Knöpfe</b>: Einnahme (grün), Umbuchung (gelb), Ausgabe (rot). Danach startet die Sprache.",
  "Der erkannte Text wird kurz mit «Abbrechen» angezeigt und sonst automatisch verarbeitet.",
  "<b>Stille Zifferneingabe</b>: über das Ziffern-Symbol einen Betrag lautlos eingeben (Standort-Auflösung am Handy).",
  "<b>Wear-Tile</b>: Schnellzugriff als Kachel.",
  "<b>Standardort-Saldo</b>: Unter den Knöpfen zeigen App und Tile den Saldo des Standardorts als "
  "«Ort: Saldo» (z. B. «Geldbeutel: 70,00 €»). Der Saldo wird automatisch aktualisiert. Kein "
  "Standardort gesetzt → Zeile ausgeblendet.",
  "<b>Grund statt Saldo</b>: Sind noch Buchungen <b>nicht übertragen</b>, steht an dieser Stelle statt des "
  "Saldos der <b>Grund</b> dafür – «Warten auf GPS» (der Standort wird noch aufgelöst), «Keine Verbindung "
  "zum Handy» oder «Wird übertragen…». Die Zeile darunter nennt weiterhin die Anzahl.",
  "<b>Konto/Ort wechseln</b>: Oberhalb von «Buchung erfassen» (mittig) schaltet ein grauer Wechsel-Knopf – "
  "in App und Tile – das angezeigte Konto bzw. dessen Orte durch; die nächste Buchung (Handy-Widget und Uhr) "
  "betrifft dann das gewählte Konto/den gewählten Ort. Bei einer Umbuchung ist das gewählte Konto das "
  "Von-Konto; das Nach-Konto ist das Standardkonto – außer das gewählte Konto ist selbst das Standardkonto, "
  "dann bleibt das Nach-Konto leer (am Handy manuell ergänzen). Nach kurzer Zeit springt die Auswahl "
  "automatisch auf den Standardort zurück.",
  "<b>Offline</b>: nicht übertragene Buchungen werden angezeigt und automatisch nachgereicht, sobald das "
  "Handy erreichbar ist – ohne Verlust und ohne Dopplung.",
])
p("Voraussetzung: Handy- und Uhren-App haben dieselbe Signatur (gleicher Schlüssel).", )

# ---------------------------------------------------------------- 16 Einstellungen (vollstaendige Referenz)
h1("16. Einstellungen (vollständige Referenz)")
p("Die Einstellungen erreichen Sie über das <b>Menü ⋮</b> oben rechts – Eintrag <b>«Einstellungen»</b> ganz "
  "unten. Beim ersten Start setzt der Willkommen-Assistent (Kapitel 2) bereits die wichtigsten Punkte; hier "
  "sind alle Felder in der Reihenfolge des Bildschirms beschrieben. Änderungen wirken mit <b>«Speichern»</b> "
  "(Sprache und Dunkelmodus sofort).")
shot_row([
    ("Einstellungen_1.png", "Sprache, Zahlenformat, Server-Verbindung"),
    ("Einstellungen_2.png", "Export-Modus, Standardkonto, Orte je Konto"),
    ("Einstellungen_3.png", "Darstellung, Sicherheit, Daten"),
])
h2("Sprache, Zahlenformat und Währung")
bullets([
  "<b>Sprache</b>: Deutsch/Englisch (weitere per Sprachdatei). Beim ersten Start nach dem Handy vorbelegt; "
  "die Wahl wirkt sofort. Über <b>«Sprachvorlage exportieren/hochladen»</b> lässt sich eine eigene "
  "Übersetzung als Datei pflegen.",
  "<b>Währung</b>: Standard-Währungskennzeichen für Konten ohne eigene Währung (beim .kmy-Import je Konto "
  "aus der Datei übernommen).",
  "<b>Zahlenformat</b>: «1.234,56», «1,234.56» oder «1234,56»/«1234.56» ohne Tausendertrennung; dazu der "
  "Schalter <b>«Währungskennzeichen anzeigen»</b>. Gilt für alle Beträge in App und Uhr; die Eingabe "
  "akzeptiert weiter Komma und Punkt, der Export bleibt formatstabil.",
  "<b>Dividenden brutto/netto</b>: ob Depot-Dividenden brutto (deklariert) oder netto verrechnet werden.",
  "<b>Budget app-intern</b>: das Budget-Ist aus dem Verlauf berechnen statt aus KMyMoney importieren "
  "(siehe Kapitel 11).",
])
h2("Verbindung zu Ihrem Server (SMB / WebDAV / Nextcloud)")
p("<b>Der wichtigste Einrichtungsschritt</b>, damit die App mit KMyMoney Daten austauscht. Wählen Sie den "
  "<b>Server-Typ</b> und tragen Sie den Zugang ein:")
bullets([
  "<b>Nextcloud</b>: Basis-URL des Servers + App-Passwort (Nextcloud → Sicherheit → App-Passwort).",
  "<b>WebDAV (generisch)</b>: vollständige DAV-Wurzel-URL, Auth per HTTP-Basic.",
  "<b>SMB/Samba</b>: «smb://Host/Freigabe» im Heimnetz; leerer Benutzer = Gast, Domäne als DOMÄNE\\Benutzer. SMB2/3.",
  "<b>«Verbindung testen»</b> prüft die Zugangsdaten.",
])
h2("Export-Modus")
bullets([
  "<b>.kmy-Modus</b>: schreibt neue Buchungen direkt in die KMyMoney-Datei (inkl. Splits und Umbuchungen) "
  "und importiert Konten/Buchungen sowie das Depot daraus. Über <b>«.kmy auswählen»</b> öffnet sich ein "
  "Datei-Browser (Unterordner, «..» zurück).",
  "<b>CSV-Modus</b>: exportiert je Konto eine CSV-Datei (jede Buchung nur einmal); Zielordner und "
  "Import-Ordner werden hier gesetzt.",
])
p("Ohne konfiguriertes Sync-Ziel wird lokal in einen selbst gewählten Ordner exportiert. Wie der Abgleich "
  "abläuft, steht in Kapitel 14.")
h2("Standardkonto und Orte")
bullets([
  "<b>Standardkonto</b>: Vorauswahl für neue Buchungen (auch für Widget und Uhr). Erst wählbar, wenn Konten "
  "vorhanden sind.",
  "<b>Orte je Konto</b>: pro Konto lassen sich Bargeld-<b>Orte</b> anlegen und ein <b>Standardort</b> "
  "festlegen (siehe Kapitel 13).",
])
h2("Alias-Namen")
p("Der Schalter <b>«Nachfrage»</b> steuert, ob die App nach dem Sprechen einen unbekannten Empfänger als "
  "Alias lernen möchte (bestehende Aliase gelten weiter). Über <b>«Alias-Namen verwalten»</b> legen Sie "
  "Aliase manuell an, ändern und löschen sie – mit gesprochenem Begriff, echtem Empfänger, Buchungsart, "
  "Konto, Kategorien und Standort (per <b>«Karte öffnen»</b> auf einer OpenStreetMap-Karte). Der Schalter "
  "<b>«bevorzugen»</b> (★) stellt einen Alias vor die Buchungssuche; derselbe Begriff darf auf mehrere per "
  "Standort unterschiedene Empfänger zeigen.")
h2("Darstellung")
bullets([
  "<b>Dunkelmodus</b>: Hell/Dunkel (wirkt sofort).",
  "<b>Erinnerung an geplante Buchungen</b>: erinnert einmal täglich an heute fällige geplante Buchungen "
  "(standardmäßig aus; siehe Kapitel 12).",
])
h2("Sicherheit & Datenschutz")
bullets([
  "<b>App-Sperre</b>: optional per Fingerabdruck, Gesicht, PIN, Muster oder Passwort – beim Start und bei "
  "Rückkehr aus dem Hintergrund.",
  "<b>Standort (GPS)</b>: standardmäßig <b>aus</b>. Aus = keine Berechtigungsabfrage, keine GPS-Notiz, keine "
  "Betrag-only-Erfassung, kein Alias-Standort. Die Position wird nur lokal genutzt, nie an einen Dienst "
  "gesendet.",
])
h2("Daten")
bullets([
  "<b>Alles exportieren</b> sowie <b>Backup/Restore</b> der Datenbank.",
  "<b>Konten verwalten (löschen/schließen)</b>: Eine <b>Mehrfachauswahl</b> listet alle Konten mit Status "
  "(Aktiv/Geschlossen). Unten stehen <b>Löschen</b> und (kontextabhängig) <b>Schließen/Öffnen</b> vor "
  "<b>Abbrechen</b>; <b>Schließen</b> nur bei Saldo 0, <b>Öffnen</b> nur bei geschlossenen Konten. Ein "
  "geschlossenes Konto erscheint nirgends mehr – nur in der Auswertung-Gesamtsicht zählt sein historischer "
  "Saldo weiter.",
  "<b>Daten zurücksetzen</b>: löscht die lokale Datenbank (Neustart wie beim ersten Mal – danach erscheint "
  "wieder der Willkommen-Assistent).",
])
h2("Haftungsausschluss")
p("Diese App wird ohne Gewähr bereitgestellt. Insbesondere gibt es keine Garantie, dass die .kmy-Datei "
  "nach einem Export vollständig gültig bleibt. Dank der automatischen Sicherung vor jedem Export haben Sie "
  "jedoch stets einen Fallback. Bewahren Sie zusätzlich eigene, regelmäßige Backups auf.", )

# CSV-Format Kurzreferenz
h1("17. CSV-Format (Export)")
p("Deutsch: Spaltentrenner «;», Dezimaltrennzeichen «,», Datum TT.MM.JJJJ, UTF-8. Splitbuchungen werden je "
  "Kategorie als eigene Zeile geschrieben.")
code = ("Datum;Empfänger;Konto;Typ;Betrag;Notiz;Kategorie<br/>"
        "29.06.2026;Metzgerei;Bargeld;Ausgabe;-7,30;Mittagessen;Lebensmittel")
story.append(Paragraph(code, S("code", fontName="DejaVu", fontSize=8.5, leading=12,
             backColor=LIGHT, borderPadding=6, textColor=colors.HexColor("#333333"))))

# ---- Titelseite: randloses Vollbild + Version/Stand im vorgesehenen gestrichelten Rahmen ----
# Rahmen-Position im Bild per Pixel-Analyse vermessen (grüne gestrichelte Box unten links,
# Bild ist im Seitenverhältnis auf randlosen A4-Druck ausgelegt): x 7,8–57,5 mm, y 6,2–34,5 mm von unten.
def cover_page(canvas, doc):
    canvas.saveState()
    canvas.drawImage(COVER_PATH_DE, 0, 0, width=A4[0], height=A4[1])
    box_x = 7.8*mm + 3*mm
    canvas.setFont("DejaVu-Bold", 13)
    canvas.setFillColor(colors.HexColor("#1b1b1b"))
    canvas.drawString(box_x, 34.5*mm - 9*mm, "Version 1.2")
    canvas.setFont("DejaVu", 10)
    canvas.setFillColor(GREY)
    canvas.drawString(box_x, 34.5*mm - 17*mm, "Stand: Juli 2026")
    canvas.restoreState()

# ---- Fußzeile mit Seitenzahl ----
def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("DejaVu", 8)
    canvas.setFillColor(GREY)
    canvas.drawString(2*cm, 1.2*cm, "Ausgaben · Benutzerhandbuch (Version 1.2)")
    canvas.drawRightString(A4[0]-2*cm, 1.2*cm, "Seite %d" % doc.page)
    canvas.restoreState()

# Zwischenüberschriften (h2) nicht allein am Seitenende: mit dem folgenden Flowable zusammenhalten,
# damit die Überschrift mit auf die nächste Seite wandert, wenn ihr Absatz dort beginnt.
def _keep_headings_with_next(flowables):
    out = []
    i, n = 0, len(flowables)
    while i < n:
        f = flowables[i]
        name = getattr(getattr(f, "style", None), "name", "")
        if name == "h2" and i + 1 < n:
            out.append(KeepTogether([f, flowables[i + 1]]))
            i += 2
            continue
        out.append(f)
        i += 1
    return out
story = _keep_headings_with_next(story)

doc = SimpleDocTemplate(OUT, pagesize=A4,
                        leftMargin=2*cm, rightMargin=2*cm, topMargin=1.8*cm, bottomMargin=1.8*cm,
                        title="Ausgaben – Benutzerhandbuch", author="Ausgaben")
doc.build(story, onFirstPage=cover_page, onLaterPages=footer)
print("OK ->", OUT)
