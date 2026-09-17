#!/usr/bin/env python3
"""
Quanto e' opaco un pixel di un PNG, senza librerie.

Serve a una domanda sola: **la mappa di copertura di RainViewer dice davvero
dove guardano i radar?** Quel livello risponde `200` con un PNG dappertutto -
sopra Forli' e sopra Nairobi - e due `200` non distinguono niente. Se la
copertura e' scritta nel canale alfa, allora il pixel della propria localita'
e' opaco dove un radar c'e' e trasparente dove non c'e', e lo stato "fuori
copertura" puo' tornare con una risposta vera invece che con un rettangolo
disegnato a mano.

Niente Pillow: sul runner non c'e' e installarla per leggere un byte sarebbe
sproporzionato. Il PNG lo si apre a mano - e' zlib piu' un filtro per riga - e
si coprono i quattro tipi di colore che esistono davvero in rete.

    python3 scripts/alfa_png.py tessera.png 72 118
"""
import struct
import sys
import zlib


def leggi(percorso):
    dati = open(percorso, "rb").read()
    if dati[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("non e' un PNG")
    i = 8
    larghezza = altezza = profondita = tipo = 0
    pezzi = []
    tavolozza = b""
    trasparenze = b""
    while i < len(dati):
        lunghezza = struct.unpack(">I", dati[i:i + 4])[0]
        nome = dati[i + 4:i + 8]
        corpo = dati[i + 8:i + 8 + lunghezza]
        if nome == b"IHDR":
            larghezza, altezza, profondita, tipo = struct.unpack(">IIBB", corpo[:10])
        elif nome == b"IDAT":
            pezzi.append(corpo)
        elif nome == b"PLTE":
            tavolozza = corpo
        elif nome == b"tRNS":
            trasparenze = corpo
        elif nome == b"IEND":
            break
        i += 12 + lunghezza
    if profondita != 8:
        raise ValueError(f"profondita' {profondita} non gestita")
    canali = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[tipo]
    grezzi = zlib.decompress(b"".join(pezzi))
    return larghezza, altezza, tipo, canali, tavolozza, trasparenze, grezzi


def righe(larghezza, altezza, canali, grezzi):
    """Toglie il filtro riga per riga. Sono i cinque filtri della specifica."""
    passo = larghezza * canali
    fuori = bytearray()
    prima = bytearray(passo)
    p = 0
    for _ in range(altezza):
        filtro = grezzi[p]
        p += 1
        riga = bytearray(grezzi[p:p + passo])
        p += passo
        for x in range(passo):
            a = riga[x - canali] if x >= canali else 0
            b = prima[x]
            c = prima[x - canali] if x >= canali else 0
            if filtro == 1:
                riga[x] = (riga[x] + a) & 0xFF
            elif filtro == 2:
                riga[x] = (riga[x] + b) & 0xFF
            elif filtro == 3:
                riga[x] = (riga[x] + (a + b) // 2) & 0xFF
            elif filtro == 4:
                p0 = a + b - c
                pa, pb, pc = abs(p0 - a), abs(p0 - b), abs(p0 - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                riga[x] = (riga[x] + pr) & 0xFF
        fuori += riga
        prima = riga
    return fuori


def alfa(percorso, x, y):
    larghezza, altezza, tipo, canali, tavolozza, trasparenze, grezzi = leggi(percorso)
    pixel = righe(larghezza, altezza, canali, grezzi)
    i = (y * larghezza + x) * canali
    if tipo == 6:
        return pixel[i + 3]
    if tipo == 4:
        return pixel[i + 1]
    if tipo == 3:
        indice = pixel[i]
        return trasparenze[indice] if indice < len(trasparenze) else 255
    return 255  # senza canale alfa, tutto opaco


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    percorso, x, y = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    a = alfa(percorso, x, y)
    print(f"alfa({x},{y}) = {a}  ->  {'COPERTO' if a > 0 else 'SCOPERTO'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
