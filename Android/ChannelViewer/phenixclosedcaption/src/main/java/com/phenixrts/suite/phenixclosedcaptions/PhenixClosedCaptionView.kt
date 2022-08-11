/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixclosedcaptions

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.setMargins
import com.phenixrts.suite.phenixclosedcaptions.common.DEFAULT_MIME_TYPE
import com.phenixrts.suite.phenixclosedcaptions.common.drawClosedCaptions
import com.phenixrts.suite.phenixclosedcaptions.common.px
import com.phenixrts.suite.phenixclosedcaptions.models.ClosedCaptionConfiguration
import com.phenixrts.suite.phenixclosedcaptions.models.ClosedCaptionMessage
import com.phenixrts.suite.phenixclosedcaptions.models.WindowUpdate
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.common.asObject
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixcore.repositories.models.PhenixMessageConfiguration
import timber.log.Timber

/**
 * A custom view that draws closed captions onto the screen with provided [ClosedCaptionConfiguration]
 * when new messages with defined mime types are received.
 *
 * Call [subscribeToCC] to start receiving messages to be drawn on the screen.
 */
@Suppress("MemberVisibilityCanBePrivate")
class PhenixClosedCaptionView : FrameLayout {

    private var lastWindowUpdates = listOf<WindowUpdate>()
    var defaultConfiguration = ClosedCaptionConfiguration()
        private set

    constructor(context: Context) : super(context) {
        addClosedCaptionButton()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        addClosedCaptionButton()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        addClosedCaptionButton()
    }

    /**
     * Subscribes to channel chat messages with the given [channelAlias] that have the selected mime types
     * and applies the given [ClosedCaptionConfiguration].
     */
    fun subscribeToCC(
        phenixCore: PhenixCore,
        channelAlias: String,
        mimeType: String = DEFAULT_MIME_TYPE,
        configuration: ClosedCaptionConfiguration = defaultConfiguration
    ) {
        Timber.d("Subscribing for closed captions: $mimeType")
        updateConfiguration(configuration)
        phenixCore.subscribeForMessages(
            channelAlias,
            PhenixMessageConfiguration(mimeType = mimeType, batchSize = 0)
        )
        launchMain {
            phenixCore.messages.collect { messages ->
                messages.lastOrNull()?.let { message ->
                    if (mimeType == message.messageMimeType && message.alias == channelAlias) {
                        showClosedCaptions(message.message)
                    }
                }
            }
        }
    }

    /**
     * Updates the [ClosedCaptionConfiguration]. The new values will be applied when the next
     * cc messages come in or when [refresh] is called.
     */
    fun updateConfiguration(configuration: ClosedCaptionConfiguration) {
        defaultConfiguration = configuration.copy()
    }

    /**
     * Refreshes the [PhenixClosedCaptionView]. Use it together with [updateConfiguration] to
     * apply the new [ClosedCaptionConfiguration].
     */
    fun refresh() {
        removeAllViews()
        addClosedCaptionButton()
    }

    private fun String.fromJson(): ClosedCaptionMessage = try {
        this.asObject()
    } catch (e: Exception) {
        Timber.d(e, "Failed to parse: $this")
        ClosedCaptionMessage()
    }

    private fun addClosedCaptionButton() {
        if (!defaultConfiguration.isButtonVisible) return
        val ccButton = ImageButton(context)
        val layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = Gravity.BOTTOM or Gravity.END
        layoutParams.setMargins(8.px)
        ccButton.layoutParams = layoutParams
        ccButton.setBackgroundResource(if (defaultConfiguration.isEnabled) R.drawable.ic_cc_enabled else R.drawable.ic_cc_disabled)
        ccButton.setOnClickListener {
            Timber.d("Closed Caption Button clicked: ${defaultConfiguration.isEnabled}")
            if (defaultConfiguration.isEnabled) {
                defaultConfiguration.isEnabled = false
                removeAllViews()
                addClosedCaptionButton()
            } else {
                defaultConfiguration.isEnabled = true
                ccButton.setBackgroundResource(R.drawable.ic_cc_enabled)
            }
        }
        addView(ccButton, layoutParams)
    }

    private fun showClosedCaptions(rawMessage: String) = launchMain {
        val closedCaptionMessage = rawMessage.fromJson()
        Timber.d("Showing closed captions:: ${defaultConfiguration.isEnabled}")
        if (!defaultConfiguration.isEnabled) return@launchMain
        removeAllViews()
        val caption = closedCaptionMessage.textUpdates.joinToString(separator = "") { it.caption }
        // Update last received window updates
        closedCaptionMessage.windowUpdates.takeIf { it.isNotEmpty() }?.let { windowUpdates ->
            lastWindowUpdates = windowUpdates
        }
        val windowUpdate = lastWindowUpdates.find {
            it.windowIndex == closedCaptionMessage.windowIndex
        } ?: WindowUpdate()

        // Values from CC message are applied or defaults are used
        drawClosedCaptions(caption, defaultConfiguration.getConfiguration(windowUpdate))
        addClosedCaptionButton()
    }

    private fun ClosedCaptionConfiguration.getConfiguration(windowUpdate: WindowUpdate) = ClosedCaptionConfiguration(
        anchorPointOnTextWindow = (windowUpdate.anchorPointOnTextWindow ?: anchorPointOnTextWindow),
        positionOfTextWindow = windowUpdate.positionOfTextWindow ?: positionOfTextWindow,
        widthInCharacters = windowUpdate.widthInCharacters ?: widthInCharacters,
        heightInTextLines = windowUpdate.heightInTextLines ?: heightInTextLines,
        backgroundColor = windowUpdate.backgroundColor ?: backgroundColor,
        backgroundAlpha = windowUpdate.backgroundAlpha ?: backgroundAlpha,
        backgroundFlashing = windowUpdate.backgroundFlashing ?: backgroundFlashing,
        visible = windowUpdate.visible ?: visible,
        zOrder = windowUpdate.zOrder ?: zOrder,
        printDirection = windowUpdate.printDirection ?: printDirection,
        scrollDirection = windowUpdate.scrollDirection ?: scrollDirection,
        justify = windowUpdate.justify ?: justify,
        wordWrap = windowUpdate.wordWrap ?: wordWrap,
        effectType = windowUpdate.effectType ?: effectType,
        effectDurationInSeconds = windowUpdate.effectDurationInSeconds ?: effectDurationInSeconds,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
        textColor = textColor,
        isEnabled = isEnabled,
        isButtonVisible = isButtonVisible
    )
}
