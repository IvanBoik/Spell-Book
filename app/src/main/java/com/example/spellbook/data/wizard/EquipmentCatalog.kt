package com.example.spellbook.data.wizard

/*
 * Справочник доспехов и оружия из Player's Handbook и разбор строк стартового снаряжения.
 *
 * В библиотеке классов снаряжение записано обычным текстом («а) два коротких меча
 * или б) два простых рукопашных оружия»), а характеристик предметов там нет вовсе.
 * Поэтому таблицы PHB заданы здесь, а текст сопоставляется с ними по основам слов:
 * так узнаются и падежи («коротких меча»), и число («четыре метательных копья»).
 */

/** Вид доспеха; щит выделен отдельно — он прибавляется к КД, а не задаёт его. */
enum class ArmorType { LIGHT, MEDIUM, HEAVY, SHIELD }

/**
 * Доспех или щит.
 *
 * @param baseAc базовый КД; у щита — прибавка к КД.
 * @param dexCap ограничение модификатора Ловкости: null — без ограничения, 0 — не добавляется.
 * @param strength требование к Силе; null — требования нет.
 */
data class ArmorInfo(
    val id: String,
    val name: String,
    val type: ArmorType,
    val baseAc: Int,
    val dexCap: Int?,
    val strength: Int?,
    val stealthDisadvantage: Boolean,
) {
    /** Формула КД для `StatFormula`: «11 + [dex]», «14 + min([dex], 2)», «16», у щита — «2». */
    fun acFormula(): String = when {
        type == ArmorType.SHIELD -> baseAc.toString()
        dexCap == null -> "$baseAc + [dex]"
        dexCap == 0 -> baseAc.toString()
        else -> "$baseAc + min([dex], $dexCap)"
    }

    /** Подпись КД: «КД 11 + Лов», «КД 14 + Лов (макс. 2)», «КД 16», «+2 к КД». */
    fun acLabel(): String = when {
        type == ArmorType.SHIELD -> "+$baseAc к КД"
        dexCap == null -> "КД $baseAc + Лов"
        dexCap == 0 -> "КД $baseAc"
        else -> "КД $baseAc + Лов (макс. $dexCap)"
    }

    /** Особенности: «Сила 15», «Помеха Скрытности». */
    fun notes(): List<String> = buildList {
        strength?.let { add("Сила $it") }
        if (stealthDisadvantage) add("Помеха Скрытности")
    }
}

/** Категория оружия по таблице PHB. */
enum class WeaponCategory { SIMPLE_MELEE, SIMPLE_RANGED, MARTIAL_MELEE, MARTIAL_RANGED }

/**
 * Оружие.
 *
 * @param damage кость урона: «1к8»; «—» у сети, которая урона не наносит.
 * @param damageType тип урона в именительном падеже прилагательного: «колющий».
 * @param properties свойства как в таблице: «фехтовальное», «универсальное (1к10)», «боеприпасы (80/320)».
 */
data class WeaponInfo(
    val id: String,
    val name: String,
    val category: WeaponCategory,
    val damage: String,
    val damageType: String,
    val properties: List<String>,
) {
    /** Краткая запись: «1к8 колющий · фехтовальное, лёгкое». */
    fun summary(): String {
        val damagePart = if (damage == NO_DAMAGE) "" else "$damage $damageType".trim()
        val propertiesPart = properties.joinToString(", ")
        return listOf(damagePart, propertiesPart).filter { it.isNotBlank() }.joinToString(" · ")
    }

    companion object {
        /** Обозначение отсутствия урона (сеть). */
        const val NO_DAMAGE = "—"
    }
}

/**
 * Предмет стартового снаряжения.
 *
 * @param armor / [weapon] заполнены, если предмет узнан в справочнике; иначе это обычный предмет.
 */
data class EquipmentEntry(
    val name: String,
    val quantity: Int = 1,
    val armor: ArmorInfo? = null,
    val weapon: WeaponInfo? = null,
)

/**
 * Оружие на выбор: «любое простое оружие» → одно оружие из простых рукопашных и дальнобойных.
 *
 * @param label исходный фрагмент текста — показывается как подпись выбора.
 */
data class WeaponSlot(val count: Int, val categories: Set<WeaponCategory>, val label: String)

/** Разобранный вариант снаряжения: конкретные предметы и оружие, которое ещё нужно выбрать. */
data class ParsedEquipment(val entries: List<EquipmentEntry>, val slots: List<WeaponSlot>)

object EquipmentCatalog {

    // region Таблицы PHB

