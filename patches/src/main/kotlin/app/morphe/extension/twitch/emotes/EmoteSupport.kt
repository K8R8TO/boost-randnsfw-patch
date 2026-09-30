package app.morphe.extension.twitch.emotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.style.ImageSpan
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object EmoteSupport {
    private val emotes = ConcurrentHashMap<String, Emote>()
    private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext }

    fun onChannelChanged(channelId: String, channelLogin: String) {
        if (appContext == null) return
        emotes.clear()
        thread {
            fetchBttv(channelId)
            fetchFfz(channelId)
        }
    }

    fun bind(textView: TextView) {
        val context = appContext ?: return
        val text = textView.text.toString()
        if (text.isEmpty()) return
        val spannable = Spannable.Factory.getInstance().newSpannable(text)
        var modified = false
        val words = text.split(" ")
        var currentIndex = 0
        for (word in words) {
            val emote = emotes[word]
            if (emote != null) {
                try {
                    val drawable = createEmoteDrawable(emote)
                    if (drawable != null) {
                        val size = textView.textSize.toInt()
                        drawable.setBounds(0, 0, size, size)
                        val span = ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
                        spannable.setSpan(span, currentIndex, currentIndex + word.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        modified = true
                    }
                } catch (e: Exception) {}
            }
            currentIndex += word.length + 1
        }
        if (modified) { textView.text = spannable }
    }

    private fun createEmoteDrawable(emote: Emote): Drawable? {
        val context = appContext ?: return null
        return try {
            val url = URL(emote.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val inputStream = connection.inputStream
            if (emote.isAnimated) {
                val movie = Movie.decodeStream(inputStream)
                if (movie != null) MovieDrawable(movie) else null
            } else {
                val bitmap = BitmapFactory.decodeStream(inputStream)
                BitmapDrawable(context.resources, bitmap)
            }
        } catch (e: Exception) { null }
    }

    private fun fetchBttv(channelId: String) {
        try {
            val url = URL("https://api.betterttv.net/3/cached/users/twitch/")
            val json = JSONObject(getJson(url))
            val channelEmotes = json.getJSONArray("channelEmotes")
            for (i in 0 until channelEmotes.length()) {
                val e = channelEmotes.getJSONObject(i)
                emotes[e.getString("code")] = Emote(e.getString("id"), e.getString("code"), "https://cdn.betterttv.net/emote/${e.getString("id")}/2x", false)
            }
            val sharedEmotes = json.getJSONArray("sharedEmotes")
            for (i in 0 until sharedEmotes.length()) {
                val e = sharedEmotes.getJSONObject(i)
                emotes[e.getString("code")] = Emote(e.getString("id"), e.getString("code"), "https://cdn.betterttv.net/emote/${e.getString("id")}/2x", false)
            }
        } catch (e: Exception) {}
    }

    private fun fetchFfz(channelId: String) {
        try {
            val url = URL("https://api.frankerfacez.com/v1/room/")
            val json = JSONObject(getJson(url))
            val sets = json.getJSONObject("sets")
            val roomId = json.getJSONObject("room").getString("set")
            val emotesArray = sets.getJSONObject(roomId).getJSONArray("emoticons")
            for (i in 0 until emotesArray.length()) {
                val e = emotesArray.getJSONObject(i)
                val urls = e.getJSONObject("urls")
                var imgUrl = ""
                if (urls.has("4")) imgUrl = urls.getString("4")
                else if (urls.has("2")) imgUrl = urls.getString("2")
                else if (urls.has("1")) imgUrl = urls.getString("1")
                if (imgUrl.isNotEmpty()) emotes[e.getString("name")] = Emote(e.getString("id"), e.getString("name"), imgUrl, false)
            }
        } catch (e: Exception) {}
    }

    private fun getJson(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    data class Emote(val id: String, val name: String, val url: String, val isAnimated: Boolean)

    class MovieDrawable(private val movie: Movie) : Drawable() {
        private var startTime = 0L
        override fun draw(canvas: Canvas) {
            if (startTime == 0L) startTime = System.currentTimeMillis()
            movie.setTime(((System.currentTimeMillis() - startTime) % movie.duration()).toInt())
            movie.draw(canvas, 0f, 0f)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}