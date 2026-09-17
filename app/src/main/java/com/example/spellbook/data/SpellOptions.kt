package com.example.spellbook.data

import androidx.annotation.StringRes
import com.example.spellbook.R

/**
 * Справочники кодов формата LSS.
 *
 * У каждого значения две подписи:
 * - [SpellOption.canonical] — русское название из источника dnd.su. Оно не переводится:
 *   по нему парсер сопоставляет загруженные страницы с кодами LSS.
 * - [SpellOption.labelRes] — подпись для интерфейса, зависящая от языка приложения.
 */
object SpellOptions {

    /** Вариант справочника: код LSS, каноническое имя источника и подпись для UI. */
    data class SpellOption(
        val code: String,
        val canonical: String,
        @param:StringRes val labelRes: Int,
    )

    /** Школы магии. */
    val schools: List<SpellOption> = listOf(
        SpellOption("abj", "Ограждение", R.string.school_abjuration),
        SpellOption("con", "Вызов", R.string.school_conjuration),
        SpellOption("div", "Прорицание", R.string.school_divination),
        SpellOption("enc", "Очарование", R.string.school_enchantment),
        SpellOption("evo", "Воплощение", R.string.school_evocation),
        SpellOption("ill", "Иллюзия", R.string.school_illusion),
        SpellOption("nec", "Некромантия", R.string.school_necromancy),
        SpellOption("trs", "Преобразование", R.string.school_transmutation),
    )

    /** Тип активации (как накладывается заклинание). */
    val activationTypes: List<SpellOption> = listOf(
        SpellOption("action", "Действие", R.string.activation_action),
        SpellOption("bonus", "Бонусное действие", R.string.activation_bonus),
        SpellOption("reaction", "Реакция", R.string.activation_reaction),
        SpellOption("minute", "Минуты", R.string.unit_minutes),
        SpellOption("hour", "Часы", R.string.unit_hours),
        SpellOption("day", "Дни", R.string.unit_days),
        SpellOption("special", "Особая", R.string.unit_special),
    )

    /** Единицы длительности. */
    val durationUnits: List<SpellOption> = listOf(
        SpellOption("inst", "Мгновенная", R.string.duration_instant),
        SpellOption("turn", "Ход", R.string.duration_turn),
        SpellOption("round", "Раунд", R.string.duration_round),
        SpellOption("minute", "Минуты", R.string.unit_minutes),
        SpellOption("hour", "Часы", R.string.unit_hours),
        SpellOption("day", "Дни", R.string.unit_days),
        SpellOption("perm", "Постоянная", R.string.duration_permanent),
        SpellOption("spec", "Особая", R.string.unit_special),
    )

    /** Единицы дистанции. */
    val rangeUnits: List<SpellOption> = listOf(
        SpellOption("self", "На себя", R.string.range_self),
        SpellOption("touch", "Касание", R.string.range_touch),
        SpellOption("ft", "Футы", R.string.unit_feet),
        SpellOption("mi", "Мили", R.string.unit_miles),
        SpellOption("spec", "Особая", R.string.unit_special),
        SpellOption("any", "Любая", R.string.range_any),
    )

    /** Тип цели или области воздействия. Пустой код — «не задано». */
    val targetTypes: List<SpellOption> = listOf(
        SpellOption("", "Не задано", R.string.value_unset),
        SpellOption("self", "На себя", R.string.range_self),
        SpellOption("creature", "Существо", R.string.target_creature),
        SpellOption("ally", "Союзник", R.string.target_ally),
        SpellOption("enemy", "Противник", R.string.target_enemy),
        SpellOption("object", "Объект", R.string.target_object),
        SpellOption("space", "Пространство", R.string.target_space),
        SpellOption("radius", "Радиус", R.string.target_radius),
        SpellOption("sphere", "Сфера", R.string.target_sphere),
        SpellOption("cylinder", "Цилиндр", R.string.target_cylinder),
        SpellOption("cone", "Конус", R.string.target_cone),
        SpellOption("cube", "Куб", R.string.target_cube),
        SpellOption("line", "Линия", R.string.target_line),
        SpellOption("wall", "Стена", R.string.target_wall),
    )

