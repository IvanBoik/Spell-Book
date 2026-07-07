package com.example.spellbook.data

import android.content.Context
import com.example.spellbook.data.model.Spell
import java.io.File

/**
 * Локальное хранилище заклинаний в едином файле во внутренней памяти приложения.
 * Список сериализуется в формате LSS (массив объектов) с добавлением служебного id.
 */
class SpellRepository(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    fun load(): List<Spell> {
        if (!file.exists()) return emptyList()
        return runCatching { SpellLssCodec.decodeList(file.readText()) }.getOrDefault(emptyList())
    }

    fun save(spells: List<Spell>) {
        file.writeText(SpellLssCodec.encodeList(spells))
    }

    private companion object {
        const val FILE_NAME = "spells.json"
    }
}
