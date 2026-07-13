# Ausgaben

**English** · [Deutsch](README.de.md)

A mobile companion app for **[KMyMoney](https://kmymoney.org/)** (Android, Java). Record cash expenses,
income and transfers on the go — right on your phone or a Wear OS watch — and export them into KMyMoney,
instead of typing everything in by hand later.

> Offline-first · no account, no ads, no tracking · open source.

*(“Ausgaben” is German for “expenses”.)*

## Why it might be useful for KMyMoney users

- 📲 **Mobile extension for KMyMoney** — capture cash spending the moment it happens
- 🔌 **Seamless KMyMoney integration** via `.kmy` files or CSV import
- 🗂️ **Sync through a shared WebDAV or SMB folder** — your own server, your data
- 🔒 **Fully offline** — no extra cloud, no vendor account required
- ⌚ **Wear OS app with voice input** — speak an expense right from your wrist
- ➗ **Split bookings and transfers**, categories, places and per-account balances
- 📈 **Charts and analysis** (bar/line, per account, place or total), portfolio import
- 🌍 **Multilingual by design** — ships in **English and German**, currency per account, and you can
  **add more languages yourself** by uploading a translation file (no rebuild needed)
- 👆 **Biometric lock**, encrypted credentials, backup & restore
- 🆓 **No ads. Open source.**

## Screenshots

<p>
  <img src="screenshots/Hauptbildschirm.png" width="150">
  <img src="screenshots/Kontenmenü.png" width="150">
  <img src="screenshots/Ausgaben.png" width="150">
  <img src="screenshots/Grafik.png" width="150">
  <img src="screenshots/Einstellungen.png" width="150">
  <img src="screenshots/UhranlagemitAlias.png" width="150">
</p>

## Download

Grab the latest APKs from the **[Releases page](../../releases/latest)**:

- **Ausgaben-v1.0.apk** — the phone app (Android 8 / API 26 and newer)
- **Ausgaben-Wear-v1.0.apk** — the Wear OS watch app (sends spoken expenses to the phone app). Only needed
  if your watch doesn't receive the app automatically with the phone install; otherwise sideload it
  separately.

Both APKs are signed with the same key (required for the Wear Data Layer pairing). To install, allow
“install from unknown sources”.

### Build flavors / F-Droid

The phone app builds in two flavors:

- **`full`** — with the Wear OS companion over Google Play Services (Wear Data Layer). This is what the
  GitHub release APK uses (`./gradlew :app:assembleFullRelease`).
- **`foss`** — the same app **without any Google Play Services** (`./gradlew :app:assembleFossRelease`).
  This is the flavor intended for **F-Droid**. Phone voice input, sync and all other features work; only the
  Wear OS bridge is absent.

The Wear OS app (`:wear`) needs the Google Wear Data Layer and is therefore **GitHub-only** (its microphone
permission is unaffected). F-Droid packaging notes are in [`fdroid/`](fdroid/).

## Features

### Recording bookings
- Type switch **Expense / Transfer / Income**, amount, payee, account (chosen from existing accounts), note
  and date (defaults to today, with a “Today” shortcut). If a single account is selected on the main screen,
  the new booking is created in that account.
- **Date prompt only when copying**: if you open an existing booking, leave the date unchanged and “save as
  new”, the app asks whether the (old) date or today should apply. Just editing the booking, or setting the
  date yourself, triggers no prompt.
- **Split bookings**: several categories with partial amounts. With one category the total and category
  amount are linked; with several, the sum must equal the total. Partial amounts may be negative.
- **Transfer** (account-to-account): two accounts (From/To) + an optional payee; creates a linked booking in
  both accounts. You can also pick a **from-place and a to-place** – then the respective **place journal** is
  updated (from-account −amount, to-account +amount); editing/deleting rolls the place movements back.
- **Place** (only for expenses/income created in the app): pre-filled with the account's default place;
  determines which cash place the booking credits its movement to (see “Places/Holdings”). Imported bookings
  show no place field.
- **GPS coordinates in the note**: only when the location switch (Settings → Security, see below) is
  **on**. For a *new* booking the current coordinates then appear in the note field while you type, as
  `GPS: lat, lon` (visible and editable; the rest of the note is preserved and never overwritten while
  typing). Purely local — the position is never sent to an external service. Without permission/position the
  field stays empty; existing bookings are left untouched.
- **Voice quick entry**: a long press on **“New booking”** opens voice input. Saying e.g. “barber 20 €”
  opens a matching booking as a template (payee, account, category/-ies, note, booking type) — with today's
  date and the spoken amount. Payee search is fuzzy (finds “Barber Frank” even for “barbers”). If there are
  **several payees with the same name** (e.g. “REWE - Zell” and “REWE - Stuttgart”), the **nearest** one is
  chosen as the template when the location is known.
- **Amount-only entry (location resolution)**: only when the location switch is on. If you say just an amount
  (or type it silently via the **digits icon** at the bottom), the app looks for a matching template at your
  current location (100 m) — in existing bookings and in the alias directory (order: preferred aliases →
  bookings → remaining aliases) — and adopts its data. In the digits dialog the resolved **payee is shown
  under the amount before saving** (updating as the location fix arrives). An alias can hold **any number of
  locations** (e.g. several branches): in the alias, **“Add coordinate”** adds a row with latitude/longitude +
  **“Open map”** (OpenStreetMap), and the minus button removes it; the alias matches when the current location
  is near **any** of these coordinates. Aliases get their location automatically when learned from a booking —
  re-learning at another place **appends** the coordinate (it does not overwrite). With no match, only the
  amount is used. If the location switch is **off**, amount-only entry on the phone is
  disabled (digits icon hidden); on the watch it stays available — the booking is then created with an empty
  payee.
- **Alias names (learned mappings)**: if you change the voice-recognized payee (or, when editing, the
  existing one) while saving, the app asks whether to remember the mapping as an alias. The **booking context
  is stored with it** — booking type, account, category/-ies and **place**, or From/To accounts and
  from/to places for transfers. The alias form's category fields and place display match the booking editor
  (grouped category tree; a place field appears only for an account that has places). The
  stored **booking type** determines the type of the new booking on the phone (in the Wear app the type
  chosen by button applies). When the same spoken term comes up again, the alias is found (same fuzzy logic
  as the booking search) and the new booking is pre-filled with the correct payee **and** the stored
  account/category data — including for Wear entry. So one alias covers every booking type. This also lets you
  use the table deliberately as an **alias/shortcut**: “mom 100 €” can be booked automatically to the real
  name with account/category.
- **Search order**: first the aliases marked **“preferred”**, then existing bookings, and only then the
  remaining aliases. “Preferred” is a property of the individual alias (set in the form, marked with ★ in the
  list).
- Configurable under **Settings → Alias names**: the prompt can be turned off (existing aliases are still
  applied, only no new prompts appear), and **“Manage alias names”** lets you create, edit and delete all
  aliases with all fields by hand.

### Overview & analysis
- Multiple **accounts** via a navigation drawer; list and balance per account or “All accounts”.
- Booking list with colour coding (negative red / positive green), an “exported” marker, and markers for
  splits (`Split`) and transfers (`→`/`←` counter-account/payee). A **short tap** opens a booking as a
  read-only **view** (same layout as the editor, no editing); a **long press** opens it for editing. In the
  list the note is truncated to two lines; in the editor the note field spans four lines.
- **Places/Holdings** per account (where the cash physically is): each place keeps its own movement journal;
  its balance is the sum of its movements. A booking created in the app automatically produces a place
  movement on the account's **default place** (later amount/deletion changes append dated balancing
  movements, so the history is preserved). **“No place”** is the computed remainder (account balance − sum of
  places), so the sum of place balances always equals the account balance. In Holdings you can create/edit/
  delete individual place movements and transfer between places (e.g. to assign an imported booking to a
  place); a cash count sets a place's balance. Imported bookings carry no place link.
