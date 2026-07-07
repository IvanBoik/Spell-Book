package com.example.spellbook.data

/**
 * Справочники кодов формата LSS и их русских названий для выпадающих списков формы.
 * Каждый список — пары (код LSS, отображаемое название).
 */
object SpellOptions {

    /** Школы магии. */
    val schools: List<Pair<String, String>> = listOf(
        "abj" to "Ограждение",
        "con" to "Вызов",
        "div" to "Прорицание",
        "enc" to "Очарование",
        "evo" to "Воплощение",
        "ill" to "Иллюзия",
        "nec" to "Некромантия",
        "trs" to "Преобразование",
    )

    /** Тип активации (как накладывается заклинание). */
    val activationTypes: List<Pair<String, String>> = listOf(
        "action" to "Действие",
        "bonus" to "Бонусное действие",
        "reaction" to "Реакция",
        "minute" to "Минуты",
        "hour" to "Часы",
        "day" to "Дни",
        "special" to "Особая",
    )

    /** Единицы длительности. */
    val durationUnits: List<Pair<String, String>> = listOf(
        "inst" to "Мгновенная",
        "turn" to "Ход",
        "round" to "Раунд",
        "minute" to "Минуты",
        "hour" to "Часы",
        "day" to "Дни",
        "perm" to "Постоянная",
        "spec" to "Особая",
    )

    /** Единицы дистанции. */
    val rangeUnits: List<Pair<String, String>> = listOf(
        "self" to "На себя",
        "touch" to "Касание",
        "ft" to "Футы",
        "mi" to "Мили",
        "spec" to "Особая",
        "any" to "Любая",
    )

    /** Тип цели или области воздействия. Пустой код — «не задано». */
    val targetTypes: List<Pair<String, String>> = listOf(
        "" to "Не задано",
        "self" to "На себя",
        "creature" to "Существо",
        "ally" to "Союзник",
        "enemy" to "Противник",
        "object" to "Объект",
        "space" to "Пространство",
        "radius" to "Радиус",
        "sphere" to "Сфера",
        "cylinder" to "Цилиндр",
        "cone" to "Конус",
        "cube" to "Куб",
        "line" to "Линия",
        "wall" to "Стена",
    )

    /** Единицы размера цели/области. Пустой код — «не задано». */
    val targetUnits: List<Pair<String, String>> = listOf(
        "" to "Не задано",
        "ft" to "Футы",
        "mi" to "Мили",
        "spec" to "Специальная",
    )

    /** Тип действия (механика применения). */
    val actionTypes: List<Pair<String, String>> = listOf(
        "util" to "Вспомогательное",
        "msak" to "Атака ближнего заклинания",
        "rsak" to "Атака дальнего заклинания",
        "save" to "Спасбросок",
        "heal" to "Лечение",
        "other" to "Другое",
    )

    /** Характеристики (для спасброска / базовой хар-ки). Пустой код — «не задано». */
    val abilities: List<Pair<String, String>> = listOf(
        "" to "Не задано",
        "str" to "Сила",
        "dex" to "Ловкость",
        "con" to "Телосложение",
        "int" to "Интеллект",
        "wis" to "Мудрость",
        "cha" to "Харизма",
    )

    /** Типы урона и лечения. */
    val damageTypes: List<Pair<String, String>> = listOf(
        "acid" to "Кислота",
        "bludgeoning" to "Дробящий",
        "cold" to "Холод",
        "fire" to "Огонь",
        "force" to "Силовое поле",
        "lightning" to "Электричество",
        "necrotic" to "Некротический",
        "piercing" to "Колющий",
        "poison" to "Яд",
        "psychic" to "Психический",
        "radiant" to "Излучение",
        "slashing" to "Рубящий",
        "thunder" to "Звук",
        "healing" to "Лечение",
    )

    /** Классы, которым может быть доступно заклинание. */
    val classes: List<Pair<String, String>> = listOf(
        "artificer" to "Изобретатель",
        "bard" to "Бард",
        "cleric" to "Жрец",
        "druid" to "Друид",
        "paladin" to "Паладин",
        "ranger" to "Следопыт",
        "sorcerer" to "Чародей",
        "warlock" to "Колдун",
        "wizard" to "Волшебник",
    )

    /** Круги заклинаний: 0 — заговор. */
    val levels: List<Pair<Int, String>> = listOf(
        0 to "Заговор",
        1 to "1 круг",
        2 to "2 круг",
        3 to "3 круг",
        4 to "4 круг",
        5 to "5 круг",
        6 to "6 круг",
        7 to "7 круг",
        8 to "8 круг",
        9 to "9 круг",
    )

    fun labelFor(options: List<Pair<String, String>>, code: String): String =
        options.firstOrNull { it.first == code }?.second ?: code
}
