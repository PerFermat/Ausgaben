# -*- coding: utf-8 -*-
"""Builds the English user manual for Ausgaben as a PDF.
Content mirrors manual_de.py. German double quotes are written as guillemets («») in the
source and converted to English curly quotes at build time (collision-safe vs. ASCII ")."""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm, mm
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, PageBreak,
                                Table, TableStyle, Image, KeepTogether)
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.utils import ImageReader

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(REPO, "screenshots")
OUT = os.path.join(REPO, "docs", "Manual-Ausgaben-en.pdf")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

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

st_title  = S("t",  fontName="DejaVu-Bold", fontSize=26, leading=30, textColor=GREEN, spaceAfter=6)
st_sub    = S("s",  fontSize=13, leading=17, textColor=GREY, spaceAfter=2)
st_h1     = S("h1", fontName="DejaVu-Bold", fontSize=16, leading=20, textColor=GREEN, spaceBefore=16, spaceAfter=6)
st_h2     = S("h2", fontName="DejaVu-Bold", fontSize=12.5, leading=16, textColor=colors.HexColor("#1b4d1e"),
              spaceBefore=10, spaceAfter=3)
st_body   = S("b",  fontSize=10, leading=14.5, spaceAfter=5, alignment=TA_LEFT)
st_bullet = S("bu", fontSize=10, leading=14.5, leftIndent=14, bulletIndent=2, spaceAfter=2)
st_cell   = S("c",  fontSize=9, leading=12)
st_cellb  = S("cb", fontName="DejaVu-Bold", fontSize=9, leading=12)
st_sym    = S("sy", fontName="DejaVu-Bold", fontSize=13, leading=14, alignment=TA_CENTER)
st_cap    = S("cap", fontSize=8.5, leading=11, textColor=GREY, alignment=TA_CENTER, spaceBefore=3)
st_note   = S("n",  fontSize=9, leading=13, textColor=GREY)

story = []
_first_h1 = [True]

# Headings get an invisible anchor (<a name=…/>) so the table of contents can link to them;
# the _toc attribute is what reports the entry to the contents page later on.
_bm = [0]
def _heading(t, style, level):
    _bm[0] += 1
    key = "sec%d" % _bm[0]
    para = Paragraph('<a name="%s"/>%s' % (key, t), style)
    para._toc = (level, t, key)
    return para

def h1(t):
    if _first_h1[0]:
        _first_h1[0] = False
    else:
        story.append(PageBreak())
    story.append(_heading(t, st_h1, 0))
def h2(t): story.append(_heading(t, st_h2, 1))
def p(t):  story.append(Paragraph(t, st_body))
def bullets(items):
    for it in items:
        story.append(Paragraph(it, st_bullet, bulletText="•"))
    story.append(Spacer(1, 4))
def gap(h=6): story.append(Spacer(1, h))

def shot(fname, caption, width=6.0*cm):
    path = os.path.join(SHOTS, fname)
    ir = ImageReader(path); iw, ih = ir.getSize()
    img = Image(path, width=width, height=width*ih/iw)
    tbl = Table([[img],[Paragraph(caption, st_cap)]], colWidths=[width])
    tbl.setStyle(TableStyle([("ALIGN",(0,0),(-1,-1),"CENTER")]))
    tbl.hAlign = "CENTER"
    story.append(KeepTogether([tbl])); gap(6)

def pic(relpath, caption, width=14*cm):
    path = os.path.join(REPO, relpath)
    ir = ImageReader(path); iw, ih = ir.getSize()
    img = Image(path, width=width, height=width*ih/iw); img.hAlign = "CENTER"
    box = Table([[img],[Paragraph(caption, st_cap)]], colWidths=[width])
    box.setStyle(TableStyle([("ALIGN",(0,0),(-1,-1),"CENTER")]))
    box.hAlign = "CENTER"
    story.append(KeepTogether([box])); gap(6)

def shot_row(items, max_width=6.0*cm, total_width=17*cm, hgap=0.4*cm):
    """Several screenshots side by side (instead of stacked), each with its own caption.
    Every image gets at most max_width; if there isn't enough room, all are shrunk together."""
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

# Guillemets in the source -> English curly quotes in the PDF (collision-safe vs. ASCII ").
_RLParagraph = Paragraph
def Paragraph(_t, *a, **k):
    if isinstance(_t, str):
        _t = _t.replace('«', '“').replace('»', '”')
    return _RLParagraph(_t, *a, **k)

# ---------------------------------------------------------------- Cover
# Borderless full-bleed cover (v3): the graphic already contains the full title text; only
# version/date are drawn onto the dashed placeholder box (see cover_page() further below). Page 1
# stays empty as far as flowables go, then straight on to chapter 1.
COVER_PATH_EN = os.path.join(SHOTS, "Handbuch Titelseite-eng.png")
story.append(PageBreak())

# ---------------------------------------------------------------- Table of contents
# Right after the cover; entries are clickable (jump to the chapter), the page numbers are
# filled in by the second pass of multiBuild.
toc = TableOfContents()
toc.levelStyles = [
    S("toc0", fontName="DejaVu-Bold", fontSize=10.5, leading=15, textColor=GREEN,
      spaceBefore=5, firstLineIndent=0, leftIndent=0, rightIndent=14),
    S("toc1", fontSize=9.5, leading=12.5, textColor=colors.HexColor("#333333"),
      firstLineIndent=0, leftIndent=16, rightIndent=14),
]
toc.dotsMinLevel = 0
story.append(Paragraph("Contents", st_h1))
story.append(toc)
story.append(PageBreak())

# ---------------------------------------------------------------- 1
h1("1. What is Ausgaben?")
p("<b>Ausgaben</b> (German for «expenses») is a mobile companion to the desktop finance software "
  "<b>KMyMoney</b>. Use it to record cash expenses, income and transfers on the go – right on your "
  "phone or a Wear OS watch – and transfer them into KMyMoney later, instead of typing everything in "
  "by hand.")
p("The app works fully offline and without any vendor account: data is exchanged with KMyMoney through "
  "a folder you provide yourself (Nextcloud, WebDAV or SMB/Samba). There are no ads and no tracking.")
h2("Key terms")
bullets([
  "<b>Booking</b>: a single money event (expense, income or transfer).",
  "<b>Account</b>: e.g. «Cash», «Checking». Every booking belongs to an account.",
  "<b>Place (holding)</b>: <i>where</i> an account's cash physically is (wallet, jar, envelope …).",
  "<b>Category</b>: what the money was spent on or received for (e.g. «Groceries»).",
  "<b>Alias</b>: a learned mapping «spoken term → correct payee» together with account/category.",
  "<b>.kmy</b>: KMyMoney's file format that is exported to and imported from.",
])

# ---------------------------------------------------------------- 2
h1("2. Getting started")
h2("Welcome assistant on first start")
p("As long as <b>no account</b> exists yet, a slim <b>welcome assistant</b> appears automatically when the "
  "app starts. It bundles the most important starting points in one place and can only be opened "
  "automatically (no menu entry).")
shot_row([
    ("Willkommen_1.png", "Language, format and synchronisation"),
    ("Willkommen_2.png", "«Import accounts», «More settings», «Done»"),
])
bullets([
  "Choose the <b>language</b> (takes effect immediately).",
  "<b>Import/export format</b>: CSV or KMyMoney file (.kmy).",
  "<b>Synchronisation</b>: server type, URL, user, password – with <b>«Test connection»</b>; in .kmy mode "
  "also <b>«Choose .kmy»</b>.",
  "<b>«Import accounts»</b>: the same selection dialog as «Add account», right here in the assistant.",
  "<b>«More settings»</b> opens the full settings; <b>«Done»</b> closes the assistant.",
])
p("<b>Important – an account first:</b> create at least one <b>account</b> – in the assistant via "
  "<b>«Import accounts»</b> or later in the account drawer (☰ top left) via <b>«Add account»</b>. The "
  "<b>default account</b> and the options that depend on it (e.g. places per account) can only be set "
  "<b>once accounts already exist</b>.")
p("All settings in detail – language, number format, currency, server connection, export mode, default "
  "account, alias names, app lock, backup and more – are explained in the <b>«Settings»</b> chapter at the "
  "end of the manual (Chapter 16). They are reachable any time via the <b>⋮ menu</b> at the top right, "
  "entry <b>«Settings»</b> at the very bottom.")