    val armor: List<ArmorInfo> = listOf(
        ArmorInfo("padded", "Стёганый доспех", ArmorType.LIGHT, 11, null, null, true),
        ArmorInfo("leather", "Кожаный доспех", ArmorType.LIGHT, 11, null, null, false),
        ArmorInfo("studded_leather", "Проклёпанная кожа", ArmorType.LIGHT, 12, null, null, false),
        ArmorInfo("hide", "Шкурный доспех", ArmorType.MEDIUM, 12, 2, null, false),
        ArmorInfo("chain_shirt", "Кольчужная рубаха", ArmorType.MEDIUM, 13, 2, null, false),
        ArmorInfo("scale_mail", "Чешуйчатый доспех", ArmorType.MEDIUM, 14, 2, null, true),
        ArmorInfo("breastplate", "Кираса", ArmorType.MEDIUM, 14, 2, null, false),
        ArmorInfo("half_plate", "Полулаты", ArmorType.MEDIUM, 15, 2, null, true),
        ArmorInfo("ring_mail", "Колечный доспех", ArmorType.HEAVY, 14, 0, null, true),
        ArmorInfo("chain_mail", "Кольчуга", ArmorType.HEAVY, 16, 0, 13, true),
        ArmorInfo("splint", "Наборный доспех", ArmorType.HEAVY, 17, 0, 15, true),
        ArmorInfo("plate", "Латы", ArmorType.HEAVY, 18, 0, 15, true),
        ArmorInfo("shield", "Щит", ArmorType.SHIELD, 2, 0, null, false),
    )

    val weapons: List<WeaponInfo> = listOf(
        // Простое рукопашное.
        weapon("quarterstaff", "Боевой посох", WeaponCategory.SIMPLE_MELEE, "1к6", BLUDGEONING, "универсальное (1к8)"),
        weapon("mace", "Булава", WeaponCategory.SIMPLE_MELEE, "1к6", BLUDGEONING),
        weapon("club", "Дубинка", WeaponCategory.SIMPLE_MELEE, "1к4", BLUDGEONING, "лёгкое"),
        weapon("dagger", "Кинжал", WeaponCategory.SIMPLE_MELEE, "1к4", PIERCING, "фехтовальное", "лёгкое", "метательное (20/60)"),
        weapon("spear", "Копьё", WeaponCategory.SIMPLE_MELEE, "1к6", PIERCING, "метательное (20/60)", "универсальное (1к8)"),
        weapon("light_hammer", "Лёгкий молот", WeaponCategory.SIMPLE_MELEE, "1к4", BLUDGEONING, "лёгкое", "метательное (20/60)"),
        weapon("javelin", "Метательное копьё", WeaponCategory.SIMPLE_MELEE, "1к6", PIERCING, "метательное (30/120)"),
        weapon("greatclub", "Палица", WeaponCategory.SIMPLE_MELEE, "1к8", BLUDGEONING, "двуручное"),
        weapon("handaxe", "Ручной топор", WeaponCategory.SIMPLE_MELEE, "1к6", SLASHING, "лёгкое", "метательное (20/60)"),
        weapon("sickle", "Серп", WeaponCategory.SIMPLE_MELEE, "1к4", SLASHING, "лёгкое"),
        // Простое дальнобойное.
        weapon("dart", "Дротик", WeaponCategory.SIMPLE_RANGED, "1к4", PIERCING, "фехтовальное", "метательное (20/60)"),
        weapon("shortbow", "Короткий лук", WeaponCategory.SIMPLE_RANGED, "1к6", PIERCING, "боеприпасы (80/320)", "двуручное"),
        weapon(
            "light_crossbow", "Лёгкий арбалет", WeaponCategory.SIMPLE_RANGED, "1к8", PIERCING,
            "боеприпасы (80/320)", "перезарядка", "двуручное",
        ),
        weapon("sling", "Праща", WeaponCategory.SIMPLE_RANGED, "1к4", BLUDGEONING, "боеприпасы (30/120)"),
        // Воинское рукопашное.
        weapon("halberd", "Алебарда", WeaponCategory.MARTIAL_MELEE, "1к10", SLASHING, "тяжёлое", "досягаемость", "двуручное"),
        weapon("war_pick", "Боевая кирка", WeaponCategory.MARTIAL_MELEE, "1к8", PIERCING),
        weapon("warhammer", "Боевой молот", WeaponCategory.MARTIAL_MELEE, "1к8", BLUDGEONING, "универсальное (1к10)"),
        weapon("battleaxe", "Боевой топор", WeaponCategory.MARTIAL_MELEE, "1к8", SLASHING, "универсальное (1к10)"),
        weapon("glaive", "Глефа", WeaponCategory.MARTIAL_MELEE, "1к10", SLASHING, "тяжёлое", "досягаемость", "двуручное"),
        weapon("greatsword", "Двуручный меч", WeaponCategory.MARTIAL_MELEE, "2к6", SLASHING, "тяжёлое", "двуручное"),
        weapon("lance", "Длинное копьё", WeaponCategory.MARTIAL_MELEE, "1к12", PIERCING, "досягаемость", "особое"),
        weapon("longsword", "Длинный меч", WeaponCategory.MARTIAL_MELEE, "1к8", SLASHING, "универсальное (1к10)"),
        weapon("whip", "Кнут", WeaponCategory.MARTIAL_MELEE, "1к4", SLASHING, "фехтовальное", "досягаемость"),
        weapon("shortsword", "Короткий меч", WeaponCategory.MARTIAL_MELEE, "1к6", PIERCING, "фехтовальное", "лёгкое"),
        weapon("morningstar", "Моргенштерн", WeaponCategory.MARTIAL_MELEE, "1к8", PIERCING),
        weapon("pike", "Пика", WeaponCategory.MARTIAL_MELEE, "1к10", PIERCING, "тяжёлое", "досягаемость", "двуручное"),
        weapon("rapier", "Рапира", WeaponCategory.MARTIAL_MELEE, "1к8", PIERCING, "фехтовальное"),
        weapon("greataxe", "Секира", WeaponCategory.MARTIAL_MELEE, "1к12", SLASHING, "тяжёлое", "двуручное"),
        weapon("scimitar", "Скимитар", WeaponCategory.MARTIAL_MELEE, "1к6", SLASHING, "фехтовальное", "лёгкое"),
        weapon("trident", "Трезубец", WeaponCategory.MARTIAL_MELEE, "1к6", PIERCING, "метательное (20/60)", "универсальное (1к8)"),
        weapon("flail", "Цеп", WeaponCategory.MARTIAL_MELEE, "1к8", BLUDGEONING),
        // Воинское дальнобойное.
        weapon("blowgun", "Духовая трубка", WeaponCategory.MARTIAL_RANGED, "1", PIERCING, "боеприпасы (25/100)", "перезарядка"),
        weapon(
            "longbow", "Длинный лук", WeaponCategory.MARTIAL_RANGED, "1к8", PIERCING,
            "боеприпасы (150/600)", "тяжёлое", "двуручное",
        ),
        weapon(
            "hand_crossbow", "Ручной арбалет", WeaponCategory.MARTIAL_RANGED, "1к6", PIERCING,
            "боеприпасы (30/120)", "лёгкое", "перезарядка",
        ),
        weapon(
            "heavy_crossbow", "Тяжёлый арбалет", WeaponCategory.MARTIAL_RANGED, "1к10", PIERCING,
            "боеприпасы (100/400)", "тяжёлое", "перезарядка", "двуручное",
        ),
        weapon("net", "Сеть", WeaponCategory.MARTIAL_RANGED, WeaponInfo.NO_DAMAGE, "", "метательное (5/15)", "особое"),
    )

