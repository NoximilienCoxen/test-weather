#!/usr/bin/env python3
"""
I commenti di Kotlin **si annidano**, e questa e' la trappola che ci e' costata
un giro di CI.

In Java `/*` dentro un commento non vuol dire niente. In Kotlin apre un
commento dentro il commento, e da quel momento serve un `*/` in piu'. Scrivere
`image/*` dentro un KDoc - un tipo MIME, un percorso con un asterisco, una
glob - basta: il compilatore legge fino a fine file e dice "Unclosed comment"
alla riga dopo l'ultima, che e' il posto in cui l'errore **non** e'.

Qui non si compila niente: si contano le aperture e le chiusure, file per file.
Un conteggio che non torna non dimostra che il file e' rotto - in una stringa
`"/*"` ci si puo' mettere quello che si vuole - ma dice dove guardare, e costa
un secondo invece di otto minuti di runner.

    python3 scripts/commenti_kotlin.py
"""
import os
import re
import sys

BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src")

def stringhe_via(testo):
    """Le stringhe vanno tolte prima di contare: un `/*` dentro le virgolette
    non apre niente, e contarlo darebbe un falso allarme a ogni regex."""
    testo = re.sub(r'"""(?:.|\n)*?"""', '""', testo)
    testo = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', testo)
    return testo

def main():
    sospetti = []
    for radice, _, file in os.walk(BASE):
        for nome in file:
            if not nome.endswith(".kt"):
                continue
            percorso = os.path.join(radice, nome)
            with open(percorso, encoding="utf-8") as f:
                corpo = stringhe_via(f.read())
            aperti = len(re.findall(r"/\*", corpo))
            chiusi = len(re.findall(r"\*/", corpo))
            if aperti != chiusi:
                rel = os.path.relpath(percorso, os.path.dirname(BASE))
                sospetti.append((rel, aperti, chiusi))
    for rel, aperti, chiusi in sospetti:
        print(f"COMMENTI: {rel} apre {aperti}, chiude {chiusi}")
        if aperti > chiusi:
            print("          cerca un `/*` dentro un commento: e' un annidamento, non un refuso")
    print(f"--- {len(sospetti)} da guardare")
    return 1 if sospetti else 0

if __name__ == "__main__":
    sys.exit(main())
