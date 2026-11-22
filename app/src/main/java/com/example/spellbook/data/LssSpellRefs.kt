package com.example.spellbook.data

import com.example.spellbook.data.model.Spell

/**
 * Запись названия заклинания вместе с английским, как на dnd.su и в LSS:
 * «Огненный снаряд [Fire bolt]».
 */
object SpellNames {

    private val BRACKETED = Regex("^(.*?)\\s*\\[([^\\[\\]]+)]\\s*$")

    /**
     * Делит «Огненный снаряд [Fire bolt]» на русское и английское название.
     * Без английского в скобках возвращает исходное название и пустую строку.
     */
    fun split(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        val match = BRACKETED.matchEntire(trimmed) ?: return trimmed to ""
        val name = match.groupValues[1].trim()
        val english = match.groupValues[2].trim()
        // «[Fire bolt]» без русской части — это просто название, а не пара.
        return if (name.isBlank()) trimmed to "" else name to english
    }

    /** Обратная операция к [split]: без английского названия — просто русское. */
    fun join(name: String, nameEn: String): String =
        if (nameEn.isBlank()) name else "$name [$nameEn]"
}

/**
 * Ссылки на заклинания в листе LSS.
 *
 * Книга заклинаний листа (`spells.prepared` / `spells.book`) ссылается на заклинания не текстом, а:
 * - id документа базы LSS (`65d3c16df3d820fa1add45d1`) — так пишет сам LSS, офлайн его не раскрыть;
 * - слагом — английским названием в нижнем регистре через дефис (`cure-wounds`);
 * - английским названием (`Cure Wounds`) или названием вида «Лечение ран [Cure Wounds]».
 *
 * При выгрузке приложение пишет слаг, при загрузке понимает всё, кроме id базы.
 */
object LssSpellRefs {

    /** id документа базы LSS — 24 шестнадцатеричных символа (ObjectId MongoDB). */
    private val LSS_ID = Regex("^[0-9a-fA-F]{24}$")

    /**
     * Заклинания SRD, которые в SRD названы без имён волшебников. На dnd.su (а значит,
     * и в библиотеке приложения) они называются по книге игрока, а лист LSS ищет SRD-слаг.
     */
    private val SRD_ALIASES = mapOf(
        "bigbys-hand" to "arcane-hand",
        "drawmijs-instant-summons" to "instant-summons",
        "evards-black-tentacles" to "black-tentacles",
        "leomunds-secret-chest" to "secret-chest",
        "leomunds-tiny-hut" to "tiny-hut",
        "melfs-acid-arrow" to "acid-arrow",
        "mordenkainens-faithful-hound" to "faithful-hound",
        "mordenkainens-magnificent-mansion" to "magnificent-mansion",
        "mordenkainens-private-sanctum" to "private-sanctum",
        "mordenkainens-sword" to "arcane-sword",
        "nystuls-magic-aura" to "arcanists-magic-aura",
        "otilukes-freezing-sphere" to "freezing-sphere",
        "otilukes-resilient-sphere" to "resilient-sphere",
        "ottos-irresistible-dance" to "irresistible-dance",
        "rarys-telepathic-bond" to "telepathic-bond",
        "tashas-hideous-laughter" to "hideous-laughter",
        "tensers-floating-disk" to "floating-disk",
    )

    /** Ссылка ли это на документ базы LSS, который можно найти только на сервере LSS. */
    fun isLssId(ref: String): Boolean = LSS_ID.matches(ref.trim())

    /**
     * Слаг LSS по английскому названию: «Tenser’s floating disk» → `floating-disk`,
     * «Antipathy/sympathy» → `antipathy-or-sympathy` (так их называет SRD).
     */
    fun slugify(english: String): String {
        val base = english.trim().lowercase()
            .replace('’', '\'')
            .replace("'", "")
            .replace("/", " or ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return SRD_ALIASES[base] ?: base
    }

    /** Слаг заклинания для листа LSS; null — английское название неизвестно. */
    fun slugOf(spell: Spell): String? = spell.nameEn.takeIf { it.isNotBlank() }?.let(::slugify)

    /** Что записать в книгу заклинаний LSS: слаг, а без английского названия — русское название. */
    fun refOf(spell: Spell): String = slugOf(spell) ?: spell.name

    /**
     * Таблица `id базы LSS → слаг` из встроенного файла (`assets/lss-spell-ids.json`).
     * Собрана из открытых справочников SRD 5.1 и SRD 5.2 сайта LSS: только у этих заклинаний
     * id общие для всех; у остальных id свой в гримуаре каждого игрока.
     */
    fun parseIdTable(json: String): Map<String, String> {
        val obj = org.json.JSONObject(json)
        return obj.keys().asSequence().associateWith { obj.optString(it) }.filterValues { it.isNotBlank() }
    }

    /**
     * В SRD 5.2 «или» из названий убрано: `antipathy-sympathy` вместо `antipathy-or-sympathy`.
     * Для поиска оба написания сводятся к одному.
     */
    private fun slugKey(slug: String): String = slug.replace("-or-", "-")

    /**
     * Поиск заклинаний библиотеки по ссылкам из листа LSS.
     * Индекс строится один раз на всю библиотеку.
     *
     * @param lssIds таблица id базы LSS → слаг (см. [parseIdTable]): по ней находятся заклинания SRD,
     * записанные в листе id.
     */
    class Resolver(library: List<Spell>, private val lssIds: Map<String, String> = emptyMap()) {

        private val bySlug: Map<String, Spell> = library
            .mapNotNull { spell -> LssSpellRefs.slugOf(spell)?.let { slugKey(it) to spell } }
            .toMap()

        private val byName: Map<String, Spell> = library.associateBy { normalizeName(it.name) }

        /** Заклинание по ссылке; null — не найдено (в том числе id не из SRD). */
        fun resolve(ref: String): Spell? {
            val raw = ref.trim()
            if (raw.isBlank()) return null
            if (LssSpellRefs.isLssId(raw)) {
                return lssIds[raw.lowercase()]?.let { bySlug[slugKey(LssSpellRefs.slugify(it))] }
            }
            val (name, english) = SpellNames.split(raw)
            if (english.isNotBlank()) {
                bySlug[slugKey(LssSpellRefs.slugify(english))]?.let { return it }
            }
            byName[normalizeName(name)]?.let { return it }
            // Слаг и английское название сводятся к одному ключу.
            return bySlug[slugKey(LssSpellRefs.slugify(raw))]
        }

        private fun normalizeName(name: String): String =
            name.trim().lowercase().replace('ё', 'е')
    }
}
