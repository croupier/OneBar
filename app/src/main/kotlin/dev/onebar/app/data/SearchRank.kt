package dev.onebar.app.data

/**
 * Варианты запроса и общий ключ ранжирования.
 *
 * Строка из поля поиска превращается в упорядоченный список «игл»: сначала сама, затем
 * разрешённые настройками нормализации номера, перестановки е/ё и транслит ру↔ен.
 * Порядок списка и есть приоритет, поэтому ранжировщику не нужно знать, какой именно
 * вариант совпал.
 */
internal enum class VariantKind {
    EXACT,
    PHONE,
    YO,
    TRANSLIT,
}

internal data class Needle(val text: String, val kind: VariantKind)

/** Все варианты запроса в порядке приоритета; регистр снят уже здесь. */
internal fun buildNeedles(
    rawQuery: String,
    options: SearchOptions = SearchOptions(),
): List<Needle> {
    val exact = rawQuery.trim().lowercase()
    if (exact.isEmpty()) return emptyList()
    val needles = mutableListOf(Needle(exact, VariantKind.EXACT))
    val phones = if (options.phone) phoneForms(exact) else emptyList()
    if (phones.isNotEmpty()) {
        // Номерной запрос: е/ё и транслит к цифрам неприменимы, вариантов больше нет.
        return (needles + phones.map { Needle(it, VariantKind.PHONE) }).distinctBy { it.text }
    }
    if (options.yo && needsYoVariants(exact)) {
        needles += yoVariants(exact).map { Needle(it, VariantKind.YO) }
    }
    if (options.translit && needles.size < MAX_NEEDLES) {
        needles += translitVariants(exact).map { Needle(it, VariantKind.TRANSLIT) }
    }
    return needles.filter { it.text.isNotBlank() }.distinctBy { it.text }.take(MAX_NEEDLES)
}

/**
 * Только цифры и ведущий «+» — то же правило, что у скрытого в API PhoneNumberUtils.digits(),
 * но написанное здесь, чтобы ранжировщик проверялся на JVM без робозависимостей.
 */
internal fun numberDigits(raw: String): String = buildString(raw.length) {
    raw.forEachIndexed { index, char ->
        when {
            char in '0'..'9' -> append(char)
            char == '+' && index == 0 && isEmpty() -> append(char)
        }
    }
}

/**
 * Формы набранного номера: без разделителей, без «+» (он есть не во всех записях и сравнивать
 * по сути не с чем) и — если набранный номер начинается на «8» — тот же номер без восьмёрки.
 * Короткие запросы номером не считаем: «12» нашёл бы половину всех записей.
 */
internal fun phoneForms(rawQuery: String): List<String> {
    val digits = numberDigits(rawQuery.trim()).trimStart('+')
    if (digits.length < MIN_PHONE_DIGITS || !rawQuery.all { it.isDigit() || it in PHONE_SEPARATORS }) {
        return emptyList()
    }
    return listOf(digits, digits.removePrefix("8")).distinct()
}

/**
 * Маски LIKE для колонок номеров: между цифрами ставим '%', потому что в телефоне номер
 * хранится с пробелами и дефисами («+7 999 123-45-67»), а SQLite их не понимает.
 */
internal fun phoneLikePatterns(
    rawQuery: String,
    options: SearchOptions = SearchOptions(),
): List<String> = if (options.phone) {
    phoneForms(rawQuery).map { form -> "%" + form.map { it.toString() }.joinToString("%") + "%" }
} else {
    emptyList()
}

/** Перестановки е/ё имеют смысл только для кириллицы: в латинице этих букв нет. */
private fun needsYoVariants(text: String): Boolean =
    text.count { it == 'е' || it == 'ё' } in 1..MAX_YO_POSITIONS

/**
 * Все замены е↔ё, отсортированные по числу замен: вариант, отличающийся на одну букву,
 * вероятнее варианта, отличающегося на три. Исходная строка не возвращается — она и так
 * первый вариант.
 */
