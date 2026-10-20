package pl.filemongry

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.content.SharedPreferences
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var game: CatGameView
    private lateinit var scoreLabel: TextView
    private lateinit var bestLabel: TextView
    private lateinit var startButton: Button
    private lateinit var prefs: SharedPreferences

    private val gold = Color.rgb(255, 200, 87)
    private val bg = Color.rgb(9, 11, 19)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        prefs = getSharedPreferences("filemon_gry", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "FILEMON GRY"
            textSize = 26f
            setTextColor(gold)
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 54))

        val subtitle = TextView(this).apply {
            text = "Zabawa dla ciekawskiego kota"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, 30))

        scoreLabel = makeLabel("WYNIK  0", Color.WHITE)
        bestLabel = makeLabel("REKORD  ${prefs.getInt("best", 0)}", gold)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        stats.addView(scoreLabel, LinearLayout.LayoutParams(0, 48, 1f))
        stats.addView(bestLabel, LinearLayout.LayoutParams(0, 48, 1f))
        root.addView(stats)

        game = CatGameView(this) { points ->
            scoreLabel.text = "WYNIK  $points"
            val best = prefs.getInt("best", 0)
            if (points > best) {
                prefs.edit().putInt("best", points).apply()
                bestLabel.text = "REKORD  $points"
            }
        }
        root.addView(game, LinearLayout.LayoutParams(-1, 0, 1f))

        val speedLabel = makeLabel("TEMPO", Color.LTGRAY)
        root.addView(speedLabel, LinearLayout.LayoutParams(-1, 32))

        val speed = SeekBar(this).apply {
            max = 2
            progress = prefs.getInt("speed", 1).coerceIn(0, 2)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: SeekBar?, value: Int, fromUser: Boolean
                ) {
                    game.setSpeed(value)
                    if (fromUser) prefs.edit().putInt("speed", value).apply()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(speed, LinearLayout.LayoutParams(-1, 48))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        startButton = Button(this).apply {
            text = "START"
            isAllCaps = false
            setOnClickListener {
                game.toggle()
                text = if (game.running) "PAUZA" else "WZNÓW"
            }
        }
        controls.addView(startButton, LinearLayout.LayoutParams(0, 54, 1f))

        val reset = Button(this).apply {
            text = "RESET"
            isAllCaps = false
            setOnClickListener {
                game.reset()
                startButton.text = "START"
                scoreLabel.text = "WYNIK  0"
            }
        }
        controls.addView(reset, LinearLayout.LayoutParams(0, 54, 1f))
        root.addView(controls)

        setContentView(root)
        game.setSpeed(speed.progress)
    }

    private fun makeLabel(value: String, color: Int) =
        TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(color)
            gravity = Gravity.CENTER
        }

    override fun onPause() {
        if (::game.isInitialized) game.stop()
        super.onPause()
    }
}

private class CatGameView(
    context: android.content.Context,
    private val onScore: (Int) -> Unit
) : View(context) {
    var running = false
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random.Default
    private var points = 0
    private var speedLevel = 1
    private var x = 150f
    private var y = 200f
    private var vx = 160f
    private var vy = 120f
    private var radius = 42f
    private var lastFrame = 0L

    fun setSpeed(level: Int) {
        speedLevel = level.coerceIn(0, 2)
    }

    fun toggle() {
        running = !running
        lastFrame = 0L
        if (running) postInvalidateOnAnimation() else invalidate()
    }

    fun stop() {
        running = false
        lastFrame = 0L
        invalidate()
    }

    fun reset() {
        points = 0
        onScore(0)
        running = false
        lastFrame = 0L
        moveTarget()
        invalidate()
    }

    private fun moveTarget() {
        val margin = radius + 8f
        x = margin + random.nextFloat() * (width - 2 * margin).coerceAtLeast(1f)
        y = margin + random.nextFloat() * (height - 2 * margin).coerceAtLeast(1f)
        val speed = when (speedLevel) {
            0 -> 90f
            1 -> 155f
            else -> 235f
        }
        val angle = random.nextFloat() * (Math.PI * 2).toFloat()
        vx = cos(angle) * speed
        vy = sin(angle) * speed
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        radius = (minOf(w, h) * 0.075f).coerceIn(30f, 50f)
        moveTarget()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(12, 16, 27))

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.rgb(35, 44, 62)
        for (i in 1..5) {
            canvas.drawCircle(
                width / 2f, height / 2f,
                i * minOf(width, height) / 7f, paint
            )
        }

        val now = System.currentTimeMillis()
        if (running && width > 0 && height > 0) {
            val dt = if (lastFrame == 0L) 0f
                else ((now - lastFrame).coerceIn(0L, 32L) / 1000f)
            lastFrame = now

            x += vx * dt
            y += vy * dt

            if (x < radius || x > width - radius) {
                vx = -vx
                x = x.coerceIn(radius, width - radius)
            }
            if (y < radius || y > height - radius) {
                vy = -vy
                y = y.coerceIn(radius, height - radius)
            }

            if (random.nextFloat() < 0.015f) moveTarget()
            postInvalidateOnAnimation()
        } else {
            lastFrame = 0L
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(65, 255, 200, 87)
        canvas.drawCircle(x, y, radius * 1.4f, paint)
        paint.color = Color.rgb(255, 200, 87)
        canvas.drawCircle(x, y, radius, paint)
        paint.color = Color.rgb(19, 24, 35)
        canvas.drawCircle(x, y, radius * 0.88f, paint)

        drawMouse(canvas)

        if (!running) {
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "DOTKNIJ START, ABY ZACZĄĆ",
                width / 2f, height * 0.82f, paint
            )
        }
    }

    private fun drawMouse(canvas: Canvas) {
        paint.color = Color.LTGRAY
        canvas.drawCircle(x - radius * 0.25f, y - radius * 0.25f, radius * 0.22f, paint)
        canvas.drawCircle(x + radius * 0.25f, y - radius * 0.25f, radius * 0.22f, paint)
        paint.color = Color.rgb(190, 194, 202)
        canvas.drawOval(
            RectF(x - radius * 0.42f, y - radius * 0.18f,
                x + radius * 0.42f, y + radius * 0.38f),
            paint
        )
        paint.color = Color.rgb(255, 170, 185)
        canvas.drawCircle(x, y + radius * 0.05f, radius * 0.07f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x - radius * 0.14f, y - radius * 0.02f, radius * 0.045f, paint)
        canvas.drawCircle(x + radius * 0.14f, y - radius * 0.02f, radius * 0.045f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        val dx = event.x - x
        val dy = event.y - y
        val hitRadius = radius * 1.25f

        if (dx * dx + dy * dy <= hitRadius * hitRadius) {
            points++
            onScore(points)
            moveTarget()
            invalidate()
        }
        return true
    }
}
