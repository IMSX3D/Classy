package com.imsx3d.classy.ui.component

import android.util.AtomicFile
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.BuildConfig
import com.imsx3d.classy.R
import com.imsx3d.classy.util.ReleaseNotes
import com.imsx3d.classy.util.ReleaseNotesPolicy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Local, offline release notes, shown once after each upgrade, including pre-feature installs. */
@Composable
fun WhatsNewDialog() {
    val context = LocalContext.current.applicationContext
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<ReleaseNotes?>(null) }
    // noBackupFilesDir prevents another device's acknowledgement from hiding this update.
    val marker = remember { AtomicFile(File(context.noBackupFilesDir, "release-notes-version")) }
    suspend fun acknowledge() = withContext(Dispatchers.IO) {
        val stream = marker.startWrite()
        try {
            stream.write(BuildConfig.VERSION_CODE.toString().toByteArray(Charsets.UTF_8))
            marker.finishWrite(stream)
        } catch (error: Exception) {
            marker.failWrite(stream)
            throw error
        }
    }
    LaunchedEffect(locale) {
        try {
            val pending = withContext(Dispatchers.IO) {
                val acknowledged = runCatching { marker.readFully().toString(Charsets.UTF_8).trim().toLongOrNull() }.getOrNull()
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val updated = info.lastUpdateTime > info.firstInstallTime
                if (ReleaseNotesPolicy.shouldShow(BuildConfig.VERSION_CODE.toLong(), acknowledged, updated)) {
                    context.assets.open("release-notes.json").bufferedReader().use { ReleaseNotes.parse(it.readText(), locale) }
                } else {
                    // Do not lower the marker when an older APK is installed.
                    if (acknowledged == null) acknowledge()
                    null
                }
            }
            notes = pending
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e("WhatsNew", "Could not load release notes", error)
        }
    }
    fun dismiss() {
        scope.launch {
            try {
                acknowledge()
                notes = null
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e("WhatsNew", "Could not acknowledge release notes", error)
                notes = null
            }
        }
    }
    notes?.let { release ->
        AlertDialog(
            onDismissRequest = ::dismiss,
            title = { Text(stringResource(R.string.whats_new_title, release.versionName)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    release.items.forEach { Text("• $it") }
                }
            },
            confirmButton = { TextButton(onClick = ::dismiss) { Text(stringResource(R.string.whats_new_done)) } }
        )
    }
}