    // endregion

    fun armorById(id: String): ArmorInfo? = armor.firstOrNull { it.id == id }

    fun weaponById(id: String): WeaponInfo? = weapons.firstOrNull { it.id == id }

    /** Оружие указанных категорий по алфавиту — варианты для [WeaponSlot]. */
    fun weapons(categories: Set<WeaponCategory>): List<WeaponInfo> =
        weapons.filter { it.category in categories }.sortedBy { it.name }

    // region Распознавание текста

    private const val LETTER_BEFORE = "(?<![а-я])"
    private const val LETTER_AFTER = "(?![а-я])"

    /**
     * Основы названий доспехов. Порядок важен: составные названия проверяются раньше
     * коротких («кольчужная рубаха» раньше «кольчуги», «проклёпанная кожа» раньше «кожаного»).
     */
    private val ARMOR_PATTERNS: List<Pair<Regex, String>> = listOf(
        "стеган" to "padded",
        "проклепан" to "studded_leather",
        "кожан\\S* (?:доспех|брон)" to "leather",
        "шкурн" to "hide",
        "кольчужн\\S* рубах" to "chain_shirt",
        "чешуйчат" to "scale_mail",
        "кирас" to "breastplate",
        "полулат" to "half_plate",
        "колечн" to "ring_mail",
        "кольчуг" to "chain_mail",
        "наборн" to "splint",
        "${LETTER_BEFORE}лат(?:ы|ах|ами)?$LETTER_AFTER" to "plate",
        "${LETTER_BEFORE}щит" to "shield",
    ).map { (pattern, id) -> Regex(pattern) to id }

