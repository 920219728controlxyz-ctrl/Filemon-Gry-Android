package pl.filemongry

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var game: CatGameView
    private lateinit var scoreText: TextView
    private lateinit var bestText: TextView
    private lateinit var speedText: TextView
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button

    private var speed = 5

    private val prefs by lazy {
        getSharedPreferences("filemon_settings", MODE_PRIVATE)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(19, 28, 38)
        window.navigationBarColor = Color.rgb(19, 28, 38)
        speed = prefs.getInt("speed", 5).coerceIn(1, 10)
        buildInterface()
    }

    private fun buildInterface() {
        val wide = resources.configuration.screenWidthDp >= 700
        val bg = Color.rgb(19, 28, 38)
        val panel = Color.rgb(30, 42, 55)
        val accent = Color.rgb(91, 210, 165)
        val muted = Color.rgb(180, 194, 207)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }

        header.addView(label(
            "FILEMON GRY", accent, if (wide) 32f else 27f
        ).apply {
            letterSpacing = 0.06f
        })

        header.addView(label(
            "Zabawa dla ciekawskiego kota", muted, 15f
        ))

        root.addView(header)

        val content = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL
                else LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }

        val gameColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scores = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = rounded(panel, 16)
        }

        scoreText = label("PUNKTY\n0", accent, 21f)
        bestText = label(
            "REKORD\n${prefs.getInt("best", 0)}",
            Color.WHITE,
            19f
        )

        scores.addView(scoreText, LinearLayout.LayoutParams(0, -2, 1f))
        scores.addView(bestText, LinearLayout.LayoutParams(0, -2, 1f))

        gameColumn.addView(
            scores,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        )

        game = CatGameView(this) { points ->
            runOnUiThread {
                scoreText.text = "PUNKTY\n$points"
                val record = prefs.getInt("best", 0)
                if (points > record) {
                    prefs.edit().putInt("best", points).apply()
                    bestText.text = "REKORD\n$points"
                }
            }
        }

        game.background = rounded(Color.rgb(235, 242, 235), 18)

        gameColumn.addView(
            game,
            LinearLayout.LayoutParams(
                -1,
                dp(if (wide) 480 else 300)
            )
        )

        val gameParams = if (wide) {
            LinearLayout.LayoutParams(0, -2, 1.5f).apply {
                rightMargin = dp(18)
            }
        } else {
            LinearLayout.LayoutParams(-1, -2)
        }
        content.addView(gameColumn, gameParams)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 18)
        }

        controls.addView(label("STEROWANIE", Color.WHITE, 19f, Gravity.START))

        controls.addView(TextView(this).apply {
            text = "Dotknij myszki, aby zdobyć punkt."
            textSize = 14f
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(10))
        })

        startButton = button("START", accent)
        startButton.setOnClickListener {
            game.startGame()
            startButton.text = "GRA W TOKU"
            pauseButton.text = "PAUZA"
        }
        controls.addView(startButton, buttonParams())

        pauseButton = button("PAUZA", Color.rgb(75, 95, 115))
        pauseButton.setOnClickListener {
            if (game.isRunning()) {
                game.pauseGame()
                pauseButton.text = "WZNÓW"
                startButton.text = "START"
            } else {
                game.startGame()
                pauseButton.text = "PAUZA"
                startButton.text = "GRA W TOKU"
            }
        }
        controls.addView(pauseButton, buttonParams())

        val reset = button("ZERUJ PUNKTY", Color.rgb(180, 91, 91))
        reset.setOnClickListener {
            game.resetGame()
            scoreText.text = "PUNKTY\n0"
            startButton.text = "START"
            pauseButton.text = "PAUZA"
        }
        controls.addView(reset, buttonParams())

        controls.addView(
            label("SZYBKOŚĆ MYSZKI", Color.WHITE, 16f, Gravity.START)
        )

        speedText = label("$speed / 10", accent, 17f, Gravity.START)
        controls.addView(speedText)

        controls.addView(SeekBar(this).apply {
            max = 9
            progress = speed - 1
            contentDescription = "Szybkość myszki"
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        speed = progress + 1
                        speedText.text = "$speed / 10"
                        game.setSpeed(speed)
                        prefs.edit().putInt("speed", speed).apply()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                }
            )
        })

        controls.addView(TextView(this).apply {
            text = "Działa lokalnie • Bez reklam • Bez internetu"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, 0)
        })

        val controlsParams = if (wide) {
            LinearLayout.LayoutParams(0, -2, 0.85f)
        } else {
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            }
        }
        content.addView(controls, controlsParams)

        if (wide) {
            root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val scroll = ScrollView(this).apply {
                isFillViewport = false
                clipToPadding = false
                addView(content)
            }
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        setContentView(root)
    }

    private fun label(
        textValue: String,
        color: Int,
        size: Float,
        gravityValue: Int = Gravity.CENTER
    ) = TextView(this).apply {
        text = textValue
        textSize = size
        gravity = gravityValue
        setTextColor(color)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setPadding(dp(4), dp(3), dp(4), dp(3))
    }

    private fun button(textValue: String, color: Int) =
        Button(this).apply {
            text = textValue
            textSize = 14f
            isAllCaps = false
            minHeight = dp(50)
            setTextColor(
                if (color == Color.rgb(91, 210, 165))
                    Color.rgb(19, 28, 38) else Color.WHITE
            )
            background = rounded(color, 14)
            stateListAnimator = null
        }

    private fun buttonParams() =
        LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(7)
        }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    override fun onPause() {
        if (::game.isInitialized) game.pauseGame()
        super.onPause()
    }

    override fun onDestroy() {
        if (::game.isInitialized) game.dispose()
        super.onDestroy()
    }
}

