package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Магический предмет из общей библиотеки.
 *
 * Как раса, класс и предыстория, это справочник для чтения: к персонажу предмет
 * здесь не привязывается, поэтому таблицы-связи нет.
 */
@Entity(tableName = "magic_items")
data class MagicItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /**
     * Описание в той же разметке, что и у остальных статей: разделы `# `,
     * сворачиваемые блоки `## `, подзаголовки `### `, списки, таблицы,
     * врезки `:::` и токены `[[ref …]]`.
     */
    val description: String = "",
    /** Ссылка на dnd.su, если предмет загружен оттуда. */
    val source: String = "",
    /**
     * Книга, в которой появился предмет, например `Dungeon Master's Guide`.
     * Пустая строка — если на странице источник не указан.
     */
    val book: String = "",
    /**
     * Строка вида «Чудесный предмет, редкий (требуется настройка)» целиком,
     * как она напечатана на сайте.
     *
     * Хранится как есть, в дополнение к разобранным [kind], [rarity] и
     * [requiresAttunement]: у части предметов там встречаются уточнения,
     * которые в эти поля не укладываются («требуется настройка волшебником»).
     */
    val category: String = "",
    /** Тип предмета: «Оружие», «Зелье», «Чудесный предмет»…; по нему строится группировка. */
    val kind: String = "",
    /** Редкость: «обычный», «редкий», «легендарный», «артефакт»… */
    val rarity: String = "",
    /** Требуется ли настройка. */
    val requiresAttunement: Boolean = false,
    /** Рекомендованная стоимость, например `101-500 зм`; пустая строка — не указана. */
    val price: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
