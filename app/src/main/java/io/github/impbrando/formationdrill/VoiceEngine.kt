package io.github.impbrando.formationdrill

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

/**
 * ONE microphone stream (AudioRecord, 16 kHz mono) feeds both:
 *  - the voice meter: loudness + pitch every 25 ms → FGN.frame(db, hz, age), used for inflection and
 *    the voice-burst execution timing;
 *  - the on-device recogniser (Vosk) restricted to the drill-word grammar the game sends →
 *    FGN.speech(text, isFinal, alts).
 * No handoff between "meter" and "recogniser" as with Chrome on Android.
 */
class VoiceEngine(private val ctx: Context, private val js: (String) -> Unit) {
    companion object {
        const val SR = 16000
        const val HOP = 400            // 25 ms
        const val WIN = 1024           // 64 ms analysis window (pitch down to 70 Hz)
        private const val TAG = "FGVoice"
    }

    @Volatile private var model: Model? = null
    @Volatile private var running = false
    @Volatile private var wantRunning = false
    private var thread: Thread? = null
    private val lock = Any()
    private var recognizer: Recognizer? = null
    @Volatile var grammar: String = DEFAULT_GRAMMAR
        private set

    fun loadModel() {
        js("FGN.state('loading')")
        StorageService.unpack(ctx, "model-en-us", "model",
            { m -> model = m; synchronized(lock) { recognizer = null }; if (wantRunning) start() },
            { e -> Log.e(TAG, "model", e); js("FGN.state('error'," + JSONObject.quote("speech model: " + e.message) + ")") })
    }

    fun setGrammar(json: String) {
        try { JSONArray(json) } catch (e: Exception) { return }   // must be a JSON array of phrases
        grammar = json
        synchronized(lock) { recognizer?.close(); recognizer = null }  // rebuilt with the new words
    }

    private fun recognizerOrNull(): Recognizer? = synchronized(lock) {
        val m = model ?: return null
        if (recognizer == null) {
            recognizer = try { Recognizer(m, SR.toFloat(), grammar) } catch (e: Exception) { Recognizer(m, SR.toFloat(), DEFAULT_GRAMMAR) }
            recognizer!!.setMaxAlternatives(3)
        }
        recognizer
    }

    fun start() {
        wantRunning = true
        if (running) return
        running = true
        thread = Thread({ loop() }, "fg-voice").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    fun stop() {
        wantRunning = false
        running = false
        thread?.join(500); thread = null
    }

    @SuppressLint("MissingPermission")   // checked by MainActivity before start()
    private fun loop() {
        val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SR, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, HOP * 2 * 8))
        } catch (e: Exception) { js("FGN.state('error'," + JSONObject.quote("mic: " + e.message) + ")"); running = false; return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { js("FGN.state('error','mic busy')"); running = false; return }
        val hop = ShortArray(HOP)
        val win = FloatArray(WIN)
        var lastPartial = ""
        rec.startRecording()
        js("FGN.state('listening')")
        try {
            while (running) {
                var got = 0
                while (got < HOP && running) { val n = rec.read(hop, got, HOP - got); if (n <= 0) break; got += n }
                if (got < HOP) continue
                val tEnd = SystemClock.elapsedRealtime()
                // meter
                System.arraycopy(win, HOP, win, 0, WIN - HOP)
                for (i in 0 until HOP) win[WIN - HOP + i] = hop[i] / 32768f
                val db = MeterDsp.rmsDb(win, WIN - 640, WIN)          // last 40 ms: sharp onsets
                val hz = if (db > -50) MeterDsp.pitch(win, SR) else 0.0
                val age = SystemClock.elapsedRealtime() - tEnd + 20     // centre of the level window
                js("FGN.frame(" + "%.1f".format(java.util.Locale.US, db) + "," + "%.1f".format(java.util.Locale.US, hz) + "," + age + ")")
                // recogniser (same samples)
                val r = recognizerOrNull() ?: continue
                if (r.acceptWaveForm(hop, HOP)) {
                    val (text, alts) = MeterDsp.parseVosk(r.result)
                    lastPartial = ""
                    js("FGN.speech(" + JSONObject.quote(text) + ",true," + JSONArray(alts) + ")")
                } else {
                    val (p, _) = MeterDsp.parseVosk(r.partialResult)
                    if (p != lastPartial) { lastPartial = p; if (p.isNotBlank()) js("FGN.speech(" + JSONObject.quote(p) + ",false,[])") }
                }
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            js("FGN.state('stopped')")
        }
    }

    fun release() { stop(); synchronized(lock) { recognizer?.close(); recognizer = null }; model?.close(); model = null }
}

/** Used until the game sends its own list (FGAndroid.setGrammar). */
val DEFAULT_GRAMMAR: String = JSONArray(listOf(
    "column right march", "column left march", "column half right march", "column half left march", "counter column march",
    "rear march", "right flank march", "left flank march", "forward march", "mark time march", "half step march",
    "change step march", "double time march", "quick time march", "right step march", "left step march", "backward march",
    "open ranks march", "close ranks march", "extend march", "close march", "platoon halt", "platoon attention",
    "right face", "left face", "about face", "parade rest", "stand at ease", "at ease", "dress right dress", "eyes right",
    "ready front", "fall in", "cover", "recover", "[unk]")).toString()