# ---------------------------------------------------------------- 3
h1("3. The main screen")
p("The main screen shows the green title bar at the top, the <b>balance line</b> below it, and then the "
  "list of bookings. The action buttons are at the bottom right.")
shot("Kontobuchungen.png", "Main screen with title bar, balance line and booking list")
h2("Balance line (tap to cycle)")
p("The coloured line below the title bar shows a balance. <b>Tapping</b> it cycles through, in order:")
bullets([
  "<b>Account</b>: balance of the currently selected account (only when a single account is selected).",
  "<b>Total</b>: sum of all accounts.",
  "<b>Net worth</b>: all accounts + current portfolio value (only after a portfolio was imported).",
  "<b>Places</b>: balances of the account's individual cash places.",
  "<b>Filtered</b>: sum of the currently filtered bookings (only when a filter is active).",
])
p("A green amount = positive/credit, a red amount = negative.")
h2("Home-screen widget")
p("For the launcher there are four selectable widgets showing the <b>default-place</b> balance "
  "(default account → default place): <b>small</b> (2×1, balance only), <b>medium</b> (4×2, balance + three "
  "quick actions: booking, voice, amount), <b>large</b> (4×4, a balance header with refresh, the most recent "
  "bookings and the action bar) and <b>type</b> (4×2, like on the watch: three coloured income/transfer/"
  "expense icon buttons + a grey switch button, with the balance below). Tapping the balance opens the app; "
  "the medium/large widget buttons launch «New booking», voice entry, amount-only entry or Holdings. On the "
  "<b>type widget</b> a type button <b>starts speech recognition immediately</b> and creates the booking "
  "directly – the app is not opened (only the system voice dialog appears); the grey <b>switch button</b> "
  "cycles through the chosen account and its places (the balance and the target of the new booking follow the "
  "selection). The widget refreshes when the app is opened and at regular intervals.")
shot("Widget.png", "All four widget sizes: medium, type, large and small", width=6.0*cm)

# ---------------------------------------------------------------- 4
h1("4. Symbol and control reference")
p("This overview explains every symbol in the app and what it does.")
sym_rows = [
  ("☰", "Menu / account drawer", "Top left. Opens the side drawer with all accounts, portfolios and «Add account»."),
  ("←", "Back", "Top left on sub-screens. Returns to the previous screen."),
  ("⬇", "Export / synchronize", "Title-bar symbol (down arrow, download style). Starts the sync: writes new bookings into the .kmy or uploads them to the sync target."),
  ("▽", "Filter", "Funnel symbol. Opens the filter by payee, category and amount."),
  ("▮▮", "Analysis", "Bar-chart symbol. Opens the graphical analysis."),
  ("⋮", "More items", "Three dots (overflow). Contains «Categories», «Holdings», «Budget», «Scheduled» and – <b>at the very bottom</b> – «Settings»."),
  ("✚", "New booking", "Round button at the bottom right. Short tap: record a booking. <b>Long press: voice input.</b>"),
  ("⊞", "Silent amount entry", "Keypad symbol (only when location is enabled). Type just the amount; the payee is resolved via your location."),
  ("↑", "To top", "Small arrow. Jumps to the top of the booking list."),
  ("★", "Preferred alias", "Marks a preferred alias in the alias list (considered before bookings)."),
  ("→", "Alias mapping", "In the alias list: «spoken term → correct payee»."),
  ("→ / ←", "Transfer", "In the booking list: → outgoing, ← incoming transfer (shows the counter-account/payee)."),
  ("Split", "Split booking", "Marks a booking that is split across several categories."),
  ("exported", "Exported", "Small green hint: this booking has already been exported."),
  ("● red/green", "Amount colour", "Red amount = expense/negative, green amount = income/positive."),
]
data = [[Paragraph("Symbol", st_cellb), Paragraph("Name", st_cellb), Paragraph("Meaning / function", st_cellb)]]
for sym, name, desc in sym_rows:
    data.append([Paragraph(sym, st_sym), Paragraph(name, st_cellb), Paragraph(desc, st_cell)])
tbl = Table(data, colWidths=[2.1*cm, 3.3*cm, 10.4*cm], repeatRows=1)
tbl.setStyle(TableStyle([
    ("BACKGROUND",(0,0),(-1,0),GREEN), ("TEXTCOLOR",(0,0),(-1,0),colors.white),
    ("FONTNAME",(0,0),(-1,0),"DejaVu-Bold"), ("VALIGN",(0,0),(-1,-1),"MIDDLE"),
    ("ALIGN",(0,0),(0,-1),"CENTER"), ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, LIGHT]),
    ("GRID",(0,0),(-1,-1),0.4,colors.HexColor("#cccccc")),
    ("TOPPADDING",(0,0),(-1,-1),4), ("BOTTOMPADDING",(0,0),(-1,-1),4),
]))
story.append(tbl)
story.append(Paragraph("Note: the title-bar symbols come from the Material Design set; their meaning is "
    "described above. Depending on space, some items appear in the «⋮» overflow menu.", st_note))

# ---------------------------------------------------------------- 5
h1("5. Accounts and the account drawer")
shot("Kontenmenü.png", "Account drawer with accounts, portfolio and «Add account»", width=6.0*cm)
bullets([
  "Open it via the ☰ symbol at the top left, or by swiping in from the left edge.",
  "<b>«All accounts»</b> shows all bookings together; a single account filters the list to it.",
  "<b>Long press on an account</b>: import/update that account from the .kmy.",
  "<b>Long press on «All accounts»</b> – or pull down in that view: reloads <b>everything</b> "
  "(accounts, portfolios and scheduled transactions) in one pass.",
  "<b>Portfolios</b> sit at the bottom under the «Portfolios» heading and behave just like accounts: a short tap opens the portfolio view (the selected portfolio stays highlighted), a long press updates it.",
  "<b>«Add account»</b> (at the bottom): loads the .kmy and offers the contained accounts for selection.",
])
h2("Account groups")
p("Besides the <b>account kind</b> (asset, liability, portfolio) there are <b>account groups</b> for your "
  "own ordering – «Favourites», «Joint» or «Savings», say. An account may sit in any number of groups; the "
  "grouping by account kind is unaffected.")
bullets([
  "<b>Tap the «Accounts» heading</b> to pick a group. The drawer then lists only its accounts, and the top "
  "row carries the group name instead of «All accounts».",
  "The chosen group applies <b>app-wide</b>: the booking list, the balance bar (as an additional first "
  "page there; «Total» still means all accounts) and the holdings view all follow it.",
  "<b>Long-press a group</b> in that picker to delete it; the accounts themselves are kept.",
  "Removing the <b>last account</b> from a group (or deleting that account) makes the group disappear "
  "without asking – there are no empty groups.",
  "Groups holding nothing but <b>closed</b> accounts no longer show up in this picker. They stay "
  "selectable in an account's «⋮» menu so they can be revived.",
  "The institutions block of the .kmy additionally yields <b>bank groups</b> (e.g. «Volksbank»). These "
  "merely mirror the file: only the import writes them, they cannot be edited by hand.",
])
h2("Searching accounts")
p("A <b>magnifier</b> sits on the kMyMoney emblem at the top of the drawer. Tapping it opens the keyboard, "
  "and for the duration of the search your input takes the place of the «Accounts» heading. What you type "
  "filters the account list instantly by part of the name – «kasse» thus also finds «Sparkasse» and "
  "«Barkasse».")
p("Closing the keyboard puts the heading back in its place – but the term <b>stays in effect</b>, so the "
  "list still shows only the matches. The magnifier now carries a small cross to signal that a filter is "
  "on; tapping it lifts the search.")
p("The search stays inside the chosen account group and is never stored. It also ends by itself as soon as "
  "you pick an account, close the drawer or leave the app.")
h2("Sorting and managing accounts")
p("The <b>gear icon</b> next to «Accounts» opens a view of its own. Unlike the drawer it also shows "
  "<b>closed accounts</b>, in grey, each with its balance on the right.")
