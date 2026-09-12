#!/usr/bin/env python3
"""
Genera le texture **provvisorie** dell'acquerello di Sala.

Non sono arte: sono un ponte. Servono a far vivere la pipeline - codice che
carica, tinge, timbra e moltiplica - **prima** che qualcuno apra Blender, cosi'
il risultato si vede in CI e le vere si infilano dentro con una sostituzione di
file, senza toccare una riga di Kotlin.

Chi dipinge quelle vere legga `docs/acquerello.md`: dice misure, canali e cosa
l'app fa di ognuna. **Conta solo l'alfa**: l'app ritinge tutto con la tavolozza
della sala, quindi il colore dipinto qui dentro viene buttato via.

    python3 scripts/texture_acquerello.py
"""

import os
import numpy as np
from PIL import Image

FUORI = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                     "app", "src", "main", "res", "drawable-nodpi")

rng = np.random.default_rng(7)   # seme fisso: due esecuzioni danno gli stessi file


def rumore(lato, celle, periodico=False):
    """Rumore di valore, interpolato morbido. `periodico` lo rende affiancabile."""
    g = rng.random((celle + 1, celle + 1))
    if periodico:
        g[-1, :] = g[0, :]
        g[:, -1] = g[:, 0]
    y = np.linspace(0, celle, lato, endpoint=False)
    x = np.linspace(0, celle, lato, endpoint=False)
    yi, xi = np.meshgrid(y, x, indexing="ij")
    y0, x0 = yi.astype(int), xi.astype(int)
    fy, fx = yi - y0, xi - x0
    # smoothstep, se no si vedono i rombi della griglia
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
    b = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


def fbm(lato, celle, ottave=4, periodico=False):
    somma = np.zeros((lato, lato))
    peso, tot = 1.0, 0.0
    for o in range(ottave):
        somma += peso * rumore(lato, celle * (2 ** o), periodico)
        tot += peso
        peso *= 0.5
    return somma / tot


def salva_alfa(alfa, nome):
    """Bianco su trasparente: l'alfa e' tutto, l'RGB lo sostituisce l'app."""
    a = np.clip(alfa, 0, 1)
    h, w = a.shape
    img = np.zeros((h, w, 4), dtype=np.uint8)
    img[..., 0:3] = 255
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(os.path.join(FUORI, nome), optimize=True)
    print(f"  {nome}  {w}x{h}")


def macchia(lato=512, irregolarita=0.13, seme_celle=3):
    """
    Una macchia d'acquerello: bordo bagnato irregolare e granulazione dentro.

    Il bordo non e' sfocato, e' **spostato**: l'acqua porta il pigmento dove
    vuole lei, e un cerchio sfocato non somiglia a una macchia, somiglia a un
    cerchio sfocato. Sul bordo il pigmento poi si accumula - la riga scura che
    fa riconoscere un acquerello a colpo d'occhio.
    """
    y, x = np.mgrid[0:lato, 0:lato].astype(float)
    cx = cy = (lato - 1) / 2
    r = np.hypot(x - cx, y - cy) / (lato / 2)

    # il bordo spostato dal rumore
    spinta = (fbm(lato, seme_celle, ottave=3) - 0.5) * 2 * irregolarita
    rw = r + spinta

    R = 0.80
    morbidezza = 0.055
    alfa = np.clip((R - rw) / morbidezza, 0, 1)

    # granulazione: il pigmento si posa a chiazze, non uniforme
    grana = fbm(lato, 7, ottave=4)
    alfa *= 0.72 + 0.28 * grana

    # accumulo sul bordo
    banda = np.exp(-(((rw - R) / 0.075) ** 2))
    alfa = np.clip(alfa + 0.42 * banda * (rw < R), 0, 1)

    # il centro resta piu' chiaro: l'acqua ci sta piu' a lungo
    alfa *= 0.80 + 0.20 * np.clip(rw / R, 0, 1)
    return np.clip(alfa, 0, 1) * 0.92


def disco(lato=512):
    """Il sole: piu' tondo e piu' pieno di una massa nuvolosa, ma sempre bagnato."""
    a = macchia(lato, irregolarita=0.055, seme_celle=2)
    return np.clip(a * 1.08, 0, 1)


