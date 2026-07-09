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

# Guillemets im Quelltext -> deutsche Anfuehrungszeichen im PDF (kollisionssicher gegen ASCII-").
_RLParagraph = Paragraph
def Paragraph(_t, *a, **k):
    if isinstance(_t, str):
        _t = _t.replace('\u00ab', '\u201e').replace('\u00bb', '\u201c')
    return _RLParagraph(_t, *a, **k)

# ---------------------------------------------------------------- Titelseite
story.append(Spacer(1, 4.5*cm))
story.append(Paragraph("Ausgaben", st_title))
story.append(Paragraph("Benutzerhandbuch", S("st2", fontName="DejaVu-Bold", fontSize=16, textColor=GREY, spaceAfter=18)))
story.append(Paragraph("Mobiles Ausgaben-/Haushaltsbuch für Android und Wear OS "
                       "mit KMyMoney-Anbindung", st_sub))
story.append(Spacer(1, 0.6*cm))
story.append(Paragraph("Version 1.1 &nbsp;·&nbsp; Stand: Juli 2026", st_sub))
story.append(Spacer(1, 0.3*cm))
story.append(Paragraph("Projekt: github.com/PerFermat/Ausgaben &nbsp;·&nbsp; Lizenz: GPL-3.0", st_note))
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
p("Alle folgenden Einstellungen erreichen Sie über das Zahnrad ⚙ oben rechts.")
shot("Einstellungen.png", "Einstellungen: Sprache, Server-Verbindung (SMB/WebDAV) und Export-Modus", width=6.0*cm)
h2("Sprache")
p("Beim ersten Start richtet sich die Sprache nach dem Handy (Deutsch → Deutsch, sonst Englisch). "
  "Änderbar jederzeit unter <i>Einstellungen → Sprache</i> (ganz oben).")
h2("Zahlenformat")
p("Ebenfalls im Sprache-Bereich lässt sich das <b>Zahlenformat</b> wählen: «1.234,56» (Tausenderpunkt, "
  "Komma), «1,234.56» (Tausenderkomma, Punkt) oder «1234,56»/«1234.56» ohne Tausendertrennung; dazu ein "
  "Schalter <b>«Währungskennzeichen anzeigen»</b>. Die Wahl gilt für alle Beträge in der App und auf der "
  "Uhr. Die Betragseingabe akzeptiert weiterhin Komma und Punkt; der CSV-/kMy-Export bleibt formatstabil.")
h2("Verbindung zu Ihrem Server einrichten (SMB oder WebDAV/Nextcloud)")
p("<b>Dies ist der wichtigste Einrichtungsschritt.</b> Damit die App mit KMyMoney Daten austauschen kann, "
  "muss sie auf den Ordner zugreifen, in dem Ihre gemeinsame <b>.kmy</b>-Datei (oder der Export-Ordner) "
  "liegt. Öffnen Sie die Einstellungen und wählen Sie den <b>Server-Typ</b>:")
bullets([
  "<b>SMB/Samba</b>: für eine Windows-/NAS-Freigabe im Heimnetz («smb://Host/Freigabe»).",
  "<b>WebDAV (generisch)</b> oder <b>Nextcloud</b>: für einen (Cloud-)WebDAV-Server.",
])
p("Tragen Sie dann URL/Freigabe, Benutzer und Passwort ein und prüfen Sie mit <b>«Verbindung testen»</b>. "
  "Wählen Sie außerdem den Export-Modus (<b>.kmy</b> oder CSV) und – im .kmy-Modus – über <b>«.kmy "
  "auswählen»</b> die Datei. Die vollständigen Details stehen in Kapitel 12.")
h2("Konten anlegen – zuerst erforderlich")
p("Legen Sie danach mindestens ein <b>Konto</b> an: in der Kontenschublade (☰ oben links) unten über "
  "<b>«Konto hinzufügen»</b>; im .kmy-Modus lassen sich die Konten direkt aus der KMyMoney-Datei "
  "importieren. <b>Wichtig:</b> Das <b>Standardkonto</b> und die davon abhängigen Einstellungen darunter "
  "(z. B. die Orte je Konto) lassen sich erst festlegen, <b>wenn bereits Konten vorhanden sind</b>.")
h2("App-Sperre (optional)")
p("In den Einstellungen können Sie die biometrische App-Sperre aktivieren (Fingerabdruck, Gesicht, PIN …).")

# ---------------------------------------------------------------- 3 Hauptbildschirm
h1("3. Der Hauptbildschirm")
p("Der Hauptbildschirm zeigt oben die grüne Titelleiste, darunter die <b>Saldenzeile</b> und dann "
  "die Liste der Buchungen. Unten rechts liegen die Aktionsknöpfe.")
