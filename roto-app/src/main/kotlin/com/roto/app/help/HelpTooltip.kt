package com.roto.app.help

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

private const val TOOLTIP_DELAY_MS = 700
private val TOOLTIP_OFFSET = DpOffset(0.dp, 20.dp)
private val TOOLTIP_WIDTH = 280.dp
private val TOOLTIP_PADDING = 8.dp

/** The help the window shows tooltips from. */
val LocalHelp = compositionLocalOf { HelpLibrary.bundled() }

/**
 * [content] with the help for [control] as its tooltip (none when [control] is null), shown when the
 * pointer rests on it. The text is the first sentence of the control's row in its page, so the page is the one place it is written.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Helped(control: HelpControl?, content: @Composable () -> Unit) {
    val text = control?.let(LocalHelp.current::tooltip)
    if (text == null) {
        content()
        return
    }
    TooltipArea(
        tooltip = {
            Surface(shape = MaterialTheme.shapes.small, shadowElevation = 4.dp) {
                Text(
                    text,
                    Modifier.widthIn(max = TOOLTIP_WIDTH).padding(TOOLTIP_PADDING),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        delayMillis = TOOLTIP_DELAY_MS,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = TOOLTIP_OFFSET),
        content = content,
    )
}
