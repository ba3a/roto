package com.roto.app.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** How wide the help pane is beside the screen. */
val HELP_PANE_WIDTH = 420.dp

private val PADDING = 16.dp
private val GAP = 8.dp
private const val FIRST_COLUMN_SHARE = 0.32f

/** The help pane: the page for the screen showing, with a bar to pick another page or close it. */
@Composable
fun HelpPane(model: HelpModel, modifier: Modifier = Modifier) {
    val state = model.state
    val document = remember(state.page) { model.library.document(state.page) }
    val listState = rememberLazyListState()
    val anchorIndex = state.anchor?.let { anchor ->
        document.blocks.indexOfFirst { it is MarkdownBlock.Heading && it.slug == anchor }
    } ?: -1
    LaunchedEffect(state.page, state.anchor) { listState.scrollToItem(anchorIndex.coerceAtLeast(0)) }
    Surface(modifier.fillMaxHeight().width(HELP_PANE_WIDTH), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            PaneBar(model)
            HorizontalDivider()
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(PADDING),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                items(document.blocks.size) { index -> BlockView(document.blocks[index], model) }
            }
        }
    }
}

@Composable
private fun PaneBar(model: HelpModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = GAP), verticalAlignment = Alignment.CenterVertically) {
        Text("Help", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (model.state.page != HelpPage.INDEX) {
            TextButton(onClick = { model.show(HelpPage.INDEX) }) { Text("All pages") }
        }
        TextButton(onClick = model::close) { Text("Close") }
    }
}

@Composable
private fun BlockView(block: MarkdownBlock, model: HelpModel) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            annotated(block.spans, model),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
        )
        is MarkdownBlock.Paragraph -> Text(annotated(block.spans, model), style = MaterialTheme.typography.bodyMedium)
        is MarkdownBlock.Bullets -> ListView(block.items, model) { "•" }
        is MarkdownBlock.Numbered -> ListView(block.items, model) { "${it + 1}." }
        is MarkdownBlock.Table -> TableView(block, model)
    }
}

@Composable
private fun ListView(items: List<List<Span>>, model: HelpModel, marker: (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row {
                Text(marker(index), Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium)
                Text(annotated(item, model), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A table as rows of cells: the first column narrow, as it names a control, the rest sharing what is left. */
@Composable
private fun TableView(table: MarkdownBlock.Table, model: HelpModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TableRow(table.header, model, bold = true)
        HorizontalDivider()
        table.rows.forEach { row -> TableRow(row, model, bold = false) }
    }
}

@Composable
private fun TableRow(cells: List<List<Span>>, model: HelpModel, bold: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        cells.forEachIndexed { index, cell ->
            val share = if (index == 0) FIRST_COLUMN_SHARE else (1f - FIRST_COLUMN_SHARE) / (cells.size - 1)
            Text(
                annotated(cell, model),
                Modifier.weight(share),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold || index == 0) FontWeight.Medium else null,
            )
        }
    }
}

/** [spans] as styled text; a link goes to another page in the pane, or to the web in the browser. */
@Composable
private fun annotated(spans: List<Span>, model: HelpModel): AnnotatedString {
    val handler = LocalUriHandler.current
    val colours = MaterialTheme.colorScheme
    val linkStyle = TextLinkStyles(SpanStyle(color = colours.primary, textDecoration = TextDecoration.Underline))
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = colours.surfaceVariant)
    return buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is Span.Plain -> append(span.text)
                is Span.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                is Span.Code -> withStyle(codeStyle) { append(span.text) }
                is Span.Link -> withLink(
                    LinkAnnotation.Clickable(span.target, linkStyle) {
                        if (!model.follow(span.target)) handler.openUri(span.target)
                    },
                ) { append(span.text) }
            }
        }
    }
}
