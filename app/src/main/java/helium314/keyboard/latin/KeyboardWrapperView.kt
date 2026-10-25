// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.prefs
import kotlin.math.abs

class KeyboardWrapperView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), View.OnClickListener {

    var keyboardActionListener: KeyboardActionListener? = null

    private lateinit var stopOneHandedModeBtn: ImageButton
    private lateinit var switchOneHandedModeBtn: ImageButton
    private lateinit var resizeOneHandedModeBtn: ImageButton

    var oneHandedModeEnabled = false
        set(enabled) {
            field = enabled
            updateViewsVisibility()
            requestLayout()
        }

    /** Samsung-style size editor: an overlay on the keyboard, drag vertically to change height */
    private lateinit var resizeOverlay: FrameLayout

    var resizeModeEnabled = false
        set(enabled) {
            field = enabled
            resizeOverlay.visibility = if (enabled) VISIBLE else GONE
            if (enabled) resizeOverlay.bringToFront()
            requestLayout()
        }

    var oneHandedGravity = Gravity.NO_GRAVITY
        set(value) {
            field = value
            updateSwitchButtonSide()
            requestLayout()
        }


    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()
        val keyboardIconsSet = KeyboardIconsSet.instance
        keyboardIconsSet.loadIcons(context)
        stopOneHandedModeBtn = findViewById(R.id.btn_stop_one_handed_mode)
        stopOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_STOP_ONEHANDED_KEY, context))
        stopOneHandedModeBtn.visibility = GONE
        switchOneHandedModeBtn = findViewById(R.id.btn_switch_one_handed_mode)
        switchOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_SWITCH_ONEHANDED_KEY, context))
        switchOneHandedModeBtn.visibility = GONE
        resizeOneHandedModeBtn = findViewById(R.id.btn_resize_one_handed_mode)
        resizeOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_RESIZE_ONEHANDED_KEY, context))
        resizeOneHandedModeBtn.visibility = GONE

        stopOneHandedModeBtn.setOnClickListener(this)
        switchOneHandedModeBtn.setOnClickListener(this)

        var x = 0f
        resizeOneHandedModeBtn.setOnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> x = motionEvent.rawX
                MotionEvent.ACTION_MOVE -> {
                    // performance is not great because settings are reloaded and keyboard is redrawn
                    // on every move, but it's good enough
                    val sign = -switchOneHandedModeBtn.scaleX
                    // factor 2 to make it more sensitive (maybe could be tuned a little)
                    val changePercent = 2 * sign * (x - motionEvent.rawX) / context.resources.displayMetrics.density
                    if (abs(changePercent) < 1) return@setOnTouchListener true
                    x = motionEvent.rawX
                    val landscape = Settings.getValues().mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE
                    val split = Settings.getValues().mIsSplitKeyboardEnabled
                    val oldScale = Settings.readOneHandedModeScale(context.prefs(), landscape, split, FoldableUtils.isFolded)
                    val newScale = (oldScale + changePercent / 100f).coerceAtMost(2.5f).coerceAtLeast(0.5f)
                    if (newScale == oldScale) return@setOnTouchListener true
                    Settings.getInstance().writeOneHandedModeScale(newScale)
                    KeyboardSwitcher.getInstance().setOneHandedModeEnabled(true, true)
                }
                else -> x = 0f
            }
            true
        }

        resizeOverlay = createResizeOverlay()
        addView(resizeOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val colors = Settings.getValues().mColors
        colors.setColor(stopOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setColor(switchOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setColor(resizeOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setBackground(stopOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setBackground(switchOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setBackground(resizeOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createResizeOverlay(): FrameLayout {
        val density = resources.displayMetrics.density
        val accent = Settings.getValues().mColors.get(ColorType.GESTURE_TRAIL)
        // FrameLayout so the overlay only takes the keyboard's size instead of stretching the input view
        val overlay = FrameLayout(context).apply {
            visibility = GONE
            background = GradientDrawable().apply {
                setColor(Color.argb(170, 0, 0, 0))
                setStroke((2 * density).toInt(), accent)
            }
        }
        // top grab bar, like the blue handle on Samsung's keyboard size screen
        val handle = View(context).apply {
            background = GradientDrawable().apply { setColor(accent); cornerRadius = 3 * density }
        }
        overlay.addView(handle, LayoutParams((48 * density).toInt(), (6 * density).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = (8 * density).toInt()
        })
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val hint = TextView(context).apply {
            setText(R.string.resize_hint)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        column.addView(hint, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (12 * density).toInt()
        })
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun button(text: Int, onClick: () -> Unit) = Button(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.argb(90, 255, 255, 255)); cornerRadius = 20 * density }
            setOnClickListener { onClick() }
            buttons.addView(this, LinearLayout.LayoutParams((110 * density).toInt(), (40 * density).toInt()).apply {
                marginStart = (8 * density).toInt(); marginEnd = (8 * density).toInt()
            })
        }
        button(R.string.resize_reset) {
            Settings.getInstance().resetHeightScale()
            KeyboardSwitcher.getInstance().reloadKeyboard()
        }
        button(R.string.resize_done) { resizeModeEnabled = false }
        column.addView(buttons, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        // centered, away from the navigation bar
        overlay.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        var startY = 0f
        var startScale = 1f
        var startHeight = 1
        overlay.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startScale = Settings.getValues().mKeyboardHeightScale
                    startHeight = KeyboardSwitcher.getInstance().visibleKeyboardView?.height?.coerceAtLeast(1) ?: 1
                }
                MotionEvent.ACTION_MOVE -> {
                    // dragging up makes the keyboard taller; keyboard is redrawn on each step, like one-handed resizing
                    val newScale = (startScale * (startHeight + startY - event.rawY) / startHeight).coerceIn(0.5f, 1.6f)
                    if (abs(newScale - Settings.getValues().mKeyboardHeightScale) >= 0.01f) {
                        Settings.getInstance().writeHeightScale(newScale)
                        KeyboardSwitcher.getInstance().reloadKeyboard()
                    }
                }
            }
            true
        }
        return overlay
    }

    @SuppressLint("RtlHardcoded")
    fun switchOneHandedModeSide() {
        oneHandedGravity = if (oneHandedGravity == Gravity.LEFT) Gravity.RIGHT else Gravity.LEFT
    }

    private fun updateViewsVisibility() {
        stopOneHandedModeBtn.visibility = if (oneHandedModeEnabled) VISIBLE else GONE
        switchOneHandedModeBtn.visibility = if (oneHandedModeEnabled) VISIBLE else GONE
        resizeOneHandedModeBtn.visibility = if (oneHandedModeEnabled) VISIBLE else GONE
    }

    @SuppressLint("RtlHardcoded")
    private fun updateSwitchButtonSide() {
        switchOneHandedModeBtn.scaleX = if (oneHandedGravity == Gravity.LEFT) -1f else 1f
    }

    override fun onClick(view: View) {
        if (view === stopOneHandedModeBtn) {
            keyboardActionListener?.onCodeInput(KeyCode.TOGGLE_ONE_HANDED_MODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */)
        } else if (view === switchOneHandedModeBtn) {
            keyboardActionListener?.onCodeInput(KeyCode.SWITCH_ONE_HANDED_MODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */)
        }
    }

    @SuppressLint("RtlHardcoded")
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!oneHandedModeEnabled) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }

        val isLeftGravity = oneHandedGravity == Gravity.LEFT
        val width = right - left
        val keyboardView = KeyboardSwitcher.getInstance().visibleKeyboardView
        val spareWidth = width - keyboardView.measuredWidth

        val keyboardLeft = if (isLeftGravity) 0 else spareWidth
        keyboardView.layout(
                keyboardLeft,
                0,
                keyboardLeft + keyboardView.measuredWidth,
                keyboardView.measuredHeight
        )

        val scale = Settings.getValues().mKeyboardHeightScale
        // scale one-handed mode button height if keyboard height scale is < 80%
        val heightScale = if (scale < 0.8f) scale + 0.2f else 1f
        val buttonsLeft = if (isLeftGravity) keyboardView.measuredWidth else 0
        val buttonXLeft = buttonsLeft + (spareWidth - stopOneHandedModeBtn.measuredWidth) / 2
        val buttonXRight = buttonsLeft + (spareWidth + stopOneHandedModeBtn.measuredWidth) / 2
        val buttonHeight = (heightScale * stopOneHandedModeBtn.measuredHeight).toInt()
        fun View.setLayout(yPosition: Int) {
            layout(buttonXLeft, yPosition - buttonHeight / 2, buttonXRight, yPosition + buttonHeight / 2)
        }
        stopOneHandedModeBtn.setLayout((keyboardView.measuredHeight * 0.2f).toInt())
        switchOneHandedModeBtn.setLayout((keyboardView.measuredHeight * 0.5f).toInt())
        resizeOneHandedModeBtn.setLayout((keyboardView.measuredHeight * 0.8f).toInt())
        if (resizeModeEnabled)
            resizeOverlay.layout(keyboardLeft, 0, keyboardLeft + keyboardView.measuredWidth, keyboardView.measuredHeight)
    }
}
