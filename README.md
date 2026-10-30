# Video Downloader (Android)

Tải video Facebook / YouTube / TikTok về `Download/VideoDownloader/` trên điện thoại.

## Cách dùng
- **Cách 1:** Trong app FB/YouTube/TikTok → **Chia sẻ** → chọn **"Tải video về máy"** → tự tải.
- **Cách 2:** Mở app → bấm **Dán** → **TẢI VỀ MÁY**.

Engine yt-dlp tự cập nhật mỗi ngày; nếu link bị lỗi hãy bấm **Cập nhật engine**.

## Build APK
**Cách A – Không cần cài gì (GitHub Actions):**
1. Tạo repo GitHub, push thư mục này lên.
2. Vào tab **Actions → Build APK** → đợi ~5 phút → tải file `VideoDownloader-apk`.
3. Copy APK vào điện thoại và cài (cho phép "Cài app không rõ nguồn gốc").

**Cách B – Android Studio:** File → Open thư mục này → Build → Build APK(s).