- **Filter** by payee, category (as a tree), amount (slider) and **date from–to** (slider in whole-month
  steps; for an exact day type it into the field). The filter applies to the list **and** the analysis. With
  a category filter, a split booking shows only the partial amount of the chosen category.
- **Analysis** (day/week/month/year) as a bar + line chart with account, place and total views; zoomable by
  gesture (horizontal = number of bars, vertical = Y axis).
- **Budget** (own menu page) puts the **actual** value against a per-category **target**. The target is
  imported from a **KMyMoney budget** (a button in the settings; read-only) or computed **app-internally**
  from the history (sum of all previous years ÷ number of years with data). Whether a category is **income or
  expense** is now taken reliably **from the `.kmy` file** (KMyMoney type), no longer from the sign of the
  bookings: a refund reduces the expense category but does not flip it; a negative actual is clamped to 0.
- **Month-accurate budgets**: KMyMoney budgets are imported per month (`monthly`/`month-by-month`). The
  **year view** sums the months, the **month view** shows the displayed month's target against that same
  month's actuals. In month view you page through months with a **swipe** (or by tapping the grey previous/next
  month in the header). Also toggle **main categories only / with subcategories**; income first, then expenses.
  **Edit** internally computed targets with a **long press** on a row; imported KMyMoney targets are read-only.
