package rocks.dearanayji.gloss.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.dearanayji.gloss.data.ModelState
import rocks.dearanayji.gloss.model.ModelCatalog
import rocks.dearanayji.gloss.ui.theme.LocalGlossColors
import java.util.Locale

@Composable
fun DownloadCard(
    modelState: ModelState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlossColors.current
    if (modelState is ModelState.Ready) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(
            1.dp,
            if (modelState is ModelState.Error) colors.statusError else colors.border
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (modelState) {
                ModelState.NotDownloaded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Download offline translation pack",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "NLLB-200 INT8 · ${formatMb(ModelCatalog.TOTAL_BYTES)} · 202 languages",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "One-time download pinned on Hugging Face. After this, every sentence and word tap works 100% offline.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onStartDownload,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Download (${formatMb(ModelCatalog.TOTAL_BYTES)})", fontWeight = FontWeight.SemiBold)
                    }
                }

                is ModelState.Downloading -> {
                    val p = modelState.progress
                    val pct = (p.fraction * 100f).toInt().coerceIn(0, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (p.verifying) "Verifying ${p.currentFileName}…"
                            else "Downloading ${p.currentFileName} (${p.currentFileIndex}/${p.totalFiles})",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$pct%",
                            color = colors.secondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { p.fraction },
                        color = colors.secondary,
                        trackColor = colors.surfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val speedStr = if (p.bytesPerSecond > 0L) {
                            " · ${formatSpeed(p.bytesPerSecond)}"
                        } else ""
                        Text(
                            text = "${formatMb(p.downloadedBytes)} / ${formatMb(p.totalBytes)}$speedStr",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        OutlinedButton(
                            onClick = onCancelDownload,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text("Cancel", color = colors.textPrimary, fontSize = 12.sp)
                        }
                    }
                }

                ModelState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = colors.secondary
                        )
                        Column {
                            Text(
                                text = "Loading NLLB-200 into memory…",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Memory-mapping INT8 weights for offline inference",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                is ModelState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = colors.statusError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = modelState.message,
                            color = colors.statusError,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = if (modelState.downloadedBeforeFailure) onRetryLoad else onStartDownload,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text(if (modelState.downloadedBeforeFailure) "Retry Load" else "Resume Download")
                    }
                }

                ModelState.Ready -> Unit
            }
        }
    }
}

fun formatMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.0f MB", mb)
}

private fun formatSpeed(bytesPerSec: Long): String {
    val mb = bytesPerSec.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB/s", mb)
    } else {
        String.format(Locale.US, "%.0f KB/s", bytesPerSec.toDouble() / 1024.0)
    }
}
