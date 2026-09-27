package com.nevoit.glasense.core.utility

import android.app.Activity

fun Activity.clearBackground() {
    window.setBackgroundDrawable(null)
}