package com.lightrumor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

enum class ThumbTab(val title: String) {
    Light("LIGHT"),
    Color("COLOR"),
    Mixer("MIXER"),
    Detail("DETAIL"),
    Optics("OPTICS"),
    Geometry("GEOM")
}

/**
 * ThumbZoneBottomBar: Human-Engineered Bottom 35% Control Center.
 * Aggregates tabs and Lightroom-style horizontal sliders strictly within natural thumb reach.
 * Smoothly animated using CSS cubic-bezier physical easing curves.
 */
@Composable
fun ThumbZoneBottomBar(
    params: DevelopmentParams,
    onParamsChange: (DevelopmentParams) -> Unit,
    hapticManager: HapticManager?,
    onOpenPrecisionDial: (label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onUpdate: (Float) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var activeTab by remember { mutableStateOf(ThumbTab.Light) }
    var selectedProfileId by remember { mutableStateOf("cinetone") }
    var selectedFilterId by remember { mutableStateOf("std") }

    val filterItems = remember { PhotoThumbnailPresets.createOpticalFilterItems() }
    val profileItems = remember { PhotoThumbnailPresets.createColorProfileItems() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(colors.surface)
            .border(width = 1.dp, color = colors.borderStrong)
    ) {
        // 1. Primary Tool Tabs (Scrollable horizontal thumb strip)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ThumbTab.values().forEach { tab ->
                val isSelected = tab == activeTab
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) colors.surfaceElevated else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) colors.accentAmber else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable {
                            if (activeTab != tab) {
                                hapticManager?.performDialTick()
                                activeTab = tab
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.title,
                        style = LightRumorTheme.typography.Tab,
                        color = if (isSelected) colors.accentAmber else colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSubtle)
        )

        // 2. Active Tab Content (Lightroom Precision Sliders & Real Photo Thumbnails)
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(LightRumorMotionSpecs.DURATION_SNAP_MS, easing = LightRumorMotionSpecs.PanelSlideEasing)) togetherWith
                fadeOut(animationSpec = tween(LightRumorMotionSpecs.DURATION_FADE_MS))
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "ThumbTabContent"
        ) { tab ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 12.dp)
            ) {
                when (tab) {
                    ThumbTab.Light -> {
                        LightroomSlider(
                            label = "Exposure",
                            value = params.exposureEV,
                            onValueChange = { onParamsChange(params.copy(exposureEV = it)) },
                            range = -5.0f..5.0f,
                            defaultValue = 0.0f,
                            unit = "EV",
                            step = 0.05f,
                            hapticManager = hapticManager,
                            onLongPressDial = {
                                onOpenPrecisionDial("Exposure", params.exposureEV, -5.0f..5.0f, "EV") {
                                    onParamsChange(params.copy(exposureEV = it))
                                }
                            }
                        )

                        LightroomSlider(
                            label = "Contrast",
                            value = params.contrast,
                            onValueChange = { onParamsChange(params.copy(contrast = it)) },
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager,
                            onLongPressDial = {
                                onOpenPrecisionDial("Contrast", params.contrast, -100f..100f, "%") {
                                    onParamsChange(params.copy(contrast = it))
                                }
                            }
                        )

                        LightroomSlider(
                            label = "Highlights",
                            value = params.highlights,
                            onValueChange = { onParamsChange(params.copy(highlights = it)) },
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )

                        LightroomSlider(
                            label = "Shadows",
                            value = params.shadows,
                            onValueChange = { onParamsChange(params.copy(shadows = it)) },
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )

                        LightroomSlider(
                            label = "Whites",
                            value = params.whites,
                            onValueChange = { onParamsChange(params.copy(whites = it)) },
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )

                        LightroomSlider(
                            label = "Blacks",
                            value = params.blacks,
                            onValueChange = { onParamsChange(params.copy(blacks = it)) },
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )
                    }

                    ThumbTab.Color -> {
                        // Photographic Profile Selector Tile Strip (Zero emojis)
                        Text(
                            text = "COLOR PROFILES",
                            style = LightRumorTheme.typography.Label,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                        PhotoThumbnailSelector(
                            items = profileItems,
                            selectedId = selectedProfileId,
                            onItemSelected = {
                                hapticManager?.performDialTick()
                                selectedProfileId = it.id
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        LightroomSlider(
                            label = "Temperature",
                            value = params.kelvin,
                            onValueChange = { onParamsChange(params.copy(kelvin = it)) },
                            range = 2000f..12000f,
                            defaultValue = 5500f,
                            unit = "K",
                            displayDecimals = 0,
                            step = 50f,
                            hapticManager = hapticManager,
                            onLongPressDial = {
                                onOpenPrecisionDial("Temperature", params.kelvin, 2000f..12000f, "K") {
                                    onParamsChange(params.copy(kelvin = it))
                                }
                            }
                        )

                        LightroomSlider(
                            label = "Tint",
                            value = params.tint,
                            onValueChange = { onParamsChange(params.copy(tint = it)) },
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "",
                            displayDecimals = 1,
                            step = 0.5f,
                            hapticManager = hapticManager
                        )

                        LightroomSlider(
                            label = "Vibrance",
                            value = params.vibrance,
                            onValueChange = { onParamsChange(params.copy(vibrance = it)) },
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )

                        LightroomSlider(
                            label = "Saturation",
                            value = params.saturation,
                            onValueChange = { onParamsChange(params.copy(saturation = it)) },
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )
                    }

                    ThumbTab.Mixer -> {
                        ColorMixerScreen(
                            params = params,
                            onParamsChange = onParamsChange,
                            hapticManager = hapticManager
                        )
                    }

                    ThumbTab.Detail -> {
                        DetailDenoiseScreen(
                            params = params,
                            onParamsChange = onParamsChange,
                            hapticManager = hapticManager
                        )
                    }

                    ThumbTab.Optics -> {
                        Text(
                            text = "MONOCHROME OPTICAL FILTERS",
                            style = LightRumorTheme.typography.Label,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                        PhotoThumbnailSelector(
                            items = filterItems,
                            selectedId = selectedFilterId,
                            onItemSelected = {
                                hapticManager?.performDialTick()
                                selectedFilterId = it.id
                                onParamsChange(params.copy(isMonochrome = it.id != "std"))
                            }
                        )
                    }

                    ThumbTab.Geometry -> {
                        CropRotateScreen(
                            params = params,
                            onParamsChange = onParamsChange,
                            hapticManager = hapticManager
                        )
                    }
                }
            }
        }
    }
}

