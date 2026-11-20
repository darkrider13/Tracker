package com.myapp.app.ui.popup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.util.UpdateChecker
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.tr
import com.myapp.app.util.trf
import java.util.Locale

private val DownloadGreen = Color(0xFF16A34A)

private fun sizeText(bytes: Long): String {
    val mb = bytes / 1048576.0
    return if (mb >= 1.0) String.format(Locale.US, "%.1f", mb) else String.format(Locale.US, "%.2f", mb)
}

private fun etaText(secs: Long): String =
    if (secs >= 60) trf("update_eta_min", "{m} min {s} sec left", "m" to (secs / 60), "s" to (secs % 60))
    else trf("update_eta_sec", "{s} sec left", "s" to secs)

/** Popup with a progress bar + percent while the new APK downloads; the installer opens when it is done. */
@Composable
fun UpdateDownloadOverlay() {
    if (!UpdateChecker.downloading) return
    val c = AppTheme.colors
    val dark = !c.isLight
    val percent = UpdateChecker.percent
    val done = UpdateChecker.downloadedBytes
    val total = UpdateChecker.totalBytes
    val speed = UpdateChecker.speedBps
    val unitMb = tr("update_unit_mb", "MB")
    val animFrac by animateFloatAsState(
        targetValue = if (percent >= 0) percent / 100f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "downloadProgress",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(if (dark) Color(0xFF141414) else Color.White, shape)
                .border(1.dp, if (dark) Color(0xFF2A2A2A) else Color(0xFFE5E7EB), shape)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText(
                text = tr("update_downloading_title", "Downloading update"), size = 16f, lineHeight = 24f,
                weight = FontWeight.ExtraBold, color = c.textGray900, align = TextAlign.Center,
            )
            AppText(
                text = if (percent >= 0) localizeDigits("$percent%") else "…", size = 28f, lineHeight = 36f,
                weight = FontWeight.ExtraBold, color = DownloadGreen, align = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            // progress bar
            val track = RoundedCornerShape(50)
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(track)
                    .background(if (dark) Color(0xFF2A2A2A) else Color(0xFFE5E7EB), track),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animFrac)
                        .height(8.dp)
                        .clip(track)
                        .background(DownloadGreen, track),
                )
            }
            // downloaded / total size and speed
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = localizeDigits(
                        if (total > 0) sizeText(done) + " / " + sizeText(total) + " " + unitMb
                        else sizeText(done) + " " + unitMb,
                    ),
                    size = 12f, lineHeight = 18f, weight = FontWeight.Bold, color = c.textGray900,
                )
                AppText(
                    text = if (speed > 0) localizeDigits(sizeText(speed) + " " + tr("update_unit_mbps", "MB/s")) else "",
                    size = 12f, lineHeight = 18f, weight = FontWeight.Bold, color = DownloadGreen,
                )
            }
            val eta = if (total > 0 && speed > 0) ((total - done).coerceAtLeast(0L) / speed) else -1L
            if (eta >= 0) {
                AppText(
                    text = localizeDigits(etaText(eta)),
                    size = 11f, lineHeight = 16.5f, color = Color(0xFF9CA3AF),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
            AppText(
                text = tr("update_downloading_hint", "Installation starts automatically when the download finishes."),
                size = 11f, lineHeight = 16.5f, color = Color(0xFF9CA3AF), align = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            ModalButton(
                text = tr("common_cancel", "Cancel"),
                kind = ModalButtonKind.Secondary,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { UpdateChecker.cancelDownload() }
        }
    }
}
