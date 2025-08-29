package com.liziwa.hisense_autorefresh

import android.graphics.drawable.Drawable

data class ListItem(
    val title: String,
    val pkg: String,
    val icon: Drawable,
    var isChecked: Boolean = false
)