- **Bar colour from expected progress**: the slim per-category bar is **green** when you are on plan, otherwise
  **red**. Instead of a purely linear time comparison, the app learns each category's typical timing from the
  **payment history**: a **one-time** expense (e.g. a transit pass bought at the start of the month) is green
  as soon as it is within budget; **regular** expenses (groceries) are still measured against the elapsed
  time. Without history the previous linear comparison applies.
- **Scheduled transactions** (own menu page, shown **only in `.kmy` mode**): imports the standing orders /
  schedules set up in KMyMoney and shows them as **one chronological list** by due date. Each recurring
  schedule is **expanded into its individual occurrences** (e.g. a weekly bakery appears several times), from
  the stored next due date up to **2 years** ahead; finished schedules and those without a date are skipped, an
  end date limits the preview. A coloured strip precedes each row: **green** = deposit, **red** = payment,
  **yellow** = transfer; the date is its own column. The list refreshes on every account import.

### Home-screen widget (phone)
Four selectable widgets show the **default-place** balance (default account → default place, like on the
watch):
- **small (2×1)** – balance only;
- **medium (4×2)** – balance + three quick actions (booking, voice, amount);
- **large (4×4)** – balance header with refresh, the most recent bookings (red/green) and the action bar;
- **type (4×2)** – like on the watch: three coloured **icon buttons** **income** (green) / **transfer**
  (yellow) / **expense** (red) and a fourth **grey switch button**, with the balance below. A type button
  **starts speech recognition immediately** and creates the booking directly — **the app is not opened** (only
  the system voice dialog appears). The switch button **cycles through the chosen account and its places**;
  the displayed balance and the target of the new booking follow the selection.

Tapping the balance opens the app; the action buttons launch “New booking”, voice entry, amount-only entry, or
Holdings directly. The widget refreshes when the app is opened and at regular intervals.

### Synchronization
- **Server type** selectable: **Nextcloud** (path scheme `…/remote.php/dav/files/<user>/`) or a **generic
  WebDAV server** (then the base URL you enter is already the DAV root). Either way the protocol is WebDAV
  with HTTP basic auth.
- **CSV export** to Nextcloud: one file `<account>-<timestamp>.csv` per account; each booking is exported
  only once (marked exported only after a successful upload). Full export via the settings.
- **KMyMoney `.kmy` mode**: writes new bookings straight into the `.kmy` (gzip XML) and imports
  accounts/bookings from it — including splits and transfers. Import replaces the already-exported bookings
  per account.
