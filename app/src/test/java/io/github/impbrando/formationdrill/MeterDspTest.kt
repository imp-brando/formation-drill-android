package io.github.impbrando.formationdrill

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MeterDspTest {
    private fun tone(hz: Double, amp: Double, n: Int = VoiceEngine.WIN) = FloatArray(n) { (amp * sin(2 * PI * hz * it / VoiceEngine.SR)).toFloat() }

    @Test fun loudnessOfFullScaleSineIsMinus3dB() = assertEquals(-3.01, MeterDsp.rmsDb(tone(200.0, 1.0)), 0.1)
    @Test fun quieterIsLower() = assertTrue(MeterDsp.rmsDb(tone(200.0, 0.01)) < MeterDsp.rmsDb(tone(200.0, 0.5)) - 30)
    @Test fun silenceIsVeryLow() = assertTrue(MeterDsp.rmsDb(FloatArray(1024)) < -150)
    @Test fun pitchOfVoiceRangeTones() {
        for (hz in listOf(90.0, 120.0, 180.0, 250.0, 400.0)) assertEquals(hz, MeterDsp.pitch(tone(hz, 0.5), VoiceEngine.SR), hz * 0.03)
    }
    @Test fun noiseHasNoPitch() {
        val r = java.util.Random(1); val x = FloatArray(1024) { (r.nextGaussian() * 0.2).toFloat() }
        assertEquals(0.0, MeterDsp.pitch(x, VoiceEngine.SR), 0.0)
    }
}
