package com.imsx3d.classy

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import com.imsx3d.classy.ui.nav.NavSession
import com.imsx3d.classy.ui.nav.rememberSleepyNavigator
import com.imsx3d.classy.ui.nav.SleepyNavHost
import com.imsx3d.classy.ui.nav.SleepyRoute
import com.imsx3d.classy.ui.nav.holdsUnsavedInput
import com.imsx3d.classy.ui.nav.SleepyNavigator
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.ui.screen.schedule.ViewMode
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.jw.JwImportDraftCodec
import com.imsx3d.classy.ui.screen.imports.ImportDraft
import com.imsx3d.classy.ui.screen.imports.JwImportActivity
import com.imsx3d.classy.ui.screen.edit.AddCourseScreen
import com.imsx3d.classy.ui.component.NavDockSpec
import com.imsx3d.classy.ui.component.PillNavigationBar
import com.imsx3d.classy.ui.component.PillBarState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import com.imsx3d.classy.ui.component.PillNavItemSpec
import com.imsx3d.classy.ui.screen.manage.ManagementPage
import com.imsx3d.classy.ui.screen.widget.WidgetManagementScreen
import com.imsx3d.classy.ui.screen.widget.WidgetEditScreen
import com.imsx3d.classy.ui.screen.mine.AllTablesScreen
import com.imsx3d.classy.ui.screen.mine.AppearanceScreen
import com.imsx3d.classy.ui.screen.mine.MineScreen
import com.imsx3d.classy.ui.screen.mine.EditTableScreen
import com.imsx3d.classy.ui.screen.mine.GeneralSettingsScreen
import com.imsx3d.classy.ui.screen.mine.HolidaySettingsScreen
import com.imsx3d.classy.ui.screen.mine.ExportScreen
import com.imsx3d.classy.ui.screen.mine.ReminderScreen
import com.imsx3d.classy.ui.screen.mine.AboutScreen
import com.imsx3d.classy.ui.screen.mine.LicenseScreen
import com.imsx3d.classy.data.CustomThemeStore
import com.imsx3d.classy.ui.screen.schedule.ScheduleScreen
import com.imsx3d.classy.ui.screen.today.TodayScreen
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import com.imsx3d.classy.util.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * 真实系统深浅(与"应用内模式"区分) —— 跟随系统模式下要用它,不能用被覆写后的 resources。
     */
    private var realSystemDark: Boolean = false

    /**
     * UI-4u：把**资源层的深浅**对齐到"应用内深浅设置"。
     *
     * 背景：本 App 的深色是**应用内设置**（themeMode=dark），系统 uiMode 可能仍是 day →
     * `values-night/` 永远不会被选中：`@color/splash_background` 取到浅色 #FAF8F2，
     * 主题也停在 Material.Light。于是所有"系统窗口侧"的底色都是浅的 —— 用户报障的
     * "进子页面时四角白光一闪"（转场没铺满时露出的就是 Window 底色）与"冷启动闪一下亮底"
     * 都是同一个根因，只是显现位置不同。
     * 改法：attach 时按应用内模式合成一份 uiMode 覆盖后的 Configuration，
     * 让 -night 限定符与主题父链都跟着应用内设置走（AppCompat 的 setDefaultNightMode 同思路，
     * 但本项目是 ComponentActivity，直接覆写 Configuration 更直接）。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val sysDark = (newBase.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        realSystemDark = sysDark
        val appDark = AppPrefs.isDarkMode(newBase, sysDark)
        val cfg = Configuration(newBase.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (appDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        super.attachBaseContext(
            com.imsx3d.classy.util.LocaleHelper.wrapDefault(newBase.createConfigurationContext(cfg))
        )
    }

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
        /**
         * 组件点击的「打开哪个 tab」extra,取值 [TAB_SCHEDULE]。
         *
         * 为什么需要:MainActivity 是 singleTask,桌面组件点击时 App 通常**已存在**
         * (只是在后台),系统走 onNewIntent 复用同一个实例 — Compose 侧的
         * `currentTab`(rememberSaveable)仍是上次停留的 tab(常见:「我的」),光靠
         * 把 App 拉到前台得到的是上次那个页面。所以组件点击必须显式带上目标 tab。
         */
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val TAB_SCHEDULE = "schedule"
        fun intentForCourse(context: Context, courseId: Long): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_COURSE_ID, courseId)
            }
        }
        val pendingImportTextState: androidx.compose.runtime.MutableState<String?> =
            androidx.compose.runtime.mutableStateOf(null)
        val pendingImportTokenState = androidx.compose.runtime.mutableStateOf<String?>(null)
        var pendingImportToken: String?
            get() = pendingImportTokenState.value
            set(value) { pendingImportTokenState.value = value }
        @Volatile var incomingImportText: String? = null
        var pendingImportText: String?
            get() = pendingImportTextState.value
            set(v) { pendingImportTextState.value = v }
        // 无表空态 → "导入第一张课表" 引导: 切管理页时自动弹 ImportSheet 一次。
        // 会话级一次性 flag (组合态可读), 消费即清 — 避免下次进管理页误弹。
        val autoShowImportOnceState: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(false)

        // 组件点击 → 直达课表 tab 的一次性 flag (组合态可读), AppRoot 消费即清。
        // 与 pendingImportText 同款: 意图从 Activity(intent) 递到 Compose 层 —
        // tab 状态活在组合里(rememberSaveable), Activity 层改不动它。
        val openScheduleOnceState: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(false)
    }

    private val editingCourseFromIntent = MutableStateFlow<CourseEntity?>(null)
    val editingCourseFlow: StateFlow<CourseEntity?> = editingCourseFromIntent.asStateFlow()

    // systemDark 变化信号: configChanges="uiMode" 不重建 Activity, Compose 的
    // isSystemInDarkTheme() 不会自行 recomposition。覆盖 onConfigurationChanged,
    // 把最新 uiMode 推入此 State 触发重组 — dark 即随 systemDark 实时重算。
    // 初始值在 onCreate 赋(取当前配置, 避免冷启时闪一次) — 属性初始化器读
    // resources 会在构造函数阶段执行, 此时 attachBaseContext 未调, resources
    // 访问 NPE → 启动秒崩(v1.0.55 测试包翻车点)。
    private val uiNightModeState: androidx.compose.runtime.MutableState<Int> =
        androidx.compose.runtime.mutableStateOf(Configuration.UI_MODE_NIGHT_UNDEFINED)

    override fun onPostResume() {
        super.onPostResume()
        // Keep the splash logo out of system window snapshots after the first frame.
        androidx.core.view.OneShotPreDrawListener.add(window.decorView) {
            applyWindowBackground()
            true
        }
    }

    /**
     * 窗口底色 = **应用内实际深浅**（不是系统深浅）。
     *
     * UI-4u（2026-09-26 用户报障）：深色模式下从 tab 进子页面时，屏幕四角有一闪而过的白光。
     * 原因：子页面转场是"从中间放大铺满"，还没铺满时露出的那一层是 **Window 底色**，
     * 而底色此前取 `R.color.splash_background` —— 它只有 `values-night/` 里才有深色版本，
     * 只有**系统**处于深色时才会被选中。本 App 的深色是"应用内设置"（themeMode=dark，
     * 系统 uiMode 可能仍是 day）→ 取到的一直是浅色 #FAF8F2 → 转场时白边一闪。
     * 改法：运行时按 `AppPrefs.isDarkMode` 二选一（深色取 values/ 里同值的
     * `splash_background_dark`，不再依赖 -night 限定符）。
     */
    private fun applyWindowBackground() {
        // 注意用 realSystemDark：resources 的 uiMode 已被 attachBaseContext 覆写成"应用内模式"
        val dark = AppPrefs.isDarkMode(this, realSystemDark)
        val color = getColor(
            if (dark) com.imsx3d.classy.R.color.splash_background_dark
            else com.imsx3d.classy.R.color.splash_background
        )
        // 两处都设：
        //  · window drawable —— 系统窗口预览/最近任务缩略图取这层；
        //  · DecorView background —— 转场（子页面放大铺满）时露出的正是这层，
        //    而主题的 windowBackground 也画在它上面：只设 window drawable 会被主题压回去
        //    （实测：应用内从深色切浅色后，转场露出的仍是主题的深色底）。
        window.setBackgroundDrawable(ColorDrawable(color))
        window.decorView.setBackgroundColor(color)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        realSystemDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        uiNightModeState.value = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        // 转场露出的窗口底色随深浅模式走（"跟随系统"时系统一改就得重算）
        applyWindowBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // uiNightModeState 初始值 = **系统**深浅（attachBaseContext 已抓过；
        // 此处不能读 resources —— 它已被覆写成"应用内模式"，语义不同）
        uiNightModeState.value = if (realSystemDark) Configuration.UI_MODE_NIGHT_YES
        else Configuration.UI_MODE_NIGHT_NO
        com.imsx3d.classy.util.UpdateManager.cleanOldApk(this)
        enableEdgeToEdge()
        // 高刷新率(流畅优先): 按开关把窗口钉到屏幕最高刷率, 不表态会被省电逻辑限 60Hz
        com.imsx3d.classy.util.HighRefreshRate.apply(this, com.imsx3d.classy.util.AppPrefs.isHighRefresh(this))
        handleDeepLinkIntent(intent)
        // 启动时检查更新: 用户可在「关于」最底 Toggle 关闭
        com.imsx3d.classy.util.UpdateNotifier.loadDismissedVersion(this)
        com.imsx3d.classy.util.UpdateNotifier.maybeCheckOnStart(this, lifecycleScope)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("mock_update", false)) {
            com.imsx3d.classy.util.UpdateNotifier.showMockUpdate()
        }
        setContent {
            // uiNightModeState.value 变化(composition-observed) → systemDark 重算 →
            // dirty 指派给 remember(systemDark) 触发 dark 重算; 此前 isSystemInDarkTheme()
            // 在 configChanges="uiMode" 场景下不会 recomposition, dark 冻结在首帧值。
            val systemDark = (uiNightModeState.value == Configuration.UI_MODE_NIGHT_YES)
            var themeMode by remember { mutableStateOf(AppPrefs.getThemeMode(this@MainActivity)) }
            var dark by remember(systemDark) { mutableStateOf(AppPrefs.isDarkMode(this@MainActivity, systemDark)) }
            fun applyTheme() { dark = AppPrefs.isDarkMode(this@MainActivity, systemDark) }
            // 窗口/Decor 底色始终跟随"当前实际深浅"（转场露出的那层就是它）
            androidx.compose.runtime.LaunchedEffect(dark) {
                applyWindowBackground()
            }
            val deepLinkCourse by editingCourseFlow.collectAsState()
            val themeKey by AppPrefs.themeKeyFlow(this@MainActivity).collectAsState(initial = AppPrefs.getThemeKey(this@MainActivity))
            // The selected custom theme can be edited in place, so its key does not change.
            // Subscribe to the custom-theme document as a separate invalidation signal.
            val customThemesJson by CustomThemeStore.changes(this@MainActivity)
                .collectAsState(initial = "")
            SleepyThemeProvider(
                darkTheme = dark,
                themeKey = themeKey,
                customThemeVersion = customThemesJson
            ) {
                // GlasenseTheme 现由 SleepyThemeProvider 统一提供（UI-3），此处不再重复包裹。
                AppRoot(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            AppPrefs.setThemeMode(this@MainActivity, mode)
                            themeMode = mode
                            applyTheme()
                            // 应用内切深浅后窗口底色同步（否则转场露出的还是切换前的颜色）
                            applyWindowBackground()
                            // 手动切主题时联动刷新 widget(广播 APPWIDGET_UPDATE)
                            lifecycleScope.launch {
                                com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(this@MainActivity)
                            }
                        },
                        deepLinkCourse = deepLinkCourse,
                        onDeepLinkConsumed = { editingCourseFromIntent.value = null },
                        pendingImportText = pendingImportText ?: pendingImportToken,
                        consumePendingImportText = { MainActivity.pendingImportText = null }
                    )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        // The active draft is durable; stale launch Intents cannot resurrect consumed imports.
        pendingImportToken = com.imsx3d.classy.util.PendingImportStore.active(this)

        // 组件点击: 显式要求落到课表 tab —— 单看 intent 里带了什么, 不猜。
        if (intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_SCHEDULE) {
            openScheduleOnceState.value = true
            intent.removeExtra(EXTRA_OPEN_TAB)
        }
        val importText = intent?.getStringExtra(
            com.imsx3d.classy.ui.screen.imports.ImportReceiverActivity.EXTRA_IMPORT_TEXT
        ) ?: com.imsx3d.classy.MainActivity.incomingImportText
        if (!importText.isNullOrBlank()) {
            com.imsx3d.classy.MainActivity.pendingImportText = importText
            com.imsx3d.classy.MainActivity.incomingImportText = null
            intent?.removeExtra(com.imsx3d.classy.ui.screen.imports.ImportReceiverActivity.EXTRA_IMPORT_TEXT)
        }
        val courseId = intent?.getLongExtra(EXTRA_COURSE_ID, -1L) ?: -1L
        if (courseId <= 0) return
        if (editingCourseFromIntent.value?.id == courseId) return
        lifecycleScope.launch {
            try {
                val course = (application as SleepyApp).repository.getCourse(courseId)
                editingCourseFromIntent.value = course
            } catch (e: Throwable) {
                android.util.Log.e("Classy", "deep link course lookup failed", e)
            }
        }
    }
}

