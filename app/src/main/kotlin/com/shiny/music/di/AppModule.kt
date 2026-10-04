

package com.shiny.music.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import com.shiny.music.constants.MaxSongCacheSizeKey
import com.shiny.music.db.InternalDatabase
import com.shiny.music.db.MusicDatabase
import com.shiny.music.home.HomeRemoteRepository
import com.shiny.music.utils.PreferencesSnapshot
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Singleton
    @Provides
    fun provideDao(
        database: InternalDatabase,
    ) = database.dao


    @Singleton
    @Provides
    fun provideDatabase(
        internalDatabase: InternalDatabase,
    ): MusicDatabase = MusicDatabase(internalDatabase)

    /**
     * The one cache of Home's network content.
     *
     * Home and New both read it. It owns a file and a mutex, so a second instance would be
     * two writers of `home_remote.json` and two sets of TTLs — the same explore call twice,
     * and a last-writer-wins race on the block itself.
     */
    @Singleton
    @Provides
    fun provideHomeRemoteRepository(
        @ApplicationContext context: Context,
        database: MusicDatabase,
    ): HomeRemoteRepository = HomeRemoteRepository(context, database)

    @Singleton
    @Provides
    fun provideInternalDatabase(
        @ApplicationContext context: Context,
    ): InternalDatabase = Room
        .databaseBuilder(context, InternalDatabase::class.java, InternalDatabase.DB_NAME)
        .addMigrations(
            com.shiny.music.db.MIGRATION_1_2,
            com.shiny.music.db.MIGRATION_21_24,
            com.shiny.music.db.MIGRATION_22_24,
            com.shiny.music.db.MIGRATION_24_25,
            com.shiny.music.db.MIGRATION_27_28,
            com.shiny.music.db.MIGRATION_28_29,
            com.shiny.music.db.MIGRATION_29_30,
            com.shiny.music.db.MIGRATION_31_32,
            com.shiny.music.db.MIGRATION_36_37,
            com.shiny.music.db.MIGRATION_37_38,
            com.shiny.music.db.MIGRATION_38_39,
            com.shiny.music.db.MIGRATION_39_40,
            com.shiny.music.db.MIGRATION_40_41,
            com.shiny.music.db.MIGRATION_41_42,
            com.shiny.music.db.MIGRATION_42_43,
            com.shiny.music.db.MIGRATION_43_44,
            com.shiny.music.db.MIGRATION_44_45,
        )
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setTransactionExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
        .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.query("PRAGMA busy_timeout = 60000").close()
                    db.query("PRAGMA cache_size = -16000").close()
                    db.query("PRAGMA wal_autocheckpoint = 1000").close()
                    db.query("PRAGMA synchronous = NORMAL").close()
                } catch (e: Exception) {
                    timber.log.Timber.tag("MusicDatabase").e(e, "Failed to set PRAGMA settings")
                }
            }
        })
        .build()

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        // The snapshot first: this provider can run during MainActivity's injection on the
        // main thread, where the DataStore read below blocks until the file has been parsed.
        val cacheSize = PreferencesSnapshot.read(MaxSongCacheSizeKey, 1024)
            .takeIf { PreferencesSnapshot.isLoaded }
            ?: context.dataStore[MaxSongCacheSizeKey] ?: 1024
        return SimpleCache(
            context.filesDir.resolve("exoplayer"),
            when (cacheSize) {
                -1 -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
            },
            databaseProvider,
        )
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        return SimpleCache(
            context.filesDir.resolve("download"),
            NoOpCacheEvictor(),
            databaseProvider
        )
    }
}
