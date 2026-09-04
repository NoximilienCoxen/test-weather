#!/usr/bin/env python3
"""Quali versioni esistono davvero oggi, chiesto ai repository.

Da dentro il container di sviluppo `dl.google.com` e Maven Central non si
raggiungono: 403 al CONNECT del proxy. Quindi le versioni delle dipendenze non
si possono verificare dove si scrivono, e scriverle a memoria e' esattamente il
modo in cui un progetto si ritrova una Compose BOM del 2026 accanto a un
core-ktx del 2024 senza che nessuno se ne accorga.

Questo script gira in CI, dove la rete c'e', e pubblica l'elenco fra gli
artefatti. E' lo stesso metodo gia' usato per i modelli numerici
(`probe_models.py`) e per il formato del feed MeteoAlarm: non si deduce, si
guarda.

Legge i coordinate direttamente da `gradle/libs.versions.toml`, cosi' una
dipendenza aggiunta domani finisce nell'elenco senza che nessuno debba
ricordarsi di aggiungerla anche qui.
"""

import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

TOML = "gradle/libs.versions.toml"
OUT = os.environ.get("OUT", "/tmp/ciout/dipendenze.txt")

# Google ospita androidx e com.android, Maven Central tutto il resto.
REPOS = [
    ("google", "https://dl.google.com/dl/android/maven2"),
    ("central", "https://repo1.maven.org/maven2"),
]

# Una versione "stabile" non ha una parola dopo il numero. alpha, beta, rc, dev
# e le istantanee non vanno proposte: un progetto che si aggiorna deve restare
# installabile.
UNSTABLE = re.compile(r"(?i)(alpha|beta|rc|dev|snapshot|-m\d|eap)")


def versions(toml_text):
    """La tabella [versions]: nome -> valore."""
    block = re.search(r"\[versions\](.*?)(?=\n\[|\Z)", toml_text, re.S)
    out = {}
    for line in (block.group(1) if block else "").splitlines():
        m = re.match(r'\s*([\w-]+)\s*=\s*"([^"]+)"', line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def coordinates(toml_text, vers):
    """Ogni libreria e ogni plugin, come (group, artifact, versione in uso)."""
    found = []
    for m in re.finditer(
        r'^\s*[\w-]+\s*=\s*\{([^}]*)\}', toml_text, re.M
    ):
        body = m.group(1)
        group = re.search(r'group\s*=\s*"([^"]+)"', body)
        name = re.search(r'name\s*=\s*"([^"]+)"', body)
        plugin = re.search(r'id\s*=\s*"([^"]+)"', body)
        ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', body)
        cur = vers.get(ref.group(1), "-") if ref else "(dalla BOM)"
        if group and name:
            found.append((group.group(1), name.group(1), cur))
        elif plugin:
            # Un plugin Gradle si pubblica come <id>:<id>.gradle.plugin
            pid = plugin.group(1)
            found.append((pid, f"{pid}.gradle.plugin", cur))
    return found


def latest(group, artifact):
    """L'ultima release stabile, dal primo repository che risponde."""
    path = group.replace(".", "/") + "/" + artifact + "/maven-metadata.xml"
    for repo_name, base in REPOS:
        url = f"{base}/{path}"
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                if r.status != 200:
                    continue
                root = ET.fromstring(r.read())
        except Exception:
            continue
        all_v = [e.text for e in root.iter("version") if e.text]
        stable = [v for v in all_v if not UNSTABLE.search(v)]
        if not stable:
            continue
        return stable[-1], repo_name
    return None, None


def main():
    toml_text = open(TOML).read()
    vers = versions(toml_text)
    rows = []
    for group, artifact, cur in coordinates(toml_text, vers):
        newest, repo = latest(group, artifact)
        if newest is None:
            rows.append((f"{group}:{artifact}", cur, "?", "irraggiungibile"))
        else:
            stato = "aggiornata" if newest == cur else "DA AGGIORNARE"
            if cur == "(dalla BOM)":
                stato = "la decide la BOM"
            rows.append((f"{group}:{artifact}", cur, newest, f"{stato} [{repo}]"))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    w = max(len(r[0]) for r in rows) if rows else 10
    lines = [f"{'coordinate'.ljust(w)}  {'in uso':<14} {'ultima':<14} stato", "-" * (w + 46)]
    for r in rows:
        lines.append(f"{r[0].ljust(w)}  {r[1]:<14} {r[2]:<14} {r[3]}")
    text = "\n".join(lines)
    open(OUT, "w").write(text + "\n")
    print(text)
    # Non fa fallire il giro: una dipendenza vecchia non e' un errore di
    # compilazione, ed e' una decisione da prendere leggendo, non un blocco.
    return 0


if __name__ == "__main__":
    sys.exit(main())
