package com.imsx3d.classy.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.imsx3d.classy.R
import com.imsx3d.classy.data.ScheduleBackgroundStore
import kotlinx.coroutines.launch

val LocalScheduleBackground = staticCompositionLocalOf { false }

@Composable
fun ScheduleBackground(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val revision by ScheduleBackgroundStore.revision.collectAsState()
    val file = remember(revision) { ScheduleBackgroundStore.file(context).takeIf { it.exists() } }
    Box(Modifier.fillMaxSize()) {
        if (file != null) {
            AsyncImage(model = ImageRequest.Builder(context).data(file)
                .memoryCacheKey("schedule-$revision-${file.lastModified()}").build(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)))
        }
        CompositionLocalProvider(LocalScheduleBackground provides (file != null)) { content() }
    }
}

/** Image selection is a system task; the short controls and explanation stay in Appearance. */
@Composable
fun ScheduleBackgroundSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by ScheduleBackgroundStore.revision.collectAsState()
    val hasImage = remember(revision) { ScheduleBackgroundStore.file(context).exists() }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true; failed = false
            try { ScheduleBackgroundStore.import(context, uri) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    SettingsGroupCard {
        SettingsGroupRow(title = stringResource(R.string.background_title),
            subtitle = stringResource(R.string.background_hint),
            onClick = if (busy) null else ({ picker.launch("image/*") }),
            trailing = { if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                else Text(stringResource(if (hasImage) R.string.background_change else R.string.background_choose),
                    color = MaterialTheme.colorScheme.primary) })
        if (hasImage) {
            SettingsRowDivider()
            SettingsGroupRow(stringResource(R.string.background_remove), onClick = if (busy) null else ({
                scope.launch { ScheduleBackgroundStore.clear(context) }
            }))
        }
        if (failed) SupportingText(stringResource(R.string.background_failed), Modifier.padding(16.dp))
    }
}
