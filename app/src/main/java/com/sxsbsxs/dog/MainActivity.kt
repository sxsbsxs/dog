package com.sxsbsxs.dog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        setContent { DogApp(initialUrl = sharedUrl(intent)) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = sharedUrl(intent)
        if (!url.isNullOrBlank()) {
            Toast.makeText(this, "链接已填入，请点击开始下载", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private fun sharedUrl(intent: Intent?): String? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DogApp(initialUrl: String?) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var cookie by rememberSaveable {
        mutableStateOf(context.getSharedPreferences("dog_settings", 0).getString("cookie", "").orEmpty())
    }
    var cookieDialog by remember { mutableStateOf(false) }
    val download by DownloadState.state.collectAsState()

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
                Text("视频，直接下载", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("粘贴链接即可。解析与下载都在本机完成。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(30.dp))

                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(18.dp), label = { Text("视频链接") },
                    placeholder = { Text("https://…") },
                    trailingIcon = {
                        if (url.isNotEmpty()) IconButton(onClick = { url = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空链接")
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        val value = url.trim()
                        if (!(value.startsWith("https://", true) || value.startsWith("http://", true))) {
                            Toast.makeText(context, "请输入有效的视频链接", Toast.LENGTH_SHORT).show()
                        } else {
                            runCatching { DownloadService.start(context, value) }
                                .onFailure {
                                    Toast.makeText(context, "无法启动下载：${it.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
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
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (download.running) "下载任务" else "就绪",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (download.running) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(download.message, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (download.running || download.progress > 0) {
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { download.progress / 100f },
                                modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text("${download.progress}%", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (download.running) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { DownloadService.cancel(context) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Text("取消下载")
                            }
                        }
                        if (download.outputPath.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text("保存位置：${download.outputPath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("由 yt-dlp 驱动 · 支持其所支持的网站",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("可选。需要登录或更高画质时，粘贴浏览器复制的 Cookie 请求头内容。",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = cookie, onValueChange = { cookie = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Cookie") }, placeholder = { Text("name=value; …") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                    )
                    Text("仅存储在本机应用数据中；下载时会随请求发送给对应的视频网站。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("dog_settings", 0).edit().putString("cookie", cookie.trim()).apply()
                    cookieDialog = false
                    Toast.makeText(context, "Cookie 已保存在本机", Toast.LENGTH_SHORT).show()
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        cookie = ""
                        context.getSharedPreferences("dog_settings", 0).edit().remove("cookie").apply()
                        cookieDialog = false
                    }) { Text("清除") }
                    TextButton(onClick = { cookieDialog = false }) { Text("关闭") }
                }
            }
        )
    }
}
