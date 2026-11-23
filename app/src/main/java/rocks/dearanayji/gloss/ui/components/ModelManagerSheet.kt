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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.dearanayji.gloss.data.ModelState
import rocks.dearanayji.gloss.engine.EngineLoadMetrics
import rocks.dearanayji.gloss.model.ModelCatalog
import rocks.dearanayji.gloss.ui.AppUpdateState
import rocks.dearanayji.gloss.ui.theme.LocalGlossColors
import rocks.dearanayji.gloss.update.AppUpdateManager
import java.io.File
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerSheet(
    modelState: ModelState,
    installedSizeBytes: Long,
    lastLoadMetrics: EngineLoadMetrics?,
    currentVersionName: String,
    updateState: AppUpdateState,
    totalGithubDownloads: Long,
    latestReleaseDownloads: Long,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallReadyApk: (File) -> Unit,
    onStartDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalGlossColors.current
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showConfirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App & Offline Model",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Gloss v${currentVersionName.removePrefix("v")} · ${ModelCatalog.MODEL_DISPLAY_NAME}",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── App Updates & GitHub Stats Card ──
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
                    Text(
                        text = "APP & GITHUB RELEASES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                        letterSpacing = 0.8.sp
                    )
                    InfoRow(
                        label = "Installed version",
                        value = "v${currentVersionName.removePrefix("v")}"
                    )
                    InfoRow(
                        label = "Update status",
                        value = when (updateState) {
                            AppUpdateState.Idle -> "Tap Check for updates"
                            AppUpdateState.Checking -> "Checking GitHub…"
                            AppUpdateState.UpToDate -> "Up to date"
                            is AppUpdateState.Available -> "${updateState.release.tagName} available"
                            is AppUpdateState.Downloading -> "Downloading ${(updateState.fraction * 100).toInt()}%"
                            is AppUpdateState.ReadyToInstall -> "${updateState.release.tagName} ready to install"
                            is AppUpdateState.Error -> updateState.message
                        },
                        valueColor = when (updateState) {
                            is AppUpdateState.Available, is AppUpdateState.ReadyToInstall -> colors.secondary
                            AppUpdateState.UpToDate -> colors.statusReady
                            is AppUpdateState.Error -> colors.statusError
                            else -> colors.textPrimary
                        }
                    )
                    InfoRow(
                        label = "GitHub downloads (all releases)",
                        value = if (totalGithubDownloads >= 0L) {
                            NumberFormat.getIntegerInstance().format(totalGithubDownloads)
                        } else {
                            "Check online to fetch"
                        }
                    )
                    if (latestReleaseDownloads >= 0L) {
                        InfoRow(
                            label = "Latest release downloads",
                            value = NumberFormat.getIntegerInstance().format(latestReleaseDownloads)
                        )
                    }

                    if (updateState is AppUpdateState.Downloading) {
                        LinearProgressIndicator(
                            progress = { updateState.fraction },
                            color = colors.secondary,
                            trackColor = colors.surfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (updateState) {
                            is AppUpdateState.Available -> {
                                Button(
                                    onClick = onDownloadUpdate,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.secondary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = null,
                                        tint = colors.bg,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Update (${updateState.release.tagName})",
                                        color = colors.bg,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            is AppUpdateState.ReadyToInstall -> {
                                Button(
                                    onClick = { onInstallReadyApk(updateState.apkFile) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.statusReady),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "Install ${updateState.release.tagName}",
                                        color = colors.bg,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = onCheckForUpdates,
                                    enabled = updateState !is AppUpdateState.Checking && updateState !is AppUpdateState.Downloading,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colors.border),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Check updates", color = colors.textPrimary, fontSize = 13.sp)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { uriHandler.openUri(AppUpdateManager.RELEASES_PAGE_URL) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Releases", color = colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Offline Model Card ──
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
                    Text(
                        text = "OFFLINE TRANSLATION MODEL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondary,
                        letterSpacing = 0.8.sp
                    )
                    InfoRow(
                        label = "Status",
                        value = when (modelState) {
                            ModelState.Ready -> "Offline ready"
                            ModelState.IdleDownloaded -> "Offline ready (standby)"
                            ModelState.Loading -> "Loading engine…"
                            is ModelState.Downloading -> "Downloading…"
                            ModelState.NotDownloaded -> "Not downloaded"
                            is ModelState.Error -> "Error: ${modelState.message}"
                        },
                        valueColor = when (modelState) {
                            ModelState.Ready, ModelState.IdleDownloaded -> colors.statusReady
                            is ModelState.Error -> colors.statusError
                            is ModelState.Downloading, ModelState.Loading -> colors.statusWarning
                            ModelState.NotDownloaded -> colors.textSecondary
                        }
                    )
                    if (lastLoadMetrics != null) {
                        InfoRow(
                            label = "Last cold start",
                            value = "${lastLoadMetrics.totalMs} ms (enc ${lastLoadMetrics.encoderMs} · dec ${lastLoadMetrics.decoderMs})"
                        )
                    }
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
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
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