bullets([
  "<b>Drag the handle</b> to sort accounts within their account kind. The order applies everywhere "
  "accounts are listed – including the pickers while recording a booking.",
  "<b>Drag a whole account kind</b>: its accounts collapse for the duration of the drag and only their "
  "number is shown. That way even a large block reaches its place in a few movements.",
  "<b>«⋮» on an account row</b>: add to or remove from groups, create a «New group …», plus «Close "
  "account» and «Reopen account». <b>Close account</b> is only offered at a zero balance.",
  "If a closed account regains a balance on import, it reopens by itself.",
])

# ---------------------------------------------------------------- 6
h1("6. Recording a booking")
p("Tap the round <b>✚</b> button. The booking editor opens.")
shot("Buchung Empfänger.png", "Booking editor with type switch, amount, payee and account")
h2("Arithmetic in the amount field")
p("The <b>amount field</b> accepts a small <b>calculation</b> instead of the system keyboard (e.g. "
  "<i>10+20*3</i> or <i>30-5</i>) – handy when splitting a bill or offsetting several items.")
h2("Fields and controls")
bullets([
  "<b>Type switch</b>: <font color='#b00020'>Expense</font> · <b>Transfer</b> · <font color='#2e7d32'>Income</font>. "
  "Sets the kind of booking.",
  "<b>Amount</b>, <b>payee</b>, <b>account</b> (chosen from existing accounts).",
  "<b>Date</b>: pre-filled with today; the <b>«Today»</b> button quickly sets the current date.",
  "<b>Note</b>: free text. With location enabled it automatically contains «GPS: lat, lon».",
  "<b>Place</b> (only for expenses/income created in the app): which cash place the booking is credited to.",
  "<b>«Exported» toggle</b>: marks whether the booking has already been exported.",
])
h2("Saving")
bullets([
  "<b>Save as new</b>: creates a new booking.",
  "<b>Update</b>: saves changes to an existing booking.",
  "<b>Delete</b>: removes the booking.",
])
p("<b>Date prompt when copying:</b> if you open an existing booking, leave the date unchanged and «save "
  "as new», the app asks whether the old date or today should apply.")
h2("Split booking (several categories)")
p("In the «Split categories» section you can enter several categories with partial amounts. With one "
  "category the total and category amount are linked; with several, the sum must equal the total. Partial "
  "amounts may be negative.")
h2("Transfer")
p("Choose the type «Transfer», then the From and To accounts plus an optional payee. This creates a linked "
  "booking in both accounts (marked → or ← in the list). You can also choose a <b>from-place</b> and a "
  "<b>to-place</b>; then the respective place journal is updated (from-account −amount, to-account +amount). "
  "Editing or deleting the transfer rolls the place movements back.")
shot("Buchung Umbuchung.png", "Detail view of a transfer with balance before/after", width=6.0*cm)

# ---------------------------------------------------------------- 7
h1("7. Voice input and location capture")
h2("Voice input")
p("A <b>long press</b> on the ✚ button opens voice input. Say e.g. «barber 20 €»: the app looks for a "
  "matching template (payee, account, category, booking type) and opens it with today's date and the "
  "spoken amount. Payee search also finds similarly spelled names.")
h2("Nearest payee")
p("If there are several payees with the same name (e.g. «REWE location 1» and «REWE location 2») and the "
  "location is known, the app picks the <b>geographically nearest</b> one – both for existing bookings and "
  "for aliases with stored coordinates.")
h2("Amount-only entry (keypad ⊞)")
p("With location enabled, the keypad symbol appears at the bottom. Type just an amount: the app looks for a "
  "matching template at your current location and shows the resolved payee before you even save.")
shot("Buchung Betrag - Automatischer Empfänger anhand GPS.png",
     "Amount-only entry: the payee is resolved via the current location", width=6.0*cm)
h2("Receipt photos")
p("In the booking editor the <b>«Receipt»</b> button lets you <b>take a photo</b> of the receipt or <b>pick "
  "one from the gallery</b> (one photo per booking; can be turned off under <i>Settings → Receipt photos</i>). "
  "The image is stored privately on the phone and uploaded <b>in the background</b> to a «Belege» folder – with "
  "a sub-folder per year – inside your configured sync folder (WebDAV/Nextcloud or SMB); photos taken offline "
  "upload on the next app start with a connection.")
p("The reference to the image is stored in the note as a <b>«BELEG: …»</b> tag, just like the GPS coordinates. "
  "Because the note survives export/import with KMyMoney, the app finds the photo again <b>even after a "
  "re-import</b>. In the editor the note field shows only your free text; <b>GPS and the receipt appear as two "
  "separate read-only rows below it</b>, each with an icon – the map icon opens the location, the receipt icon "
  "opens the photo (view) or the camera/gallery chooser (edit).")
h2("Multi-page receipts")
p("A booking can hold <b>several pages</b> – for the second half of a till roll or an invoice spanning "
  "multiple sheets. The camera icon in the receipt row <b>appends another page</b> every time; while "
  "editing, each page gets its own line with crop and delete. When merely <b>viewing</b> a booking one line "
  "is enough: the page count on the left, a single image icon on the right that opens the viewer.")
p("The <b>viewer</b> is part of the app itself. <b>Swiping</b> pages through the receipt (the header counts "
  "along: «Page 2 of 3»), <b>pinching</b> and a <b>double tap</b> zoom in to read the small print; another "
  "double tap fits the image back in. No external photo app is called on purpose: those remember images by "
  "file name and would still show the pre-edit version from their cache.")
p("All pages of a booking share <b>the same UUID</b> and differ only by the suffix «_p1», «_p2» and so on. "
  "The note – and therefore the KMyMoney memo – only holds <b>«BELEG: &lt;uuid&gt;»</b>; the app looks for "
  "the pages itself, first in the phone's receipt folder, then on the network share. Older receipts without "
  "a page suffix count as page 1 and are not renamed; a new shot added to them becomes page 2.")
p("The images are uploaded into a <b>«Belege»</b> folder with one subfolder per year. It sits <b>next to the "
  "KMyMoney file</b> (in CSV mode inside the configured sync folder) – the same place the «Backup» folder "
  "appears in. The year folder follows the <b>booking date</b>; move a booking across new year and its "
  "images follow on the server. After switching phones the app fetches each receipt from there when you "
  "open the booking.")
p("Delete a booking and its images disappear on the <b>next app start</b> – not right away, so that the "
  "«Undo» offered straight after deleting still brings the receipt back.")
p("If you delete a page in the middle, the following ones move up when you save, so the numbering stays "
  "gapless. Even deleting <b>page 1</b> keeps the booking linked to its receipt – the former page 2 simply "
  "becomes page 1.")
h2("Cropping and straightening receipt photos")
p("Right after the shot the app asks whether you want to <b>edit</b> the image. <b>«Keep as is»</b> leaves the "
  "photo untouched – so you never have to bother. Later you can reopen the editor at any time via the "
  "<b>crop icon</b> in the receipt row.")
p("In the editor you drag the <b>four corners</b> onto the bill. There are two modes: <b>«Rectangle»</b> cuts "
  "straight, leaving just a section of the image; <b>«Trapezoid»</b> lets you place each corner freely and then "
  "<b>straightens</b> a skewed photo into a clean rectangle. Two sliders adjust <b>brightness</b> and "
  "<b>contrast</b> – handy for faded till receipts. The preview follows every change immediately, and "
  "<b>«Reset»</b> restores the starting state.")
p("The <b>untouched original</b> is not lost: on «Apply» the app keeps a separate file carrying "
  "<b>«_original»</b> in its name and uploads it as well. That backup stays <b>for good</b> – even after "
  "several rounds of editing the starting image is still there. Deleting the receipt removes the original too.")
p("So when you edit an image <b>a second time</b>, the app asks: <b>«Continue editing»</b> works on the "
  "current version – handy to nudge the brightness without losing your crop. <b>«Start from original»</b> "
  "brings back the untouched photo when a crop turned out too tight. For an image never edited before, the "
  "question does not appear.")
h2("Alias names (learned mappings)")
p("If you change the recognized payee while saving, the app asks whether to remember the mapping as an "
  "alias – together with account, category and <b>place</b> (for transfers the from/to accounts and "
  "from/to places). So «mom 100 €» is later booked automatically to the real name with the right "
  "account/place. The category and place fields in the alias form behave like the booking editor (grouped "
  "category picker; a place field only for accounts that have places). Resolution order: <b>preferred "
  "aliases (★) → bookings → remaining aliases</b>.")
