package com.imsx3d.classy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.SleepyTheme

/** A local, cancellable press response; no sensor, idle loop or extra navigation. */
@Composable
fun SemesterOverviewCard(tableName: String, startDate: String?, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    var press by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(interactions) {
        interactions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> press = interaction.pressPosition.takeIf { android.animation.ValueAnimator.areAnimatorsEnabled() }
                is PressInteraction.Cancel, is PressInteraction.Release -> press = null
            }
        }
    }
    val x = press?.let { if (size.height > 0) -(it.y / size.height * 2 - 1).coerceIn(-1f, 1f) * 4 else 0f } ?: 0f
    val y = press?.let { if (size.width > 0) (it.x / size.width * 2 - 1).coerceIn(-1f, 1f) * 4 else 0f } ?: 0f
    val tiltX by animateFloatAsState(x, sleepyIndicatorSpec(), label = "overview tilt x")
    val tiltY by animateFloatAsState(y, sleepyIndicatorSpec(), label = "overview tilt y")
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, sleepyIndicatorSpec(), label = "overview expand")
    val stateLabel = stringResource(if (expanded) R.string.overview_collapse else R.string.overview_expand)
    Column(Modifier.fillMaxWidth().onSizeChanged { size = it }
        .graphicsLayer { rotationX = tiltX; rotationY = tiltY; cameraDistance = 20 * density }
        .clip(SleepyTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button,
            onClickLabel = stateLabel) { expanded = !expanded }
        .semantics { stateDescription = stateLabel }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.overview_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.graphicsLayer { rotationZ = arrow },
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
        AnimatedVisibility(expanded, enter = sleepyExpandEnter(), exit = sleepyExpandExit()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tableName.ifBlank { stringResource(R.string.overview_empty) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                startDate?.let { SupportingText(stringResource(R.string.overview_start, it)) }
            }
        }
    }
}
