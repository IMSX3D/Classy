package com.imsx3d.classy.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.imsx3d.classy.R
import kotlin.math.roundToInt

/** Geometry is anchored to the shared period sizer in XML, never to intrinsic text height. */
internal fun applyGridBlockTypography(context: Context, block: RemoteViews, room: String, barStyle: Boolean) {
    val density = context.resources.displayMetrics.density
    fun dp(value: Float) = (value * density).roundToInt()
    val left = dp(if (barStyle) 9f else 1f)
    block.setViewPadding(R.id.wg_blk_name, left, 0, dp(1f), 0)
    block.setViewPadding(R.id.wg_blk_room, left, dp(2f), dp(1f), 0)
    block.setViewVisibility(R.id.wg_blk_room, if (room.isNotBlank()) View.VISIBLE else View.GONE)
}
