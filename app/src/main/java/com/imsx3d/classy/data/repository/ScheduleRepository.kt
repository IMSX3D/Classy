package com.imsx3d.classy.data.repository

import com.imsx3d.classy.data.AppDatabase
import com.imsx3d.classy.data.diff.DiffResult
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.data.undo.UndoManager
import com.imsx3d.classy.data.undo.UndoSnapshot
import com.imsx3d.classy.SleepyApp
import androidx.room.withTransaction
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.ConflictLayoutEngine
import com.imsx3d.classy.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 课表仓库 — 业务数据访问的唯一入口。
 *
 * UI 层只调这个类，不直接碰 DAO。
 */
class ScheduleRepository(private val db: AppDatabase) {

    private val courseDao = db.courseDao()
    private val tableDao = db.timeTableDao()
    private val periodTableDao = db.periodTableDao()

    // ========== v7.10.16 单级撤回 ==========

    val canUndo: Boolean get() = UndoManager.hasSnapshot
    val canRedo: Boolean get() = UndoManager.hasRedoSnapshot

    suspend fun <T> readConsistently(block: suspend () -> T): T = db.withTransaction { block() }

    private val writeMutex = Mutex()
    private class EditContext(val repository: ScheduleRepository) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<EditContext>
    }

    /** One user action: serialize, snapshot, commit all writes, then refresh once.
     * Nested repository writes join this coroutine's transaction, never another action's.
     * A failed/cancelled transaction leaves both the database and undo slot untouched.
     */
    suspend fun <T> atomicEdit(recordUndo: Boolean = true, block: suspend () -> T): T {
        if (coroutineContext[EditContext]?.repository === this) return block()
        return writeMutex.withLock {
            val before = snapshotCurrent()
            val result = db.withTransaction {
                withContext(EditContext(this@ScheduleRepository)) { block() }
            }
            // The database has committed: finish bookkeeping even if the caller leaves the screen.
            withContext(NonCancellable) {
                val after = snapshotCurrent()
                if (before != after) {
                    if (recordUndo) UndoManager.capture(
                        tables = before.tables, courses = before.courses,
                        defaultTableId = before.defaultTableId, periodTables = before.periodTables,
                        holidayTransfers = before.holidayTransfers
                    )
                    (before.tables.map { it.id } - after.tables.map { it.id }.toSet()).forEach {
                        AppPrefs.clearHolidayTransfers(SleepyApp.get(), it)
                    }
                    onDataChanged()
                }
            }
            result
        }
    }

    /** 取一份当前库态 — 内部用: 给 undo / redo 的反向槽用。 */
    private suspend fun snapshotCurrent(): UndoSnapshot = UndoSnapshot(
        periodTables = periodTableDao.getAll(),
        tables = tableDao.getAll(),
        courses = courseDao.getAll(),
        defaultTableId = tableDao.getDefault()?.id,
        holidayTransfers = tableDao.getAll().associate { it.id to AppPrefs.getHolidayTransfers(SleepyApp.get(), it.id) }
    )

    private suspend fun applySnapshot(snap: UndoSnapshot) {
        db.withTransaction {
            courseDao.deleteAll()
            tableDao.deleteAll()
            // issue#40: 恢复顺序 period_tables → time_tables → courses。
            // time_tables.periodTableId 指向 period_tables.id — 先插 periodTables
            // 保证引用目标先存在; 先课程后课表会触发外键约束闪退。
            periodTableDao.deleteAll()
            periodTableDao.insertAll(snap.periodTables)
            tableDao.insertAll(snap.tables)
            courseDao.insertAll(snap.courses)
            snap.defaultTableId?.let { tableDao.setDefault(it) }
        }
    }

    /** 撤回最近一次改动: 事务内清三表→按外键顺序重插快照→恢复 default → 刷 widget/通知。false = 无可撤回 */
    suspend fun restoreLastSnapshot(): Boolean = writeMutex.withLock {
        val undoSnap = UndoManager.peek() ?: return@withLock false
        val redoSnap = snapshotCurrent()
        applySnapshot(undoSnap)
        withContext(NonCancellable) {
            restoreTransfers(undoSnap, redoSnap)
            UndoManager.poll()
            UndoManager.recordRedo(redoSnap)
            onDataChanged()
        }
        true
    }

    suspend fun redoLastUndo(): Boolean = writeMutex.withLock {
        val redoSnap = UndoManager.peekRedo() ?: return@withLock false
        val undoSnap = snapshotCurrent()
        applySnapshot(redoSnap)
        withContext(NonCancellable) {
            restoreTransfers(redoSnap, undoSnap)
            UndoManager.pollRedo()
            UndoManager.reinsertForRedoSymmetry(undoSnap)
            onDataChanged()
        }
        true
    }

    private fun restoreTransfers(target: UndoSnapshot, previous: UndoSnapshot) {
        val ctx = SleepyApp.get()
        (previous.tables.map { it.id } - target.tables.map { it.id }.toSet()).forEach {
            AppPrefs.clearHolidayTransfers(ctx, it)
        }
        target.tables.forEach { table ->
            AppPrefs.setHolidayTransfers(ctx, table.id, target.holidayTransfers[table.id].orEmpty())
        }
    }

    // ========== TimeTable ==========

    fun observeAllTables(): Flow<List<TimeTableEntity>> = tableDao.observeAll()

    fun observeTable(id: Long): Flow<TimeTableEntity?> = tableDao.observeById(id)

    suspend fun getAllTables(): List<TimeTableEntity> = tableDao.getAll()

    suspend fun getTable(id: Long): TimeTableEntity? = tableDao.getById(id)

    suspend fun getDefaultTable(): TimeTableEntity? = tableDao.getDefault()

    suspend fun insertTable(table: TimeTableEntity): Long = atomicEdit() {
        val id = tableDao.insert(table)
        if (table.isDefault || tableDao.count() == 1) {
            tableDao.setDefault(id)
        }
        return@atomicEdit id
    }

    suspend fun updateTable(table: TimeTableEntity): Unit = atomicEdit() {
        tableDao.update(table)
        onDataChanged()
    }

    /**
     * issue#28 P3: 编辑课表保存 — timeJson 变更时课程按绝对时间自适应新节次。
     *
     * 节次编号语义 = "该节在新表上的钟点", 表变了编号必须跟着变, 否则改完 16→12 节
     * 课程还停在 13-16 节。ownTime 课自带绝对时间(timeToNode 直接定位), 普通课按
     * remapCourseNodes 重排; 无法映射的课保持原节次。与 updateTable 同为单动作
     * 撤回单元(首快照 = 改表前)。
     */
    suspend fun updateTableRemappingCourses(table: TimeTableEntity): Unit = atomicEdit() {
        val oldJson = tableDao.getById(table.id)?.timeJson.orEmpty()
        tableDao.update(table)
        if (table.timeJson != oldJson) {
            val courses = courseDao.getByTable(table.id)
            val remapped = courses.map { c ->
                if (c.ownTime) {
                    val mapped = com.imsx3d.classy.util.TimeTableUtils.timeToNode(
                        c.startTime, c.endTime, table.timeJson
                    )
                    if (mapped != null) c.copy(startNode = mapped.first, step = mapped.second) else c
                } else {
                    val (node, step) = com.imsx3d.classy.util.TimeTableUtils.remapCourseNodes(
                        c.startNode, c.step, oldJson, table.timeJson
                    )
                    c.copy(startNode = node, step = step)
                }
            }
            val changed = remapped.filterIndexed { i, c -> c != courses[i] }
            if (changed.isNotEmpty()) courseDao.updateAll(changed)
        }
        onDataChanged()
    }

    suspend fun deleteTable(id: Long): Unit = atomicEdit() {
        tableDao.deleteById(id)
        onDataChanged()
    }

    suspend fun setDefault(id: Long): Unit = atomicEdit(recordUndo = false) {
        // v7.10.16i 不捕获快照: 切表(选择哪个表是当前表)是导航动作,不是课表数据改动 —
        // 捕获会让撤回键亮起、点了把用户切回原表(用户 2026-09-03「撤回键不是返回键」)。
        // 导入建新表路径的快照由同批内的 insertTable/insertCourses 捕获, 不受影响。
        tableDao.setDefault(id)
        onDataChanged()
    }

    // ========== PeriodTable (issue#40 独立时间节次表) ==========

    fun observeAllPeriodTables(): Flow<List<com.imsx3d.classy.data.entity.PeriodTableEntity>> =
        periodTableDao.observeAll()

    suspend fun getAllPeriodTables(): List<com.imsx3d.classy.data.entity.PeriodTableEntity> =
        periodTableDao.getAll()

    suspend fun getPeriodTable(id: Long): com.imsx3d.classy.data.entity.PeriodTableEntity? =
        periodTableDao.getById(id)

    /** 某时间节次表被多少张课程表绑定 — 管理页"已绑定 N 张课表"与删除守卫共用 */
    suspend fun periodTableBoundCount(id: Long): Int = periodTableDao.boundTableCount(id)

    /**
     * 有效时间节次表解析(设计 §5.1): 绑定表存在 → 返回它;
     * 绑定指向已删除的 id(悬空引用, 仅可能来自旧数据/导入)或未绑定 → null, 调用方回退旧兼容列。
     */
    suspend fun effectivePeriodTable(tableId: Long): com.imsx3d.classy.data.entity.PeriodTableEntity? {
        val table = tableDao.getById(tableId) ?: return null
        val boundId = table.periodTableId ?: return null
        return periodTableDao.getById(boundId)
    }

    /**
     * 绑定时间节次表的观察流 — 绑定切换/时间表内容修改都会 emit 新值,
     * 上游(如 ScheduleViewModel)合并此流实现"立即全部同步"(设计 §5.2)。
     */
    fun observeEffectivePeriodTable(tableId: Long): Flow<com.imsx3d.classy.data.entity.PeriodTableEntity?> =
        tableDao.observeById(tableId)
            .flatMapLatest { table ->
                val boundId: Long? = table?.periodTableId
                if (boundId == null) {
                    kotlinx.coroutines.flow.flowOf<com.imsx3d.classy.data.entity.PeriodTableEntity?>(null)
                } else {
                    periodTableDao.observeById(boundId)
                }
            }

    /** 新建时间节次表, 返回新 id。独立写动作 = 独立撤回单元。 */
    suspend fun insertPeriodTable(table: com.imsx3d.classy.data.entity.PeriodTableEntity): Long = atomicEdit() {
        val now = System.currentTimeMillis()
        val withStamp = if (table.createdAt == 0L) table.copy(createdAt = now, updatedAt = now) else table
        return@atomicEdit periodTableDao.insert(withStamp)
    }

    /**
     * 复制时间节次表(设计 §4.2): 生成新实体, 原表与原绑定关系零改动。返回新副本 id。
     */
    suspend fun copyPeriodTable(sourceId: Long): Long = atomicEdit() {
        val src = periodTableDao.getById(sourceId) ?: return@atomicEdit -1L
        val copy = src.copy(
            id = 0,
            name = src.name,   // 名称原样保留, UI 层决定是否加"副本"后缀
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return@atomicEdit periodTableDao.insert(copy)
    }

    /**
     * v1.0.56 T8: 按指定名建副本 — 复制弹窗确认后调用, 名字已由 UI 层查重。
     * 返回新副本 id, 源不存在返回 -1。
     */
    suspend fun copyPeriodTableAs(sourceId: Long, newName: String): Long = atomicEdit() {
        val src = periodTableDao.getById(sourceId) ?: return@atomicEdit -1L
        val copy = src.copy(
            id = 0,
            name = newName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return@atomicEdit periodTableDao.insert(copy)
    }

    /**
     * 绑定/解绑独立作息表: 绑定是单向指针, 不改本课表的兼容列。
     *
     * 首次绑定(null → 非 null)时把本课表原有的兼容列存入 preBindSnapshotJson;
     * 换绑(非 null → 非 null)不刷新快照; 解绑时恢复首次绑定前的兼容列并清空快照。
     * 这样绑定期间实时读取目标表, 解绑后仍回到用户自己的原始作息, 不发生数据覆盖。
     * 悬空目标拒绝, 无效动作不拍撤回快照。
     */
    suspend fun bindPeriodTable(timeTableId: Long, periodTableId: Long?): Unit = atomicEdit() {
        val table = tableDao.getById(timeTableId) ?: return@atomicEdit
        if (periodTableId != null && periodTableDao.getById(periodTableId) == null) return@atomicEdit
        if (table.periodTableId == periodTableId) return@atomicEdit
        db.withTransaction {
            val next = when {
                table.periodTableId == null && periodTableId != null ->
                    TimeTableEntity.snapshotForBind(table, periodTableId)
                table.periodTableId != null && periodTableId == null ->
                    TimeTableEntity.restoredForUnbind(table)
                else -> table.copy(periodTableId = periodTableId)
            }
            tableDao.update(next)
        }
        onDataChanged()
    }

    /**
     * 修改时间节次表内容(设计 §5.2): 更新实体+updatedAt。
     * 所有绑定课表经 effectivePeriodTable 立即读到新作息, 课程行不重算。
     */
    suspend fun updatePeriodTable(table: com.imsx3d.classy.data.entity.PeriodTableEntity): Unit = atomicEdit() {
        periodTableDao.update(table.copy(updatedAt = System.currentTimeMillis()))
        onDataChanged()
    }

    /**
     * 保存独立作息表内容。绑定课表通过 hydratedWith 实时读取新值，
     * 因此这里只更新 period_tables，不再污染任何课表兼容列。
     * 返回当前绑定课表数，供 UI 预览文案使用。
     */
    suspend fun savePeriodTable(
        table: com.imsx3d.classy.data.entity.PeriodTableEntity
    ): Int = atomicEdit() {
        if (periodTableDao.getById(table.id) == null) return@atomicEdit 0
        val boundCount = periodTableDao.boundTableCount(table.id)
        periodTableDao.update(table.copy(updatedAt = System.currentTimeMillis()))
        onDataChanged()
        return@atomicEdit boundCount
    }

    /**
     * 删除守卫(设计 §7): 被引用的时间节次表禁止删除(返回 false), 无绑定才真删。
     * 禁止产生悬空引用。
     */
    suspend fun deletePeriodTable(id: Long): Boolean = atomicEdit() {
        // 删除守卫先行 — 被引用时不动库也不拍快照, 禁止产生悬空引用
        if (periodTableDao.boundTableCount(id) > 0) return@atomicEdit false
        periodTableDao.deleteById(id)
        onDataChanged()
        return@atomicEdit true
    }

    suspend fun tableCount(): Int = tableDao.count()

    suspend fun saveCourseDrafts(
        selectedTableId: Long?,
        drafts: List<CourseEntity>,
        editingGroupId: String?,
        newTable: TimeTableEntity,
        updateTimeJson: (String) -> String
    ): Long = atomicEdit {
        val tableId = selectedTableId ?: insertTable(newTable)
        val table = requireNotNull(getTable(tableId)) { "课表已不存在，请返回重新选择" }
        val groupId = editingGroupId ?: java.util.UUID.randomUUID().toString()
        val fixed = drafts.map { it.copy(tableId = tableId, groupId = groupId) }
        // Build slot changes from the pre-edit table; diff may reclaim unused nodes.
        val updatedJson = updateTimeJson(table.timeJson)
        if (editingGroupId != null) {
            val existing = getCourses(tableId).filter { it.groupId == editingGroupId }
            applyDiff(tableId, com.imsx3d.classy.data.diff.RowKeyDiffer.diff(fixed, existing))
        } else {
            insertCourses(fixed)
        }
        if (updatedJson != table.timeJson) updateTable(table.copy(timeJson = updatedJson))
        tableId
    }

    // ========== Course ==========

    fun observeCourses(tableId: Long): Flow<List<CourseEntity>> =
        courseDao.observeByTable(tableId)

    fun observeCoursesByDay(tableId: Long, day: Int): Flow<List<CourseEntity>> =
        courseDao.observeByTableAndDay(tableId, day)

    suspend fun getCoursesByDayOnce(tableId: Long, day: Int): List<CourseEntity> =
        courseDao.getByTableAndDayOnce(tableId, day)

    suspend fun getCourses(tableId: Long): List<CourseEntity> = courseDao.getByTable(tableId)

    /** issue#40: 全库课程 — 时间节次表保存预览须统计【所有】绑定表的课, 不只当前选中表 */
    suspend fun getAllCourses(): List<CourseEntity> = courseDao.getAll()

    suspend fun getCourse(id: Long): CourseEntity? = courseDao.getById(id)

    suspend fun insertCourse(course: CourseEntity): Long = atomicEdit() {
        val id = courseDao.insert(course)
        onDataChanged()
        return@atomicEdit id
    }

    suspend fun insertCourses(courses: List<CourseEntity>): List<Long> = atomicEdit() {
        // 导入时以规范化课程名为身份；时间、教师、教室只属于课程的一个时段。
        val withGroupIds = assignGroupIds(courses)
        val ids = courseDao.insertAll(withGroupIds)
        onDataChanged()
        return@atomicEdit ids
    }

    /**
     * sleepy-v1 (§3.4 契约一): groupId 已由解析端权威生成(按文档内 token 分区),
     * 落库绕过 assignGroupIds — 否则同名不同 token 的分区会被静默合并, 分区往返被破坏。
     */
    suspend fun insertCoursesKeepingGroups(courses: List<CourseEntity>): List<Long> = atomicEdit() {
        val ids = courseDao.insertAll(courses)
        onDataChanged()
        return@atomicEdit ids
    }

    /** 覆盖式导入(保留解析端 groupId), 配合 insertCoursesKeepingGroups 的 sleepy-v1 路径 */
    suspend fun replaceCoursesKeepingGroups(tableId: Long, courses: List<CourseEntity>): Unit = atomicEdit() {
        courseDao.replaceAll(tableId, courses)
        onDataChanged()
        pruneDefaultTopPrefs()
    }

    suspend fun updateCourse(course: CourseEntity): Unit = atomicEdit() {
        courseDao.update(course)
        onDataChanged()
    }

    /** Recheck the source and conflicts inside the same transaction as the split. */
    suspend fun moveCourseMeeting(
        expected: CourseEntity,
        sourceWeek: Int,
        targetWeek: Int,
        target: com.imsx3d.classy.util.GridSelection,
        scope: com.imsx3d.classy.util.CourseMoveScope,
        expectedTable: TimeTableEntity,
        expectedTransfers: List<com.imsx3d.classy.util.HolidayTransferEntry>,
        allowConflicts: Boolean = false
    ): Unit = atomicEdit {
        val current = requireNotNull(courseDao.getById(expected.id)) { "课程已删除，请返回课表" }
        require(current == expected) { "课程已发生变化，请重新拖动" }
        val table = requireNotNull(tableDao.getById(current.tableId)) { "课表已删除" }
        val hydrated = table.hydratedWith(table.periodTableId?.let { periodTableDao.getById(it) })
        require(hydrated.timeJson == expectedTable.timeJson && table.startDate == expectedTable.startDate &&
            table.maxWeek == expectedTable.maxWeek &&
            AppPrefs.getHolidayTransfers(SleepyApp.get(), table.id) == expectedTransfers) {
            "课表时间设置已变化，请重新拖动"
        }
        val plan = com.imsx3d.classy.util.CourseMovePlanner.details(current, sourceWeek, targetWeek,
            target, scope, table.maxWeek, hydrated.timeJson)
        val planned = plan.rows
        if (targetWeek == sourceWeek && plan.moved.all {
                it.day == current.day && it.startNode == current.startNode && it.step == current.step &&
                    it.startTime == current.startTime && it.endTime == current.endTime
            }) return@atomicEdit
        val stored = courseDao.getByTable(table.id).filter { it.id != current.id } + plan.retained
        val conflicts = com.imsx3d.classy.util.ConflictDetailReporter.draftConflictDetails(plan.moved, stored,
            arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"), hydrated.timeJson)
        if (conflicts.isNotEmpty() && !allowConflicts) throw CourseMoveConflictException(
            conflicts.map { "${it.dayText} ${it.nodeRangeText} 节 · ${it.weekText} · ${it.existingName}" }.distinct())
        courseDao.update(planned.first())
        if (planned.size > 1) courseDao.insertAll(planned.drop(1))
    }

    /** 查同 groupId 下所有课程（用于编辑回填，按时段分 block） */
    suspend fun getGroupCourses(tableId: Long, groupId: String): List<CourseEntity> =
        courseDao.getByGroupId(tableId, groupId)

    /** 编辑课程组：原子地删除同 groupId 全部记录并插入新草稿（DAO 层 @Transaction）。
     *  防呆: groupId 空串(早期版本导入的存量数据)禁止走组替换 — 否则 DELETE WHERE groupId=''
     *  会把该表全部空组课程一起删掉。空组时退化为逐条插入。 */
    suspend fun updateCourseGroup(tableId: Long, groupId: String, newCourses: List<CourseEntity>): Unit = atomicEdit() {
        if (groupId.isBlank()) {
            courseDao.insertAll(newCourses)
            onDataChanged()
            return@atomicEdit
        }
        courseDao.replaceGroup(tableId, groupId, newCourses)
        onDataChanged()
    }

    suspend fun deleteCourse(id: Long): Unit = atomicEdit() {
        val course = courseDao.getById(id) ?: return@atomicEdit
        courseDao.deleteById(id)
        reclaimUnusedEdgeNodes(course.tableId)
        onDataChanged()
        pruneDefaultTopPrefs()
    }

    /** 删除同 groupId 全部记录。防呆: 空 groupId 拒删(否则整表空组课程全没了) */
    suspend fun deleteCourseGroup(tableId: Long, groupId: String): Unit = atomicEdit() {
        if (groupId.isBlank()) return@atomicEdit
        courseDao.deleteByGroupId(tableId, groupId)
        reclaimUnusedEdgeNodes(tableId)
        onDataChanged()
        pruneDefaultTopPrefs()
    }

    /**
     * issue#23 修: 删课/删组后扫描该表 timeJson, 回收所有无人引用的边缘节次节点.
     * 直接走 tableDao.update 绕过 updateTable 的 captureForUndo — 撤回时上层
     * (deleteCourse 等)已捕获了"删前完整快照(含完整 timeJson)", 撤回 = 回到删前,
     * 课程和节点都复原, 此处不应再叠加中间快照.
     */
    private suspend fun reclaimUnusedEdgeNodes(tableId: Long) {
        val table = tableDao.getById(tableId) ?: return
        val remaining = courseDao.getByTable(tableId)
        val used = mutableSetOf<Int>()
        remaining.forEach { c ->
            val effStart = if (c.ownTime) {
                com.imsx3d.classy.util.TimeTableUtils.timeToNode(
                    c.startTime, c.endTime, table.timeJson
                )?.first ?: c.startNode
            } else c.startNode
            val end = (effStart + c.step - 1).coerceAtLeast(effStart)
            for (n in effStart..end) used.add(n)
            // 防御: 存储的 startNode 也算"用户意图", 即便 normalize 后位置不同
            // 也保留节点 — 防止误回收导致课崩.
            val storedEnd = c.startNode + c.step - 1
            for (n in c.startNode..storedEnd) used.add(n)
        }
        val reclaimed = com.imsx3d.classy.util.TimeTableUtils.reclaimUnusedEdgeNodes(
            table.timeJson, used
        )
        if (reclaimed != table.timeJson) {
            tableDao.update(table.copy(timeJson = reclaimed))
        }
    }

    /**
     * 行级 diff/patch 落库(替代 [updateCourseGroup] 整组覆盖, issue#22 同名多地点修复):
     *   - toDelete 行按 id 批量删除
     *   - toUpdate 行按 id 批量覆盖(保留 RowKey)
     *   - toInsert 行批量新增(id=0 让 Room 自增)
     *
     * v7.10.16v 撤回修复(用户 2026-09-10 报"编辑课程后没有撤回按钮"): 本方法自带
     * [captureForUndo] — issue#22 接手时原契约"调用前必须已 capture"无任何调用方
     * 履行, 编辑课程从此不产生快照。
     */
    suspend fun applyDiff(tableId: Long, diff: DiffResult): Unit = atomicEdit() {
        if (diff.toDelete.isNotEmpty()) courseDao.deleteByIds(diff.toDelete)
        if (diff.toUpdate.isNotEmpty()) courseDao.updateAll(diff.toUpdate)
        if (diff.toInsert.isNotEmpty()) courseDao.insertAll(diff.toInsert)
        // issue#23 修: 编辑删行也要回收(否则编辑掉 edge 上的 block 后节点残留)
        if (diff.toDelete.isNotEmpty()) reclaimUnusedEdgeNodes(tableId)
        onDataChanged()
    }

    /**
     * 改组色(issue#22 spec §6.2 "改组色"按钮的落库路径):
     * 把选中色写到同 groupId 所有 colorMode=GROUP 行的 color 字段。
     *
     * 只动 GROUP 行 — AUTO 行 hue 源来自组色源(groupSourceColorHex),组色变则
     * 自动行跟着变(spec §5.2"组色变则自动跟随"),不需要写;CUSTOM 行独立色不跟组。
     * 落所有 GROUP 行而非只落最小 id 行:groupSourceColorHex 取同组 GROUP 模式
     * 最小 id 行的 color,任一 GROUP 行带上色即成立;全组写一致值还能避免
     * RowKeyDiffer 把"只改了最小 id 行"之外的 GROUP 行 diff 出假更新。
     */
    suspend fun setGroupSourceColor(tableId: Long, groupId: String, hex: String): Unit = atomicEdit() {
        if (groupId.isBlank()) return@atomicEdit
        val group = courseDao.getByGroupId(tableId, groupId)
            .filter { it.colorMode == com.imsx3d.classy.data.entity.CourseColorMode.GROUP }
        if (group.isEmpty()) return@atomicEdit
        courseDao.updateAll(group.map { it.copy(color = hex) })
        onDataChanged()
    }

    suspend fun countCourses(tableId: Long): Int = courseDao.countByTable(tableId)

    suspend fun totalCourseCount(): Int = courseDao.totalCount()

    /** 覆盖式导入（先删后插） */
    suspend fun replaceCourses(tableId: Long, courses: List<CourseEntity>): Unit = atomicEdit() {
        val withGroupIds = assignGroupIds(courses)
        courseDao.replaceAll(tableId, withGroupIds)
        onDataChanged()
        pruneDefaultTopPrefs()
    }

    /**
     * v7.10.16p: 课程集变化后清理指向已失效课程的置顶偏好 —
     * repId 已删/键已不存在(锚课被删·簇解体)的条目静默失效还会画出幽灵图层选项,
     * 这里按现存课全量校验删除。删课/删组/覆盖导入/撤销四条写路径都会走到。
     */
    private suspend fun pruneDefaultTopPrefs() {
        if (coroutineContext[EditContext]?.repository === this) return
        val ctx = SleepyApp.get()
        val stored = AppPrefs.getConflictDefaultTop(ctx)
        if (stored.isEmpty()) return
        val allCourses = courseDao.getAll()
        // 用户报障 2026-09-10: liveKeys 与网格聚簇同一时间域, 偏好不被节点域误删
        val timeJson = tableDao.getDefault()?.timeJson
        val pruned = ConflictLayoutEngine.pruneConflictDefaultTop(stored, allCourses, timeJson)
        if (pruned.size != stored.size) {
            AppPrefs.setConflictDefaultTop(ctx, pruned)
        }
    }

    /**
     * 数据变更后：刷新所有 widget，并在提醒开启时重排通知（含流体云）。
     * 修复：之前只刷 widget 不重排通知，导致编辑课表后课前提醒/流体云仍按旧时间。
     */
    private suspend fun onDataChanged() {
        if (coroutineContext[EditContext]?.repository === this) return
        val app = SleepyApp.get()
        try { pruneDefaultTopPrefs() }
        catch (e: Exception) { android.util.Log.w("ScheduleRepository", "Preference cleanup failed", e) }
        try { WidgetUpdater.notifyDataChanged(app) }
        catch (e: Exception) { android.util.Log.w("ScheduleRepository", "Widget refresh failed", e) }
        app.notificationScheduler.requestReschedule()
    }

    private fun assignGroupIds(courses: List<CourseEntity>): List<CourseEntity> {
        val nameToGroupId = mutableMapOf<String, String>()
        return courses.map { c ->
            val key = c.courseName.trim().replace(Regex("\\s+"), " ").lowercase()
            val gid = nameToGroupId.getOrPut(key) { c.groupId.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString() }
            c.copy(groupId = gid)
        }
    }
}
