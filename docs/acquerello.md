# I timbri dell'acquerello di Sala

Chi dipinge le texture vere legga questo. Chi tocca il codice che le usa legga
`ui/sala/SalaAcquerello.kt`.

## La regola che vale per tutti i file

**Conta solo l'alfa.** I timbri sono bianchi su trasparente e l'app li **ritinge**
con la tavolozza della sala: turchese di giorno, piu' chiaro sulla carta scurita,
giallo per il disco, e cosi' via al variare dell'ora e del tempo. Il colore che
dipingi dentro il file **viene buttato via**. Dipingi la forma e la densita', non
la tinta.

L'unica eccezione e' `sala_carta.png`, che e' in scala di grigi e viene
moltiplicata invece che timbrata.

I file vanno in `app/src/main/res/drawable-nodpi/`. `nodpi` non e' un dettaglio:
impedisce ad Android di riscalarli per densita' dello schermo, perche' a
scalarli ci pensa il codice in base alla scena.

Sostituirli e' una copia di file. Nessuna riga di Kotlin cambia.

## I file

| file | misura | cosa e' | cosa ne fa l'app |
|---|---|---|---|
| `sala_macchia_1..4.png` | 512 x 512 | Una massa d'acquerello tonda, bordo irregolare | Timbra le **sette masse** della nuvola, scalate e disposte in profondita'. Quattro varianti ruotate/scalate bastano: sette diverse non si distinguerebbero. Alfa intorno a 0,5-0,6 in uso, quindi **le sovrapposizioni si scuriscono** - e' cosi' che si legge il volume. |
| `sala_disco.png` | 512 x 512 | Il disco solare: piu' tondo e pieno di una massa | Sole di giorno (giallo), luna di notte (grigio chiaro). Dietro le masse quando c'e' nuvola. |
| `sala_pennellata_1..6.png` | 128 x 300 | Un tratto di pioggia, punta in basso | Sei-otto tratti sotto la nuvola, sfalsati. Sono **segni di pennello**, non gocce: nel concept la pioggia e' disegnata. |
| `sala_ombra.png` | 512 x 224 | Ellisse molto diffusa | L'ombra portata sotto l'insieme. |
| `sala_carta.png` | 512 x 512, **affiancabile**, grigi | La fibra della carta | Moltiplicata sotto le macchie del fondo, al 9% su carta chiara e 14% su carta scura. |

## Cosa fa "acquerello" e cosa no

Tre cose, e nessuna e' la sfocatura:

1. **Il bordo spostato.** L'acqua porta il pigmento dove vuole lei. Un cerchio
   sfocato non somiglia a una macchia: somiglia a un cerchio sfocato.
2. **L'accumulo sul filo del bordo.** Il pigmento si deposita dove il lavaggio
   si ferma - la riga piu' scura che fa riconoscere un acquerello a colpo
   d'occhio.
3. **La granulazione dentro.** Il pigmento si posa a chiazze, non uniforme.

**Non servono ombre ne' luci dipinte dentro il timbro**, e anzi danno fastidio:
la scena le ha gia' sue, e una luce dipinta in un timbro che viene ruotato
resta ferma mentre tutto il resto gira.

## Perche' dipinte e non calcolate

`Modifier.blur` di Compose e' API 31; il minimo di questo progetto e' **26**.
Un bordo bagnato calcolato a runtime, su un terzo dei telefoni supportati, non
si vedrebbe affatto. Dipingerlo non e' un ripiego: e' anche il modo giusto di
ottenerlo.

## I file di adesso sono provvisori

Li genera `scripts/texture_acquerello.py`, con del rumore e un po' di
matematica. Non sono arte: sono un **ponte**, perche' la catena - carica,
tinge, timbra, moltiplica - fosse viva e fotografabile in CI prima che qualcuno
aprisse Blender. Il seme e' fisso, quindi due esecuzioni danno gli stessi file.

## Da Blender, in pratica

Il pacchetto `caelum-blender` (font istanziato agli assi dell'app, contorni
delle cifre in SVG, script della scena) contiene le sette masse gia' pronte
come **metaball** alle coordinate vere, che e' la primitiva giusta: sette sfere
separate darebbero sette palle, le metaball si fondono in un guscio continuo.

Per ricavarne un timbro: renderizza **una massa sola**, di fronte, con
illuminazione piatta e fondo trasparente, e porta in alfa il trattamento ad
acquerello. Le altre sei le compone l'app, alle loro profondita'.
