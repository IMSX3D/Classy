package com.imsx3d.classy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwNewZfSectionSuffixTest {
    private fun ranges(section: String): List<Pair<Int, Int>> {
        val source = """{"kbList":[{"kcmc":"测试课程","xqj":1,"jc":"$section","zcd":"1-16周"}]}"""
        return JwNewZfParser(source).generateCourseList().map { it.startNode to it.endNode }
    }

    @Test fun rangeWithChineseSuffixIsNotDropped() {
        assertEquals(listOf(8 to 9), ranges("8-9节"))
    }

    @Test fun multipleSuffixedRangesPreserveSeparateMeetings() {
        assertEquals(listOf(3 to 4, 6 to 7), ranges("3-4节，6-7节"))
    }

    @Test fun whitespaceAroundSuffixIsAccepted() {
        assertEquals(listOf(8 to 9), ranges(" 8 - 9 节 "))
    }

    @Test fun paddedSectionsStillWork() {
        assertEquals(listOf(1 to 2, 13 to 14), ranges("01021314"))
    }

    @Test fun invalidSuffixedRangeIsRejected() {
        assertEquals(emptyList<Pair<Int, Int>>(), ranges("8-x节"))
    }
}
