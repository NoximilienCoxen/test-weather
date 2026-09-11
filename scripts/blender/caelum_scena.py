"""Le scene di Caelum, costruite in Blender e rese per il diorama dell'app.

## Come si esegue

**Non dalla Console Python di Blender.** Quella e' un interprete riga per riga:
incollandoci dentro un file con funzioni e righe vuote, la prima riga vuota
chiude la definizione e da li' in poi sono errori di indentazione a catena. Non
e' un difetto dello script, e' cosa fa una REPL. Le tre strade che funzionano:

1. **Editor di testo** (la piu' comoda). In Blender: `Scripting` in alto, poi
   `Apri`, si sceglie questo file, e `Esegui` (o Alt+P). La scena si costruisce
   e resta li' da guardare e da ritoccare.

2. **Da terminale**, senza aprire Blender:

       blender --background --python scripts/blender/caelum_scena.py -- \
           --variante astri_nuvole_pioggia --out app/src/main/assets/scene

3. **Dalla console, ma facendola leggere come file**, che e' il modo giusto di
   dare un file a una REPL:

       p = "/percorso/assoluto/caelum_scena.py"
       exec(compile(open(p).read(), p, "exec"))

Questo script **prepara** la scena, non la sostituisce. Quello che fa a mano e' il
lavoro noioso e facile da sbagliare: la camera con la focale giusta,
l'orizzonte all'altezza che l'app si aspetta, il passe di profondita' tarato e
**invertito**, e il ciclo cucito.

Blender 4.x. Nessun addon.

## Le cinque varianti

    astri                        il sole e la luna, cielo terso
    astri_nuvole                 piu' i cartelli di nuvole
    astri_nuvole_pioggia         piu' la pioggia
    astri_nuvole_pioggia_forte   la stessa, piu' fitta e piu' scura
    astri_nuvole_pioggia_fulmini piu' il bagliore del fulmine

## Due cose da sapere prima di renderizzare

**Le nuvole sono cartelli, non volumi.** Sembra una scorciatoia e non lo e': il
passe Mist non scrive una profondita' sensata attraverso un volume, e senza
profondita' vera la parallasse dell'app non ha niente da spostare. I cartelli a
distanze diverse danno una profondita' leggibile **e** sono il modo piu' diretto
per un cielo ad acquerello. Il volumetrico si tiene per i coni di luce, dove
serve davvero e dove non deve scrivere profondita'.

**La pioggia qui e' spenta di default**, anche nelle varianti che la nominano.
L'app la disegna da se' in `ui/scene/SceneWeather.kt`, con la densita' presa dai
millimetri veri e l'inclinazione dal vento vero: dipingerla dentro vorrebbe dire
che un acquazzone e una pioggerella sono la stessa immagine con due etichette.
Con `--pioggia-dipinta` la si accende comunque - serve sapere che allora va
spento il livello dell'app per quella scena, o piovera' due volte.
"""

import math
import os
import random
import sys

import bpy
from mathutils import Vector

# ─────────────────────────────────────────────────────────────────────────────
# Le misure, in un posto solo
# ─────────────────────────────────────────────────────────────────────────────

# Il formato che l'app si aspetta: verticale tre a quattro.
LARGHEZZA, ALTEZZA = 1536, 2048

# Il ciclo. Sessanta fotogrammi a dodici al secondo fanno cinque secondi, che
# per delle nuvole sono abbastanza perche' il ritorno non si noti.
CICLO = 60
FPS = 12

# La camera. `sensor_fit = VERTICAL` con sensore 36 e focale 32 da' un campo
# verticale di 58 gradi: abbastanza largo da tenere insieme il cielo e la linea
# di terra senza l'aria da grandangolo.
SENSORE_MM = 36.0
FOCALE_MM = 32.0
CAMERA_Z = 3.0

# L'orizzonte al 60% dell'altezza. Novanta gradi lo mette al centro; l'alzata
# che serve si ricava dal campo verticale, e sono i sei gradi qui sotto.
ORIZZONTE = 0.60
CAMPO_V = 2.0 * math.atan(SENSORE_MM / 2.0 / FOCALE_MM)
ALZATA = math.atan(2.0 * (ORIZZONTE - 0.5) * math.tan(CAMPO_V / 2.0))