- **Multi-select import**: the account picker now lets you tick **several accounts (and portfolios) at once**;
  **already-imported accounts are hidden**. The import itself runs **in the background** — the UI stays usable;
  a **yellow banner** at the top of the booking list (“Importing account …”) with a moving gradient, status
  text and percentage shows the progress and disappears when done. A message appears **only on error**.
- **Portfolio import**: the **investment account** (portfolio) is offered **once** in the import picker as
  “… (Depot)” (no longer each security individually). The import reads the **securities**, their
  **buys/sells/dividends/add-ins** and the **latest price** per security. After import the portfolio appears
  **in the account drawer** (short tap opens the portfolio view, long tap refreshes it from the `.kmy`). The
  **portfolio view** is laid out like an account view (drawer, header with the portfolio name, its own menu,
  filter) and shows shares × price = current value per security. The **balance line toggles by tap** through
  portfolio value → buys → sells (if any) → dividends (if any) → **net invested** (buys − sells − dividends)
  → **gain/loss** (coloured, with percentage). The **portfolio filter** narrows the securities by name and
  **value (slider)**. A tap on a security opens its **movements full-screen** with the same figures for that
  security plus a **filter by buys/sells/dividends** and a **date slider** (start date = first purchase). The
  **“Analysis”** menu opens a **pie chart** of the securities (share of the portfolio value): the slices are
  unlabelled; a tap shows the **name + amount** of that security in the centre, and with nothing selected the
  centre reads **“Total: <portfolio value>”**. The **Export** in the portfolio menu runs right there, without
  switching to the cash view. The portfolio value is kept **separate** (not mixed into account
  balances/analysis); the buy costs/dividends still appear on the respective cash account. The main screen's
  balance line additionally offers **“Net worth”** = all accounts + portfolio value.
- **Dividends gross/net**: a setting controls whether dividends are shown **gross** (declared dividend) or
  **net** (cash received after tax) and used in the balance line (dividends, net invested, gain/loss). The net
  value is captured on portfolio import; for existing data a one-time re-import is needed.
- **Asset and liability accounts**: accounts are grouped by their KMyMoney type into **asset accounts**,
  **liability accounts** (loans, credit cards) and **portfolios** — with colour-coded section headers (assets
  green, liabilities red, portfolios blue; light → lighter colour/black text, dark → darker colour/white text)
  both in the account drawer **and** in the holdings view. In holdings each category header shows its **category
  total** right-aligned; the portfolio counts as one line (portfolio value) towards the **total** (still shown
  in the neutral grey/black colour).
- **CSV import** of KMyMoney ledger CSV (account from the `Kontentyp:` line, ISO date, sign → type).

### Languages
- **German and English** are built in and switchable in the settings (at the very top) — phone **and** watch
  (the watch adopts the language chosen on the phone automatically).
- **Default language is English.** On the very first launch the phone language decides: German → German, any
  other → English. After that your own choice applies.
- All texts live in a **database table** (filled at startup with DE/EN from the app; the UI reads
  consistently from this table — buttons, hints, field labels, menus, dialogs). **“Export template”** saves a
  JSON structure with all keys (including the watch texts) as a local file; you can fill it in another
  language by hand and read it back via **“Upload language”**. The new language is then selectable and also
  applies to the watch. **If a translation is missing, English is always used** (never another language).
- **Number format** (in the language section): choose between `1.234,56` (grouping dot / comma), `1,234.56`
  (grouping comma / point), `1234,56` and `1234.56` (both without grouping); plus a **“Show currency
  symbol”** toggle. The choice applies to **all** amounts in the app **and on the watch**. Amount entry still
  accepts `,` and `.`; the CSV/`.kmy` export keeps a stable format.

### Security & settings
- Optional **app lock** via biometrics/device credential (fingerprint, face, PIN, pattern, password) —
  authentication on start or when returning from the background.
- **Location (GPS) switch** (default **off**): controls all location use. Off = no permission prompt, no GPS
  note, no amount-only entry on the phone and no alias location.
