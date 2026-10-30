package com.angravity.videodownloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            DownloadStore.engineReady.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init yt-dlp", e)
        }
        autoUpdateEngine()
    }

    /** Platforms change often; keep yt-dlp fresh (at most once per day). */
    private fun autoUpdateEngine() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val last = prefs.getLong("last_update", 0L)
        if (System.currentTimeMillis() - last < 24 * 3600_000L) return
        appScope.launch {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this@App)
                prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Auto update failed", e)
            }
        }
    }

    companion object {
        const val TAG = "VideoDownloader"
    }
}
