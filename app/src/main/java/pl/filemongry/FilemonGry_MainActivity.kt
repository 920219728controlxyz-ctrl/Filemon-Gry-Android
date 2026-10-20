package pl.filemongry

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private enum class GameMode(
    val id: String,
    val title: String,
    val icon: String,
    val description: String,
    val tip: String,
    val symbols: List<String>,
    val targetCount: Int,
    val speedFactor: Float,
    val accent: Int,
    val fieldTop: Int,
    val fieldBottom: Int
) {
    MOUSE(
        "mouse", "Polowanie na mysz", "🐭", "Zwinna mysz ucieka po ekranie.",
        "Celuj w myszkę. Złap 5 obiektów z rzędu, aby zdobyć bonus.",
        listOf("🐭", "🐁", "🐭"), 1, 1.0f,
        Color.rgb(91, 224, 176), Color.rgb(15, 44, 45), Color.rgb(25, 71, 64)
    ),
    FISH(
        "fish", "Akwarium rybek", "🐠", "Kilka rybek pływa w różnych kierunkach.",
        "Łap rybki w ruchu. Złota rybka daje więcej punktów.",
        listOf("🐟", "🐠", "🐡"), 3, 0.8f,
        Color.rgb(93, 214, 244), Color.rgb(11, 39, 66), Color.rgb(17, 73, 94)
    ),
    BUTTERFLY(
        "butterfly", "Ogród motyli", "🦋", "Motyle wykonują miękkie zwroty.",
        "Motyle zmieniają tor lotu. Obserwuj ruch i wybierz moment.",
        listOf("🦋", "🦋", "🦋"), 3, 0.95f,
        Color.rgb(196, 145, 255), Color.rgb(34, 23, 62), Color.rgb(65, 38, 91)
    ),
    PAW(
        "paw", "Uciekające łapki", "🐾", "Łapki pojawiają się w wielu miejscach.",
        "Szybka zabawa z kilkoma celami na ekranie.",
        listOf("🐾", "🐾", "🐾"), 4, 1.1f,
        Color.rgb(255, 190, 105), Color.rgb(57, 36, 22), Color.rgb(88, 55, 28)
    ),
    YARN(
        "yarn", "Wełniane piłeczki", "🧶", "Kolorowe kłębki przemykają po planszy.",
        "Kłębki poruszają się w różnym tempie. Bonusowy cel jest złoty.",
        listOf("🧶", "🎾", "🧶"), 2, 0.9f,
        Color.rgb(255, 133, 184), Color.rgb(58, 24, 48), Color.rgb(91, 35, 67)
    ),
    FIREFLY(
        "firefly", "Polowanie na świetliki", "✨", "Świetlne punkty migoczą i uciekają.",
        "Świetliki są małe i szybkie. Szukaj złotych bonusów.",
        listOf("✨", "💫", "⭐"), 5, 1.25f,
        Color.rgb(255, 235, 116), Color.rgb(19, 31, 61), Color.rgb(40, 43, 82)
    ),
    FEATHER(
        "feather", "Taniec piórek", "🪶", "Piórka kołyszą się na wietrze.",
        "Śledź kołyszące się piórka. Każde trafienie podnosi wynik.",
        listOf("🪶", "🪶", "🪶"), 2, 0.72f,
        Color.rgb(145, 198, 255), Color.rgb(20, 35, 59), Color.rgb(34, 61, 89)
    ),
    LASER(
        "laser", "Laserowe kropki", "🔴", "Jasne punkty szybko zmieniają pozycję.",
        "Tryb refleksu: kropki są szybkie, więc wybieraj cel uważnie.",
        listOf("🔴", "✨", "🔴"), 4, 1.4f,
        Color.rgb(255, 105, 126), Color.rgb(49, 17, 31), Color.rgb(83, 23, 46)
    )
}

