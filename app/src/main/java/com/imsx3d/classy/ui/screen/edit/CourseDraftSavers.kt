package com.imsx3d.classy.ui.screen.edit

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.imsx3d.classy.util.TimeTableUtils
import org.json.JSONArray
import org.json.JSONObject

/** Configuration restoration only: partial input is never validated, normalized or saved to DB. */
internal fun <T> draftListSaver(encode: (T) -> JSONObject, decode: (JSONObject) -> T) =
    Saver<SnapshotStateList<T>, String>(
        save = { rows -> JSONArray().apply { rows.forEach { put(encode(it)) } }.toString() },
        restore = { raw ->
            val array = JSONArray(raw)
            mutableStateListOf<T>().apply { for (i in 0 until array.length()) add(decode(array.getJSONObject(i))) }
        }
    )

internal val MeetingBlocksSaver = draftListSaver<MeetingBlockDraft>(
    encode = { b -> JSONObject().apply {
        put("id", b.id)
        put("startNode", b.startNode)
        put("step", b.step)
        put("startTime", b.startTime)
        put("endTime", b.endTime)
        put("isIrregularNode", b.isIrregularNode)
        put("selectedEdgeNode", b.selectedEdgeNode)
        put("isIrregularTime", b.isIrregularTime)
        put("startWeek", b.startWeek)
        put("endWeek", b.endWeek)
        put("weekType", b.weekType)
        put("room", b.roomState)
        put("teacher", b.teacherState)
        put("note", b.noteState)
        put("color", b.colorState)
        put("colorMode", b.colorModeState)
        put("days", JSONArray(b.days.toList()))
        put("sourceIds", JSONArray(b.sourceIds))
        put("durationText", b.durationText)
        put("irregularOptionsExpanded", b.irregularOptionsExpanded)
        put("clamped", b.clamped)
    } },
    decode = { o -> MeetingBlockDraft(
        id = o.getInt("id"),
        startNode = o.getInt("startNode"),
        step = o.getInt("step"),
        startTime = o.getString("startTime"),
        endTime = o.getString("endTime"),
        isIrregularNode = o.getBoolean("isIrregularNode"),
        selectedEdgeNode = o.getInt("selectedEdgeNode"),
        isIrregularTime = o.getBoolean("isIrregularTime"),
        startWeek = o.getInt("startWeek"),
        endWeek = o.getInt("endWeek"),
        weekType = o.getInt("weekType"),
        room = o.getString("room"),
        teacher = o.getString("teacher"),
        note = o.getString("note"),
        color = o.getString("color"),
        colorMode = o.getInt("colorMode"),
        days = mutableStateListOf<Int>().apply {
            val days = o.getJSONArray("days")
            for (i in 0 until days.length()) add(days.getInt(i))
        },
        sourceIds = o.getJSONArray("sourceIds").let { ids -> List(ids.length()) { ids.getLong(it) } }
    ).also {
        it.durationText = o.getString("durationText")
        it.irregularOptionsExpanded = o.getBoolean("irregularOptionsExpanded")
        it.clamped = o.getBoolean("clamped")
    } }
)

internal val EdgeInsertsSaver = draftListSaver<PendingEdgeInsert>(
    encode = { JSONObject().put("edge", it.edgeClass.name).put("node", it.node)
        .put("start", it.start).put("end", it.end).put("owner", it.ownerBlockId) },
    decode = { PendingEdgeInsert(TimeTableUtils.EdgeClass.valueOf(it.getString("edge")),
        it.getInt("node"), it.getString("start"), it.getString("end"), it.getInt("owner")) }
)

internal val EdgeEditsSaver = draftListSaver<EdgeSlotEdit>(
    encode = { JSONObject().put("node", it.node).put("start", it.start).put("end", it.end) },
    decode = { EdgeSlotEdit(it.getInt("node"), it.getString("start"), it.getString("end")) }
)