p("An alias can hold <b>any number of locations</b> (e.g. several branches). In the alias form, "
  "«Add coordinate» adds a row with latitude/longitude and «Open map»; the minus button removes a coordinate. "
  "When resolving (amount-only/voice) the alias matches if the current location is near <b>any</b> of its "
  "coordinates. Re-learning an alias from a booking at another place <b>appends</b> the coordinate (it does "
  "not overwrite).")
shot("Aliase.png", "Managing alias names: spoken term → real payee", width=6.0*cm)

# ---------------------------------------------------------------- 8
h1("8. Booking list and filter")
bullets([
  "<b>Short tap</b> on a booking opens it as a read-only view (same layout as the editor, but without any "
  "editing): a large coloured heading shows the type (income = green, transfer = yellow, expense = red), and "
  "the balance before and after the booking is shown at the bottom. <b>Long press</b> opens it for editing.",
  "In the list the note is truncated to at most two lines; in the editor the note field spans four lines.",
  "<b>Pull down</b> (in an account or portfolio view): in the <b>kmy variant</b> the .kmy file is re-read "
  "and that account or portfolio is refreshed. In the <b>CSV variant</b> this is not available – there you "
  "refresh an account via the account menu (long-press the account in the drawer).",
  "Markers: «Split», → / ← (transfer), «exported»; colours red/green.",
  "<b>Filter</b> (funnel ▽): with a <b>search field</b> that searches <b>payee, note and category</b> "
  "(substring, case-insensitive – so you can find a booking by its note as well); plus category (as a tree), "
  "amount (slider) and <b>date from–to</b> (slider in whole-month steps; type an exact day into the field). "
  "The filter applies to the list and the analysis. With a category filter a split booking shows only the "
  "partial amount of the chosen category.",
  "<b>Undo</b>: after deleting a booking, «Undo» appears briefly at the bottom – one tap recreates it. It "
  "gets a new internal number; in the place journal the deletion and the recreation stay visible as "
  "movements (the history is preserved, the balance is correct). Transfers have no undo.",
  "<b>Long press on the app icon</b> (home screen): «New booking» opens the editor directly.",
])
shot("Buchungen Filtern.png", "Filter by search text, category, amount and period", width=6.0*cm)

# ---------------------------------------------------------------- 9
h1("9. Analysis")
shot("Buchungen Auswertung.png", "Analysis as a bar and line chart", width=9.5*cm)
bullets([
  "Periods: day / week / month / year.",
  "Views: single account, place or total.",
  "<b>Zoom by gesture</b>: horizontal = number of bars, vertical = Y axis.",
  "Closed accounts count only in the total view (historical balance).",
])

# ---------------------------------------------------------------- 10 Budget
h1("10. Where does my money go?")
p("The <b>Categories</b> page (menu <b>⋮ → Categories</b>) shows the <b>expenses per category</b> – as a pie "
  "chart and below it as a list, sorted <b>descending</b> by amount. It complements the analysis (chapter 9), "
  "which only shows the development over time per account/place.")
shot("Kategorien Auswertung.png", "Category pie chart: «Where does my money go?»", width=6.0*cm)
bullets([
  "<b>Month/Year</b> toggle: current month resp. current year.",
  "In both views you page through the periods by <b>swiping</b> – just like in the budget "
  "(right = back, left = forward); the header shows the displayed month resp. year centred and bold, with "
  "the previous and next one in grey to the left and right (tapping pages as well). Paging stops before the "
  "first booking period and after the current one.",
  "The chart uses the <b>full width</b> and at most <b>half the screen height</b>; the list below scrolls on "
  "its own while the chart stays visible.",
  "The slices are unlabelled; a <b>tap</b> shows category and amount in the centre, without a selection the "
  "total is shown there. The list additionally states the <b>share in percent</b>.",
  "<b>Split transactions</b> count through their partial amounts (not twice), <b>transfers</b> are left out "
  "– the same data source as the budget page.",
  "The analysis covers <b>all accounts</b> and lists only categories with expenses.",
])
h2("Include scheduled transactions (calendar icon)")
p("The <b>calendar toggle</b> at the top right adds the <b>scheduled expenses due</b> in the period "
  "(chapter 8) to the ones already made. Then:")
bullets([
  "The <b>sum per category</b> grows by the scheduled share; the list sorts by the new sum.",
  "In the chart the <b>already paid</b> share appears in the category's strong colour, the <b>scheduled</b> "
  "share right next to it in the <b>same, lighter</b> shade – together they form <b>one continuous "
  "sector</b> with no separator between them.",
  "Rows with a scheduled share can be <b>expanded</b> (arrow on the right), showing «Already paid» and "
  "«Scheduled» separately.",
  "With the toggle on, the slider also pages into <b>future</b> months/years – which then show only "
  "scheduled values – up to the last scheduled occurrence.",
])
h2("Set colours (palette icon)")
p("Each category has a <b>fixed colour</b> – regardless of its current position in the list. The "
  "<b>palette icon</b> at the top right opens the colour list: tapping a category opens the picker; "
  "«Default» restores the automatic colour. The assignment takes effect immediately in the chart and list.")

h1("11. Budget")
p("The <b>Budget</b> page (menu <b>⋮ → Budget</b>) puts the <b>actual</b> value against a per-category "
  "<b>target</b>. It answers the question: «Am I on plan?»")
shot("Budget.png", "Budget page: actual/target per category, with progress bars", width=6.0*cm)
h2("Where does the target come from?")
bullets([
  "<b>Import from KMyMoney</b> (a button in the settings): if your .kmy contains a budget, its values are "
  "taken as the target — <b>month-accurate</b> (monthly and month-by-month budgets are stored per month). "
  "Such targets are <b>not editable</b>.",
  "<b>Compute from history</b>: sum of all previous years ÷ number of years with data. Automatic when the "
  "«compute budget internally» switch is on, otherwise via a button. These targets can be edited by a "
  "<b>long press on a row</b>.",
])
p("Whether a category is <b>income or expense</b> is taken reliably from the KMyMoney type in the .kmy file "
  "(not from the sign of individual bookings). A refund therefore reduces the expense category without turning "
  "it into income; a computed negative actual is clamped to 0.")
h2("Display")
bullets([
  "Toggle <b>year / month</b>. The year view sums the months; the month view shows the displayed month's "
  "target against that same month's actuals. In month view you page through months with a <b>swipe</b> (or by "
  "tapping the grey previous/next month in the header).",
  "Toggle <b>main categories only / with subcategories</b> (a main category sums its subcategories).",
  "<b>Income</b> first, then <b>expenses</b> (large headers); main categories bold, subcategories indented. "
  "The right side shows «actual / target».",
  "Under each category a slim <b>bar</b>: <b>green</b> when on plan, <b>red</b> when behind (width = share of "
  "the target used). Instead of a purely linear time comparison, the app learns each category's typical timing "
  "from the <b>payment history</b>: a <b>one-time</b> expense (e.g. a transit pass bought at the start of the "
  "month) is green as soon as it is within budget, while <b>regular</b> expenses (groceries) are still "
  "measured against the elapsed time. Without history the linear comparison applies (for income reversed).",
])

# ---------------------------------------------------------------- 11 Scheduled transactions
h1("12. Scheduled transactions")
p("The <b>Scheduled transactions</b> page (menu <b>⋮ → Scheduled transactions</b>, shown only in "
  "<b>.kmy mode</b>) imports the standing orders / schedules set up in KMyMoney and shows them as <b>one "
  "chronological list</b> by due date. The schedules are <b>not</b> refreshed by the normal account import; "
  "they update only when you use the <b>pull-to-refresh</b> gesture on this page – with the familiar "
  "<b>yellow progress banner</b>.")