# Il passe Mist: da qui a li'. Start sul primo piano vero, Depth oltre la nuvola
# piu' lontana, se no le distanze si schiacciano tutte sullo stesso valore.
MIST_START = 5.0
MIST_DEPTH = 950.0

ACQUA_LATO = 600.0

VARIANTI = {
    "astri": dict(nuvole=0, pioggia=0.0, fulmini=False, copertura=0.0),
    "astri_nuvole": dict(nuvole=7, pioggia=0.0, fulmini=False, copertura=0.45),
    "astri_nuvole_pioggia": dict(nuvole=9, pioggia=0.5, fulmini=False, copertura=0.75),
    "astri_nuvole_pioggia_forte": dict(nuvole=11, pioggia=1.0, fulmini=False, copertura=0.92),
    "astri_nuvole_pioggia_fulmini": dict(nuvole=12, pioggia=1.0, fulmini=True, copertura=1.0),
}

# Come si chiamera' il file, cioe' quale scena dell'app va a sostituire.
NOMI_APP = {
    "astri": "sereno",
    "astri_nuvole": "poco_nuvoloso",
    "astri_nuvole_pioggia": "pioggia",
    "astri_nuvole_pioggia_forte": "coperto",
    "astri_nuvole_pioggia_fulmini": "temporale",
}


# ─────────────────────────────────────────────────────────────────────────────
# Attrezzi
# ─────────────────────────────────────────────────────────────────────────────

def svuota():
    """Via tutto: uno script che si esegue due volte non deve raddoppiare."""
    for collezione in (bpy.data.objects, bpy.data.meshes, bpy.data.materials,
                       bpy.data.lights, bpy.data.cameras, bpy.data.curves):
        for elemento in list(collezione):
            collezione.remove(elemento, do_unlink=True)


def per_tipo(nodi, tipo):
    """Il primo nodo di un certo tipo, o nulla.

    I nomi dei nodi si possono cambiare, duplicare, tradurre; `bl_idname` no.
    Cercare per nome funziona finche' il file di partenza e' quello previsto, e
    quando smette di funzionare l'errore arriva a tre righe di distanza dalla
    causa, parlando di un `None` invece che di un nodo che non c'era.
    """
    return next((n for n in nodi if n.bl_idname == tipo), None)


def materiale(nome, colore, emissione=0.0, ruvidita=0.9, alpha_nodo=None):
    """Un materiale semplice, con l'emissione quando serve.

    `alpha_nodo` e' una funzione che riceve i nodi e torna il socket da
    collegare all'alpha: e' come le nuvole si ritagliano da un piano.
    """
    mat = bpy.data.materials.new(nome)
    mat.use_nodes = True
    nodi = mat.node_tree.nodes
    fili = mat.node_tree.links
    # **Per tipo e non per nome.** `nodes.get("Principled BSDF")` dipende da
    # come si chiama il nodo nel file di partenza, e basta un template diverso
    # o una versione che lo rinomina per ritrovarsi un `None` e un errore a
    # tre righe di distanza da dove sta la causa.
    bsdf = per_tipo(nodi, "ShaderNodeBsdfPrincipled")
    if bsdf is None:
        bsdf = nodi.new("ShaderNodeBsdfPrincipled")
        uscita = per_tipo(nodi, "ShaderNodeOutputMaterial") or nodi.new("ShaderNodeOutputMaterial")
        fili.new(bsdf.outputs[0], uscita.inputs["Surface"])
    bsdf.inputs["Base Color"].default_value = (*colore, 1.0)
    bsdf.inputs["Roughness"].default_value = ruvidita
    if "Emission Color" in bsdf.inputs:
        bsdf.inputs["Emission Color"].default_value = (*colore, 1.0)
        bsdf.inputs["Emission Strength"].default_value = emissione
    if alpha_nodo is not None:
        fili.new(alpha_nodo(nodi, fili), bsdf.inputs["Alpha"])
        trasparenza(mat)
    return mat


