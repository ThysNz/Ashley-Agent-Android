package nz.thys.ashley.device

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File
import java.io.FileOutputStream

/** Photo helpers. ImageDecoder applies the camera's EXIF rotation, so pictures come out upright. */
object Photos {

    /** Rewrites [file] as an upright JPEG no bigger than [maxPx] on its long side. */
    fun shrink(file: File, maxPx: Int) {
        val bmp = decode(file, maxPx) ?: return
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    /** Decodes [file] scaled so its long side is at most [maxPx]. Null if it isn't a readable image. */
    fun decode(file: File, maxPx: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = maxPx.toFloat() / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()

    fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
}
