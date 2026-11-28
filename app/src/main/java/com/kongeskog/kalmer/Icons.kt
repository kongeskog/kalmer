package com.kongeskog.kalmer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** The tiles' icon size in real pixels: 108, as every size here is a pixel measurement on the Kompakt's screen. */
const val TILE_ICON_PX = 108

/**
 * App icons for the tiles layout, drawn as each app supplies them, with no frame or recolouring:
 * what Mudita's first launcher shows.
 */
object AppIcons {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    /** Apps whose icon couldn't be loaded; their tiles show the app's first letter. */
    private val failed = ConcurrentHashMap.newKeySet<String>()
    /** Bumped when the installed apps change, so tiles on screen load their icons again. */
    var generation by mutableIntStateOf(0)
        private set

    fun clear() {
        cache.clear()
        failed.clear()
        generation++
    }

    private fun key(pkg: String, size: Int, glyph: Boolean) = if (glyph) "$pkg:$size:glyph" else "$pkg:$size"
    fun cached(pkg: String, size: Int, glyph: Boolean = false): ImageBitmap? = cache[key(pkg, size, glyph)]
    fun failed(pkg: String, size: Int, glyph: Boolean = false): Boolean = key(pkg, size, glyph) in failed

    /**
     * With [glyph], only the middle two thirds of the icon are kept. Mudita's own icons are a line drawing
     * inside a rounded square, and this leaves the drawing without its square, as Kalm shows it on a home button.
     */
    suspend fun load(context: Context, pkg: String, size: Int, glyph: Boolean = false): ImageBitmap? =
        withContext(Dispatchers.Default) { draw(context, pkg, size, glyph) }

    /**
     * Draws the tiles' icons ahead of time, in the background, so the apps page opens with them in place
     * instead of filling in. Called at start and whenever the installed apps change.
     */
    fun preload(context: Context, pkgs: List<String>) {
        val size = TILE_ICON_PX
        val started = generation
        Thread {
            for (pkg in pkgs) {
                if (generation != started) return@Thread // the apps changed again; a newer run has taken over
                draw(context, pkg, size, false)
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun draw(context: Context, pkg: String, size: Int, glyph: Boolean): ImageBitmap? {
        val key = key(pkg, size, glyph)
        return cache[key] ?: try {
            val icon = context.packageManager.getApplicationIcon(pkg)
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                val full = if (glyph) size * 3 / 2 else size
                val edge = (size - full) / 2
                icon.setBounds(edge, edge, edge + full, edge + full)
                icon.draw(Canvas(it))
            }.asImageBitmap()
        } catch (e: Exception) {
            null
        }.also { if (it != null) cache[key] = it else failed += key }
    }
}

/** An app's icon at [sizePx] real pixels, or its first letter if it has none. */
@Composable
fun AppIcon(pkg: String, sizePx: Int, letterPx: Int, glyph: Boolean = false) {
    val context = LocalContext.current
    val generation = AppIcons.generation
    var bitmap by remember(pkg, sizePx, glyph, generation) { mutableStateOf(AppIcons.cached(pkg, sizePx, glyph)) }
    var missing by remember(pkg, sizePx, glyph, generation) { mutableStateOf(AppIcons.failed(pkg, sizePx, glyph)) }
    LaunchedEffect(pkg, sizePx, glyph, generation) {
        if (bitmap == null && !missing) {
            bitmap = AppIcons.load(context, pkg, sizePx, glyph)
            missing = bitmap == null
        }
    }
    val shown = bitmap
    // Pixel for pixel: the bitmap is already the size it is shown at.
    if (shown != null) Image(shown, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
    else if (missing) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(Apps.name(pkg).take(1).uppercase(), style = text(letterPx, FontWeight.Black))
    }
}