def trasparenza(mat):
    """Dire a Blender che questo materiale ha dei buchi, su tre versioni diverse.

    E' il punto in cui uno script scritto per una versione si pianta su
    un'altra. `blend_method` e `shadow_method` sono spariti con EEVEE Next
    nella 4.2-4.3, sostituiti da `surface_render_method`; nelle versioni prima
    esistono solo i vecchi. Si prova quello che c'e' e si tace su quello che non
    c'e': una nuvola senza trasparenza si vede subito, un errore qui fermerebbe
    tutto lo script per un attributo rinominato.
    """
    if hasattr(mat, "surface_render_method"):
        mat.surface_render_method = "BLENDED"
    if hasattr(mat, "blend_method"):
        try:
            mat.blend_method = "BLEND"
        except TypeError:
            pass
    if hasattr(mat, "shadow_method"):
        try:
            mat.shadow_method = "HASHED"
        except TypeError:
            pass


class interpolazione_lineare:
    """Tutte le chiavi nascono lineari, invece di raddrizzarle dopo.

    **E' la correzione di uno script che si piantava su Blender 4.4.** Prima si
    inserivano le chiavi e poi si rileggevano le curve per metterle a `LINEAR`,
    passando da `azione.fcurves`. Nella 4.4 le azioni sono diventate a strati -
    livelli, strisce, sacche di canali, una per slot - e `fcurves` su un'azione
    nuova non esiste piu': `AttributeError` a meta' della costruzione
    dell'acqua.

    Si potrebbe inseguire la nuova API e tenere un ramo per ogni versione. Ma la
    domanda vera non e' "come si raggiungono le curve": e' **come nascono le
    chiavi**, e per quella c'e' una preferenza che ha lo stesso nome da dieci
    versioni. Impostata prima di inserire, non c'e' piu' niente da raddrizzare
    dopo, e il codice non sa piu' come sia fatta un'azione dentro.

    Si rimette com'era uscendo: e' una preferenza dell'utente, non nostra.
    """

    def __enter__(self):
        self.modifica = bpy.context.preferences.edit
        self.prima = self.modifica.keyframe_new_interpolation_type
        self.modifica.keyframe_new_interpolation_type = "LINEAR"
        return self

    def __exit__(self, *_):
        self.modifica.keyframe_new_interpolation_type = self.prima
        return False


def cicla(oggetto, campo, partenza, arrivo, indice=None):
    """Un valore che va da `partenza` ad `arrivo` e torna al punto di partenza.

    **La regola del ciclo cucito e' tutta qui**: si mette la chiave finale al
    fotogramma CICLO+1, non a CICLO, e si renderizza fino a CICLO. Cosi'
    l'ultimo fotogramma reso e' quello *prima* del ritorno, e riprodotto dopo
    di se' ricomincia senza salto. Mettendo la chiave a CICLO si renderebbe due
    volte lo stesso istante e il ciclo scatterebbe.
    """
    for fotogramma, valore in ((1, partenza), (CICLO + 1, arrivo)):
        if indice is None:
            setattr(oggetto, campo, valore)
            oggetto.keyframe_insert(data_path=campo, frame=fotogramma)
        else:
            getattr(oggetto, campo)[indice] = valore
            oggetto.keyframe_insert(data_path=campo, frame=fotogramma, index=indice)


# ─────────────────────────────────────────────────────────────────────────────
# I pezzi della scena
# ─────────────────────────────────────────────────────────────────────────────

def acqua():
    """Il piano d'acqua, con una increspatura che compie un ciclo intero."""
    bpy.ops.mesh.primitive_plane_add(size=ACQUA_LATO, location=(0, ACQUA_LATO / 2.4, 0))
    piano = bpy.context.active_object
    piano.name = "acqua"

    mat = bpy.data.materials.new("acqua")
    mat.use_nodes = True
    nodi, fili = mat.node_tree.nodes, mat.node_tree.links
    bsdf = per_tipo(nodi, "ShaderNodeBsdfPrincipled")
    bsdf.inputs["Base Color"].default_value = (0.30, 0.38, 0.46, 1.0)
    bsdf.inputs["Roughness"].default_value = 0.18

    # L'increspatura: una Wave la cui **fase avanza di un periodo intero** sul
    # ciclo. Qualunque altro valore e l'acqua salta tornando al primo fotogramma.
    onda = nodi.new("ShaderNodeTexWave")
    onda.inputs["Scale"].default_value = 2.5
    onda.inputs["Distortion"].default_value = 6.0
    onda.inputs["Detail"].default_value = 3.0
    urto = nodi.new("ShaderNodeBump")
    urto.inputs["Strength"].default_value = 0.12
    fili.new(onda.outputs["Fac"], urto.inputs["Height"])
    fili.new(urto.outputs["Normal"], bsdf.inputs["Normal"])

    mappa = nodi.new("ShaderNodeMapping")
    coord = nodi.new("ShaderNodeTexCoord")
    fili.new(coord.outputs["Generated"], mappa.inputs["Vector"])
    fili.new(mappa.outputs["Vector"], onda.inputs["Vector"])
    # Un periodo intero: la scala e' 2.5, quindi lo spostamento e' 1/2.5.
    for fotogramma, valore in ((1, 0.0), (CICLO + 1, 1.0 / 2.5)):
        mappa.inputs["Location"].default_value[1] = valore
        mappa.inputs["Location"].keyframe_insert("default_value", index=1, frame=fotogramma)
    piano.data.materials.append(mat)
    return piano


