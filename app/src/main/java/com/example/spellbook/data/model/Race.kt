package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Раса из общей библиотеки.
 *
 * В отличие от заклинаний и черт, раса пока не привязывается к персонажу: это
 * справочник для чтения. Поэтому таблицы-связи у неё нет.
 */
@Entity(tableName = "races")
data class Race(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /**
     * Полное описание в той же разметке, что и у заклинаний: заголовки `## `,
     * списки, врезки `:::` и токены `[[ref …]]`.
     */
    val description: String = "",
    /** Ссылка на dnd.su, если раса загружена оттуда. */
    val source: String = "",
    /**
     * Книга, в которой появилась раса, например `Player's Handbook`.
     * Показывается на экране расы; пустая строка — у созданных вручную.
     */
    val book: String = "",
    /**
     * Раздел каталога dnd.su, к которому отнесена раса.
     *
     * По нему расы группируются в библиотеке. Книга для этого не подходит: внутри
     * одного раздела сайта встречаются расы из десятка разных книг.
     * Пустая строка — у рас, добавленных вручную.
     */
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
