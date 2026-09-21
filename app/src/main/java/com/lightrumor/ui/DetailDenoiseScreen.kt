package com.lightrumor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.ApexTheme
import com.lightrumor.DevelopmentParams
import com.lightrumor.HapticManager

/**
 * DetailDenoiseScreen: High-Fidelity Sharpening, Dual-Domain Noise Reduction & Lens Optics.
 * Features:
 * - Unsharp Masking with precision Radius, Detail, and High-Pass Edge Masking
 * - Alt/Option Masking Preview Mode (High-contrast B&W visualizer for edge thresholding)
 * - Bilateral Luminance Noise Reduction with texture & contrast preservation
 * - False-color Chroma Noise Reduction with edge-aware spatial filtering
 * - Purple/Green Axial Chromatic Aberration Defringe
 * - Strict Anti-AI styling: Obsidian black chassis, titanium hairline accents, zero emojis
 */
@Composable
fun DetailDenoiseScreen(
    params: DevelopmentParams,
    onParamsChange: (DevelopmentParams) -> Unit,
    hapticManager: HapticManager? = null,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ---------------------------------------------------------------------
        // SECTION 1: SHARPENING (UNSHARP MASKING & EDGE MASK)
        // ---------------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SHARPENING & EDGE MASKING",
                style = ApexTheme.typography.Header,
                color = colors.accentAmber,
                fontSize = 11.sp
            )

            // Visualize Mask Toggle (Alt/Option Key preview equivalent)
            val isMaskPreview = params.sharpeningPreviewMask
            Box(
                modifier = Modifier
                    .background(
                        if (isMaskPreview) colors.accentRed else colors.surfaceElevated,
                        RoundedCornerShape(2.dp)
                    )
                    .border(
                        1.dp,
                        if (isMaskPreview) colors.accentRed else colors.borderStrong,
                        RoundedCornerShape(2.dp)
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        onParamsChange(params.copy(sharpeningPreviewMask = !isMaskPreview))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isMaskPreview) "MASK PREVIEW [ON]" else "VISUALIZE MASK",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (isMaskPreview) Color.White else colors.textPrimary
                )
            }
        }

        LightroomSlider(
            label = "Sharpen Amount",
            value = params.sharpeningAmount,
            onValueChange = { onParamsChange(params.copy(sharpeningAmount = it)) },
            range = 0f..150f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Sharpen Radius",
            value = params.sharpeningRadius,
            onValueChange = { onParamsChange(params.copy(sharpeningRadius = it)) },
            range = 0.5f..3.0f,
            defaultValue = 1.0f,
            unit = "px",
            displayDecimals = 1,
            step = 0.1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Sharpen Detail",
            value = params.sharpeningDetail,
            onValueChange = { onParamsChange(params.copy(sharpeningDetail = it)) },
            range = 0f..100f,
            defaultValue = 25f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Edge Masking",
            value = params.sharpeningMasking,
            onValueChange = { onParamsChange(params.copy(sharpeningMasking = it)) },
            range = 0f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------------------------------------------------------------
        // SECTION 2: NOISE REDUCTION (LUMINANCE)
        // ---------------------------------------------------------------------
        Text(
            text = "LUMINANCE NOISE REDUCTION",
            style = ApexTheme.typography.Header,
            color = colors.accentAmber,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        LightroomSlider(
            label = "Luminance NR",
            value = params.luminanceNR,
            onValueChange = { onParamsChange(params.copy(luminanceNR = it)) },
            range = 0f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Luma Detail",
            value = params.luminanceNRDetail,
            onValueChange = { onParamsChange(params.copy(luminanceNRDetail = it)) },
            range = 0f..100f,
            defaultValue = 50f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Luma Contrast",
            value = params.luminanceNRContrast,
            onValueChange = { onParamsChange(params.copy(luminanceNRContrast = it)) },
            range = 0f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------------------------------------------------------------
        // SECTION 3: COLOR NOISE REDUCTION (CHROMA SPECKLE)
        // ---------------------------------------------------------------------
        Text(
            text = "COLOR NOISE REDUCTION (CHROMA)",
            style = ApexTheme.typography.Header,
            color = colors.accentAmber,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        LightroomSlider(
            label = "Color NR",
            value = params.chromaNR,
            onValueChange = { onParamsChange(params.copy(chromaNR = it)) },
            range = 0f..100f,
            defaultValue = 25f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Color Detail",
            value = params.chromaNRDetail,
            onValueChange = { onParamsChange(params.copy(chromaNRDetail = it)) },
            range = 0f..100f,
            defaultValue = 50f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Color Smoothness",
            value = params.chromaNRSmoothness,
            onValueChange = { onParamsChange(params.copy(chromaNRSmoothness = it)) },
            range = 0f..100f,
            defaultValue = 50f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------------------------------------------------------------
        // SECTION 4: OPTICS & CHROMATIC ABERRATION DEFRINGE
        // ---------------------------------------------------------------------
        Text(
            text = "OPTICS & DEFRINGE",
            style = ApexTheme.typography.Header,
            color = colors.accentAmber,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        LightroomSlider(
            label = "Defringe Purple",
            value = params.lensCorrection.defringePurple,
            onValueChange = { onParamsChange(params.copy(lensCorrection = params.lensCorrection.copy(defringePurple = it))) },
            range = 0f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Defringe Green",
            value = params.lensCorrection.defringeGreen,
            onValueChange = { onParamsChange(params.copy(lensCorrection = params.lensCorrection.copy(defringeGreen = it))) },
            range = 0f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Vignette Falloff",
            value = params.lensCorrection.vignettingCorrection,
            onValueChange = { onParamsChange(params.copy(lensCorrection = params.lensCorrection.copy(vignettingCorrection = it))) },
            range = 0f..200f,
            defaultValue = 100f,
            unit = "%",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )
    }
}