def astro(nome, posizione, raggio, colore, emissione):
    """Il sole o la luna: una sfera che si illumina da se'."""
    bpy.ops.mesh.primitive_uv_sphere_add(radius=raggio, location=posizione, segments=48, ring_count=24)
    corpo = bpy.context.active_object
    corpo.name = nome
    bpy.ops.object.shade_smooth()
    corpo.data.materials.append(materiale(nome, colore, emissione=emissione, ruvidita=1.0))
    return corpo


def luce_del_sole(posizione, forza, copertura):
    """Il sole come sorgente, distinto dal suo disco.

    Due oggetti per una cosa sola, e serve: il disco e' cio' che si vede, la
    lampada e' cio' che illumina. Con la sola lampada non ci sarebbe niente in
    cielo; con la sola sfera emissiva le nuvole non avrebbero un'ombra da cui
    ritagliare i coni di luce, che sono meta' del disegno.
    """
    dato = bpy.data.lights.new("sole", type="SUN")
    dato.energy = forza * (1.0 - 0.45 * copertura)
    dato.angle = math.radians(1.5)
    dato.color = (1.0, 0.93, 0.78)
    lampada = bpy.data.objects.new("luce_sole", dato)
    bpy.context.collection.objects.link(lampada)
    lampada.location = posizione
    # Puntata all'origine: `track_to` a mano, senza vincoli da risolvere.
    lampada.rotation_euler = (Vector((0, 0, 0)) - Vector(posizione)).to_track_quat("-Z", "Y").to_euler()
    return lampada


def nuvole(quante, copertura, seme=7):
    """I cartelli, a profondita' diverse, che derivano e tornano indietro.

    **Il seme e' fisso.** Due esecuzioni dello script devono dare la stessa
    scena, se no una scena ritoccata a mano non si puo' piu' rigenerare.
    """
    rnd = random.Random(seme)
    fatti = []
    for indice in range(quante):
        # Piu' lontane le grandi e in alto, piu' vicine le piccole: e' cosi' che
        # la profondita' si legge anche da ferma.
        distanza = 160.0 + (760.0 * indice / max(quante - 1, 1))
        quota = 120.0 + rnd.uniform(0.0, 240.0) + distanza * 0.18
        larghezza = 90.0 + distanza * 0.55
        bpy.ops.mesh.primitive_plane_add(size=1.0)
        carta = bpy.context.active_object
        carta.name = f"nuvola_{indice:02d}"
        carta.scale = (larghezza, 1.0, larghezza * 0.42)
        # In piedi, di faccia alla camera: una nuvola sdraiata si vede di taglio.
        carta.rotation_euler = (math.radians(90.0), 0.0, 0.0)
        partenza = rnd.uniform(-1.0, 1.0) * larghezza * 0.35
        carta.location = (partenza, distanza, quota)

        def alpha(nodi, fili, _s=rnd.random()):
            rumore = nodi.new("ShaderNodeTexNoise")
            rumore.inputs["Scale"].default_value = 1.6
            rumore.inputs["Detail"].default_value = 6.0
            rumore.inputs["Roughness"].default_value = 0.62
            if "W" in rumore.inputs:
                rumore.inputs["W"].default_value = _s * 20.0
                rumore.noise_dimensions = "4D"
            rampa = nodi.new("ShaderNodeValToRGB")
            # La soglia decide quanto e' coperto il cielo: stringendo la rampa
            # la nuvola si compatta invece di sfilacciarsi.
            rampa.color_ramp.elements[0].position = 0.42 - 0.16 * copertura
            rampa.color_ramp.elements[1].position = 0.66 - 0.10 * copertura
            fili.new(rumore.outputs["Fac"], rampa.inputs["Fac"])
            return rampa.outputs["Color"]

        grigio = 0.82 - 0.45 * copertura * (1.0 - indice / max(quante, 1) * 0.4)
        carta.data.materials.append(
            materiale(f"nuvola_{indice:02d}", (grigio, grigio * 1.02, grigio * 1.06),
                      ruvidita=1.0, alpha_nodo=alpha)
        )

        # La deriva: le lontane si muovono meno, come si vede dal vero.
        corsa = (18.0 + 26.0 * (1.0 - distanza / 920.0)) * rnd.uniform(0.7, 1.3)
        cicla(carta, "location", partenza, partenza + corsa, indice=0)
        fatti.append(carta)
    return fatti


