package com.aipdfreader.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aipdfreader.app.data.local.dao.ChatMessageDao
import com.aipdfreader.app.data.local.dao.HighlightDao
import com.aipdfreader.app.data.local.dao.PdfDocumentDao
import com.aipdfreader.app.data.local.entity.ChatMessageEntity
import com.aipdfreader.app.data.local.entity.HighlightEntity
import com.aipdfreader.app.data.local.entity.PdfDocumentEntity

@Database(
    entities = [
        PdfDocumentEntity::class,
        HighlightEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun highlightDao(): HighlightDao
    abstract fun chatMessageDao(): ChatMessageDao
}
