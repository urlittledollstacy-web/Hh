package app.hikari.di

import android.content.Context
import androidx.room.Room
import app.hikari.data.DefaultMediaRepository
import app.hikari.data.MediaRepository
import app.hikari.data.local.HikariDatabase
import app.hikari.data.local.HikariFavoriteDao
import app.hikari.data.local.MediaCacheDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule { @Binds abstract fun bindMediaRepository(implementation: DefaultMediaRepository): MediaRepository }

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): HikariDatabase = Room.databaseBuilder(context, HikariDatabase::class.java, "hikari.db").fallbackToDestructiveMigration().build()
    @Provides fun mediaCacheDao(database: HikariDatabase): MediaCacheDao = database.mediaCacheDao()
    @Provides fun hikariFavoriteDao(database: HikariDatabase): HikariFavoriteDao = database.hikariFavoriteDao()
    @Provides @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}
