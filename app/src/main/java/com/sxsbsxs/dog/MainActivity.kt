package com.sxsbsxs.dog

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    var cookie by rememberSaveable { mutableStateOf(SecureCookieStore.load(context)) }
    var cookieDialog by remember { mutableStateOf(false) }
    val download by DownloadState.state.collectAsState()

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) url = initialUrl
    }

    val startDownload: () -> Unit = {
        val value = url.trim()
        if (!isHttpUrl(value)) {
            Toast.makeText(context, "请输入有效的视频链接", Toast.LENGTH_SHORT).show()
        } else {
            onDownload(value)
        }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Dog", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { cookieDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Cookie 设置")
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
                    enabled = url.isNotBlank() && !download.running,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("开始下载", fontWeight = FontWeight.SemiBold)
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

    if (cookieDialog) {
        AlertDialog(
            onDismissRequest = { cookieDialog = false },
            title = { Text("Cookie 设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "需要登录或更高画质时，粘贴浏览器复制的 Cookie 请求头。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = cookie,
                        onValueChange = { cookie = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                        label = { Text("Cookie") },
                        placeholder = { Text("name=value; …") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                    )
                    Text(
                        "Cookie 使用 Android Keystore 加密后保存在本机，不会上传到服务器。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { SecureCookieStore.save(context, cookie.trim()) }
                        .onSuccess {
                            cookieDialog = false
                            Toast.makeText(context, "Cookie 已加密保存在本机", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(
                                context,
                                "Cookie 保存失败：" + (it.message ?: "设备安全存储不可用"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        cookie = ""
                        runCatching { SecureCookieStore.save(context, "") }
                        cookieDialog = false
                    }) { Text("清除") }
                    TextButton(onClick = { cookieDialog = false }) { Text("关闭") }
                }
            }
        )
    }
}
