package com.imsx3d.classy.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.*
import com.imsx3d.classy.ui.theme.DockMaterialPolicy
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/** Isolated, opt-in fixture. Does not change app appearance preferences or timetable data. */
class GlassSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var dark by rememberSaveable { mutableStateOf(false) }
            SleepyThemeProvider(darkTheme = dark) {
                GlassSample(dark, { dark = it }, ::finish)
            }
        }
    }
}

@Composable
private fun GlassSample(dark: Boolean, onDark: (Boolean) -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var powerSave by remember { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { powerSave = power.isPowerSaveMode }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    var requested by rememberSaveable { mutableStateOf(false) }
    var busyBackground by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val enabled = DockMaterialPolicy.useGlass(requested, Build.VERSION.SDK_INT, view.isHardwareAccelerated, powerSave)
    val backdrop = rememberLayerBackdrop()
    val colors = MaterialTheme.colorScheme
    val tint = colors.surfaceContainer
    val shape = RoundedCornerShape(50)
    val material = if (enabled) Modifier.drawPlainBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { blur(8.dp.toPx()); lens(12.dp.toPx(), 8.dp.toPx()) },
        onDrawSurface = { drawRect(tint.copy(alpha = 0.72f)) }
    ) else Modifier.background(tint)
    val items = listOf(
        PillNavItemSpec(Icons.Outlined.CalendarMonth, stringResource(R.string.glass_sample_tab_schedule)),
        PillNavItemSpec(Icons.Outlined.Home, stringResource(R.string.glass_sample_tab_today)),
        PillNavItemSpec(Icons.Outlined.Settings, stringResource(R.string.glass_sample_tab_manage)),
        PillNavItemSpec(Icons.Outlined.Person, stringResource(R.string.glass_sample_tab_mine))
    )
    Box(Modifier.fillMaxSize().background(colors.background)) {
        // Capture content only. Dock is a sibling, so it cannot sample itself or its text.
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(if (enabled) Modifier.layerBackdrop(backdrop) else Modifier),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().statusBarsPadding()) {
                    SettingsPageHeader(stringResource(R.string.glass_sample_title), onBack)
                    SupportingText(stringResource(R.string.glass_sample_intro))
                }
            }
            item {
                Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    SettingsGroupCard {
                        SettingsSwitchRow(stringResource(R.string.glass_sample_enable), checked = requested, onCheckedChange = { requested = it })
                        SettingsSwitchRow(stringResource(R.string.glass_sample_dark), checked = dark, onCheckedChange = onDark)
                        SettingsSwitchRow(stringResource(R.string.glass_sample_busy), checked = busyBackground, onCheckedChange = { busyBackground = it })
                        SettingsNoteRow(stringResource(if (enabled) R.string.glass_sample_active else if (requested) R.string.glass_sample_fallback else R.string.glass_sample_solid))
                    }
                }
            }
            item { SupportingText(stringResource(R.string.glass_sample_selected, items[selected].label)) }
            items(24) { index ->
                val course = DesignSampleCourses.rows[index % DesignSampleCourses.rows.size]
                val brush = if (busyBackground) Brush.linearGradient(listOf(Color(0xFF235C97), Color(0xFFFFD07D), Color(0xFF764578)))
                    else Brush.linearGradient(listOf(colors.surfaceContainer, colors.surfaceContainer))
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().background(brush, RoundedCornerShape(20.dp)).padding(16.dp)) {
                    // Opaque text surface keeps test content readable even on the stress pattern.
                    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text(course.courseName, style = MaterialTheme.typography.titleMedium)
                        SupportingText("${course.startTime}–${course.endTime} · ${course.room}")
                    }
                }
            }
        }
        PillNavigationBar(items, selected, { selected = it }, Modifier.align(Alignment.BottomCenter), dock = true, dockSurface = material)
    }
}
