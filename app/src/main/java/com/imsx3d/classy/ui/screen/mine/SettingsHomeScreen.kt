package com.imsx3d.classy.ui.screen.mine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SettingsRowDivider
import com.imsx3d.classy.ui.component.SettingsScaffold

/** Choose a topic first; individual preferences live in their existing pages. */
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenScheduleDisplay: () -> Unit,
    onOpenGeneral: () -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.settings_home_title), onBack = onBack) {
        item {
            SettingsGroupCard {
                SettingsTopic(R.string.schedule_display_title, onOpenScheduleDisplay)
                SettingsRowDivider()
                SettingsTopic(R.string.mine_general, onOpenGeneral)
            }
        }
    }
}

@Composable
private fun SettingsTopic(title: Int, onClick: () -> Unit) {
    SettingsGroupRow(title = stringResource(title), onClick = onClick, trailing = {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    })
}