shot("Hauptbildschirm.png", "Hauptbildschirm mit Titelleiste, Saldenzeile und Buchungsliste")
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

# ---------------------------------------------------------------- 4 Symbol-Referenz
h1("4. Symbol- und Bedienelement-Referenz")
p("Diese Übersicht erklärt jedes Symbol der App und was es tut.")

sym_rows = [
  ("☰", "Menü / Kontenschublade", "Oben links. Öffnet die seitliche Schublade mit allen Konten, Depots und «Konto hinzufügen»."),
  ("←", "Zurück", "Oben links in Unterseiten. Kehrt zum vorherigen Bildschirm zurück."),
  ("⬇", "Export / Synchronisieren", "Titelleisten-Symbol (Pfeil nach unten, Download-Stil). Startet den Abgleich: schreibt neue Buchungen in die .kmy bzw. lädt sie zum Sync-Ziel."),
  ("▽", "Filter", "Trichter-Symbol. Öffnet den Filter nach Empfänger, Kategorie und Betrag."),
  ("▮▮", "Auswertung", "Balkendiagramm-Symbol. Öffnet die grafische Auswertung."),
  ("⚙", "Einstellungen", "Zahnrad. Öffnet die Einstellungen."),
  ("⋮", "Weitere Menüpunkte", "Drei Punkte (Überlauf). Enthält u. a. «Bestände»."),
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
shot("Ausgaben.png", "Buchungs-Editor mit Typ-Umschalter, Betrag, Empfänger und Konto")
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

# ---------------------------------------------------------------- 7 Sprache & Standort
h1("7. Spracheingabe und Standort-Erfassung")
h2("Spracheingabe")
p("<b>Langer Druck</b> auf den ✚-Knopf öffnet die Spracheingabe. Sagen Sie z. B. «Frisör 20 €»: Die App "
  "sucht eine passende Vorlage (Empfänger, Konto, Kategorie, Buchungsart) und öffnet sie mit heutigem "
  "Datum und dem gesprochenen Betrag. Die Empfängersuche ist unscharf.")
h2("Nächstgelegener Empfänger")
p("Gibt es mehrere gleichnamige Empfänger (z. B. «REWE Ort 1» und «REWE Ort 2») und ist der Standort "
  "bekannt, wählt die App den <b>geografisch nächstgelegenen</b> – sowohl bei bestehenden Buchungen als "
  "auch bei Aliasen mit hinterlegten Koordinaten.")
h2("Nur den Betrag erfassen (Ziffernblock ⊞)")
p("Bei eingeschaltetem Standort erscheint unten das Ziffernblock-Symbol. Tippen Sie nur einen Betrag ein: "
  "Die App sucht am aktuellen Standort (100 m) eine passende Vorlage und zeigt den gefundenen Empfänger "
  "bereits vor dem Speichern an.")
h2("Alias-Namen (gelernte Zuordnungen)")
p("Ändern Sie beim Speichern den erkannten Empfänger, fragt die App, ob sie sich die Zuordnung als Alias "
  "merken soll – samt Konto, Kategorie und <b>Ort</b> (bei Umbuchungen Von-/Nach-Konto und Von-/Nach-Ort). "
  "So wird «Mama 100 €» künftig automatisch auf den echten Namen mit passendem Konto/Ort gebucht. Die "
  "Kategorie- und Ort-Felder im Alias-Formular verhalten sich wie im Buchungseditor (gruppierte Kategorie-"
  "Auswahl; ein Ortsfeld nur bei Konten mit Orten). Reihenfolge der Auflösung: <b>bevorzugte Aliase (★) → "
  "Buchungen → übrige Aliase</b>.")

# ---------------------------------------------------------------- 8 Liste & Filter
h1("8. Buchungsliste und Filter")
bullets([
  "<b>Langer Druck</b> auf eine Buchung öffnet sie zum Bearbeiten.",
  "<b>Herunterziehen</b> aktualisiert die Liste (Sync).",
  "Markierungen: «Split», → / ← (Umbuchung), «exportiert»; Farben rot/grün.",
  "<b>Filter</b> (Trichter ▽): nach Empfänger, Kategorie (als Baum), Betrag (Schieberegler) und "
  "<b>Datum von–bis</b> (Schieberegler in Monatsschritten; ein taggenaues Datum geben Sie direkt im Feld "
  "ein). Der Filter wirkt auf Liste und Auswertung. Bei Kategorie-Filter zeigt eine Splitbuchung nur den "
  "Teilbetrag der gewählten Kategorie.",
])

# ---------------------------------------------------------------- 9 Auswertung
h1("9. Auswertung")
shot("Grafik.png", "Auswertung als Balken- und Liniendiagramm", width=9.5*cm)
bullets([
  "Zeiträume: Tag / Woche / Monat / Jahr.",
  "Sichten: einzelnes Konto, Ort oder Gesamt.",
  "<b>Zoomen per Fingergeste</b>: waagerecht = Anzahl der Balken, senkrecht = Y-Achse.",
  "Geschlossene Konten zählen nur in der Gesamtsicht (historischer Saldo).",
])

# ---------------------------------------------------------------- 10 Bestände / Depot
h1("10. Bestände (Orte) und Depot")
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
bullets([
  "Eine in der App angelegte Buchung erzeugt automatisch eine Bewegung auf dem <b>Standardort</b> des Kontos.",
  "Ort-Bewegungen lassen sich einzeln <b>anlegen, ändern und löschen</b>.",
  "Geld lässt sich <b>zwischen Orten umbuchen</b> (z. B. um eine importierte Buchung einem Ort zuzuordnen "
  "oder um Bargeld vom Umschlag in den Geldbeutel zu verschieben).",
  "Spätere Betrags- oder Lösch-Änderungen hängen datierte Ausgleichs-Bewegungen an – die Historie bleibt erhalten.",
])
h2("Kassensturz auf Orts-Ebene")
p("Ein besonderer Vorteil: Der <b>Kassensturz</b> (Soll-Ist-Abgleich) lässt sich <b>pro Ort</b> "
  "durchführen. Sie zählen nur das Bargeld <i>eines</i> Ortes – etwa den Geldbeutel –, tragen den "
  "gezählten Betrag ein, und die App bucht automatisch die Differenz als Ausgleichs-Bewegung. Sie müssen "
  "also nicht das gesamte Konto auf einmal abstimmen; das <b>vereinfacht den Abgleich erheblich</b>. "
  "Importierte Buchungen tragen keine Ort-Verknüpfung.")
h2("Anlage- und Verbindlichkeitskonten")
p("Konten werden anhand ihres KMyMoney-Typs in <b>Anlagekonten</b>, <b>Verbindlichkeitskonten</b> "
  "(Kredite, Kreditkarten) und <b>Depots</b> gruppiert – mit gleich gestalteten Überschriften sowohl in der "
  "Kontenschublade als auch in den Beständen. In den Beständen zählt das Depot als eine Zeile (Depotwert) "
  "zum <b>Gesamt</b> mit. Die Einteilung wird beim Import aus der .kmy übernommen (nach einem erneuten "
  "Import sichtbar).")
h2("Depot (Wertpapiere)")
p("Nach dem Import eines KMyMoney-Investmentkontos erscheint das <b>Depot</b> in der Kontenschublade. Die "
  "Depot-Ansicht ist wie eine Konto-Ansicht aufgebaut (Schublade, Kopf mit Depotname, eigenes Menü, Filter) "
  "und zeigt je Wertpapier <b>Stückzahl × Kurs = aktueller Wert</b>; vollständig verkaufte Papiere werden "
  "ausgeblendet. Die <b>Saldenzeile schaltet per Tipp</b> durch Depotwert → Käufe → Verkäufe (falls "
  "vorhanden) → Dividenden (falls vorhanden) → <b>Nettoeinsatz</b> (Käufe − Verkäufe − Dividenden) → "
  "<b>Gewinn/Verlust</b> (farbig, mit Prozent). Der <b>Depot-Filter</b> grenzt die Wertpapiere nach Name "
  "und Wert (Schieberegler) ein. Ein Tipp auf ein Wertpapier öffnet seine <b>Bewegungen im Vollbild</b> "
  "(Käufe grün, Verkäufe rot, Dividenden neutral) mit denselben Kennzahlen für dieses Papier und einem "
  "<b>Filter nach Käufen/Verkäufen/Dividenden</b> sowie einem Datums-Slider (Startdatum = erster Kauf). Der "
  "Depotwert wird getrennt geführt und erscheint zusätzlich als «Gesamtvermögen» in der Saldenzeile des "
  "Hauptbildschirms.")
p("Das Menü <b>«Auswertung»</b> öffnet ein <b>Kreisdiagramm</b> der Wertpapiere (Anteil am Depotwert). Die "
  "Segmente sind unbeschriftet; ein Tipp zeigt in der Mitte <b>Name und Betrag</b> des Papiers, ohne Auswahl "
  "steht dort «Gesamt: &lt;Depotwert&gt;». Der <b>Export</b> im Depot-Menü läuft direkt im Depot (ohne Wechsel "
  "zur Bargeld-Ansicht).")
p("<b>Dividenden brutto/netto:</b> In den Einstellungen wählbar, ob Dividenden brutto (deklarierte Dividende) "
  "oder netto (gutgeschriebenes Geld nach Steuer) angezeigt und in der Saldenzeile (Dividenden, Nettoeinsatz, "
  "Gewinn/Verlust) verrechnet werden. Der Netto-Wert wird beim Depot-Import erfasst – für bestehende Daten ist "
  "einmalig ein Depot-Neuimport nötig.")

# ---------------------------------------------------------------- 11 Synchronisieren durchführen
h1("11. Synchronisieren: Export und Import durchführen")
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
  "<b>«Konto hinzufügen»</b>: holt ein noch nicht vorhandenes Konto aus der .kmy.",
  "<b>Langer Druck auf das Depot</b>: aktualisiert Wertpapiere und Kurse.",
])
p("Ein Import ersetzt je Konto die bereits exportierten Buchungen (keine Dubletten). Im <b>CSV-Modus mit "
  "SMB/WebDAV</b> öffnet sich beim Hinzufügen bzw. beim langen Druck ein <b>navigierbarer Ordner-Browser</b> "
  "(Unterordner 📁 + CSV-Dateien, «..» zurück); die gewählte CSV wird aus dem aktuellen Ordner importiert. "
  "Die Schublade bleibt beim langen Druck geöffnet.")