shot("Geplante Buchungen.png", "Scheduled transactions: chronological list with balance strip", width=6.0*cm)
bullets([
  "Each recurring schedule is <b>expanded into its individual occurrences</b> (e.g. a weekly bakery appears "
  "several times) — from the stored next due date up to <b>2 years</b> ahead.",
  "Only <b>active</b> schedules; entries <b>without a date</b> are skipped, and an <b>end date</b> (or a "
  "limited number of payments in KMyMoney) limits the preview.",
  "A coloured <b>strip</b> precedes each row: <b>green</b> = deposit, <b>red</b> = payment, <b>yellow</b> = "
  "transfer. The <b>date</b> is its own column at the front, next to it the <b>name</b> with the <b>payee</b> "
  "in smaller, grey text behind it and below it the <b>category</b> (with several categories <b>«Split "
  "transaction»</b>), then an <b>account column</b> and the amount on the right. For a <b>transfer</b> the "
  "account column shows <b>both accounts, one below the other</b> (instead of a category).",
  "The <b>type</b> is taken strictly from KMyMoney: a <b>stock/ETF purchase</b> (savings plan) is a "
  "<b>transfer</b> from the cash account into the security – not an expense.",
  "A <b>tap on a row</b> opens the transaction in the <b>familiar detail view</b> (read-only, exactly like a "
  "real account movement – including the categories of a split transaction and the balance before/after).",
  "<b>Book now</b>: a <b>long press</b> on a row opens the booking editor as a <b>new booking</b>, prefilled "
  "from the schedule (date = due date, amount, payee, account, categories resp. from/to). Check it, adjust "
  "if needed and save with «New booking» – it lands in the account and (with a place chosen) in the "
  "holdings. Tap = view, long press = book – just like in the booking list.",
  "<b>Skip booking</b>: the prefilled editor also offers a <b>«Skip booking»</b> button (with a "
  "confirmation) – <b>no</b> transaction is created, but the date still counts as done.",
  "<b>The schedule moves on.</b> Booked or skipped, the date disappears from the list right away, and on "
  "the <b>next .kmy export</b> the KMyMoney schedule is moved on by <b>one period</b> (for an actual "
  "booking the date of the last payment is written as well). The schedule itself – amount, rhythm, splits, "
  "start date – stays unchanged, and so do later dates. If KMyMoney has meanwhile moved the schedule on "
  "itself, <b>the file wins</b>: the app writes nothing and drops its pending change. Cancelling the "
  "editor leaves the schedule as it is.",
])
h2("Reminder for due transactions")
p("If you want, the app reports once a day when something is due <b>today</b> («3 scheduled transactions due "
  "today»); tapping the notification opens this page. The switch <b>«Remind me of due transactions»</b> is in "
  "the settings and is <b>off by default</b>; when switching it on the app asks once for the notification "
  "permission. If nothing is due, no notification appears.")
h2("Balance strip (cycling)")
p("At the top – like in the normal account view – a <b>balance strip</b> (label on the left, amount on the "
  "right) that always reflects the <b>currently set filter</b>. Each <b>tap</b> cycles the display:")
bullets([
  "<b>Surplus</b> or <b>deficit</b> (default) = total deposits − total bills; only the applicable state is "
  "shown (green = surplus, red = deficit).",
  "<b>Total deposits</b> (green).",
  "<b>Total bills</b> (red).",
  "<b>Transfers</b> (yellow) = sum of all scheduled transfers.",
])
p("Transfers count towards <b>neither</b> deposits nor bills (they only move money between your own accounts) "
  "and therefore have their own line.")
h2("Filter")
p("The <b>filter icon</b> at the top right narrows the list (and thus also the balance strip and the chart) by:")
bullets([
  "<b>Type</b>: deposits, bills, transfers (each can be toggled on/off).",
  "<b>Account</b>: single selection from a list of all accounts (or «All accounts»).",
  "<b>Name</b>: search text matched against <b>name or payee</b>.",
  "<b>Period</b>: slider (month granularity) or direct, day-precise date entry.",
])
h2("Chart")
p("The <b>chart icon</b> at the top right opens an analysis that <b>looks exactly like the account view's "
  "chart</b>: green/red <b>bars</b> for deposits and bills and a <b>development line</b> of the selected "
  "account or of total wealth. You can pick the <b>view</b> (total or a single account) and the <b>time unit</b> "
  "day/week/month. The line <b>starts at 0 at the beginning of the observation period (= today)</b> and can be "
  "<b>zoomed and panned</b> like the account chart; it starts at the far left (today) and runs to the right "
  "into the future. The chart inherits the list filter. For a <b>transfer</b> the amount decreases on the "
  "<b>source account</b> (red) and increases on the <b>target account</b> (green) – the direction («pay to» "
  "or «from») is taken from KMyMoney.")
p("When you scroll far down, the <b>scroll-to-top button</b> (bottom left) jumps straight back to the start of "
  "the list.")

# ---------------------------------------------------------------- 12 Holdings / portfolio
h1("13. Holdings (places) and portfolio")
h2("Holdings / places – what are they?")
p("A <b>place</b> describes <b>where</b> an account's cash physically is – e.g. wallet, jar, petty cash or "
  "an envelope. An account «Cash» can thus be split into several real storage locations. <b>This feature "
  "does not exist in KMyMoney</b>; it is an addition of this app for everyday cash handling.")
p("Places are <b>not limited to cash accounts</b> – they can be used for <b>any account</b>. In the "
  "following examples we stay with cash, though.")
p("Reached via <b>⋮ → Holdings</b>. Each place keeps its own <b>movement journal</b>; its balance is the "
  "sum of its movements. The sum of all place balances always equals the account balance: <b>«no place»</b> "
  "is the automatically computed remainder (account balance − sum of the other places). So the account "
  "stays consistent no matter how many places you create.")
shot("Bestände.png", "Holdings: asset/liability accounts and portfolio, with places per account", width=6.0*cm)
bullets([
  "A booking created in the app automatically produces a movement on the account's <b>default place</b>.",
  "Place movements can be <b>created, edited and deleted</b> individually.",
  "Money can be <b>transferred between places</b> (e.g. to assign an imported booking to a place, or to move "
  "cash from the envelope into the wallet).",
  "Later amount or deletion changes append dated balancing movements – the history is preserved.",
])
h2("Place on old (imported) transactions")
p("A transaction that was <b>exported and imported again</b> loses its place link – that is intended. If you "
  "still open such a transaction <b>for editing</b> (long press), the <b>place field is shown</b> as soon as "
  "the account has any places, so you can pick one. What happens then depends on the button you press:")
bullets([
  "<b>«New transaction»</b> (duplicate): a <b>new</b> transaction is created – the chosen place <b>is saved</b> "
  "and a movement is added in the holdings. This is the way to give an old transaction a place afterwards.",
  "<b>«Update transaction»</b> on a transaction that had <b>no place before</b> and is <b>already exported</b>: "
  "the entered place is <b>ignored</b>, the place journal stays untouched.",
  "<b>«Update transaction»</b> on a transaction that <b>had a place before</b>: the new place <b>is saved</b> "
  "and followed up with balancing movements (old place −amount, new place +amount).",
])
p("In the plain <b>view</b> (short tap) the place field stays hidden for imported transactions, as before. The "
  "same applies to <b>transfers</b> – there you get a from-place and a to-place, and the movement is created "
  "on <b>both accounts</b>.")
h2("Cash count at place level")
p("A particular advantage: the <b>cash count</b> (reconciliation) can be done <b>per place</b>. You count "
  "only the cash of <i>one</i> place – say the wallet – enter the counted amount, and the app automatically "
  "books the difference as a balancing movement. So you don't have to reconcile the whole account at once; "
  "this <b>simplifies reconciliation considerably</b>. Imported bookings carry no place link.")
p("Next to the <b>«Create booking»</b> switch there is a button for the <b>payee and category</b> of that "
  "balancing booking. Both are empty at first and the button reads «Set payee/category»; as long as nothing "
  "is set, <b>«Apply»</b> stays greyed out. The button opens a small dialog with both fields (with the same "
  "suggestion lists as in the editor); <b>«Save»</b> keeps them permanently, so the next cash count already "
  "has them prefilled.")
h2("Asset and liability accounts")
p("Accounts are grouped by their KMyMoney type into <b>asset accounts</b>, <b>liability accounts</b> "
  "(loans, credit cards) and <b>portfolios</b> – with colour-coded section headers (assets green, "
  "liabilities red, portfolios blue; light theme → lighter colour/black text, dark theme → darker "
  "colour/white text) both in the account drawer and in the holdings view. In holdings each category header "
  "shows its <b>category total</b> right-aligned; the portfolio counts as one line (portfolio value) towards "
  "the <b>total</b> (in a neutral grey/black). The split is taken from the .kmy on import (visible after a "
  "re-import).")
