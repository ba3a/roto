package com.roto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import com.roto.core.review.LintFinding

private val LIST_HEIGHT = 160.dp

/**
 * The lint of the clip under the flipbook: what each rule found, and the findings in report order.
 * Picking one shows its frame in the player and marks its pixel. The lint only reports.
 */
@Composable
internal fun LintPanel(model: LintModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Art lint", style = MaterialTheme.typography.titleMedium)
        when (val load = model.load) {
            LintLoad.Loading -> Text("Checking the art…", style = MaterialTheme.typography.bodyMedium)
            is LintLoad.Disabled -> SelectableMessage("Lint is off: ${load.reason}")
            is LintLoad.Ready -> LintFindings(model, load)
        }
    }
}

@Composable
private fun LintFindings(model: LintModel, load: LintLoad.Ready) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val findings = load.report.findings
    if (findings.isEmpty()) {
        Text("No findings", style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            model.summary.filter { it.second > 0 }.joinToString(" · ") { "${it.first}: ${it.second}" },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        LazyColumn(Modifier.heightIn(max = LIST_HEIGHT)) {
            items(findings) { finding ->
                val highlight =
                    if (finding == model.selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                Helped(HelpControl.LINT_FINDING) {
                    Text(
                        describe(finding),
                        Modifier.fillMaxWidth().background(highlight)
                            .clickable { model.select(finding) }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (model.skipped.isNotEmpty()) {
        Text(
            "Not checked, no art yet: frames ${model.skipped.joinToString { (it + 1).toString() }}",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

private fun describe(finding: LintFinding): String {
    val pixel = finding.pixel?.let { " (${it.x}, ${it.y})" }.orEmpty()
    return "${finding.rule} · frame ${finding.frame + 1}$pixel: ${finding.message}"
}
