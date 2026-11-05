package com.efprojects.vombatsu_client.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.efprojects.vombatsu_client.R

@Composable
fun PostBlocks(blocks: List<Block>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { BlockView(it) }
    }
}

@Composable
private fun BlockView(block: Block) {
    when (block) {
        is Block.Paragraph -> InlineText(block.inlines, MaterialTheme.typography.bodyLarge)
        is Block.Heading -> InlineText(
            block.inlines,
            if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        )
        is Block.Quote -> QuoteView(block)
        is Block.Items -> ItemsView(block)
        Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
        is Block.MediaBlock -> MediaView(block.media)
        is Block.Gallery -> GalleryView(block.items)
        is Block.Embed -> EmbedView(block)
        is Block.Unsupported -> Text(
            stringResource(R.string.unsupported_block),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuoteView(block: Block.Quote) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        ) {}
        PostBlocks(block.blocks, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ItemsView(block: Block.Items) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row {
                Text(
                    if (block.ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
                PostBlocks(item)
            }
        }
    }
}

private val IntSetSaver = listSaver<Set<Int>, Int>(
    save = { it.toList() },
    restore = { it.toSet() },
)

@Composable
private fun InlineText(inlines: List<Inline>, style: TextStyle) {
    var revealed by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet()) }
    val linkColor = MaterialTheme.colorScheme.primary
    val spoilerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(inlines, revealed, linkColor, spoilerColor) {
        annotate(inlines, linkColor, spoilerColor, revealed) { group -> revealed = revealed + group }
    }
    Text(text, style = style)
}

private fun annotate(
    inlines: List<Inline>,
    linkColor: Color,
    spoilerColor: Color,
    revealed: Set<Int>,
    onReveal: (Int) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val groups = spoilerGroups(inlines)
    inlines.forEachIndexed { index, inline ->
        val group = groups[index]
        val hidden = group != null && group !in revealed
        val decorations = listOfNotNull(
            TextDecoration.Underline.takeIf { inline.underline || inline.link != null },
            TextDecoration.LineThrough.takeIf { inline.strike },
        )
        val span = SpanStyle(
            fontWeight = FontWeight.Bold.takeIf { inline.bold },
            fontStyle = FontStyle.Italic.takeIf { inline.italic },
            textDecoration = TextDecoration.combine(decorations),
            baselineShift = BaselineShift.Superscript.takeIf { inline.superscript },
            color = when {
                hidden -> spoilerColor
                inline.link != null -> linkColor
                else -> Color.Unspecified
            },
            background = if (hidden) spoilerColor else Color.Unspecified,
        )
        when {
            hidden -> withLink(LinkAnnotation.Clickable("spoiler-$group") { onReveal(group) }) {
                withStyle(span) { append(inline.text) }
            }
            inline.link != null -> withLink(
                LinkAnnotation.Url(inline.link, TextLinkStyles(span)),
            ) { append(inline.text) }
            else -> withStyle(span) { append(inline.text) }
        }
    }
}