    /**
     * Основы названий оружия. Составные названия («метательное копьё», «длинное копьё»)
     * проверяются раньше простого «копья».
     */
    private val WEAPON_PATTERNS: List<Pair<Regex, String>> = listOf(
        "боев\\S* посох" to "quarterstaff",
        "боев\\S* кирк" to "war_pick",
        "боев\\S* молот" to "warhammer",
        "боев\\S* топор" to "battleaxe",
        "л[её]гк\\S* молот" to "light_hammer",
        "л[её]гк\\S* арбалет" to "light_crossbow",
        "ручн\\S* топор" to "handaxe",
        "ручн\\S* арбалет" to "hand_crossbow",
        "тяж[её]л\\S* арбалет" to "heavy_crossbow",
        "метательн\\S* коп" to "javelin",
        "длинн\\S* коп" to "lance",
        "длинн\\S* меч" to "longsword",
        "длинн\\S* лук" to "longbow",
        "коротк\\S* меч" to "shortsword",
        "коротк\\S* лук" to "shortbow",
        "двуручн\\S* меч" to "greatsword",
        "духов\\S* трубк" to "blowgun",
        "${LETTER_BEFORE}коп(?:ье|ья|ий|ьям|ьями|ьем)$LETTER_AFTER" to "spear",
        "${LETTER_BEFORE}булав" to "mace",
        "${LETTER_BEFORE}дубинк" to "club",
        "${LETTER_BEFORE}кинжал" to "dagger",
        "${LETTER_BEFORE}палиц" to "greatclub",
        "${LETTER_BEFORE}серп(?:а|ы|ов|ом)?$LETTER_AFTER" to "sickle",
        "${LETTER_BEFORE}дротик" to "dart",
        "${LETTER_BEFORE}пращ" to "sling",
        "${LETTER_BEFORE}алебард" to "halberd",
        "${LETTER_BEFORE}глеф" to "glaive",
        "${LETTER_BEFORE}кнут" to "whip",
        "${LETTER_BEFORE}моргенштерн" to "morningstar",
        "${LETTER_BEFORE}пик(?:а|и|у|ой|ам|ами)?$LETTER_AFTER" to "pike",
        "${LETTER_BEFORE}рапир" to "rapier",
        "${LETTER_BEFORE}секир" to "greataxe",
        "${LETTER_BEFORE}скимитар" to "scimitar",
        "${LETTER_BEFORE}трезуб" to "trident",
        "${LETTER_BEFORE}цеп(?:а|ы|ов|ом)?$LETTER_AFTER" to "flail",
        "${LETTER_BEFORE}сет(?:ь|и|ью)$LETTER_AFTER" to "net",
    ).map { (pattern, id) -> Regex(pattern) to id }

    /** Боеприпасы с количеством впереди: «20 болтов» → «Болты» ×20. */
    private val AMMO_PATTERNS: List<Pair<Regex, String>> = listOf(
        "${LETTER_BEFORE}болт" to "Болты",
        "${LETTER_BEFORE}стрел" to "Стрелы",
        "${LETTER_BEFORE}игл" to "Иглы для духовой трубки",
        "${LETTER_BEFORE}(?:снаряд|пул)" to "Снаряды для пращи",
    ).map { (pattern, name) -> Regex(pattern) to name }

    /** Числа, которых нет в [MarkupText.number]. */
    private val EXTRA_NUMBERS = mapOf(
        "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10, "двадцать" to 20,
    )

    /** Слова, означающие одно оружие на выбор: «любое», «одно». */
    private val ANY_WORDS = listOf("любое", "любой", "любая")

    private val PARENTHESES = Regex("\\([^)]*\\)")

    /**
     * Разбирает вариант снаряжения на предметы и оружие на выбор.
     *
     * Текст делится на части через [BackgroundRulesParser.splitItems]; каждая часть
     * узнаётся как слот выбора оружия, доспех, оружие, боеприпасы или обычный предмет.
     */
    fun parseOption(label: String): ParsedEquipment {
        val entries = mutableListOf<EquipmentEntry>()
        val slots = mutableListOf<WeaponSlot>()
        BackgroundRulesParser.splitItems(MarkupText.plain(label)).forEach { raw ->
            val item = raw.trim()
            if (item.isEmpty()) return@forEach
            val normalized = normalize(item)
            val (quantity, rest) = splitQuantity(normalized)

            weaponSlot(normalized, item)?.let { slots += it; return@forEach }

            ARMOR_PATTERNS.firstOrNull { it.first.containsMatchIn(rest) }?.let { (_, id) ->
                val info = armorById(id)!!
                entries += EquipmentEntry(info.name, quantity ?: 1, armor = info)
                return@forEach
            }
            WEAPON_PATTERNS.firstOrNull { it.first.containsMatchIn(rest) }?.let { (_, id) ->
                val info = weaponById(id)!!
                entries += EquipmentEntry(info.name, quantity ?: 1, weapon = info)
                return@forEach
            }
            if (quantity != null) {
                AMMO_PATTERNS.firstOrNull { it.first.containsMatchIn(rest) }?.let { (_, name) ->
                    entries += EquipmentEntry(name, quantity)
                    return@forEach
                }
            }
            entries += EquipmentEntry(item.replaceFirstChar { it.uppercase() })
        }
        return ParsedEquipment(entries, slots)
    }