private data class MovingTarget(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var symbol: String,
    var bonus: Boolean,
    var phase: Float,
    var age: Float = 0f
)

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("filemon_settings", MODE_PRIVATE)
    }

    private var currentScreen = "home"
    private var activeMode = GameMode.MOUSE
    private var activeDailyChallenge = false
    private var sessionSaved = true
    private var currentScore = 0
    private var currentCombo = 0

    private var activeGameView: CatArcadeView? = null

    private lateinit var scoreValue: TextView
    private lateinit var comboValue: TextView
    private lateinit var timerValue: TextView
    private lateinit var gameStatus: TextView
    private lateinit var pauseButton: Button

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun isWide(): Boolean = resources.configuration.screenWidthDp >= 700

    private fun accent(): Int = when (prefs.getInt("theme", 0).coerceIn(0, 2)) {
        1 -> Color.rgb(183, 133, 255)
        2 -> Color.rgb(255, 193, 92)
        else -> Color.rgb(91, 224, 176)
    }

    private fun pageRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(13, 21, 31))
        setPadding(dp(if (isWide()) 24 else 16), dp(8), dp(if (isWide()) 24 else 16), dp(12))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true)
        }
        window.statusBarColor = Color.rgb(13, 21, 31)
        window.navigationBarColor = Color.rgb(13, 21, 31)
        buildHome()
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun label(
        value: String,
        color: Int = Color.WHITE,
        size: Float = 16f,
        gravityValue: Int = Gravity.START,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        gravity = gravityValue
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setPadding(dp(2), dp(3), dp(2), dp(3))
    }

    private fun appButton(
        value: String,
        color: Int = accent(),
        textColor: Int = Color.rgb(15, 24, 32)
    ): Button = Button(this).apply {
        text = value
        textSize = 14f
        isAllCaps = false
        minHeight = dp(48)
        setTextColor(textColor)
        background = rounded(color, 14)
        stateListAnimator = null
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun addPageHeader(
        root: LinearLayout,
        title: String,
        subtitle: String,
        backText: String? = null,
        onBack: (() -> Unit)? = null
    ) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(12))
        }

        if (backText != null && onBack != null) {
            val back = appButton(backText, Color.rgb(34, 48, 63), Color.WHITE)
            header.addView(back, LinearLayout.LayoutParams(dp(88), dp(44)).apply {
                rightMargin = dp(12)
            })
            back.setOnClickListener { onBack() }
        }

        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label(title, accent(), if (isWide()) 27f else 23f, bold = true))
        titles.addView(label(subtitle, Color.rgb(164, 180, 194), 13f))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
    }

    private fun addGap(parent: LinearLayout, heightDp: Int = 12) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun makeCard(color: Int = Color.rgb(27, 39, 53), radius: Int = 20): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(color, radius)
        }

    private fun dailyMode(): GameMode {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return GameMode.values()[(day - 1) % GameMode.values().size]
    }

    private fun todayKey(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun dailyIsDone(): Boolean =
        prefs.getString("daily_done_key", "") == todayKey()

    private fun bestKey(mode: GameMode) = "best_${mode.id}"

    private fun buildHome() {
        currentScreen = "home"
        val root = pageRoot()
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        scroll.addView(body)
        addPageHeader(root, "FILEMON GRY", "CAT ARCADE  •  EDYCJA PREMIUM")
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val hero = makeCard(Color.rgb(27, 48, 57), 24)
        hero.addView(label("✦  KOCIA STREFA ZABAWY", accent(), 12f, bold = true))
        hero.addView(label("Wybierz polowanie.", Color.WHITE, if (isWide()) 27f else 24f, bold = true))
        hero.addView(label(
            "8 minigier • rekordy • wyzwania • działa offline",
            Color.rgb(192, 207, 218), 14f
        ))
        addGap(hero, 12)
        val quickStats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val totalPoints = prefs.getInt("total_points", 0)
        val sessions = prefs.getInt("sessions", 0)
        quickStats.addView(label("★  $totalPoints punktów  ", accent(), 13f, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        quickStats.addView(label("◉  $sessions rund", Color.WHITE, 13f, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(quickStats)
        body.addView(hero)
        addGap(body)

        val daily = dailyMode()
        val dailyCard = makeCard(Color.rgb(39, 36, 57), 20)
        dailyCard.addView(label("WYZWANIE DNIA", Color.rgb(202, 167, 255), 12f, bold = true))
        dailyCard.addView(label("${daily.icon}  ${daily.title}", Color.WHITE, 20f, bold = true))
        dailyCard.addView(label(
            if (dailyIsDone()) "Zaliczone! Możesz poprawić swój rekord." else "Zdobądź 15 punktów w 60 sekund.",
            Color.rgb(196, 194, 213), 13f
        ))
        addGap(dailyCard, 10)
        val dailyButton = appButton(if (dailyIsDone()) "ZAGRAJ PONOWNIE" else "PODEJMIJ WYZWANIE",
            Color.rgb(183, 133, 255))
        dailyButton.setOnClickListener { openGame(daily, true) }
        dailyCard.addView(dailyButton, LinearLayout.LayoutParams(-1, -2))
        body.addView(dailyCard)
        addGap(body, 18)

        body.addView(label("WYBIERZ TRYB GRY", Color.WHITE, 18f, bold = true))
        body.addView(label("Każdy tryb ma osobny rekord i własną dynamikę.",
            Color.rgb(153, 170, 185), 13f))
        addGap(body, 8)

        val columns = when {
            resources.configuration.screenWidthDp >= 1100 -> 4
            isWide() -> 3
            else -> 2
        }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val modes = GameMode.values()
        var index = 0
        while (index < modes.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (column in 0 until columns) {
                if (index < modes.size) {
                    val mode = modes[index++]
                    val card = modeCard(mode)
                    row.addView(card, LinearLayout.LayoutParams(0, dp(if (isWide()) 158 else 148), 1f).apply {
                        rightMargin = dp(6)
                        leftMargin = dp(6)
                        bottomMargin = dp(12)
                    })
                } else {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        body.addView(grid)

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val statsButton = appButton("STATYSTYKI", Color.rgb(34, 48, 63), Color.WHITE)
        statsButton.setOnClickListener { buildStats() }
        val settingsButton = appButton("USTAWIENIA", Color.rgb(34, 48, 63), Color.WHITE)
        settingsButton.setOnClickListener { buildSettings() }
        bottom.addView(statsButton, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        bottom.addView(settingsButton, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        body.addView(bottom)

        body.addView(label("FILEMON GRY  •  BEZ REKLAM  •  BEZ INTERNETU",
            Color.rgb(105, 125, 142), 11f, Gravity.CENTER).apply {
            setPadding(0, dp(16), 0, dp(4))
        })
        setContentView(root)
    }

    private fun modeCard(mode: GameMode): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(27, 39, 53), 18)
            isClickable = true
            isFocusable = true
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(mode.icon, Color.WHITE, 28f))
        top.addView(label("  ${prefs.getInt(bestKey(mode), 0)} pkt", mode.accent, 11f, bold = true))
        card.addView(top)
        card.addView(label(mode.title, Color.WHITE, 15f, bold = true).apply { setPadding(dp(2), dp(6), dp(2), dp(2)) })
        card.addView(label(mode.description, Color.rgb(155, 173, 188), 11f).apply {
            maxLines = 2
            setPadding(dp(2), 0, dp(2), 0)
        })
        card.setOnClickListener { openGame(mode, false) }
        return card
    }

    private fun openGame(mode: GameMode, daily: Boolean) {
        activeMode = mode
        activeDailyChallenge = daily
        currentScore = 0
        currentCombo = 0
        sessionSaved = false
        buildGameScreen()
    }

    private fun buildGameScreen() {
        currentScreen = "game"
        val root = pageRoot()
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        addPageHeader(
            root, "${activeMode.icon}  ${activeMode.title}",
            if (activeDailyChallenge) "WYZWANIE DNIA  •  CEL: 15 PUNKTÓW" else activeMode.description,
            "‹ MENU"
        ) { leaveGame() }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scoreCard = metricCard("PUNKTY", "0", accent())
        val comboCard = metricCard("SERIA", "×0", Color.rgb(255, 190, 105))
        val timerStart = if (activeDailyChallenge) 60 else prefs.getInt("duration", 60)
        val timerText = if (timerStart == 0) "∞" else formatTime(timerStart)
        val timerCard = metricCard("CZAS", timerText, Color.rgb(143, 193, 255))
        scoreValue = scoreCard.second
        comboValue = comboCard.second
        timerValue = timerCard.second
        metrics.addView(scoreCard.first, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        metrics.addView(comboCard.first, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
        metrics.addView(timerCard.first, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        body.addView(metrics)
        addGap(body, 10)

        gameStatus = label(
            if (activeDailyChallenge) "Cel: 15 punktów. Utrzymaj serię!" else activeMode.tip,
            Color.rgb(177, 194, 208), 13f, Gravity.CENTER
        )
        body.addView(gameStatus, LinearLayout.LayoutParams(-1, -2))
        addGap(body, 8)

        val playRow = LinearLayout(this).apply {
            orientation = if (isWide()) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }

        val gameDuration = if (activeDailyChallenge) 60 else prefs.getInt("duration", 60)
        activeGameView = CatArcadeView(
            this,
            activeMode,
            prefs.getInt("speed", 5).coerceIn(1, 10),
            prefs.getInt("target_size", 2).coerceIn(1, 3),
            gameDuration,
            prefs.getBoolean("haptics", true),
            onState = { points, combo, seconds ->
                currentScore = points
                currentCombo = combo
                scoreValue.text = points.toString()
                comboValue.text = "×$combo"
                timerValue.text = if (seconds < 0) "∞" else formatTime(seconds)

                val oldBest = prefs.getInt(bestKey(activeMode), 0)
                if (points > oldBest) {
                    prefs.edit().putInt(bestKey(activeMode), points).apply()
                }
                if (activeDailyChallenge) {
                    if (points >= 15) {
                        if (!dailyIsDone()) {
                            prefs.edit().putString("daily_done_key", todayKey()).apply()
                        }
                        gameStatus.text = "✦ WYZWANIE ZALICZONE! Możesz grać dalej."
                        gameStatus.setTextColor(accent())
                    } else {
                        gameStatus.text = "CEL DNIA: 15 PUNKTÓW  •  POSTĘP $points/15"
                    }
                }
            },
            onFinished = { points ->
                saveSession(points)
                gameStatus.text = if (activeDailyChallenge && points >= 15) {
                    "✦ WYZWANIE ZALICZONE! Świetna robota."
                } else {
                    "RUNDA ZAKOŃCZONA  •  WYNIK: $points"
                }
                pauseButton.text = "ZAGRAJ PONOWNIE"
            }
        )

        val canvasHeight = if (isWide()) 540 else 350
        playRow.addView(
            activeGameView,
            if (isWide()) LinearLayout.LayoutParams(0, dp(canvasHeight), 1.65f).apply {
                rightMargin = dp(12)
            } else LinearLayout.LayoutParams(-1, dp(canvasHeight))
        )

        val controlPanel = makeCard(Color.rgb(25, 37, 51), 20)
        controlPanel.addView(label("PANEL GRY", Color.WHITE, 17f, bold = true))
        controlPanel.addView(label(activeMode.tip, Color.rgb(165, 182, 197), 12f))
        addGap(controlPanel, 10)

        pauseButton = appButton("PAUZA", Color.rgb(67, 88, 108), Color.WHITE)
        pauseButton.setOnClickListener {
            val game = activeGameView ?: return@setOnClickListener
            when {
                game.isFinished() -> {
                    sessionSaved = false
                    currentScore = 0
                    currentCombo = 0
                    game.startNewRound()
                    pauseButton.text = "PAUZA"
                }
                game.isRunning() -> {
                    game.pauseGame()
                    pauseButton.text = "WZNÓW"
                }
                else -> {
                    game.resumeGame()
                    pauseButton.text = "PAUZA"
                }
            }
        }
        controlPanel.addView(pauseButton, LinearLayout.LayoutParams(-1, -2))
        addGap(controlPanel, 8)

        val restart = appButton("NOWA RUNDA", accent())
        restart.setOnClickListener {
            sessionSaved = false
            currentScore = 0
            currentCombo = 0
            activeGameView?.startNewRound()
            pauseButton.text = "PAUZA"
        }
        controlPanel.addView(restart, LinearLayout.LayoutParams(-1, -2))
        addGap(controlPanel, 8)

        val exit = appButton("ZAKOŃCZ GRĘ", Color.rgb(51, 61, 75), Color.WHITE)
        exit.setOnClickListener { leaveGame() }
        controlPanel.addView(exit, LinearLayout.LayoutParams(-1, -2))
        addGap(controlPanel, 12)
        controlPanel.addView(label("WYNIK", Color.rgb(150, 168, 183), 12f, bold = true))
        controlPanel.addView(label("Rekord: ${prefs.getInt(bestKey(activeMode), 0)} pkt",
            Color.WHITE, 17f, bold = true))
        controlPanel.addView(label("Seria co 5 trafień daje dodatkowe punkty.",
            Color.rgb(145, 165, 180), 12f))

        playRow.addView(
            controlPanel,
            if (isWide()) LinearLayout.LayoutParams(0, -2, 0.85f)
            else LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        )

        body.addView(playRow)
        body.addView(label("Dotykaj tylko poruszających się celów. Rekordy zapisują się na tym urządzeniu.",
            Color.rgb(105, 125, 142), 11f, Gravity.CENTER).apply {
            setPadding(dp(4), dp(14), dp(4), dp(10))
        })

        setContentView(root)
        activeGameView?.post { activeGameView?.startNewRound() }
    }

    private fun metricCard(title: String, value: String, color: Int): Pair<LinearLayout, TextView> {
        val card = makeCard(Color.rgb(27, 39, 53), 16)
        card.setPadding(dp(8), dp(8), dp(8), dp(8))
        card.addView(label(title, Color.rgb(145, 164, 180), 10f, Gravity.CENTER, true))
        val valueView = label(value, color, 21f, Gravity.CENTER, true)
        card.addView(valueView)
        return card to valueView
    }

    private fun formatTime(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return String.format(java.util.Locale.ROOT, "%02d:%02d", safe / 60, safe % 60)
    }

    private fun leaveGame() {
        activeGameView?.let { game ->
            saveSession(currentScore)
            game.dispose()
        }
        activeGameView = null
        activeDailyChallenge = false
        buildHome()
    }

    private fun saveSession(score: Int) {
        if (sessionSaved) return
        sessionSaved = true
        val oldSessions = prefs.getInt("sessions", 0)
        val oldPoints = prefs.getInt("total_points", 0)
        val oldCombo = prefs.getInt("best_combo", 0)
        prefs.edit()
            .putInt("sessions", oldSessions + 1)
            .putInt("total_points", oldPoints + score.coerceAtLeast(0))
            .putInt("best_combo", maxOf(oldCombo, currentCombo))
            .apply()
        if (activeDailyChallenge && score >= 15) {
            prefs.edit().putString("daily_done_key", todayKey()).apply()
        }
    }

    private fun buildStats() {
        currentScreen = "stats"
        val root = pageRoot()
        addPageHeader(root, "STATYSTYKI", "Twoje wyniki i odblokowania", "‹ WRÓĆ") { buildHome() }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val totalPoints = prefs.getInt("total_points", 0)
        val sessions = prefs.getInt("sessions", 0)
        val bestCombo = prefs.getInt("best_combo", 0)
        val summary = makeCard(Color.rgb(27, 48, 57), 22)
        summary.addView(label("CENTRUM WYNIKÓW", accent(), 12f, bold = true))
        summary.addView(label("$totalPoints", Color.WHITE, 36f, bold = true))
        summary.addView(label("Łącznie zdobytych punktów", Color.rgb(181, 199, 211), 13f))
        addGap(summary, 10)
        val detailRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        detailRow.addView(label("$sessions rund", Color.WHITE, 14f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        detailRow.addView(label("Najlepsza seria: ×$bestCombo", accent(), 14f, Gravity.END, true), LinearLayout.LayoutParams(0, -2, 1f))
        summary.addView(detailRow)
        body.addView(summary)
        addGap(body, 18)
        body.addView(label("REKORDY TRYBÓW", Color.WHITE, 18f, bold = true))
        addGap(body, 8)

        for (mode in GameMode.values()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Color.rgb(27, 39, 53), 15)
            }
            row.addView(label(mode.icon, Color.WHITE, 26f))
            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(4), 0)
            }
            textColumn.addView(label(mode.title, Color.WHITE, 14f, bold = true))
            textColumn.addView(label(mode.description, Color.rgb(147, 166, 181), 11f))
            row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label("${prefs.getInt(bestKey(mode), 0)} pkt", mode.accent, 16f, Gravity.END, true))
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }

        addGap(body, 12)
        body.addView(label("ODZNAKI", Color.WHITE, 18f, bold = true))
        addGap(body, 8)
        val totalModeRecords = GameMode.values().count { prefs.getInt(bestKey(it), 0) > 0 }
        addAchievement(body, "Pierwszy łowca", "Zdobądź pierwszy punkt", totalPoints >= 1)
        addAchievement(body, "Setka Filemona", "Zdobądź 100 punktów łącznie", totalPoints >= 100)
        addAchievement(body, "Mistrz serii", "Zbuduj serię 5 trafień", bestCombo >= 5)
        addAchievement(body, "Kolekcjoner", "Zagraj we wszystkich 8 trybach", totalModeRecords == GameMode.values().size)
        addAchievement(body, "Stały bywalec", "Rozegraj 20 rund", sessions >= 20)
        addAchievement(body, "Legenda", "Zdobądź 50 pkt w jednym trybie", GameMode.values().any { prefs.getInt(bestKey(it), 0) >= 50 })
        setContentView(root)
    }

    private fun addAchievement(parent: LinearLayout, title: String, description: String, unlocked: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.rgb(25, 37, 51), 14)
        }
        row.addView(label(if (unlocked) "🏆" else "🔒", if (unlocked) accent() else Color.GRAY, 24f))
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        textColumn.addView(label(title, if (unlocked) Color.WHITE else Color.rgb(143, 157, 170), 14f, bold = true))
        textColumn.addView(label(description, Color.rgb(143, 157, 170), 12f))
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(if (unlocked) "ODblok." else "W TOKU", if (unlocked) accent() else Color.rgb(125, 142, 158), 10f, Gravity.END, true))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }

    private fun buildSettings() {
        currentScreen = "settings"
        val root = pageRoot()
        addPageHeader(root, "USTAWIENIA", "Dostosuj zabawę do swojego kota", "‹ WRÓĆ") { buildHome() }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val speedCard = makeCard()
        speedCard.addView(label("SZYBKOŚĆ OBIEKTÓW", Color.WHITE, 17f, bold = true))
        val currentSpeedLabel = label("${prefs.getInt("speed", 5)} / 10", accent(), 16f, bold = true)
        speedCard.addView(currentSpeedLabel)
        speedCard.addView(SeekBar(this).apply {
            max = 9
            progress = prefs.getInt("speed", 5).coerceIn(1, 10) - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 1
                    currentSpeedLabel.text = "$value / 10"
                    prefs.edit().putInt("speed", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        body.addView(speedCard)
        addGap(body)

        val durationCard = makeCard()
        durationCard.addView(label("DŁUGOŚĆ RUNDY", Color.WHITE, 17f, bold = true))
        durationCard.addView(label("Wybierz limit czasu albo graj bez końca.", Color.rgb(155, 173, 188), 13f))
        addGap(durationCard, 10)
        val durationRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val currentDuration = prefs.getInt("duration", 60)
        val durationOptions = listOf(30, 60, 0)
        val durationLabels = listOf("30 SEK", "60 SEK", "∞ BEZ LIMITU")
        for (i in durationOptions.indices) {
            val option = durationOptions[i]
            val chosen = option == currentDuration
            val b = appButton(
                durationLabels[i],
                if (chosen) accent() else Color.rgb(43, 58, 74),
                if (chosen) Color.rgb(15, 24, 32) else Color.WHITE
            )
            b.textSize = 12f
            b.setOnClickListener {
                prefs.edit().putInt("duration", option).apply()
                buildSettings()
            }
            durationRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i > 0) leftMargin = dp(5)
                if (i < durationOptions.lastIndex) rightMargin = dp(5)
            })
        }
        durationCard.addView(durationRow)
        body.addView(durationCard)
        addGap(body)

        val targetsCard = makeCard()
        targetsCard.addView(label("WIELKOŚĆ CELÓW", Color.WHITE, 17f, bold = true))
        targetsCard.addView(label("Większe obiekty są łatwiejsze do dotknięcia łapką.", Color.rgb(155, 173, 188), 13f))
        addGap(targetsCard, 8)
        val size = prefs.getInt("target_size", 2).coerceIn(1, 3)
        val sizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sizes = listOf("MAŁE", "DUŻE", "MAX")
        for (i in 1..3) {
            val chosen = i == size
            val b = appButton(sizes[i - 1], if (chosen) accent() else Color.rgb(43, 58, 74), if (chosen) Color.rgb(15, 24, 32) else Color.WHITE)
            b.setOnClickListener {
                prefs.edit().putInt("target_size", i).apply()
                buildSettings()
            }
            sizeRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i > 1) leftMargin = dp(5)
                if (i < 3) rightMargin = dp(5)
            })
        }
        targetsCard.addView(sizeRow)
        body.addView(targetsCard)
        addGap(body)

        val themeCard = makeCard()
        themeCard.addView(label("KOLORY INTERFEJSU", Color.WHITE, 17f, bold = true))
        val themeNames = listOf("Szmaragd", "Neonowy fiolet", "Złoty")
        val currentTheme = prefs.getInt("theme", 0).coerceIn(0, 2)
        themeCard.addView(label("Motyw: ${themeNames[currentTheme]}", accent(), 14f))
        addGap(themeCard, 8)
        val themeButton = appButton("ZMIEŃ MOTYW", Color.rgb(43, 58, 74), Color.WHITE)
        themeButton.setOnClickListener {
            prefs.edit().putInt("theme", (currentTheme + 1) % 3).apply()
            buildSettings()
        }
        themeCard.addView(themeButton, LinearLayout.LayoutParams(-1, -2))
        body.addView(themeCard)
        addGap(body)

        val hapticsCard = makeCard()
        val hapticsOn = prefs.getBoolean("haptics", true)
        hapticsCard.addView(label("WIBRACJE PRZY TRAFIENIU", Color.WHITE, 17f, bold = true))
        hapticsCard.addView(label("Delikatna informacja dotykowa po trafieniu celu.", Color.rgb(155, 173, 188), 13f))
        addGap(hapticsCard, 8)
        val hapticButton = appButton(if (hapticsOn) "WŁĄCZONE" else "WYŁĄCZONE",
            if (hapticsOn) accent() else Color.rgb(43, 58, 74),
            if (hapticsOn) Color.rgb(15, 24, 32) else Color.WHITE)
        hapticButton.setOnClickListener {
            prefs.edit().putBoolean("haptics", !hapticsOn).apply()
            buildSettings()
        }
        hapticsCard.addView(hapticButton, LinearLayout.LayoutParams(-1, -2))
        body.addView(hapticsCard)
        addGap(body, 16)

        body.addView(label("Wszystkie ustawienia oraz rekordy są przechowywane lokalnie na urządzeniu. Aplikacja nie wymaga konta, sieci ani reklam.",
            Color.rgb(119, 139, 155), 12f))
        setContentView(root)
    }

    override fun onPause() {
        activeGameView?.let { game ->
            if (game.isRunning()) {
                game.pauseGame()
                if (::pauseButton.isInitialized) pauseButton.text = "WZNÓW"
            }
        }
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when (currentScreen) {
            "game" -> leaveGame()
            "settings", "stats" -> buildHome()
            else -> super.onBackPressed()
        }
    }
}

