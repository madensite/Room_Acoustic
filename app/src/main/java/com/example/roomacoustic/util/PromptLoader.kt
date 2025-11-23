// com/example/roomacoustic/util/PromptLoader.kt
package com.example.roomacoustic.util

import android.content.Context

object PromptLoader {

    /** assets/{assetPath} 내용을 그대로 문자열로 리턴 */
    fun load(context: Context, assetPath: String): String {
        return context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }
}
