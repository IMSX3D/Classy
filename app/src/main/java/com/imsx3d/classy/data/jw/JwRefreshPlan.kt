package com.imsx3d.classy.data.jw

import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import org.json.JSONArray
import org.json.JSONObject

data class JwBaselineRow(val key: String, val localId: Long, val fingerprint: String)
data class JwSource(val school: JwSchoolInfo, val rows: List<JwBaselineRow>)
data class JwRefreshRemoval(val course: CourseEntity, val safe: Boolean)
data class JwRefreshAddition(val course: CourseEntity, val key: String, val defaultSelected: Boolean)
data class JwRefreshPlan(
    val table: TimeTableEntity,
    val effectiveTable: TimeTableEntity,
    val before: List<CourseEntity>,
    val school: JwSchoolInfo,
    val incoming: List<CourseEntity>,
    val additions: List<JwRefreshAddition>,
    val removals: List<JwRefreshRemoval>,
    val retained: Map<String, Long>,
    val firstLink: Boolean
)

object JwRefresh {
    // Matching includes the complete academic arrangement. Changed arrangements are shown
    // as a removal plus addition instead of guessing identity from a non-unique course name.
    fun key(c: CourseEntity): String = JSONArray(listOf(c.courseName, c.teacher, c.room,
        c.day, c.startNode, c.step, c.startWeek, c.endWeek, c.type)).toString()
    fun fingerprint(c: CourseEntity): String = c.copy(id = 0, tableId = 0, groupId = "").toString()

    fun decode(raw: String): JwSource? = runCatching {
        if (raw.isBlank()) return null
        val root = JSONObject(raw)
        require(root.getInt("version") == 1)
        val draft = requireNotNull(JwImportDraftCodec.fromJson(root.getString("school")))
        val rows = root.getJSONArray("rows")
        JwSource(draft.school, (0 until rows.length()).map {
            val row = rows.getJSONObject(it)
            JwBaselineRow(row.getString("key"), row.getLong("id"), row.getString("fingerprint"))
        })
    }.getOrNull()

    fun encode(school: JwSchoolInfo, incoming: List<CourseEntity>, links: Map<String, Long>, locals: List<CourseEntity>, previous: JwSource? = null): String {
        val byId = locals.associateBy { it.id }
        return JSONObject().apply {
            put("version", 1)
            put("school", JwImportDraftCodec.toJson(JwImportDraftSnapshot(school, emptyList(), emptyList())))
            put("rows", JSONArray().apply {
                incoming.distinctBy(::key).forEach { c ->
                    val id = links[key(c)] ?: 0L
                    put(JSONObject().put("key", key(c)).put("id", id)
                        .put("fingerprint", previous?.rows?.firstOrNull { it.key == key(c) && it.localId == id }?.fingerprint
                            ?: byId[id]?.let(::fingerprint).orEmpty()))
                }
            })
        }.toString()
    }

    fun plan(table: TimeTableEntity, effective: TimeTableEntity, locals: List<CourseEntity>,
             incoming: List<CourseEntity>, school: JwSchoolInfo): JwRefreshPlan {
        require(incoming.isNotEmpty()) { "empty" }
        require(incoming.all { it.startNode >= 1 && it.step >= 1 &&
            it.startNode + it.step - 1 <= effective.nodesPerDay && it.day in 1..7 &&
            it.startWeek >= 1 && it.endWeek >= it.startWeek && it.endWeek <= table.maxWeek }) { "range" }
        val source = decode(table.jwSourceJson)
        require(table.jwSourceJson.isBlank() || source != null) { "source" }
        require(source == null || source.school.name == school.name && source.school.type == school.type) { "school" }
        val baseline = source?.rows.orEmpty().associateBy { it.key }
        val remote = incoming.distinctBy(::key)
        val localById = locals.associateBy { it.id }
        val claimed = mutableSetOf<Long>()
        val retained = mutableMapOf<String, Long>()
        val additions = mutableListOf<JwRefreshAddition>()
        remote.forEach { c ->
            val k = key(c)
            val previous = baseline[k]
            val linked = previous?.localId?.let(localById::get)
            val exact = linked ?: locals.firstOrNull { it.id !in claimed && key(it) == k && !it.ownTime && !it.isIrregularTime }
            if (exact != null) {
                claimed += exact.id
                retained[k] = exact.id
            } else additions += JwRefreshAddition(c, k, previous == null)
        }
        val remoteKeys = remote.map(::key).toSet()
        val removals = if (source == null) {
            locals.filter { it.id !in claimed }.map { JwRefreshRemoval(it, false) }
        } else source.rows.filter { it.key !in remoteKeys }.mapNotNull { old ->
            localById[old.localId]?.takeIf { it.id !in claimed }?.let {
                JwRefreshRemoval(it, fingerprint(it) == old.fingerprint)
            }
        }.distinctBy { it.course.id }
        return JwRefreshPlan(table, effective, locals, school, remote, additions, removals, retained, source == null)
    }
}
