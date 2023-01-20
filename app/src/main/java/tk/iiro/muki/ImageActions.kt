package tk.iiro.muki

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.toColor

class ImageActions {

    fun convertToMuki(image: Bitmap) {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val value = image.getPixel(x, y)
                val color = value.toColor()
                val newValue = (color.red() + color.green() + color.blue()) / 3
                if (newValue >= 0.5) {
                    image.setPixel(x, y, Color.BLACK)
                } else {
                    image.setPixel(x, y, Color.WHITE)
                }
            }
        }
    }
}