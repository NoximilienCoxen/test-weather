#!/usr/bin/env python3
"""Import morti e simboli non risolti, sui file Kotlin passati.

Non e' un compilatore: e' il controllo che **due volte** avrebbe risparmiato un
giro di CI a questo progetto. Qui l'SDK Android non c'e', quindi l'unica prova
che si puo' fare prima di spingere e' questa.

Due domande, e basta quelle:
  - un import c'e' e il suo simbolo non compare mai nel corpo?  -> morto
  - un simbolo con l'aria del tipo compare e non e' ne' importato, ne'
    dichiarato nel file, ne' dichiarato altrove nello stesso pacchetto? -> manca
"""
import re, sys, os, io, collections

RADICE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                      "app/src/main/kotlin")

# Il rumore va tolto prima di guardare, se no il controllo stampa venti parole
# italiane per file e nessuno lo rilegge piu' - che e' il modo piu' sicuro di
# avere un controllo che non controlla niente.
# Cio' che non ha bisogno di un import: builtin di Kotlin, java.lang, i due
# operatori della delega (`by` li chiama senza nominarli) e la classe generata
# da Gradle.
BUILTIN = set("""
getValue setValue provideDelegate BuildConfig Math System Runnable Thread

Any Unit Nothing Boolean Byte Short Int Long Float Double Char String Array
List Map Set MutableList MutableMap MutableSet Pair Triple Result Comparable
Iterable Sequence Collection Number Throwable Exception Enum Function Lazy
IntArray FloatArray DoubleArray LongArray BooleanArray CharArray ByteArray
ShortArray Regex StringBuilder Suppress Deprecated JvmStatic JvmField
Volatile Synchronized Throws OptIn RequiresApi SuppressLint
""".split())

# Costrutti del linguaggio e libreria standard: si scrivono come chiamate ma
# non vengono da un import.
PAROLE = set("""
if when for while return do try catch finally throw synchronized
listOf listOfNotNull mutableListOf emptyList arrayOf intArrayOf floatArrayOf
mapOf mutableMapOf emptyMap setOf mutableSetOf emptySet sequenceOf
require requireNotNull check checkNotNull error TODO print println
run let also apply with use lazy repeat runCatching maxOf minOf
abs min max sin cos tan sqrt floor ceil round pow atan2 hypot exp ln
coerceIn coerceAtLeast coerceAtMost roundToInt toFloat toInt toString
else val var get set constructor init emit invoke it this super
""".split())

# I membri degli ambiti con ricevitore - `DrawScope`, `Path`, `PathBuilder` -
# si chiamano nudi dentro una lambda e non vogliono nessun import.
PAROLE_AMBITO = set("""
drawCircle drawLine drawRect drawPath drawArc drawOval drawPoints drawImage
drawRoundRect drawText drawIntoCanvas clipPath clipRect translate rotate scale
inset withTransform moveTo lineTo quadraticTo cubicTo arcTo relativeLineTo
close addOval addRect addRoundRect addPath addArc reset op
""".split())

def pulisci(testo):
    """Via import, commenti e **testo** delle stringhe, tenendo il codice.

    Scritto a mano e non con una espressione regolare, per un motivo preciso:
    in Kotlin dentro una stringa ci sta `${...}`, e dentro quello ci sta
    un'altra stringa. Una regolare si ferma alla prima virgoletta e taglia a
    meta' una riga di codice - il primo tentativo dichiarava morto un
    `roundToInt` che il file chiamava due volte, cioe' il controllo produceva
    proprio il guasto che esiste per evitare.
    """
    righe = [r for r in testo.splitlines() if not r.startswith("import ")]
    t = "\n".join(righe)
    t = re.sub(r"/\*(?:.|\n)*?\*/", " ", t)
    t = re.sub(r"//[^\n]*", " ", t)

    fuori = []
    i, n = 0, len(t)
    while i < n:
        tripla = t.startswith('"""', i)
        if not tripla and t[i] != '"':
            fuori.append(t[i]); i += 1; continue
        # dentro una stringa: si tiene solo cio' che sta in ${...}
        chiusura = '"""' if tripla else '"'
        i += len(chiusura)
        while i < n:
            if not tripla and t[i] == "\\":
                i += 2; continue
            if t.startswith(chiusura, i):
                i += len(chiusura); break
            if t.startswith("${", i):
                i += 2
                livello, dentro = 1, []
                while i < n and livello > 0:
                    if t[i] == "{":
                        livello += 1
                    elif t[i] == "}":
                        livello -= 1
                        if livello == 0:
                            i += 1
                            break
                    dentro.append(t[i]); i += 1
                fuori.append(" " + pulisci("".join(dentro)) + " ")
                continue
            i += 1
        fuori.append(' "" ')
    return "".join(fuori)

def simboli(testo):
    return set(re.findall(r"\b[A-Za-z_][A-Za-z0-9_]*\b", testo))

def pacchetto(testo):
    m = re.search(r"^package\s+([\w.]+)", testo, re.M)
    return m.group(1) if m else ""

