package com.shiny.music.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.AesCipherDataSource
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Plays a download from the listener's download folder.
 *
 * Playback's resolver rewrites a downloaded song to `shiny-offline:<id>`; this source opens
 * that song's folder file through the content resolver and decrypts it as it reads, seeking
 * included. Everything else passes straight through to [fallback], so streaming, the song
 * cache and downloads still in the private cache behave exactly as before. Nothing read
 * here is written to any cache: the folder copy is the only copy.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineFileDataSource(
    context: Context,
    private val store: DownloadFolderStore,
    private val fallback: DataSource,
) : DataSource {
    private val offline = AesCipherDataSource(DownloadFolderStore.KEY, ContentDataSource(context))
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        fallback.addTransferListener(transferListener)
        offline.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.uri.scheme != DownloadFolderStore.SCHEME) {
            current = fallback
            return fallback.open(dataSpec)
        }
        val id = dataSpec.key ?: dataSpec.uri.schemeSpecificPart
        val document = store.documentFor(id)
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        current = offline
        return try {
            offline.open(dataSpec.buildUpon().setUri(document).setKey(id).build())
        } catch (e: Exception) {
            store.forgetVerified(id)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current).read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    class Factory(
        private val context: Context,
        private val store: DownloadFolderStore,
        private val fallback: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            OfflineFileDataSource(context, store, fallback.createDataSource())
    }
}
