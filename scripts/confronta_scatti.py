#!/usr/bin/env python3
"""Quanto sono cambiati gli scatti fra due giri di CI, pixel per pixel.

Serve dopo un aggiornamento che puo' muovere i pixel (una BOM di Compose, una
libreria grafica): la CI dice che gli scatti si fanno, non che sono uguali a
prima (CONTESTO §47).

**L'impronta del file non basta, ed e' la prima cosa che si prova.** Fra due
giri senza nessun cambio di codice cambiano 67 scatti su 74: l'orologio nella
barra di stato, il meteo vero di Forli', l'ora reale (che decide se compare
"torna all'ora attuale"), la scena d'apertura scelta a caso. Quindi:

- si tolgono la barra di stato e quella di navigazione;
- si conta la quota di pixel che cambiano in modo visibile (soglia 24 su 255);
- si confronta con un **riferimento**: lo stesso conto fra due giri precedenti
  senza cambi di codice dell'app. Uno scatto che cambia molto piu' del suo
  riferimento va guardato a occhio; con `--affianca` si scrive un PNG con
  prima, dopo e la differenza.

Gli scatti si prendono dalla storia di `ci-artifacts/screenshots` con l'API,
senza scaricare il ramo (§17.6). Uso:

    python3 scripts/confronta_scatti.py RIF_PRIMA PRIMA DOPO [--affianca out.png]

dove i tre argomenti sono numeri di giro di CI (run id) i cui scatti sono stati
pubblicati: RIF_PRIMA e PRIMA senza cambi d'app fra loro, DOPO col cambio.
Serve `gh` autenticato, Pillow e numpy (`pip install pillow numpy`).
"""

import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageChops

REPO = "NoximilienCoxen/test-weather"
CACHE = os.environ.get("CACHE", "/tmp/scatti-ci")
SOGLIA = 24


def gh(*argomenti, raw=False):
    cmd = ["gh", "api", *argomenti]
    if raw:
        cmd += ["-H", "Accept: application/vnd.github.raw"]
    return subprocess.run(cmd, check=True, capture_output=True).stdout


def commit_del_giro(giro):
    """Il commit di `ci-artifacts` che ha pubblicato gli scatti di quel giro."""
    pagina = 1
    while pagina < 10:
        commits = json.loads(gh(f"repos/{REPO}/commits?sha=ci-artifacts&path=screenshots&per_page=100&page={pagina}"))
        if not commits:
            break
        for c in commits:
            if c["commit"]["message"].endswith(f"run {giro}"):
                return c["sha"]
        pagina += 1
    sys.exit(f"nessuna pubblicazione di scatti per il giro {giro}")


def scarica(giro):
    cartella = os.path.join(CACHE, str(giro))
    if os.path.isdir(cartella) and os.listdir(cartella):
        return cartella
    os.makedirs(cartella, exist_ok=True)
    sha = commit_del_giro(giro)
    albero = json.loads(gh(f"repos/{REPO}/git/trees/{sha}:screenshots?recursive=1"))
    for voce in albero["tree"]:
        if voce["type"] == "blob" and voce["path"].endswith(".png") and "/" not in voce["path"]:
            open(os.path.join(cartella, voce["path"]), "wb").write(
                gh(f"repos/{REPO}/git/blobs/{voce['sha']}", raw=True)
            )
    return cartella


def ritaglio(percorso):
    a = np.asarray(Image.open(percorso).convert("RGB")).astype(np.int16)
    h = a.shape[0]
    # Senza barra di stato (l'orologio) e senza barra di navigazione.
    return a[int(h * 0.04):int(h * 0.955)]


def quota(a, b):
    if a.shape != b.shape:
        return 100.0
    return float((np.abs(a - b).max(axis=2) > SOGLIA).mean() * 100)


def main():
    argomenti = [x for x in sys.argv[1:] if not x.startswith("--")]
    if len(argomenti) < 3:
        sys.exit(__doc__)
    rif, prima, dopo = (scarica(g) for g in argomenti[:3])
    sospetti = []
    print(f"{'scatto':42} {'riferimento':>12} {'cambio':>10}")
    for nome in sorted(os.listdir(prima)):
        if not all(os.path.exists(os.path.join(c, nome)) for c in (rif, dopo)):
            continue
        r = quota(ritaglio(os.path.join(rif, nome)), ritaglio(os.path.join(prima, nome)))
        c = quota(ritaglio(os.path.join(prima, nome)), ritaglio(os.path.join(dopo, nome)))
        segno = "  <<" if c > max(2 * r, 0.5) else ""
        if segno:
            sospetti.append(nome)
        print(f"{nome:42} {r:11.2f}% {c:9.2f}%{segno}")
    print(f"\n{len(sospetti)} da guardare a occhio: {', '.join(sospetti) or 'nessuno'}")

    if "--affianca" in sys.argv and sospetti:
        uscita = sys.argv[sys.argv.index("--affianca") + 1]
        w, h = 270, 600
        tela = Image.new("RGB", (w * 3 + 20, h * len(sospetti)), "white")
        for i, nome in enumerate(sospetti):
            a = Image.open(os.path.join(prima, nome)).convert("RGB")
            b = Image.open(os.path.join(dopo, nome)).convert("RGB")
            d = ImageChops.difference(a, b).point(lambda v: 255 if v > SOGLIA else 0)
            for j, im in enumerate((a, b, d)):
                tela.paste(im.resize((w, h)), (j * (w + 10), i * h))
        tela.save(uscita)
        print(f"prima | dopo | differenza: {uscita}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