h2("Portfolio (securities)")
p("After importing a KMyMoney investment account, the <b>portfolio</b> appears in the account drawer. The "
  "portfolio view is laid out exactly like an account view – same <b>menu bar</b> (hamburger menu on the left, "
  "portfolio name as the title, and on the right the same menu items as a normal account) plus drawer and "
  "filter – and shows <b>shares × price = current value</b> per security; fully sold securities are hidden. "
  "The <b>balance line toggles by tap</b> through portfolio value → buys → sells (if any) → dividends (if "
  "any) → <b>net invested</b> (buys − sells − dividends) → <b>gain/loss</b> (coloured, with percentage). The "
  "<b>portfolio filter</b> narrows the securities by name and value (slider). When you scroll far down, the "
  "<b>scroll-to-top button</b> (bottom left) jumps straight back to the start. A tap on a security opens its "
  "<b>movements full-screen</b> (buys green, sells red, dividends neutral) with the same figures for that "
  "security plus a <b>filter by buys/sells/dividends</b> and a date slider (start date = first purchase). "
  "The portfolio value is kept separate and also appears as «Net worth» in the main screen's balance line.")
p("For <b>Add</b> and <b>Remove</b> transactions, KMyMoney only ever records a share count, never a "
  "monetary value. A <b>long press</b> on such a row opens a dialog to set the value manually (using the "
  "same calculator keypad as elsewhere) – it then counts like a buy or sell towards the cost basis and "
  "gain/loss, and survives a fresh portfolio import.")
shot("Depot.png", "Portfolio view: securities with shares, price and current value", width=6.0*cm)
shot_row([
    ("Depot Buchungen.png", "Movements of a security: buys, sells and dividends"),
    ("Depot Filtern.png", "Filtering portfolio movements (buys/sells/dividends, period)"),
])
p("The <b>«Analysis»</b> menu opens a <b>pie chart</b> of the securities – styled like the Categories page "
  "(continuous ring, a fixed colour per security, at most half the screen height, with its own scrollable "
  "list below). The slices are unlabelled; a tap shows the <b>name and amount</b> of the security in the "
  "centre, with nothing selected it reads «Total». Chart and list are <b>always</b> sorted descending by the "
  "current value of the period.")
shot("Depot Auswertung.png", "Portfolio analysis: icon toggle, period filter and pie chart", width=6.0*cm)
p("At the top an <b>icon toggle</b> picks the view: <b>Current value</b> (default, € symbol) · <b>Net "
  "deposits</b> (arrow) · <b>Total dividends</b> (% symbol) · <b>Gain/loss</b> (trend arrow). Below it a "
  "<b>period filter</b> narrows the analysis – a monthly range slider (first portfolio transaction to "
  "today) plus two <b>From/To</b> date fields for any date. Any change to the view or period updates chart "
  "and list immediately.")
bullets([
  "<b>Current value:</b> today's value (current price) of the <b>positions built</b> in the period – i.e. "
  "the shares bought within the period that are still held. Over the full period this equals the current "
  "portfolio value (shares sold again within the period do not count negative).",
  "<b>Net deposits:</b> buys − sells − dividends within the period.",
  "<b>Total dividends:</b> all dividends received within the period (gross/net per the setting).",
  "<b>Gain/loss:</b> current value − net deposits, per security as a € amount and as a % return on cost "
  "basis (buys) – coloured green/red in the list. The pie-chart slices keep the security's normal colour.",
])
p("In the <b>toolbar</b> an icon shows or hides <b>fully sold securities</b> (hidden by default). What "
  "counts is the net holding <b>at the end of the selected period</b> – a sale after that does not yet "
  "count as «fully sold».")
p("The <b>Export</b> in the portfolio menu runs right in the portfolio (no switch to the cash view).")
p("<b>Dividends gross/net:</b> the settings let you choose whether dividends are shown gross (declared "
  "dividend) or net (cash received after tax) and used in the balance line (dividends, net invested, "
  "gain/loss). The net value is captured on portfolio import – for existing data a one-time re-import is "
  "needed.")

# ---------------------------------------------------------------- 11
h1("14. Synchronizing: performing export and import")
p("The actual sync with KMyMoney runs through <b>a single symbol</b> in the main screen's title bar – the "
  "<b>export/sync symbol</b> (down arrow).")
pic("docs/img/export_button.png", "The export/sync symbol in the title bar (circled in red)", width=14*cm)
h2("Exporting bookings")
p("Tap the export symbol ⬇. The app loads the current .kmy from the sync target, inserts your new bookings, "
  "makes a backup first (see below) and writes the file back. Each booking is exported only <b>once</b> and "
  "then marked «exported». In CSV mode one CSV file per account is uploaded instead.")
h2("(Re-)importing accounts")
p("Import is done selectively via the account drawer (long press):")
bullets([
  "<b>Long press on an account</b>: imports/updates exactly that account from the .kmy.",
  "<b>Long press on «All accounts»</b> (or pull down in the «All accounts» view): reloads <b>everything</b> – "
  "accounts, portfolios and scheduled transactions, from a single download.",
  "<b>«Add account»</b>: fetches an account not yet present from the .kmy. In the picker you can tick "
  "<b>several accounts (and portfolios) at once</b>; <b>already-imported accounts (including closed) and portfolios are hidden</b>.",
  "<b>Long press on the portfolio</b> (or pull-to-refresh in the portfolio view): updates securities and "
  "prices – also in the background with the yellow progress banner.",
])
p("<b>Securities buys and sells</b> (money from the account into a security or back) are imported as a "
  "<b>transfer</b> into/out of the security. This keeps the full purchase amount from wrongly counting as "
  "«bank fees». The actual small <b>fee</b> (or tax) inside the transaction does <b>not</b> appear in the "
  "booking list and changes no balances – but it is counted as a small expense in the <b>category "
  "evaluations</b> (chapter «Where does my money go?» and Budget). <b>Dividends</b> stay normal income. To "
  "convert old, already-imported buys, re-import the affected account once.")
p("An import replaces the already-exported bookings per account (no duplicates). The import runs <b>in the "
  "background</b> – the UI stays usable; a <b>yellow banner</b> at the top of the booking list («Importing "
  "account …») with a moving gradient, status text and percentage shows the progress and disappears when done. "
  "A message appears <b>only on error</b>. In <b>CSV mode with SMB/WebDAV</b>, adding an account or "
  "long-pressing one opens a <b>navigable folder browser</b> (subfolders 📁 + CSV files, «..» to go up); the "
  "chosen CSV is imported from the current folder. The drawer stays open on long press.")
h2("What the progress in the banner means")
p("The percentage keeps moving throughout the import (downloading, preparing, reading bookings, saving) "
  "instead of jumping in large steps.")
p("To keep the import quick, the app reads the file <b>once for all selected accounts</b> and saves "
  "everything in one go. Prices, budgets and scheduled transactions only appear <b>after</b> the bookings "
  "in the .kmy — the app jumps straight there instead of walking through the whole ledger again for each "
  "of those parts.")
h2("Backup before every export")
p("Before every write-back the app automatically creates a <b>timestamped backup</b> of the .kmy (e.g. "
  "<i>file.kmy.bak-YYYYMMDD-HHMMSS</i>). The backups go into the <b>«Backup» subfolder</b> next to the "
  "original; the folder is created automatically the first time. So you always have a fallback in case "
  "something is ever wrong with the file, and the actual folder stays tidy.")
h2("Protection against overwriting")
p("A few seconds pass between downloading the .kmy and writing it back. If somebody works in KMyMoney on "
  "the computer during that window, their changes must not be lost. The app therefore remembers the "
  "<b>state of the file</b> when downloading and only writes if it is unchanged.")
p("If the file changed in the meantime, the <b>export aborts and writes nothing</b>; the bookings stay "
  "unexported and can be exported again after a fresh import, without any loss. If the server reports no "
  "state, the app writes unchecked as before – the export never fails because of this.")

# ---------------------------------------------------------------- 12
# ---------------------------------------------------------------- 15 Wear OS
h1("15. Wear OS (watch)")
shot("Promo-UhranlagemitAlias.png", "Recording on the watch", width=6.0*cm)
p("With the watch app you record an expense by voice right on your wrist. The watch only captures the text; "
  "processing and creating the booking happen on the phone (the same parser).")
