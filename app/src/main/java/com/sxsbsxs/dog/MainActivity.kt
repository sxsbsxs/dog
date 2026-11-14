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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DogApp(initialUrl: String?, onDownload: (String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("dog_settings", Context.MODE_PRIVATE) }
    var themeMode by rememberSaveable { mutableStateOf(preferences.getString("theme_mode", "system") ?: "system") }
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

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

    val pageTitle = when (selectedTab) {
        0 -> "Dog 下载器"
        1 -> "下载任务"
        2 -> "下载记录"
        else -> "设置"
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(pageTitle, fontWeight = FontWeight.Bold) },
                    actions = {
                        if (selectedTab == 2) {
                            IconButton(onClick = { historyJson = DownloadHistoryStore.read(context) }) {
                                Icon(Icons.Default.History, contentDescription = "刷新记录")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
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
                        label = { Text("记录") }
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
                        modifier = Modifier.fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(Modifier.padding(22.dp)) {
                                Text(
                                    "把视频保存到本机",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "粘贴公开视频链接，选择画质后即可下载",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AssistChip(
                                        onClick = { selectedTab = 3 },
                                        label = { Text(if (hasCookieJar) "账号已授权" else "可选：登录提升画质") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    AssistChip(
                                        onClick = { selectedTab = 3 },
                                        label = { Text(if (audioOnly) "音频模式" else if (maxHeight == 0) "最高画质" else "\${maxHeight}p 上限") }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("视频链接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    preferences.edit().putString("last_input_url", it).apply()
                                    preview = null
                                    previewError = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                placeholder = { Text("粘贴 YouTube、B站、抖音等链接") },
                                trailingIcon = {
                                    if (url.isNotEmpty()) {
                                        IconButton(onClick = {
                                            url = ""
                                            preview = null
                                            previewError = ""
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "清空")
                                        }
                                    } else {
                                        IconButton(onClick = pasteUrl) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(onGo = { startDownload() })
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = pasteUrl,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("粘贴链接")
                                }
                                OutlinedButton(
                                    onClick = startPreview,
                                    enabled = url.isNotBlank() && !previewLoading && !download.running && !engineUpdating,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (previewLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Tune, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (previewLoading) "解析中" else "解析信息")
                                }
                            }
                            Button(
                                onClick = startDownload,
                                enabled = url.isNotBlank() && !previewLoading && !engineUpdating,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (download.running) "加入下载队列" else "开始下载",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "默认下载当前视频；整张播放列表可在设置中开启。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (preview != null) {
                            val info = preview!!
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("视频信息", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    if (info.uploader.isNotBlank()) Text("作者：\${info.uploader}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        listOf(info.duration, info.resolution, info.fileSize)
                                            .filter { it.isNotBlank() && it != "未知" }
                                            .joinToString(" · "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("来源：\${info.extractor}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (previewError.isNotBlank()) {
                            Text(previewError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        if (download.running) {
                            ActiveDownloadCard(
                                message = download.message,
                                progress = download.progress,
                                queuedCount = download.queuedCount,
                                outputPath = download.outputPath,
                                onCancel = { DownloadService.cancel(context) },
                                onClearQueue = { DownloadService.clearQueue(context) }
                            )
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (download.running) {
                            ActiveDownloadCard(
                                message = download.message,
                                progress = download.progress,
                                queuedCount = download.queuedCount,
                                outputPath = download.outputPath,
                                onCancel = { DownloadService.cancel(context) },
                                onClearQueue = { DownloadService.clearQueue(context) }
                            )
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("队列状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (download.queuedCount > 0) "还有 \${download.queuedCount} 个任务等待下载"
                                        else "当前没有等待任务",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "队列按顺序执行。清空等待队列不会影响正在下载的视频。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            EmptyState(
                                title = if (download.progress == 100) "最近一次下载已完成" else "当前没有下载任务",
                                description = download.message,
                                action = "去粘贴视频链接",
                                onAction = { selectedTab = 0 }
                            )
                        }
                    }
                }

                2 -> {
                    val historyItems = remember(historyJson) { parseDownloadHistory(historyJson) }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("最近下载", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("\${historyItems.size} 条记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = {
                                    DownloadHistoryStore.clear(context)
                                    historyJson = "[]"
                                },
                                enabled = historyItems.isNotEmpty()
                            ) { Text("清空") }
                        }
                        if (historyItems.isEmpty()) {
                            EmptyState(
                                title = "还没有下载记录",
                                description = "下载完成或失败后，记录会自动出现在这里。",
                                action = "开始下载",
                                onAction = { selectedTab = 0 }
                            )
                        } else {
                            historyItems.forEach { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            item.fileName.ifBlank { item.message.ifBlank { "下载任务" } },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            listOf(item.source, item.time, item.statusLabel)
                                                .filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.message.isNotBlank() && item.fileName.isNotBlank()) {
                                            Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (item.location.startsWith("content://")) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = {
                                                    runCatching {
                                                        val uri = Uri.parse(item.location)
                                                        val mime = context.contentResolver.getType(uri) ?: "*/*"
                                                        val intent = Intent(Intent.ACTION_VIEW)
                                                            .setDataAndType(uri, mime)
                                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        context.startActivity(intent)
                                                    }.onFailure {
                                                        Toast.makeText(context, "无法打开该文件", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) { Text("打开文件") }
                                                TextButton(onClick = {
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
                                                }) { Text("分享") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SettingsSection(title = "外观", description = "选择应用显示方式") {
                            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = themeMode == option.first, onClick = { themeMode = option.first })
                                    Text(option.second, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        SettingsSection(title = "下载类型", description = "常用选项会自动保存") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !audioOnly, onClick = { audioOnly = false })
                                Text("视频")
                                Spacer(Modifier.width(18.dp))
                                RadioButton(selected = audioOnly, onClick = { audioOnly = true })
                                Text("音频")
                            }
                            HorizontalDivider()
                            if (!audioOnly) {
                                Text("画质上限", style = MaterialTheme.typography.titleSmall)
                                listOf(0 to "最高画质", 2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p").forEach { option ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = maxHeight == option.first, onClick = { maxHeight = option.first })
                                        Text(option.second)
                                    }
                                }
                                HorizontalDivider()
                                Text("视频格式", style = MaterialTheme.typography.titleSmall)
                                listOf("mp4" to "MP4 · 通用", "mkv" to "MKV · 字幕兼容", "webm" to "WebM · 开放格式").forEach { option ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = videoContainer == option.first, onClick = { videoContainer = option.first })
                                        Text(option.second)
                                    }
                                }
                            } else {
                                Text("音频格式", style = MaterialTheme.typography.titleSmall)
                                listOf("mp3" to "MP3", "m4a" to "M4A", "opus" to "Opus", "flac" to "FLAC").forEach { option ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = audioFormat == option.first, onClick = { audioFormat = option.first })
                                        Text(option.second)
                                    }
                                }
                                SettingSwitch(
                                    title = "嵌入封面图片",
                                    checked = embedThumbnail,
                                    onCheckedChange = { embedThumbnail = it }
                                )
                            }
                        }

                        SettingsSection(title = "字幕与列表", description = "播放列表默认关闭，避免意外下载大量视频") {
                            SettingSwitch(
                                title = "同时下载字幕",
                                checked = downloadSubtitles,
                                onCheckedChange = { downloadSubtitles = it }
                            )
                            if (downloadSubtitles) {
                                OutlinedTextField(
                                    value = subtitleLanguages,
                                    onValueChange = { subtitleLanguages = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("字幕语言") },
                                    placeholder = { Text("all 或 zh-Hans,zh-CN,en") },
                                    supportingText = { Text("输入 all 下载所有语言，或填写语言代码") }
                                )
                            }
                            SettingSwitch(
                                title = "下载整个播放列表",
                                checked = downloadPlaylist,
                                onCheckedChange = { downloadPlaylist = it }
                            )
                            SettingSwitch(
                                title = "跳过已经下载过的视频",
                                checked = skipExisting,
                                onCheckedChange = { skipExisting = it }
                            )
                        }

                        SettingsSection(title = "下载速度", description = "较高并发可能增加流量和设备负载") {
                            listOf(1 to "省流量", 4 to "均衡", 8 to "高速").forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = concurrentFragments == option.first, onClick = { concurrentFragments = option.first })
                                    Text(option.second)
                                }
                            }
                        }

                        SettingsSection(title = "账号授权", description = "授权仅保存在本机，并使用 Android Keystore 加密") {
                            Text(
                                if (hasCookieJar) "已保存 \${authorizedSites.size} 个网站授权配置" else "尚未保存网站授权",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (authorizedSites.isNotEmpty()) {
                                authorizedSites.forEach { site ->
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(site, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        TextButton(onClick = {
                                            SecureCookieStore.removeAuthorizedSite(context, site)
                                            authorizedSites = SecureCookieStore.listAuthorizedSites(context)
                                            hasCookieJar = SecureCookieStore.hasCookieJar(context)
                                            Toast.makeText(context, "已移除 $site 的授权", Toast.LENGTH_SHORT).show()
                                        }) { Text("移除") }
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    val target = url.trim().ifBlank { preferences.getString("last_input_url", "").orEmpty() }
                                    if (!isHttpUrl(target)) {
                                        Toast.makeText(context, "先在下载页粘贴一个该网站的视频链接", Toast.LENGTH_SHORT).show()
                                    } else {
                                        loginLauncher.launch(
                                            Intent(context, LoginActivity::class.java)
                                                .putExtra(LoginActivity.EXTRA_TARGET_URL, target)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("添加网站登录授权")
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

                        SettingsSection(title = "解析引擎", description = "当网站改版导致解析失败时，可尝试更新 yt-dlp 提取器") {
                            OutlinedButton(
                                onClick = {
                                    engineUpdating = true
                                    EngineUpdateManager.update(context) { message ->
                                        engineUpdating = false
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !engineUpdating && !download.running && !previewLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (engineUpdating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在更新解析引擎…")
                                } else {
                                    Text("检查并更新 yt-dlp")
                                }
                            }
                            Text(
                                "这只更新本机网站解析脚本，不会更新 Dog 应用本身。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            "Dog · 本地视频下载器",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
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
private fun ActiveDownloadCard(
    message: String,
    progress: Int,
    queuedCount: Int,
    outputPath: String,
    onCancel: () -> Unit,
    onClearQueue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("正在下载", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("\${progress}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            if (queuedCount > 0) {
                Text("另有 \${queuedCount} 个任务排队中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = onClearQueue, modifier = Modifier.fillMaxWidth()) {
                    Text("只清空等待队列")
                }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("取消当前任务和全部队列")
            }
            if (outputPath.isNotBlank()) {
                Text("保存位置：$outputPath", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

