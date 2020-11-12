/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixclosedcaption

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.text.StaticLayout
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.setMargins
import androidx.core.view.setPadding
import com.phenixrts.chat.RoomChatService
import com.phenixrts.chat.RoomChatServiceFactory
import com.phenixrts.common.Disposable
import com.phenixrts.room.RoomService
import com.phenixrts.suite.phenixclosedcaption.ClosedCaptionMessage.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber

private const val MESSAGE_BATCH_SIZE = 0
private const val DEFAULT_MIME_TYPE = "application/Phenix-CC"
private const val MEASURABLE_CHARACTER = "W"

val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

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

    private fun getTextGravity(mode: JustificationMode): Int = when(mode) {
        JustificationMode.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
        JustificationMode.CENTER -> Gravity.CENTER
        JustificationMode.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
        // Android doesn't support full justification out of the box
        JustificationMode.FULL -> Gravity.FILL_HORIZONTAL or Gravity.CENTER_VERTICAL
    }

    private fun String.fromJson(): ClosedCaptionMessage = try {
        Json { ignoreUnknownKeys = true }.decodeFromString(this)
    } catch (e: Exception) {
        Timber.d(e, "Failed to parse: $this")
        ClosedCaptionMessage()
    }

    private fun View.setVisible(condition: Boolean) {
        val newVisibility = if (condition) View.VISIBLE else View.GONE
        if (visibility != newVisibility) {
            visibility = newVisibility
        }
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
        Timber.d("Showing closed captions:: ${defaultConfiguration.isEnabled}, $closedCaptionMessage")
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
        val textView = TextView(context)

        // Values from CC message are applied or defaults are used
        val layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        var backgroundColor = Color.parseColor(
            windowUpdate.backgroundColor ?: defaultConfiguration.backgroundColor
        )
        val alpha = (255 * (windowUpdate.backgroundAlpha ?: defaultConfiguration.backgroundAlpha)).toInt()
        backgroundColor = ColorUtils.setAlphaComponent(backgroundColor, alpha)

        textView.layoutParams = layoutParams
        if (windowUpdate.wordWrap == false) {
            textView.maxLines = windowUpdate.heightInTextLines ?: defaultConfiguration.heightInTextLines
        }
        textView.typeface = Typeface.MONOSPACE
        textView.setVisible(windowUpdate.visible ?: defaultConfiguration.visible && caption.isNotBlank())
        textView.setBackgroundColor(backgroundColor)
        textView.setTextColor(Color.parseColor(defaultConfiguration.textColor))
        textView.setPadding(defaultConfiguration.textPadding)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, defaultConfiguration.textSize)
        textView.gravity = getTextGravity(
            JustificationMode.fromString(
                windowUpdate.justify ?: defaultConfiguration.justify
            )
        )
        val textViewWidth = StaticLayout.getDesiredWidth(MEASURABLE_CHARACTER, textView.paint).toInt() *
                (windowUpdate.widthInCharacters ?: defaultConfiguration.widthInCharacters)
        val textViewHeight = textView.lineHeight *
                (windowUpdate.heightInTextLines ?: defaultConfiguration.heightInTextLines)
        textView.minWidth = textViewWidth
        textView.maxWidth = textViewWidth + defaultConfiguration.textPadding * 2
        textView.minHeight = textViewHeight
        textView.text = caption

        // Measure text view size and apply position
        textView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        )
        val locationX = width *( windowUpdate.positionOfTextWindow ?: defaultConfiguration.positionOfTextWindow).x -
                textView.measuredWidth * (windowUpdate.anchorPointOnTextWindow ?: defaultConfiguration.anchorPointOnTextWindow).x
        val locationY = height * (windowUpdate.positionOfTextWindow ?: defaultConfiguration.positionOfTextWindow).y -
                textView.measuredHeight * (windowUpdate.anchorPointOnTextWindow ?: defaultConfiguration.anchorPointOnTextWindow).y
        textView.x = locationX
        textView.y = locationY
        addView(textView, windowUpdate.zOrder ?: defaultConfiguration.zOrder)
        addClosedCaptionButton()
    }

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
    var heightInTextLines: Int = 1,
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
    var textPadding: Int = 8.px,
    var textColor: String = "#f4f4f4",
    var textSize: Float = 14f,
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
