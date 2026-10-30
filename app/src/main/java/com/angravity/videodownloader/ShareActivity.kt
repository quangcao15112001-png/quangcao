package com.angravity.videodownloader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Invisible target for "Share → Tải video về máy": starts download and closes instantly. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = extractUrl(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (url == null) {
            Toast.makeText(this, "Không tìm thấy link video", Toast.LENGTH_SHORT).show()
        } else {
            DownloadService.start(this, url)
            Toast.makeText(this, "⬇️ Đang tải video về mục Download...", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
