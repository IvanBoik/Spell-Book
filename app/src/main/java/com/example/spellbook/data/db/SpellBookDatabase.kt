package com.example.spellbook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterSpellCrossRef
import com.example.spellbook.data.model.Spell

/** Единая база данных приложения: библиотека заклинаний, персонажи и их связи. */
@Database(
    entities = [Spell::class, Character::class, CharacterSpellCrossRef::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SpellBookDatabase : RoomDatabase() {

    abstract fun spellDao(): SpellDao
    abstract fun characterDao(): CharacterDao

    companion object {
        private const val DB_NAME = "spellbook.db"

        @Volatile
        private var instance: SpellBookDatabase? = null

        fun get(context: Context): SpellBookDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpellBookDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}
