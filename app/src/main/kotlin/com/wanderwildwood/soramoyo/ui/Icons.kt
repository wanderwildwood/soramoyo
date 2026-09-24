package com.wanderwildwood.soramoyo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icons this app draws, all Material Symbols (Apache-2.0), kept as the path data they
 * ship as. The transport glyphs are the filled cut: a hollow arrowhead at this size, on this
 * panel, reads as a smudge.
 */
object Icons {

    /**
     * Material Symbols are authored in a 960 grid whose origin sits at the bottom left,
     * so the path data runs from -960 to 0 vertically. Shifting the whole thing down by
     * 960 puts it in the top-left grid Compose uses.
     */
    private fun symbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        )
            .addGroup(name = name, translationY = 960f)
            .addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
            .clearGroup()
            .build()

    val Back: ImageVector = symbol("Back", "m313-440 224 224-57 56-320-320 320-320 57 56-224 224h487v80H313Z")

    val Close: ImageVector = symbol("Close", "m256-200-56-56 224-224-224-224 56-56 224 224 224-224 56 56-224 224 224 224-56 56-224-224-224 224Z")

    val Settings: ImageVector = symbol("Settings", "m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm70-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z")

    val Info: ImageVector = symbol("Info", "M440-280h80v-240h-80v240Zm40-320q17 0 28.5-11.5T520-640q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640q0 17 11.5 28.5T480-600Zm0 520q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z")

    val Play: ImageVector = symbol("Play", "M320-200v-560l440 280-440 280Z")

    val Pause: ImageVector = symbol("Pause", "M560-200v-560h160v560H560Zm-320 0v-560h160v560H240Z")

    val StepBack: ImageVector = symbol("StepBack", "M220-240v-480h80v480h-80Zm520 0L380-480l360-240v480Z")

    val StepForward: ImageVector = symbol("StepForward", "M660-240v-480h80v480h-80Zm-440 0v-480l360 240-360 240Z")

    val Plus: ImageVector = symbol("Plus", "M440-440H200v-80h240v-240h80v240h240v80H520v240h-80v-240Z")

    val Minus: ImageVector = symbol("Minus", "M200-440v-80h560v80H200Z")
}