- Settings: language, **currency symbol** (default; taken per account from the file on `.kmy` import),
  Nextcloud access (app password encrypted), export mode (CSV/`.kmy`), default account, places per account,
  alias names, light/dark theme, database backup/restore, **delete/close account**.
- **Close an account instead of deleting it**: under “Delete/close account” a list shows all accounts with
  their status (Active/Closed) as a **multi-select**. The bottom row has **Delete** and (context-dependent)
  **Close/Reopen** before **Cancel**; **Close** only appears when **all** selected accounts have a balance of
  **0**, **Reopen** when all selected are closed — so several accounts can be closed, reopened or deleted at
  once. A closed account no longer appears anywhere (account menu, booking selection manual/automatic, holdings
  including its places, single-account analysis) — only in the **total analysis view** does its historical
  balance still count. Deleting removes bookings + places permanently.

## Wear OS (voice quick entry)

An additional `:wear` module lets you record a cash expense by voice directly on a Wear OS watch (“barber 20
euro”). The watch only captures the text; the actual processing (the same parser as on the phone) and the
booking creation happen on the phone.

- **Watch**: a “Record booking” screen with three type buttons (income green, transfer yellow, expense red)
  + a Wear tile (“widget”). After choosing the type, voice starts; the recognized text is shown for 10
  seconds with “Cancel” and — unless cancelled — processed without further action. The type is transmitted
  along and enforced on the phone. If the phone is offline, “x bookings not yet transmitted” is shown under
  the buttons.
- **Default-place balance on the watch**: below the buttons, the app **and** the tile show the balance of
  the default place (default account → default place) as “Place: balance”, e.g. “Wallet: 70.00 €”. The phone
  sends the ready-made text as a `/balance` DataItem only when it **changes**; the watch reads it from the
  local Data Layer cache on start and reacts to changes via push — **no polling, no noticeable battery
  drain**. If no default place is set, the line stays hidden. Above “Record booking” (centred) — in both the
  app **and** the tile — a **grey switch button** cycles through the shown **account and its places**; the
  next booking (phone widget **and** watch) then targets the **chosen account/place**. For a **transfer** the
  chosen account is the **from-account**; the **to-account** is the default account — unless the chosen
  account already is the default, in which case the to-account stays empty (to fill in manually on the phone).
  After 60 s the selection reverts to the default place.
- **Silent digit entry**: on the “speak now” screen there is a **digits icon** at the bottom → a keypad (0–9
  + comma, backspace, enter, the entered number on top). This lets you record a booking **silently**; the
  amount is resolved on the phone via the current location (see “Amount-only entry”).
- **Location only after a fresh fix**: after input the location is no longer taken from a possibly **stale**
  fix; instead the watch **waits up to ~1 minute for a fresh GPS fix** and sends the booking only afterwards.
  If no fresh fix arrives, the **last stored measurement** is used (at most 5 minutes old), otherwise the
  booking is sent **without coordinates**. This prevents a payment from landing at a previously visited place
  when you have since moved elsewhere.
- **Offline & sync**: transmission runs **fully automatically** over the Wear Data Layer API as a **DataItem**
  (`DataClient`). If the phone is unreachable, the entry stays PENDING; the Data Layer delivers it
  automatically on reconnect (without the watch having to stay awake or poll). The phone processes it and
  deletes the DataItem — which confirms the transmission to the watch.
- **No loss / no duplication**: each entry has a unique ID. The phone processes every ID only once and
  acknowledges receipt (ACK); only then does the watch remove the entry.

**Data flow:**

```
Watch (voice)
  → local storage (PENDING, id/text/timestamp)
  → transmission  MessageClient  /expense/new  {id,text,timestamp}
  → phone  WearableListenerService
  → parser (VoiceInput) + create booking (Repository, dedup by id)
  → ACK  /expense/ack  {id}
  → watch: entry SYNCED/removed  → synchronization complete
```

