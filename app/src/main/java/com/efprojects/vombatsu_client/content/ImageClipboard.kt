package com.efprojects.vombatsu_client.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.efprojects.vombatsu_client.ErrorLog
import com.efprojects.vombatsu_client.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val CLIP_DIR = "clipboard"
private const val JPEG_QUALITY = 92

suspend fun copyImage(context: Context, path: String) {
    val request = ImageRequest.Builder(context).data(rawImageUrl(path)).allowHardware(false).build()
    val image = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    val uri = image?.let { withContext(Dispatchers.IO) { saveJpeg(context, it) } }
    if (uri == null) {
        Toast.makeText(context, R.string.copy_image_failed, Toast.LENGTH_SHORT).show()
        return
    }
    val clip = ClipData.newUri(context.contentResolver, context.getString(R.string.copy_image_label), uri)
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    // Android 13+ shows its own confirmation for clipboard writes
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, R.string.copy_image_done, Toast.LENGTH_SHORT).show()
    }
}

private fun saveJpeg(context: Context, image: Bitmap): Uri? = try {
    val dir = File(context.cacheDir, CLIP_DIR)
    dir.deleteRecursively()
    dir.mkdirs()
    val file = File(dir, "${System.currentTimeMillis()}.jpg")
    file.outputStream().use { opaque(image).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
} catch (e: IOException) {
    ErrorLog.write("Image copy failed to save", e)
    null
}

// JPEG has no alpha channel: transparent pixels would come out black
private fun opaque(image: Bitmap): Bitmap {
    if (!image.hasAlpha()) return image
    val result = createBitmap(image.width, image.height)
    Canvas(result).apply {
        drawColor(Color.WHITE)
        drawBitmap(image, 0f, 0f, null)
    }
    return result
}