internal fun yoVariants(text: String): List<String> {
    val positions = text.indices.filter { text[it] == 'е' || text[it] == 'ё' }
    if (positions.isEmpty()) return emptyList()
    val combos = (1 until (1 shl positions.size)).sortedBy { Integer.bitCount(it) }.take(MAX_YO_VARIANTS)
    return combos.map { mask ->
        val chars = text.toCharArray()
        positions.forEachIndexed { index, at ->
            if ((mask shr index) and 1 == 1) chars[at] = if (chars[at] == 'е') 'ё' else 'е'
        }
        String(chars)
    }.distinct()
}

private class Fragment(val text: String, val level: Int, val last: Int)

/**
 * Транслит в обе стороны: по «код» ищем kod/cod, по «mus» — мус/муз. У буквы может быть
 * несколько записей, первая — основная; комбинации перебираются по числу отступлений от
 * основной, а внутри одного числа — справа налево, чтобы «муз» шёл раньше «мис». Дальние
 * комбинации (больше двух замен) отбрасываем: для одного слова они маловероятны, а место
 * в списке игл занимают.
 */
internal fun translitVariants(text: String): List<String> {
    val hasCyrillic = text.any { it.isCyrillic() }
    val hasLatin = text.any { it.isLatin() }
    // Смешанное написание («код kod») двусмысленно: направление транслита не определить.
    if (hasCyrillic == hasLatin) return emptyList()
    val table = if (hasCyrillic) RU_TO_LAT else LAT_TO_RU
    var fragments = listOf(Fragment("", 0, 0))
    text.forEachIndexed { position, char ->
        val options = table[char] ?: listOf(char.toString())
        fragments = options.flatMapIndexed { option, variant ->
            fragments.mapNotNull { fragment ->
                val level = fragment.level + if (option == 0) 0 else 1
                if (level > MAX_TRANSLIT_LEVEL) return@mapNotNull null
                Fragment(fragment.text + variant, level, if (option == 0) fragment.last else position)
            }
        }.sortedWith(compareBy({ it.level }, { -it.last })).take(MAX_TRANSLIT_VARIANTS)
    }
    return fragments.map { it.text }.filter { it != text }.distinct()
}

private fun Char.isCyrillic(): Boolean = code in 0x0400..0x04FF

