#!/usr/bin/env python3
"""Le pagine di licenza delle fonti, scaricate e ridotte a testo.

Il blocco ATTRIBUZIONE delle note legali (`SalaLegali.kt`) aspetta da §28 le
frasi verbatim che le fonti chiedono: scriverle a memoria in una pagina legale
e' l'errore che quella pagina esiste per evitare. Da dentro il container le
pagine non si raggiungono; qui si', come le API (CONTESTO §47).

Per ogni indirizzo: il testo intero in `licenze/<nome>.txt`, e in
`licenze/INDICE.txt` lo stato HTTP e le righe che parlano di licenza o di
attribuzione, per trovare subito il passo da copiare. Gli indirizzi sono piu'
d'uno per fonte perche' da qui non si sa quale risponda: chi risponde 404 lo
dice l'indice.
"""

import html
import os
import re
import sys
import urllib.request

OUT = os.environ.get("OUT", "/tmp/ciout/licenze")

PAGINE = [
    ("open-meteo-licenza", "https://open-meteo.com/en/license"),
    ("open-meteo-termini", "https://open-meteo.com/en/terms"),
    ("open-meteo-aria", "https://open-meteo.com/en/docs/air-quality-api"),
    ("open-meteo-previsione", "https://open-meteo.com/en/docs"),
    ("cams-licenza", "https://ads.atmosphere.copernicus.eu/cdsapp/#!/terms/licence-to-use-copernicus-products"),
    ("copernicus-licenza", "https://atmosphere.copernicus.eu/sites/default/files/repository/20170117_Copernicus_License_V1.0.pdf"),
    ("cams-come-citare", "https://atmosphere.copernicus.eu/how-cite-cams-products"),
    ("meteoalarm-termini", "https://www.meteoalarm.org/en/live/page/terms-and-conditions"),
    ("meteoalarm-termini-2", "https://meteoalarm.org/en/live/page/terms"),
    ("meteoalarm-feed", "https://feeds.meteoalarm.org/"),
]

CHIAVI = re.compile(
    r"(?i)(attribut|licen[cs]e|cc[ -]by|creative commons|copernicus|"
    r"must (cite|mention|credit)|credit|citation|terms of use|reuse|re-use|copyright)"
)


def testo_di(corpo, tipo):
    if "pdf" in tipo:
        return "(PDF: il file e' salvato accanto, da leggere a parte)"
    t = corpo.decode("utf-8", "replace")
    t = re.sub(r"(?is)<(script|style|noscript)[^>]*>.*?</\1>", " ", t)
    t = re.sub(r"(?i)<br\s*/?>|</(p|div|li|h[1-6]|tr|section)>", "\n", t)
    t = re.sub(r"<[^>]+>", " ", t)
    t = html.unescape(t)
    righe = [re.sub(r"[ \t]+", " ", r).strip() for r in t.splitlines()]
    return "\n".join(r for r in righe if r)


def main():
    os.makedirs(OUT, exist_ok=True)
    indice = []
    for nome, url in PAGINE:
        req = urllib.request.Request(url, headers={
            "User-Agent": "caelum-probe (github.com/NoximilienCoxen/test-weather)",
            "Accept": "text/html,application/pdf,*/*",
        })
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                stato, tipo, corpo = r.status, r.headers.get("Content-Type", ""), r.read()
        except Exception as e:
            indice.append(f"== {nome}  {url}\n   ERRORE: {e}\n")
            continue
        if "pdf" in tipo:
            open(os.path.join(OUT, f"{nome}.pdf"), "wb").write(corpo)
        testo = testo_di(corpo, tipo)
        open(os.path.join(OUT, f"{nome}.txt"), "w").write(testo + "\n")
        trovate = [r for r in testo.splitlines() if CHIAVI.search(r)][:40]
        indice.append(
            f"== {nome}  {url}\n   HTTP {stato}  {tipo}  {len(corpo)} byte\n"
            + "".join(f"   | {r[:300]}\n" for r in trovate)
        )
    testo_indice = "\n".join(indice)
    open(os.path.join(OUT, "INDICE.txt"), "w").write(testo_indice + "\n")
    print(testo_indice)
    return 0


if __name__ == "__main__":
    sys.exit(main())