internal enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Schedule(R.string.tab_schedule, Icons.Outlined.CalendarMonth),
    Today(R.string.tab_today, Icons.Outlined.Today),
    Manage(R.string.tab_manage, Icons.Outlined.Settings),
    Mine(R.string.tab_mine, Icons.Outlined.Person)
}

@Composable
private fun AppRoot(
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {},
    deepLinkCourse: CourseEntity? = null,
    onDeepLinkConsumed: () -> Unit = {},
    pendingImportText: String? = null,
    consumePendingImportText: () -> Unit = {}
) {
    // issue#45: 自研 Overlay 栈 → Navigation Compose。
    // AppRoot 只持「不属于任何路由的会话态」: 当前 tab / 底栏形态 / 课表视图模式。
    // 导航栈、页面状态保存、返回手势全部交给 NavHost(见 SleepyNavHost.kt)。
    var currentTab by rememberSaveable { mutableStateOf(Tab.Schedule) }
    val context = LocalContext.current
    // 课表视图模式(周视图/网格) — 会话级,与 currentTab 同级持有:
    // overlay 与 tab 切换都会整页移除 ScheduleScreen,状态必须提升到这层才存活。
    var scheduleViewMode by remember {
        mutableStateOf(
            if (AppPrefs.getStartView(context) == "cards") ViewMode.Cards else ViewMode.Full
        )
    }
    var navDock by remember { mutableStateOf(AppPrefs.isNavDock(context)) }
    val mainScope = rememberCoroutineScope()
    val mainVm: ScheduleViewModel = viewModel()
    // composition 内读 StateFlow.value 会被 lint(StateFlowValueCalledInComposition)拦:
    // 快照值不随 flow 更新重组。改订阅, holiday 设置页拿到的 tableId 恒为当前值。
    val mainState by mainVm.state.collectAsState()
    val navigator = rememberSleepyNavigator()
    val nav = navigator.backStack
    // 底栏 thumb 状态提升到 NavDisplay 之外: entry<Main> 在 push 子页时会被销毁,
    // pop 返回时高亮若随 entry 重建,首帧会闪现在课表 tab 再挪回目标 tab
    // (2026-09-21 用户报障)。放这层后 pop 重建首帧即正确。
    val pillBarState = remember { PillBarState() }

    // 外部导入文本 → 切管理页(与旧实现等价,语义不变)。
    var autoImportTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(pendingImportText) {
        if (pendingImportText != null) {
            autoImportTriggered = true
            currentTab = Tab.Manage
        }
    }

    // 组件点击 → 直达课表:桌面点击时 App 常已在后台(singleTask → onNewIntent 复用
    // 实例),currentTab 还是上次停留的页(常见「我的」),所以这里显式改回来。
    //   · 已压着未保存表单页时不弹栈 —— 保住用户填了一半的内容(见 holdsUnsavedInput)
    //   · 外部导入文本(分享课表进 App)优先级更高,那条路径要的是管理页
    val openScheduleOnce = MainActivity.openScheduleOnceState.value
    LaunchedEffect(openScheduleOnce, pendingImportText) {
        if (!openScheduleOnce) return@LaunchedEffect
        MainActivity.openScheduleOnceState.value = false
        if (pendingImportText != null) return@LaunchedEffect
        currentTab = Tab.Schedule
        if (nav.none { it.holdsUnsavedInput() }) navigator.popToMain()
    }

    SleepyNavHost(
        nav = nav,
        navigator = navigator,
        currentTab = currentTab,
        setCurrentTab = { currentTab = it },
        navDock = navDock,
        onNavDockChange = { navDock = it },
        scheduleViewMode = scheduleViewMode,
        onScheduleViewModeChange = { scheduleViewMode = it },
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        deepLinkCourse = deepLinkCourse,
        onDeepLinkConsumed = onDeepLinkConsumed,
        mainVm = mainVm,
        currentTableId = mainState.currentTable?.id,
        mainScope = mainScope,
        onCreateNewTable = {
            mainScope.launch {
                val previousId = mainVm.state.value.currentTable?.id ?: NavSession.NO_ID
                val newId = mainVm.createEmptyTable(commitSelection = false)
                navigator.openEditTable(tableId = newId, pendingNew = newId, prevDefault = previousId)
            }
        },
        pillBarState = pillBarState,
    )
}

