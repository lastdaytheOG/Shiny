package com.shiny.music.localmedia

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import com.music.innertube.SharedHttp
import com.shiny.music.artwork.ArtworkMatcher
import com.shiny.music.artwork.ArtworkQuery
import com.shiny.music.artwork.Artworks
import com.shiny.music.constants.LocalCoversOnlineKey
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException

class LocalArtworkFetcher(
    private val context: Context,
    private val volume: String,
    private val mediaId: Long?,
    private val albumId: Long?,
    private val original: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val target = options.size.width.pxOrElse { DefaultPx }.coerceIn(96, 1200)
        val id = mediaId ?: albumId?.let(::firstTrackOf)
        val bitmap = id?.let { fromFile(it, target) }
            ?: fromLegacyAlbumArt(target)
            ?: id?.let { fromCatalogue(it, target) }
            ?: throw FileNotFoundException("No artwork for $original")
        ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun audioUri(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(volume), id)

    private fun firstTrackOf(album: Long): Long? = runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.getContentUri(volume),
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.ALBUM_ID} = ?",
            arrayOf(album.toString()),
            null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()

    private fun fromFile(id: Long, target: Int): Bitmap? {
        val uri = audioUri(id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(target, target), null) }
                .getOrNull()?.let { return it }
        }
        return runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, uri)
                    embeddedPicture?.let { decodeSampled(it, target) }
                } finally {
                    release()
                }
            }
        }.getOrNull()
    }

    private fun fromLegacyAlbumArt(target: Int): Bitmap? = runCatching {
        context.contentResolver.openInputStream(original)?.use { it.readBytes() }?.let { decodeSampled(it, target) }
    }.getOrNull()

    private suspend fun fromCatalogue(id: Long, target: Int): Bitmap? {
        if (!context.dataStore.get(LocalCoversOnlineKey, true)) return null
        val cached = File(context.cacheDir, "local_covers/$volume-$id.jpg")
        if (cached.length() > 0) return decodeSampled(cached.readBytes(), target)
        val query = queryFor(id) ?: return null
        val address = runCatching { Artworks.resolver(context).appleCover(query) }.getOrNull() ?: return null
        val url = ArtworkMatcher.sized(address, CataloguePx) ?: return null
        val bytes = runCatching {
            SharedHttp.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body.bytes() else null
            }
        }.getOrNull() ?: return null
        val bitmap = decodeSampled(bytes, target) ?: return null
        runCatching {
            cached.parentFile?.mkdirs()
            cached.writeBytes(bytes)
        }
        return bitmap
    }

    private fun queryFor(id: Long): ArtworkQuery? = runCatching {
        context.contentResolver.query(
            audioUri(id),
            arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
            ),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val title = c.getString(0)?.trim().orEmpty()
            val artist = c.getString(1)?.trim()?.takeUnless { it.isBlank() || it.equals("<unknown>", true) }
            if (title.isBlank() || artist == null) return@use null
            ArtworkQuery(
                title = title,
                artists = artist.split(ArtistSeparators).map(String::trim).filter(String::isNotBlank),
                album = c.getString(2)?.trim()?.takeUnless { it.isBlank() || it.equals("<unknown>", true) },
                durationSeconds = (c.getLong(3) / 1000L).toInt(),
            )
        }
    }.getOrNull()

    private fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    class Factory(private val context: Context) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val text = data.toString()
            val target = artTarget(text) ?: return null
            return LocalArtworkFetcher(context, target.volume, target.mediaId, target.albumId, Uri.parse(text), options)
        }
    }

    internal data class ArtTarget(val volume: String, val mediaId: Long?, val albumId: Long?)

    companion object {
        internal fun artTarget(text: String): ArtTarget? {
            PerTrack.matchEntire(text)?.let { m ->
                return m.groupValues[2].toLongOrNull()?.let { ArtTarget(m.groupValues[1], it, null) }
            }
            PerAlbum.matchEntire(text)?.let { m ->
                return m.groupValues[2].toLongOrNull()?.let { ArtTarget(m.groupValues[1], null, it) }
            }
            return null
        }

        private val PerTrack = Regex("^content://media/([^/]+)/audio/media/(\\d+)/albumart$")
        private val PerAlbum = Regex("^content://media/([^/]+)/audio/albumart/(-?\\d+)$")
        private val ArtistSeparators = Regex("[,;/&]|\\bfeat\\.?\\b|\\bft\\.?\\b", RegexOption.IGNORE_CASE)
        private const val DefaultPx = 512
        private const val CataloguePx = 600

        fun uriFor(trackUri: Uri): String = "$trackUri/albumart"
    }
}
