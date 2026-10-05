#!/usr/bin/env python3
"""Le allerte meteo fuori dall'area MeteoAlarm, interrogate davvero.

MeteoAlarm copre l'Europa e basta (CONTESTO §8-ter). Prima di scrivere un
lettore per un'altra fonte si guarda cosa risponde: la prima stesura del
parser MeteoAlarm cercava `awareness_level`, che nel feed non esiste, e il
406 di ottobre 2026 e' rimasto nascosto perche' la sonda usava curl e non le
intestazioni dell'app. Da qui le due regole di questo script:

- ogni fonte si chiede **con le intestazioni che userebbe l'app**
  (`User-Agent: Caelum/1.0 ...`, un `Accept` coi tipi preferiti e il jolly);
- la risposta grezza si salva in `allerte-mondo/<nome>.<ext>`, e in
  `allerte-mondo/INDICE.txt` vanno stato HTTP, tipo, byte, quante voci e i
  campi CAP che compaiono davvero.

Le fonti sono quelle che i servizi meteorologici nazionali pubblicano senza
chiave, scelte dove l'app ha localita' suggerite o utenti probabili: Regno
Unito (dentro MeteoAlarm, ma va verificato che il feed abbia voci), Stati
Uniti, Canada, Nuova Zelanda, Giappone, Australia. Gli indirizzi vengono
dalla documentazione dei servizi, non da un elenco di terze parti; se uno
risponde 404 lo dice l'indice, ed e' per questo che la sonda esiste.

Non fa fallire il giro: e' una ricognizione, non un contratto.
"""

import os
import re
import urllib.error
import urllib.request

OUT = os.environ.get("OUT", "/tmp/ciout/allerte-mondo")
AGENTE = "Caelum/1.0 (+https://github.com/NoximilienCoxen/test-weather)"

