#!/usr/bin/env python3
"""Quali versioni delle azioni di GitHub esistono, e su quale Node girano.

Gemello di `probe_deps.py`, per il workflow invece che per Gradle. GitHub ha
cominciato ad avvisare che alcune azioni usate qui girano su Node 20, deprecato
e gia' forzato su Node 24 (CONTESTO §46). Per alzarle serve sapere quale
versione dichiara `node24`, e da dentro il container di sviluppo i repository
delle azioni non si leggono: la domanda si fa qui, dove la rete c'e'.

Legge le azioni direttamente da `.github/workflows/build.yml`, cosi' un'azione
aggiunta domani finisce nell'elenco senza ricordarsi di aggiungerla anche qui.
"""

import json
import os
import re
import sys
import urllib.request

WORKFLOW = ".github/workflows/build.yml"
OUT = os.environ.get("OUT", "/tmp/ciout/azioni.txt")
TOKEN = os.environ.get("GITHUB_TOKEN", "")


def get(url, raw=False):
    req = urllib.request.Request(url)
    req.add_header("Accept", "application/vnd.github.raw" if raw else "application/vnd.github+json")
    req.add_header("User-Agent", "caelum-probe")
    if TOKEN:
        req.add_header("Authorization", f"Bearer {TOKEN}")
    with urllib.request.urlopen(req, timeout=20) as r:
        body = r.read().decode("utf-8", "replace")
    return body if raw else json.loads(body)


def azioni(testo):
    """(repository, sottocartella, versione in uso) per ogni `uses:`."""
    trovate = {}
    for m in re.finditer(r"uses:\s*([\w.-]+/[\w.-]+)((?:/[\w.-]+)*)@([\w.-]+)", testo):
        trovate[(m.group(1), m.group(2).lstrip("/"))] = m.group(3)
    return [(r, sub, v) for (r, sub), v in sorted(trovate.items())]


def node_di(repo, sub, ref):
    """Il `runs.using` dell'action.yml a quel riferimento."""
    for nome in ("action.yml", "action.yaml"):
        percorso = f"{sub}/{nome}" if sub else nome
        try:
            testo = get(f"https://api.github.com/repos/{repo}/contents/{percorso}?ref={ref}", raw=True)
        except Exception:
            continue
        m = re.search(r"^\s*using:\s*['\"]?([\w-]+)", testo, re.M)
        return m.group(1) if m else "?"
    return "?"


def main():
    righe = []
    for repo, sub, in_uso in azioni(open(WORKFLOW).read()):
        nome = f"{repo}/{sub}" if sub else repo
        try:
            ultima = get(f"https://api.github.com/repos/{repo}/releases/latest")["tag_name"]
        except Exception as e:
            righe.append((nome, in_uso, "?", "?", "?", f"irraggiungibile: {e}"))
            continue
        # La versione in uso e' spesso solo la maggiore ("v4"): il suo Node si
        # legge dal riferimento mobile, che punta all'ultima di quella serie.
        righe.append((nome, in_uso, node_di(repo, sub, in_uso), ultima, node_di(repo, sub, ultima), ""))

    w = max((len(r[0]) for r in righe), default=10)
    linee = [f"{'azione'.ljust(w)}  {'in uso':<8} {'node':<8} {'ultima':<10} {'node':<8} nota", "-" * (w + 50)]
    for r in righe:
        linee.append(f"{r[0].ljust(w)}  {r[1]:<8} {r[2]:<8} {r[3]:<10} {r[4]:<8} {r[5]}")
    testo = "\n".join(linee)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w").write(testo + "\n")
    print(testo)
    return 0


if __name__ == "__main__":
    sys.exit(main())