def pioggia(intensita, seme=13):
    """Gocce come sistema di particelle, e il suo limite dichiarato.

    **La pioggia e' l'unico pezzo che non cuce il ciclo.** Le particelle hanno
    uno stato che avanza, e riportarlo indietro al fotogramma uno e' un salto
    visibile. Si puo' nascondere - gocce corte, molte, veloci - ma non togliere.
    E' la ragione principale per cui l'app la disegna da se'.
    """
    bpy.ops.mesh.primitive_plane_add(size=260.0, location=(0, 260.0, 220.0))
    emettitore = bpy.context.active_object
    emettitore.name = "pioggia_sorgente"
    emettitore.hide_render = True

    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, -500.0))
    goccia = bpy.context.active_object
    goccia.name = "goccia"
    goccia.scale = (0.06, 0.06, 1.6)
    goccia.data.materials.append(materiale("goccia", (0.62, 0.72, 0.86), emissione=0.4))

    modificatore = emettitore.modifiers.new("pioggia", type="PARTICLE_SYSTEM")
    impostazioni = modificatore.particle_system.settings
    impostazioni.count = int(900 * intensita)
    impostazioni.frame_start = -40
    impostazioni.frame_end = CICLO
    impostazioni.lifetime = 70
    impostazioni.normal_factor = -14.0
    impostazioni.render_type = "OBJECT"
    impostazioni.instance_object = goccia
    impostazioni.particle_size = 1.0
    impostazioni.physics_type = "NEWTON"
    impostazioni.effector_weights.gravity = 1.0
    return emettitore


def fulmine(seme=29):
    """Il bagliore, che e' quello che di un fulmine si vede davvero.

    Non una saetta disegnata: una saetta va dove va, e sopra una composizione
    che non la prevede finisce dietro una nuvola o dentro l'acqua. Quello che
    cambia la scena e' **la luce che salta per un istante**, ed e' quello che si
    anima qui. Due lampi a distanza diversa dentro il ciclo, cosi' non si legge
    come un semaforo.
    """
    dato = bpy.data.lights.new("fulmine", type="AREA")
    dato.energy = 0.0
    dato.size = 120.0
    dato.color = (0.86, 0.90, 1.0)
    lampada = bpy.data.objects.new("fulmine", dato)
    bpy.context.collection.objects.link(lampada)
    lampada.location = (-90.0, 420.0, 300.0)
    lampada.rotation_euler = (math.radians(70.0), 0.0, math.radians(-20.0))

    # I due lampi cadono dentro il ciclo e si spengono prima della fine: un
    # bagliore a cavallo del ritorno si vedrebbe tagliato.
    picchi = [(14, 90000.0), (16, 22000.0), (41, 52000.0)]
    dato.energy = 0.0
    dato.keyframe_insert("energy", frame=1)
    for fotogramma, forza in picchi:
        dato.energy = 0.0
        dato.keyframe_insert("energy", frame=fotogramma - 1)
        dato.energy = forza
        dato.keyframe_insert("energy", frame=fotogramma)
        dato.energy = 0.0
        dato.keyframe_insert("energy", frame=fotogramma + 3)
    dato.energy = 0.0
    dato.keyframe_insert("energy", frame=CICLO + 1)
    return lampada