# tutto cio' che ogni file del pacchetto mette a disposizione degli altri
def indice_pacchetti(radice):
    per_pacchetto = collections.defaultdict(set)
    estensioni = collections.defaultdict(set)
    for base, _, files in os.walk(radice):
        for f in files:
            if not f.endswith(".kt"):
                continue
            p = os.path.join(base, f)
            t = open(p, encoding="utf-8").read()
            pkg = pacchetto(t)
            for m in re.finditer(
                r"^\s*(?:@\w+\s+)*(?:public |internal |private |abstract |sealed |open |data |value |inline |const |expect |actual )*"
                r"(?:class|interface|object|enum class|annotation class|fun|val|var|typealias)\s+"
                r"(?:<[^>]*>\s*)?(?:[\w.]+\.)?([A-Za-z_]\w*)", t, re.M):
                per_pacchetto[pkg].add(m.group(1))
            # **Le estensioni di primo livello, a parte.** Si scrivono col punto
            # davanti - `posto.key` - quindi passavano per membri e nessuno
            # chiedeva da dove venissero. E' cosi' che un `Unresolved reference
            # 'key'` ha fermato la CI mentre questo script diceva zero.
            for m in re.finditer(
                r"^(?:public |internal |private )?(?:inline |suspend )*(?:fun|val|var)\s+"
                r"(?:<[^>]*>\s*)?[A-Z]\w*(?:<[^>]*>)?\??(?:\.\w+)*?\.(\w+)\s*[(:<=]", t, re.M):
                estensioni[m.group(1)].add(pkg)
            # anche i membri di enum e i nomi di companion contano poco: bastano i tipi
    return per_pacchetto, estensioni

BASELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "import_audit_baseline.txt")

def carica_baseline():
    """Cio' che questo controllo segnala su codice che **ha gia' compilato**.

    Non e' un compilatore e non pretende di esserlo: su un albero verde stampa
    una novantina di righe, quasi tutte per costrutti che non sa leggere -
    membri ereditati, tipi annidati, eccezioni di `java.lang`. Un controllo che
    stampa novanta righe innocue e' un controllo che nessuno rilegge, cioe' un
    controllo spento.

    Quindi si tara: il riferimento e' un commit di cui **la CI ha detto che
    compila**, e conta solo cio' che compare in piu'. Si rigenera con
    `--baseline` dopo ogni giro verde.
    """
    if not os.path.exists(BASELINE):
        return set()
    return {r.rstrip("\n") for r in io.open(BASELINE, encoding="utf-8") if r.strip()}

def main(argv):
    scrivi_baseline = "--baseline" in argv
    argv = [a for a in argv if a != "--baseline"]
    noti = set() if scrivi_baseline else carica_baseline()
    trovati = []

    per_pacchetto, estensioni = indice_pacchetti(RADICE)
    guasti = 0
    for p in argv:
        if not p.endswith(".kt"):
            continue
        t = open(p, encoding="utf-8").read()
        corpo = pulisci(t)
        usati = simboli(corpo)
        pkg = pacchetto(t)

        # 1. import morti
        for riga in t.splitlines():
            m = re.match(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", riga)
            if not m:
                continue
            nome = m.group(2) or m.group(1).split(".")[-1]
            if nome == "*":
                continue
            if nome in BUILTIN:
                continue
            if nome not in usati:
                trovati.append(f"{p}: import morto -> {riga.strip()}")

        # 2. simboli che hanno l'aria del tipo e non si sa da dove vengano
        importati = set()
        for m in re.finditer(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", t, re.M):
            importati.add(m.group(2) or m.group(1).split(".")[-1])
        locali = per_pacchetto.get(pkg, set())
        # ── Le funzioni. **E' qui che si perdono i giri di CI.**
        #
        # Un tipo mancante si nota rileggendo; una `animateFloatAsState` senza
        # import no, perche' somiglia a tutto il resto del file. Le due volte in
        # cui questo progetto e' andato rosso senza capire perche', era questo.
        membri = set(re.findall(r"\.\s*([a-z]\w*)\s*\(", corpo))
        chiamate = set(re.findall(r"(?<![.\w])([a-z]\w*)\s*\(", corpo))
        dichiarati = set(re.findall(r"\b(?:val|var|fun)\s+(?:<[^>]*>\s*)?(\w+)", t))
        dichiarati |= set(re.findall(r"(\w+)\s*:", t))          # parametri
        dichiarati |= set(re.findall(r"(\w+)\s*->", t))         # lambda
        for f in sorted(chiamate - membri - dichiarati):
            if f in importati or f in locali or f in BUILTIN or f in PAROLE or f in PAROLE_AMBITO:
                continue
            trovati.append(f"{p}: funzione non risolta -> {f}()")

        # ── Le estensioni chiamate col punto ────────────────────────────────
        for e in sorted(set(re.findall(r"\.\s*(\w+)", corpo))):
            case = estensioni.get(e)
            if not case: continue
            if e in importati or pkg in case: continue
            trovati.append(f"{p}: estensione non importata -> .{e}")

        for s in sorted(usati):
            # Parametri di tipo (T, R), costanti ed etichette di enum usate
            # senza qualificatore: non sono tipi da importare.
            if not re.match(r"^[A-Z][a-z][A-Za-z0-9]*$", s):
                continue
            if s in importati or s in locali or s in BUILTIN:
                continue
            # dichiarato qui dentro?
            if re.search(r"\b(class|interface|object|enum class|typealias)\s+" + s + r"\b", t):
                continue
            # membro di qualcosa (Foo.Bar) o etichetta: si ignora
            if re.search(r"[\w)\]]\s*\.\s*" + s + r"\b", t):
                continue
            trovati.append(f"{p}: simbolo non risolto -> {s}")
    if scrivi_baseline:
        io.open(BASELINE, "w", encoding="utf-8").write("\n".join(sorted(set(trovati))) + "\n")
        print(f"--- taratura scritta: {len(set(trovati))} righe note in {BASELINE}")
        return 0

    nuovi = [r for r in trovati if r not in noti]
    for r in nuovi:
        print(r)
    zittiti = len(trovati) - len(nuovi)
    print(f"--- {len(nuovi)} da guardare ({zittiti} gia' noti sull'albero di riferimento)")
    return 1 if nuovi else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
