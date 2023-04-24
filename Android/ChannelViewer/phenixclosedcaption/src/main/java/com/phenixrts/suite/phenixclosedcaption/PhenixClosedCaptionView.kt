/*
 * Copyright 2023 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixclosedcaption

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.setMargins
import com.phenixrts.chat.RoomChatService
import com.phenixrts.chat.RoomChatServiceFactory
import com.phenixrts.common.Disposable
import com.phenixrts.room.RoomService
import com.phenixrts.suite.phenixclosedcaption.ClosedCaptionMessage.AnchorPosition
import com.phenixrts.suite.phenixclosedcaption.ClosedCaptionMessage.WindowUpdate
import com.phenixrts.suite.phenixclosedcaption.common.drawClosedCaptions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber

private const val MESSAGE_BATCH_SIZE = 0
private const val DEFAULT_MIME_TYPE = "application/Phenix-CC"

val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

val Int.sp: Float
    get() =  TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), Resources.getSystem().displayMetrics)

class PhenixClosedCaptionView : FrameLayout {

    private val captionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var chatMessageDisposable: Disposable? = null
    private var chatService: RoomChatService? = null
    private var lastWindowUpdates = listOf<WindowUpdate>()
    var defaultConfiguration = ClosedCaptionConfiguration()

    constructor(context: Context) : super(context) {
        addClosedCaptionButton()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        addClosedCaptionButton()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        addClosedCaptionButton()
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) = captionScope.launch(
        context = CoroutineExceptionHandler { _, e ->
            Timber.w(e, "Coroutine failed: ${e.localizedMessage}")
            e.printStackTrace()
        },
        block = block
    )

    private fun String.fromJson(): ClosedCaptionMessage = try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
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

    private fun showClosedCaptions(rawMessage: String) = launch {
        val closedCaptionMessage = rawMessage.fromJson()
        Timber.d("Showing closed captions:: ${defaultConfiguration.isEnabled}")
        if (!defaultConfiguration.isEnabled) return@launch
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

    private fun ClosedCaptionConfiguration.getConfiguration(windowUpdate: WindowUpdate): ClosedCaptionConfiguration = ClosedCaptionConfiguration(
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

    fun updateConfiguration(configuration: ClosedCaptionConfiguration) {
        defaultConfiguration = configuration.copy()
    }

    fun refresh() {
        removeAllViews()
        addClosedCaptionButton()
    }
}

@Serializable
data class ClosedCaptionMessage(
    val windowUpdates: List<WindowUpdate> = listOf(),
    val textUpdates: List<TextUpdate> = listOf(),
    val windowIndex: Int = 0,
    val serviceId: String = ""
) {

    @Serializable
    data class TextUpdate(
        val timestamp: Long = 0,
        val caption: String = "",
        val windowIndex: Int = 0
    )

    @Serializable
    data class WindowUpdate(
        val windowIndex: Int? = null,
        val anchorPointOnTextWindow: AnchorPosition? = null,
        val positionOfTextWindow: AnchorPosition? = null,
        val widthInCharacters: Int? = null,
        val heightInTextLines: Int? = null,
        val backgroundColor: String? = null,
        val backgroundAlpha: Float? = null,
        val backgroundFlashing: Boolean? = null,
        val visible: Boolean? = null,
        val zOrder: Int? = null,
        val printDirection: String? = null,
        val scrollDirection: String? = null,
        val justify: String? = null,
        val wordWrap: Boolean? = null,
        val effectType: String? = null,
        val effectDurationInSeconds: Int? = null
    )

    @Serializable
    data class AnchorPosition(val x: Float = 0.0f, val y: Float = 0.0f)
}

data class ClosedCaptionConfiguration(
    var anchorPointOnTextWindow: AnchorPosition = AnchorPosition(0.5f, 1f),
    var positionOfTextWindow: AnchorPosition = AnchorPosition(0.5f, 0.95f),
    var widthInCharacters: Int = 32,
    var heightInTextLines: Int = 15,
    var backgroundColor: String = "#000000",
    var backgroundAlpha: Float = 1f,
    var backgroundFlashing: Boolean = false,
    var visible: Boolean = true,
    var zOrder: Int = 0,
    var printDirection: String = "left-to-right",
    var scrollDirection: String = "top-to-bottom",
    var justify: String = JustificationMode.CENTER.value,
    var wordWrap: Boolean = true,
    var effectType: String = "pop-on",
    var effectDurationInSeconds: Int = 1,
    var paddingStart: Int = 4.px,
    var paddingEnd: Int = 4.px,
    val paddingTop: Int = 1.px,
    val paddingBottom: Int = 1.px,
    var textColor: String = "#f4f4f4",
    var isEnabled: Boolean = true,
    var isButtonVisible: Boolean = true
)

enum class JustificationMode(val value: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
    FULL("full");

    companion object {
        private val valuesMap = values().associateBy { it.value }
        fun fromString(value: String) = valuesMap[value] ?: LEFT
    }
}