# ─────────────────────────────────────────────────────────────────────────────
# Camera, motore, profondita'
# ─────────────────────────────────────────────────────────────────────────────

def camera():
    dato = bpy.data.cameras.new("camera")
    dato.sensor_fit = "VERTICAL"
    dato.sensor_height = SENSORE_MM
    dato.lens = FOCALE_MM
    obiettivo = bpy.data.objects.new("camera", dato)
    bpy.context.collection.objects.link(obiettivo)
    obiettivo.location = (0.0, 0.0, CAMERA_Z)
    # Novanta gradi guarda dritto verso +Y; l'alzata porta l'orizzonte dove
    # l'app lo vuole. Vedi ORIZZONTE qui sopra.
    obiettivo.rotation_euler = (math.radians(90.0) + ALZATA, 0.0, 0.0)
    bpy.context.scene.camera = obiettivo
    return obiettivo


def motore(scena):
    """EEVEE e non Cycles, e non e' una resa al tempo di calcolo.

    Sessanta fotogrammi da rifare a ogni ritocco di colore sono il genere di
    costo che fa smettere di ritoccare, e il trattamento ad acquerello che va
    sopra appiattisce proprio le differenze che Cycles paga care. Chi vuole
    Cycles cambia questa riga: il resto della scena non se ne accorge.
    """
    for nome in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
        try:
            scena.render.engine = nome
            break
        except TypeError:
            continue

    scena.render.resolution_x = LARGHEZZA
    scena.render.resolution_y = ALTEZZA
    scena.render.resolution_percentage = 100
    scena.render.fps = FPS
    scena.frame_start = 1
    scena.frame_end = CICLO
    scena.render.film_transparent = False

    # **Standard e non AgX.** Il trattamento ad acquerello vive nel
    # compositore, e una curva di tono che schiaccia gia' le alte luci gli
    # toglie proprio il materiale su cui lavorare.
    scena.view_settings.view_transform = "Standard"
    scena.view_settings.look = "None"

    occhio = scena.eevee
    for campo, valore in (("use_bloom", True), ("taa_render_samples", 64),
                          ("use_gtao", True), ("volumetric_samples", 64)):
        if hasattr(occhio, campo):
            setattr(occhio, campo, valore)


def profondita(scena):
    """Il passe Mist, tarato e **invertito**.

    L'app vuole **bianco vicino, nero lontano**; il Mist di Blender esce al
    contrario. E' il lancio di moneta che, sbagliato, fa scorrere la scena nel
    verso opposto senza che nessun errore lo dica: da qui il nodo Invert, che e'
    l'unica ragione per cui questa funzione esiste.
    """
    livello = scena.view_layers[0]
    livello.use_pass_mist = True

    mondo = scena.world or bpy.data.worlds.new("mondo")
    scena.world = mondo
    # **Un mondo appena creato non ha nodi**, e `node_tree` e' `None`: due righe
    # piu' sotto sarebbe un errore su un attributo di nulla, col messaggio che
    # parla di `NoneType` invece che di questo.
    mondo.use_nodes = True
    mondo.mist_settings.use_mist = True
    mondo.mist_settings.start = MIST_START
    mondo.mist_settings.depth = MIST_DEPTH
    mondo.mist_settings.falloff = "LINEAR"
    sfondo = next(
        (n for n in mondo.node_tree.nodes if n.bl_idname == "ShaderNodeBackground"), None
    )
    if sfondo is not None:
        sfondo.inputs[0].default_value = (0.52, 0.60, 0.72, 1.0)
        sfondo.inputs[1].default_value = 0.6

    scena.use_nodes = True
    albero = scena.node_tree
    for nodo in list(albero.nodes):
        albero.nodes.remove(nodo)

    sorgente = albero.nodes.new("CompositorNodeRLayers")
    schermo = albero.nodes.new("CompositorNodeComposite")
    albero.links.new(sorgente.outputs["Image"], schermo.inputs["Image"])

    inverti = albero.nodes.new("CompositorNodeInvert")
    uscita = albero.nodes.new("CompositorNodeOutputFile")
    uscita.name = "profondita"
    uscita.label = "profondita"
    uscita.format.file_format = "PNG"
    uscita.format.color_mode = "BW"
    uscita.format.color_depth = "8"
    uscita.format.compression = 100
    albero.links.new(sorgente.outputs["Mist"], inverti.inputs["Color"])
    albero.links.new(inverti.outputs["Color"], uscita.inputs[0])
    return uscita


