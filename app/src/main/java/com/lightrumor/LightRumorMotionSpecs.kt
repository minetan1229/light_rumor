package com.lightrumor

import androidx.compose.animation.core.*

object LightRumorMotionSpecs {

    /**
     * Dial Snap & Mechanical Magnet Easing:
     * CSS cubic-bezier(0.16, 1, 0.3, 1)
     * High initial surge, sharp asymptotic mechanical deceleration, snaps onto target value with 0 overshoot.
     */
    val DialSnapEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)

    /**
     * Panel & Drawer Expand / Collapse Easing:
     * CSS cubic-bezier(0.05, 0.7, 0.1, 1.0)
     * Ultra-fast departure with silky smooth organic glide into rest.
     */
    val PanelSlideEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /**
     * Mechanical Detent Spring:
     * No-bouncy stiff spring for tactile slider snap.
     */
    val MechanicalSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Quick Response Spring:
     * Subtle tactile rebound for rotary dial steps and mode transitions.
     */
    val QuickResponseSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMedium
    )

    // Animation Timing Constants (Targeting 60Hz - 120Hz Smooth Displays)
    const val DURATION_SNAP_MS = 180
    const val DURATION_PANEL_MS = 240
    const val DURATION_FADE_MS = 120
    const val FRAME_BUDGET_MS = 16.66f // 60fps budget

    /**
     * Evaluate CSS cubic-bezier curve y for a given normalized time t in [0.0, 1.0].
     * Useful for non-Compose animation loops and test validation.
     */
    fun evaluateCubicBezier(p1x: Float, p1y: Float, p2x: Float, p2y: Float, t: Float): Float {
        var s = t
        for (iter in 0 until 8) {
            val oneMinusS = 1.0f - s
            val currentX = 3.0f * oneMinusS * oneMinusS * s * p1x +
                           3.0f * oneMinusS * s * s * p2x +
                           s * s * s
            val diff = currentX - t
            if (kotlin.math.abs(diff) < 1e-5f) break
            val dx = 3.0f * oneMinusS * oneMinusS * p1x +
                     6.0f * oneMinusS * s * (p2x - p1x) +
                     3.0f * s * s * (1.0f - p2x)
            if (kotlin.math.abs(dx) < 1e-5f) break
            s -= diff / dx
            s = s.coerceIn(0.0f, 1.0f)
        }
        val oneMinusS = 1.0f - s
        return 3.0f * oneMinusS * oneMinusS * s * p1y +
               3.0f * oneMinusS * s * s * p2y +
               s * s * s
    }
}