    /** Единицы размера цели/области. Пустой код — «не задано». */
    val targetUnits: List<SpellOption> = listOf(
        SpellOption("", "Не задано", R.string.value_unset),
        SpellOption("ft", "Футы", R.string.unit_feet),
        SpellOption("mi", "Мили", R.string.unit_miles),
        SpellOption("spec", "Специальная", R.string.unit_special),
    )

    /** Тип действия (механика применения). */
    val actionTypes: List<SpellOption> = listOf(
        SpellOption("util", "Вспомогательное", R.string.action_type_utility),
        SpellOption("msak", "Атака ближнего заклинания", R.string.action_type_melee),
        SpellOption("rsak", "Атака дальнего заклинания", R.string.action_type_ranged),
        SpellOption("save", "Спасбросок", R.string.action_type_save),
        SpellOption("heal", "Лечение", R.string.damage_healing),
        SpellOption("other", "Другое", R.string.action_type_other),
    )

    /** Характеристики (для спасброска / базовой хар-ки). Пустой код — «не задано». */
    val abilities: List<SpellOption> = listOf(
        SpellOption("", "Не задано", R.string.value_unset),
        SpellOption("str", "Сила", R.string.ability_strength),
        SpellOption("dex", "Ловкость", R.string.ability_dexterity),
        SpellOption("con", "Телосложение", R.string.ability_constitution),
        SpellOption("int", "Интеллект", R.string.ability_intelligence),
        SpellOption("wis", "Мудрость", R.string.ability_wisdom),
        SpellOption("cha", "Харизма", R.string.ability_charisma),
    )

    /** Типы урона и лечения. */
    val damageTypes: List<SpellOption> = listOf(
        SpellOption("bludgeoning", "Дробящий", R.string.damage_bludgeoning),
        SpellOption("piercing", "Колющий", R.string.damage_piercing),
        SpellOption("slashing", "Рубящий", R.string.damage_slashing),
        SpellOption("fire", "Огонь", R.string.damage_fire),
        SpellOption("cold", "Холод", R.string.damage_cold),
        SpellOption("poison", "Яд", R.string.damage_poison),
        SpellOption("acid", "Кислота", R.string.damage_acid),
        SpellOption("lightning", "Электричество", R.string.damage_lightning),
        SpellOption("thunder", "Звук", R.string.damage_thunder),
        SpellOption("necrotic", "Некротический", R.string.damage_necrotic),
        SpellOption("radiant", "Излучение", R.string.damage_radiant),
        SpellOption("psychic", "Психический", R.string.damage_psychic),
        SpellOption("force", "Силовое поле", R.string.damage_force),
        SpellOption("healing", "Лечение", R.string.damage_healing),
    )

    /** Классы, которым может быть доступно заклинание. */
    val classes: List<SpellOption> = listOf(
        SpellOption("bard", "Бард", R.string.class_bard),
        SpellOption("wizard", "Волшебник", R.string.class_wizard),
        SpellOption("druid", "Друид", R.string.class_druid),
        SpellOption("cleric", "Жрец", R.string.class_cleric),
        SpellOption("artificer", "Изобретатель", R.string.class_artificer),
        SpellOption("warlock", "Колдун", R.string.class_warlock),
        SpellOption("paladin", "Паладин", R.string.class_paladin),
        SpellOption("ranger", "Следопыт", R.string.class_ranger),
        SpellOption("sorcerer", "Чародей", R.string.class_sorcerer),
    )

    /** Круги заклинаний: 0 — заговор. */
    val levels: List<Int> = (0..MAX_SPELL_LEVEL).toList()

    /** Находит вариант по коду LSS. */
    fun optionFor(options: List<SpellOption>, code: String): SpellOption? =
        options.firstOrNull { it.code == code }

    /** Каноническое (непереводимое) название — используется при разборе страниц dnd.su. */
    fun canonicalLabel(options: List<SpellOption>, code: String): String =
        optionFor(options, code)?.canonical ?: code
}

/** Максимальный круг заклинаний в игре. */
const val MAX_SPELL_LEVEL = 9
