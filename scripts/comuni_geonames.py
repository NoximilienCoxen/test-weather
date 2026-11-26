#!/usr/bin/env python3
"""Le localita' per dare un nome alla posizione senza chiederlo a nessuno.

Il geocoder della piattaforma non c'e' su ogni telefono (senza servizi Google
non risponde mai), e allora in testa all'app restava "POSIZIONE". Questo script
prepara `app/src/main/assets/localita.tsv.gz`: le localita' di GeoNames con
almeno mille abitanti (cities1000, CC BY 4.0), una per riga, ordinate per
latitudine perche' l'app ci cerchi dentro per bisezione.

Il file di partenza e' `cities.json` del pacchetto npm omonimo (GeoNames
gia' in JSON, con gli accenti): `npm pack cities.json` e si scompatta.

    python3 scripts/comuni_geonames.py <cartella con cities.json e admin1.json>

Riga: latitudine*1e4, longitudine*1e4, nome, regione, codice del paese.
"""
import gzip
import json
import os
import sys

src = sys.argv[1]
citta = json.load(open(os.path.join(src, "cities.json"), encoding="utf-8"))
regioni = {a["code"]: a["name"] for a in json.load(open(os.path.join(src, "admin1.json"), encoding="utf-8"))}

righe = []
for c in citta:
    nome = c["name"].replace("\t", " ").strip()
    if not nome:
        continue
    la = round(float(c["lat"]) * 1e4)
    lo = round(float(c["lng"]) * 1e4)
    reg = regioni.get(f'{c["country"]}.{c["admin1"]}', "").replace("\t", " ")
    righe.append((la, lo, nome, reg, c["country"]))
righe.sort()

out = "app/src/main/assets/localita.tsv.gz"
os.makedirs(os.path.dirname(out), exist_ok=True)
with gzip.GzipFile(out, "wb", mtime=0, compresslevel=9) as f:
    for la, lo, nome, reg, cc in righe:
        f.write(f"{la}\t{lo}\t{nome}\t{reg}\t{cc}\n".encode("utf-8"))
print(len(righe), "localita'", os.path.getsize(out), "byte")
