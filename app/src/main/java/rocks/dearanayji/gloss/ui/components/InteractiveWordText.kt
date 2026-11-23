package rocks.dearanayji.gloss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.dearanayji.gloss.engine.WordSegmenter
import rocks.dearanayji.gloss.ui.theme.LocalGlossColors

/**
 * Duolingo-style sentence renderer where every word is individually tappable
 * with a subtle dotted underline and smooth selected highlight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveWordText(
    tokens: List<WordSegmenter.TextToken>,
    selectedWordIndex: Int?,
    enabled: Boolean,
    fontSize: TextUnit = 19.sp,
    accentColor: Color = LocalGlossColors.current.primary,
    onWordTap: (WordSegmenter.TextToken.Word) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlossColors.current
    val lines = remember(tokens) { splitTokensIntoLines(tokens) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { lineTokens ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                lineTokens.forEach { token ->
                    when (token) {
                        is WordSegmenter.TextToken.Word -> {
                            val isSelected = token.index == selectedWordIndex
                            val shape = RoundedCornerShape(8.dp)
                            val bg = if (isSelected) colors.wordSelectedBg else Color.Transparent
                            val borderColor = if (isSelected) accentColor else Color.Transparent
                            val underlineColor = if (isSelected) accentColor else colors.wordUnderline

                            Box(
                                modifier = Modifier
                                    .clip(shape)
                                    .background(bg)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(1.dp, borderColor, shape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(
                                        enabled = enabled,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onWordTap(token) }
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                    .drawBehind {
                                        if (enabled) {
                                            val y = size.height - 1.dp.toPx()
                                            drawLine(
                                                color = underlineColor,
                                                start = Offset(0f, y),
                                                end = Offset(size.width, y),
                                                strokeWidth = 1.4.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(
                                                    floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                                                    0f
                                                )
                                            )
                                        }
                                    }
                            ) {
                                Text(
                                    text = token.display,
                                    color = if (isSelected) accentColor else colors.textPrimary,
                                    fontSize = fontSize,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    lineHeight = fontSize * 1.45f
                                )
                            }
                        }
                        is WordSegmenter.TextToken.Separator -> {
                            Text(
                                text = token.text,
                                color = colors.textSecondary,
                                fontSize = fontSize,
                                lineHeight = fontSize * 1.45f,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun splitTokensIntoLines(
    tokens: List<WordSegmenter.TextToken>
): List<List<WordSegmenter.TextToken>> {
    if (tokens.isEmpty()) return emptyList()
    val result = mutableListOf<MutableList<WordSegmenter.TextToken>>()
    var current = mutableListOf<WordSegmenter.TextToken>()
    for (token in tokens) {
        if (token is WordSegmenter.TextToken.Separator && '\n' in token.text) {
            val parts = token.text.split('\n')
            parts.forEachIndexed { idx, part ->
                if (part.isNotEmpty()) current += WordSegmenter.TextToken.Separator(part)
                if (idx < parts.lastIndex) {
                    result += current
                    current = mutableListOf()
                }
            }
        } else {
            current += token
        }
    }
    if (current.isNotEmpty()) result += current
    return result
}
