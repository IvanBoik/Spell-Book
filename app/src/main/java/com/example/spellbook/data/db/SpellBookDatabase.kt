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
import com.example.spellbook.data.model.Spell

/** Единая база данных приложения: библиотека заклинаний, персонажи и их связи. */
@Database(
    entities = [Spell::class, Character::class, CharacterSpellCrossRef::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SpellBookDatabase : RoomDatabase() {

    abstract fun spellDao(): SpellDao
    abstract fun characterDao(): CharacterDao

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

        @Volatile
        private var instance: SpellBookDatabase? = null

        fun get(context: Context): SpellBookDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpellBookDatabase::class.java,
                    DB_NAME,
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
