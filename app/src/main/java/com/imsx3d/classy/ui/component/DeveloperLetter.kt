package com.imsx3d.classy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.AppIdentity

/** An optional letter opens locally; exploring it never navigates away from About. */
@Composable
fun DeveloperLetter() {
    var open by rememberSaveable { mutableStateOf(false) }
    DeveloperLetter(open = open, onToggle = { open = !open })
}

@Composable
fun DeveloperLetter(open: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val angle by animateFloatAsState(if (open) 180f else 0f, sleepyIndicatorSpec(), label = "letter arrow")
    val stateLabel = stringResource(if (open) R.string.about_letter_close else R.string.about_letter_open)
    SettingsGroupCard {
        Row(Modifier.fillMaxWidth().noRippleClickable(onToggle)
            .semantics(mergeDescendants = true) { role = Role.Button; stateDescription = stateLabel }
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (open) Icons.Outlined.Drafts else Icons.Outlined.Email, null,
                Modifier.size(32.dp), tint = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.about_letter_title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                SupportingText(stringResource(if (open) R.string.about_letter_close else R.string.about_letter_hint))
            }
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp).rotate(angle), tint = colors.onSurfaceVariant)
        }
        AnimatedVisibility(open, enter = sleepyExpandEnter(), exit = sleepyExpandExit()) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRowDivider()
                stringArrayResource(R.array.about_heart_paragraphs).forEach { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp), color = colors.onSurfaceVariant)
                }
                Text("—— ${AppIdentity.AUTHOR_NAME}", Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            }
        }
    }
}
