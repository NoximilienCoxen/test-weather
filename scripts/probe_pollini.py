#!/usr/bin/env python3
"""Le fonti che misurano cipresso e urticacee, interrogate davvero.

CAMS, da cui viene il polline dell'app, non ha ne' l'uno ne' le altre (§33).
Chi li ha sono le reti di misura (CONTESTO §49.11), e la prima candidata e'
Arpae Emilia-Romagna: bollettino settimanale per stazione, Forli' compresa, da
un REST aperto. Da dentro il container quei siti non si raggiungono; qui si'.

Prima di scrivere un client si guarda cosa rispondono, come per MeteoAlarm
(`awareness_level` cercato e mai esistito) e per le licenze (§47.1). Per ogni
indirizzo: la risposta grezza in `pollini/<nome>.<ext>`, e in
`pollini/INDICE.txt` lo stato HTTP, le chiavi di primo livello, e cio' che
interessa davvero:

- quale stazione e' Forli', e con quale identificativo;
- se nelle letture compaiono cipresso (Cupressaceae) e urticacee
  (Urticaceae), con quale nome e in quale unita';
- che date porta il bollettino piu' recente, cioe' quanto e' vecchio il dato.

Gli indirizzi Arpae vengono da chi li usa gia' (un'integrazione di Home
Assistant), non dalla documentazione ufficiale: se uno risponde 404 lo dice
l'indice. POLLnet e polleninformation.at si guardano solo per sapere come ci
si arriva; il secondo vuole una chiave che non abbiamo.

Non fa fallire il giro: e' una ricognizione, non un contratto ancora.
"""

import json
import os
import re
import urllib.parse
import urllib.request

OUT = os.environ.get("OUT", "/tmp/ciout/pollini")
AGENTE = "caelum-probe (github.com/NoximilienCoxen/test-weather)"
REST = "https://apps.arpae.it/REST"


def q(**parametri):
    return urllib.parse.urlencode(parametri)


INDIRIZZI = [
    ("arpae-stazioni", f"{REST}/pollini_stazioni?" + q(
        sort='[("ordine",1)]', projection='{"foto":0}', where='{"stato":true}')),
    # Il piu' recente, con le stazioni dentro: e' quello che l'app leggerebbe.
    ("arpae-bollettino-ultimo", f"{REST}/bollettini_pollini?" + q(
        sort='[("_id",-1)]', max_results="1")),
    # Gli ultimi tre, senza stazioni: per vedere ogni quanto esce e che date ha.
    ("arpae-bollettini-recenti", f"{REST}/bollettini_pollini?" + q(
        embedded='{"stazioni":0}', sort='[("_id",-1)]', max_results="3")),
    ("arpae-radice", f"{REST}/"),
    ("pollnet-home", "https://pollnet.isprambiente.it/"),
    # Le due pagine di dati che la home di POLLnet nomina (primo giro).
    ("pollnet-opendata", "https://pollnet.isprambiente.it/opendata/"),
    ("pollnet-download", "https://pollnet.isprambiente.it/download-dati/"),
    ("polleninformation-interfaccia", "https://www.polleninformation.at/en/data-interface"),
]

CERCATI = {
    "cipresso": re.compile(r"(?i)cupress|cipress|taxac"),
    "urticacee": re.compile(r"(?i)urtic|parietar"),
    "forli": re.compile(r"(?i)forl[iì]"),
}


def scarica(url):
    req = urllib.request.Request(url, headers={
        "User-Agent": AGENTE, "Accept": "application/json,text/html,*/*"})
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.status, r.headers.get("Content-Type", ""), r.read()


def contesti(testo, regex, larghezza=160, quanti=6):
    """I punti in cui compare un nome, con un po' di contorno."""
    fuori = []
    for m in regex.finditer(testo):
        a, b = max(0, m.start() - larghezza // 2), m.end() + larghezza // 2
        fuori.append(" ".join(testo[a:b].split()))
        if len(fuori) >= quanti:
            break
    return fuori


def forma(valore, profondita=0, max_prof=8):
    """Lo scheletro di un JSON: chiavi e tipi, il primo elemento delle liste."""
    rientro = "  " * profondita
    if profondita >= max_prof:
        return [f"{rientro}..."]
    righe = []
    if isinstance(valore, dict):
        for k, v in list(valore.items())[:40]:
            tipo = type(v).__name__
            esempio = "" if isinstance(v, (dict, list)) else f" = {repr(v)[:60]}"
            righe.append(f"{rientro}{k}: {tipo}{esempio}")
            if isinstance(v, (dict, list)):
                righe += forma(v, profondita + 1, max_prof)
    elif isinstance(valore, list):
        righe.append(f"{rientro}[{len(valore)} elementi]")
        if valore:
            righe += forma(valore[0], profondita + 1, max_prof)
    return righe


def main():
    os.makedirs(OUT, exist_ok=True)
    indice = []
    for nome, url in INDIRIZZI:
        indice.append(f"== {nome}\n   {url}")
        try:
            stato, tipo, corpo = scarica(url)
        except Exception as e:
            indice.append(f"   ERRORE: {e}\n")
            continue
        ext = "json" if "json" in tipo else "html" if "html" in tipo else "bin"
        open(os.path.join(OUT, f"{nome}.{ext}"), "wb").write(corpo)
        testo = corpo.decode("utf-8", "replace")
        indice.append(f"   HTTP {stato}  tipo={tipo}  byte={len(corpo)}")
        if ext == "json":
            try:
                dati = json.loads(testo)
                # Le lettere accentate arrivano come \u00ec: senza ridecodificarle
                # "Forli'" non si trova (primo giro: zero occorrenze, c'era).
                testo = json.dumps(dati, ensure_ascii=False)
                indice.append("   forma:")
                indice += ["     " + r for r in forma(dati)]
            except ValueError as e:
                indice.append(f"   JSON non valido: {e}")
        for chi, regex in CERCATI.items():
            trovati = contesti(testo, regex)
            indice.append(f"   {chi}: {len(regex.findall(testo))} occorrenze")
            indice += [f"     ... {c}" for c in trovati]
        indice.append("")

    open(os.path.join(OUT, "INDICE.txt"), "w").write("\n".join(indice) + "\n")
    print("\n".join(indice))


if __name__ == "__main__":
    main()
