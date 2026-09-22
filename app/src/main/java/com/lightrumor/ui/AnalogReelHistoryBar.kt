package com.lightrumor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AnalogReelHistoryBar: 35mm Film Reel Skeuomorphic Undo/Redo & History Engine.
 * Features:
 * - Analog film reel sprockets, frame ticks, and timecodes
 * - Drag to rotate reel with mechanical haptic tick feedback
 * - 0ms instantaneous time travel to 10, 50, or 100 steps in the past
 * - Snapshot Branching support: switch between parallel non-destructive edit branches
 * - Zero AI dependencies, zero emojis, industrial camera chassis design.
 */
@Composable
fun AnalogReelHistoryBar(
    historyManager: HistoryManager,
    onStateRestored: (HistoryNode) -> Unit,
    hapticManager: HapticManager?,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    val timeline = historyManager.getActiveLinearTimeline()
    val currentNode = historyManager.getCurrentNode()
    val branches = historyManager.getBranches()
    val activeBranchId = historyManager.getActiveBranchId()

    var dragAccumulator by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(1.dp, colors.borderStrong)
    ) {
        // 1. TOP HEADER: Status, Active Step, Branch Selector Pills, Undo/Redo Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "REEL STEP #${currentNode?.stepIndex ?: 0}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.accentAmber
                )
                Text(
                    text = "[${currentNode?.actionLabel ?: "BASE"}]",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    maxLines = 1
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable {
                            hapticManager?.performDialTick()
                            historyManager.jumpStepsBack(50)?.let { onStateRestored(it) }
                        }
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "-50",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.accentAmber
                    )
                }

                Box(
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable {
                            hapticManager?.performDialTick()
                            historyManager.jumpStepsBack(10)?.let { onStateRestored(it) }
                        }
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "-10",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.accentAmber
                    )
                }

                Box(
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable(enabled = historyManager.canUndo()) {
                            hapticManager?.performDialTick()
                            historyManager.undo()?.let { onStateRestored(it) }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "戻す",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (historyManager.canUndo()) colors.textPrimary else colors.textSecondary.copy(alpha = 0.35f)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable(enabled = historyManager.canRedo()) {
                            hapticManager?.performDialTick()
                            historyManager.redo()?.let { onStateRestored(it) }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "進む",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (historyManager.canRedo()) colors.textPrimary else colors.textSecondary.copy(alpha = 0.35f)
                    )
                }
            }
        }

        // Branch Selection Strip (if branches exist)
        if (branches.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BRANCHES:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
                branches.forEach { branch ->
                    val isBranchActive = (branch.branchId == activeBranchId)
                    Box(
                        modifier = Modifier
                            .background(if (isBranchActive) colors.accentAmber else colors.surfaceElevated, RoundedCornerShape(2.dp))
                            .border(1.dp, if (isBranchActive) colors.accentAmber else colors.borderSubtle, RoundedCornerShape(2.dp))
                            .clickable {
                                hapticManager?.performDialTick()
                                historyManager.switchBranch(branch.branchId)?.let { onStateRestored(it) }
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = branch.name.uppercase(),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isBranchActive) colors.surface else colors.textPrimary
                        )
                    }
                }
            }
        }

        // 2. 35MM ANALOG FILM REEL STRIP
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(Color(0xFF0D0E10))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragAccumulator += delta
                        val threshold = 35f
                        if (dragAccumulator > threshold) {
                            dragAccumulator = 0f
                            if (historyManager.canUndo()) {
                                hapticManager?.performDialTick()
                                historyManager.undo()?.let { onStateRestored(it) }
                            }
                        } else if (dragAccumulator < -threshold) {
                            dragAccumulator = 0f
                            if (historyManager.canRedo()) {
                                hapticManager?.performDialTick()
                                historyManager.redo()?.let { onStateRestored(it) }
                            }
                        }
                    }
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(24) {
                        Box(
                            modifier = Modifier
                                .width(7.dp)
                                .height(6.dp)
                                .background(Color(0xFF1E2024), RoundedCornerShape(1.dp))
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    timeline.forEach { node ->
                        val isCurrent = (node.id == currentNode?.id)
                        FilmFrameCell(
                            node = node,
                            isCurrent = isCurrent,
                            onClick = {
                                hapticManager?.performDialTick()
                                historyManager.jumpToNode(node.id)?.let { onStateRestored(it) }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(24) {
                        Box(
                            modifier = Modifier
                                .width(7.dp)
                                .height(6.dp)
                                .background(Color(0xFF1E2024), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmFrameCell(
    node: HistoryNode,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val colors = LightRumorTheme.colors

    Box(
        modifier = Modifier
            .width(74.dp)
            .fillMaxHeight()
            .background(
                if (isCurrent) colors.surfacePressed else Color(0xFF141619),
                RoundedCornerShape(2.dp)
            )
            .border(
                1.dp,
                if (isCurrent) colors.accentAmber else Color(0xFF26282E),
                RoundedCornerShape(2.dp)
            )
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "#${"%02d".format(node.stepIndex)}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isCurrent) colors.accentAmber else colors.textSecondary
            )
            Text(
                text = node.actionLabel,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = if (isCurrent) colors.textPrimary else colors.textSecondary,
                maxLines = 1
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(22.dp)
                        .height(2.dp)
                        .background(colors.accentAmber)
                )
            }
        }
    }
}