bullets([
  "<b>Three type buttons</b>: income (green), transfer (yellow), expense (red). Then voice starts.",
  "The recognized text is shown briefly with «Cancel» and otherwise processed automatically.",
  "<b>Silent digit entry</b>: enter an amount silently via the digits symbol (resolved by location on the phone).",
  "<b>Wear tile</b>: quick access as a tile.",
  "<b>Default-place balance</b>: below the buttons, the app and tile show the balance of the default place "
  "as «Place: balance» (e.g. «Wallet: 70.00 €»). The balance updates automatically. No default place set → "
  "line hidden.",
  "<b>Reason instead of balance</b>: while bookings are <b>not yet transferred</b>, this line shows the "
  "<b>reason</b> instead of the balance – «Waiting for GPS» (the location is still being resolved), «No "
  "connection to phone» or «Transferring…». The line below still states the count.",
  "<b>Switch account/place</b>: above «Record booking» (centred), a grey switch button – in the app and the "
  "tile – cycles through the shown account and its places; the next booking (phone widget and watch) then "
  "targets the chosen account/place. For a transfer the chosen account is the from-account; the to-account is "
  "the default account – unless the chosen account already is the default, in which case the to-account stays "
  "empty (to fill in manually on the phone). After a short while the selection reverts to the default place.",
  "<b>Offline</b>: bookings not yet transmitted are shown and delivered automatically once the phone is "
  "reachable – without loss and without duplication.",
])
h2("Recording offline (phone off or disconnected)")
p("Speech recognition uses the <b>language chosen in the settings</b> (including uploaded ones) and "
  "<b>prefers offline</b> recognition, so recording works even without the phone/network – provided an "
  "offline speech model is present on the watch. If offline speech fails, the watch automatically opens the "
  "<b>number pad</b>: you just type the amount, the booking is <b>buffered including GPS</b> and sent "
  "automatically once the phone is reachable again (the payee is resolved by location on the phone).")
p("So that <b>voice</b> recording works offline, the <b>offline speech model</b> of the chosen language can "
  "be installed on the watch. On the phone, enable the setting <b>«Install offline speech package on the "
  "watch»</b> (only in the GitHub build with the Wear bridge). On Wear OS 4+ the watch then triggers the "
  "download automatically (once, when a network is reachable); on older watches a hint appears to load the "
  "model in the watch's system settings. Without this switch, the number-pad fallback applies.")
p("Requirement: the phone and watch apps share the same signature (same key).")

# ---------------------------------------------------------------- 15
# ---------------------------------------------------------------- 16 Settings (complete reference)
h1("16. Settings (complete reference)")
p("The settings are reached via the <b>⋮ menu</b> at the top right – entry <b>«Settings»</b> at the very "
  "bottom. On first start the welcome assistant (Chapter 2) already sets the essentials; here all fields are "
  "described in screen order. Changes take effect on <b>«Save»</b> (language and dark mode immediately).")
shot_row([
    ("Einstellungen_1.png", "Language, number format, server connection"),
    ("Einstellungen_2.png", "Export mode, default account, places per account"),
    ("Einstellungen_3.png", "Appearance, security, data"),
])
h2("Language, number format and currency")
bullets([
  "<b>Language</b>: German/English (more via a language file). Pre-selected from the phone on first start; "
  "the choice takes effect immediately. Via <b>«Export/upload language template»</b> you can maintain your "
  "own translation as a file.",
  "<b>Currency</b>: default currency symbol for accounts without their own currency (taken per account from "
  "the file on .kmy import).",
  "<b>Number format</b>: «1.234,56», «1,234.56» or «1234,56»/«1234.56» without grouping; plus the <b>«Show "
  "currency symbol»</b> toggle. Applies to all amounts in the app and on the watch; entry still accepts comma "
  "and point, the export keeps a stable format.",
  "<b>Dividends gross/net</b>: whether portfolio dividends are settled gross (declared) or net.",
  "<b>Budget in-app</b>: compute the budget actuals from history instead of importing from KMyMoney "
  "(see Chapter 11).",
  "<b>Install offline speech package on the watch</b> (GitHub build with the Wear bridge only): lets the "
  "watch download the offline speech model of the chosen language so voice recording works offline too "
  "(see Chapter 15). Off → the number-pad fallback applies offline.",
])
h2("Connection to your server (SMB / WebDAV / Nextcloud)")
p("<b>The most important setup step</b>, so the app can exchange data with KMyMoney. Choose the <b>server "
  "type</b> and enter the access:")
bullets([
  "<b>Nextcloud</b>: base URL of the server + app password (Nextcloud → Security → App password).",
  "<b>WebDAV (generic)</b>: full DAV root URL, auth via HTTP basic.",
  "<b>SMB/Samba</b>: set up through a <b>wizard</b> – see below. SMB2/3.",
  "<b>«Test connection»</b> checks the credentials.",
])
h2("Setting up SMB/Samba (wizard)")
p("With server type <b>SMB/Samba</b> a wizard replaces the address fields, so you don't have to remember "
  "host or share names:")
bullets([
  "<b>1. Search for servers</b>: on opening, the app scans the local network automatically (Bonjour/mDNS, "
  "a NetBIOS name query and a connection test on the SMB port). Each hit is listed with its display name "
  "and the IP address below it – plus its <b>workgroup</b> if the server reports one. <b>«Search "
  "again»</b> restarts the scan; the servers found last time are remembered and shown right away.",
  "<b>2. Log in</b>: username and password of the server, with a <b>«Port»</b> field above them – leave it "
  "empty as long as the server listens on the usual SMB port 445. If it announces a different port over "
  "Bonjour, that port is already filled in; if nothing answers there, the app also tries the <b>default "
  "port 445</b> and corrects the stored address. <b>An empty user means guest.</b> For a share "
  "<b>without a password</b> simply leave the password field empty – the app then continues as guest, even "
  "with a user name filled in. If the server "
  "belongs to a workgroup other than «WORKGROUP», the field is pre-filled with «WORKGROUP\\». The "
  "password is stored encrypted and never shown in clear text.",
  "<b>3. Pick the share</b>: after logging in the app reads the server's <b>shares</b> and offers them for "
  "selection (system shares such as IPC$ are hidden). Clicking one fills the <b>«Share name»</b> field; you "
  "can also type it there if the server refuses to list them.",
  "<b>4. Save</b>: this becomes the address «smb://host/share» – with a different port "
  "«smb://host:7777/share». The <b>target folder</b> inside the share is "
  "then chosen as usual with the «Browse folder» resp. «Choose .kmy» buttons.",
  "<b>«Enter server manually»</b> brings back the classic fields (type «smb://host/share» resp. "
  "«smb://host:port/share» yourself). "
  "<b>«Search for servers on the network»</b> returns to the wizard at any time.",
])
p("<b>Workgroup or Windows domain:</b> at home the workgroup is usually called «WORKGROUP» – it plays no "
  "role for signing in, Samba servers and NAS boxes only check user and password. It is different in a real "
  "<b>Windows domain (Active Directory)</b>: there you enter the user as <b>DOMAIN\\user</b> (or as "
  "«user@domain.local»). Without the domain the login may fail with «Username or password is invalid» even "
  "though the password is correct. For a Windows PC with a local account, «COMPUTERNAME\\user» helps.")
p("If something fails, the wizard names the reason: «Server not reachable», «Username or password is "
  "invalid», «The selected share is not available» or «Access to the folder was denied» – followed, in "
  "brackets, by the server's <b>raw status code</b>, so a report can actually be traced. «Server not "
  "reachable» only ever appears while no connection has been established yet. If the port field "
  "does not hold a number between 1 and 65535, it says so right away without trying to connect.")
h2("Check connection (diagnostics)")
p("The button <b>«Check connection (diagnostics)»</b> – in the settings <b>and</b> in the first-start "
  "wizard – walks the whole chain in <b>one</b> login and shows, for every step, whether it worked, how "
  "long it took and, on failure, the raw status code:")
