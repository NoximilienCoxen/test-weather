package io.github.noximiliencoxen.caelum.ui.render3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.random.Random

/**
 * L'acqua che cade e cosa succede quando tocca: gocce, schizzi, anelli, pozze.
 *
 * **Stava dentro `WeatherSculpture.kt`**, ed era privata, per la sola ragione
 * che la scultura e' stato il primo posto in cui e' servita. Adesso i posti
 * sono due - la scultura della prima scheda e la vasca graduata della scheda
 * della pioggia - e il progetto ha gia' deciso due volte cosa si fa in questo
 * caso. La prima e' `isWet()`, passata a `Wmo.kt` perche' *"parla di codici
 * WMO, non di come si disegna una nuvola"*: qui vale identica, perche' uno
 * schizzo parla di acqua che tocca una superficie, non di una scultura. La
 * seconda sono i mari della luna in `MOON_SEAS`: *"due copie degli stessi
 * quattro numeri sono due copie destinate a divergere alla prima volta che
 * qualcuno ne sposta una"*, che esclude il copia-incolla.
 *
 * Non `internal` sul posto, e non e' una sfumatura: avrebbe reso millesettecento
 * righe di Compose della schermata principale una dipendenza di compilazione di
 * una scheda del feed. `ui/render3d/` e' dove sta gia' il disegno condiviso, ed
 * e' dove la scheda della pioggia gia' arriva per la camera e per la luna.
 *
 * **Le spiegazioni sono venute qui con il codice, parola per parola.** Sono le
 * lezioni gia' pagate - perche' lo schizzo nasce gia' aperto, perche' la pozza
 * si smorza al quadrato, perche' gli urti contano solo il passaggio dall'aria
 * alla superficie - e una di esse riscritta a memoria e' una lezione da pagare
 * di nuovo.
 *
 * Cosa **non** e' venuto qui: `drawRain` e `drawSnow`. Sono saldate a
 * `SceneContact` e a `Skyline` - chiedono alla sagoma della cifra dove sta la
 * superficie - mentre la vasca il suo piano d'acqua lo conosce per via
 * analitica. Chi ne ha bisogno si scrive il proprio ciclo di caduta e riusa i
 * pezzi che stanno qui, invece di costruire una sagoma finta per farsi
 * rispondere quello che gia' sa.
 */

/**
 * Quali gocce stanno toccando la cifra, e quante hanno appena cominciato.
 *
 * Lo stato sta qui e non in Compose apposta: viene scritto dentro il disegno e
 * riletto dal ciclo della caduta, sullo stesso filo e a un fotogramma di
 * distanza. Uno stato osservabile chiederebbe una ricomposizione per qualcosa
 * che sullo schermo non cambia niente - la vibrazione non si vede.
 */
internal class RainImpacts {

    /** Se ognuna stava gia' toccando al fotogramma prima. */
    private val touching = BooleanArray(DROPS.size)

    private var landed = 0

    /**
     * Dal disegno: questa goccia sta toccando, o no.
     *
     * Conta solo il passaggio dall'aria alla superficie. Senza il confronto col
     * fotogramma prima, ogni goccia gia' arrivata ne segnerebbe uno per
     * fotogramma e non ci sarebbe piu' differenza fra una goccia che arriva e
     * una goccia ferma sul posto.
     */
    fun mark(index: Int, hitting: Boolean) {
        if (index !in touching.indices) return
        if (hitting && !touching[index]) landed++
        touching[index] = hitting
    }

    /** Dal ciclo: quante ne sono arrivate dall'ultima volta che si e' guardato. */
    fun take(): Int {
        val count = landed
        landed = 0
        return count
    }

    fun forget() {
        java.util.Arrays.fill(touching, false)
        landed = 0
    }
}

/**
 * Una goccia, con un posto suo sotto la nuvola.
 *
 * Le gocce vivono nello spazio del modello, non sullo schermo. Prima cadevano
 * lungo una fascia fissa attorno al centro: non seguivano la nuvola quando la
 * si girava, non ne rispettavano la larghezza, e da qualunque angolo la si
 * guardasse restavano li'. Cosi' invece ruotano con lei, quelle davanti scorrono
 * piu' di quelle dietro, e sono grandi quanto la loro distanza impone.
 */
internal class Drop(
    /** Posizione sotto la nuvola, da -1 a 1 sui due assi orizzontali. */
    val x: Float,
    val z: Float,
    val phase: Float,
    val speed: Float,
    val length: Float,
)

