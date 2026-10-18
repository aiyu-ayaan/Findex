package com.aiyu.findex.core.di

import android.content.Context
import androidx.room.Room
import com.aiyu.findex.core.db.BookmarkDao
import com.aiyu.findex.core.db.FindexDatabase
import com.aiyu.findex.core.db.OpenTabDao
import com.aiyu.findex.core.db.RecentFileDao
import com.aiyu.findex.core.db.TrashDao
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.repository.StorageRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        storageRepositoryImpl: StorageRepositoryImpl
    ): StorageRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): FindexDatabase =
            Room.databaseBuilder(context, FindexDatabase::class.java, "findex.db").build()

        @Provides
        fun provideBookmarkDao(db: FindexDatabase): BookmarkDao = db.bookmarkDao()

        @Provides
        fun provideRecentFileDao(db: FindexDatabase): RecentFileDao = db.recentFileDao()

        @Provides
        fun provideTrashDao(db: FindexDatabase): TrashDao = db.trashDao()

        @Provides
        fun provideOpenTabDao(db: FindexDatabase): OpenTabDao = db.openTabDao()
    }
}