def pennellata(largo=128, alto=300):
    """
    Un tratto di pioggia: un segno di pennello, non una goccia.

    Nel concept la pioggia e' **disegnata**: tratti corti, appena incurvati,
    pieni in mezzo e affusolati solo in fondo, come li lascia una punta che si
    solleva. La prima stesura li faceva rastremare da entrambe le parti e
    uscivano **aghi** - sottili, dritti, senza peso. Da qui il profilo pieno:
    la punta e' una sola, in basso.
    """
    y, x = np.mgrid[0:alto, 0:largo].astype(float)
    t = y / (alto - 1)
    # l'incurvatura del tratto: poca, se no sembra un amo
    curva = (largo / 2) + np.sin(t * 1.8 + rng.random() * 3) * largo * 0.10
    # pieno quasi per tutta la corsa, poi si chiude negli ultimi due quinti
    corpo = np.clip(1.0 - np.clip((t - 0.58) / 0.42, 0, 1) ** 1.5, 0, 1)
    # Anche l'attacco si stringe, se no il tratto comincia con un taglio netto
    # in orizzontale: una pennellata non inizia mai con uno spigolo.
    corpo *= np.clip(t / 0.12, 0, 1) ** 0.5
    spessore = np.maximum(largo * 0.30 * corpo, 0.6)
    d = np.abs(x - curva) / spessore
    # Interno **pieno**: con un'esponente alto il tratto si svuotava in mezzo e
    # con l'accumulo sui bordi diventava un tubo - due binari scuri e la luce in
    # mezzo, cioe' una matita, non una pennellata.
    alfa = np.clip(1.0 - d, 0, 1) ** 0.30
    # L'accumulo resta, ma sottile e proprio sul filo del bordo.
    alfa = np.clip(alfa + 0.14 * np.exp(-(((d - 0.94) / 0.10) ** 2)) * (d < 1.0), 0, 1)
    # vita dentro il tratto, se no e' una goccia di vernice
    alfa *= 0.74 + 0.26 * fbm(max(largo, alto), 5, ottave=3)[:alto, :largo]
    # l'attacco in alto e' netto ma non tagliato di netto
    alfa *= np.clip(t * 14, 0, 1)
    return np.clip(alfa, 0, 1) * 0.9


def carta(lato=512):
    """
    La grana della carta, **affiancabile**: l'app la ripete su tutto lo schermo.

    Tenuta bassa di contrasto apposta. Moltiplicata sopra ogni cosa, una grana
    marcata non fa "carta": fa sporco.
    """
    g = fbm(lato, 6, ottave=4, periodico=True)
    fibre = fbm(lato, 26, ottave=2, periodico=True)
    v = 0.72 * g + 0.28 * fibre
    v = (v - v.min()) / (v.max() - v.min())
    return v * 0.30          # solo il 30% di escursione: e' un velo


def ombra(largo=512, alto=224):
    """L'ombra portata sotto l'insieme: un'ellisse molto diffusa."""
    y, x = np.mgrid[0:alto, 0:largo].astype(float)
    d = np.hypot((x - largo / 2) / (largo * 0.42), (y - alto / 2) / (alto * 0.40))
    return np.clip(1 - d, 0, 1) ** 2.2 * 0.5


def main():
    os.makedirs(FUORI, exist_ok=True)
    print("texture provvisorie ->", FUORI)
    for i in range(1, 5):
        salva_alfa(macchia(irregolarita=0.10 + 0.03 * i, seme_celle=2 + i % 3),
                   f"sala_macchia_{i}.png")
    salva_alfa(disco(), "sala_disco.png")
    for i in range(1, 7):
        salva_alfa(pennellata(), f"sala_pennellata_{i}.png")
    salva_alfa(ombra(), "sala_ombra.png")
    # La carta e' l'unica in scala di grigi e non in alfa: moltiplica, non timbra.
    c = carta()
    v = ((1 - c) * 255).astype(np.uint8)
    Image.fromarray(np.dstack([v, v, v]), "RGB").save(
        os.path.join(FUORI, "sala_carta.png"), optimize=True)
    print(f"  sala_carta.png  {c.shape[1]}x{c.shape[0]}  (grigio, moltiplicata)")


if __name__ == "__main__":
    main()
