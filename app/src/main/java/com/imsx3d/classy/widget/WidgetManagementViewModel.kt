package com.imsx3d.classy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imsx3d.classy.SleepyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import androidx.compose.runtime.Composable
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * One row on the "manage widgets" screen.
 *
 * - [widgetId]: the system appWidgetId — used as the row key and to look up
 *   per-widget bindings via [WidgetBindingStore].
 * - [variant]: which of the 9 widget layouts this instance is using.
 * - [tableName]: human-readable name of the schedule currently bound to
 *   this widget, or `null` when the widget follows the app-wide default
 *   (i.e. [WidgetTableResolver.resolveCurrentTable]).
 */
data class PlacedWidgetItem(
    val widgetId: Int,
    val variant: WidgetVariantInfo,
    val tableName: String? = null
)

/**
 * Lists every Sleepy widget instance currently placed on the home screen,
 * across all 9 variants, with the name of the schedule each is bound to
 * (if the user has explicitly picked one).
 *
 * Enumeration uses [AppWidgetManager.getAppWidgetIds] which is a stable
 * IPC to the system — the set of placed widgets is the source of truth
 * here. The repository lookup for table names runs on [Dispatchers.IO]
 * because [ScheduleRepository.getTable] goes through Room.
 */
class WidgetManagementViewModel : ViewModel() {

    private val _state = MutableStateFlow<List<PlacedWidgetItem>?>(null)
    val state: StateFlow<List<PlacedWidgetItem>?> = _state.asStateFlow()
    private var reloadJob: Job? = null

    init { reload() }

    fun reload() {
        if (reloadJob?.isActive == true) return
        reloadJob = viewModelScope.launch {
            val ctx = SleepyApp.get()
            val repo = SleepyApp.get().repository
            val awm = AppWidgetManager.getInstance(ctx)
            val items = withContext(Dispatchers.IO) {
                // One Room round-trip for all names; per-id getTable() is
                // suspend and can't run inside the plain lambdas below.
                val nameById = runCatching { repo.getAllTables() }
                    .getOrDefault(emptyList())
                    .associate { it.id to it.name }
                WidgetManagementCore.buildPlacedItems(
                    variants = ALL_WIDGET_VARIANTS,
                    idsFor = { variant ->
                        runCatching {
                            awm.getAppWidgetIds(ComponentName(ctx, variant.receiverClass))
                        }.getOrDefault(intArrayOf())
                    },
                    bindingFor = { id -> WidgetBindingStore.get(ctx, id) },
                    tableNameFor = { bound -> nameById[bound] }
                )
            }
            _state.value = items
        }
    }
}

/** Mine preloads the same Activity-owned list that the child page reads. */
@Composable
fun rememberWidgetManagementViewModel(): WidgetManagementViewModel = viewModel(
    viewModelStoreOwner = (LocalActivity.current as? ViewModelStoreOwner)
        ?: checkNotNull(LocalViewModelStoreOwner.current)
)
