package com.aipdfreader.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aipdfreader.app.data.local.dao.ChatMessageDao
import com.aipdfreader.app.data.local.dao.HighlightDao
import com.aipdfreader.app.data.local.dao.PdfDocumentDao
import com.aipdfreader.app.data.local.dao.LocalMaterialDao
import com.aipdfreader.app.data.local.dao.LocalCardDao
import com.aipdfreader.app.data.local.entity.ChatMessageEntity
import com.aipdfreader.app.data.local.entity.HighlightEntity
import com.aipdfreader.app.data.local.entity.PdfDocumentEntity
import com.aipdfreader.app.data.local.entity.LocalMaterialEntity
import com.aipdfreader.app.data.local.entity.LocalCardEntity
import com.aipdfreader.app.data.local.entity.LocalCardMaterialEntity
import com.aipdfreader.app.data.local.entity.LocalCardNoteEntity

@Database(
    entities = [
        PdfDocumentEntity::class,
        HighlightEntity::class,
        ChatMessageEntity::class,
        LocalMaterialEntity::class,
        LocalCardEntity::class,
        LocalCardMaterialEntity::class,
        LocalCardNoteEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun highlightDao(): HighlightDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun localMaterialDao(): LocalMaterialDao
    abstract fun localCardDao(): LocalCardDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `local_materials` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `displayName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `importedAtMillis` INTEGER NOT NULL)"""
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS `local_cards` (`id` TEXT NOT NULL, `ownerUid` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `local_card_materials` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `addedAtMillis` INTEGER NOT NULL)""")
                database.execSQL("""CREATE INDEX IF NOT EXISTS `index_local_card_materials_cardId` ON `local_card_materials` (`cardId`)""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `local_card_notes` (`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                database.execSQL("""CREATE INDEX IF NOT EXISTS `index_local_card_notes_cardId` ON `local_card_notes` (`cardId`)""")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `local_cards` ADD COLUMN `remoteCardId` TEXT")
                database.execSQL("ALTER TABLE `local_card_materials` ADD COLUMN `remoteResourceId` TEXT")
                database.execSQL("ALTER TABLE `local_card_notes` ADD COLUMN `remoteNoteId` TEXT")
                database.execSQL("ALTER TABLE `local_card_notes` ADD COLUMN `lastSyncedAtMillis` INTEGER")
                database.execSQL("ALTER TABLE `local_card_notes` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
