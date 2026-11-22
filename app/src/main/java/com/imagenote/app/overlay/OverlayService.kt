package com.imagenote.app.overlay

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.imagenote.app.MainActivity
import com.imagenote.app.R
import com.imagenote.app.data.NoteStore
import com.imagenote.app.data.SettingsStore
import com.imagenote.app.data.TextNote
import com.imagenote.app.render.Exporter
import com.imagenote.app.render.NoteRenderer
import com.imagenote.app.speech.VoiceCapture
import com.imagenote.app.speech.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Transparent mode: a small floating ImageNote bar over whatever app you're using.
 * Tap the mic → the current screen is captured (with the bar hidden), you speak, and your words
 * are attached to that screenshot as an editable note in ImageNote.
 */
class OverlayService : Service() {

    companion object {
        private const val ACTION_START = "com.imagenote.app.overlay.START"
        private const val ACTION_STOP = "com.imagenote.app.overlay.STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val CHANNEL = "transparent_mode"
        private const val NOTIFICATION_ID = 7

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Set by MainActivity so the bar hides while ImageNote itself is on screen. */
        val appVisible = MutableStateFlow(false)

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, OverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var voice: VoiceCapture
    private var grabber: ScreenGrabber? = null

    private var root: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var transcript: TextView
    private lateinit var card: LinearLayout
    private lateinit var cardImage: ImageView
    private lateinit var cardText: TextView
    private lateinit var micView: ImageView
    private lateinit var micBg: FrameLayout

    private var pendingShot: Bitmap? = null
    private var shotY = 0.5f
    private var lastCapture: Uri? = null
    private var cardJob: Job? = null
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        voice = VoiceCapture(this, scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_START -> {
                if (grabber != null) return START_NOT_STICKY
                goForeground()
                val code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                val mpm = getSystemService(MediaProjectionManager::class.java)
                val projection = data?.let { runCatching { mpm.getMediaProjection(code, it) }.getOrNull() }
                if (projection == null) { shutdown(); return START_NOT_STICKY }
                grabber = ScreenGrabber(this, projection) { scope.launch { shutdown() } }
                    .also { runCatching { it.start() }.onFailure { shutdown(); return START_NOT_STICKY } }
                showOverlay()
                _running.value = true
                scope.launch { appVisible.collect { visible -> root?.visibility = if (visible) View.GONE else View.VISIBLE } }
                scope.launch { voice.state.collect(::renderVoice) }
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Transparent mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the floating ImageNote bar is active"
            }
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle("ImageNote transparent mode")
            .setContentText("Tap the floating mic on any screen to add a voice note")
            .setContentIntent(openApp)
            .setColor(0xFFFF7A1A.toInt())
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(NOTIFICATION_ID, n, type)
    }

    // ------------------------------------------------------------------ UI

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    private fun pill(color: Int, stroke: Int = 0x66FF7A1A, radius: Float = 999f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.coerceAtMost(60f))
        setStroke(dpi(1f), stroke)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val prefs = getSharedPreferences("overlay", MODE_PRIVATE)

        transcript = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            maxWidth = dpi(280f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.START
            background = pill(0xEE141418.toInt(), 0x99FF7A1A.toInt(), 18f)
            setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
            visibility = View.GONE
        }

        cardImage = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = dpi(150f)
            maxWidth = dpi(120f)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        cardText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; text = "Note added"; maxWidth = dpi(170f)
        }
        fun cardButton(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(0xFF140800.toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFFA040.toInt(), 0xFFFF7A1A.toInt(), 0xFFFF4E00.toInt())
            ).apply { cornerRadius = dp(40f) }
            setPadding(dpi(14f), dpi(6f), dpi(14f), dpi(6f))
            setOnClickListener { onClick() }
        }
        val cardButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cardButton("Open") { openInApp(lastCapture) })
            addView(View(context), LinearLayout.LayoutParams(dpi(8f), 1))
            addView(TextView(context).apply {
                text = "Undo"; textSize = 13f; setTextColor(Color.WHITE)
                background = pill(0x33FFFFFF, 0x55FFFFFF, 40f)
                setPadding(dpi(14f), dpi(6f), dpi(14f), dpi(6f))
                setOnClickListener { undoLast() }
            })
        }
        val cardInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12f), 0, 0, 0)
            val wrap = { LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
            addView(cardText, wrap())
            addView(View(context), LinearLayout.LayoutParams(1, dpi(8f)))
            addView(cardButtons, wrap())
        }
        card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill(0xF0141418.toInt(), 0xAAFF7A1A.toInt(), 20f)
            setPadding(dpi(10f), dpi(10f), dpi(14f), dpi(10f))
            addView(cardImage)
            addView(cardInfo)
            visibility = View.GONE
        }

        // The bar: [open app] [ MIC ] [close]
        fun smallButton(icon: Int, desc: String, onClick: () -> Unit) = ImageView(this).apply {
            setImageResource(icon)
            contentDescription = desc
            val p = dpi(10f)
            setPadding(p, p, p, p)
            background = pill(0x22FFFFFF, 0x33FFFFFF)
            setOnClickListener { onClick() }
        }
        micView = ImageView(this).apply { setImageResource(R.drawable.ic_ov_mic); contentDescription = "Speak a note" }
        micBg = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFFA040.toInt(), 0xFFFF7A1A.toInt(), 0xFFFF4E00.toInt())
            ).apply { shape = GradientDrawable.OVAL; setStroke(dpi(2f), 0x99FFFFFF.toInt()) }
            elevation = dp(8f)
            addView(micView, FrameLayout.LayoutParams(dpi(30f), dpi(30f), Gravity.CENTER))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill(0xEE111114.toInt(), 0x99FF7A1A.toInt())
            setPadding(dpi(8f), dpi(6f), dpi(8f), dpi(6f))
            elevation = dp(10f)
            addView(smallButton(R.drawable.ic_ov_open, "Open ImageNote") { openInApp(null) }, LinearLayout.LayoutParams(dpi(40f), dpi(40f)))
            addView(micBg, LinearLayout.LayoutParams(dpi(58f), dpi(58f)).apply { marginStart = dpi(10f); marginEnd = dpi(10f) })
            addView(smallButton(R.drawable.ic_ov_close, "Exit transparent mode") { shutdown() }, LinearLayout.LayoutParams(dpi(40f), dpi(40f)))
        }

        val rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f))
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
            addView(transcript, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dpi(24f))
            y = prefs.getInt("y", resources.displayMetrics.heightPixels - dpi(220f))
        }

        // Drag the bar anywhere; a tap on the mic records.
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        val dragListener = View.OnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                    v.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        clampPosition()
                        root?.let { wm.updateViewLayout(it, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (dragging) prefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    else if (v === micBg) onMicTapped()
                }
                MotionEvent.ACTION_CANCEL -> v.isPressed = false
            }
            true
        }
        micBg.setOnTouchListener(dragListener)
        bar.setOnTouchListener(dragListener)

        root = rootView
        clampPosition()
        wm.addView(rootView, params)
    }

    private fun clampPosition() {
        val m = resources.displayMetrics
        params.x = params.x.coerceIn(0, (m.widthPixels - dpi(200f)).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (m.heightPixels - dpi(90f)).coerceAtLeast(0))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Fold / unfold: keep the bar on screen and the capture size in sync.
        clampPosition()
        root?.let { runCatching { wm.updateViewLayout(it, params) } }
        grabber?.onScreenChanged()
    }

    private fun renderVoice(state: VoiceState) {
        when (state) {
            is VoiceState.Listening -> {
                transcript.visibility = View.VISIBLE
                transcript.text = state.text.ifBlank { "Listening… speak now" }
                micView.setImageResource(R.drawable.ic_ov_stop)
                val s = 1f + state.level * 0.18f
                micBg.scaleX = s; micBg.scaleY = s
            }
            is VoiceState.Error -> {
                transcript.visibility = View.VISIBLE
                transcript.text = state.message
                resetMic()
                scope.launch { delay(3000); if (voice.state.value !is VoiceState.Listening) transcript.visibility = View.GONE }
            }
            VoiceState.Idle -> {
                transcript.visibility = View.GONE
                resetMic()
            }
        }
    }

    private fun resetMic() {
        micView.setImageResource(R.drawable.ic_ov_mic)
        micBg.scaleX = 1f; micBg.scaleY = 1f
    }

    // ------------------------------------------------------------------ Actions

    private fun onMicTapped() {
        if (voice.isListening) { voice.stop(); return }
        if (busy) return
        val g = grabber ?: return
        busy = true
        hideCard()
        scope.launch {
            val r = root ?: return@launch
            // Note goes at the height where the bar is, so drag the bar to where you want the note.
            val screenH = resources.displayMetrics.heightPixels.toFloat()
            shotY = ((params.y + r.height / 2f) / screenH).coerceIn(0.08f, 0.92f)
            r.alpha = 0f
            val shot = g.capture()
            r.alpha = 1f
            busy = false
            if (shot == null) {
                showMessage("Couldn't capture the screen")
                return@launch
            }
            pendingShot = shot
            voice.start(SettingsStore(this@OverlayService).loadSettings()) { text -> onSpoken(text) }
            // If nothing was said, drop the capture once listening ends.
            launch {
                voice.state.collect { if (it !is VoiceState.Listening) { delay(100); if (pendingShot === shot) pendingShot = null; cancel() } }
            }
        }
    }

    private fun onSpoken(text: String) {
        val shot = pendingShot ?: return
        pendingShot = null
        scope.launch {
            runCatching {
                val style = SettingsStore(this@OverlayService).loadDefaultStyle()
                val note = TextNote(text = text, x = 0.5f, y = shotY, style = style)
                val uri = Exporter.saveScreenshot(this@OverlayService, shot, text)
                NoteStore.get(this@OverlayService).save(uri, listOf(note))
                val preview = withContext(Dispatchers.Default) { previewOf(shot, note) }
                lastCapture = uri
                showCard(preview, text)
            }.onFailure { showMessage("Couldn't save: ${it.message}") }
        }
    }

    private fun previewOf(shot: Bitmap, note: TextNote): Bitmap {
        val w = 360
        val h = (shot.height * w.toFloat() / shot.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(shot, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        NoteRenderer.draw(Canvas(small), this, note, w.toFloat(), h.toFloat())
        return small
    }

    private fun showCard(preview: Bitmap, text: String) {
        cardImage.setImageBitmap(preview)
        cardText.text = "✓ Note added\n" + text.take(40) + if (text.length > 40) "…" else ""
        card.visibility = View.VISIBLE
        cardJob?.cancel()
        cardJob = scope.launch { delay(5000); hideCard() }
    }

    private fun hideCard() {
        cardJob?.cancel()
        card.visibility = View.GONE
    }

    private fun showMessage(msg: String) {
        transcript.visibility = View.VISIBLE
        transcript.text = msg
        scope.launch { delay(2500); if (!voice.isListening) transcript.visibility = View.GONE }
    }

    private fun undoLast() {
        val uri = lastCapture ?: return
        lastCapture = null
        hideCard()
        scope.launch {
            withContext(Dispatchers.IO) {
                NoteStore.get(this@OverlayService).save(uri, emptyList())
                runCatching { contentResolver.delete(uri, null, null) }
            }
            showMessage("Removed")
        }
    }

    private fun openInApp(uri: Uri?) {
        hideCard()
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (uri != null) i.setAction(MainActivity.ACTION_OPEN_CAPTURE).setData(uri)
        startActivity(i)
    }

    private fun shutdown() {
        voice.release()
        grabber?.stop()
        grabber = null
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_running.value) shutdown()
        scope.cancel()
        super.onDestroy()
    }
}