# nome, indirizzo, Accept, estensione del file grezzo
FONTI = [
    # Regno Unito: il Met Office e' membro di EUMETNET, e l'app lo mappa gia'
    # su `united-kingdom`. La domanda e' se il feed porta voci vere.
    ("uk-meteoalarm",
     "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-united-kingdom",
     "application/atom+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Il feed RSS degli avvisi nazionali del Met Office, per tutto il Regno Unito.
    ("uk-metoffice-rss",
     "https://www.metoffice.gov.uk/public/data/PWSCache/WarningsRSS/Region/UK",
     "application/rss+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Stati Uniti, National Weather Service: un punto (New York) e il
    # conteggio nazionale, in GeoJSON e in CAP.
    ("us-nws-punto-geojson",
     "https://api.weather.gov/alerts/active?point=40.7128,-74.0060",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("us-nws-punto-cap",
     "https://api.weather.gov/alerts/active?point=40.7128,-74.0060",
     "application/cap+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Il punto di New York il 5 ottobre era vuoto: per vedere la forma di una
    # voce vera servono stati con allerte attive in quel momento. Si chiedono
    # i piu' grandi, e l'indice dice quanti `features` ha ciascuno.
    ("us-nws-area-fl",
     "https://api.weather.gov/alerts/active?area=FL",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("us-nws-area-ca",
     "https://api.weather.gov/alerts/active?area=CA",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("us-nws-area-ak",
     "https://api.weather.gov/alerts/active?area=AK",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("us-nws-conteggio",
     "https://api.weather.gov/alerts/active/count",
     "application/json, */*;q=0.8", "json"),
    # Canada, Environment and Climate Change Canada: l'API OGC di GeoMet.
    ("ca-eccc-ogc",
     "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&limit=3",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    # Nuova Zelanda, MetService: il feed CAP pubblico (Aoraki e' fra i
    # suggerimenti dell'app).
    ("nz-metservice-cap",
     "https://alerts.metservice.com/cap/rss",
     "application/rss+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Giappone, JMA: il feed Atom dei bollettini XML e gli avvisi per Tokyo.
    ("jp-jma-feed",
     "https://www.data.jma.go.jp/developer/xml/feed/extra.xml",
     "application/atom+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    ("jp-jma-tokyo",
     "https://www.jma.go.jp/bosai/warning/data/warning/130000.json",
     "application/json, */*;q=0.8", "json"),
    # Canada, secondo giro: le allerte **attive**, e attorno a quattro citta'
    # (bbox di qualche chilometro) - cosi' si vede come si chiede per un punto.
    ("ca-eccc-attive",
     "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&limit=5&status_en=active",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("ca-eccc-toronto",
     "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&bbox=-79.43,43.61,-79.33,43.71",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("ca-eccc-montreal",
     "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&bbox=-73.62,45.45,-73.52,45.55",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("ca-eccc-vancouver",
     "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&bbox=-123.17,49.23,-123.07,49.33",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("ca-eccc-calgary",
     "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&bbox=-114.12,51.0,-114.02,51.1",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    # Giappone, secondo giro: il JSON degli avvisi di Tokyo era fermo al 28
    # maggio 2026. Le aree della JMA (nomi e codici) e la geocodifica di
    # Open-Meteo per citta' giapponesi e canadesi: per sapere come si passa da
    # un posto dell'app a un codice d'area.
    ("jp-jma-aree",
     "https://www.jma.go.jp/bosai/common/const/area.json",
     "application/json, */*;q=0.8", "json"),
    ("geo-tokyo",
     "https://geocoding-api.open-meteo.com/v1/search?name=Tokyo&count=2&language=it",
     "application/json", "json"),
    ("geo-osaka",
     "https://geocoding-api.open-meteo.com/v1/search?name=Osaka&count=2&language=it",
     "application/json", "json"),
    ("geo-sapporo",
     "https://geocoding-api.open-meteo.com/v1/search?name=Sapporo&count=2&language=it",
     "application/json", "json"),
    ("geo-toronto",
     "https://geocoding-api.open-meteo.com/v1/search?name=Toronto&count=2&language=it",
     "application/json", "json"),
    # Gli aggregatori: se uno solo coprisse molti paesi, sarebbe la strada per
    # "tutti". L'IFRC Alert Hub raccoglie i feed CAP del registro WMO; il
    # registro stesso elenca i feed per paese.
    ("ifrc-alerthub",
     "https://alerthub.ifrc.org/",
     "text/html, */*;q=0.8", "html"),
    ("ifrc-alerthub-api",
     "https://alerthub-api.ifrc.org/graphql/",
     "application/json, */*;q=0.8", "html"),
    ("wmo-registro",
     "https://alertingauthority.wmo.int/",
     "text/html, */*;q=0.8", "html"),
    # Altri paesi con feed CAP pubblici dichiarati dal servizio nazionale.
    ("br-inmet",
     "https://apiprevmet3.inmet.gov.br/avisos/rss",
     "application/rss+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    ("ar-smn",
     "https://ssl.smn.gob.ar/CAP/AR.php",
     "application/rss+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Australia, Bureau of Meteorology: gli avvisi del Victoria in XML.
    ("au-bom-vic",
     "https://www.bom.gov.au/fwo/IDZ00059.warnings_vic.xml",
     "application/xml, */*;q=0.8", "xml"),
]

# Cio' che si conta in ogni risposta: dice se ci sono voci e in che forma.
SEGNI = [
    "<entry", "<item", "<alert", "cap:event", "<event>", "cap:severity",
    "<severity>", "areaDesc", "\"features\"", "\"event\"", "\"severity\"",
    "\"areaDesc\"", "\"onset\"", "\"expires\"", "senderName", "\"senderName\"",
]


def chiedi(url, accept):
    req = urllib.request.Request(url, headers={"User-Agent": AGENTE, "Accept": accept})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.headers.get("Content-Type", ""), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", ""), e.read()
    except Exception as e:  # rete, TLS, tempo scaduto
        return 0, "", f"{type(e).__name__}: {e}".encode()


def main():
    os.makedirs(OUT, exist_ok=True)
    righe = []
    for nome, url, accept, ext in FONTI:
        code, tipo, corpo = chiedi(url, accept)
        with open(os.path.join(OUT, f"{nome}.{ext}"), "wb") as f:
            f.write(corpo)
        testo = corpo.decode("utf-8", errors="replace")
        righe.append(f"== {nome}")
        righe.append(f"   {url}")
        righe.append(f"   Accept: {accept}")
        righe.append(f"   HTTP {code}  tipo={tipo}  byte={len(corpo)}")
        conti = [f"{s}={testo.count(s)}" for s in SEGNI if s in testo]
        if ext == "json" and "\"features\"" in testo:
            try:
                import json
                conti.append(f"voci={len(json.loads(testo).get('features', []))}")
            except Exception as e:
                conti.append(f"json illeggibile: {e}")
        righe.append("   " + ("  ".join(conti) if conti else "nessun segno noto"))
        # I primi eventi, per vedere come si chiamano davvero.
        eventi = re.findall(r"<(?:cap:)?event>([^<]{1,80})<", testo)[:5]
        eventi += re.findall(r"\"event\"\s*:\s*\"([^\"]{1,80})\"", testo)[:5]
        if eventi:
            righe.append("   eventi: " + " | ".join(eventi))
        righe.append("   inizio: " + re.sub(r"\s+", " ", testo[:400]))
        righe.append("")
    righe += secondo_passo()
    indice = "\n".join(righe)
    with open(os.path.join(OUT, "INDICE.txt"), "w") as f:
        f.write(indice + "\n")
    print(indice)


def secondo_passo():
    """Cio' che si chiede solo dopo aver letto le prime risposte.

    - I documenti XML della JMA: il feed elenca voci con un `link`; si
      prendono le prime due il cui titolo parla di avvisi (警報 o 注意報).
    - I link del registro WMO che sembrano feed (rss, cap, xml, atom).
    """
    righe = ["######## secondo passo"]
    feed = _leggi("jp-jma-feed.xml")
    voci = re.findall(r"<entry>(.*?)</entry>", feed, re.S)
    scelte = [v for v in voci if re.search(r"<title>[^<]*(警報|注意報)[^<]*</title>", v)][:2]
    righe.append(f"== jp-jma-documenti: {len(voci)} voci nel feed, {len(scelte)} sugli avvisi")
    titoli = sorted(set(re.findall(r"<title>([^<]*)</title>", feed)))
    righe.append("   titoli nel feed: " + " | ".join(titoli[:30]))
    for i, v in enumerate(scelte, 1):
        m = re.search(r'<link[^>]*href="([^"]+)"', v)
        if not m:
            continue
        code, tipo, corpo = chiedi(m.group(1), "application/xml, */*;q=0.8")
        with open(os.path.join(OUT, f"jp-jma-doc-{i}.xml"), "wb") as f:
            f.write(corpo)
        testo = corpo.decode("utf-8", errors="replace")
        righe.append(f"   doc {i}: {m.group(1)}  HTTP {code}  byte={len(corpo)}")
        righe.append("   " + re.sub(r"\s+", " ", testo[:600]))
    registro = _leggi("wmo-registro.html")
    link = sorted(set(re.findall(r'href="([^"]+)"', registro)))
    feedish = [l for l in link if re.search(r"(rss|cap|atom|\.xml|feed)", l, re.I)]
    righe.append(f"== wmo-registro: {len(link)} link, {len(feedish)} simili a feed")
    righe.append("   " + " | ".join(feedish[:40]))
    righe.append("   altri: " + " | ".join(link[:60]))
    return righe


def _leggi(nome):
    try:
        with open(os.path.join(OUT, nome), encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return ""


if __name__ == "__main__":
    main()
