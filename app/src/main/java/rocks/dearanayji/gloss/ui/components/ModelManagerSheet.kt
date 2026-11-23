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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.dearanayji.gloss.data.ModelState
import rocks.dearanayji.gloss.model.ModelCatalog
import rocks.dearanayji.gloss.ui.theme.LocalGlossColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerSheet(
    modelState: ModelState,
    installedSizeBytes: Long,
    onStartDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalGlossColors.current
    val uriHandler = LocalUriHandler.current
    var showConfirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Offline Model",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = ModelCatalog.MODEL_DISPLAY_NAME,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bg,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow(
                        label = "Status",
                        value = when (modelState) {
                            ModelState.Ready -> "Offline ready"
                            ModelState.Loading -> "Loading engine…"
                            is ModelState.Downloading -> "Downloading…"
                            ModelState.NotDownloaded -> "Not downloaded"
                            is ModelState.Error -> "Error: ${modelState.message}"
                        },
                        valueColor = when (modelState) {
                            ModelState.Ready -> colors.statusReady
                            is ModelState.Error -> colors.statusError
                            is ModelState.Downloading, ModelState.Loading -> colors.statusWarning
                            ModelState.NotDownloaded -> colors.textSecondary
                        }
                    )
                    InfoRow(
                        label = "Storage on device",
                        value = if (installedSizeBytes > 0L) formatMb(installedSizeBytes) else "0 MB"
                    )
                    InfoRow(
                        label = "Pinned mirror",
                        value = ModelCatalog.REPO_ID
                    )
                    InfoRow(
                        label = "Revision",
                        value = ModelCatalog.PINNED_REVISION.take(12)
                    )
                    InfoRow(
                        label = "Model license",
                        value = ModelCatalog.LICENSE_NAME
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Attribution: ${ModelCatalog.UPSTREAM_CREDIT}",
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { uriHandler.openUri(ModelCatalog.REPO_URL) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("View on HF", color = colors.textPrimary, fontSize = 13.sp)
                }

                if (installedSizeBytes > 0L && modelState !is ModelState.Downloading) {
                    Button(
                        onClick = { showConfirmDelete = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.statusError.copy(alpha = 0.16f),
                            contentColor = colors.statusError
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Model", fontSize = 13.sp)
                    }
                } else if (modelState is ModelState.NotDownloaded) {
                    Button(
                        onClick = {
                            onStartDownload()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Download", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            containerColor = colors.surface,
            title = { Text("Delete offline model?", color = colors.textPrimary) },
            text = {
                Text(
                    "This removes ~${formatMb(installedSizeBytes)} from your phone. You can re-download it anytime.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        onDeleteModel()
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = colors.statusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = LocalGlossColors.current.textPrimary
) {
    val colors = LocalGlossColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 13.sp)
        Text(text = value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
