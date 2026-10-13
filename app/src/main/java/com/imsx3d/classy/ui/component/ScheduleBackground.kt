package com.imsx3d.classy.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.imsx3d.classy.data.BackgroundAdjustments
import com.imsx3d.classy.data.BackgroundSoftening
import java.io.File
import kotlin.math.roundToInt
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
            val adjustments = remember(revision) { BackgroundAdjustments.read(context) }
            BackgroundImage(file, adjustments, Modifier.matchParentSize())
        }
        CompositionLocalProvider(LocalScheduleBackground provides (file != null)) { content() }
    }
}

/** Production and preview share image processing, cropping and theme overlay. */
@Composable
private fun BackgroundImage(file: File, adjustments: BackgroundAdjustments, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = "${file.absolutePath}-${file.lastModified()}-${file.length()}"
    val sharp = remember(key) { ImageRequest.Builder(context).data(file).memoryCacheKey(key).build() }
    val soft = remember(key) { ImageRequest.Builder(context).data(file).memoryCacheKey("$key-soft")
        .allowHardware(false).transformations(BackgroundSoftening()).build() }
    val filter = remember(adjustments.saturation) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(adjustments.saturation) })
    }
    val alignment = BiasAlignment(adjustments.horizontal * 2 - 1, adjustments.vertical * 2 - 1)
    Box(modifier.clip(androidx.compose.ui.graphics.RectangleShape)) {
        AsyncImage(sharp, contentDescription = null, contentScale = ContentScale.Crop,
            alignment = alignment, colorFilter = filter, modifier = Modifier.matchParentSize())
        if (adjustments.softness > 0f) AsyncImage(soft, contentDescription = null,
            contentScale = ContentScale.Crop, alignment = alignment, colorFilter = filter,
            alpha = adjustments.softness, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 1f - adjustments.visibility)))
    }
}

/** Reversible appearance options stay inline; no large task disguised as a bottom sheet. */
@Composable
fun ScheduleBackgroundSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by ScheduleBackgroundStore.revision.collectAsState()
    val hasImage = remember(revision) { ScheduleBackgroundStore.file(context).exists() }
    var adjustments by remember(revision) { mutableStateOf(BackgroundAdjustments.read(context)) }
    var expanded by rememberSaveable { mutableStateOf(false) }
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
        if (hasImage && !busy) {
            SettingsRowDivider()
            SettingsGroupFold(stringResource(R.string.background_adjust), expanded, { expanded = !expanded }) {
                val file = ScheduleBackgroundStore.file(context)
                Box(Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium)) {
                    BackgroundImage(file, adjustments, Modifier.matchParentSize())
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.background_preview), style = MaterialTheme.typography.titleSmall)
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .9f),
                            shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.background_preview_course), Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                SupportingText(stringResource(R.string.background_adjust_hint), Modifier.padding(top = 12.dp))
                BackgroundAdjustmentControls(adjustments, { adjustments = it }, {
                    BackgroundAdjustments.save(context, adjustments)
                }, {
                    adjustments = BackgroundAdjustments()
                    BackgroundAdjustments.save(context, adjustments)
                })
            }
            SettingsRowDivider()
            SettingsGroupRow(stringResource(R.string.background_remove), onClick = {
                scope.launch {
                    busy = true
                    try { ScheduleBackgroundStore.clear(context) }
                    finally { busy = false }
                }
            })
        }
        if (failed) SupportingText(stringResource(R.string.background_failed), Modifier.padding(16.dp))
    }
}

@Composable
internal fun BackgroundAdjustmentControls(
    value: BackgroundAdjustments,
    onChange: (BackgroundAdjustments) -> Unit,
    onFinished: () -> Unit,
    onReset: () -> Unit,
) {
    SettingsSliderRow(stringResource(R.string.background_visibility), "${(value.visibility * 100).roundToInt()}%",
        value.visibility, { onChange(value.copy(visibility = it)) }, 0f..0.65f, onValueChangeFinished = onFinished)
    SettingsSliderRow(stringResource(R.string.background_softness), "${(value.softness * 100).roundToInt()}%",
        value.softness, { onChange(value.copy(softness = it)) }, 0f..1f, onValueChangeFinished = onFinished)
    SettingsSliderRow(stringResource(R.string.background_saturation), "${(value.saturation * 100).roundToInt()}%",
        value.saturation, { onChange(value.copy(saturation = it)) }, 0f..1.5f, onValueChangeFinished = onFinished)
    SupportingText(stringResource(R.string.background_position_hint), Modifier.padding(horizontal = 16.dp))
    SettingsSliderRow(stringResource(R.string.background_horizontal), "${(value.horizontal * 100).roundToInt()}%",
        value.horizontal, { onChange(value.copy(horizontal = it)) }, 0f..1f, onValueChangeFinished = onFinished)
    SettingsSliderRow(stringResource(R.string.background_vertical), "${(value.vertical * 100).roundToInt()}%",
        value.vertical, { onChange(value.copy(vertical = it)) }, 0f..1f, onValueChangeFinished = onFinished)
    TextButton(onClick = onReset) { Text(stringResource(R.string.background_reset)) }
}
