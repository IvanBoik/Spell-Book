package com.example.spellbook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterSpellCrossRef
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboStepLink
import com.example.spellbook.data.model.InventoryItem
import com.example.spellbook.data.model.Spell

/** Единая база данных приложения: библиотека заклинаний, персонажи и их связи. */
@Database(
    entities = [
        Spell::class,
        Character::class,
        CharacterSpellCrossRef::class,
        Combo::class,
        ComboStep::class,
        ComboStepLink::class,
        InventoryItem::class,
    ],
    version = 9,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SpellBookDatabase : RoomDatabase() {

    abstract fun spellDao(): SpellDao
    abstract fun characterDao(): CharacterDao
    abstract fun comboDao(): ComboDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        private const val DB_NAME = "spellbook.db"

        /**
         * v1 → v2: поля подготовки заклинаний и ячеек у персонажа + флаг prepared у связи.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN canPrepareSpells INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE characters ADD COLUMN maxPreparedSpells INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE characters ADD COLUMN spellSlots TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE characters ADD COLUMN spellSlotsUsed TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE character_spells ADD COLUMN prepared INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3: лимит заговоров у персонажа. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN maxCantrips INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4: произвольные восполняемые ресурсы персонажа. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN resources TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** v4 → v5: комбинации, библиотека шагов персонажа и упорядоченные связи. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `combo_steps` (`id` TEXT NOT NULL, `characterId` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `diceCount` INTEGER NOT NULL, `diceSides` INTEGER NOT NULL, `modifier` INTEGER NOT NULL, `flatValue` INTEGER NOT NULL, `effect` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_combo_steps_characterId` ON `combo_steps` (`characterId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `combos` (`id` TEXT NOT NULL, `characterId` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_combos_characterId` ON `combos` (`characterId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `combo_step_links` (`comboId` TEXT NOT NULL, `stepId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`comboId`, `stepId`), FOREIGN KEY(`comboId`) REFERENCES `combos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`stepId`) REFERENCES `combo_steps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_combo_step_links_comboId` ON `combo_step_links` (`comboId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_combo_step_links_stepId` ON `combo_step_links` (`stepId`)")
            }
        }

        /** v5 → v6: тип урона у шага комбинации. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE combo_steps ADD COLUMN damageType TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v6 → v7: инвентарь, кошелёк и лимит настройки магических предметов. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN coins TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE characters ADD COLUMN maxAttunedItems INTEGER NOT NULL DEFAULT 3")
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory_items` (`id` TEXT NOT NULL, `characterId` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `description` TEXT NOT NULL, `categories` TEXT NOT NULL, `isMagic` INTEGER NOT NULL, `rarity` TEXT NOT NULL, `requiresAttunement` INTEGER NOT NULL, `attuned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_characterId` ON `inventory_items` (`characterId`)")
            }
        }

        /** v7 → v8: сохраняемый пользовательский порядок предметов. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                // Сохраняем прежний порядок «новые сверху».
                db.execSQL("UPDATE inventory_items SET sortOrder = -createdAt")
            }
        }

        /** v8 → v9: сохраняемый пользовательский порядок комбинаций. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE combos ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                // Сохраняем прежний порядок «новые сверху».
                db.execSQL("UPDATE combos SET sortOrder = -createdAt")
            }
        }

        @Volatile
        private var instance: SpellBookDatabase? = null

        fun get(context: Context): SpellBookDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpellBookDatabase::class.java,
                    DB_NAME,
).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                ).build()
            }
    }
}
