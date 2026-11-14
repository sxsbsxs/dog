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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var hasCookieJar by remember { mutableStateOf(SecureCookieStore.hasCookieJar(context)) }
    var cookieDialog by remember { mutableStateOf(false) }
    var historyDialog by remember { mutableStateOf(false) }
    var historyJson by remember { mutableStateOf(DownloadHistoryStore.read(context)) }
    val download by DownloadState.state.collectAsState()
    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hasCookieJar = SecureCookieStore.hasCookieJar(context)
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "网站登录状态已保存", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) url = initialUrl
    }

    val startDownload: () -> Unit = {
        val value = url.trim()
        if (!isHttpUrl(value)) {
            Toast.makeText(context, "请输入有效的视频链接", Toast.LENGTH_SHORT).show()
        } else {
            onDownload(value)
            url = ""
        }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Dog", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = {
                            historyJson = DownloadHistoryStore.read(context)
                            historyDialog = true
                        }) {
                            Icon(Icons.Default.History, contentDescription = "下载记录")
                        }
                        IconButton(onClick = { cookieDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "账号授权")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .imePadding().padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(22.dp))
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(84.dp)
                ) {
                    Icon(
                        Icons.Default.Download, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "视频，直接下载",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "粘贴链接即可。解析与下载都在本机完成。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(30.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("视频链接") },
                    placeholder = { Text("https://…") },
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { url = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空链接")
                            }
                        } else {
                            IconButton(onClick = {
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
                                } else {
                                    Toast.makeText(context, "剪贴板里没有找到视频链接", Toast.LENGTH_SHORT).show()
                                }
                            }) {
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
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = startDownload,
                    enabled = url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (download.running) "加入下载队列" else "开始下载", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (download.running) "下载任务" else if (download.progress == 100) "已完成" else "下载状态",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (download.running) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            download.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (download.running || download.progress > 0) {
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { download.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                download.progress.toString() + "%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (download.running) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { DownloadService.cancel(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("取消下载")
                            }
                        }
                        if (download.outputPath.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "保存位置：" + download.outputPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "由 yt-dlp 驱动 · 支持其所支持的网站",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (historyDialog) {
        val historyItems = remember(historyJson) { parseDownloadHistory(historyJson) }
        AlertDialog(
            onDismissRequest = { historyDialog = false },
            title = { Text("下载记录") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (historyItems.isEmpty()) {
                        Text(
                            "还没有下载记录。完成或失败的任务会自动保存在这里。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    historyItems.forEach { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    item.fileName.ifBlank { item.message.ifBlank { "下载任务" } },
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    listOf(item.source, item.time, item.statusLabel)
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.location.startsWith("content://")) {
                                    TextButton(onClick = {
                                        runCatching {
                                            val uri = Uri.parse(item.location)
                                            val mime = context.contentResolver.getType(uri) ?: "*/*"
                                            val openIntent = Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(uri, mime)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            context.startActivity(openIntent)
                                        }.onFailure {
                                            Toast.makeText(context, "无法打开该文件", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text("打开文件") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyDialog = false }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(onClick = {
                    DownloadHistoryStore.clear(context)
                    historyJson = "[]"
                }, enabled = historyItems.isNotEmpty()) { Text("清空记录") }
            }
        )
    }

    if (cookieDialog) {
        AlertDialog(
            onDismissRequest = { cookieDialog = false },
            title = { Text("网站账号授权") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "不需要手动查找 Cookie。先填写视频链接，然后在应用内打开对应网站登录，登录完成后点击“完成授权”。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (hasCookieJar) "本机已保存网页登录 Cookie，仅对已登录的网站生效。" else "当前尚未保存网页登录状态。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "登录信息只保存在本机并经过加密。下载时会发送给对应的网站；部分平台可能限制应用内登录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = url.trim()
                    if (!isHttpUrl(target)) {
                        Toast.makeText(context, "请先粘贴要下载的视频链接", Toast.LENGTH_SHORT).show()
                    } else {
                        cookieDialog = false
                        val intent = Intent(context, LoginActivity::class.java)
                            .putExtra(LoginActivity.EXTRA_TARGET_URL, target)
                        loginLauncher.launch(intent)
                    }
                }) { Text("网页登录授权") }
            },
            dismissButton = {
                Row {
                    if (hasCookieJar) {
                        TextButton(onClick = {
                            SecureCookieStore.clear(context)
                            hasCookieJar = false
                            cookieDialog = false
                            Toast.makeText(context, "已清除本机授权", Toast.LENGTH_SHORT).show()
                        }) { Text("清除授权") }
                    }
                    TextButton(onClick = { cookieDialog = false }) { Text("关闭") }
                }
            }
        )
    }
}
