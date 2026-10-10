package com.imsx3d.classy.ui.screen.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsGroupFold
import com.imsx3d.classy.ui.component.SupportingText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.imsx3d.classy.R
import com.imsx3d.classy.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WidgetAddPanel(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<WidgetDiscoverySnapshot?>(null) }
    var status by remember { mutableStateOf("") }
    var lastRequest by remember { mutableStateOf("none") }
    var busy by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var choicesExpanded by rememberSaveable { mutableStateOf(false) }
    fun refresh() {
        scope.launch {
            snapshot = withContext(Dispatchers.IO) {
                runCatching { WidgetDiscovery.inspect(context.applicationContext) }.getOrElse {
                    WidgetDiscoverySnapshot(WidgetDiscoveryState.UNKNOWN, "queryError=${it.javaClass.simpleName}")
                }
            }
            onRefresh()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { busy = false; refresh() }
        }
        lifecycle.addObserver(observer)
        refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    SettingsGroupCard {
        SettingsGroupFold(
            title = stringResource(R.string.widget_add_title),
            expanded = choicesExpanded,
            onToggle = { choicesExpanded = !choicesExpanded }
        ) {
            SupportingText(stringResource(R.string.widget_add_help))
            ALL_WIDGET_VARIANTS.forEach { variant ->
                androidx.compose.material3.FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    onClick = {
                        busy = true
                        val result = WidgetDiscovery.requestPin(context, variant)
                        lastRequest = "${variant.receiverClass.simpleName}: $result"
                        status = context.getString(when (result) {
                            WidgetPinResult.REQUESTED -> R.string.widget_pin_requested
                            WidgetPinResult.UNSUPPORTED -> R.string.widget_pin_unsupported
                            WidgetPinResult.REJECTED, WidgetPinResult.ERROR -> R.string.widget_pin_failed
                        })
                        // No success claim or timeout-based failure: the launcher owns confirmation.
                        busy = false
                        refresh()
                    }
                ) { Text(stringResource(variant.displayNameRes)) }
            }
        }
        if (status.isNotEmpty()) SupportingText(status, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SettingsGroupFold(
            title = stringResource(R.string.widget_diagnostics),
            expanded = expanded,
            onToggle = { expanded = !expanded; if (expanded) refresh() }
        ) {
                SupportingText(stringResource(R.string.widget_manual_help))
                Text(stringResource(when (snapshot?.state) {
                    WidgetDiscoveryState.REGISTERED -> R.string.widget_system_registered
                    WidgetDiscoveryState.MISSING -> R.string.widget_system_missing
                    else -> R.string.widget_system_unknown
                }))
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Classy widget diagnostics",
                        snapshot?.report.orEmpty() + "\nlastPin=$lastRequest"))
                    status = context.getString(R.string.widget_diagnostics_copied)
                }, enabled = snapshot != null) { Text(stringResource(R.string.widget_copy_diagnostics)) }
                TextButton(onClick = { refresh() }) { Text(stringResource(R.string.widget_refresh_diagnostics)) }
        }
    }
}
