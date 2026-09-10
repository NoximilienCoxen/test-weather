#!/usr/bin/env python3
"""Le otto scene segnaposto, e la loro profondita'.

Servono a chiudere il motore del diorama **prima** che i dipinti veri esistano:
il codice si compila, la CI fotografa, e quando i file dipinti arrivano si
sostituiscono in `app/src/main/assets/scene/` senza toccare una riga di Kotlin.

Sono bande e sagome, non arte: dichiarano la geometria che il motore si aspetta -
cielo lontano in cima, terreno vicino in fondo - cosi' la parallasse ha qualcosa
di vero da spostare. Un segnaposto piatto direbbe che il motore non funziona.

Solo libreria standard: in questo container non ci sono ne' PIL ne' cwebp, e
Android decodifica il PNG esattamente come il WebP.

    python3 scripts/scene_placeholder.py

**La convenzione della profondita' e' bianco vicino, nero lontano**, la stessa
che vale per i file veri. Se un giorno la scena scorre al contrario, si guarda
qui prima che altrove.
"""

import os
import struct
import zlib

W, H = 768, 1024
OUT = os.path.join("app", "src", "main", "assets", "scene")

# L'orizzonte sta al 58% dell'altezza, dentro la finestra 55-60% chiesta ai
# dipinti veri: il motore colloca gli astri sopra quella riga.
HORIZON = 0.58


def png(path, pixels, greyscale=False):
    """Un PNG scritto a mano: intestazione, dati sgonfiabili, chiusura."""
    depth_type = 0 if greyscale else 2
    stride = W if greyscale else W * 3
    raw = bytearray()
    for y in range(H):
        raw.append(0)  # nessun filtro: le bande comprimono bene lo stesso
        raw += pixels[y * stride:(y + 1) * stride]

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    body = b"\x89PNG\r\n\x1a\n"
    body += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, depth_type, 0, 0, 0))
    body += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    body += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(body)


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(int(lerp(c1[i], c2[i], t)) for i in range(3))


def wave(x, freq, phase):
    """Una collina: seno a mano, cosi' non serve nemmeno `math`."""
    v = (x * freq + phase) % 1.0
    # triangolo addolcito: -1..1 senza chiamate a libreria
    t = v * 2 - 1
    return (1 - t * t) * (1 if v < 0.5 else -1) * 0.5 + 0.0


# Ogni scena: zenit, orizzonte, nuvola, collina lontana, collina media, terreno.
SCENES = {
    "sereno":        ((0x4A, 0x84, 0xC4), (0xC8, 0xDE, 0xEE), (0xFF, 0xFF, 0xFF),
                      (0x8F, 0xA8, 0xB4), (0x6E, 0x8B, 0x74), (0x4C, 0x6B, 0x4E)),
    "poco_nuvoloso": ((0x5B, 0x8C, 0xC0), (0xCF, 0xDD, 0xE6), (0xF4, 0xF6, 0xF8),
                      (0x92, 0xA6, 0xB0), (0x71, 0x8A, 0x76), (0x50, 0x6B, 0x51)),
    "coperto":       ((0x76, 0x80, 0x8C), (0xB4, 0xBC, 0xC4), (0xD8, 0xDC, 0xE0),
                      (0x88, 0x90, 0x98), (0x6C, 0x76, 0x70), (0x4E, 0x56, 0x50)),
    "nebbia":        ((0x9A, 0xA2, 0xA8), (0xD2, 0xD6, 0xD8), (0xE6, 0xE8, 0xEA),
                      (0xAE, 0xB4, 0xB6), (0x8C, 0x94, 0x90), (0x6A, 0x72, 0x6E)),
    "pioggia":       ((0x4E, 0x5A, 0x68), (0x8E, 0x9C, 0xAA), (0xB6, 0xC0, 0xC8),
                      (0x6A, 0x76, 0x82), (0x52, 0x62, 0x5C), (0x3A, 0x4A, 0x44)),
    "temporale":     ((0x2E, 0x33, 0x44), (0x5E, 0x68, 0x7C), (0x8A, 0x92, 0xA4),
                      (0x44, 0x4C, 0x5C), (0x34, 0x40, 0x44), (0x24, 0x2E, 0x32)),
    "neve":          ((0x8C, 0x9C, 0xB0), (0xDC, 0xE4, 0xEC), (0xF6, 0xF8, 0xFA),
                      (0xC4, 0xCC, 0xD4), (0xE0, 0xE6, 0xEA), (0xF0, 0xF2, 0xF4)),
    "grandine":      ((0x3C, 0x46, 0x58), (0x76, 0x84, 0x94), (0xA6, 0xB0, 0xBC),
                      (0x58, 0x62, 0x70), (0x46, 0x52, 0x50), (0x30, 0x3C, 0x3A)),
}


def build(name, palette):
    zenith, horizon_c, cloud, far, mid, ground = palette
    art = bytearray(W * H * 3)
    dep = bytearray(W * H)

    hy = H * HORIZON
    for y in range(H):
        v = y / H
        for x in range(W):
            u = x / W
            i3 = (y * W + x) * 3
            i1 = y * W + x

            if y < hy:
                # ── Cielo ──────────────────────────────────────────────────
                t = y / hy
                col = mix(zenith, horizon_c, t * t)
                d = 0.06 + 0.10 * t          # il cielo e' il piu' lontano
                # Una fascia di nuvole al 30-45%, con un bordo mosso.
                cl = wave(u, 2.3, 0.15) * 0.06 + wave(u, 5.1, 0.62) * 0.03
                top, bot = 0.26 + cl, 0.46 + cl
                if top < v < bot:
                    f = (v - top) / (bot - top)
                    soft = 1 - abs(f * 2 - 1)
                    col = mix(col, cloud, min(1.0, soft * 1.4))
                    d = lerp(d, 0.30, min(1.0, soft * 1.4))
            else:
                # ── Terra ──────────────────────────────────────────────────
                t = (y - hy) / (H - hy)
                col = mix(far, ground, t)
                d = lerp(0.42, 1.0, t)

            # Due creste: una lontana appena sotto l'orizzonte, una piu' vicina.
            for freq, phase, base, tint, depth in (
                (1.7, 0.30, 0.575, far, 0.40),
                (2.9, 0.71, 0.660, mid, 0.62),
            ):
                ridge = base + wave(u, freq, phase) * 0.055
                if v > ridge:
                    col = tint
                    d = depth
                    break

            # Il terreno davanti a tutto, che e' cio' che la parallasse muove.
            fore = 0.80 + wave(u, 1.1, 0.44) * 0.03
            if v > fore:
                col = mix(ground, (0, 0, 0), 0.35)
                d = lerp(0.86, 1.0, (v - fore) / (1 - fore))

            art[i3], art[i3 + 1], art[i3 + 2] = col
            dep[i1] = max(0, min(255, int(d * 255)))

    os.makedirs(OUT, exist_ok=True)
    png(os.path.join(OUT, "scena_%s.png" % name), art)
    png(os.path.join(OUT, "scena_%s_z.png" % name), dep, greyscale=True)
    return name


if __name__ == "__main__":
    for n, p in SCENES.items():
        build(n, p)
        print("  scena", n)
    print("otto scene in", OUT)
