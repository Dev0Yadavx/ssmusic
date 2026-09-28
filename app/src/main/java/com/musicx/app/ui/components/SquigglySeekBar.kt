package com.musicx.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class SquigglySeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val activeWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8F5CE") // Dynamic M3 Accent
        strokeWidth = 24f // Thick seekbar ("mota seekbar")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val inactiveTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44474E") // M3 Surface Variant
        strokeWidth = 20f // Thick seekbar ("mota seekbar")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8F5CE")
        style = Paint.Style.FILL
    }

    private val wavePath = Path()
    private var progress: Float = 0f // 0.0 to 1.0
    private var isPlaying: Boolean = false
    private var phase: Float = 0f

    var onSeekListener: ((Float) -> Unit)? = null

    private val waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        if (playing) {
            waveAnimator.start()
        } else {
            waveAnimator.cancel()
            invalidate()
        }
    }

    fun setAccentColor(color: Int) {
        activeWavePaint.color = color
        thumbPaint.color = color
        invalidate()
    }

    fun setColors(activeColor: Int, inactiveColor: Int, thumbColor: Int) {
        activeWavePaint.color = activeColor
        inactiveTrackPaint.color = inactiveColor
        thumbPaint.color = thumbColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        val startX = paddingLeft.toFloat()
        val endX = width - paddingRight.toFloat()
        val currentProgressX = startX + (endX - startX) * progress

        // 1. Draw Inactive Flat Track (Unplayed area)
        if (currentProgressX < endX) {
            canvas.drawLine(currentProgressX, centerY, endX, centerY, inactiveTrackPaint)
        }

        // 2. Draw Active Wave Track (Played area)
        wavePath.reset()
        if (currentProgressX > startX) {
            wavePath.moveTo(startX, centerY)

            val waveLength = 64f
            val amplitude = if (isPlaying) 14f else 0f // Paused hone par flat line

            var x = startX
            while (x <= currentProgressX) {
                val y = centerY + amplitude * sin(((x - startX) / waveLength * (2 * Math.PI) + phase)).toFloat()
                wavePath.lineTo(x, y)
                x += 4f
            }
            canvas.drawPath(wavePath, activeWavePaint)
        }

        // 3. Draw Vertical Bar Thumb ("|" symbol) instead of circular dot
        val barWidth = 12f
        val barHeight = 60f
        val barCorner = 6f
        canvas.drawRoundRect(
            currentProgressX - barWidth / 2f,
            centerY - barHeight / 2f,
            currentProgressX + barWidth / 2f,
            centerY + barHeight / 2f,
            barCorner,
            barCorner,
            thumbPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val newProgress = ((event.x - paddingLeft) / (width - paddingLeft - paddingRight)).coerceIn(0f, 1f)
                setProgress(newProgress)
                onSeekListener?.invoke(newProgress)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
