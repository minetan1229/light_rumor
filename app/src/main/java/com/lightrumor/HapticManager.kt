package com.lightrumor

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.abs

/**
 * HapticManager: Precision mechanical haptic feedback engine.
 * Emulates Minolta and Canon optical camera detents and dials.
 * Safe across Android and Desktop platforms.
 */
class HapticManager(context: Context? = null) {

    private val vibrator: Vibrator? = try {
        if (context != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } else {
            null
        }
    } catch (e: Throwable) {
        null
    }

    private var lastTickTime = 0L
    private val tickThrottleMs = 22L

    /**
     * Mechanical Zero-Point Snap Click:
     * Sharp tactile click when crossing 0 (e.g. 0.0 EV, 0 Contrast, 0 Tint).
     */
    fun performZeroSnap() {
        vibrator?.let { vib ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                        .compose()
                    vib.vibrate(effect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(12, 180))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(12)
                }
            } catch (e: Throwable) {
                // Ignore vibration failure
            }
        }
    }

    /**
     * Precision Dial / Stepper Tick:
     * Ultra-short micro-tick when rotating virtual knurled dial or stepping sliders.
     */
    fun performDialTick() {
        val now = System.currentTimeMillis()
        if (now - lastTickTime < tickThrottleMs) return
        lastTickTime = now

        vibrator?.let { vib ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
                        .compose()
                    vib.vibrate(effect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(5, 70))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(5)
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    /**
     * Range Limit Detent Thud:
     * Heavy thud when reaching parameter minimum or maximum limit.
     */
    fun performLimitThud() {
        vibrator?.let { vib ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                        .compose()
                    vib.vibrate(effect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(24, 255))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(24)
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    /**
     * Evaluates value movement and triggers appropriate haptic effects.
     */
    fun evaluateMovement(oldVal: Float, newVal: Float, minVal: Float, maxVal: Float, zeroSnapTolerance: Float = 0.02f) {
        // 1. Check boundary
        if ((oldVal > minVal && newVal <= minVal) || (oldVal < maxVal && newVal >= maxVal)) {
            performLimitThud()
            return
        }

        // 2. Check zero point crossing
        if ((oldVal < 0f && newVal >= 0f) || (oldVal > 0f && newVal <= 0f) ||
            (abs(newVal) <= zeroSnapTolerance && abs(oldVal) > zeroSnapTolerance)) {
            performZeroSnap()
            return
        }

        // 3. Regular step tick
        if (abs(newVal - oldVal) >= 0.05f) {
            performDialTick()
        }
    }
}