private class CatGameView(
    context: Context,
    private val onScore: (Int) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val random = Random.Default
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var points = 0
    private var speed = 5
    private var running = false
    private var lastMove = 0L
    private var targetX = -1f
    private var targetY = -1f
    private var phase = 0f

    private val animation = object : Runnable {
        override fun run() {
            if (!running) return

            val now = SystemClock.uptimeMillis()
            val interval = (1150L - speed * 85L).coerceAtLeast(250L)

            if (now - lastMove >= interval) {
                moveTarget()
                lastMove = now
            }

            phase += 0.10f
            invalidate()
            handler.postDelayed(this, 32L)
        }
    }

    fun isRunning() = running

    fun setSpeed(value: Int) {
        speed = value.coerceIn(1, 10)
    }

    fun startGame() {
        if (width > 0 && height > 0 &&
            (targetX < 0f || targetY < 0f)
        ) {
            moveTarget()
        }
        running = true
        lastMove = SystemClock.uptimeMillis()
        handler.removeCallbacks(animation)
        handler.post(animation)
        invalidate()
    }

    fun pauseGame() {
        running = false
        handler.removeCallbacks(animation)
        invalidate()
    }

    fun resetGame() {
        pauseGame()
        points = 0
        onScore(0)
        moveTarget()
        invalidate()
    }

    fun dispose() {
        pauseGame()
    }

    private fun moveTarget() {
        if (width <= 0 || height <= 0) return

        val marginX = min(48f * density, width / 2f)
        val marginY = min(52f * density, height / 2f)

        targetX = marginX + random.nextFloat() *
            (width - 2f * marginX).coerceAtLeast(1f)

        targetY = marginY + random.nextFloat() *
            (height - 2f * marginY).coerceAtLeast(1f)

        invalidate()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        moveTarget()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(235, 242, 235)
        canvas.drawRoundRect(
            0f, 0f, w, h,
            18f * density, 18f * density, paint
        )

        paint.color = Color.rgb(220, 231, 218)
        val step = 34f * density
        var gx = 0f
        while (gx < w) {
            var gy = 0f
            while (gy < h) {
                canvas.drawCircle(gx, gy, 1.5f * density, paint)
                gy += step
            }
            gx += step
        }

        if (targetX < 0f || targetY < 0f) {
            targetX = w / 2f
            targetY = h / 2f
        }

        drawMouse(canvas, targetX, targetY)

        paint.color = Color.rgb(49, 69, 54)
        paint.textSize = 13f * density
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(
            "sans-serif-medium", Typeface.NORMAL
        )
        canvas.drawText(
            if (running) "ZŁAP MYSZKĘ!" else "NACIŚNIJ START",
            w / 2f,
            h - 16f * density,
            paint
        )
    }

    private fun drawMouse(canvas: Canvas, x: Float, y: Float) {
        val scale = min(width, height).toFloat().coerceAtLeast(1f)
        val r = (scale * 0.075f).coerceIn(
            25f * density, 43f * density
        )

        val bob = if (running) sin(phase) * 3f * density else 0f
        val cx = x
        val cy = y + bob

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawOval(
            cx - r * 0.85f, cy + r * 0.72f,
            cx + r * 0.85f, cy + r, paint
        )

        paint.color = Color.rgb(151, 161, 171)
        canvas.drawCircle(cx - r * 0.52f, cy - r * 0.57f, r * 0.37f, paint)
        canvas.drawCircle(cx + r * 0.52f, cy - r * 0.57f, r * 0.37f, paint)

        paint.color = Color.rgb(238, 155, 169)
        canvas.drawCircle(cx - r * 0.52f, cy - r * 0.57f, r * 0.21f, paint)
        canvas.drawCircle(cx + r * 0.52f, cy - r * 0.57f, r * 0.21f, paint)

        paint.color = Color.rgb(151, 161, 171)
        canvas.drawOval(
            cx - r, cy - r * 0.55f,
            cx + r, cy + r * 0.85f, paint
        )

        paint.color = Color.WHITE
        canvas.drawCircle(cx - r * 0.35f, cy - r * 0.05f, r * 0.18f, paint)
        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.05f, r * 0.18f, paint)

        paint.color = Color.rgb(35, 43, 50)
        canvas.drawCircle(cx - r * 0.32f, cy - r * 0.03f, r * 0.08f, paint)
        canvas.drawCircle(cx + r * 0.32f, cy - r * 0.03f, r * 0.08f, paint)
        canvas.drawCircle(cx, cy + r * 0.20f, r * 0.10f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.rgb(115, 126, 135)

        canvas.drawLine(
            cx - r * 0.15f, cy + r * 0.35f,
            cx - r * 0.70f, cy + r * 0.25f, paint
        )
        canvas.drawLine(
            cx + r * 0.15f, cy + r * 0.35f,
            cx + r * 0.70f, cy + r * 0.25f, paint
        )

        paint.color = Color.rgb(224, 115, 129)
        paint.strokeWidth = 4f * density
        canvas.drawLine(
            cx + r * 0.75f, cy + r * 0.55f,
            cx + r * 1.15f, cy + r * 0.72f, paint
        )
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        if (!running) {
            performClick()
            return true
        }

        val radius = min(width, height) * 0.14f
        val dx = event.x - targetX
        val dy = event.y - targetY

        if (dx * dx + dy * dy <= radius * radius) {
            points++
            onScore(points)
            moveTarget()
            lastMove = SystemClock.uptimeMillis()
        }

        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