# ─────────────────────────────────────────────────────────────────────────────
# Il montaggio
# ─────────────────────────────────────────────────────────────────────────────

def costruisci(variante, con_pioggia):
    with interpolazione_lineare():
        return _costruisci(variante, con_pioggia)


def _costruisci(variante, con_pioggia):
    conto = VARIANTI[variante]
    svuota()
    scena = bpy.context.scene
    motore(scena)
    camera()
    acqua()

    # Il sole alto a sinistra, la luna bassa a destra: si vedono insieme come
    # capita davvero all'alba e al tramonto, ed e' l'unica disposizione in cui
    # nessuno dei due copre l'altro.
    astro("sole", (-150.0, 700.0, 430.0), 34.0, (1.0, 0.90, 0.66), emissione=14.0)
    astro("luna", (210.0, 780.0, 250.0), 22.0, (0.92, 0.93, 0.96), emissione=3.0)
    luce_del_sole((-150.0, 700.0, 430.0), 4.0, conto["copertura"])

    if conto["nuvole"]:
        nuvole(conto["nuvole"], conto["copertura"])
    if con_pioggia and conto["pioggia"] > 0.0:
        pioggia(conto["pioggia"])
    if conto["fulmini"]:
        fulmine()

    return profondita(scena)


def rendi(variante, cartella, uscita_profondita):
    """Due uscite dalla stessa scena: il dipinto e la sua profondita'.

    La profondita' e' **un fotogramma solo**, il primo. Non cambia lungo il
    ciclo - le nuvole derivano di pochi metri su ottocento - e una profondita'
    per fotogramma triplicherebbe il peso per una differenza che non si vede.
    """
    nome = NOMI_APP[variante]
    scena = bpy.context.scene
    os.makedirs(cartella, exist_ok=True)

    scena.frame_set(1)
    uscita_profondita.base_path = cartella
    uscita_profondita.file_slots[0].path = f"scena_{nome}_z_"
    scena.render.filepath = os.path.join(cartella, f"scena_{nome}.png")
    scena.render.image_settings.file_format = "PNG"
    scena.render.image_settings.color_mode = "RGB"
    bpy.ops.render.render(write_still=True)

    print(f"  dipinto:     scena_{nome}.png")
    print(f"  profondita': scena_{nome}_z_0001.png  (rinominare in scena_{nome}_z.png)")


def argomenti():
    grezzi = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    scelta = {"variante": "astri_nuvole", "out": "", "pioggia": False, "rendi": False}
    for indice, voce in enumerate(grezzi):
        if voce == "--variante" and indice + 1 < len(grezzi):
            scelta["variante"] = grezzi[indice + 1]
        elif voce == "--out" and indice + 1 < len(grezzi):
            scelta["out"] = grezzi[indice + 1]
            scelta["rendi"] = True
        elif voce == "--pioggia-dipinta":
            scelta["pioggia"] = True
    return scelta


def esegui():
    scelta = argomenti()
    if scelta["variante"] not in VARIANTI:
        print("varianti disponibili: " + ", ".join(VARIANTI))
        return

    print(f"Blender {bpy.app.version_string}, motore {bpy.context.scene.render.engine}")
    uscita = costruisci(scelta["variante"], scelta["pioggia"])
    print(f"scena '{scelta['variante']}' costruita: {CICLO} fotogrammi a {FPS} al secondo")
    print(f"orizzonte al {ORIZZONTE:.0%}, alzata camera {math.degrees(ALZATA):.1f} gradi")
    if scelta["rendi"]:
        rendi(scelta["variante"], scelta["out"], uscita)
    else:
        print("nessun --out: la scena resta aperta, niente e' stato reso")


# **Senza la guardia su `__main__`, e non e' una svista.** Questo file esiste per
# essere eseguito, mai importato, e le tre strade per eseguirlo danno tre valori
# diversi a `__name__`: l'editor di testo di Blender lo mette a `__main__`, un
# `exec` dalla console lo lascia a quello che c'era, e `--python` da terminale
# dipende dalla versione. Una guardia che a volte non scatta e' peggio di
# nessuna guardia: lo script sembra partito e non ha fatto niente.
esegui()