class CatArcadeView(
    context: Context,
    private val mode: GameMode,
    initialSpeed: Int,
    private val targetSize: Int,
    private val durationSeconds: Int,
    private val haptics: Boolean,
    private val onState: (score: Int, combo: Int, secondsLeft: Int) -> Unit,
    private val onFinished: (score: Int) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val random = Random.Default
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targets = mutableListOf<MovingTarget>()

    private var speed = initialSpeed.coerceIn(1, 10)
    private var score = 0
    private var combo = 0
    private var running = false
    private var finished = false
    private var hasStarted = false
    private var elapsedMs = 0L
    private var lastFrame = 0L
    private var lastUiUpdate = 0L

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val deltaMs = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(1L, 50L)
            lastFrame = now
            elapsedMs += deltaMs
            val dt = deltaMs / 16f

            updateTargets(dt)
            val remaining = secondsLeft()
            if (durationSeconds > 0 && remaining <= 0) {
                running = false
                finished = true
                handler.removeCallbacks(this)
                onState(score, combo, 0)
                invalidate()
                onFinished(score)
                return
            }

            if (now - lastUiUpdate >= 110L) {
                onState(score, combo, remaining)
                lastUiUpdate = now
            }
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    fun isRunning() = running
    fun isFinished() = finished
    fun setSpeed(value: Int) { speed = value.coerceIn(1, 10) }

    fun startNewRound() {
        handler.removeCallbacks(loop)
        score = 0
        combo = 0
        elapsedMs = 0L
        lastFrame = 0L
        lastUiUpdate = 0L
        finished = false
        hasStarted = true
        running = true
        targets.clear()
        ensureTargets()
        onState(score, combo, if (durationSeconds == 0) -1 else durationSeconds)
        lastFrame = SystemClock.uptimeMillis()
        handler.post(loop)
        invalidate()
    }

    fun pauseGame() {
        running = false
        lastFrame = 0L
        handler.removeCallbacks(loop)
        invalidate()
    }

    fun resumeGame() {
        if (finished) return
        if (running) return
        hasStarted = true
        running = true
        lastFrame = SystemClock.uptimeMillis()
        handler.removeCallbacks(loop)
        handler.post(loop)
        invalidate()
    }

    fun dispose() {
        pauseGame()
    }

    private fun secondsLeft(): Int {
        if (durationSeconds == 0) return -1
        val remaining = durationSeconds * 1000L - elapsedMs
        return ((remaining + 999L) / 1000L).toInt().coerceAtLeast(0)
    }

    private fun ensureTargets() {
        if (width <= 0 || height <= 0) return
        while (targets.size < mode.targetCount) targets.add(createTarget())
    }

    private fun createTarget(): MovingTarget {
        val base = min(width, height).toFloat().coerceAtLeast(1f)
        val radius = (base * (0.052f + 0.008f * targetSize)).coerceIn(
            27f * density, 48f * density
        )
        val marginX = min(radius + 10f * density, width / 2f)
        val marginY = min(radius + 10f * density, height / 2f)
        val x = marginX + random.nextFloat() * (width - 2f * marginX).coerceAtLeast(1f)
        val y = marginY + random.nextFloat() * (height - 2f * marginY).coerceAtLeast(1f)
        val velocity = (0.65f + speed * 0.12f) * mode.speedFactor * density
        val vx = (random.nextFloat() * 2f - 1f) * velocity
        val vy = (random.nextFloat() * 2f - 1f) * velocity
        return MovingTarget(
            x, y, if (abs(vx) < 0.3f * density) velocity else vx,
            if (abs(vy) < 0.3f * density) -velocity else vy,
            radius,
            mode.symbols[random.nextInt(mode.symbols.size)],
            random.nextFloat() < 0.12f,
            random.nextFloat() * 6.28f
        )
    }

    private fun updateTargets(dt: Float) {
        if (width <= 0 || height <= 0) return
        ensureTargets()
        val multiplier = (0.45f + speed * 0.11f) * mode.speedFactor
        for (target in targets) {
            target.phase += 0.055f * dt
            target.age += dt
            target.x += target.vx * dt * multiplier
            target.y += target.vy * dt * multiplier

            if (mode == GameMode.BUTTERFLY) {
                target.y += sin(target.phase * 1.7f) * density * 0.85f * dt
            }
            if (mode == GameMode.FIREFLY) {
                target.x += sin(target.phase * 2.1f) * density * 0.65f * dt
                target.y += sin(target.phase * 1.4f) * density * 0.65f * dt
            }
            if (mode == GameMode.FEATHER) {
                target.x += sin(target.phase) * density * 0.5f * dt
            }

            val minX = target.radius + dp(6)
            val maxX = (width - target.radius - dp(6)).coerceAtLeast(minX)
            val minY = target.radius + dp(6)
            val maxY = (height - target.radius - dp(24)).coerceAtLeast(minY)
            if (target.x < minX) { target.x = minX; target.vx = abs(target.vx) }
            if (target.x > maxX) { target.x = maxX; target.vx = -abs(target.vx) }
            if (target.y < minY) { target.y = minY; target.vy = abs(target.vy) }
            if (target.y > maxY) { target.y = maxY; target.vy = -abs(target.vy) }

            if (target.age > 170f && random.nextFloat() < 0.008f) {
                target.vx *= -1f
                target.vy *= -1f
            }
        }
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        targets.clear()
        ensureTargets()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, w, h, mode.fieldTop, mode.fieldBottom, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(0f, 0f, w, h, dp(22).toFloat(), dp(22).toFloat(), paint)
        paint.shader = null

        paint.color = Color.argb(22, 255, 255, 255)
        val gridStep = dp(34).toFloat()
        var gx = 0f
        while (gx < w) {
            var gy = 0f
            while (gy < h) {
                canvas.drawCircle(gx, gy, dp(1).toFloat(), paint)
                gy += gridStep
            }
            gx += gridStep
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1).toFloat()
        paint.color = Color.argb(100, Color.red(mode.accent), Color.green(mode.accent), Color.blue(mode.accent))
        canvas.drawRoundRect(
            dp(1).toFloat(), dp(1).toFloat(), w - dp(1), h - dp(1),
            dp(22).toFloat(), dp(22).toFloat(), paint
        )
        paint.style = Paint.Style.FILL

        ensureTargets()
        for (target in targets) drawTarget(canvas, target)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = dp(11).toFloat()
        paint.color = Color.argb(180, 232, 243, 248)
        canvas.drawText(
            if (running) "DOTKNIJ CELU  •  BONUS CO 5 TRAFIEŃ" else "FILEMON ARCADE",
            w / 2f, h - dp(12).toFloat(), paint
        )

        if (!running) drawOverlay(canvas, w, h)

        if (durationSeconds > 0) {
            val progress = (1f - elapsedMs.toFloat() / (durationSeconds * 1000f)).coerceIn(0f, 1f)
            paint.color = Color.argb(80, 255, 255, 255)
            canvas.drawRoundRect(dp(12).toFloat(), dp(9).toFloat(), w - dp(12), dp(13).toFloat(),
                dp(2).toFloat(), dp(2).toFloat(), paint)
            paint.color = mode.accent
            canvas.drawRoundRect(dp(12).toFloat(), dp(9).toFloat(), dp(12) + (w - dp(24)) * progress,
                dp(13).toFloat(), dp(2).toFloat(), dp(2).toFloat(), paint)
        }
    }

    private fun drawTarget(canvas: Canvas, target: MovingTarget) {
        val radius = target.radius
        val cr = if (target.bonus) Color.rgb(255, 209, 94) else mode.accent
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(22, Color.red(cr), Color.green(cr), Color.blue(cr))
        canvas.drawCircle(target.x, target.y, radius * 1.7f, paint)
        paint.color = Color.argb(35, Color.red(cr), Color.green(cr), Color.blue(cr))
        canvas.drawCircle(target.x, target.y, radius * 1.35f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(if (target.bonus) 3 else 2).toFloat()
        paint.color = Color.argb(if (target.bonus) 235 else 115,
            Color.red(cr), Color.green(cr), Color.blue(cr))
        canvas.drawCircle(target.x, target.y, radius * 1.05f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = radius * 1.7f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.color = Color.WHITE
        canvas.drawText(target.symbol, target.x, target.y + paint.textSize * 0.34f, paint)

        if (target.bonus) {
            paint.color = Color.rgb(255, 224, 131)
            paint.textSize = dp(13).toFloat()
            canvas.drawText("★", target.x + radius * 0.7f, target.y - radius * 0.65f, paint)
        }
    }

    private fun drawOverlay(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(166, 7, 13, 23)
        canvas.drawRoundRect(0f, 0f, w, h, dp(22).toFloat(), dp(22).toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        paint.textSize = dp(25).toFloat()
        paint.color = Color.WHITE
        val title = when {
            finished -> "KONIEC RUNDY"
            hasStarted -> "PAUZA"
            else -> "GOTOWY DO GRY"
        }
        canvas.drawText(title, w / 2f, h / 2f, paint)
        paint.textSize = dp(13).toFloat()
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.color = mode.accent
        canvas.drawText(
            when {
                finished -> "Naciśnij ZAGRAJ PONOWNIE"
                hasStarted -> "Naciśnij WZNÓW, aby wrócić"
                else -> "Ruszaj za celem!"
            }, w / 2f, h / 2f + dp(28), paint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (!running || finished) {
            performClick()
            return true
        }

        var selectedIndex = -1
        var selectedDistance = Float.MAX_VALUE
        for (i in targets.indices) {
            val target = targets[i]
            val dx = event.x - target.x
            val dy = event.y - target.y
            val distance = dx * dx + dy * dy
            val hitRadius = target.radius * 1.65f
            if (distance <= hitRadius * hitRadius && distance < selectedDistance) {
                selectedDistance = distance
                selectedIndex = i
            }
        }

        if (selectedIndex >= 0) {
            val target = targets.removeAt(selectedIndex)
            combo++
            score += if (target.bonus) 3 else 1
            if (combo % 5 == 0) score += 2
            ensureTargets()
            if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onState(score, combo, secondsLeft())
        } else {
            combo = 0
            onState(score, combo, secondsLeft())
            if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        invalidate()
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
