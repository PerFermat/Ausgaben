# Ausgaben

**English** · [Deutsch](README.de.md)

A mobile companion app for **[KMyMoney](https://kmymoney.org/)** (Android, Java). Record cash expenses,
income and transfers on the go — right on your phone or a Wear OS watch — and export them into KMyMoney,
instead of typing everything in by hand later.

> Offline-first · no account, no ads, no tracking · open source.

*("Ausgaben" is German for "expenses".)*

📖 The full **[user manual (PDF, English)](docs/Manual-Ausgaben-en.pdf)** describes every feature in
detail, with screenshots.

<p>
  <img src="screenshots/Promo-Datenschutz.png" width="220">
  <img src="screenshots/Promo-Syncronisation.png" width="220">
  <img src="screenshots/Promo-Alias.png" width="220">
  <img src="screenshots/Promo-UhranlagemitAlias.png" width="220">
</p>
<sub>(Promo graphics are in German; the app itself is fully available in English.)</sub>

## Why it might be useful for KMyMoney users

- 📲 **Mobile extension for KMyMoney** — capture cash spending the moment it happens
- 🔌 **Seamless KMyMoney integration** via `.kmy` files or CSV import
- 🗂️ **Sync through a shared WebDAV or SMB folder** — your own server, your data
- 🔒 **Fully offline** — no extra cloud, no vendor account required
- ⌚ **Wear OS app with voice input** — speak an expense right from your wrist
- ➗ **Split bookings and transfers**, categories, places/holdings and portfolio import
- 📈 **Analysis**: history per account, category pie chart, budget (actual/planned), portfolio return
- 🌍 **Multilingual** — ships in English and German, more languages via translation upload
- 👆 **Biometric lock**, encrypted credentials, backup & restore
- 🆓 **No ads. Open source.**

## Screenshots

<p>
  <img src="screenshots/Kontobuchungen.png" width="140">
  <img src="screenshots/Kontenmenü.png" width="140">
  <img src="screenshots/Buchung Empfänger.png" width="140">
  <img src="screenshots/Buchungen Auswertung.png" width="140">
  <img src="screenshots/Kategorien Auswertung.png" width="140">
  <img src="screenshots/Budget.png" width="140">
  <img src="screenshots/Depot Auswertung.png" width="140">
  <img src="screenshots/Einstellungen_1.png" width="140">
</p>

## Download

The current APKs are on the **[releases page](../../releases/latest)**:

- **app-full-release.apk** — the phone app with the Wear OS bridge (Android 8 / API 26 and up)
- **app-foss-release.apk** — the same phone app without Google Play Services (F-Droid build)
- **wear-release.apk** — the Wear OS watch app (spoken expenses to the phone app). Only needed if
  the watch doesn't get the app automatically alongside the phone install; otherwise sideload it onto the
  watch separately.

Both are signed with the same key (required for Wear Data Layer pairing). Enable "Install from unknown
sources" to install.

### Build flavors / F-Droid

The phone app builds in two flavors:

- **`full`** — with the Wear OS bridge via Google Play Services (`./gradlew :app:assembleFullRelease`).
- **`foss`** — the same app **without any Google Play Services**
  (`./gradlew :app:assembleFossRelease`), meant for **F-Droid**. Every feature stays; only the Wear OS
  bridge is missing.

The Wear OS app (`:wear`) needs the Google Wear Data Layer and stays **GitHub-only**. See [`fdroid/`](fdroid/)
for F-Droid packaging notes.

## Features at a glance

Details, screenshots and exact behavior are in the **[user manual](docs/Manual-Ausgaben-en.pdf)**.

- **Record bookings**: expense/transfer/income, split bookings, receipt photos, voice input
  ("hairdresser 20 €"), silent amount-only entry resolved via GPS location, learning payee aliases.
- **List & filter**: search across payee/note/category, amount and date-range filters, undo after delete,
  a built-in calculator keyboard in the amount field.
- **Analysis**: history chart per account/place/total, category pie chart ("Where does my money go?"),
  budget (actual vs. planned, imported from KMyMoney or computed in-app), scheduled bookings preview.
- **Holdings & portfolio**: several cash **places** per account with their own movement journal and
  reconciliation; portfolio import with prices, buys/sells/dividends, gain/loss analysis.
- **Sync**: Nextcloud/WebDAV/SMB, `.kmy` mode (writes/reads the KMyMoney file directly, including splits,
  transfers and the portfolio) or CSV export; automatic backup before every export, protection against
  concurrent overwrites.
- **Multilingual**: English/German built in, more languages via a translation file (also on the watch).
- **Security**: optional biometric app lock, GPS off by default, encrypted credentials.

## Wear OS (voice quick capture)

An additional `:wear` module records a cash expense by voice right on a Wear OS watch ("hairdresser 20
euros"). The watch only captures the text; processing and creating the booking happen on the phone (the
same parser). Recognition follows the selected app language and **prefers offline** speech, so recording
works even with the phone off; if offline speech isn't available the watch falls back to the silent number
pad. Bookings recorded offline are buffered (incl. GPS) and sent automatically once the phone is reachable
— without loss or duplication. An opt-in phone setting ("Install offline speech package on the watch",
`full` build only) lets the watch download the offline speech model for the chosen language. See the
"Wear OS" chapter in the manual for details.

Requirement: the phone and watch app share the same `applicationId` **and** the same signature.

## CSV format (export)

German: column separator `;`, decimal separator `,`, date `DD.MM.YYYY`, UTF-8, CRLF. Split bookings are
written as one row per category.

```
Datum;Empfänger;Konto;Typ;Betrag;Notiz;Kategorie
29.06.2026;Metzgerei;Bargeld;Ausgabe;-7,30;Mittagessen;Lebensmittel
```

## Tech

- Java, Gradle 8.9 / AGP 8.7.3, `minSdk 26` (`:app`) / `minSdk 30` (`:wear`), `compileSdk 34`.
- Modules: `:app` (phone) and `:wear` (Wear OS).
- [Room](https://developer.android.com/training/data-storage/room) (SQLite), OkHttp (WebDAV),
  [smbj](https://github.com/hierynomus/smbj) (SMB), [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart),
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

In the settings choose the **server type**, then enter base URL/share, username and password; a **"Test
connection"** button checks the credentials. Without a configured sync target, export goes locally into a
folder you choose.

- **Nextcloud**: base URL of the server + an **app password** (Nextcloud → Settings → Security → App
  password).
- **WebDAV (generic)**: the full DAV root URL, auth via HTTP basic.
- **SMB/Samba**: `smb://host/share`; empty user = guest, a Windows domain as `DOMAIN\user`. SMB2/3.

## License

Released under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

## Disclaimer

This project was initially developed with extensive AI assistance.

I have been working as a software developer for approximately 25 years, but primarily in technologies outside the modern mobile application ecosystem. While I have experience with Java and have reviewed parts of the codebase, I cannot claim to fully understand every implementation detail generated during the development process.

The application has been tested and is actively used, but there may still be bugs, architectural shortcomings, or code that could be improved by developers with more Android-specific experience.

I am continuously reviewing, learning from, and refining the generated code. Contributions, code reviews, bug reports, and suggestions are therefore especially welcome.