Requirement: the phone and Wear apps share the same `applicationId` **and** the same signature (for the
release build the same `keystore.properties`; in debug the same debug key anyway).

## CSV format (export)

German locale: column separator `;`, decimal separator `,`, date `DD.MM.YYYY`, UTF-8, CRLF. Split bookings
are written as one line per category.

```
Datum;Empfänger;Konto;Typ;Betrag;Notiz;Kategorie
29.06.2026;Metzgerei;Bargeld;Ausgabe;-7,30;Mittagessen;Lebensmittel
```

## Tech

- Java, Gradle 8.9 / AGP 8.7.3, `minSdk 26` (`:app`) / `minSdk 30` (`:wear`), `compileSdk 34`.
- Modules: `:app` (phone) and `:wear` (Wear OS).
- [Room](https://developer.android.com/training/data-storage/room) (SQLite, DB version 17), OkHttp
  (WebDAV), [smbj](https://github.com/hierynomus/smbj) (SMB), [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart),
  [osmdroid](https://github.com/osmdroid/osmdroid) (map picker),
  [androidx.security](https://developer.android.com/jetpack/androidx/releases/security)
  (encrypted prefs), [androidx.biometric](https://developer.android.com/jetpack/androidx/releases/biometric),
  [play-services-wearable](https://developer.android.com/training/wearables/data/data-layer) (Data Layer)
  and [androidx.wear.tiles](https://developer.android.com/training/wearables/tiles) (tile).

## Building

```bash
./gradlew assembleDebug
```

The Android SDK is located via `local.properties` (`sdk.dir=…`) — this file is not checked in and must be
present locally (Android Studio creates it automatically). A signed release build needs
`keystore.properties` (also not checked in); without it an unsigned release is produced.

## Setting up a sync target (Nextcloud / WebDAV / SMB)

In the settings choose the **server type**, then enter base URL/share, username and password, optionally a
target/import folder or — in `.kmy` mode — the path to the `.kmy` file. A **“Test connection”** button
checks the credentials; in `.kmy` mode **“Choose .kmy”** opens a **file browser** showing subfolders (📁)
and `.kmy` files — you can descend into subfolders and go back up one level with **“..”**. Without a
configured sync target, export goes locally into a folder chosen via SAF. In **CSV mode with SMB/WebDAV**,
import (add account **or** long-press an account to refresh) shows the same **navigable folder browser** —
subfolders (📁) and CSV files; the chosen CSV is imported from the current folder.

In **`.kmy` mode**, an account (or a **portfolio**) is additionally re-read straight from the `.kmy` by
**pulling the list down** (in that account's or portfolio's view); in the portfolio, long-pressing the
portfolio in the drawer also refreshes it in place. In **CSV mode** this is not available — there you refresh
an account only via the account menu (long-press the account).

- **Nextcloud**: base URL = server (e.g. `https://cloud.example.com`) and an **app password** (Nextcloud →
  Settings → Security → App password). Files end up under `<URL>/remote.php/dav/files/<user>/<folder>/`.
- **WebDAV (generic)**: base URL = full DAV root (e.g. `https://host/dav/`); the folder is appended directly.
  Auth via HTTP basic.
- **SMB/Samba**: “URL” = `smb://host/share` (optionally with a base subfolder); folder and `.kmy` paths are
  relative to the share. Username/password as usual — **empty user = guest**; a Windows domain as
  `DOMAIN\user`. SMB2/3.

## License

Released under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

## Disclaimer

This project was initially developed with extensive AI assistance.

I have been working as a software developer for approximately 25 years, but primarily in technologies outside the modern mobile application ecosystem. While I have experience with Java and have reviewed parts of the codebase, I cannot claim to fully understand every implementation detail generated during the development process.

The application has been tested and is actively used, but there may still be bugs, architectural shortcomings, or code that could be improved by developers with more Android-specific experience.

I am continuously reviewing, learning from, and refining the generated code. Contributions, code reviews, bug reports, and suggestions are therefore especially welcome.

