package com.imsx3d.classy.widget

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class WidgetPresetColumnsTest {
    private val main = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    @Test fun `two day columns have separate adapters and sibling empty views`() {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(main, "res/layout/widget_twoday_columns.xml")).documentElement
        val lists = root.getElementsByTagName("ListView")
        assertEquals(2, lists.length)
        val ids = mutableSetOf<String>()
        for (i in 0 until lists.length) {
            val list = lists.item(i) as Element
            ids.add(list.getAttribute("android:id"))
            val siblings = (list.parentNode as Element).getElementsByTagName("TextView")
            assertEquals("Each column needs an independent empty view", 1, siblings.length)
        }
        assertEquals(setOf("@+id/twoday_left_list", "@+id/twoday_right_list"), ids)
    }
    @Test fun `compact and regular rows retain all required binding targets`() {
        val required = setOf("@+id/widget_row_root", "@+id/widget_row_bar", "@+id/widget_row_name", "@+id/widget_row_meta", "@+id/widget_row_time")
        for (name in listOf("widget_course_row", "widget_course_row_compact")) {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(main, "res/layout/$name.xml")).documentElement
            val nodes = root.getElementsByTagName("*")
            val ids = mutableSetOf(root.getAttribute("android:id"))
            for (i in 0 until nodes.length) ids.add((nodes.item(i) as Element).getAttribute("android:id"))
            assertTrue(name, ids.containsAll(required))
        }
    }
}
