#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Startet den Handbuch-Editor und richtet beim ersten Mal seine Umgebung ein.

    tools/handbuch-editor.py                 # starten
    tools/handbuch-editor.py --neu-aufsetzen # Umgebung wegwerfen und neu bauen

PySide6 gibt es unter Ubuntu nicht als apt-Paket, und das System-Python ist gegen pip gesperrt.
Deshalb wohnt es in einer eigenen Umgebung unter ~/.venvs/handbuch-editor. Das System-Python
bleibt unangetastet; wer den Editor loswerden will, löscht diesen einen Ordner.
"""
import os
import shutil
import subprocess
import sys

UMGEBUNG = os.path.join(os.path.expanduser("~"), ".venvs", "handbuch-editor")
WERKZEUGE = os.path.dirname(os.path.abspath(__file__))
# PySide6 für die Oberfläche, reportlab für die Kapitelvorschau (dieselbe Bibliothek, mit der
# build_manual.py das Handbuch setzt), pypdfium2 zum Anzeigen der gebauten Seiten.
PAKETE = ["PySide6", "reportlab", "pypdfium2"]


def python_der_umgebung() -> str:
    unterordner = "Scripts" if os.name == "nt" else "bin"
    name = "python.exe" if os.name == "nt" else "python"
    return os.path.join(UMGEBUNG, unterordner, name)


def umgebung_bauen() -> None:
    print(f"Richte die Umgebung unter {UMGEBUNG} ein – das dauert beim ersten Mal ein paar Minuten.")
    subprocess.run([sys.executable, "-m", "venv", UMGEBUNG], check=True)
    subprocess.run([python_der_umgebung(), "-m", "pip", "install", "--upgrade", "pip"], check=True)
    subprocess.run([python_der_umgebung(), "-m", "pip", "install"] + PAKETE, check=True)


def bereit() -> bool:
    if not os.path.isfile(python_der_umgebung()):
        return False
    pruefung = "; ".join(f"import {name.lower() if name != 'PySide6' else 'PySide6'}"
                         for name in PAKETE)
    fertig = subprocess.run([python_der_umgebung(), "-c", pruefung], capture_output=True)
    return fertig.returncode == 0


def main() -> int:
    argumente = sys.argv[1:]
    if "--neu-aufsetzen" in argumente:
        argumente.remove("--neu-aufsetzen")
        shutil.rmtree(UMGEBUNG, ignore_errors=True)

    if not bereit():
        try:
            umgebung_bauen()
        except subprocess.CalledProcessError as fehler:
            print(f"Die Umgebung ließ sich nicht einrichten: {fehler}", file=sys.stderr)
            return 1

    umwelt = dict(os.environ)
    umwelt["PYTHONPATH"] = WERKZEUGE + os.pathsep + umwelt.get("PYTHONPATH", "")
    # build_manual.py braucht reportlab, screenshots.py braucht PIL und tkinter – beides steckt
    # im System-Python, nicht in der schlanken Editor-Umgebung.
    umwelt["HANDBUCH_EDITOR_SYSTEM_PYTHON"] = sys.executable
    return subprocess.run([python_der_umgebung(), "-m", "handbuch_editor"] + argumente,
                          env=umwelt).returncode


if __name__ == "__main__":
    sys.exit(main())