h2("Sicherung vor jedem Export")
p("Vor jedem Rückschreiben legt die App automatisch eine <b>zeitgestempelte Sicherung</b> der .kmy an "
  "(z. B. <i>datei.kmy.bak-JJJJMMTT-HHMM</i>) direkt neben dem Original. So haben Sie stets einen Fallback, "
  "falls mit der Datei einmal etwas nicht stimmt.")

# ---------------------------------------------------------------- 12 Sync einrichten
h1("12. Synchronisation einrichten (Einstellungen)")
p("Bevor Sie zum ersten Mal synchronisieren, hinterlegen Sie einmalig Server, Zugang und Export-Modus "
  "in den Einstellungen (abgebildet in Kapitel 2).")
h2("Server-Typ und Zugang")
bullets([
  "<b>Nextcloud</b>: Basis-URL des Servers + App-Passwort (Nextcloud → Sicherheit → App-Passwort).",
  "<b>WebDAV (generisch)</b>: vollständige DAV-Wurzel-URL, Auth per HTTP-Basic.",
  "<b>SMB/Samba</b>: «smb://Host/Freigabe»; leerer Benutzer = Gast, Domäne als DOMÄNE\\Benutzer. SMB2/3.",
  "<b>«Verbindung testen»</b> prüft die Zugangsdaten; im .kmy-Modus öffnet <b>«.kmy auswählen»</b> einen "
  "Datei-Browser mit Unterordnern und .kmy-Dateien – man navigiert in Unterordner und mit «..» zurück.",
])
h2("Export-Modus")
bullets([
  "<b>.kmy-Modus</b>: schreibt neue Buchungen direkt in die KMyMoney-Datei (inkl. Splits und Umbuchungen) "
  "und importiert Konten/Buchungen sowie das Depot daraus.",
  "<b>CSV-Modus</b>: exportiert pro Konto eine CSV-Datei; jede Buchung wird nur einmal exportiert.",
  "<b>Depot-Import</b>: liest Wertpapiere, Käufe/Verkäufe/Dividenden und den letzten Kurs.",
])
p("Ohne konfiguriertes Sync-Ziel wird lokal in einen selbst gewählten Ordner exportiert.")

