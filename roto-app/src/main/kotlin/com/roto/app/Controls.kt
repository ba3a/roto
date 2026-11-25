package com.roto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import kotlin.math.roundToInt

/** A message the artist can select and copy, such as an error; [colour] defaults to the error colour. */
@Composable
internal fun SelectableMessage(
    message: String,
    modifier: Modifier = Modifier,
    colour: Color = MaterialTheme.colorScheme.error,
) {
    SelectionContainer { Text(message, modifier, color = colour) }
}

/** A titled group of settings in a side panel. */
@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** A labelled slider. */
@Composable
internal fun Setting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    help: HelpControl? = null,
    onChange: (Float) -> Unit,
) {
    Helped(help) {
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Slider(value = value, onValueChange = onChange, valueRange = range)
        }
    }
}

/** A labelled slider over whole numbers. */
@Composable
internal fun Radius(
    label: String,
    value: Int,
    maximum: Int,
    minimum: Int = 0,
    help: HelpControl? = null,
    onChange: (Int) -> Unit,
) {
    Setting("$label: $value", value.toFloat(), minimum.toFloat()..maximum.toFloat(), help) {
        onChange(it.roundToInt())
    }
}

/** A button showing [label] and its [value], opening a list of [options]; those in [marked] show a dot. */
@Composable
internal fun Choice(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    help: HelpControl? = null,
    marked: Set<String> = emptySet(),
    onChoose: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Helped(help) {
            OutlinedButton(onClick = { open = true }, enabled = enabled) { Text("$label: $value") }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option in marked) "• $option" else option) },
                    onClick = {
                        open = false
                        onChoose(option)
                    },
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}
