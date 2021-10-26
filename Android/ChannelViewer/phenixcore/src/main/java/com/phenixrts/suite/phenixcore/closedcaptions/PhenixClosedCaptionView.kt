/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.closedcaptions

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.setMargins
import com.phenixrts.chat.RoomChatService
import com.phenixrts.chat.RoomChatServiceFactory
import com.phenixrts.common.Disposable
import com.phenixrts.room.RoomService
import com.phenixrts.suite.phenixcore.R
import com.phenixrts.suite.phenixcore.closedcaptions.common.DEFAULT_MIME_TYPE
import com.phenixrts.suite.phenixcore.closedcaptions.common.MESSAGE_BATCH_SIZE
import com.phenixrts.suite.phenixcore.closedcaptions.common.drawClosedCaptions
import com.phenixrts.suite.phenixcore.closedcaptions.common.px
import com.phenixrts.suite.phenixcore.closedcaptions.models.ClosedCaptionConfiguration
import com.phenixrts.suite.phenixcore.closedcaptions.models.ClosedCaptionMessage
import com.phenixrts.suite.phenixcore.closedcaptions.models.ClosedCaptionMessage.*
import com.phenixrts.suite.phenixcore.closedcaptions.models.WindowUpdate
import com.phenixrts.suite.phenixcore.common.asObject
import com.phenixrts.suite.phenixcore.common.launchMain
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * A custom view that draws closed captions onto the screen with provided [ClosedCaptionConfiguration]
 * when new messages with defined mime types are received.
 *
 * Call [subscribe] to start receiving messages to be drawn on the screen.
 */
@Suppress("MemberVisibilityCanBePrivate")
class PhenixClosedCaptionView : FrameLayout {

    private var chatMessageDisposable: Disposable? = null
    private var chatService: RoomChatService? = null
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
     * Subscribes to given [RoomService] chat service messages that have the selected mime types
     * and applies the given [ClosedCaptionConfiguration].
     */
    fun subscribe(
        roomService: RoomService,
        mimeTypes: List<String> = listOf(DEFAULT_MIME_TYPE),
        configuration: ClosedCaptionConfiguration = defaultConfiguration
    ) {
        Timber.d("Subscribing for closed captions: $mimeTypes")
        updateConfiguration(configuration)
        chatMessageDisposable?.dispose()
        chatMessageDisposable = null
        chatService?.dispose()
        chatService = RoomChatServiceFactory.createRoomChatService(
            roomService,
            MESSAGE_BATCH_SIZE,
            mimeTypes.toTypedArray()
        )
        chatService?.observableLastChatMessage?.subscribe { chatMessage ->
            Timber.d("Last message received: ${chatMessage.observableMessage.value}, ${chatMessage.observableMimeType.value}, $mimeTypes")
            if (mimeTypes.contains(chatMessage.observableMimeType.value)) {
                showClosedCaptions(chatMessage.observableMessage.value)
            }
        }?.run { chatMessageDisposable = this }
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