private fun Char.isLatin(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/**
 * Итоговый порядок совпадений: избранные → основное поле → доп. поля, внутри — вариант
 * запроса по приоритету и положение (с начала названия важнее внутри). Равные ключи
 * сохраняют порядок источника, поэтому сортировка провайдера контактов остаётся
 * последним tie-break'ом.
 */
internal data class Rank(val favorite: Int, val field: Int, val variant: Int, val position: Int) :
    Comparable<Rank> {
    override fun compareTo(other: Rank): Int = compareValuesBy(this, other, { it.favorite }, { it.field },
        { it.variant }, { it.position })
}

/** Лучшее из совпадений по всем группам полей; null — если ни один вариант не подошёл. */
internal fun rankOf(
    needles: List<Needle>,
    fieldsByGroup: List<List<String>>,
    favorite: Boolean,
    options: SearchOptions = SearchOptions(),
): Rank? {
    var best: Rank? = null
    // Выключенные обе опции не означают «ничего не искать»: совпадение тогда ищем в любом месте.
    val matchInside = options.contains || !options.prefix
    for (groupIndex in fieldsByGroup.indices) {
        for (variant in needles.indices) {
            val needle = needles[variant].text
            for (field in fieldsByGroup[groupIndex]) {
                if (field.isBlank()) continue
                val starts = field.startsWith(needle)
                val contains = field.contains(needle)
                val position = when {
                    starts && options.prefix -> POSITION_START
                    contains && matchInside -> POSITION_INSIDE
                    starts && matchInside -> POSITION_INSIDE
                    else -> continue
                }
                val candidate = Rank(if (favorite) 0 else 1, groupIndex, variant, position)
                val current = best
                if (current == null || candidate < current) best = candidate
            }
        }
    }
    return best
}

/**
 * Маски для LIKE у провайдера контактов. SQLite складывает регистр только по ASCII, поэтому
 * кириллицу отдаём в трёх регистрах, а е/ё заменяем на «любой символ» — иначе перестановки
 * е/ё дали бы десятки условий. Набор шире нужного: точность проверяет ранжировщик.
 *
 * Число масок ограничено: их перебирает провайдер, а хвост списка — самые редкие написания,
 * ради которых не стоит платить полным сканом телефонной книги.
 */
internal fun likePatterns(rawQuery: String, options: SearchOptions = SearchOptions()): List<String> {
    val patterns = LinkedHashSet<String>()
    for (needle in buildNeedles(rawQuery, options)) {
        val wildcarded = buildString(needle.text.length) {
            for (char in needle.text) when (char) {
                '%', '_' -> append('\\').append(char)
                'е', 'ё' -> append('_')
                else -> append(char)
            }
        }
        patterns += casings(wildcarded)
        if (patterns.size >= MAX_LIKE_PATTERNS) break
    }
    return patterns.toList().take(MAX_LIKE_PATTERNS)
}

private fun casings(pattern: String): List<String> {
    val lower = pattern.lowercase()
    // Латиницу SQLite сложит сам; для кириллицы нужны все три способа написания слова.
    if (lower.none { it.code > 127 }) return listOf(lower)
    return listOf(lower, lower.uppercase(), lower.replaceFirstChar { it.uppercaseChar() })
}

internal const val POSITION_START = 0
internal const val POSITION_INSIDE = 1
private const val MAX_NEEDLES = 24
private const val MIN_PHONE_DIGITS = 4

// Разделители, которые пользователь ставит в номере руками: скобки, дефис, пробел, точка.
private const val PHONE_SEPARATORS = "+-(). "
private const val MAX_YO_POSITIONS = 5
private const val MAX_YO_VARIANTS = 12

// Больше двух отступлений от основной записи в одном слове — это уже другая система
// транслитерации, в контактах и названиях приложений она почти не встречается.
private const val MAX_TRANSLIT_LEVEL = 2
private const val MAX_TRANSLIT_VARIANTS = 24

// Масок LIKE на запрос к провайдеру: дальше добавочные написания дают меньше новых строк,
// чем стоят полным перебором таблицы.
private const val MAX_LIKE_PATTERNS = 12

private val RU_TO_LAT: Map<Char, List<String>> = mapOf(
    'а' to listOf("a"), 'б' to listOf("b"), 'в' to listOf("v"), 'г' to listOf("g"),
    'д' to listOf("d"), 'е' to listOf("e"), 'ё' to listOf("yo", "e"), 'ж' to listOf("zh", "j"),
    'з' to listOf("z"), 'и' to listOf("i"), 'й' to listOf("y", "i"), 'к' to listOf("k", "c"),
    'л' to listOf("l"), 'м' to listOf("m"), 'н' to listOf("n"), 'о' to listOf("o"),
    'п' to listOf("p"), 'р' to listOf("r"), 'с' to listOf("s"), 'т' to listOf("t"),
    'у' to listOf("u"), 'ф' to listOf("f"), 'х' to listOf("h", "kh"), 'ц' to listOf("ts", "c"),
    'ч' to listOf("ch"), 'ш' to listOf("sh"), 'щ' to listOf("sch", "shch"), 'ъ' to listOf(""),
    'ы' to listOf("y", "i"), 'ь' to listOf(""), 'э' to listOf("e"), 'ю' to listOf("yu"),
    'я' to listOf("ya"),
)

private val LAT_TO_RU: Map<Char, List<String>> = mapOf(
    'a' to listOf("а"), 'b' to listOf("б"), 'c' to listOf("к", "ц"), 'd' to listOf("д"),
    'e' to listOf("е", "ё"), 'f' to listOf("ф"), 'g' to listOf("г"), 'h' to listOf("х"),
    'i' to listOf("и"), 'j' to listOf("й", "ж"), 'k' to listOf("к"), 'l' to listOf("л"),
    'm' to listOf("м"), 'n' to listOf("н"), 'o' to listOf("о"), 'p' to listOf("п"),
    'q' to listOf("а"), 'r' to listOf("р"), 's' to listOf("с", "з"), 't' to listOf("т"),
    'u' to listOf("у", "и"), 'v' to listOf("в"), 'w' to listOf("ш", "ц"), 'x' to listOf("кс", "х"),
    'y' to listOf("ы", "и"), 'z' to listOf("з"),
    // Апостроф в русском слове, набранном латиницей, — мягкий знак («ob'ekt»), клавиша «`» — «ё».
    '\'' to listOf("ь"), '`' to listOf("ё"),
)
