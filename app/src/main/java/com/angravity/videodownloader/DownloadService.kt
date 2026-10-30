package com.angravity.videodownloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Pair<String, String>>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Đang tải", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_RESULT, "Kết quả tải", NotificationManager.IMPORTANCE_DEFAULT))
        }
        ServiceCompat.startForeground(
            this, NOTIF_PROGRESS, progressNotif("Đang chuẩn bị...", 0, true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        scope.launch {
            for ((id, url) in queue) {
                process(id, url)
                if (pending.decrementAndGet() == 0) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            if (pending.get() == 0) stopSelf()
            return START_NOT_STICKY
        }
        val id = UUID.randomUUID().toString()
        DownloadStore.add(DownloadTask(id = id, url = url))
        pending.incrementAndGet()
        queue.trySend(id to url)
        return START_NOT_STICKY
    }

    private fun process(id: String, url: String) {
        val workDir = File(cacheDir, "dl/$id").apply { mkdirs() }
        try {
            if (!DownloadStore.engineReady.value) error("Engine chưa sẵn sàng")
            DownloadStore.edit(id) { it.copy(status = Status.DOWNLOADING, message = "Đang lấy thông tin...") }
            nm.notify(NOTIF_PROGRESS, progressNotif("Đang lấy thông tin video...", 0, true))

            val request = YoutubeDLRequest(url).apply {
                addOption("-o", "${workDir.absolutePath}/%(title).80B [%(id)s].%(ext)s")
                // Best MP4-compatible quality, merged by bundled ffmpeg
                addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b")
                addOption("--merge-output-format", "mp4")
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--windows-filenames")
            }
            var lastNotify = 0L
            YoutubeDL.getInstance().execute(request, id) { progress, eta, _ ->
                val p = progress.coerceIn(0f, 100f)
                DownloadStore.edit(id) { it.copy(progress = p / 100f, message = "Đang tải ${p.toInt()}% • còn ${eta}s") }
                val now = System.currentTimeMillis()
                if (now - lastNotify > 700) {
                    lastNotify = now
                    nm.notify(NOTIF_PROGRESS, progressNotif("Đang tải ${p.toInt()}%", p.toInt(), false))
                }
            }

            val file = workDir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: error("Không tìm thấy file đã tải")

            DownloadStore.edit(id) { it.copy(title = file.nameWithoutExtension, message = "Đang lưu vào Download...") }
            saveToDownloads(this, file)
            DownloadStore.edit(id) { it.copy(status = Status.DONE, progress = 1f, message = "Đã lưu: Download/$FOLDER") }
            resultNotif("Tải xong ✅", file.name)
        } catch (e: Exception) {
            val msg = e.message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.take(200) ?: "Lỗi không xác định"
            DownloadStore.edit(id) { it.copy(status = Status.FAILED, message = msg) }
            resultNotif("Tải thất bại ❌", msg)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun progressNotif(text: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Video Downloader")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(openAppIntent())
            .build()

    private fun resultNotif(title: String, text: String) {
        nm.notify(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            NotificationCompat.Builder(this, CH_RESULT)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build(),
        )
    }

    private fun openAppIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val FOLDER = "VideoDownloader"
        private const val CH_PROGRESS = "progress"
        private const val CH_RESULT = "result"
        private const val NOTIF_PROGRESS = 1

        fun start(context: Context, url: String) {
            ContextCompat.startForegroundService(
                context, Intent(context, DownloadService::class.java).putExtra(EXTRA_URL, url),
            )
        }

        /** Copy the finished file into the public Download/VideoDownloader folder. */
        fun saveToDownloads(context: Context, file: File) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "video/mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Không tạo được file trong Download")
                resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
                dir.mkdirs()
                val dest = File(dir, file.name)
                file.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
            }
        }
    }
}