# ---------------------------------------------------------------- 13 Alias-Verwaltung
h1("13. Alias-Namen verwalten")
p("Unter <b>Einstellungen → Alias-Namen</b>: Die automatische Nachfrage lässt sich abschalten (bestehende "
  "Aliase gelten weiter). Über <b>«Alias-Namen verwalten»</b> legen Sie Aliase manuell an, ändern und "
  "löschen sie – mit gesprochenem Begriff, echtem Empfänger, Buchungsart, Konto, Kategorien und Standort "
  "(per <b>«Karte öffnen»</b> auf einer OpenStreetMap-Karte setzbar). Der Schalter <b>«bevorzugen»</b> "
  "(★) stellt einen Alias vor die Buchungssuche. Derselbe gesprochene Begriff darf auf mehrere Empfänger "
  "zeigen, die dann per Standort unterschieden werden.")

# ---------------------------------------------------------------- 13 Wear OS
h1("14. Wear OS (Uhr)")
shot("UhranlagemitAlias.png", "Erfassung auf der Uhr", width=6.0*cm)
p("Mit der Uhren-App erfassen Sie eine Ausgabe per Sprache direkt am Handgelenk. Die Uhr nimmt nur den "
  "Text auf; die Verarbeitung und das Anlegen der Buchung passieren auf dem Handy (derselbe Parser).")