    /** Нижний регистр, «ё» → «е», без пояснений в скобках («(если владеете)»). */
    private fun normalize(text: String): String =
        text.lowercase().replace('ё', 'е').replace(PARENTHESES, " ").replace(Regex("\\s+"), " ").trim().trimEnd('.', ',', ';')

    /** Количество в начале части: «два коротких меча» → 2 и «коротких меча»; null — числа нет. */
    private fun splitQuantity(text: String): Pair<Int?, String> {
        val first = text.substringBefore(' ')
        val number = MarkupText.number(first) ?: EXTRA_NUMBERS[first]
        return if (number != null && text.contains(' ')) number to text.substringAfter(' ') else null to text
    }

    /**
     * Оружие на выбор: «любое простое оружие», «два воинских оружия», «простое рукопашное оружие».
     * null — часть не про выбор оружия.
     */
    private fun weaponSlot(normalized: String, label: String): WeaponSlot? {
        if (!normalized.contains("оруж")) return null
        val first = normalized.substringBefore(' ')
        val count = if (first in ANY_WORDS) 1 else MarkupText.number(first) ?: EXTRA_NUMBERS[first] ?: 1
        val simple = normalized.contains("прост")
        val martial = normalized.contains("воинск")
        val melee = normalized.contains("рукопашн")
        val ranged = normalized.contains("дальнобойн")
        val categories = WeaponCategory.entries.filter { category ->
            val isSimple = category == WeaponCategory.SIMPLE_MELEE || category == WeaponCategory.SIMPLE_RANGED
            val isMelee = category == WeaponCategory.SIMPLE_MELEE || category == WeaponCategory.MARTIAL_MELEE
            val byKind = (!simple && !martial) || (simple && isSimple) || (martial && !isSimple)
            val byRange = (!melee && !ranged) || (melee && isMelee) || (ranged && !isMelee)
            byKind && byRange
        }.toSet()
        return WeaponSlot(count = count, categories = categories, label = label.replaceFirstChar { it.uppercase() })
    }

    // endregion

    /** Описание предмета для инвентаря: характеристики доспеха или оружия; пусто для прочего. */
    fun description(entry: EquipmentEntry): String {
        entry.armor?.let { armor ->
            val kind = when (armor.type) {
                ArmorType.LIGHT -> "Лёгкий доспех"
                ArmorType.MEDIUM -> "Средний доспех"
                ArmorType.HEAVY -> "Тяжёлый доспех"
                ArmorType.SHIELD -> "Щит"
            }
            return (listOf(kind, armor.acLabel()) + armor.notes()).joinToString(". ") + "."
        }
        entry.weapon?.let { weapon ->
            val kind = when (weapon.category) {
                WeaponCategory.SIMPLE_MELEE -> "Простое рукопашное оружие"
                WeaponCategory.SIMPLE_RANGED -> "Простое дальнобойное оружие"
                WeaponCategory.MARTIAL_MELEE -> "Воинское рукопашное оружие"
                WeaponCategory.MARTIAL_RANGED -> "Воинское дальнобойное оружие"
            }
            val parts = buildList {
                add(kind)
                if (weapon.damage != WeaponInfo.NO_DAMAGE) add("Урон: ${weapon.damage} ${weapon.damageType}".trim())
                if (weapon.properties.isNotEmpty()) add("Свойства: ${weapon.properties.joinToString(", ")}")
            }
            return parts.joinToString(". ") + "."
        }
        return ""
    }

    private fun weapon(
        id: String,
        name: String,
        category: WeaponCategory,
        damage: String,
        damageType: String,
        vararg properties: String,
    ) = WeaponInfo(id, name, category, damage, damageType, properties.toList())

    private const val PIERCING = "колющий"
    private const val SLASHING = "рубящий"
    private const val BLUDGEONING = "дробящий"
}
