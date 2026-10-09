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
import com.aipdfreader.app.data.local.entity.WorkspaceSnapshotEntity
import com.aipdfreader.app.data.local.entity.CachedResourceFileEntity
import com.aipdfreader.app.data.local.dao.WorkspaceSnapshotDao

@Database(
    entities = [
        PdfDocumentEntity::class,
        HighlightEntity::class,
        ChatMessageEntity::class,
        LocalMaterialEntity::class,
        LocalCardEntity::class,
        LocalCardMaterialEntity::class,
        LocalCardNoteEntity::class,
        WorkspaceSnapshotEntity::class,
        CachedResourceFileEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun highlightDao(): HighlightDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun localMaterialDao(): LocalMaterialDao
    abstract fun localCardDao(): LocalCardDao
    abstract fun workspaceSnapshotDao(): WorkspaceSnapshotDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS `workspace_snapshots` (`ownerUid` TEXT NOT NULL, `cardId` TEXT NOT NULL, `cardOwnerId` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `isShared` INTEGER NOT NULL, `role` TEXT NOT NULL, `memberCardId` TEXT, `shareApproval` INTEGER, `lastSyncedAtMillis` INTEGER NOT NULL, `contentJson` TEXT NOT NULL, PRIMARY KEY(`ownerUid`, `cardId`))""")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_snapshots_ownerUid` ON `workspace_snapshots` (`ownerUid`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_snapshots_isShared` ON `workspace_snapshots` (`isShared`)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `cached_resource_files` (`ownerUid` TEXT NOT NULL, `cardId` TEXT NOT NULL, `resourceId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `downloadedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`ownerUid`, `cardId`, `resourceId`))""")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_resource_files_ownerUid` ON `cached_resource_files` (`ownerUid`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_resource_files_cardId` ON `cached_resource_files` (`cardId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `local_cards` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
