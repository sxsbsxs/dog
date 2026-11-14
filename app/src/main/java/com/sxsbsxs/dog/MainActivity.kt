package com.sxsbsxs.dog

import android.Manifest
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)
    private var pendingDownloadUrl: String? = null

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pending = pendingDownloadUrl
            pendingDownloadUrl = null
            if (granted && !pending.isNullOrBlank()) {
                DownloadService.start(this, pending)
            } else if (!granted) {
                Toast.makeText(this, "需要存储权限才能写入系统下载目录", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl = extractHttpUrl(sharedText(intent))

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        setContent {
            DogApp(
                initialUrl = sharedUrl,
                onDownload = ::startDownload
            )
        }
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

    private fun startDownload(url: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadUrl = url
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        DownloadService.start(this, url)
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
    val message: String
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
                        message = item.optString("message")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun isHttpUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)

private val DogLightColors = lightColorScheme(
    primary = Color(0xFF4657B8), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E5FF), onPrimaryContainer = Color(0xFF111C55),
    secondary = Color(0xFF59617C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E6F5), onSecondaryContainer = Color(0xFF171E36),
    tertiary = Color(0xFF76558E), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1DAFF), onTertiaryContainer = Color(0xFF2A103F),
    background = Color(0xFFF7F7FB), onBackground = Color(0xFF191B24),
    surface = Color(0xFFFFFBFF), onSurface = Color(0xFF191B24),
    surfaceVariant = Color(0xFFE8E9F1), onSurfaceVariant = Color(0xFF464957),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F1F7),
    surfaceContainer = Color(0xFFECECF3),
    surfaceContainerHigh = Color(0xFFE6E7EF),
    outline = Color(0xFF777B8C), outlineVariant = Color(0xFFC7C8D4),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DogDarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF), onPrimary = Color(0xFF202D83),
    primaryContainer = Color(0xFF35469F), onPrimaryContainer = Color(0xFFE1E5FF),
    secondary = Color(0xFFC2C7E0), onSecondary = Color(0xFF2B3148),
    secondaryContainer = Color(0xFF424862), onSecondaryContainer = Color(0xFFE0E4FF),
    tertiary = Color(0xFFE0B8F7), onTertiary = Color(0xFF43245A),
    tertiaryContainer = Color(0xFF5C3B72), onTertiaryContainer = Color(0xFFF1DAFF),
    background = Color(0xFF11131B), onBackground = Color(0xFFE4E5F0),
    surface = Color(0xFF171923), onSurface = Color(0xFFE4E5F0),
    surfaceVariant = Color(0xFF414452), onSurfaceVariant = Color(0xFFC3C6D5),
    surfaceContainerLowest = Color(0xFF0C0E15),
    surfaceContainerLow = Color(0xFF1D202B),
    surfaceContainer = Color(0xFF222531),
    surfaceContainerHigh = Color(0xFF2D303D),
    outline = Color(0xFF8D91A2), outlineVariant = Color(0xFF424654),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DogApp(initialUrl: String?, onDownload: (String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("dog_settings", Context.MODE_PRIVATE) }
    var themeMode by rememberSaveable {
        mutableStateOf(preferences.getString("theme_mode", "system") ?: "system")
    }
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DogDarkColors else DogLightColors

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var hasCookieJar by remember { mutableStateOf(SecureCookieStore.hasCookieJar(context)) }
    var authorizedSites by remember { mutableStateOf(SecureCookieStore.listAuthorizedSites(context)) }
    var historyJson by remember { mutableStateOf(DownloadHistoryStore.read(context)) }

    var audioOnly by rememberSaveable { mutableStateOf(preferences.getBoolean("audio_only", false)) }
    var maxHeight by rememberSaveable { mutableStateOf(preferences.getInt("max_height", 0)) }
    var downloadSubtitles by rememberSaveable { mutableStateOf(preferences.getBoolean("download_subtitles", false)) }
    var downloadPlaylist by rememberSaveable { mutableStateOf(preferences.getBoolean("download_playlist", false)) }
    var videoContainer by rememberSaveable { mutableStateOf(preferences.getString("video_container", "mp4") ?: "mp4") }
    var audioFormat by rememberSaveable { mutableStateOf(preferences.getString("audio_format", "mp3") ?: "mp3") }
    var subtitleLanguages by rememberSaveable { mutableStateOf(preferences.getString("subtitle_languages", "all") ?: "all") }
    var embedThumbnail by rememberSaveable { mutableStateOf(preferences.getBoolean("embed_thumbnail", true)) }
    var skipExisting by rememberSaveable { mutableStateOf(preferences.getBoolean("skip_existing", false)) }
    var concurrentFragments by rememberSaveable { mutableStateOf(preferences.getInt("concurrent_fragments", 4)) }

    var previewLoading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<VideoInfoService.Preview?>(null) }
    var previewError by remember { mutableStateOf("") }
    var engineUpdating by remember { mutableStateOf(false) }
    val download by DownloadState.state.collectAsState()

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authorizedSites = SecureCookieStore.listAuthorizedSites(context)
        hasCookieJar = SecureCookieStore.hasCookieJar(context)
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "网站授权已保存在本机", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) url = initialUrl
    }

    LaunchedEffect(
        themeMode, audioOnly, maxHeight, downloadSubtitles, downloadPlaylist,
        videoContainer, audioFormat, subtitleLanguages, embedThumbnail,
        skipExisting, concurrentFragments
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
            .apply()
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
            preferences.edit().putString("last_input_url", pastedUrl).apply()
            preview = null
            previewError = ""
        } else {
            Toast.makeText(context, "剪贴板里没有找到视频链接", Toast.LENGTH_SHORT).show()
        }
    }

    val startPreview: () -> Unit = {
        val value = url.trim()
        if (!isHttpUrl(value)) {
            Toast.makeText(context, "先粘贴有效的视频链接", Toast.LENGTH_SHORT).show()
        } else if (download.running || engineUpdating) {
            Toast.makeText(context, "当前任务进行中，暂不能解析", Toast.LENGTH_SHORT).show()
        } else {
            previewLoading = true
            preview = null
            previewError = ""
            VideoInfoService.inspect(context, value, object : VideoInfoService.Callback {
                override fun onSuccess(result: VideoInfoService.Preview) {
                    previewLoading = false
                    if (url.trim() == value) preview = result
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
        if (engineUpdating || previewLoading) {
            Toast.makeText(context, "请等待当前操作完成", Toast.LENGTH_SHORT).show()
        } else if (!isHttpUrl(value)) {
            Toast.makeText(context, "请输入有效的视频链接", Toast.LENGTH_SHORT).show()
        } else {
            preferences.edit().putString("last_input_url", value).apply()
            onDownload(value)
            url = ""
            preview = null
            previewError = ""
            selectedTab = 1
        }
    }

    val pageLabel = when (selectedTab) {
        0 -> "下载"
        1 -> "任务"
        2 -> "历史"
        else -> "设置"
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text("Dog", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(pageLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("下载") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("任务") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            historyJson = DownloadHistoryStore.read(context)
                            selectedTab = 2
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("历史") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = {
                            authorizedSites = SecureCookieStore.listAuthorizedSites(context)
                            hasCookieJar = SecureCookieStore.hasCookieJar(context)
                            selectedTab = 3
                        },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState()).imePadding()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("保存你喜欢的视频", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("粘贴视频链接，剩下的交给 Dog。", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("视频链接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("支持 yt-dlp 网站", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    preferences.edit().putString("last_input_url", it).apply()
                                    preview = null
                                    previewError = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 3,
                                shape = RoundedCornerShape(20.dp),
                                placeholder = { Text("粘贴 YouTube、B站、抖音等视频链接") },
                                trailingIcon = {
                                    if (url.isNotEmpty()) {
                                        IconButton(onClick = {
                                            url = ""
                                            preview = null
                                            previewError = ""
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "清空链接")
                                        }
                                    } else {
                                        IconButton(onClick = pasteUrl) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴链接")
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(onGo = { startDownload() })
                            )
                            Text("也可以从其他 App 分享视频链接到 Dog。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("当前下载方案", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (audioOnly) "音频 · " + audioFormat
                                        else "视频 · " + (if (maxHeight == 0) "最高画质" else maxHeight.toString() + "p") + " · " + videoContainer.uppercase(Locale.ROOT),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(onClick = { selectedTab = 3 }) {
                                    Text("调整")
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Button(
                            onClick = startDownload,
                            enabled = url.isNotBlank() && !previewLoading && !engineUpdating,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(9.dp))
                            Text(if (download.running) "加入下载队列" else "开始下载", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (previewLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(7.dp))
                                Text("正在解析视频信息…", style = MaterialTheme.typography.labelMedium)
                            } else {
                                TextButton(
                                    onClick = startPreview,
                                    enabled = url.isNotBlank() && !download.running && !engineUpdating
                                ) { Text("下载前查看视频信息") }
                            }
                        }

                        if (preview != null) {
                            val info = preview!!
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("视频预览", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (info.uploader.isNotBlank()) {
                                        Text("作者：" + info.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        listOf(info.duration, info.resolution, info.fileSize).filter { it.isNotBlank() && it != "未知" }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (previewError.isNotBlank()) {
                            Text(previewError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        if (download.running) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("有下载任务正在进行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(download.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(onClick = { selectedTab = 1 }) { Text("查看") }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("下载任务", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("查看当前进度和等待中的下载。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                                            Text("正在处理视频", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (download.queuedCount > 0) "还有 " + download.queuedCount + " 个等待任务" else "当前队列正在执行",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(download.progress.toString() + "%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    LinearProgressIndicator(progress = { download.progress / 100f }, modifier = Modifier.fillMaxWidth())
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
                        } else {
                            EmptyState(
                                icon = if (download.progress == 100) Icons.Default.CheckCircle else Icons.Default.Download,
                                title = if (download.progress == 100) "下载已完成" else "目前没有下载任务",
                                description = if (download.outputPath.isNotBlank()) download.outputPath else download.message,
                                action = if (download.progress == 100) "查看下载历史" else "去粘贴视频链接",
                                onAction = { selectedTab = if (download.progress == 100) 2 else 0 }
                            )
                        }
                    }
                }

                2 -> {
                    val historyItems = remember(historyJson) { parseDownloadHistory(historyJson) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("下载历史", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(historyItems.size.toString() + " 条本机记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = {
                                        DownloadHistoryStore.clear(context)
                                        historyJson = "[]"
                                    },
                                    enabled = historyItems.isNotEmpty()
                                ) { Text("清空") }
                            }
                        }
                        if (historyItems.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.History,
                                    title = "还没有下载记录",
                                    description = "完成或失败的任务会保存在这里。",
                                    action = "开始下载",
                                    onAction = { selectedTab = 0 }
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

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("按你的使用习惯调整下载方式。修改会自动保存。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

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
                                Text("画质上限", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChoiceChipRows(
                                    options = listOf("0" to "最高", "2160" to "4K", "1440" to "1440p", "1080" to "1080p", "720" to "720p", "480" to "480p"),
                                    selected = maxHeight.toString(),
                                    onSelected = { maxHeight = it.toIntOrNull() ?: 0 }
                                )
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

                        SettingsGroup("网站账号", "登录仅保存在本机并加密，不需要手动复制 Cookie") {
                            Text(
                                if (hasCookieJar) "已保存 " + authorizedSites.size + " 个网站授权" else "尚未添加网站授权",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            authorizedSites.forEach { site ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(site, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = {
                                        SecureCookieStore.removeAuthorizedSite(context, site)
                                        authorizedSites = SecureCookieStore.listAuthorizedSites(context)
                                        hasCookieJar = SecureCookieStore.hasCookieJar(context)
                                        Toast.makeText(context, "已移除 " + site + " 的授权", Toast.LENGTH_SHORT).show()
                                    }) { Text("移除") }
                                }
                            }
                            Button(
                                onClick = {
                                    val target = url.trim().ifBlank { preferences.getString("last_input_url", "").orEmpty() }
                                    if (!isHttpUrl(target)) {
                                        Toast.makeText(context, "先在下载页粘贴一个该网站的视频链接", Toast.LENGTH_SHORT).show()
                                    } else {
                                        loginLauncher.launch(
                                            Intent(context, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_TARGET_URL, target)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("添加网站登录授权") }
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

                        SettingsGroup("解析引擎", "网站改版导致解析失败时，可以尝试更新 yt-dlp") {
                            OutlinedButton(
                                onClick = {
                                    engineUpdating = true
                                    EngineUpdateManager.update(context) { message ->
                                        engineUpdating = false
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !engineUpdating && !download.running && !previewLoading,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (engineUpdating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在更新…")
                                } else Text("检查并更新 yt-dlp")
                            }
                            Text("这只更新本机的网站解析脚本，不会更新 Dog 应用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Dog · 本地视频下载器", modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.fileName.ifBlank { item.message.ifBlank { "下载任务" } },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(item.statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
        }
        Text(
            listOf(item.source, item.time).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (item.message.isNotBlank() && item.fileName.isNotBlank()) {
            Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (item.location.startsWith("content://")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("打开文件") }
                TextButton(onClick = onShare) { Text("分享") }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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