internal val DROPS: List<Drop> = List(48) { i ->
    val r = Random(i * 7919 + 13)
    Drop(
        x = r.nextFloat() * 2f - 1f,
        z = r.nextFloat() * 2f - 1f,
        phase = r.nextFloat(),
        speed = 0.85f + r.nextFloat() * 0.5f,
        length = 0.05f + r.nextFloat() * 0.05f,
    )
}

/**
 * Una mini-pozzanghera alla base della cifra.
 *
 * Un'ellisse molto schiacciata, non un cerchio: il piano d'appoggio si guarda
 * di sbieco, e un cerchio pieno li' si legge come una pallina appoggiata a
 * terra invece che come acqua distesa.
 *
 * Si allarga mentre si smorza, e la smorzatura va al quadrato: l'acqua non
 * evapora a velocita' costante, sparisce in fretta sul finire. Con una
 * dissolvenza lineare si vedeva un disco grigio restare li' troppo a lungo.
 */
internal fun DrawScope.puddle(
    at: Offset,
    stroke: Float,
    age: Float,
    colour: Color,
    alpha: Float,
) {
    val half = stroke * (1.1f + age * 5.0f)
    val tall = half * 0.30f
    drawOval(
        color = colour.copy(alpha = alpha * 0.45f * (1f - age) * (1f - age)),
        topLeft = Offset(at.x - half, at.y - tall * 0.5f),
        size = Size(half * 2f, tall),
    )
}

/**
 * Lo schizzo: due schegge che partono ai lati del punto colpito e si aprono.
 *
 * Non una corona tonda vista di taglio, che a questa dimensione sarebbe una
 * riga. E non due segmenti attaccati al punto d'impatto: staccate dal centro si
 * leggono come acqua che rimbalza, unite come una punta di freccia.
 *
 * Piu' invecchiano piu' si allontanano e si abbassano, come se ricadessero.
 */
internal fun DrawScope.splash(
    at: Offset,
    stroke: Float,
    age: Float,
    colour: Color,
    alpha: Float,
) {
    val fade = alpha * (1f - age) * 0.9f
    if (fade <= 0.01f) return

    // Nasce gia' aperto e radente. Partendo stretto e ripido, i due segmenti
    // restavano appesi sotto la goccia e insieme a lei formavano una punta di
    // freccia: l'acqua che rimbalza si allarga subito, non parte a coda.
    val gap = stroke * (0.9f + age * 1.3f)
    val reach = stroke * (1.5f + age * 3.2f)
    val lift = stroke * (1.15f - age * 0.8f)
    val tint = lerp(colour, Lightning, 0.30f).copy(alpha = fade)

    drawLine(
        color = tint,
        start = Offset(at.x - gap, at.y),
        end = Offset(at.x - gap - reach, at.y - lift),
        strokeWidth = stroke * 0.60f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = tint,
        start = Offset(at.x + gap, at.y),
        end = Offset(at.x + gap + reach * 0.88f, at.y - lift * 0.85f),
        strokeWidth = stroke * 0.60f,
        cap = StrokeCap.Round,
    )
}

/**
 * Un piccolo anello che si allarga dal punto colpito, accanto allo schizzo.
 *
 * Lo schizzo dice "acqua che rimbalza", l'anello dice "onda che si apre": sono
 * due letture diverse dello stesso urto, e insieme rendono l'impatto piu'
 * ricco senza aggiungere nessuno stato - la stessa eta' che gia' guida lo
 * schizzo basta anche a lui.
 */
internal fun DrawScope.microSplashRing(
    at: Offset,
    stroke: Float,
    age: Float,
    colour: Color,
    alpha: Float,
) {
    val fade = alpha * (1f - age) * (1f - age)
    if (fade <= 0.01f) return
    drawCircle(
        color = colour.copy(alpha = fade * 0.5f),
        radius = stroke * (0.8f + age * 2.4f),
        center = at,
        style = Stroke(width = stroke * 0.35f),
    )
}

/** Bianco pieno: e' il nucleo, e un nucleo non ha colore. */
internal val Lightning = Color(0xFFFFFFFF)

/** Quanto vive una pozzanghera e quanto uno schizzo, in unita' di caduta. */
internal const val PUDDLE_LIFE = 0.34f
internal const val SPLASH_LIFE = 0.16f

internal const val FALL_CYCLE_MS = 1400L

/**
 * Quanto deve passare, come minimo, fra un colpetto e il successivo.
 *
 * Un decimo di secondo. Sotto, in un rovescio, il vibratore non stacca piu' e
 * quello che dovrebbe leggersi come pioggia si legge come un ronzio; sopra, si
 * perde il legame fra la goccia che si vede arrivare e quella che si sente.
 */
internal const val TAP_GAP_NS = 110_000_000L
