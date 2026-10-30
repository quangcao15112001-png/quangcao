package com.angravity.videodownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0B14)
private val Card = Color(0xFF161626)
private val Violet = Color(0xFF7C3AED)
private val Pink = Color(0xFFEC4899)
private val Muted = Color(0xFF9CA3AF)
private val Green = Color(0xFF34D399)
private val Red = Color(0xFFF87171)
private val Accent = Brush.horizontalGradient(listOf(Violet, Pink))

class MainActivity : ComponentActivity() {

    private val sharedUrl = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Violet, background = Bg, surface = Card)) {
                HomeScreen(sharedUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { sharedUrl.value = it }
    }
}

@Composable
private fun HomeScreen(urlState: MutableState<String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by DownloadStore.tasks.collectAsStateWithLifecycle()
    var url by urlState
    var updating by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
    }

    fun pasteFromClipboard() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        extractUrl(text)?.let { url = it } ?: Toast.makeText(context, "Clipboard không có link", Toast.LENGTH_SHORT).show()
    }

    fun startDownload() {
        val link = extractUrl(url)
        if (link == null) {
            Toast.makeText(context, "Link không hợp lệ", Toast.LENGTH_SHORT).show(); return
        }
        DownloadService.start(context, link)
        url = ""
    }

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Video Downloader", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text("Facebook • YouTube • TikTok", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))

        // Input card
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card)
                .border(1.dp, Brush.linearGradient(listOf(Violet.copy(.6f), Pink.copy(.4f))), RoundedCornerShape(24.dp))
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Dán link video vào đây...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = { TextButton(onClick = ::pasteFromClipboard) { Text("Dán", color = Pink) } },
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = ::startDownload, modifier = Modifier.fillMaxSize()) {
                    Text("⬇  TẢI VỀ MÁY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Mẹo: trong FB/YouTube/TikTok bấm Chia sẻ → \"Tải video về máy\" để tải ngay.",
                color = Muted, fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lịch sử", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(enabled = !updating, onClick = {
                updating = true
                scope.launch {
                    val msg = withContext(Dispatchers.IO) {
                        runCatching { YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext) }
                            .fold({ "Engine: ${it?.name ?: "OK"}" }, { "Cập nhật lỗi: ${it.message}" })
                    }
                    updating = false
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }) { Text(if (updating) "Đang cập nhật..." else "Cập nhật engine", color = Muted, fontSize = 12.sp) }
            TextButton(onClick = DownloadStore::clearFinished) { Text("Xóa", color = Muted, fontSize = 12.sp) }
        }

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text("Chưa có video nào.\nDán link hoặc chia sẻ từ app để bắt đầu.", color = Muted, fontSize = 14.sp)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(tasks, key = { it.id }) { TaskItem(it) }
        }
    }
}

@Composable
private fun TaskItem(task: DownloadTask) {
    val progress by animateFloatAsState(task.progress, label = "progress")
    val color = when (task.status) {
        Status.DONE -> Green
        Status.FAILED -> Red
        else -> Pink
    }
    Column(
        Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(18.dp)).background(Card).padding(14.dp),
    ) {
        Text(
            task.title.ifBlank { task.url }, color = Color.White, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        if (task.status == Status.DOWNLOADING || task.status == Status.QUEUED) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Pink, trackColor = Color(0xFF2A2A40),
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(task.message, color = color, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
