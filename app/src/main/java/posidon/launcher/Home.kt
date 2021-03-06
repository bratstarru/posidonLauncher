package posidon.launcher

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.*
import android.graphics.drawable.shapes.RoundRectShape
import android.os.*
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import posidon.launcher.external.Kustom
import posidon.launcher.external.Widget
import posidon.launcher.feed.notifications.NotificationService
import posidon.launcher.items.users.AppLoader
import posidon.launcher.items.users.DrawerAdapter
import posidon.launcher.items.users.ItemLongPress
import posidon.launcher.items.users.SectionedDrawerAdapter
import posidon.launcher.search.SearchActivity
import posidon.launcher.storage.Settings
import posidon.launcher.tools.*
import posidon.launcher.tools.Tools.tryAnimate
import posidon.launcher.tools.Tools.updateNavbarHeight
import posidon.launcher.tools.drawable.FastBitmapDrawable
import posidon.launcher.tutorial.WelcomeActivity
import posidon.launcher.view.AlphabetScrollbar
import posidon.launcher.view.ResizableLayout
import posidon.launcher.view.drawer.BottomDrawerBehavior
import posidon.launcher.view.drawer.BottomDrawerBehavior.BottomSheetCallback
import posidon.launcher.view.drawer.BottomDrawerBehavior.STATE_EXPANDED
import posidon.launcher.view.drawer.DrawerView
import posidon.launcher.view.feed.Feed
import java.lang.ref.WeakReference
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class Home : AppCompatActivity() {

    val drawer by lazy { findViewById<DrawerView>(R.id.drawer) }

    val feed by lazy { findViewById<Feed>(R.id.feed) }

    private val drawerScrollBar by lazy { AlphabetScrollbar(drawer.drawerGrid) }

    private val dock by lazy { drawer.dock }

    private val blurBg = LayerDrawable(arrayOf<Drawable>(
            ColorDrawable(0x0),
            ColorDrawable(0x0),
            ColorDrawable(0x0),
            ColorDrawable(0x0)
    ))

    private val batteryInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            dock.battery.progress = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        }
    }

    private val onAppLoaderEnd = {
        val s = drawer.drawerGrid.scrollY
        if (Settings["drawer:sections_enabled", false]) {
            drawer.drawerGrid.adapter = SectionedDrawerAdapter(this)
            drawer.drawerGrid.onItemClickListener = null
            drawer.drawerGrid.onItemLongClickListener = null
        } else {
            drawer.drawerGrid.adapter = DrawerAdapter()
            drawer.drawerGrid.onItemClickListener = AdapterView.OnItemClickListener { _, v, i, _ -> Global.apps[i].open(this@Home, v) }
            drawer.drawerGrid.onItemLongClickListener = ItemLongPress.olddrawer(this@Home)
        }
        drawer.drawerGrid.scrollY = s
        drawerScrollBar.updateAdapter()
        setDock()
    }

    private lateinit var powerManager: PowerManager

    init { instance = this }
    companion object { lateinit var instance: Home private set }

    private fun setCustomizations() {

        fun setSearchHintText(text: String) {
            drawer.searchTxt.text = text
            dock.searchTxt.text = text
        }

        fun setDrawerSearchbarBGColor(color: Int) {
            val bg = ShapeDrawable()
            val tr = Settings["searchradius", 0].dp
            bg.shape = RoundRectShape(floatArrayOf(tr, tr, tr, tr, 0f, 0f, 0f, 0f), null, null)
            bg.paint.color = color
            drawer.searchBar.background = bg
        }

        fun setDrawerSearchbarRadius(radius: Int) {
            val bg = ShapeDrawable()
            val tr = radius.dp
            bg.shape = RoundRectShape(floatArrayOf(tr, tr, tr, tr, 0f, 0f, 0f, 0f), null, null)
            bg.paint.color = Settings["searchcolor", 0x33000000]
            drawer.searchBar.background = bg
        }

        fun setDrawerSearchbarFGColor(color: Int) {
            drawer.searchTxt.setTextColor(color)
            val searchIcon = drawer.searchIcon
            searchIcon.imageTintList = ColorStateList(arrayOf(intArrayOf(0)), intArrayOf(Settings["searchhintcolor", -0x1]))
        }

        fun setDrawerSearchBarVisible(visible: Boolean) {
            if (visible) {
                drawer.drawerGrid.setPadding(0, getStatusBarHeight(), 0, Tools.navbarHeight + 56.dp.toInt())
                drawer.searchBar.setPadding(0, 0, 0, Tools.navbarHeight)
                drawer.searchBar.visibility = VISIBLE
            } else {
                drawer.searchBar.visibility = GONE
                drawer.drawerGrid.setPadding(0, getStatusBarHeight(), 0, Tools.navbarHeight + 12.dp.toInt())
            }
        }

        fun setDockSearchbarBGColor(color: Int) {
            val bg = ShapeDrawable()
            val tr = Settings["dock:search:radius", 30].dp
            bg.shape = RoundRectShape(floatArrayOf(tr, tr, tr, tr, tr, tr, tr, tr), null, null)
            bg.paint.color = color
            dock.searchBar.background = bg
        }

        fun setDockSearchbarRadius(radius: Int) {
            val bg = ShapeDrawable()
            val tr = radius.dp
            bg.shape = RoundRectShape(floatArrayOf(tr, tr, tr, tr, tr, tr, tr, tr), null, null)
            bg.paint.color = Settings["docksearchcolor", -0x22000001]
            dock.searchBar.background = bg
        }

        fun setDockSearchbarFGColor(color: Int) {
            dock.searchTxt.setTextColor(color)
            val dickSearchIcon = dock.searchIcon
            dickSearchIcon.imageTintList = ColorStateList.valueOf(color)
            val battery = dock.battery
            battery.progressTintList = ColorStateList.valueOf(color)
            battery.indeterminateTintMode = PorterDuff.Mode.MULTIPLY
            battery.progressBackgroundTintList = ColorStateList.valueOf(color)
            battery.progressBackgroundTintMode = PorterDuff.Mode.MULTIPLY
            (dock.battery.progressDrawable as LayerDrawable).getDrawable(3).setTint(if (ColorTools.useDarkText(color)) -0x23000000 else -0x11000001)
        }

        fun setDockSearchBarVisible(visible: Boolean) {
            if (visible) {
                dock.searchBar.visibility = VISIBLE
                dock.battery.visibility = VISIBLE
            } else {
                dock.searchBar.visibility = GONE
                dock.battery.visibility = GONE
            }
        }

        fun setDockSearchbarBelowApps(isBelow: Boolean) {
            if (isBelow) {
                dock.searchBar.bringToFront()
            } else {
                dock.containerContainer.bringToFront()
            }
        }

        fun setDrawerScrollbarEnabled(enabled: Boolean) {
            if (enabled) {
                drawerScrollBar.visibility = VISIBLE
                (drawer.drawerGrid.layoutParams as FrameLayout.LayoutParams).marginEnd =
                        if (Settings["drawer:sections_enabled", false]) 0
                        else 24.dp.toInt()
                drawer.drawerGrid.layoutParams = drawer.drawerGrid.layoutParams
                drawerScrollBar.update()
            } else  {
                drawerScrollBar.visibility = GONE
                (drawer.drawerGrid.layoutParams as FrameLayout.LayoutParams).marginEnd = 0
                drawer.drawerGrid.layoutParams = drawer.drawerGrid.layoutParams
            }
        }

        fun setDockHorizontalMargin(margin: Int) {
            val m = margin.dp.toInt()
            dock.setPadding(m, 0, m, 0)
        }

        feed.update(this, drawer)

        dock.updateBG(drawer)

        setDockSearchBarVisible(Settings["docksearchbarenabled", false])
        setDockSearchbarBelowApps(Settings["dock:search:below_apps", true])
        setDockSearchbarBGColor(Settings["docksearchcolor", -0x22000001])
        setDockSearchbarFGColor(Settings["docksearchtxtcolor", -0x1000000])
        setDockSearchbarRadius(Settings["dock:search:radius", 30])
        setDockHorizontalMargin(Settings["dock:margin_x", 16])

        if (Global.shouldSetApps) AppLoader(this@Home, onAppLoaderEnd).execute() else {
            if (Settings["drawer:sections_enabled", false]) {
                drawer.drawerGrid.adapter = SectionedDrawerAdapter(this)
                drawer.drawerGrid.onItemClickListener = null
                drawer.drawerGrid.onItemLongClickListener = null
            } else {
                drawer.drawerGrid.adapter = DrawerAdapter()
                drawer.drawerGrid.onItemClickListener = AdapterView.OnItemClickListener { _, v, i, _ -> Global.apps[i].open(this@Home, v) }
                drawer.drawerGrid.onItemLongClickListener = ItemLongPress.olddrawer(this@Home)
            }
            setDock()
        }

        if (Settings["drawer:sections_enabled", false]) {
            drawer.drawerGrid.numColumns = 1
            drawer.drawerGrid.verticalSpacing = 0
        } else {
            drawer.drawerGrid.numColumns = Settings["drawer:columns", 4]
            drawer.drawerGrid.verticalSpacing = Settings["verticalspacing", 12].dp.toInt()
        }

        drawer.setLocked(!Settings["drawer:slide_up", true])

        applyFontSetting()

        if (Settings["hidestatus", false]) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        setDrawerSearchBarVisible(Settings["drawersearchbarenabled", true])
        setDrawerSearchbarBGColor(Settings["searchcolor", 0x33000000])
        setDrawerSearchbarFGColor(Settings["searchtxtcolor", -0x1])
        setDrawerSearchbarRadius(Settings["searchradius", 0])

        setSearchHintText(Settings["searchhinttxt", "Search.."])

        setDrawerScrollbarEnabled(Settings["drawer:scrollbar_enabled", false])

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Settings["gesture:back", ""] == "") {
                window.decorView.findViewById<View>(android.R.id.content).systemGestureExclusionRects = listOf(Rect(0, 0, Device.displayWidth, Device.displayHeight))
            } else {
                window.decorView.findViewById<View>(android.R.id.content).systemGestureExclusionRects = listOf()
            }
        }

        Global.shouldSetApps = false
        Global.customized = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tools.appContextReference = WeakReference(applicationContext)
        Settings.init(applicationContext)
        if (Settings["init", true]) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            exitProcess(0)
        }
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("posidonLauncher", "uncaught exception", throwable)
            Settings.applyNow()
            startActivity(Intent(this, StackTraceActivity::class.java).apply { putExtra("throwable", throwable) })
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
        Global.accentColor = Settings["accent", 0x1155ff] or -0x1000000
        Kustom["accent"] = Global.accentColor.toUInt().toString(16)

        updateNavbarHeight(this@Home)

        if (Settings["search:asHome", false]) {
            startActivity(Intent(this, SearchActivity::class.java))
            finish()
            return
        }

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).registerCallback(AppLoader.Callback(this, onAppLoaderEnd))

        setContentView(R.layout.main)

        registerReceiver(batteryInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        feed.apply {
            onTopOverScroll = {
                if (!LauncherMenu.isActive && drawer.state != BottomDrawerBehavior.STATE_EXPANDED) {
                    Gestures.performTrigger(Settings["gesture:feed:top_overscroll", Gestures.PULL_DOWN_NOTIFICATIONS])
                }
            }
            onBottomOverScroll = {
                if (!LauncherMenu.isActive) {
                    Gestures.performTrigger(Settings["gesture:feed:bottom_overscroll", Gestures.OPEN_APP_DRAWER])
                }
            }
        }

        drawer.run {
            init()
            addCallback(object : BottomSheetCallback() {

                val things = IntArray(5)
                val radii = FloatArray(8)
                val colors = IntArray(3)
                val floats = FloatArray(1)

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomDrawerBehavior.STATE_COLLAPSED -> {
                            if (this@Home.hasWindowFocus() && Settings["kustom:variables:enable", false]) {
                                Kustom["screen"] = "home"
                            }
                            drawerGrid.smoothScrollToPositionFromTop(0, 0, 0)
                            colors[0] = Settings["dock:background_color", -0x78000000] and 0x00ffffff
                            colors[1] = Settings["dock:background_color", -0x78000000]

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Settings["gesture:back", ""] == "") {
                                window.decorView.findViewById<View>(android.R.id.content).systemGestureExclusionRects = listOf(Rect(0, 0, Device.displayWidth, Device.displayHeight))
                            }
                            val tr = Settings["dockradius", 30].dp
                            radii[0] = tr
                            radii[1] = tr
                            radii[2] = tr
                            radii[3] = tr
                        }
                        BottomDrawerBehavior.STATE_EXPANDED -> {
                            if (Settings["kustom:variables:enable", false]) {
                                Kustom["screen"] = "drawer"
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                window.decorView.findViewById<View>(android.R.id.content).systemGestureExclusionRects = listOf()
                            }
                        }
                    }
                    ItemLongPress.currentPopup?.dismiss()
                    floats[0] = dock.dockHeight.toFloat() / (Device.displayHeight + dock.dockHeight)
                    things[0] = if (Tools.canBlurDrawer) Settings["blurLayers", 1] else 0
                    things[1] = Settings["dock:background_color", -0x78000000]
                    things[2] = Settings["dock:background_type", 0]
                    colors[2] = Settings["drawer:background_color", -0x78000000]
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val inverseOffset = 1 - slideOffset
                    drawerGrid.alpha = slideOffset
                    drawerScrollBar.alpha = slideOffset
                    feed.alpha = inverseOffset.pow(1.2f)
                    if (slideOffset >= 0) {
                        try {
                            val bg = drawer.background
                            when (things[2]) {
                                0 -> {
                                    bg as ShapeDrawable
                                    bg.paint.color = ColorTools.blendColors(colors[2], things[1], slideOffset)
                                    bg.shape = RoundRectShape(radii, null, null)
                                }
                                1 -> {
                                    bg as LayerDrawable
                                    colors[1] = ColorTools.blendColors(colors[2], things[1], slideOffset)
                                    (bg.getDrawable(0) as GradientDrawable).colors = intArrayOf(colors[0], colors[1])
                                    (bg.getDrawable(1) as GradientDrawable).colors = intArrayOf(colors[1], colors[2])
                                }
                                2, 3 -> {
                                    bg as ShapeDrawable
                                    bg.paint.color = colors[2] and 0xffffff or (((colors[2] ushr 24).toFloat() * slideOffset).toInt() shl 24)
                                    bg.shape = RoundRectShape(radii, null, null)
                                }
                            }
                            val blurLayers = things[0]
                            if (blurLayers != 0) {
                                val repetitive = (slideOffset * 255).roundToInt() * blurLayers
                                for (i in 0 until blurLayers) {
                                    blurBg.getDrawable(i).alpha = max(min(repetitive - (i shl 8) + i, 255), 0)
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    } else if (!Settings["feed:show_behind_dock", false]) {
                        (feed.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = ((1 + slideOffset) * (dock.dockHeight + Tools.navbarHeight + (Settings["dockbottompadding", 10] - 18).dp)).toInt()
                        feed.requestLayout()
                    }
                    dock.alpha = inverseOffset
                }
            })
        }

        drawer.drawerContent.addView(drawerScrollBar)
        (drawerScrollBar.layoutParams as FrameLayout.LayoutParams).apply {
            width = 24.dp.toInt()
            gravity = Gravity.END
        }
        drawerScrollBar.bringToFront()

        setCustomizations()
        feed.loadNews(this)

        findViewById<View>(R.id.homeView).setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP && drawer.state == BottomDrawerBehavior.STATE_COLLAPSED)
                WallpaperManager.getInstance(this@Home).sendWallpaperCommand(
                    v.windowToken,
                    WallpaperManager.COMMAND_TAP,
                    event.x.toInt(),
                    event.y.toInt(),
                    0, null)
            false
        }
        if (Settings["mnmlstatus", false]) {
            window.decorView.systemUiVisibility =
                SYSTEM_UI_FLAG_LAYOUT_STABLE or
                SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                SYSTEM_UI_FLAG_LOW_PROFILE
        } else {
            window.decorView.systemUiVisibility =
                SYSTEM_UI_FLAG_LAYOUT_STABLE or
                SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        findViewById<ImageView>(R.id.blur).setImageDrawable(blurBg)

        System.gc()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val w = Widget.handleActivityResult(this, requestCode, resultCode, data)
        if (w != null) {
            feed.add(w)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onUpdate()
        setDock()
    }

    override fun onResume() {
        super.onResume()
        if (Settings["search:asHome", false]) {
            startActivity(Intent(this, SearchActivity::class.java))
            finish()
            return
        }
        if (Settings["kustom:variables:enable", false]) {
            Kustom["screen"] = "home"
        }
        overridePendingTransition(R.anim.home_enter, R.anim.appexit)
        onUpdate()
        feed.onResume(this)
    }

    private fun onUpdate() {
        if (!Settings["search:asHome", false]) {
            val tmp = Tools.navbarHeight
            updateNavbarHeight(this)
            if (Tools.canBlurDrawer) {
                val shouldHide = drawer.state == BottomDrawerBehavior.STATE_COLLAPSED || drawer.state == BottomDrawerBehavior.STATE_HIDDEN
                thread(isDaemon = true) {
                    val blurLayers = Settings["blurLayers", 1]
                    val radius = Settings["drawer:blur:rad", 15f]
                    for (i in 0 until blurLayers) {
                        val bmp = Tools.blurredWall(radius / blurLayers * (i + 1))
                        val bd = FastBitmapDrawable(bmp)
                        if (shouldHide) bd.alpha = 0
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) blurBg.setDrawable(i, bd)
                        else {
                            blurBg.setId(i, i)
                            blurBg.setDrawableByLayerId(i, bd)
                        }
                    }
                }
            }
            if (Global.customized || tmp != Tools.navbarHeight) {
                setCustomizations()
            } else if (!powerManager.isPowerSaveMode && Settings["animatedicons", true]) {
                for (app in Global.apps) {
                    tryAnimate(app.icon!!)
                }
            }
            if (Settings["news:load_on_resume", true]) {
                feed.loadNews(this)
            }
            if (feed.notifications != null || Settings["notif:badges", true]) {
                NotificationService.onUpdate()
            }
        }
        if (!powerManager.isPowerSaveMode && Settings["animatedicons", true]) {
            for (app in Global.apps) {
                tryAnimate(app.icon!!)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (LauncherMenu.isActive) {
            LauncherMenu.dialog!!.dismiss()
        }
        drawer.state = BottomDrawerBehavior.STATE_COLLAPSED
        if (!Settings["feed:keep_pos", false]) {
            feed.scroll.scrollTo(0, 0)
        }
        if (Settings["kustom:variables:enable", false]) {
            Kustom["screen"] = "?"
        }
        feed.onPause()
    }

    override fun onStart() {
        super.onStart()

        if (Settings["drawer:sections_enabled", false]) {
            drawer.drawerGrid.adapter = SectionedDrawerAdapter(this)
            drawer.drawerGrid.onItemClickListener = null
            drawer.drawerGrid.onItemLongClickListener = null
        } else {
            drawer.drawerGrid.adapter = DrawerAdapter()
            drawer.drawerGrid.onItemClickListener = AdapterView.OnItemClickListener { _, v, i, _ -> Global.apps[i].open(this@Home, v) }
            drawer.drawerGrid.onItemLongClickListener = ItemLongPress.olddrawer(this@Home)
        }
        drawerScrollBar.updateAdapter()
    }

    override fun onDestroy() {
        runCatching {
            unregisterReceiver(batteryInfoReceiver)
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!Settings["search:asHome", false]) {
            if (hasFocus) {
                if (feed.notifications != null || Settings["notif:badges", true]) {
                    try { startService(Intent(this, NotificationService::class.java)) }
                    catch (e: Exception) {}
                }
                if (Settings["mnmlstatus", false]) window.decorView.systemUiVisibility =
                        SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        SYSTEM_UI_FLAG_LOW_PROFILE
                if (Global.shouldSetApps) {
                    AppLoader(this@Home, onAppLoaderEnd).execute()
                }
                if (Settings["kustom:variables:enable", false]) {
                    if (drawer.state == STATE_EXPANDED) {
                        Kustom["screen"] = "drawer"
                    } else {
                        Kustom["screen"] = "home"
                    }
                }
            } else {
                window.decorView.systemUiVisibility =
                    SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        }
    }

    override fun onBackPressed() = when {
        drawer.state == BottomDrawerBehavior.STATE_EXPANDED -> drawer.state = BottomDrawerBehavior.STATE_COLLAPSED
        ResizableLayout.currentlyResizing != null -> ResizableLayout.currentlyResizing?.resizing = false
        else -> Gestures.performTrigger(Settings["gesture:back", ""])
    }

    fun openSearch(v: View) = SearchActivity.open(this)

    fun setDock() = dock.loadApps(drawer, feed, feed.desktopContent, this)
}