bullets([
  "<b>Connect</b> (noting it if the default port was used instead of the configured one),",
  "<b>Negotiate</b>: which SMB dialect, whether the server requires signing and whether encryption was "
  "negotiated,",
  "<b>Log in</b>: as a user or as guest, encrypted or not,",
  "<b>Read shares</b>, <b>open share</b>, <b>read folder</b>,",
  "<b>Write in the folder</b> – the app creates a tiny test file and deletes it right away. A "
  "<b>read-only</b> directory would otherwise only surface when writing back, that is after you have "
  "already recorded bookings.",
  "<b>Check the file</b> and, if it exists, whether it is <b>writable</b> (.kmy mode only).",
])
p("<b>«Copy report»</b> puts the result on the clipboard – meant for sending along with a bug report. The "
  "report contains <b>neither the password nor the user name</b> (only «set» or «empty»).")
h2("Export mode")
bullets([
  "<b>.kmy mode</b>: writes new bookings straight into the KMyMoney file (including splits and transfers) "
  "and imports accounts/bookings as well as the portfolio from it. <b>«Choose .kmy»</b> opens a file browser "
  "(subfolders, «..» to go back).",
  "<b>CSV mode</b>: exports one CSV file per account (each booking only once); the target folder and import "
  "folder are set here.",
])
p("Without a configured sync target, export goes locally into a folder you choose. How the sync runs is in "
  "Chapter 14.")
h2("Default account and places")
bullets([
  "<b>Default account</b>: pre-selection for new bookings (also for widget and watch). Selectable only once "
  "accounts exist.",
  "<b>Places per account</b>: per account you can create cash <b>places</b> and set a <b>default place</b> "
  "(see Chapter 13).",
])
h2("Alias names")
p("The <b>«Prompt»</b> toggle controls whether the app offers to learn an unknown payee as an alias after "
  "you speak (existing aliases still apply). Via <b>«Manage alias names»</b> you create, edit and delete "
  "aliases by hand – with spoken term, real payee, booking type, account, categories and location (via "
  "<b>«Open map»</b> on an OpenStreetMap map). The <b>«prefer»</b> toggle (★) puts an alias ahead of the "
  "booking search; the same term may point to several payees distinguished by location.")
h2("Appearance")
bullets([
  "<b>Dark mode</b>: light/dark (takes effect immediately).",
  "<b>Reminder for scheduled bookings</b>: reminds once a day of scheduled bookings due today (off by "
  "default; see Chapter 12).",
])
h2("Security & privacy")
bullets([
  "<b>App lock</b>: optional via biometrics/device credential (fingerprint, face, PIN, pattern, password) – "
  "on start and when returning from the background. When the app hands over to another app itself – camera, "
  "gallery, file picker, speech input – returning within five minutes does not ask again: from the user's "
  "point of view the app was never left. Stay away longer and the lock is back.",
  "<b>Location (GPS)</b> switch (default <b>off</b>): controls all location use. Off = no permission prompt, "
  "no GPS note, no amount-only entry, no alias location. The position is used locally only, never sent to a "
  "service.",
])
h2("Data")
bullets([
  "<b>Export all</b> and <b>Create/restore backup</b> (see below).",
  "<b>Manage accounts (delete/close)</b>: a <b>multi-select</b> lists all accounts with status "
  "(Active/Closed). The bottom row has (context-dependent) <b>Close/Reopen</b> and, on the right, "
  "<b>Delete</b>; <b>Close</b> only at balance 0, <b>Reopen</b> only for closed accounts. A closed account "
  "no longer appears anywhere – only in the total analysis view does its historical balance still count.",
  "<b>Reset data</b>: deletes the local database (a fresh start like the first time – the welcome assistant "
  "appears again afterwards).",
])
h2("Creating and restoring a backup")
p("<b>«Create backup»</b> writes <b>data and settings</b> into a ZIP file: the database (bookings, accounts, "
  "place journal, portfolio, schedules) <i>and</i> all settings – including the places per account, the "
  "category colours and the server access. Beforehand the app asks two things:")
bullets([
  "<b>Include the server password?</b> <b>No</b> by default – then the password appears nowhere in the file "
  "and is entered once after restoring.",
  "<b>Backup password</b> (optional, entered twice): it encrypts the <b>whole file</b> (AES-256-GCM, file "
  "extension <i>.abk</i> instead of <i>.zip</i>). Without a password it stays a plain ZIP.",
])
p("On <b>«Restore backup»</b> the app first asks for the backup password if needed, and then <b>what</b> "
  "should come back: <b>data only</b>, <b>settings only</b> or <b>both</b>. That way a broken configuration "
  "can be repaired without touching the recorded bookings. The app restarts afterwards. Receipt photos are "
  "not part of the backup – once uploaded they live on the server. Backups of older versions (plain "
  "<i>.db</i> files) are no longer accepted.")
h2("Disclaimer")
p("This app is provided as-is, with no warranty. In particular, there is no guarantee that the .kmy file "
  "remains fully valid after an export. Thanks to the automatic backup before every export, however, you "
  "always have a fallback. Keep your own regular backups as well.")

# ---------------------------------------------------------------- 16
h1("17. CSV format (export)")
p("German locale: column separator «;», decimal separator «,», date DD.MM.YYYY, UTF-8. Split bookings are "
  "written as one line per category.")
code = ("Datum;Empfänger;Konto;Typ;Betrag;Notiz;Kategorie<br/>"
        "29.06.2026;Metzgerei;Bargeld;Ausgabe;-7,30;Mittagessen;Lebensmittel")
story.append(Paragraph(code, S("code", fontName="DejaVu", fontSize=8.5, leading=12,
             backColor=LIGHT, borderPadding=6, textColor=colors.HexColor("#333333"))))

# ---- Cover page: borderless full-bleed image + version/date in the dashed placeholder box ----
# Box position measured by pixel analysis on the dashed green box (bottom left of the image, which is
# sized for borderless A4 printing): x 7.8-57.5 mm, y 6.2-34.5 mm from the bottom.
def cover_page(canvas, doc):
    canvas.saveState()
    canvas.drawImage(COVER_PATH_EN, 0, 0, width=A4[0], height=A4[1])
    box_x = 7.8*mm + 3*mm
    canvas.setFont("DejaVu-Bold", 13)
    canvas.setFillColor(colors.HexColor("#1b1b1b"))
    canvas.drawString(box_x, 34.5*mm - 9*mm, "Version 1.8")
    canvas.setFont("DejaVu", 10)
    canvas.setFillColor(GREY)
    canvas.drawString(box_x, 34.5*mm - 17*mm, "Updated: August 2026")
    canvas.restoreState()

# ---- footer ----
def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("DejaVu", 8); canvas.setFillColor(GREY)
    canvas.drawString(2*cm, 1.2*cm, "Ausgaben · User Manual (Version 1.8)")
    canvas.drawRightString(A4[0]-2*cm, 1.2*cm, "Page %d" % doc.page)
    canvas.restoreState()

def _keep_headings_with_next(flowables):
    out, i, n = [], 0, len(flowables)
    while i < n:
        f = flowables[i]
        name = getattr(getattr(f, "style", None), "name", "")
        if name == "h2" and i + 1 < n:
            out.append(KeepTogether([f, flowables[i + 1]])); i += 2; continue
        out.append(f); i += 1
    return out
story = _keep_headings_with_next(story)

class ManualDoc(SimpleDocTemplate):
    """Reports every heading, with its page number, to the table of contents."""

    def beforeDocument(self):
        self._seen_toc = set()   # fresh for every pass (multiBuild builds more than once)

    def afterFlowable(self, flowable):
        # h2 headings sit inside a KeepTogether (see _keep_headings_with_next).
        entries = getattr(flowable, "_content", None) or [flowable]
        for f in entries:
            entry = getattr(f, "_toc", None)
            if entry is None or entry[2] in self._seen_toc:
                continue
            self._seen_toc.add(entry[2])
            level, text, key = entry
            text = text.replace('\u00ab', '\u201c').replace('\u00bb', '\u201d')
            self.notify("TOCEntry", (level, text, self.page, key))


doc = ManualDoc(OUT, pagesize=A4, leftMargin=2*cm, rightMargin=2*cm,
                topMargin=1.8*cm, bottomMargin=1.8*cm,
                title="Ausgaben – User Manual", author="Ausgaben")
# multiBuild: the first pass collects the page numbers, the second one fills them in.
doc.multiBuild(story, onFirstPage=cover_page, onLaterPages=footer)
print("OK ->", OUT)