@Composable
internal fun MainTabs(
    currentTab: Tab,
    setCurrentTab: (Tab) -> Unit,
    navigator: SleepyNavigator,
    mainVm: ScheduleViewModel,
    mainScope: CoroutineScope,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onCreateNewTable: () -> Unit,
    holder: SaveableStateHolder,
    updateNoticeVisible: Boolean = false
) {
    // tab 往返滚动位置保真: when 条件组合同样整页移除被切走的 tab, 各 tab 内容包
    // SaveableStateProvider(currentTab.name) — key 稳定(tab 枚举名), 返回时恢复。
    // 注意: scheduleViewMode 会话态仍由 AppRoot 持有(§1.4 契约), 此处只管组合作用域。
    val session = navigator.session
    val draftScope = rememberCoroutineScope()
    // 课程色槽位表：按**整张表**的课程分配一次（同一张表内互不撞色、跨周稳定、三个视图同色）。
    // 计算放在这里 —— 三个视图（网格 / 周视图 / 今日）都是它的子树，一趟提供给全部叶子。
    val tabsState by mainVm.state.collectAsState()
    val courseColorSlots = androidx.compose.runtime.remember(tabsState.courses) {
        com.imsx3d.classy.util.CourseColorUtil.slotsFor(tabsState.courses)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        com.imsx3d.classy.util.LocalCourseColorSlots provides courseColorSlots
    ) {
    when (currentTab) {
        Tab.Schedule -> holder.SaveableStateProvider(currentTab.name) {
            ScheduleScreen(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onGoImport = { MainActivity.autoShowImportOnceState.value = true; setCurrentTab(Tab.Manage) },
                onManualAdd = { navigator.openAddCourse() },
                onQuickAdd = { navigator.openAddCourse(prefill = it) },
                onCreateTable = onCreateNewTable,
                onEditCourse = { course -> session.beginEditCourse(course); navigator.openAddCourse(course.id, editing = true) })
        }
        Tab.Today -> holder.SaveableStateProvider(currentTab.name) {
            TodayScreen(onEditCourse = { course -> session.beginEditCourse(course); navigator.openAddCourse(course.id, editing = true) })
        }
        Tab.Manage -> holder.SaveableStateProvider(currentTab.name) {
            val ctx = LocalContext.current
            val importCoursesLabel = stringResource(com.imsx3d.classy.R.string.import_courses)
            // 空态导入引导: autoShowImportOnce 置位过 → 本次进管理页自动弹 ImportSheet, 随即消费清零。
            // pendingImportText != null 是另一路 (外部 app 分享课表文本进来) 的既有自动弹层, 语义不同并存。
            val autoOnce = MainActivity.autoShowImportOnceState.value
            if (autoOnce) MainActivity.autoShowImportOnceState.value = false
            val draftEntities by SleepyApp.get().importDraftRepository.observeAll().collectAsState(initial = emptyList())
            val drafts = draftEntities.mapNotNull { entity ->
                val snapshot = JwImportDraftCodec.fromJson(entity.payloadJson) ?: return@mapNotNull null
                ImportDraft(
                    id = entity.id,
                    name = snapshot.tableName.ifBlank { snapshot.school.name },
                    details = "${snapshot.courses.size} $importCoursesLabel",
                )
            }
            ManagementPage(autoShowImportSheet = autoOnce || MainActivity.pendingImportText != null || MainActivity.pendingImportToken != null, onJwImportRequested = { ctx.startActivity(Intent(ctx, com.imsx3d.classy.ui.screen.imports.JwImportActivity::class.java)) }, onCreateNewTableRequested = onCreateNewTable,
                onEditCurrentTable = { navigator.openEditTable() }, onExportRequested = { navigator.openExport() },
                onOpenAllTables = { navigator.openAllTables() },
                onOpenPeriodTables = { navigator.openPeriodTables() },
                onOpenCourseList = { navigator.openCourseList() },
                drafts = drafts,
                onRestoreDraft = { id ->
                    ctx.startActivity(Intent(ctx, JwImportActivity::class.java).putExtra(JwImportActivity.EXTRA_DRAFT_ID, id))
                },
                onDeleteDraft = { id ->
                    draftScope.launch { SleepyApp.get().importDraftRepository.delete(id) }
                },
                // v7.10.16w 用户 2026-09-10: 导入完成留在管理页 — 此前硬跳课表页(周/网格),
                // 打断"复制副本→追加导入→继续操作"的管理动线。当前课表摘要卡就地刷新可见。
                onImported = { /* 留在管理页, 摘要卡就地刷新 */ })
        }
        Tab.Mine -> holder.SaveableStateProvider(currentTab.name) {
            MineScreen(
                onOpenManagement = { setCurrentTab(Tab.Manage) },
                onOpenSettings = { navigator.openSettings() },
                onOpenAppearance = { navigator.openAppearance() },
                onOpenWidgets = { navigator.openWidgetManagement() },
                onOpenReminder = { navigator.openReminder() },
                onOpenAbout = { navigator.openAbout() },
                updateNoticeVisible = updateNoticeVisible)
        }
    }
    }
}
