package io.github.impbrando.formationdrill

import kotlin.math.log10
import kotlin.math.sqrt

/** Loudness (dBFS) and pitch (Hz) of one analysis window, same method as the web game's meter. */
object MeterDsp {
    fun rmsDb(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var s = 0.0
        for (i in from until to) s += x[i] * x[i]
        val rms = sqrt(s / (to - from).coerceAtLeast(1))
        return 20 * log10(rms + 1e-9)
    }

    /** Normalised autocorrelation, 70–450 Hz. Returns 0 when unvoiced. */
    fun pitch(x: FloatArray, sr: Int): Double {
        val n = x.size
        val minLag = sr / 450
        val maxLag = minOf(sr / 70, n / 2)
        val m = n - maxLag
        var e0 = 0.0
        for (i in 0 until m) e0 += x[i] * x[i]
        val r = DoubleArray(maxLag + 2)
        var best = 0.0; var bestLag = 0; var prev = 1.0; var rising = false
        for (lag in minLag..maxLag) {
            var c = 0.0; var e1 = 0.0
            for (i in 0 until m) { c += x[i] * x[i + lag]; e1 += x[i + lag] * x[i + lag] }
            val v = c / sqrt(e0 * e1 + 1e-12); r[lag] = v
            if (v > prev) rising = true
            if (rising && v > best) { best = v; bestLag = lag }
            prev = v
        }
        if (best < 0.5 || bestLag == 0) return 0.0
        val a = if (bestLag - 1 >= minLag) r[bestLag - 1] else best
        val c = if (bestLag + 1 <= maxLag) r[bestLag + 1] else best
        val den = (a - 2 * best + c).let { if (it == 0.0) 1.0 else it }
        val p = ((a - c) / (2 * den)).coerceIn(-0.5, 0.5)
        return sr / (bestLag + p)
    }

    /** Vosk JSON → (text, alternatives). Handles {"text"}, {"partial"} and {"alternatives":[{"text"}]}. */
    fun parseVosk(json: String): Pair<String, List<String>> {
        val o = org.json.JSONObject(json)
        if (o.has("partial")) return o.optString("partial") to emptyList()
        if (o.has("alternatives")) {
            val arr = o.getJSONArray("alternatives")
            val all = (0 until arr.length()).map { arr.getJSONObject(it).optString("text") }
            return (all.firstOrNull() ?: "") to all.drop(1)
        }
        return o.optString("text") to emptyList()
    }
}
