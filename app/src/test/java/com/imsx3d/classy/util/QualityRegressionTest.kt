package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.data.undo.UndoManager
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

class QualityRegressionTest {
    private val start = "2025-12-29" // Monday; Jan 1 belongs to odd week 1
    private val source = LocalDate.parse("2026-01-01")
    private val target = LocalDate.parse("2026-01-11") // Sunday of even week 2
    private val transfer = listOf(HolidayTransferEntry(source, target, "holiday"))
    private fun course(id: Long, day: Int, type: Int = 0) = CourseEntity(
        id = id, tableId = 1, groupId = "g$id", courseName = "Course $id", day = day,
        startNode = 1, step = 2, startWeek = 1, endWeek = 3, type = type, color = "#123456"
    )
    private val oddThursday = course(1, 4, 1)
    private val evenThursday = course(2, 4, 2)
    private val sunday = course(3, 7)

    @Test fun targetUsesSourceWeekParityAndReplacesNativeCourses() {
        val visible = CourseDateResolver.coursesOn(target, start, 3,
            listOf(oddThursday, evenThursday, sunday), transfer)
        assertEquals(listOf(oddThursday), visible)
    }

    @Test fun movedSourceIsEmptyAndUndoMappingRestoresNaturalSchedule() {
        assertTrue(CourseDateResolver.coursesOn(source, start, 3, listOf(oddThursday), transfer).isEmpty())
        assertEquals(listOf(oddThursday), CourseDateResolver.coursesOn(source, start, 3, listOf(oddThursday)))
        assertEquals(listOf(sunday), CourseDateResolver.coursesOn(target, start, 3, listOf(sunday)))
    }

    @Test fun displayWeekProjectsIntoTargetColumnWithoutMutatingStoredRow() {
        val rows = CourseDateResolver.displayWeek(2, start, 3, listOf(oddThursday, sunday), transfer)
        assertEquals(1, rows.size)
        assertEquals(7, rows.single().day)
        assertEquals(oddThursday.id, rows.single().id)
        assertEquals(4, oddThursday.day)
    }

    @Test fun transferAcrossSemesterEndRetainsValidSourceOccurrence() {
        val afterEnd = LocalDate.parse("2026-02-01")
        assertEquals(listOf(oddThursday), CourseDateResolver.coursesOn(afterEnd, start, 1,
            listOf(oddThursday), listOf(HolidayTransferEntry(source, afterEnd, "h"))))
    }

    @Test fun invalidSourceOutsideSemesterDoesNotFabricateWeekOneCourse() {
        val beforeStart = LocalDate.parse("2025-12-25")
        assertTrue(CourseDateResolver.coursesOn(target, start, 3, listOf(oddThursday),
            listOf(HolidayTransferEntry(beforeStart, target, "h"))).isEmpty())
    }

    @Test fun sourceLookupForEditorStillWorksIndependently() {
        assertEquals(transfer.single(), HolidayRangeOps.HolidayTransferOps.transferFor(source, transfer))
        assertEquals(source, HolidayRangeOps.HolidayTransferOps.sourceDateFor(target, transfer))
        assertNull(HolidayRangeOps.HolidayTransferOps.sourceDateFor(source, transfer))
    }

    @Test fun calendarExportMovesOccurrenceInsteadOfKeepingDuplicateOnSource() {
        val table = TimeTableEntity(id = 1, name = "Test", startDate = start, maxWeek = 3)
        val ics = com.imsx3d.classy.data.parser.ScheduleExporter.exportIcs(table, listOf(oddThursday), transfer)
        assertTrue(ics.contains("EXDATE:20260101T"))
        assertTrue(ics.contains("RDATE:20260111T"))
    }

    @Test fun selfMappingDoesNotRemoveClasses() {
        assertEquals(listOf(oddThursday), CourseDateResolver.coursesOn(source, start, 3, listOf(oddThursday),
            listOf(HolidayTransferEntry(source, source, "h"))))
    }

    @Test fun importAllowsExactLimitButRejectsOneByteOverWithoutReadingWholeStream() {
        assertEquals("1234", BoundedImportReader.read(ByteArrayInputStream("1234".toByteArray()), 4))
        val stream = ByteArrayInputStream(ByteArray(100))
        try {
            BoundedImportReader.read(stream, 4)
            fail("Oversized input accepted")
        } catch (_: IllegalArgumentException) {
            assertEquals(95, stream.available())
        }
    }

    @Test fun importLimitCountsBytesNotCharactersAndHandlesBom() {
        val bytes = "课程".toByteArray(Charsets.UTF_8)
        assertEquals("课程", BoundedImportReader.read(ByteArrayInputStream(bytes), 6))
        assertEquals("{}", BoundedImportReader.read(ByteArrayInputStream("\uFEFF{}".toByteArray())))
        assertThrows(IllegalArgumentException::class.java) {
            BoundedImportReader.read(ByteArrayInputStream(bytes), 2)
        }
    }

    @Test fun missingAbiOrEmptyAssetCannotBeDownloaded() {
        val missing = parseReleaseJson("""{"tag_name":"v0.0.9","assets":[]}""", "0.0.8", "arm64-v8a")
        assertTrue(missing.isUpdateAvailable)
        assertFalse(missing.canDownload)
        val wrongAbi = parseReleaseJson("""{"tag_name":"v0.0.9","assets":[{"name":"app-x86_64-release.apk","browser_download_url":"https://example.com/app.apk"}]}""", "0.0.8", "arm64-v8a")
        assertFalse(wrongAbi.canDownload)
    }

    @Test fun validAssetEnablesDownloadAndInvalidUrlsAreRejected() {
        val available = parseReleaseJson("""{"tag_name":"v0.0.9","assets":[{"name":"app-arm64-v8a-release.apk","browser_download_url":"https://example.com/app.apk"}]}""", "0.0.8", "arm64-v8a")
        assertTrue(available.canDownload)
        listOf("", "file:///tmp/app.apk", "javascript:alert(1)", "https://", "http://example.com/app.apk").forEach {
            assertFalse(it, isValidDownloadUrl(it))
        }
    }

    @Test fun undoRedoSnapshotRetainsTransfersAndPeekDoesNotConsumeHistory() {
        UndoManager.clear()
        val table = TimeTableEntity(id = 1, name = "Test", startDate = start)
        UndoManager.capture(listOf(table), listOf(oddThursday), 1L, holidayTransfers = mapOf(1L to transfer))
        val snapshot = requireNotNull(UndoManager.peek())
        assertTrue(UndoManager.hasSnapshot)
        assertEquals(transfer, snapshot.holidayTransfers[1L])
        assertEquals(snapshot, UndoManager.poll())
        UndoManager.recordRedo(snapshot)
        assertEquals(snapshot, UndoManager.peekRedo())
        assertEquals(snapshot, UndoManager.pollRedo())
        UndoManager.clear()
    }
}
