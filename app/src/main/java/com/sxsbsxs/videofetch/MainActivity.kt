package com.sxsbsxs.videofetch

import android.Manifest
import android.graphics.BitmapFactory
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)
    private var pendingDownloadUrl: String? = null
    private var pendingFormatSelector: String = ""
    private var pendingAudioOnly: Boolean = false
    private var pendingFormatLabel: String = ""

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pending = pendingDownloadUrl
            val pendingFormat = pendingFormatSelector
            val pendingAudioOnlyValue = pendingAudioOnly
            val pendingLabel = pendingFormatLabel
            pendingDownloadUrl = null
            pendingFormatSelector = ""
            pendingAudioOnly = false
            pendingFormatLabel = ""
            if (granted && !pending.isNullOrBlank()) {
                DownloadService.start(this, pending, pendingFormat, pendingAudioOnlyValue, pendingLabel)
            } else if (!granted) {
                Toast.makeText(this, "需要存储权限才能写入系统下载目录", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.mark(this, "MainActivity.onCreate entered")

        // Show the previous crash before running Compose again. This fallback
        // uses only the platform AlertDialog and remains usable even when the
        // Compose UI itself is what crashed.
        if (CrashReporter.hasPreviousCrash(this)) {
            val previousCrash = CrashReporter.readPreviousCrash(this)
            val wasKnownAdaptiveIconCrash =
                previousCrash.contains("Only VectorDrawables and rasterized asset types are supported") &&
                previousCrash.contains("PainterResources_androidKt.loadVectorResource") &&
                previousCrash.contains("MainActivityKt")
            if (wasKnownAdaptiveIconCrash) {
                // This report is the confirmed launcher-icon crash fixed in this build.
                CrashReporter.clearPreviousCrash(this)
            } else {
                showPreviousCrashReport()
                return
            }
        }

        CrashReporter.mark(this, "enableEdgeToEdge")
        enableEdgeToEdge()
        sharedUrl = extractHttpUrl(sharedText(intent))

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        CrashReporter.mark(this, "MainActivity.setContent dispatched")
        setContent {
            androidx.compose.runtime.SideEffect {
                CrashReporter.mark(this@MainActivity, "Compose initial composition committed")
            }
            VideoFetchApp(
                initialUrl = sharedUrl,
                onDownload = ::startDownload
            )
        }
    }

    private fun showPreviousCrashReport() {
        val report = CrashReporter.readPreviousCrash(this)
        val visibleReport = if (report.length > 10000) {
            report.take(10000) + "\n[日志过长，已截断显示]"
        } else report

        android.app.AlertDialog.Builder(this)
            .setTitle("video-dl 上次启动异常")
            .setMessage(
                "检测到应用上次意外退出。请把下面的日志分享出来，我就能按真实异常定位。\n" +
                    visibleReport
            )
            .setPositiveButton("分享崩溃日志") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "video-dl crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                startActivity(Intent.createChooser(send, "分享崩溃日志"))
            }
            .setNegativeButton("清除日志并重试") { _, _ ->
                CrashReporter.clearPreviousCrash(this)
                recreate()
            }
            .setNeutralButton("暂不处理", null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = extractHttpUrl(sharedText(intent))
        if (!url.isNullOrBlank()) {
            sharedUrl = url
            Toast.makeText(this, "链接已填入，点击开始下载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownload(url: String, formatSelector: String, audioOnly: Boolean, formatLabel: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadUrl = url
            pendingFormatSelector = formatSelector
            pendingAudioOnly = audioOnly
            pendingFormatLabel = formatLabel
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        DownloadService.start(this, url, formatSelector, audioOnly, formatLabel)
    }

    private fun sharedText(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.dataString
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }

    private fun extractHttpUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val candidate = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
            .find(text)?.value
            ?.trimEnd('.', ',', ';', '!', '。', '，', '；', ')', '）', ']', '】')
            ?: return null
        return candidate.takeIf { isHttpUrl(it) }
    }
}

private data class DownloadHistoryItem(
    val fileName: String,
    val source: String,
    val time: String,
    val statusLabel: String,
    val location: String,
    val message: String,
    val formatLabel: String
)

private fun parseDownloadHistory(json: String): List<DownloadHistoryItem> {
    return runCatching {
        val array = org.json.JSONArray(json)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val status = item.optString("status")
                val statusLabel = when (status) {
                    "completed" -> "已完成"
                    "saved_private" -> "已完成（应用目录）"
                    "failed" -> "失败"
                    "canceled" -> "已取消"
                    else -> status
                }
                add(
                    DownloadHistoryItem(
                        fileName = item.optString("fileName"),
                        source = item.optString("source"),
                        time = formatter.format(Date(item.optLong("timestamp"))),
                        statusLabel = statusLabel,
                        location = item.optString("location"),
                        message = item.optString("message"),
                        formatLabel = item.optString("formatLabel")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun isHttpUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)

private val VideoFetchLightColors = lightColorScheme(
    primary = Color(0xFF006B7B), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5F1F3), onPrimaryContainer = Color(0xFF002027),
    secondary = Color(0xFF47636A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8EB), onSecondaryContainer = Color(0xFF102D33),
    tertiary = Color(0xFF356A9A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5E7FF), onTertiaryContainer = Color(0xFF071E35),
    background = Color(0xFFF4F8FA), onBackground = Color(0xFF151D22),
    surface = Color(0xFFFBFEFF), onSurface = Color(0xFF151D22),
    surfaceVariant = Color(0xFFE1EAED), onSurfaceVariant = Color(0xFF526166),
    surfaceContainerLow = Color(0xFFF0F5F7),
    outline = Color(0xFF718187), outlineVariant = Color(0xFFC7D3D7),
    error = Color(0xFFBA1A1A)
)

private val VideoFetchDarkColors = darkColorScheme(
    primary = Color(0xFF78D9E5), onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF004F59), onPrimaryContainer = Color(0xFFB9F2F7),
    secondary = Color(0xFFB5CCD0), onSecondary = Color(0xFF20363B),
    secondaryContainer = Color(0xFF354B50), onSecondaryContainer = Color(0xFFD1E8EC),
    tertiary = Color(0xFFA8CCF8), onTertiary = Color(0xFF143354),
    tertiaryContainer = Color(0xFF254A70), onTertiaryContainer = Color(0xFFD4E7FF),
    background = Color(0xFF0C141B), onBackground = Color(0xFFE2EBEF),
    surface = Color(0xFF111C24), onSurface = Color(0xFFE2EBEF),
    surfaceVariant = Color(0xFF3E5058), onSurfaceVariant = Color(0xFFB9C8CE),
    surfaceContainerLow = Color(0xFF172630),
    outline = Color(0xFF899BA2), outlineVariant = Color(0xFF3E5058),
    error = Color(0xFFFFB4AB)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoFetchApp(initialUrl: String?, onDownload: (String, String, Boolean, String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("videofetch_settings", Context.MODE_PRIVATE) }
    var themeMode by rememberSaveable {
        mutableStateOf(preferences.getString("theme_mode", "system") ?: "system")
    }
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    // Match Seal's Material You behavior on Android 12+, with a stable
    // indigo fallback for older Android versions.
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> VideoFetchDarkColors
        else -> VideoFetchLightColors
    }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var hasCookieJar by remember { mutableStateOf(SecureCookieStore.hasCookieJar(context)) }
    var authorizedSites by remember { mutableStateOf(SecureCookieStore.listAuthorizedSites(context)) }
    var historyJson by remember { mutableStateOf(DownloadHistoryStore.read(context)) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var historyFilter by rememberSaveable { mutableStateOf("all") }
    val navigateFromDrawer: (Int) -> Unit = { destination ->
        if (destination == 1) historyJson = DownloadHistoryStore.read(context)
        if (destination == 2) {
            authorizedSites = SecureCookieStore.listAuthorizedSites(context)
            hasCookieJar = SecureCookieStore.hasCookieJar(context)
        }
        if (destination == 4) settingsReturnTab = selectedTab
        selectedTab = destination
        drawerScope.launch { drawerState.close() }
    }

    var audioOnly by rememberSaveable { mutableStateOf(preferences.getBoolean("audio_only", false)) }
    var maxHeight by rememberSaveable { mutableStateOf(preferences.getInt("max_height", 0)) }
    var selectedVideoFormatId by rememberSaveable { mutableStateOf("") }
    var selectedAudioFormatId by rememberSaveable { mutableStateOf("") }
    var downloadSubtitles by rememberSaveable { mutableStateOf(preferences.getBoolean("download_subtitles", false)) }
    var downloadPlaylist by rememberSaveable { mutableStateOf(preferences.getBoolean("download_playlist", false)) }
    var videoContainer by rememberSaveable { mutableStateOf(preferences.getString("video_container", "mp4") ?: "mp4") }
    var audioFormat by rememberSaveable { mutableStateOf(preferences.getString("audio_format", "mp3") ?: "mp3") }
    var subtitleLanguages by rememberSaveable { mutableStateOf(preferences.getString("subtitle_languages", "all") ?: "all") }
    var embedThumbnail by rememberSaveable { mutableStateOf(preferences.getBoolean("embed_thumbnail", true)) }
    var skipExisting by rememberSaveable { mutableStateOf(preferences.getBoolean("skip_existing", false)) }
    var concurrentFragments by rememberSaveable { mutableStateOf(preferences.getInt("concurrent_fragments", 4)) }
    var autoUpdateEngine by rememberSaveable {
        // Migrate existing installs away from automatic native engine startup.
        // Users can opt back in from Settings after confirming the app runs reliably.
        if (!preferences.getBoolean("engine_auto_update_default_migrated_v1", false)) {
            preferences.edit()
                .putBoolean("auto_update_engine", false)
                .putBoolean("engine_auto_update_default_migrated_v1", true)
                .apply()
        }
        mutableStateOf(preferences.getBoolean("auto_update_engine", false))
    }
    var autoUpdateWifiOnly by rememberSaveable {
        mutableStateOf(preferences.getBoolean("auto_update_wifi_only", true))
    }

    var previewLoading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<VideoInfoService.Preview?>(null) }
    var previewError by remember { mutableStateOf("") }
    var engineUpdating by remember { mutableStateOf(false) }
    var showFormatSheet by rememberSaveable { mutableStateOf(false) }
    var showInputSheet by rememberSaveable { mutableStateOf(false) }
    var queueFilter by rememberSaveable { mutableStateOf("all") }
    var showAdvancedDownloadSettings by rememberSaveable { mutableStateOf(false) }
    var settingsReturnTab by rememberSaveable { mutableStateOf(0) }
    val download by DownloadState.state.collectAsState()
    val inputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authorizedSites = SecureCookieStore.listAuthorizedSites(context)
        hasCookieJar = SecureCookieStore.hasCookieJar(context)
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "授权已保存，正在重新获取可用画质…", Toast.LENGTH_SHORT).show()
            val target = url.trim()
            if (isHttpUrl(target)) {
                previewLoading = true
                showFormatSheet = false
                VideoInfoService.inspect(context, target, object : VideoInfoService.Callback {
                    override fun onSuccess(result: VideoInfoService.Preview) {
                        previewLoading = false
                        if (url.trim() == target) {
                            selectedVideoFormatId = ""
                            selectedAudioFormatId = bestAudioFormat(result.formats)?.formatId.orEmpty()
                            maxHeight = 0
                            preferences.edit().putInt("max_height", 0).apply()
                            preview = result
                            previewError = ""
                            showFormatSheet = true
                        }
                    }

                    override fun onError(message: String) {
                        previewLoading = false
                        previewError = message
                        Toast.makeText(context, "重新解析失败：" + message, Toast.LENGTH_LONG).show()
                    }
                })
            }
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            url = initialUrl
            showInputSheet = true
            maxHeight = 0
            selectedVideoFormatId = ""
            selectedAudioFormatId = ""
            preferences.edit().putInt("max_height", 0).apply()
        }
    }

    LaunchedEffect(
        themeMode, audioOnly, maxHeight, downloadSubtitles, downloadPlaylist,
        videoContainer, audioFormat, subtitleLanguages, embedThumbnail,
        skipExisting, concurrentFragments, autoUpdateEngine, autoUpdateWifiOnly
    ) {
        preferences.edit()
            .putString("theme_mode", themeMode)
            .putBoolean("audio_only", audioOnly)
            .putInt("max_height", maxHeight)
            .putBoolean("download_subtitles", downloadSubtitles)
            .putBoolean("download_playlist", downloadPlaylist)
            .putString("video_container", videoContainer)
            .putString("audio_format", audioFormat)
            .putString("subtitle_languages", subtitleLanguages.trim().ifBlank { "all" })
            .putBoolean("embed_thumbnail", embedThumbnail)
            .putBoolean("skip_existing", skipExisting)
            .putInt("concurrent_fragments", concurrentFragments)
            .putBoolean("auto_update_engine", autoUpdateEngine)
            .putBoolean("auto_update_wifi_only", autoUpdateWifiOnly)
            .apply()
    }

    LaunchedEffect(autoUpdateEngine, autoUpdateWifiOnly, download.running, previewLoading) {
        if (autoUpdateEngine) {
            delay(5_000)
            val started = EngineUpdateManager.updateIfDue(context, autoUpdateWifiOnly) { message ->
                engineUpdating = false
                if (message.isNotBlank()) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
            engineUpdating = started
        }
    }

    val pasteUrl: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        val pastedUrl = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
            .find(clipText.orEmpty())?.value
            ?.trimEnd('.', ',', ';', '!', '。', '，', '；', ')', '）', ']', '】')
        if (!pastedUrl.isNullOrBlank() && isHttpUrl(pastedUrl)) {
            url = pastedUrl
            maxHeight = 0
            preferences.edit().putString("last_input_url", pastedUrl).putInt("max_height", 0).apply()
            preview = null
            previewError = ""
            selectedVideoFormatId = ""
            selectedAudioFormatId = ""
            showFormatSheet = false
        } else {
            Toast.makeText(context, "剪贴板里没有找到视频链接", Toast.LENGTH_SHORT).show()
        }
    }

    val startPreview: () -> Unit = {
        val value = url.trim()
        if (!isHttpUrl(value)) {
            Toast.makeText(context, "先粘贴有效的视频链接", Toast.LENGTH_SHORT).show()
        } else if (engineUpdating || EngineUpdateManager.isUpdating()) {
            Toast.makeText(context, "当前任务进行中，暂不能解析", Toast.LENGTH_SHORT).show()
        } else {
            previewLoading = true
            preview = null
            previewError = ""
            selectedVideoFormatId = ""
            selectedAudioFormatId = ""
            maxHeight = 0
            preferences.edit().putInt("max_height", 0).apply()
            VideoInfoService.inspect(context, value, object : VideoInfoService.Callback {
                override fun onSuccess(result: VideoInfoService.Preview) {
                    previewLoading = false
                    if (url.trim() == value) {
                        val defaultAudio = bestAudioFormat(result.formats)
                        selectedVideoFormatId = ""
                        selectedAudioFormatId = defaultAudio?.formatId.orEmpty()
                        maxHeight = 0
                        preferences.edit().putInt("max_height", 0).apply()
                        preview = result
                        showInputSheet = false
                        showFormatSheet = true
                    }
                }
                override fun onError(message: String) {
                    previewLoading = false
                    if (url.trim() == value) previewError = message
                }
            })
        }
    }

    val startDownload: () -> Unit = {
        val value = url.trim()
        val selectedVideo = preview?.formats?.firstOrNull { it.formatId == selectedVideoFormatId && it.hasVideo }
        val selectedAudio = preview?.formats?.firstOrNull { it.formatId == selectedAudioFormatId && it.hasAudio }
        val formatSelector = when {
            audioOnly -> selectedAudio?.formatId.orEmpty()
            selectedVideo == null -> ""
            selectedVideo.hasAudio -> selectedVideo.formatId
            selectedAudio != null -> selectedVideo.formatId + "+" + selectedAudio.formatId
            else -> selectedVideo.formatId + "+bestaudio"
        }
        val formatLabel = when {
            audioOnly -> "仅音频 · " + (selectedAudio?.label ?: "自动选择最佳")
            selectedVideo == null -> "视频 · 自动选择最佳"
            selectedVideo.hasAudio -> selectedVideo.label + "（含音频）"
            selectedAudio != null -> selectedVideo.label + " + " + selectedAudio.label
            else -> selectedVideo.label + " + 自动音频"
        }
        if (engineUpdating || EngineUpdateManager.isUpdating() || previewLoading) {
            Toast.makeText(context, "请等待当前操作完成", Toast.LENGTH_SHORT).show()
        } else if (!isHttpUrl(value)) {
            Toast.makeText(context, "请输入有效的视频链接", Toast.LENGTH_SHORT).show()
        } else {
            preferences.edit().putString("last_input_url", value).apply()
            // A parsed video keeps its explicit source-backed quality selection.
            // Direct downloads without a preview fall back to yt-dlp's best format.
            if (preview == null) {
                maxHeight = 0
                preferences.edit().putInt("max_height", 0).apply()
            }
            showFormatSheet = false
            onDownload(value, formatSelector, audioOnly, formatLabel)
            url = ""
            preview = null
            previewError = ""
            selectedVideoFormatId = ""
            selectedAudioFormatId = ""
            // Keep the user on the download form so progress remains visible,
            // matching bilibilidl's inline status/progress workflow.
            selectedTab = 0
        }
    }

    val openLogin: () -> Unit = {
        val target = url.trim().ifBlank { preferences.getString("last_input_url", "").orEmpty() }
        if (!isHttpUrl(target)) {
            Toast.makeText(context, "先粘贴一个目标网站的视频链接", Toast.LENGTH_SHORT).show()
        } else {
            loginLauncher.launch(
                Intent(context, LoginActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_TARGET_URL, target)
            )
        }
    }

    MaterialTheme(colorScheme = colors) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = selectedTab != 4,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 16.dp, top = 36.dp, bottom = 26.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_brand_mark),
                                contentDescription = "video-dl",
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "video-dl",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "视频 / 音频下载器",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        NavigationDrawerItem(
                            label = { Text("下载队列") },
                            selected = selectedTab == 0,
                            onClick = { navigateFromDrawer(0) },
                            icon = { Icon(Icons.Default.Download, contentDescription = null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("下载记录") },
                            selected = selectedTab == 1,
                            onClick = { navigateFromDrawer(1) },
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("任务列表") },
                            selected = selectedTab == 3,
                            onClick = { navigateFromDrawer(3) },
                            icon = { Icon(Icons.Default.List, contentDescription = null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "账户与偏好",
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        NavigationDrawerItem(
                            label = { Text("网站登录与 Cookie") },
                            selected = selectedTab == 2,
                            onClick = { navigateFromDrawer(2) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("设置") },
                            selected = selectedTab == 4,
                            onClick = { navigateFromDrawer(4) },
                            icon = { Icon(Icons.Default.Tune, contentDescription = null) }
                        )
                        Text(
                            "video-dl · yt-dlp",
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 24.dp, bottom = 20.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    val pageTitle = when (selectedTab) {
                        0 -> "下载队列"
                        1 -> "下载记录"
                        2 -> "网站授权"
                        3 -> "任务列表"
                        else -> "设置"
                    }
                    val navigation: @Composable () -> Unit = {
                        if (selectedTab == 4) {
                            IconButton(onClick = { selectedTab = settingsReturnTab }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                        } else {
                            IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "打开导航菜单")
                            }
                        }
                    }
                    if (selectedTab == 1 || selectedTab == 2 || selectedTab == 4) {
                        LargeTopAppBar(
                            navigationIcon = navigation,
                            title = {
                                Text(pageTitle, fontWeight = FontWeight.Medium)
                            }
                        )
                    } else {
                        TopAppBar(
                            navigationIcon = navigation,
                            title = {
                                Text(
                                    pageTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        )
                    }
                },
                floatingActionButton = {
                    if (selectedTab == 0) {
                        androidx.compose.material3.FloatingActionButton(
                            onClick = {
                                url = ""
                                preview = null
                                previewError = ""
                                selectedVideoFormatId = ""
                                selectedAudioFormatId = ""
                                showInputSheet = true
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "新建下载")
                        }
                    }
                }
            ) { padding ->
            when (selectedTab) {
                0 -> {
                    val showRunning = queueFilter == "all" || queueFilter == "downloading"
                    val showQueued = queueFilter == "all" || queueFilter == "queued"
                    val showFinished = queueFilter == "all" || queueFilter == "finished"
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "all" to "全部",
                                    "downloading" to "下载中",
                                    "queued" to "等待中",
                                    "finished" to "最近完成"
                                ).forEach { (value, label) ->
                                    FilterChip(
                                        selected = queueFilter == value,
                                        onClick = { queueFilter = value },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        if (download.running && showRunning) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(13.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                modifier = Modifier.size(46.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Icon(
                                                    Icons.Default.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(11.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(
                                                    if (download.audioOnly) "正在下载音频" else "正在下载视频",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    if (download.progressKnown) "下载进度" else "正在连接媒体源",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (download.progressKnown) {
                                                Text(
                                                    "${download.progress}%",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(22.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                        if (download.formatLabel.isNotBlank()) {
                                            Text(
                                                download.formatLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (download.progressKnown) {
                                            LinearProgressIndicator(
                                                progress = { download.progress / 100f },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                        Text(
                                            download.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { DownloadService.cancel(context) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp)
                                            ) { Text("取消任务") }
                                            if (download.queuedCount > 0) {
                                                TextButton(
                                                    onClick = { DownloadService.clearQueue(context) },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text("清空等待项") }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (download.running && showQueued && download.queuedLabels.isNotEmpty()) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "等待队列",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${download.queuedCount} 项",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(download.queuedLabels.take(12)) { label ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Icon(
                                                Icons.Default.History,
                                                null,
                                                modifier = Modifier.padding(8.dp).size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            label,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "等待中",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (!download.running && showFinished && download.lastUrl.isNotBlank()) {
                            item {
                                val completed = download.progress == 100
                                val failed = download.message.startsWith("下载失败")
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                modifier = Modifier.size(44.dp),
                                                shape = RoundedCornerShape(15.dp),
                                                color = if (failed) MaterialTheme.colorScheme.errorContainer
                                                    else MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Icon(
                                                    if (completed) Icons.Default.CheckCircle else Icons.Default.History,
                                                    null,
                                                    modifier = Modifier.padding(11.dp),
                                                    tint = if (failed) MaterialTheme.colorScheme.onErrorContainer
                                                        else MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    when {
                                                        failed -> "上次下载失败"
                                                        completed -> "最近下载已完成"
                                                        else -> "上次任务已结束"
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    download.formatLabel.ifBlank { download.message },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (completed) {
                                                Text(
                                                    "100%",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            download.lastUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (download.outputPath.isNotBlank()) {
                                            Text(
                                                download.outputPath,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                historyJson = DownloadHistoryStore.read(context)
                                                selectedTab = 1
                                            },
                                            modifier = Modifier.align(Alignment.End)
                                        ) { Text("查看文件记录") }
                                    }
                                }
                            }
                        }

                        val hasVisibleTask =
                            (download.running && (showRunning || (showQueued && download.queuedLabels.isNotEmpty()))) ||
                                (!download.running && showFinished && download.lastUrl.isNotBlank())
                        if (!hasVisibleTask) {
                            item {
                                EmptyState(
                                    icon = when (queueFilter) {
                                        "downloading" -> Icons.Default.Download
                                        "queued" -> Icons.Default.History
                                        "finished" -> Icons.Default.History
                                        else -> Icons.Default.Folder
                                    },
                                    title = when (queueFilter) {
                                        "downloading" -> "没有正在下载的任务"
                                        "queued" -> "没有等待中的任务"
                                        "finished" -> "还没有完成记录"
                                        else -> "下载队列还是空的"
                                    },
                                    description = when (queueFilter) {
                                        "downloading" -> "新建一个下载任务后，进度会显示在这里。"
                                        "queued" -> "添加多个链接后，后续任务会排在这里。"
                                        "finished" -> "最近完成或失败的任务会显示在这里。"
                                        else -> "把视频链接交给 VideoFetch，格式和画质会在解析后选择。"
                                    },
                                    action = "新建下载",
                                    onAction = {
                                        url = ""
                                        preview = null
                                        previewError = ""
                                        selectedVideoFormatId = ""
                                        selectedAudioFormatId = ""
                                        showInputSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
                3 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text(
                            "查看当前进度和等待中的下载",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (download.running) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                if (download.audioOnly) "正在下载音频" else "正在下载视频",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                if (download.queuedCount > 0) "还有 " + download.queuedCount + " 个等待任务" else "当前队列正在执行",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                            if (download.progressKnown) {
                                            Text(download.progress.toString() + "%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                    if (download.formatLabel.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                "本次格式 · " + download.formatLabel,
                                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (download.queuedLabels.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "等待队列",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(Modifier.weight(1f))
                                                Text(
                                                    download.queuedCount.toString() + " 项",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            download.queuedLabels.take(4).forEachIndexed { index, label ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        (index + 1).toString().padStart(2, '0'),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(Modifier.width(9.dp))
                                                    Text(
                                                        label,
                                                        modifier = Modifier.weight(1f),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (download.queuedLabels.size > 4) {
                                                Text(
                                                    "还有 " + (download.queuedLabels.size - 4) + " 项等待中",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (download.progressKnown) {
                                        LinearProgressIndicator(progress = { download.progress / 100f }, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    Text(download.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (download.outputPath.isNotBlank()) {
                                        Text("保存位置：" + download.outputPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { DownloadService.cancel(context) }, modifier = Modifier.weight(1f)) {
                                            Text("取消全部")
                                        }
                                        if (download.queuedCount > 0) {
                                            Button(onClick = { DownloadService.clearQueue(context) }, modifier = Modifier.weight(1f)) {
                                                Text("清空等待项")
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (download.lastUrl.isNotBlank()) {
                            val completed = download.progress == 100
                            val failed = download.message.startsWith("下载失败")
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(44.dp),
                                            shape = RoundedCornerShape(15.dp),
                                            color = if (failed) MaterialTheme.colorScheme.errorContainer
                                                else MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Icon(
                                                if (completed) Icons.Default.CheckCircle else Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.padding(11.dp),
                                                tint = if (failed) MaterialTheme.colorScheme.onErrorContainer
                                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                when {
                                                    completed -> "下载已完成"
                                                    failed -> "下载未成功"
                                                    else -> "任务已停止"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                download.formatLabel.ifBlank { "自动选择格式" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        download.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (failed) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (download.outputPath.isNotBlank()) {
                                        Text(
                                            "保存位置：" + download.outputPath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onDownload(
                                                download.lastUrl,
                                                download.formatSelector,
                                                download.audioOnly,
                                                download.formatLabel
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (completed) "再次下载" else "重试下载")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            url = download.lastUrl
                                            selectedTab = 0
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("重新解析并修改格式")
                                    }
                                }
                            }
                        } else {
                            EmptyState(
                                icon = Icons.Default.Download,
                                title = "目前没有下载任务",
                                description = "粘贴视频链接，选择格式后开始下载。",
                                action = "去粘贴视频链接",
                                onAction = { selectedTab = 0 }
                            )
                        }
                    }
                }

                1 -> {
                    val allHistoryItems = remember(historyJson) { parseDownloadHistory(historyJson) }
                    val historyItems = remember(allHistoryItems, historySearchQuery, historyFilter) {
                        val query = historySearchQuery.trim().lowercase(Locale.getDefault())
                        allHistoryItems.filter { item ->
                            val matchesFilter = when (historyFilter) {
                                "completed" -> item.statusLabel.contains("完成")
                                "failed" -> item.statusLabel.contains("失败")
                                "canceled" -> item.statusLabel.contains("取消")
                                else -> true
                            }
                            val matchesQuery = query.isBlank() ||
                                listOf(item.fileName, item.source, item.formatLabel, item.message)
                                    .any { it.lowercase(Locale.getDefault()).contains(query) }
                            matchesFilter && matchesQuery
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "显示 " + historyItems.size + " / " + allHistoryItems.size + " 条记录",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        DownloadHistoryStore.clear(context)
                                        historyJson = "[]"
                                    },
                                    enabled = allHistoryItems.isNotEmpty()
                                ) { Text("清空") }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = historySearchQuery,
                                onValueChange = { historySearchQuery = it },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                placeholder = { Text("搜索文件名、网站或格式") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (historySearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { historySearchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "清除搜索")
                                        }
                                    }
                                }
                            )
                            ChoiceChipRows(
                                options = listOf(
                                    "all" to "全部",
                                    "completed" to "已完成",
                                    "failed" to "失败"
                                ),
                                selected = historyFilter,
                                onSelected = { historyFilter = it }
                            )
                            Spacer(Modifier.height(9.dp))
                        }
                        if (historyItems.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.History,
                                    title = if (allHistoryItems.isEmpty()) "还没有下载记录" else "没有找到匹配记录",
                                    description = if (allHistoryItems.isEmpty())
                                        "完成或失败的任务会保存在这里。"
                                    else "试试修改搜索内容或切换状态筛选。",
                                    action = if (allHistoryItems.isEmpty()) "开始下载" else "清除筛选",
                                    onAction = {
                                        if (allHistoryItems.isEmpty()) selectedTab = 0
                                        else {
                                            historySearchQuery = ""
                                            historyFilter = "all"
                                        }
                                    }
                                )
                            }
                        } else {
                            items(historyItems) { item ->
                                HistoryRow(
                                    item = item,
                                    onOpen = {
                                        runCatching {
                                            val uri = Uri.parse(item.location)
                                            val mime = context.contentResolver.getType(uri) ?: "*/*"
                                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            context.startActivity(intent)
                                        }.onFailure {
                                            Toast.makeText(context, "无法打开该文件", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onShare = {
                                        runCatching {
                                            val uri = Uri.parse(item.location)
                                            val intent = Intent(Intent.ACTION_SEND)
                                                .setType(context.contentResolver.getType(uri) ?: "video/*")
                                                .putExtra(Intent.EXTRA_STREAM, uri)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            context.startActivity(Intent.createChooser(intent, "分享下载文件"))
                                        }.onFailure {
                                            Toast.makeText(context, "无法分享该文件", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }


                2 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text(
                            "管理网站登录和本机授权",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("网站授权", modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = if (hasCookieJar) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            if (hasCookieJar) "已授权" else "未授权",
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (hasCookieJar) MaterialTheme.colorScheme.onSecondaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    if (hasCookieJar) "本机安全保存 " + authorizedSites.size + " 个网站授权，不需要手动复制 Cookie。"
                                    else "可按需登录视频网站账号，以获取账号允许的画质。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (authorizedSites.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    authorizedSites.forEach { site ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp))
                                            Spacer(Modifier.width(9.dp))
                                            Text(site, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                            TextButton(onClick = {
                                                SecureCookieStore.removeAuthorizedSite(context, site)
                                                authorizedSites = SecureCookieStore.listAuthorizedSites(context)
                                                hasCookieJar = SecureCookieStore.hasCookieJar(context)
                                            }) { Text("移除") }
                                        }
                                    }
                                }
                                Button(
                                    onClick = openLogin,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(if (hasCookieJar) "添加其他网站授权" else "登录网站账号")
                                }
                                if (hasCookieJar) {
                                    TextButton(
                                        onClick = {
                                            SecureCookieStore.clear(context)
                                            authorizedSites = emptyList()
                                            hasCookieJar = false
                                            Toast.makeText(context, "已清除全部本机授权", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("清除全部授权") }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("应用设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "外观、下载格式、字幕、下载目录和解析引擎",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { settingsReturnTab = 2; selectedTab = 4 },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Tune, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("打开全部设置")
                                }
                            }
                        }

                        Text(
                            "video-dl · 本地运行，不经过第三方下载服务器",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                4 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            "按使用习惯调整下载方式，修改会自动保存",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SettingsGroup("外观", "应用的显示方式") {
                            ChoiceChipRows(
                                options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                                selected = themeMode,
                                onSelected = { themeMode = it }
                            )
                        }

                        SettingsGroup("下载格式", "选择视频或音频，以及画质和文件格式") {
                            ChoiceChipRows(
                                options = listOf("video" to "视频", "audio" to "音频"),
                                selected = if (audioOnly) "audio" else "video",
                                onSelected = { audioOnly = it == "audio" }
                            )
                            if (!audioOnly) {
                                Text("当前视频可用画质", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val currentVideoQualities = preview?.qualities.orEmpty()
                                if (currentVideoQualities.isEmpty()) {
                                    Text(
                                        if (preview == null) "先在主页解析视频，这里才会显示源站真实提供的画质。"
                                        else "当前视频没有返回可识别的画质选项。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    ChoiceChipRows(
                                        options = currentVideoQualities.map { it.height.toString() to it.label },
                                        selected = maxHeight.toString(),
                                        onSelected = {
                                            val selectedHeight = it.toIntOrNull() ?: 0
                                            maxHeight = selectedHeight
                                            preferences.edit().putInt("max_height", selectedHeight).apply()
                                        }
                                    )
                                }
                                Text("视频格式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChoiceChipRows(
                                    options = listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WebM"),
                                    selected = videoContainer,
                                    onSelected = { videoContainer = it }
                                )
                            } else {
                                Text("音频格式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChoiceChipRows(
                                    options = listOf("mp3" to "MP3", "m4a" to "M4A", "opus" to "Opus", "flac" to "FLAC"),
                                    selected = audioFormat,
                                    onSelected = { audioFormat = it }
                                )
                                SettingsToggleRow(
                                    title = "嵌入封面图片",
                                    description = "将视频封面写入音频文件",
                                    checked = embedThumbnail,
                                    onCheckedChange = { embedThumbnail = it }
                                )
                            }
                        }

                        SettingsGroup("字幕与播放列表", "默认仅下载当前视频，避免意外下载整张列表") {
                            SettingsToggleRow("同时下载字幕", "包含网站提供的自动字幕", downloadSubtitles) { downloadSubtitles = it }
                            if (downloadSubtitles) {
                                OutlinedTextField(
                                    value = subtitleLanguages,
                                    onValueChange = { subtitleLanguages = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    label = { Text("字幕语言") },
                                    placeholder = { Text("all 或 zh-Hans,zh-CN,en") },
                                    supportingText = { Text("输入 all 下载全部语言，或填写语言代码") }
                                )
                            }
                            SettingsToggleRow("下载整个播放列表", "开启后按顺序下载列表中的视频", downloadPlaylist) { downloadPlaylist = it }
                            SettingsToggleRow("跳过已经下载过的视频", "在本机保存 yt-dlp 下载归档", skipExisting) { skipExisting = it }
                        }

                        SettingsGroup("下载速度", "较高并发会增加网络和设备负载") {
                            ChoiceChipRows(
                                options = listOf("1" to "省流量", "4" to "均衡", "8" to "高速"),
                                selected = concurrentFragments.toString(),
                                onSelected = { concurrentFragments = it.toIntOrNull() ?: 4 }
                            )
                        }

                        SettingsGroup("解析引擎", "网站改版导致解析失败时，可以尝试更新 yt-dlp") {
                            SettingsToggleRow(
                                "自动更新解析引擎",
                                "每周检查一次，只在下载和解析空闲时执行",
                                autoUpdateEngine
                            ) { autoUpdateEngine = it }
                            if (autoUpdateEngine) {
                                SettingsToggleRow(
                                    "仅使用 Wi-Fi 更新",
                                    "避免自动更新消耗移动数据",
                                    autoUpdateWifiOnly
                                ) { autoUpdateWifiOnly = it }
                            }
                            OutlinedButton(
                                onClick = {
                                    engineUpdating = true
                                    EngineUpdateManager.update(context) { message ->
                                        engineUpdating = false
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !engineUpdating && !EngineUpdateManager.isUpdating() &&
                                    !download.running && !previewLoading,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (engineUpdating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在更新…")
                                } else Text("检查并更新 yt-dlp")
                            }
                            Text("这只更新本机的网站解析脚本，不会更新 video-dl 应用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("video-dl · 本地视频下载器", modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        }
        if (showInputSheet) {
            ModalBottomSheet(
                onDismissRequest = { showInputSheet = false },
                sheetState = inputSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(15.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.padding(11.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "新建下载",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "粘贴一个视频链接，选择画质后开始下载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showInputSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            maxHeight = 0
                            preferences.edit().putString("last_input_url", it)
                                .putInt("max_height", 0).apply()
                            preview = null
                            previewError = ""
                            selectedVideoFormatId = ""
                            selectedAudioFormatId = ""
                            showFormatSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("粘贴视频链接 https://…") },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null)
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = pasteUrl) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                                }
                                if (url.isNotBlank()) {
                                    IconButton(onClick = {
                                        url = ""
                                        preview = null
                                        previewError = ""
                                        selectedVideoFormatId = ""
                                        selectedAudioFormatId = ""
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "清空")
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { startPreview() }),
                        shape = RoundedCornerShape(18.dp)
                    )

                    if (previewError.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                previewError,
                                modifier = Modifier.padding(13.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "下载内容",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        ChoiceChipRows(
                            options = listOf("video" to "视频", "audio" to "音频"),
                            selected = if (audioOnly) "audio" else "video",
                            onSelected = { audioOnly = it == "audio" }
                        )
                    }

                    Button(
                        onClick = startPreview,
                        enabled = url.isNotBlank() && !previewLoading && !engineUpdating &&
                            !EngineUpdateManager.isUpdating(),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        if (previewLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(9.dp))
                            Text("正在解析…")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(9.dp))
                            Text("解析并选择格式", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        "解析和下载都在本机完成，网站授权仅保存在设备上。",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showFormatSheet && preview != null) {
            Dialog(
                onDismissRequest = { showFormatSheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                val info = preview!!
                val videoOptions = info.formats.filter { it.hasVideo }
                val audioOptions = info.formats.filter { it.hasAudio && !it.hasVideo }
                val selectedVideoOption = videoOptions.firstOrNull { it.formatId == selectedVideoFormatId }
                val selectedAudioOption = audioOptions.firstOrNull { it.formatId == selectedAudioFormatId }
                val bestOption = bestVideoFormat(videoOptions)
                val selectedSummary = when {
                    audioOnly -> selectedAudioOption?.label ?: "自动选择最佳音频"
                    selectedVideoOption != null -> selectedVideoOption.label
                    bestOption != null -> "自动最高 · ${bestOption.height}p"
                    else -> "自动选择源站最高可用画质"
                }
                val qualityGroups = videoOptions.filter { it.height > 0 }
                    .groupBy { it.height }
                    .toSortedMap(compareByDescending { it })

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "选择格式",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { showFormatSheet = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "关闭")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "当前选择 · " + selectedSummary +
                                        if (audioOnly) " · ${audioFormat.uppercase(Locale.ROOT)}"
                                        else " · ${videoContainer.uppercase(Locale.ROOT)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Button(
                                    onClick = startDownload,
                                    enabled = !engineUpdating && !EngineUpdateManager.isUpdating() && !previewLoading,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (download.running) "加入下载队列" else if (audioOnly) "下载音频" else "下载此画质",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                ) { formatPadding ->
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .padding(formatPadding)
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(horizontal = 18.dp)
                            .padding(top = 12.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            ) {
                                VideoThumbnail(
                                    url = info.thumbnail,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.Center).size(62.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.Black.copy(alpha = 0.68f)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "视频预览",
                                        modifier = Modifier.padding(12.dp),
                                        tint = Color.White
                                    )
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(13.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.56f)
                                ) {
                                    Text(
                                        "ニコニコ動画",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(17.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("标题", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    info.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val meta = listOf(info.uploader, info.duration)
                                    .filter { it.isNotBlank() }.joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(meta, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (info.niconicoApiMode.isNotBlank()) {
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                when (info.niconicoApiMode) {
                                                    "v3-dms" -> "Niconico 标准 v3 · DMS"
                                                    "v3_guest-dms" -> "Niconico 游客 v3_guest · DMS"
                                                    "watchV4-dms" -> "Niconico V4 · DMS"
                                                    "server-response-dms" -> "Niconico DMS"
                                                    "v3_guest" -> "Niconico 游客 v3_guest"
                                                    else -> "Niconico 标准 v3"
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Info, null, modifier = Modifier.size(15.dp))
                                        }
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 5.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        if (audioOnly) "仅提取音频，保存为所选音频格式"
                                        else "视频与音频将合并为一个文件",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(25.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("下载内容", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                ChoiceChipRows(
                                    options = listOf("video" to "视频", "audio" to "音频"),
                                    selected = if (audioOnly) "audio" else "video",
                                    onSelected = { audioOnly = it == "audio" }
                                )
                            }

                            if (!audioOnly) {
                                Text("选择图像质量", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                val bestOption = bestVideoFormat(videoOptions)
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedVideoFormatId = ""
                                        selectedAudioFormatId = bestAudioFormat(audioOptions)?.formatId.orEmpty()
                                        maxHeight = 0
                                        preferences.edit().putInt("max_height", 0).apply()
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selectedVideoFormatId.isBlank())
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        if (selectedVideoFormatId.isBlank()) 2.dp else 1.dp,
                                        if (selectedVideoFormatId.isBlank()) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(15.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("自动（最高品质）", style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold)
                                            Text(
                                                if (bestOption != null && bestOption.height > 0)
                                                    "本次解析最高返回 ${bestOption.height}p，下载时自动选择最佳流"
                                                else "由 yt-dlp 根据源站实际提供的格式自动选择",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        RadioButton(
                                            selected = selectedVideoFormatId.isBlank(),
                                            onClick = {
                                                selectedVideoFormatId = ""
                                                selectedAudioFormatId = bestAudioFormat(audioOptions)?.formatId.orEmpty()
                                                maxHeight = 0
                                                preferences.edit().putInt("max_height", 0).apply()
                                            }
                                        )
                                    }
                                }

                                if (qualityGroups.isNotEmpty()) {
                                    qualityGroups.entries.toList().chunked(2).forEach { pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            pair.forEach { entry ->
                                                val option = entry.value.maxWithOrNull(
                                                    compareBy<VideoInfoService.FormatOption> { it.fps }
                                                        .thenBy { it.bitrate }
                                                )!!
                                                QualityOptionCard(
                                                    height = entry.key,
                                                    detail = listOf(
                                                        if (option.fps > 0) "${option.fps}fps" else "",
                                                        option.ext.uppercase(Locale.ROOT),
                                                        if (option.filesize > 0L) formatBytesForUi(option.filesize) else ""
                                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                                    selected = selectedVideoFormatId == option.formatId,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    selectedVideoFormatId = option.formatId
                                                    maxHeight = entry.key
                                                    if (option.hasAudio) {
                                                        selectedAudioFormatId = ""
                                                    } else if (selectedAudioFormatId.isBlank()) {
                                                        selectedAudioFormatId = bestAudioFormat(audioOptions)?.formatId.orEmpty()
                                                    }
                                                    preferences.edit().putInt("max_height", maxHeight).apply()
                                                }
                                            }
                                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                } else if (videoOptions.isNotEmpty()) {
                                    Text("源站没有返回标准分辨率字段，以下为实际格式：",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FormatSelectionList(
                                        options = videoOptions,
                                        selectedId = selectedVideoFormatId,
                                        onSelected = { selectedVideoFormatId = it }
                                    )
                                } else {
                                    Text(
                                        info.qualityNotice.ifBlank { "源站没有返回可用的视频流。" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (selectedVideoOption != null && !selectedVideoOption.hasAudio) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("音频轨道", style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold)
                                        if (audioOptions.isEmpty()) {
                                            Text("没有返回独立音频列表，将由 yt-dlp 自动匹配。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            FormatSelectionList(
                                                options = audioOptions,
                                                selectedId = selectedAudioFormatId,
                                                onSelected = { selectedAudioFormatId = it }
                                            )
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("保存格式", style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold)
                                    ChoiceChipRows(
                                        options = listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WebM"),
                                        selected = videoContainer,
                                        onSelected = { videoContainer = it }
                                    )
                                }
                            } else {
                                Text("选择音频来源", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                                if (audioOptions.isEmpty()) {
                                    Text("源站没有返回独立音频流，将自动选择最佳音频。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    FormatSelectionList(
                                        options = audioOptions,
                                        selectedId = selectedAudioFormatId,
                                        onSelected = { selectedAudioFormatId = it }
                                    )
                                }
                                ChoiceChipRows(
                                    options = listOf("mp3" to "MP3", "m4a" to "M4A", "opus" to "Opus", "flac" to "FLAC"),
                                    selected = audioFormat,
                                    onSelected = { audioFormat = it }
                                )
                                SettingsToggleRow(
                                    "嵌入封面图片", "将视频封面写入音频文件",
                                    embedThumbnail, { embedThumbnail = it }
                                )
                            }

                            if (info.qualityNotice.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(17.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(info.qualityNotice, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        if (info.niconicoApiMode == "v3_guest" ||
                                            info.qualityNotice.contains("没有检测到本机 user_session Cookie")) {
                                            OutlinedButton(
                                                onClick = openLogin,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("登录 Niconico 并重新解析")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { showAdvancedDownloadSettings = !showAdvancedDownloadSettings }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("更多下载选项", fontWeight = FontWeight.SemiBold)
                                Text("字幕、播放列表、下载速度", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (showAdvancedDownloadSettings) "收起" else "展开",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (showAdvancedDownloadSettings) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                SettingsToggleRow("同时下载字幕", "下载网站提供的字幕", downloadSubtitles) {
                                    downloadSubtitles = it
                                }
                                if (downloadSubtitles) {
                                    OutlinedTextField(
                                        value = subtitleLanguages,
                                        onValueChange = { subtitleLanguages = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        label = { Text("字幕语言") },
                                        placeholder = { Text("all 或 zh-Hans,zh-CN,en") }
                                    )
                                }
                                SettingsToggleRow("下载整个播放列表", "默认只下载当前视频", downloadPlaylist) {
                                    downloadPlaylist = it
                                }
                                SettingsToggleRow("跳过已下载的视频", "在本机保存下载归档", skipExisting) {
                                    skipExisting = it
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text("下载速度", fontWeight = FontWeight.SemiBold)
                                ChoiceChipRows(
                                    options = listOf("1" to "省流量", "4" to "均衡", "8" to "高速"),
                                    selected = concurrentFragments.toString(),
                                    onSelected = { concurrentFragments = it.toIntOrNull() ?: 4 }
                                )
                            }
                        }
                    }


                }
                }
                }
            }
        }
    }
}

private fun bestVideoFormat(
    formats: List<VideoInfoService.FormatOption>
): VideoInfoService.FormatOption? {
    val videoOnly = formats.filter { it.hasVideo && !it.hasAudio }
    val candidates = if (videoOnly.isNotEmpty()) videoOnly else formats.filter { it.hasVideo }
    return candidates.maxWithOrNull(
        compareBy<VideoInfoService.FormatOption> { it.height }
            .thenBy { it.fps }
            .thenBy { it.bitrate }
    )
}

private fun bestAudioFormat(
    formats: List<VideoInfoService.FormatOption>
): VideoInfoService.FormatOption? {
    return formats.filter { it.hasAudio && !it.hasVideo }.maxWithOrNull(
        compareBy<VideoInfoService.FormatOption> { it.bitrate }
            .thenBy { it.filesize }
    )
}

@Composable
private fun QualityOptionCard(
    height: Int,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (height >= 4320) "${height}p · 8K"
                    else if (height >= 2160) "${height}p · 4K"
                    else "${height}p",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    detail.ifBlank { "源站格式" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

private fun formatBytesForUi(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    else String.format(Locale.getDefault(), "%.0f MB", mb)
}

@Composable
private fun FormatSelectionList(
    options: List<VideoInfoService.FormatOption>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val selected = selectedId == option.formatId
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSelected(option.formatId) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { onSelected(option.formatId) })
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            option.details,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (option.hasVideo && option.hasAudio) "完整音视频流"
                            else if (option.hasVideo) "独立视频流"
                            else "独立音频流",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChipRows(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    options.chunked(3).forEach { rowOptions ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowOptions.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun VideoThumbnail(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = url
    ) {
        value = if (url.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 7000
                    readTimeout = 10000
                    useCaches = true
                }
                connection.getInputStream().use { stream ->
                    BitmapFactory.decodeStream(
                        stream,
                        null,
                        BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                    )
                }
            }.getOrNull()
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.padding(25.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HistoryRow(
    item: DownloadHistoryItem,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val statusColor = when {
        item.statusLabel.contains("失败") -> MaterialTheme.colorScheme.error
        item.statusLabel.contains("完成") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    item.fileName.ifBlank { item.message.ifBlank { "下载任务" } },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(shape = RoundedCornerShape(100.dp), color = statusColor.copy(alpha = 0.12f)) {
                    Text(
                        item.statusLabel,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            Text(
                listOf(item.source, item.time).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.formatLabel.isNotBlank()) {
                Text(
                    item.formatLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (item.message.isNotBlank() && item.fileName.isNotBlank()) {
                Text(
                    item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.location.startsWith("content://")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpen) { Text("打开文件") }
                    TextButton(onClick = onShare) { Text("分享") }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(17.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onAction, shape = RoundedCornerShape(16.dp)) { Text(action) }
    }
}

