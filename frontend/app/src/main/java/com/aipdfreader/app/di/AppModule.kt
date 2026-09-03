package com.aipdfreader.app.di

import android.content.Context
import androidx.room.Room
import com.aipdfreader.app.data.local.AppDatabase
import com.aipdfreader.app.data.local.dao.ChatMessageDao
import com.aipdfreader.app.data.local.dao.HighlightDao
import com.aipdfreader.app.data.local.dao.PdfDocumentDao
import com.aipdfreader.app.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, Constants.DATABASE_NAME)
            .fallbackToDestructiveMigration() // acceptable for a local cache/notes DB
            .build()

    @Provides
    fun providePdfDocumentDao(db: AppDatabase): PdfDocumentDao = db.pdfDocumentDao()

    @Provides
    fun provideHighlightDao(db: AppDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()
}
