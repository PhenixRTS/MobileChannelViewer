/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixclosedcaption.common

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import com.phenixrts.suite.phenixclosedcaption.ClosedCaptionConfiguration
import com.phenixrts.suite.phenixclosedcaption.JustificationMode
import com.phenixrts.suite.phenixclosedcaption.sp
import timber.log.Timber

private const val MEASURABLE_CHARACTER = "W"
private const val MAX_WIDTH_PERCENTAGE = 0.8
private const val MAX_HEIGHT_PERCENTAGE = 0.8
private const val ALPHA_TRANSPARENT = 0
private const val ALPHA_OPAQUE = 255
private const val FONT_CHANGE_DELTA = 0.01f
private val MIN_FONT_SIZE = 8.sp
private val MAX_FONT_SIZE = 32.sp

private fun View.setVisible() {
    if (visibility != View.VISIBLE) {
        visibility = View.VISIBLE
    }
}

private fun View.setGone() {
    if (visibility != View.GONE) {
        visibility = View.GONE
    }
}

private fun getTextGravity(mode: JustificationMode): Int = when(mode) {
    JustificationMode.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
    JustificationMode.CENTER -> Gravity.CENTER
    JustificationMode.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
    // Android doesn't support full justification out of the box
    JustificationMode.FULL -> Gravity.FILL_HORIZONTAL or Gravity.CENTER_VERTICAL
}

private fun getMeasuredFontSize(widthInCharacters: Int, maxRowWidth: Int, maxRowHeight: Int,
                                paddingHorizontal: Int, paddingVertical: Int): Float {
    val paint = Paint()
    val minFontSize = MIN_FONT_SIZE
    var fontSize = MAX_FONT_SIZE
    var sizeFound = false
    val textToMeasure = MEASURABLE_CHARACTER.repeat(widthInCharacters)
    while (!sizeFound) {
        val textBounds = Rect()
        paint.textSize = fontSize
        paint.getTextBounds(textToMeasure, 0, textToMeasure.length, textBounds)
        val measuredRowWidth = textBounds.width() + paddingHorizontal
        val measuredRowHeight = paint.fontMetrics.bottom - paint.fontMetrics.top + paint.fontMetrics.leading + paddingVertical

        if (measuredRowWidth <= maxRowWidth && measuredRowHeight <= maxRowHeight || fontSize < minFontSize) {
            sizeFound = true
            Timber.d("Font size calculated: $fontSize, width: [$measuredRowWidth / $maxRowWidth], height: [$measuredRowHeight / $maxRowHeight}")
        }
        else {
            fontSize -= FONT_CHANGE_DELTA
        }
    }
    return fontSize
}

private fun AppCompatTextView.drawText(caption: String, rowHeight: Int, fontSize: Float, configuration: ClosedCaptionConfiguration) {
    var backgroundColor = Color.parseColor(configuration.backgroundColor)
    val alpha = (if (caption.isBlank()) ALPHA_TRANSPARENT else ALPHA_OPAQUE * configuration.backgroundAlpha).toInt()
    backgroundColor = ColorUtils.setAlphaComponent(backgroundColor, alpha)

    val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, rowHeight)
    params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    gravity = getTextGravity(JustificationMode.fromString(configuration.justify))
    layoutParams = params
    text = caption

    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
    setBackgroundColor(backgroundColor)
    setTextColor(Color.parseColor(configuration.textColor))
    setPadding(configuration.paddingStart, configuration.paddingTop, configuration.paddingEnd, configuration.paddingBottom)
}

fun FrameLayout.drawClosedCaptions(caption: String, configuration: ClosedCaptionConfiguration) {
    val holderWidth = (width * MAX_WIDTH_PERCENTAGE).toInt()
    val holderHeight = (height * MAX_HEIGHT_PERCENTAGE).toInt()
    val textRowHeight = holderHeight / configuration.heightInTextLines
    val fontSize = getMeasuredFontSize(
        configuration.widthInCharacters,
        holderWidth,
        textRowHeight,
        configuration.paddingStart + configuration.paddingEnd,
        configuration.paddingTop + configuration.paddingBottom
    )

    // Create Closed Caption holder
    val holder = LinearLayout(context)
    val layoutParams = FrameLayout.LayoutParams(holderWidth, holderHeight)
    layoutParams.gravity = Gravity.CENTER
    holder.layoutParams = layoutParams
    holder.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    holder.orientation = LinearLayout.VERTICAL
    if (configuration.visible && caption.isNotBlank()) {
        holder.setVisible()
    } else {
        holder.setGone()
    }

    // Add Closed Captions to holder
    var rowsAdded = 0
    caption.split("\n".toRegex()).map { it.takeIf { it.isNotBlank() } ?: " "}.forEach { row ->
        row.chunked(configuration.widthInCharacters).forEach { textRow ->
            val textView = AppCompatTextView(context)
            textView.drawText(textRow, textRowHeight, fontSize, configuration)
            if (rowsAdded < configuration.heightInTextLines) {
                holder.addView(textView)
                rowsAdded++
            }
        }
    }

    // Add Closed Caption holder to window
    addView(holder, configuration.zOrder)
}
