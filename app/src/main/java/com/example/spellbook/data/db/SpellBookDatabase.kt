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
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SpellBookDatabase : RoomDatabase() {

    abstract fun spellDao(): SpellDao
    abstract fun characterDao(): CharacterDao
    abstract fun comboDao(): ComboDao

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
                ).build().also { instance = it }
            }
    }
}
