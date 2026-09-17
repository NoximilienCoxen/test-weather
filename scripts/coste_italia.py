"""
Da Natural Earth 1:50m (dominio pubblico) alle coste che servono al radar.

Non e' una carta: e' il riferimento minimo perche' una macchia di pioggia
si possa posare su qualcosa di riconoscibile. Si tiene solo cio' che cade
nella finestra del radar italiano, si semplifica, e si scrive in Kotlin
nello stesso formato di WORLD_COASTS - longitudine e latitudine a coppie.
"""
import json, math, sys

LON0, LON1 = 5.0, 20.5
LAT0, LAT1 = 35.0, 48.5

VICINI = ["France", "Switzerland", "Austria", "Slovenia", "Croatia",
          "Bosnia and Herzegovina", "Montenegro", "Albania", "Greece",
          "Tunisia", "Malta", "Algeria", "Serbia", "Hungary", "Germany",
          "Kosovo", "North Macedonia"]

def perp(p, a, b):
    (x, y), (x1, y1), (x2, y2) = p, a, b
    dx, dy = x2 - x1, y2 - y1
    if dx == 0 and dy == 0:
        return math.hypot(x - x1, y - y1)
    t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)))
    return math.hypot(x - (x1 + t * dx), y - (y1 + t * dy))

def douglas(pts, eps):
    if len(pts) < 3:
        return pts
    dmax, idx = 0.0, 0
    for i in range(1, len(pts) - 1):
        d = perp(pts[i], pts[0], pts[-1])
        if d > dmax:
            dmax, idx = d, i
    if dmax <= eps:
        return [pts[0], pts[-1]]
    return douglas(pts[:idx + 1], eps)[:-1] + douglas(pts[idx:], eps)

def dentro(p):
    return LON0 <= p[0] <= LON1 and LAT0 <= p[1] <= LAT1

def spezza(ring):
    """Un anello tagliato dalla finestra non e' piu' un anello: diventa
    uno o piu' tratti aperti. Si tiene un vertice di margine per lato,
    cosi' la linea esce dal bordo invece di fermarcisi dentro."""
    tratti, corrente = [], []
    n = len(ring)
    for i, p in enumerate(ring):
        vicino = dentro(p) or dentro(ring[(i - 1) % n]) or dentro(ring[(i + 1) % n])
        if vicino:
            corrente.append(p)
        elif corrente:
            tratti.append(corrente)
            corrente = []
    if corrente:
        tratti.append(corrente)
    return tratti

def anelli(feature):
    g = feature["geometry"]
    polys = [g["coordinates"]] if g["type"] == "Polygon" else g["coordinates"]
    return [r for poly in polys for r in poly]

def area(r):
    a = 0.0
    for i in range(len(r)):
        x1, y1 = r[i]
        x2, y2 = r[(i + 1) % len(r)]
        a += x1 * y2 - x2 * y1
    return abs(a) / 2

def kotlin(nome, tratti):
    righe = [f"    // {len(tratti)} tratti, {sum(len(t) for t in tratti)} vertici"]
    corpo = []
    for t in tratti:
        numeri = []
        for lon, lat in t:
            numeri.append(f"{lon:.3f}f, {lat:.3f}f")
        riga, blocco = [], []
        for k, num in enumerate(numeri):
            riga.append(num)
            if len(riga) == 5:
                blocco.append("        " + ", ".join(riga) + ",")
                riga = []
        if riga:
            blocco.append("        " + ", ".join(riga) + ",")
        corpo.append("    floatArrayOf(\n" + "\n".join(blocco) + "\n    ),")
    return f"val {nome}: List<FloatArray> = listOf(\n" + "\n".join(righe + corpo) + "\n)"

d = json.load(open("ne50.geojson"))
feats = {f["properties"].get("NAME"): f for f in d["features"]}

italia = []
for r in sorted(anelli(feats["Italy"]), key=area, reverse=True):
    if area(r) < 0.004:
        continue
    s = douglas(r, 0.012)
    if len(s) >= 4:
        italia.append(s)

vicini = []
for nome in VICINI:
    f = feats.get(nome)
    if not f:
        continue
    for r in anelli(f):
        for t in spezza(r):
            s = douglas(t, 0.035)
            if len(s) >= 3:
                vicini.append(s)

print(f"italia: {len(italia)} anelli, {sum(len(t) for t in italia)} vertici", file=sys.stderr)
print(f"vicini: {len(vicini)} tratti, {sum(len(t) for t in vicini)} vertici", file=sys.stderr)
open("italia.kt.frag", "w").write(kotlin("COSTE_ITALIA", italia) + "\n\n" + kotlin("COSTE_VICINE", vicini) + "\n")