bullets([
  "<b>Drei Typ-Knöpfe</b>: Einnahme (grün), Umbuchung (gelb), Ausgabe (rot). Danach startet die Sprache.",
  "Der erkannte Text wird 10 Sekunden mit «Abbrechen» angezeigt und sonst automatisch verarbeitet.",
  "<b>Stille Zifferneingabe</b>: über das Ziffern-Symbol einen Betrag lautlos eingeben (Standort-Auflösung am Handy).",
  "<b>Wear-Tile</b>: Schnellzugriff als Kachel.",
  "<b>Standardort-Saldo</b>: Unter den Knöpfen zeigen App und Tile den Saldo des Standardorts als "
  "«Ort: Saldo» (z. B. «Geldbeutel: 70,00 €»). Das Handy sendet den Wert nur bei Änderung; die Uhr liest "
  "ihn beim Start und reagiert per Push – ohne Polling und ohne merklichen Akku-Mehrverbrauch. Kein "
  "Standardort gesetzt → Zeile ausgeblendet.",
  "<b>Offline</b>: nicht übertragene Buchungen werden angezeigt und automatisch nachgereicht, sobald das "
  "Handy erreichbar ist – ohne Verlust und ohne Dopplung.",
])
p("Voraussetzung: Handy- und Uhren-App haben dieselbe Signatur (gleicher Schlüssel).", )

# ---------------------------------------------------------------- 14 Sicherheit
h1("15. Sicherheit, Datenschutz & Einstellungen")
bullets([
  "<b>App-Sperre</b>: optional per Fingerabdruck, Gesicht, PIN, Muster oder Passwort – beim Start und bei "
  "Rückkehr aus dem Hintergrund.",
  "<b>Standort (GPS)</b>: standardmäßig <b>aus</b>. Aus = keine Berechtigungsabfrage, keine GPS-Notiz, keine "
  "Betrag-only-Erfassung, kein Alias-Standort. Die Position wird nur lokal genutzt, nie an einen Dienst gesendet.",
  "<b>Währungskennzeichen</b>: Standard; beim .kmy-Import je Konto aus der Datei übernommen.",
  "<b>Design</b>: Hell/Dunkel. <b>Backup/Restore</b> der Datenbank.",
  "<b>Konto löschen/schließen</b>: Ein Konto lässt sich <b>schließen</b>, wenn sein Saldo 0 ist (sonst nur "
  "löschen), und jederzeit wieder öffnen. Ein geschlossenes Konto erscheint nirgends mehr – nur in der "
  "Auswertung-Gesamtsicht zählt sein historischer Saldo weiter.",
])
h2("Haftungsausschluss")
p("Diese App wird ohne Gewähr bereitgestellt. Insbesondere gibt es keine Garantie, dass die .kmy-Datei "
  "nach einem Export vollständig gültig bleibt. Dank der automatischen Sicherung vor jedem Export haben Sie "
  "jedoch stets einen Fallback. Bewahren Sie zusätzlich eigene, regelmäßige Backups auf.", )

# CSV-Format Kurzreferenz
h1("16. CSV-Format (Export)")
p("Deutsch: Spaltentrenner «;», Dezimaltrennzeichen «,», Datum TT.MM.JJJJ, UTF-8. Splitbuchungen werden je "
  "Kategorie als eigene Zeile geschrieben.")
code = ("Datum;Empfänger;Konto;Typ;Betrag;Notiz;Kategorie<br/>"
        "29.06.2026;Metzgerei;Bargeld;Ausgabe;-7,30;Mittagessen;Lebensmittel")
story.append(Paragraph(code, S("code", fontName="DejaVu", fontSize=8.5, leading=12,
             backColor=LIGHT, borderPadding=6, textColor=colors.HexColor("#333333"))))

# ---- Fußzeile mit Seitenzahl ----
def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("DejaVu", 8)
    canvas.setFillColor(GREY)
    canvas.drawString(2*cm, 1.2*cm, "Ausgaben · Benutzerhandbuch (Version 1.1)")
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
doc.build(story, onFirstPage=footer, onLaterPages=footer)
print("OK ->", OUT)
