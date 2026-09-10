package io.github.noximiliencoxen.caelum.ui.scene

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * Il diorama per-pixel: ogni punto si sposta di quanto e' lontano.
 *
 * **Perche' uno shader e non due livelli che slittano.** La parallasse a livelli
 * e' quella dei cartoni animati: piani di vetro che scorrono a velocita'
 * diverse, e fra un piano e l'altro il salto si vede. Qui lo spostamento e' una
 * funzione continua della profondita', quindi la collina lontana e la collina
 * media si muovono di quanto **sono** distanti e non di quanto le ha
 * classificate qualcuno. E' la differenza fra una scena e un collage.
 *
 * **La profondita' si legge due volte, e non e' uno spreco.** La prima lettura
 * dice di quanto spostare il campionamento; dopo lo spostamento si rilegge, per
 * sapere quanto e' lontano il pixel che si e' davvero preso e tingerlo di
 * conseguenza. Con una lettura sola il colore dell'ora finirebbe sulla distanza
 * sbagliata proprio dove la scena si muove di piu'.
 *
 * **La dissolvenza e' sfalsata per profondita'**, ed e' la parte che rende il
 * cambio di tempo un evento invece di una transizione. Il fondo si sostituisce
 * per primo, il primo piano per ultimo: l'orizzonte si scurisce prima che
 * arrivi la pioggia in strada, che e' l'ordine in cui il tempo cambia davvero.
 * Il conto e' un termine solo — la soglia locale scala con la distanza — e a
 * fine corsa tutto e' arrivato a uno comunque.
 *
 * **Le funzioni non prendono `shader` come parametro.** In AGSL `shader` e' un
 * tipo opaco che vive solo come uniforme: passarlo a una funzione non compila.
 * Da qui il corpo scritto due volte, per la scena che c'e' e per quella che
 * arriva. E' ripetizione voluta, non una svista da accorpare.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class DioramaEffect {

    private val shader = RuntimeShader(SOURCE)
    private val paint = Paint().apply { isAntiAlias = true }
    private val matrix = Matrix()

    /**
     * Le lastre entrano con una matrice che le porta a **coprire** il riquadro.
     *
     * Cosi' dentro lo shader le coordinate sono quelle della tela e non quelle
     * della bitmap: lo spostamento della parallasse si scrive in pixel di
     * schermo, che e' l'unica unita' in cui ha senso deciderlo. Copre e non
     * contiene, perche' una scena che lascia due bande vuote ai lati non e' una
     * scena, e il ritaglio e' esattamente cio' per cui si chiede l'otto per
     * cento di margine ai dipinti veri.
     */
    fun setPlates(now: ScenePlates, next: ScenePlates?, width: Float, height: Float) {
        bind("art", "dep", now, width, height)
        bind("art2", "dep2", next ?: now, width, height)
    }

    private fun bind(art: String, dep: String, plates: ScenePlates, width: Float, height: Float) {
        shader.setInputShader(art, cover(plates.painting, width, height))
        shader.setInputShader(dep, cover(plates.depth, width, height))
    }

    private fun cover(bitmap: Bitmap, width: Float, height: Float): BitmapShader {
        val scale = max(width / bitmap.width, height / bitmap.height)
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            (width - bitmap.width * scale) / 2f,
            (height - bitmap.height * scale) / 2f,
        )
        return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
    }

    /**
     * [tiltX] e [tiltY] sono **gia' in pixel**: quanto si sposta il punto piu'
     * lontano. [drift] e' lo scorrimento orizzontale delle nuvole, che va col
     * quadrato della distanza perche' il vento si vede in cielo e non sull'erba
     * ai propri piedi.
     */
    fun setMotion(tiltX: Float, tiltY: Float, drift: Float, progress: Float) {
        shader.setFloatUniform("uTilt", tiltX, tiltY)
        shader.setFloatUniform("uDrift", drift)
        shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
    }

    fun setLight(near: Color, far: Color, amount: Float) {
        shader.setFloatUniform("uNear", near.red, near.green, near.blue)
        shader.setFloatUniform("uFar", far.red, far.green, far.blue)
        shader.setFloatUniform("uTint", amount.coerceIn(0f, 1f))
    }

    fun draw(canvas: Canvas, width: Float, height: Float) {
        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private companion object {
        val SOURCE = """
            uniform shader art;
            uniform shader dep;
            uniform shader art2;
            uniform shader dep2;

            uniform float2 uTilt;
            uniform float uDrift;
            uniform float uProgress;
            uniform float3 uNear;
            uniform float3 uFar;
            uniform float uTint;

            const float STAGGER = 0.60;

            half4 main(float2 p) {
                // ── La scena che c'e' ────────────────────────────────────────
                float z = dep.eval(p).r;
                float far = 1.0 - z;
                float2 q = p + uTilt * far + float2(uDrift * far * far, 0.0);
                half4 ca = art.eval(q);
                float za = dep.eval(q).r;
                float3 ta = mix(uFar, uNear, za);
                ca = half4(mix(ca.rgb, ca.rgb * half3(ta), half(uTint)), ca.a);

                if (uProgress <= 0.0) {
                    return ca;
                }

                // ── La scena che arriva ─────────────────────────────────────
                float z2 = dep2.eval(p).r;
                float far2 = 1.0 - z2;
                float2 q2 = p + uTilt * far2 + float2(uDrift * far2 * far2, 0.0);
                half4 cb = art2.eval(q2);
                float zb = dep2.eval(q2).r;
                float3 tb = mix(uFar, uNear, zb);
                cb = half4(mix(cb.rgb, cb.rgb * half3(tb), half(uTint)), cb.a);

                // ── Il fondo si sostituisce per primo ───────────────────────
                float local = clamp(uProgress * (1.0 + STAGGER) - z2 * STAGGER, 0.0, 1.0);
                local = local * local * (3.0 - 2.0 * local);
                return mix(ca, cb, half(local));
            }
        """.trimIndent()
    }
}
