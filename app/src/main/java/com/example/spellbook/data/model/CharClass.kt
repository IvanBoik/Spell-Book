package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Класс персонажа из общей библиотеки.
 *
 * Как и раса, это справочник для чтения: к персонажу класс здесь не привязывается,
 * поэтому таблицы-связи нет.
 *
 * Назван `CharClass`, а не `Class`: последнее — ключевое слово Java и конфликтует
 * с `java.lang.Class` в сгенерированном Room коде.
 */
@Entity(tableName = "char_classes")
data class CharClass(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /**
     * Полное описание в той же разметке, что и у рас: заголовки `## `,
     * подзаголовки `### `, списки, таблицы, врезки `:::` и токены `[[ref …]]`.
     */
    val description: String = "",
    /** Ссылка на dnd.su, если класс загружен оттуда. */
    val source: String = "",
    /**
     * Книга, в которой появился класс, например `Player's Handbook`.
     * Пустая строка — если на странице источник не указан.
     */
    val book: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
