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
l'indice. polleninformation.at si guarda solo per sapere come ci si arriva:
vuole una chiave che non abbiamo.

**POLLnet** (secondo giro, §49.11) ha un WFS nazionale con licenza CC-BY 4.0,
e la sonda lo interroga in due tempi: prima stazioni e particelle, da cui si
ricavano l'identificativo di Forli' e quelli di cipresso e urticacee; poi le
misure degli ultimi 60 giorni di quella stazione per quelle due particelle.
In fondo all'indice: l'ultima data misurata e quanti giorni fa e', da mettere
accanto al ritardo di Arpae (5-12 giorni).

Non fa fallire il giro: e' una ricognizione, non un contratto ancora.
"""

import datetime
import json
import os
import re
import urllib.parse
import urllib.request

OUT = os.environ.get("OUT", "/tmp/ciout/pollini")
AGENTE = "caelum-probe (github.com/NoximilienCoxen/test-weather)"
REST = "https://apps.arpae.it/REST"
WFS = "https://sdi.isprambiente.it/geoserver/om/ows"


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


def wfs(strato, **altro):
    """Un GetFeature del WFS di POLLnet, in GeoJSON."""
    return f"{WFS}?" + q(service="WFS", version="2.0.0", request="GetFeature",
                         typeName=f"om:{strato}", outputFormat="application/json", **altro)


def proprieta(geojson):
    """Le `properties` di ogni elemento: il resto del GeoJSON qui non serve."""
    return [f.get("properties", {}) for f in (geojson or {}).get("features", [])]


def primo(riga, *chiavi):
    """Il primo campo presente fra quelli nominati: i nomi esatti li dice la risposta."""
    for k in chiavi:
        if riga.get(k) not in (None, ""):
            return riga[k]
    return None


def pollnet(indice):
    """Stazioni e particelle, poi le misure di Forli' per cipresso e urticacee."""
    indice.append("######## POLLnet, WFS")
    dati = {}
    for nome, strato in (("pollnet-stazioni", "Stazioni_POLLnet"), ("pollnet-particelle", "Pollini_spore")):
        url = wfs(strato)
        indice.append(f"== {nome}\n   {url}")
        try:
            stato, tipo, corpo = scarica(url)
            open(os.path.join(OUT, f"{nome}.json"), "wb").write(corpo)
            dati[nome] = json.loads(corpo.decode("utf-8", "replace"))
            righe = proprieta(dati[nome])
            indice.append(f"   HTTP {stato}  tipo={tipo}  byte={len(corpo)}  elementi={len(righe)}")
            indice.append("   forma del primo elemento:")
            indice += ["     " + r for r in forma(righe[0] if righe else {})]
        except Exception as e:
            indice.append(f"   ERRORE: {e}")
        indice.append("")

    stazioni = proprieta(dati.get("pollnet-stazioni"))
    particelle = proprieta(dati.get("pollnet-particelle"))
    forli = [s for s in stazioni if CERCATI["forli"].search(str(primo(s, "STAT_NAME_I", "STAT_NAME_E", "STAT_CODE") or ""))]
    emilia = [s for s in stazioni if re.search(r"(?i)emilia", json.dumps(s, ensure_ascii=False))]
    cercate = {
        chi: [p for p in particelle if CERCATI[chi].search(str(primo(p, "PART_NAME_L", "PART_NAME_I", "PART_CODE") or ""))]
        for chi in ("cipresso", "urticacee")
    }
    indice.append("== cosa ne esce")
    indice.append(f"   stazioni di Forli': {[(primo(s, 'STAT_ID'), primo(s, 'STAT_NAME_I')) for s in forli]}")
    indice.append(f"   stazioni in Emilia-Romagna: {[(primo(s, 'STAT_ID'), primo(s, 'STAT_NAME_I')) for s in emilia]}")
    for chi, trovate in cercate.items():
        indice.append(f"   {chi}: {[(primo(p, 'PART_ID'), primo(p, 'PART_NAME_L', 'PART_NAME_I'), primo(p, 'PART_LOW'), primo(p, 'PART_MIDDLE'), primo(p, 'PART_HIGH')) for p in trovate]}")
    indice.append("")

    # Forli' se c'e', altrimenti la prima stazione emiliana: serve un ritardo
    # vero da confrontare con Arpae, e una stazione vicina lo da' lo stesso.
    stazione = (forli or emilia or [None])[0]
    ids = [primo(p, "PART_ID") for t in cercate.values() for p in t if primo(p, "PART_ID") is not None]
    if stazione is None or not ids:
        indice.append("   nessuna stazione o particella trovata: niente misure da chiedere\n")
        return
    oggi = datetime.date.today()
    filtro = (f"STAT_ID={primo(stazione, 'STAT_ID')} and PART_ID in ({','.join(str(i) for i in ids)}) "
              f"and REMA_DATE >= '{oggi - datetime.timedelta(days=60)}'")
    url = wfs("Concentrazione_pollini_spore", cql_filter=filtro)
    indice.append(f"== pollnet-misure ({primo(stazione, 'STAT_NAME_I')}, ultimi 60 giorni)\n   {url}")
    try:
        stato, tipo, corpo = scarica(url)
        open(os.path.join(OUT, "pollnet-misure.json"), "wb").write(corpo)
        misure = proprieta(json.loads(corpo.decode("utf-8", "replace")))
        indice.append(f"   HTTP {stato}  tipo={tipo}  byte={len(corpo)}  misure={len(misure)}")
        indice.append("   forma della prima misura:")
        indice += ["     " + r for r in forma(misure[0] if misure else {})]
        date = sorted({str(primo(m, "REMA_DATE"))[:10] for m in misure if primo(m, "REMA_DATE")})
        if date:
            ultima = datetime.date.fromisoformat(date[-1])
            indice.append(f"   date: dalla {date[0]} alla {date[-1]}, {len(date)} giorni misurati")
            indice.append(f"   **ritardo: l'ultima misura e' di {(oggi - ultima).days} giorni fa** (oggi {oggi})")
        for m in sorted(misure, key=lambda m: str(primo(m, "REMA_DATE")))[-10:]:
            indice.append(f"     {primo(m, 'REMA_DATE')}  PART_ID={primo(m, 'PART_ID')}  {primo(m, 'REMA_CONCENTRATION')}")
    except Exception as e:
        indice.append(f"   ERRORE: {e}")
    indice.append("")


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

    # Isolata: se il WFS cade, l'indice di Arpae si scrive lo stesso.
    try:
        pollnet(indice)
    except Exception as e:
        indice.append(f"######## POLLnet: ERRORE inatteso: {e}")

    open(os.path.join(OUT, "INDICE.txt"), "w").write("\n".join(indice) + "\n")
    print("\n".join(indice))


if __name__ == "__main__":
    main()
