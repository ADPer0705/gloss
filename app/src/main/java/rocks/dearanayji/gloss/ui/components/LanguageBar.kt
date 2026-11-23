package rocks.dearanayji.gloss.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.dearanayji.gloss.data.LanguageCatalog
import rocks.dearanayji.gloss.ui.theme.LocalGlossColors

@Composable
fun LanguageBar(
    sourceLanguage: LanguageCatalog.Language,
    targetLanguage: LanguageCatalog.Language,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlossColors.current
    var swapTurns by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = swapTurns * 180f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swap_rotation"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LanguagePill(
            caption = "FROM",
            language = sourceLanguage,
            accentColor = colors.primary,
            onClick = onSourceClick,
            modifier = Modifier.weight(1f)
        )

        OutlinedIconButton(
            onClick = {
                swapTurns++
                onSwapClick()
            },
            shape = CircleShape,
            border = BorderStroke(1.dp, colors.border),
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = colors.surface,
                contentColor = colors.secondary
            ),
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = "Swap languages",
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }

        LanguagePill(
            caption = "TO",
            language = targetLanguage,
            accentColor = colors.secondary,
            onClick = onTargetClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LanguagePill(
    caption: String,
    language: LanguageCatalog.Language,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlossColors.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontSize = 10.sp
                )
                Text(
                    text = language.name,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = language.nativeName,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
