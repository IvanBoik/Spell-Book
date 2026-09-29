package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Предыстория персонажа из общей библиотеки.
 *
 * Как раса и класс, это справочник для чтения: к персонажу предыстория здесь
 * не привязывается, поэтому таблицы-связи нет.
 */
@Entity(tableName = "backgrounds")
data class Background(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /**
     * Полное описание в той же разметке, что и у классов: части `# `,
     * сворачиваемые разделы `## `, подзаголовки `### `, списки, таблицы,
     * врезки `:::` и токены `[[ref …]]`.
     */
    val description: String = "",
    /** Ссылка на dnd.su, если предыстория загружена оттуда. */
    val source: String = "",
    /**
     * Книга, в которой появилась предыстория, например `Player's Handbook`.
     * Пустая строка — если на странице источник не указан.
     */
    val book: String = "",
    /**
     * Блок каталога dnd.su, в котором стоит предыстория.
     *
     * По нему записи группируются в библиотеке. [book] для этого не годится:
     * плашка на странице иногда расходится с блоком каталога — например,
     * «Преследуемый» стоит в Curse of Strahd, а плашка указывает Van Richten's Guide.
     * Пустая строка — у записей, добавленных вручную.
     */
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
