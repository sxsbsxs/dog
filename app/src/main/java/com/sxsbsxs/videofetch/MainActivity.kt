package com.sxsbsxs.videofetch

/*
 * The Compose UI in this file includes adaptations of UI patterns and components
 * from JunkFood02/Seal (https://github.com/JunkFood02/Seal), licensed under GPL-3.0.
 * Adapted and modified for video-dl on 2026-11-15. Download and media services remain
 * video-dl implementations.
 */

import android.Manifest
import android.graphics.BitmapFactory
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import java.text.SimpleDateFormat
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import com.sxsbsxs.videofetch.ui.component.SealSettingItem
import com.sxsbsxs.videofetch.ui.component.SelectionGroupItem
import com.sxsbsxs.videofetch.ui.component.SelectionGroupRow
import com.sxsbsxs.videofetch.ui.svg.DynamicColorImageVectors
import com.sxsbsxs.videofetch.ui.svg.download
import com.sxsbsxs.videofetch.ui.svg.videoSteaming

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
        val normalizedUrl = VideoUrlNormalizer.normalize(url)
        if (normalizedUrl == null) {
            Toast.makeText(this, "链接格式不正确，请粘贴完整视频链接或 11 位 YouTube 视频 ID", Toast.LENGTH_LONG).show()
            return
        }
        val selectedTree = getSharedPreferences(
            "videofetch_settings", Context.MODE_PRIVATE
        ).getString("download_tree_uri", "")
        val customFolder = !selectedTree.isNullOrBlank()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !customFolder &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadUrl = normalizedUrl
            pendingFormatSelector = formatSelector
            pendingAudioOnly = audioOnly
            pendingFormatLabel = formatLabel
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        DownloadService.start(this, normalizedUrl, formatSelector, audioOnly, formatLabel)
    }

    private fun sharedText(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.dataString
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }

    private fun extractHttpUrl(text: String?): String? =
        VideoUrlNormalizer.normalize(text)
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
    VideoUrlNormalizer.isValidHttpUrl(value)

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
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsReturnTab by rememberSaveable { mutableStateOf(0) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var hasCookieJar by remember { mutableStateOf(SecureCookieStore.hasCookieJar(context)) }
    var authorizedSites by remember { mutableStateOf(SecureCookieStore.listAuthorizedSites(context)) }
    // Cookie plaintext stays in transient UI state only while the authorization page is visible.
    var cookieJarPreview by remember { mutableStateOf("") }
    var isCookieJarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        if (selectedTab != 2) {
            cookieJarPreview = ""
            isCookieJarVisible = false
        }
    }
    var historyJson by remember { mutableStateOf(DownloadHistoryStore.read(context)) }
    var playingHistoryItem by remember { mutableStateOf<DownloadHistoryItem?>(null) }
    var playerReturnTab by rememberSaveable { mutableStateOf(1) }
    var mediaFilter by rememberSaveable { mutableStateOf("all") }
    var downloadTreeUri by rememberSaveable {
        mutableStateOf(preferences.getString("download_tree_uri", "").orEmpty())
    }
    val downloadFolderLabel = remember(downloadTreeUri) {
        if (downloadTreeUri.isBlank()) "Download/video-dl"
        else runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(downloadTreeUri))?.name
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "已选择的文件夹"
    }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { pickedUri ->
        if (pickedUri != null) {
            runCatching {
                val pickedFolder = DocumentFile.fromTreeUri(context, pickedUri)
                if (pickedFolder == null || !pickedFolder.isDirectory || !pickedFolder.canWrite()) {
                    throw IllegalArgumentException("所选文件夹不可写，请重新选择")
                }
                context.contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val oldTree = downloadTreeUri
                if (oldTree.isNotBlank() && oldTree != pickedUri.toString()) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(oldTree),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                }
                preferences.edit().putString("download_tree_uri", pickedUri.toString()).apply()
                downloadTreeUri = pickedUri.toString()
                Toast.makeText(context, "下载目录已更新", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "无法保存文件夹权限，请重新选择", Toast.LENGTH_LONG).show()
            }
        }
    }

    val localMediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri != null) {
            runCatching {
                // OpenDocument already grants temporary read access to this Activity.
                // Persist it when supported, but do not block immediate playback if a
                // third-party document provider refuses a persistable permission.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        pickedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val displayName = context.contentResolver.query(
                    pickedUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty().ifBlank { "本地媒体" }
                val mime = context.contentResolver.getType(pickedUri).orEmpty()
                val audio = mime.startsWith("audio/") ||
                    displayName.substringAfterLast('.', "").lowercase(Locale.ROOT) in
                    setOf("mp3", "m4a", "aac", "opus", "flac", "wav", "ogg")
                playingHistoryItem = DownloadHistoryItem(
                    fileName = displayName,
                    source = "本地文件",
                    time = "",
                    statusLabel = "本地媒体",
                    location = pickedUri.toString(),
                    message = "",
                    formatLabel = if (audio) "音频" else "视频"
                )
                playerReturnTab = 7
                selectedTab = 6
            }.onFailure {
                Toast.makeText(context, "无法打开所选媒体文件", Toast.LENGTH_LONG).show()
            }
        }
    }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var historySearchVisible by rememberSaveable { mutableStateOf(false) }
    var historyFilter by rememberSaveable { mutableStateOf("all") }
    var showQueueMenu by rememberSaveable { mutableStateOf(false) }
    val navigateFromDrawer: (Int) -> Unit = { destination ->
        if (destination == 1 || destination == 7) historyJson = DownloadHistoryStore.read(context)
        if (destination == 2) {
            authorizedSites = SecureCookieStore.listAuthorizedSites(context)
            hasCookieJar = SecureCookieStore.hasCookieJar(context)
        }
        if (destination == 4) {
            settingsReturnTab = selectedTab
            settingsSection = null
        }
        selectedTab = destination
        drawerScope.launch { drawerState.close() }
    }

    var audioOnly by rememberSaveable { mutableStateOf(preferences.getBoolean("audio_only", false)) }
    var maxHeight by rememberSaveable { mutableStateOf(preferences.getInt("max_height", 0)) }
    var selectedVideoFormatId by rememberSaveable { mutableStateOf("") }
    var selectedAudioFormatId by rememberSaveable { mutableStateOf("") }
    var downloadSubtitles by rememberSaveable { mutableStateOf(preferences.getBoolean("download_subtitles", false)) }
    var downloadAutoSubtitles by rememberSaveable { mutableStateOf(preferences.getBoolean("download_auto_subtitles", false)) }
    var embedSubtitles by rememberSaveable { mutableStateOf(preferences.getBoolean("embed_subtitles", true)) }
    var downloadPlaylist by rememberSaveable { mutableStateOf(preferences.getBoolean("download_playlist", false)) }
    var videoContainer by rememberSaveable { mutableStateOf(preferences.getString("video_container", "mp4") ?: "mp4") }
    var audioFormat by rememberSaveable { mutableStateOf(preferences.getString("audio_format", "mp3") ?: "mp3") }
    var audioQuality by rememberSaveable { mutableStateOf(preferences.getString("audio_quality", "0") ?: "0") }
    var subtitleLanguages by rememberSaveable { mutableStateOf(preferences.getString("subtitle_languages", "all") ?: "all") }
    var embedThumbnail by rememberSaveable { mutableStateOf(preferences.getBoolean("embed_thumbnail", true)) }
    var writeThumbnail by rememberSaveable { mutableStateOf(preferences.getBoolean("write_thumbnail", false)) }
    var embedMetadata by rememberSaveable { mutableStateOf(preferences.getBoolean("embed_metadata", true)) }
    var skipExisting by rememberSaveable { mutableStateOf(preferences.getBoolean("skip_existing", false)) }
    var filenameTemplate by rememberSaveable { mutableStateOf(preferences.getString("filename_template", "title") ?: "title") }
    var concurrentFragments by rememberSaveable { mutableStateOf(preferences.getInt("concurrent_fragments", 4)) }
    var downloadRetries by rememberSaveable { mutableStateOf(preferences.getInt("download_retries", 10)) }
    var useAria2 by rememberSaveable { mutableStateOf(preferences.getBoolean("use_aria2", true)) }
    var rateLimit by rememberSaveable { mutableStateOf(preferences.getString("rate_limit", "").orEmpty()) }
    var autoUpdateEngine by rememberSaveable {
        // YouTube extraction changes frequently; keep the local yt-dlp engine
        // current by default. Users can still disable this in Settings.
        if (!preferences.getBoolean("engine_nightly_default_migrated_v2", false)) {
            preferences.edit()
                .putBoolean("auto_update_engine", true)
                .putBoolean("engine_nightly_default_migrated_v2", true)
                .apply()
        }
        mutableStateOf(preferences.getBoolean("auto_update_engine", true))
    }
    var autoUpdateWifiOnly by rememberSaveable {
        mutableStateOf(preferences.getBoolean("auto_update_wifi_only", true))
    }

    var previewLoading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<VideoInfoService.Preview?>(null) }
    var lastDownloadPreview by remember { mutableStateOf<VideoInfoService.Preview?>(null) }
    var previewError by remember { mutableStateOf("") }
    var previewDiagnostic by remember { mutableStateOf("") }
    var engineUpdating by remember { mutableStateOf(false) }
    var showFormatSheet by rememberSaveable { mutableStateOf(false) }
    var queueFilter by rememberSaveable { mutableStateOf("all") }
    var showAdvancedDownloadSettings by rememberSaveable { mutableStateOf(false) }
    val download by DownloadState.state.collectAsState()
    val formatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authorizedSites = SecureCookieStore.listAuthorizedSites(context)
        hasCookieJar = SecureCookieStore.hasCookieJar(context)
        if (result.resultCode == Activity.RESULT_OK) {
            val failedTask = download
            val requestedTarget = url.trim().ifBlank {
                preferences.getString("last_input_url", "").orEmpty()
            }
            val retryBlockedYoutubeTask =
                failedTask.lastUrl.isNotBlank() &&
                failedTask.lastUrl == requestedTarget &&
                YouTubeRequestFallback.isYoutubeUrl(failedTask.lastUrl) &&
                failedTask.message.contains("YouTube 拦截了当前会话")

            if (retryBlockedYoutubeTask) {
                Toast.makeText(context, "YouTube 登录已保存，正在自动重试刚才的下载…", Toast.LENGTH_LONG).show()
                onDownload(
                    failedTask.lastUrl,
                    failedTask.formatSelector,
                    failedTask.audioOnly,
                    failedTask.formatLabel
                )
            } else {
                Toast.makeText(context, "授权已保存，正在重新获取可用画质…", Toast.LENGTH_SHORT).show()
                val target = requestedTarget
                if (isHttpUrl(target)) {
                    url = target
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
                            lastDownloadPreview = result
                            previewError = ""
                            showFormatSheet = true
                        }
                    }

                    override fun onError(message: String) {
                        previewLoading = false
                        previewError = message
                        previewDiagnostic = ""
                        Toast.makeText(context, "重新解析失败：" + message, Toast.LENGTH_LONG).show()
                    }
                    override fun onError(message: String, diagnostic: String) {
                        previewLoading = false
                        previewError = message
                        previewDiagnostic = diagnostic
                        Toast.makeText(context, "重新解析失败：" + message, Toast.LENGTH_LONG).show()
                    }
                    })
                }
            }
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            url = initialUrl
            maxHeight = 0
            selectedVideoFormatId = ""
            selectedAudioFormatId = ""
            preferences.edit().putInt("max_height", 0).apply()
        }
    }

    LaunchedEffect(
        themeMode, audioOnly, maxHeight, downloadSubtitles, downloadAutoSubtitles,
        embedSubtitles, downloadPlaylist, videoContainer, audioFormat, audioQuality,
        subtitleLanguages, embedThumbnail, writeThumbnail, embedMetadata,
        skipExisting, filenameTemplate, concurrentFragments, downloadRetries,
        useAria2, rateLimit, autoUpdateEngine, autoUpdateWifiOnly
    ) {
        preferences.edit()
            .putString("theme_mode", themeMode)
            .putBoolean("audio_only", audioOnly)
            .putInt("max_height", maxHeight)
            .putBoolean("download_subtitles", downloadSubtitles)
            .putBoolean("download_auto_subtitles", downloadAutoSubtitles)
            .putBoolean("embed_subtitles", embedSubtitles)
            .putBoolean("download_playlist", downloadPlaylist)
            .putString("video_container", videoContainer)
            .putString("audio_format", audioFormat)
            .putString("audio_quality", audioQuality)
            .putString("subtitle_languages", subtitleLanguages.trim().ifBlank { "all" })
            .putBoolean("embed_thumbnail", embedThumbnail)
            .putBoolean("write_thumbnail", writeThumbnail)
            .putBoolean("embed_metadata", embedMetadata)
            .putBoolean("skip_existing", skipExisting)
            .putString("filename_template", filenameTemplate)
            .putInt("concurrent_fragments", concurrentFragments)
            .putInt("download_retries", downloadRetries)
            .putBoolean("use_aria2", useAria2)
            .putString("rate_limit", rateLimit.trim())
            .putBoolean("auto_update_engine", autoUpdateEngine)
            .putBoolean("auto_update_wifi_only", autoUpdateWifiOnly)
            .apply()
    }

    LaunchedEffect(download.running, download.message, selectedTab) {
        if (selectedTab == 7 && !download.running) {
            historyJson = DownloadHistoryStore.read(context)
        }
    }

    BackHandler(enabled = selectedTab == 6) {
        selectedTab = playerReturnTab
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
        val pastedUrl = VideoUrlNormalizer.normalize(clipText)
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

    val copyPreviewDiagnostic: () -> Unit = {
        val report = previewDiagnostic
        if (report.isBlank()) {
            Toast.makeText(context, "当前没有可复制的解析诊断", Toast.LENGTH_SHORT).show()
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                Toast.makeText(context, "系统剪贴板不可用", Toast.LENGTH_SHORT).show()
            } else {
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("VideoFetch 预解析诊断（已脱敏）", report)
                )
                Toast.makeText(context, "已复制解析诊断，Cookie 值和链接参数已隐藏", Toast.LENGTH_LONG).show()
            }
        }
    }

    val copyDownloadDiagnostic: () -> Unit = {
        val report = download.diagnostic
        if (report.isBlank()) {
            Toast.makeText(context, "当前没有可复制的诊断信息", Toast.LENGTH_SHORT).show()
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                Toast.makeText(context, "系统剪贴板不可用", Toast.LENGTH_SHORT).show()
            } else {
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("VideoFetch 诊断信息（已脱敏）", report)
                )
                Toast.makeText(
                    context,
                    "已复制诊断信息，Cookie 值和链接参数已自动隐藏",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val startPreview: () -> Unit = {
        val value = VideoUrlNormalizer.normalize(url).orEmpty()
        if (value.isBlank()) {
            Toast.makeText(context, "链接格式不正确，请粘贴完整视频链接或 11 位 YouTube 视频 ID", Toast.LENGTH_LONG).show()
        } else if (engineUpdating || EngineUpdateManager.isUpdating()) {
            Toast.makeText(context, "当前任务进行中，暂不能解析", Toast.LENGTH_SHORT).show()
        } else {
            url = value
            previewLoading = true
            preview = null
            previewError = ""
            previewDiagnostic = ""
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
                        lastDownloadPreview = result
                        previewDiagnostic = ""
                        showFormatSheet = true
                    }
                }
                override fun onError(message: String) {
                    previewLoading = false
                    if (url.trim() == value) {
                        previewError = message
                        previewDiagnostic = ""
                    }
                }
                override fun onError(message: String, diagnostic: String) {
                    previewLoading = false
                    if (url.trim() == value) {
                        previewError = message
                        previewDiagnostic = diagnostic
                    }
                }
            })
        }
    }

    val startDownload: () -> Unit = {
        val value = VideoUrlNormalizer.normalize(url).orEmpty()
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
            lastDownloadPreview = preview ?: lastDownloadPreview
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
        val target = VideoUrlNormalizer.normalize(
            url.ifBlank { preferences.getString("last_input_url", "").orEmpty() }
        ).orEmpty()
        if (target.isBlank()) {
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
                ModalDrawerSheet(modifier = Modifier.width(360.dp)) {
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
                            label = { Text("首页") },
                            selected = selectedTab == 0,
                            onClick = { navigateFromDrawer(0) },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("下载队列") },
                            selected = selectedTab == 5,
                            onClick = { navigateFromDrawer(5) },
                            icon = { Icon(Icons.Default.Download, contentDescription = null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("下载记录") },
                            selected = selectedTab == 1,
                            onClick = { navigateFromDrawer(1) },
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("媒体库") },
                            selected = selectedTab == 7,
                            onClick = { navigateFromDrawer(7) },
                            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) }
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
                    if (selectedTab != 0 && selectedTab != 6) {
                        val pageTitle = when (selectedTab) {
                            1 -> "下载记录"
                            2 -> "网站授权"
                            3 -> "任务列表"
                            5 -> "下载队列"
                            6 -> "播放器"
                            7 -> "媒体库"
                            else -> when (settingsSection) {
                                "appearance" -> "外观"
                                "format" -> "下载格式"
                                "advanced" -> "字幕与播放列表"
                                "engine" -> "解析引擎"
                                "storage" -> "下载目录"
                                "about" -> "关于 video-dl"
                                else -> "设置"
                            }
                        }
                        val navigation: @Composable () -> Unit = {
                            IconButton(onClick = {
                                if (selectedTab == 4 && settingsSection != null) {
                                    settingsSection = null
                                } else if (selectedTab == 4) {
                                    selectedTab = settingsReturnTab
                                } else {
                                    drawerScope.launch { drawerState.open() }
                                }
                            }) {
                                Icon(
                                    if (selectedTab == 4) Icons.Default.ArrowBack else Icons.Default.Menu,
                                    contentDescription = if (selectedTab == 4) "返回" else "打开导航菜单"
                                )
                            }
                        }
                        if (selectedTab == 1 || selectedTab == 2 || selectedTab == 4) {
                            LargeTopAppBar(
                                navigationIcon = navigation,
                                title = { Text(pageTitle, fontWeight = FontWeight.Medium) },
                                actions = {
                                    if (selectedTab == 1) {
                                        IconButton(onClick = { historySearchVisible = !historySearchVisible }) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = if (historySearchVisible) "关闭搜索" else "搜索下载记录"
                                            )
                                        }
                                    }
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
                                },
                                actions = {
                                    if (selectedTab == 5) {
                                        TextButton(onClick = {
                                            url = ""
                                            preview = null
                                            previewError = ""
                                            selectedVideoFormatId = ""
                                            selectedAudioFormatId = ""
                                            selectedTab = 0
                                        }) {
                                            Text("新建下载")
                                        }
                                    }
                                    if (selectedTab == 7) {
                                        IconButton(onClick = {
                                            localMediaPicker.launch(arrayOf("video/*", "audio/*"))
                                        }) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = "打开本地媒体")
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
            ) { padding ->
            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .padding(padding)
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = 18.dp, bottom = 28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "打开导航菜单")
                            }
                            Spacer(Modifier.width(8.dp))
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
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { selectedTab = 5 }) {
                                Icon(Icons.Default.Download, contentDescription = "打开下载队列")
                            }
                        }

                        Spacer(Modifier.height(28.dp))
                        Text(
                            "下载视频",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "粘贴视频链接，解析后选择画质并开始下载",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(24.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(42.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Icon(
                                            Icons.Default.Link,
                                            contentDescription = null,
                                            modifier = Modifier.padding(10.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            "视频链接",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "支持粘贴或手动输入",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                previewError,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            if (previewDiagnostic.isNotBlank()) {
                                                TextButton(
                                                    onClick = copyPreviewDiagnostic,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("复制解析诊断（已隐藏 Cookie）")
                                                }
                                            }
                                            if (previewError.contains("YouTube 拦截了当前会话")) {
                                                OutlinedButton(
                                                    onClick = openLogin,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(14.dp)
                                                ) {
                                                    Text("登录 YouTube 并继续")
                                                }
                                            }
                                        }
                                    }
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
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Text(
                            "解析和下载都在本机完成，网站授权仅保存在设备上。",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                }
                5 -> {
                    val showRunning = queueFilter == "all" || queueFilter == "downloading"
                    val showQueued = queueFilter == "all" || queueFilter == "queued"
                    val showFinished = queueFilter == "all" || queueFilter == "finished"
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp, end = 20.dp, top = 0.dp, bottom = 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            ChoiceChipRows(
                                options = listOf(
                                    "all" to "全部",
                                    "downloading" to "下载中",
                                    "queued" to "等待中",
                                    "finished" to "最近完成"
                                ),
                                selected = queueFilter,
                                onSelected = { queueFilter = it }
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    when {
                                        download.running -> "正在进行"
                                        download.lastUrl.isNotBlank() -> "最近任务"
                                        else -> "下载列表"
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (download.running) "1 项" else if (download.lastUrl.isNotBlank()) "1 项" else "0 项",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { isGridView = !isGridView }) {
                                    Icon(
                                        if (isGridView) Icons.Default.List else Icons.Default.ViewModule,
                                        contentDescription = if (isGridView) "切换列表视图" else "切换网格视图"
                                    )
                                }
                                Box {
                                    IconButton(onClick = { showQueueMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "更多任务操作")
                                    }
                                    DropdownMenu(
                                        expanded = showQueueMenu,
                                        onDismissRequest = { showQueueMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("清空等待队列") },
                                            enabled = download.queuedCount > 0,
                                            onClick = {
                                                showQueueMenu = false
                                                DownloadService.clearQueue(context)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("取消当前下载") },
                                            enabled = download.running,
                                            onClick = {
                                                showQueueMenu = false
                                                DownloadService.cancel(context)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (download.running && showRunning) {
                            item {
                                SealVideoTaskCard(
                                    title = lastDownloadPreview?.title
                                        ?: if (download.audioOnly) "正在下载音频" else "正在下载视频",
                                    uploader = lastDownloadPreview?.uploader ?: sourceHost(download.lastUrl),
                                    thumbnailUrl = lastDownloadPreview?.thumbnail.orEmpty(),
                                    isGridView = isGridView,
                                    stateLabel = if (download.progressKnown) "下载中 · ${download.progress}%" else "正在连接",
                                    progress = download.progress,
                                    progressKnown = download.progressKnown,
                                    message = download.message,
                                    formatLabel = download.formatLabel,
                                    onActions = {
                                        OutlinedButton(
                                            onClick = { DownloadService.cancel(context) },
                                            modifier = Modifier.weight(1f),
                                            shape = MaterialTheme.shapes.large
                                        ) { Text("取消下载") }
                                        if (download.queuedCount > 0) {
                                            TextButton(
                                                onClick = { DownloadService.clearQueue(context) },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("清空等待项") }
                                        }
                                    }
                                )
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
                                val youtubeLoginRequired = failed &&
                                    (download.message.contains("YouTube 拦截了当前会话") ||
                                     download.message.contains("YouTube 仍在拦截"))
                                SealVideoTaskCard(
                                    title = lastDownloadPreview?.title ?: download.lastUrl,
                                    uploader = lastDownloadPreview?.uploader ?: sourceHost(download.lastUrl),
                                    thumbnailUrl = lastDownloadPreview?.thumbnail.orEmpty(),
                                    isGridView = isGridView,
                                    stateLabel = when {
                                        failed -> "下载失败"
                                        completed -> "已完成"
                                        else -> "任务已停止"
                                    },
                                    progress = if (completed) 100 else download.progress,
                                    progressKnown = completed || download.progressKnown,
                                    message = download.message,
                                    formatLabel = download.formatLabel,
                                    onActions = {
                                        Button(
                                            onClick = {
                                                if (youtubeLoginRequired) {
                                                    url = download.lastUrl
                                                    openLogin()
                                                } else {
                                                    onDownload(
                                                        download.lastUrl,
                                                        download.formatSelector,
                                                        download.audioOnly,
                                                        download.formatLabel
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text(
                                                when {
                                                    completed -> "再次下载"
                                                    youtubeLoginRequired -> "登录并自动重试"
                                                    else -> "重试下载"
                                                }
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                url = download.lastUrl
                                                preview = lastDownloadPreview
                                                selectedTab = 0
                                                showFormatSheet = preview != null
                                                selectedTab = 0
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("修改格式") }
                                        if (failed && download.diagnostic.isNotBlank()) {
                                            TextButton(
                                                onClick = copyDownloadDiagnostic,
                                                modifier = Modifier.weight(1f)
                                            ) { Text("复制诊断") }
                                        }
                                    }
                                )
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
                                    illustration = DynamicColorImageVectors.download(),
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
                                        else -> "把视频链接交给 video-dl，格式和画质会在解析后选择。"
                                    },
                                    action = "新建下载",
                                    onAction = {
                                        url = ""
                                        preview = null
                                        previewError = ""
                                        selectedVideoFormatId = ""
                                        selectedAudioFormatId = ""
                                        selectedTab = 0
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
                                    if (failed && download.diagnostic.isNotBlank()) {
                                        TextButton(
                                            onClick = copyDownloadDiagnostic,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("复制诊断信息（已隐藏 Cookie）")
                                        }
                                    }
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
                                illustration = DynamicColorImageVectors.download(),
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
                            if (historySearchVisible) {
                                OutlinedTextField(
                                    value = historySearchQuery,
                                    onValueChange = { historySearchQuery = it },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.large,
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
                            }
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
                                    illustration = if (allHistoryItems.isEmpty()) DynamicColorImageVectors.videoSteaming() else null,
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
                                    onPlay = {
                                        if (item.location.isNotBlank()) {
                                            playingHistoryItem = item
                                            playerReturnTab = 1
                                            selectedTab = 6
                                        } else {
                                            Toast.makeText(context, "该记录没有可播放的本地文件", Toast.LENGTH_SHORT).show()
                                        }
                                    },
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


                6 -> {
                    val item = playingHistoryItem
                    if (item == null || item.location.isBlank()) {
                        EmptyState(
                            icon = Icons.Default.PlayArrow,
                            title = "没有可播放的文件",
                            description = "请从下载记录中选择一个已完成的视频或音频。",
                            action = "返回下载记录",
                            onAction = { selectedTab = 1 }
                        )
                    } else {
                        MediaPlaybackScreen(
                            item = item,
                            onBack = { selectedTab = playerReturnTab }
                        )
                    }
                }
                7 -> {
                    val allMediaItems = remember(historyJson) {
                        parseDownloadHistory(historyJson).filter {
                            it.location.isNotBlank() &&
                                it.statusLabel.contains("完成") &&
                                isPlayableMediaItem(it)
                        }
                    }
                    val visibleMediaItems = remember(allMediaItems, mediaFilter) {
                        allMediaItems.filter { item ->
                            when (mediaFilter) {
                                "video" -> !isAudioHistoryItem(item)
                                "audio" -> isAudioHistoryItem(item)
                                else -> true
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${visibleMediaItems.size} 个媒体文件",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilterChip(
                                selected = mediaFilter == "all",
                                onClick = { mediaFilter = "all" },
                                label = { Text("全部") }
                            )
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = mediaFilter == "video",
                                onClick = { mediaFilter = "video" },
                                label = { Text("视频") }
                            )
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = mediaFilter == "audio",
                                onClick = { mediaFilter = "audio" },
                                label = { Text("音频") }
                            )
                        }
                        if (visibleMediaItems.isEmpty()) {
                            EmptyState(
                                icon = Icons.Default.VideoLibrary,
                                title = "媒体库还是空的",
                                description = "已下载完成的视频和音频会自动出现在这里，也可以从设备中打开其他媒体文件。",
                                action = "打开本地媒体",
                                onAction = {
                                    localMediaPicker.launch(arrayOf("video/*", "audio/*"))
                                }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 20.dp, end = 20.dp, bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(visibleMediaItems) { item ->
                                    HistoryRow(
                                        item = item,
                                        onPlay = {
                                            playingHistoryItem = item
                                            playerReturnTab = 7
                                            selectedTab = 6
                                        },
                                        onOpen = {
                                            runCatching {
                                                val uri = historyLocationUri(item.location)
                                                val mime = context.contentResolver.getType(uri) ?: "*/*"
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW)
                                                        .setDataAndType(uri, mime)
                                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                )
                                            }.onFailure {
                                                Toast.makeText(context, "无法打开该文件", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onShare = {
                                            runCatching {
                                                val uri = historyLocationUri(item.location)
                                                val intent = Intent(Intent.ACTION_SEND)
                                                    .setType(context.contentResolver.getType(uri) ?: "*/*")
                                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                context.startActivity(Intent.createChooser(intent, "分享媒体文件"))
                                            }.onFailure {
                                                Toast.makeText(context, "无法分享该文件", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
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
                                    if (hasCookieJar) "已在本机加密保存 " + authorizedSites.size + " 个网站授权。下载时自动使用，也可以按需查看或复制。"
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
                                                cookieJarPreview = ""
                                                isCookieJarVisible = false
                                            }) { Text("移除") }
                                        }
                                    }
                                }
                                if (hasCookieJar) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (isCookieJarVisible) {
                                                    cookieJarPreview = ""
                                                    isCookieJarVisible = false
                                                } else {
                                                    val stored = runCatching {
                                                        SecureCookieStore.loadCookieJar(context)
                                                    }.getOrDefault("")
                                                    if (stored.isBlank()) {
                                                        Toast.makeText(context, "没有可显示的 Cookie，请重新授权", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        cookieJarPreview = stored
                                                        isCookieJarVisible = true
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(if (isCookieJarVisible) "隐藏 Cookie" else "显示 Cookie")
                                        }
                                        Button(
                                            onClick = {
                                                val stored = runCatching {
                                                    SecureCookieStore.loadCookieJar(context)
                                                }.getOrDefault("")
                                                if (stored.isBlank()) {
                                                    Toast.makeText(context, "没有可复制的 Cookie，请重新授权", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    if (clipboard == null) {
                                                        Toast.makeText(context, "系统剪贴板不可用", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val clip = android.content.ClipData.newPlainText(
                                                            "VideoFetch Cookie（敏感）",
                                                            stored
                                                        )
                                                        val sensitiveExtras = android.os.PersistableBundle().apply {
                                                            putBoolean(
                                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                                                    android.content.ClipDescription.EXTRA_IS_SENSITIVE
                                                                else "android.content.extra.IS_SENSITIVE",
                                                                true
                                                            )
                                                        }
                                                        clip.description.extras = sensitiveExtras
                                                        clipboard.setPrimaryClip(clip)
                                                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                                            Toast.makeText(context, "已复制全部 Cookie。它等同账号凭证，请勿发给他人。", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("复制全部 Cookie")
                                        }
                                    }
                                    Text(
                                        "Cookie 等同账号登录凭证。复制后只粘贴到你信任的本机工具，不要发送给他人或公开截图。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    if (isCookieJarVisible && cookieJarPreview.isNotBlank()) {
                                        OutlinedTextField(
                                            value = cookieJarPreview,
                                            onValueChange = {},
                                            readOnly = true,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp, max = 240.dp),
                                            textStyle = MaterialTheme.typography.labelSmall,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Text(
                                            "共 " + CookieJarExporter.countCookies(cookieJarPreview) + " 条 Cookie，来自 " +
                                                CookieJarExporter.countDomains(cookieJarPreview) + " 个域名。",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                                    onClick = { settingsReturnTab = 2; settingsSection = null; selectedTab = 4 },
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
                    if (settingsSection == null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)
                        ) {
                            item {
                                SealSettingItem(
                                    title = "外观",
                                    description = "主题颜色、浅色和深色模式",
                                    icon = Icons.Default.Tune
                                ) { settingsSection = "appearance" }
                            }
                            item {
                                SealSettingItem(
                                    title = "下载格式",
                                    description = "视频、音频、画质与文件格式",
                                    icon = Icons.Default.Download
                                ) { settingsSection = "format" }
                            }
                            item {
                                SealSettingItem(
                                    title = "字幕与播放列表",
                                    description = "字幕语言、播放列表与重复下载",
                                    icon = Icons.Default.List
                                ) { settingsSection = "advanced" }
                            }
                            item {
                                SealSettingItem(
                                    title = "下载目录",
                                    description = "Download/video-dl",
                                    icon = Icons.Default.Folder
                                ) { settingsSection = "storage" }
                            }
                            item {
                                SealSettingItem(
                                    title = "网站登录与 Cookie",
                                    description = "管理本机保存的网站授权",
                                    icon = Icons.Default.Link
                                ) {
                                    authorizedSites = SecureCookieStore.listAuthorizedSites(context)
                                    hasCookieJar = SecureCookieStore.hasCookieJar(context)
                                    selectedTab = 2
                                }
                            }
                            item {
                                SealSettingItem(
                                    title = "解析引擎",
                                    description = "yt-dlp 更新与下载并发设置",
                                    icon = Icons.Default.Refresh
                                ) { settingsSection = "engine" }
                            }
                            item {
                                SealSettingItem(
                                    title = "关于 video-dl",
                                    description = "版本信息、开源许可与隐私说明",
                                    icon = Icons.Default.Info
                                ) { settingsSection = "about" }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                when (settingsSection) {
                                    "appearance" -> "选择你喜欢的显示方式"
                                    "format" -> "设置默认下载的媒体类型和容器"
                                    "advanced" -> "字幕和播放列表的下载行为"
                                    "storage" -> "下载文件统一保存在本机公共目录"
                                    "engine" -> "管理本机解析引擎"
                                    else -> "关于 video-dl"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            when (settingsSection) {
                                "appearance" -> {
                                    SettingsGroup("外观", "应用的显示方式") {
                                        ChoiceChipRows(
                                            options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                                            selected = themeMode,
                                            onSelected = { themeMode = it }
                                        )
                                    }
                                }
                                "format" -> {
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
                                            Text("音频编码质量", style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            ChoiceChipRows(
                                                options = listOf("0" to "最佳", "2" to "高", "5" to "均衡", "7" to "省空间", "10" to "最小"),
                                                selected = audioQuality,
                                                onSelected = { audioQuality = it }
                                            )
                                        }
                                        SettingsToggleRow(
                                            "嵌入元数据",
                                            "将标题、作者等信息写入媒体文件",
                                            embedMetadata
                                        ) { embedMetadata = it }
                                        SettingsToggleRow(
                                            "嵌入封面图片",
                                            "将封面写入支持封面的音频或视频容器",
                                            embedThumbnail
                                        ) { embedThumbnail = it }
                                        SettingsToggleRow(
                                            "额外保存封面图片",
                                            "同时保留独立的 JPG / WebP 封面文件",
                                            writeThumbnail
                                        ) { writeThumbnail = it }
                                    }
                                }
                                "advanced" -> {
                                    SettingsGroup("字幕与播放列表", "字幕可单独保存，也可以封装进视频文件") {
                                        SettingsToggleRow(
                                            "下载网站字幕",
                                            "下载网站提供的人工字幕轨道",
                                            downloadSubtitles
                                        ) { downloadSubtitles = it }
                                        SettingsToggleRow(
                                            "下载自动生成字幕",
                                            "下载语音识别生成的字幕轨道",
                                            downloadAutoSubtitles
                                        ) { downloadAutoSubtitles = it }
                                        if (downloadSubtitles || downloadAutoSubtitles) {
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
                                            SettingsToggleRow(
                                                "将字幕嵌入视频",
                                                "关闭后字幕仍会以独立 .srt / .vtt 文件保存",
                                                embedSubtitles
                                            ) { embedSubtitles = it }
                                        }
                                        SettingsToggleRow(
                                            "下载整个播放列表",
                                            "开启后按顺序下载列表中的视频",
                                            downloadPlaylist
                                        ) { downloadPlaylist = it }
                                        SettingsToggleRow(
                                            "跳过已经下载过的视频",
                                            "在本机保存 yt-dlp 下载归档",
                                            skipExisting
                                        ) { skipExisting = it }
                                    }
                                    SettingsGroup("文件命名", "选择下载文件的名称规则") {
                                        ChoiceChipRows(
                                            options = listOf(
                                                "title" to "视频标题",
                                                "title_id" to "标题 + ID",
                                                "id_title" to "ID + 标题"
                                            ),
                                            selected = filenameTemplate,
                                            onSelected = { filenameTemplate = it }
                                        )
                                    }
                                }
                                "storage" -> {
                                    SettingsGroup("下载目录", "选择完成后媒体文件保存的位置") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Folder,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    downloadFolderLabel,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                    if (downloadTreeUri.isBlank())
                                                        "默认公共目录：Download/video-dl"
                                                    else "已获得该文件夹的长期读写授权",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { folderPicker.launch(null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("选择下载文件夹")
                                        }
                                        if (downloadTreeUri.isNotBlank()) {
                                            TextButton(
                                                onClick = {
                                                    runCatching {
                                                        context.contentResolver.releasePersistableUriPermission(
                                                            Uri.parse(downloadTreeUri),
                                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                                        )
                                                    }
                                                    preferences.edit().remove("download_tree_uri").apply()
                                                    downloadTreeUri = ""
                                                    Toast.makeText(
                                                        context,
                                                        "已恢复默认下载目录",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("恢复默认目录")
                                            }
                                        }
                                    }
                                }
                                "engine" -> {
                                    SettingsGroup("下载速度", "较高并发会增加网络和设备负载") {
                                        ChoiceChipRows(
                                            options = listOf("1" to "省流量", "2" to "稳定", "4" to "均衡", "6" to "快速", "8" to "高速"),
                                            selected = concurrentFragments.toString(),
                                            onSelected = { concurrentFragments = it.toIntOrNull() ?: 4 }
                                        )
                                    }
                                    SettingsGroup("网络与重试", "按网络情况控制下载方式与失败重试次数") {
                                        SettingsToggleRow(
                                            "使用 aria2c 加速",
                                            "使用内置 aria2c 下载器处理媒体分片",
                                            useAria2
                                        ) { useAria2 = it }
                                        Text("失败重试次数", style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        ChoiceChipRows(
                                            options = listOf("3" to "3 次", "10" to "10 次", "20" to "20 次", "30" to "30 次"),
                                            selected = downloadRetries.toString(),
                                            onSelected = { downloadRetries = it.toIntOrNull() ?: 10 }
                                        )
                                        OutlinedTextField(
                                            value = rateLimit,
                                            onValueChange = { rateLimit = it.filter { ch -> ch.isLetterOrDigit() || ch == '.' }.take(12) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            label = { Text("下载限速（可选）") },
                                            placeholder = { Text("如 2M、800K，留空不限速") },
                                            supportingText = { Text("支持 K / M / G；只限制单任务下载速度") },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                    }
                                    SettingsGroup("解析引擎", "自动跟进 YouTube 提取器的最新兼容修复") {
                                        SettingsToggleRow(
                                            "自动更新解析引擎",
                                            "每周检查 yt-dlp nightly，只在下载和解析空闲时执行",
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
                                            } else Text("检查并更新 yt-dlp nightly")
                                        }
                                        Text("nightly 更新比 stable 更及时；仅更新本机解析脚本，不会更新 VideoFetch 应用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                else -> {
                                    SettingsGroup("video-dl", "Android 本地视频与音频下载器") {
                                        Text("下载解析在本机完成，不经过第三方下载服务器。")
                                        Text("下载文件保存在 Download/video-dl。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("项目采用 GPL-3.0 开源许可。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        }
        if (showFormatSheet && preview != null) {
            ModalBottomSheet(
                onDismissRequest = { showFormatSheet = false },
                sheetState = formatSheetState,
                modifier = Modifier.fillMaxHeight(0.96f),
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                                Text(
                                    "完整格式流、字幕、封面、文件命名、网络和重试",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!audioOnly && videoOptions.isNotEmpty()) {
                                    Text("全部可用视频流", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "展开每个真实格式，查看编码、帧率、文件大小和格式 ID",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FormatSelectionList(
                                        options = videoOptions,
                                        selectedId = selectedVideoFormatId,
                                        onSelected = { id ->
                                            selectedVideoFormatId = id
                                            val selected = videoOptions.firstOrNull { it.formatId == id }
                                            maxHeight = selected?.height ?: 0
                                            if (selected?.hasAudio == true) {
                                                selectedAudioFormatId = ""
                                            } else if (selectedAudioFormatId.isBlank()) {
                                                selectedAudioFormatId =
                                                    bestAudioFormat(audioOptions)?.formatId.orEmpty()
                                            }
                                            preferences.edit().putInt("max_height", maxHeight).apply()
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                Text("字幕下载", fontWeight = FontWeight.SemiBold)
                                SettingsToggleRow(
                                    "下载网站字幕",
                                    "获取网站提供的人工字幕轨道",
                                    downloadSubtitles
                                ) { downloadSubtitles = it }
                                SettingsToggleRow(
                                    "下载自动生成字幕",
                                    "获取语音识别生成的字幕轨道",
                                    downloadAutoSubtitles
                                ) { downloadAutoSubtitles = it }
                                if (downloadSubtitles || downloadAutoSubtitles) {
                                    OutlinedTextField(
                                        value = subtitleLanguages,
                                        onValueChange = { subtitleLanguages = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        label = { Text("字幕语言") },
                                        placeholder = { Text("all 或 zh-Hans,zh-CN,en") }
                                    )
                                    SettingsToggleRow(
                                        "将字幕嵌入视频",
                                        "关闭后字幕以独立文件保存",
                                        embedSubtitles
                                    ) { embedSubtitles = it }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text("封面和媒体信息", fontWeight = FontWeight.SemiBold)
                                SettingsToggleRow(
                                    "嵌入元数据",
                                    "写入标题、作者和媒体信息",
                                    embedMetadata
                                ) { embedMetadata = it }
                                SettingsToggleRow(
                                    "嵌入封面图片",
                                    "将封面写入支持封面的媒体文件",
                                    embedThumbnail
                                ) { embedThumbnail = it }
                                SettingsToggleRow(
                                    "额外保存封面图片",
                                    "同时保存独立 JPG / WebP 图片",
                                    writeThumbnail
                                ) { writeThumbnail = it }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text("下载任务", fontWeight = FontWeight.SemiBold)
                                SettingsToggleRow(
                                    "下载整个播放列表",
                                    "默认只下载当前视频",
                                    downloadPlaylist
                                ) { downloadPlaylist = it }
                                SettingsToggleRow(
                                    "跳过已下载的视频",
                                    "在本机保存下载归档",
                                    skipExisting
                                ) { skipExisting = it }
                                SettingsToggleRow(
                                    "使用 aria2c 加速",
                                    "使用内置 aria2c 下载器获取媒体分片",
                                    useAria2
                                ) { useAria2 = it }
                                Text("分片并发", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChoiceChipRows(
                                    options = listOf(
                                        "1" to "省流量", "2" to "稳定", "4" to "均衡",
                                        "6" to "快速", "8" to "高速"
                                    ),
                                    selected = concurrentFragments.toString(),
                                    onSelected = { concurrentFragments = it.toIntOrNull() ?: 4 }
                                )
                                Text("失败重试次数", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChoiceChipRows(
                                    options = listOf(
                                        "3" to "3 次", "10" to "10 次",
                                        "20" to "20 次", "30" to "30 次"
                                    ),
                                    selected = downloadRetries.toString(),
                                    onSelected = { downloadRetries = it.toIntOrNull() ?: 10 }
                                )
                                OutlinedTextField(
                                    value = rateLimit,
                                    onValueChange = {
                                        rateLimit = it.filter { ch ->
                                            ch.isLetterOrDigit() || ch == '.'
                                        }.take(12)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("下载限速（可选）") },
                                    placeholder = { Text("如 2M、800K，留空不限速") },
                                    supportingText = { Text("支持 K / M / G 单位") },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Text("文件命名", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChoiceChipRows(
                                    options = listOf(
                                        "title" to "标题",
                                        "title_id" to "标题 + ID",
                                        "id_title" to "ID + 标题"
                                    ),
                                    selected = filenameTemplate,
                                    onSelected = { filenameTemplate = it }
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

private fun sourceHost(url: String): String {
    return runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }
        .getOrDefault("")
        .ifBlank { "媒体来源" }
}

@Composable
private fun SealVideoTaskCard(
    title: String,
    uploader: String,
    thumbnailUrl: String,
    isGridView: Boolean,
    stateLabel: String,
    progress: Int,
    progressKnown: Boolean,
    message: String,
    formatLabel: String,
    onActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        if (isGridView) {
            Column {
                SealTaskPoster(
                    thumbnailUrl = thumbnailUrl,
                    stateLabel = stateLabel,
                    progress = progress,
                    progressKnown = progressKnown,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
                SealTaskDetails(
                    title = title,
                    uploader = uploader,
                    progress = progress,
                    progressKnown = progressKnown,
                    message = message,
                    formatLabel = formatLabel,
                    onActions = onActions
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                SealTaskPoster(
                    thumbnailUrl = thumbnailUrl,
                    stateLabel = stateLabel,
                    progress = progress,
                    progressKnown = progressKnown,
                    modifier = Modifier.width(112.dp).aspectRatio(16f / 9f)
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        title.ifBlank { "视频下载任务" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        uploader.ifBlank { "媒体来源" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (formatLabel.isNotBlank()) {
                        Text(
                            formatLabel,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (progressKnown) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    if (message.isNotBlank()) {
                        Text(
                            message,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        content = onActions
                    )
                }
            }
        }
    }
}

@Composable
private fun SealTaskPoster(
    thumbnailUrl: String,
    stateLabel: String,
    progress: Int,
    progressKnown: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (thumbnailUrl.isNotBlank()) {
            VideoThumbnail(thumbnailUrl, Modifier.fillMaxSize())
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            color = Color.Black.copy(alpha = 0.68f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                stateLabel,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.Center).size(52.dp),
            color = Color.Black.copy(alpha = 0.52f),
            shape = RoundedCornerShape(50)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (progressKnown) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.24f)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp,
                        color = Color.White
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = Color.Black.copy(alpha = 0.68f)
        ) {
            Text(
                if (progressKnown) "${progress.coerceIn(0, 100)}%" else "video-dl",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SealTaskDetails(
    title: String,
    uploader: String,
    progress: Int,
    progressKnown: Boolean,
    message: String,
    formatLabel: String,
    onActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    title.ifBlank { "视频下载任务" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    uploader.ifBlank { "媒体来源" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (formatLabel.isNotBlank()) {
                Text(
                    formatLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progressKnown) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (message.isNotBlank()) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = onActions
        )
    }
}

@Composable
private fun ChoiceChipRows(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    SelectionGroupRow(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        options.forEach { (value, label) ->
            SelectionGroupItem(
                selected = selected == value,
                onClick = { onSelected(value) }
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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

private fun historyLocationUri(location: String): Uri {
    return if (location.startsWith("content://") || location.startsWith("file://")) {
        Uri.parse(location)
    } else {
        Uri.fromFile(File(location))
    }
}

private fun isAudioHistoryItem(item: DownloadHistoryItem): Boolean {
    val extension = item.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return item.formatLabel.contains("音频") ||
        extension in setOf("mp3", "m4a", "aac", "opus", "flac", "wav", "ogg")
}

private fun isPlayableMediaItem(item: DownloadHistoryItem): Boolean {
    val extension = item.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts", "mpeg", "mpg", "avi"
    )
    val audioExtensions = setOf(
        "mp3", "m4a", "aac", "opus", "flac", "wav", "ogg", "oga"
    )
    if (extension in videoExtensions || extension in audioExtensions) return true
    // SAF imports can legitimately have no file extension; the picker supplies
    // the MIME-derived type label for those entries.
    return extension.isBlank() &&
        (item.formatLabel == "视频" || item.formatLabel == "音频")
}

@OptIn(UnstableApi::class)
@Composable
private fun MediaPlaybackScreen(
    item: DownloadHistoryItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mediaUri = remember(item.location) { historyLocationUri(item.location) }
    val playbackError = remember(mediaUri) { mutableStateOf("") }
    val player = remember(mediaUri) {
        ExoPlayer.Builder(context).build().apply {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(
                    if (isAudioHistoryItem(item)) C.AUDIO_CONTENT_TYPE_MUSIC
                    else C.AUDIO_CONTENT_TYPE_MOVIE
                )
                .build()
            setAudioAttributes(audioAttributes, true)
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            playWhenReady = true
        }
    }
    val exoPlayer = player
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError.value = error.localizedMessage
                    ?: error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val audioOnly = remember(item) { isAudioHistoryItem(item) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回下载记录")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (audioOnly) "音频播放" else "视频播放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    item.fileName.ifBlank { "本地媒体" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (audioOnly) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(220.dp),
                    shape = RoundedCornerShape(48.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.padding(58.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    item.fileName.ifBlank { "正在播放音频" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    item.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(30.dp))
                AndroidView(
                    factory = { viewContext ->
                        PlayerControlView(viewContext).apply {
                            this.player = exoPlayer
                            setShowTimeoutMs(0)
                        }
                    },
                    update = { it.player = exoPlayer },
                    modifier = Modifier.fillMaxWidth()
                )
                if (playbackError.value.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            "播放失败：${playbackError.value}",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        } else {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(Color.Black)
            )
            Text(
                item.fileName,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "支持播放本地视频文件，控制栏可暂停、拖动进度和调整播放位置。",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (playbackError.value.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "播放失败：${playbackError.value}",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: DownloadHistoryItem,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val statusColor = when {
        item.statusLabel.contains("失败") -> MaterialTheme.colorScheme.error
        item.statusLabel.contains("完成") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.width(112.dp).aspectRatio(16f / 9f),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.68f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            if (item.formatLabel.contains("音频")) "音频" else "媒体",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.fileName.ifBlank { item.message.ifBlank { "下载任务" } },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(item.source, item.time).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            item.statusLabel,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                    if (item.formatLabel.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            item.formatLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (item.location.isNotBlank() && isPlayableMediaItem(item)) {
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "应用内播放",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (item.location.startsWith("content://")) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "文件操作")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("打开文件") },
                            leadingIcon = { Icon(Icons.Default.Folder, null) },
                            onClick = { expanded = false; onOpen() }
                        )
                        DropdownMenuItem(
                            text = { Text("分享文件") },
                            leadingIcon = { Icon(Icons.Default.ArrowForward, null) },
                            onClick = { expanded = false; onShare() }
                        )
                    }
                }
            }
        }
        if (item.message.isNotBlank() && item.fileName.isNotBlank()) {
            Text(
                item.message,
                modifier = Modifier.fillMaxWidth().padding(start = 124.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit,
    illustration: ImageVector? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (illustration != null) {
            Image(
                imageVector = illustration,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.78f).height(190.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(17.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onAction, shape = RoundedCornerShape(16.dp)) { Text(action) }
    }
}

