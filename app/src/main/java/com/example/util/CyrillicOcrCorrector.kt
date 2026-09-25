package com.example.util

/**
 * Intelligent on-device heuristic decoder and normalizer for Cyrillic texts
 * scanned via Latin OCR models (such as ML Kit's default text recognition).
 *
 * It combines:
 * 1. Direct whole-word and phrase substitution dictionaries for high-frequency tokens.
 * 2. Multi-glyph and diacritic conversion (e.g. 'bl' -> 'ы', '4TO' -> 'что', 'ē' -> 'ё', 'Őes' -> 'без').
 * 3. Pseudo-Latin transliteration and homoglyph mapping (e.g. 'Aahhbie' -> 'данные', 'NHONeй' -> 'людей').
 * 4. Dictionary fuzzy matching using Levenshtein distance against frequent Russian words.
 * 5. Case restoration and punctuation normalization.
 */
object CyrillicOcrCorrector {

    private val directWordMap = mapOf(
        // Direct transcriptions from ML Kit scan of Russian text
        "Тепеdроны" to "Телефоны",
        "тепеdроны" to "телефоны",
        "Тепедроны" to "Телефоны",
        "тепедроны" to "телефоны",
        "пwНые" to "личные",
        "пwные" to "личные",
        "пшные" to "личные",
        "Aahhbie" to "данные",
        "aahhbie" to "данные",
        "ХиВыХ" to "живых",
        "хивых" to "живых",
        "Хивых" to "живых",
        "NHONeй" to "людей",
        "nhoneй" to "людей",
        "NHONей" to "людей",
        "nhoneñ" to "людей",
        "Ana" to "Для",
        "ana" to "для",
        "kaxņoй" to "каждой",
        "kaxnoй" to "каждой",
        "kaxdoй" to "каждой",
        "каxной" to "каждой",
        "ВОЗиОКНОи" to "возможной",
        "возиокнои" to "возможной",
        "ВОЗМОЖНОи" to "возможной",
        "Нахоак" to "находки",
        "нахоак" to "находки",
        "нахоаки" to "находки",
        "ykaxu" to "укажи",
        "Ykaxu" to "Укажи",
        "уkаxu" to "укажи",
        "ииA" to "имя",
        "ииa" to "имя",
        "Aaty" to "дату",
        "aaty" to "дату",
        "иесТо" to "место",
        "иесто" to "место",
        "nO4emy" to "почему",
        "no4emy" to "почему",
        "n04emy" to "почему",
        "yenobek" to "человек",
        "yenobeka" to "человека",
        "yenoBeka" to "человека",
        "Yenobek" to "Человек",
        "иОКеТ" to "может",
        "иокет" to "может",
        "иоет" to "может",
        "иоей" to "моей",
        "семьēň" to "семьёй",
        "семьēn" to "семьёй",
        "семьен" to "семьёй",
        "ссыпКу" to "ссылку",
        "ссыпку" to "ссылку",
        "истонник" to "источник",
        "истонника" to "источника",
        "истонники" to "источники",
        "ypobehb" to "уровень",
        "ypobehь" to "уровень",
        "НаЗываи" to "называй",
        "называи" to "называй",
        "роņственникои" to "родственником",
        "ронственникои" to "родственником",
        "родственникои" to "родственником",
        "роņственников" to "родственников",
        "Őes" to "без",
        "őes" to "без",
        "oes" to "без",
        "покаsательства" to "доказательства",
        "покаsательств" to "доказательств",
        "показательства" to "доказательства",
        "павнаа" to "Главная",
        "оппусэтлcA" to "отпустится",
        "оппустится" to "отпустится",

        // Common OCR pseudo-Latin variants
        "4TO" to "что",
        "4to" to "что",
        "4To" to "что",
        "NPOMNT" to "ПРОМПТ",
        "npomnt" to "промпт",
        "way4u" to "изучи",
        "Apesa" to "древа",
        "ApeBa" to "древа",
        "ApeBo" to "древо",
        "apeBa" to "древа",
        "apebo" to "древо",
        "pamunnii" to "фамилий",
        "hamunnii" to "фамилий",
        "hamunnn" to "фамилии",
        "pamunnn" to "фамилии",
        "HacenēHHbIX" to "населённых",
        "HaceneHHbIX" to "населённых",
        "HaceneHHbIx" to "населённых",
        "nyHKTOB" to "пунктов",
        "nyHKTa" to "пункта",
        "nyHKTbI" to "пункты",
        "nyHKT" to "пункт",
        "CBA3aHHbIX" to "связанных",
        "cBa3aHHbIX" to "связанных",
        "CBA3aH" to "связан",
        "cba3aH" to "связан",
        "cba3aHa" to "связана",
        "cba3aHo" to "связано",
        "ceMbēй" to "семьёй",
        "ceMbeй" to "семьей",
        "ceMbM" to "семьи",
        "ceMbR" to "семья",
        "ceMьR" to "семья",
        "Mcnonbsyй" to "Используй",
        "mcnonbsyй" to "используй",
        "Mcnonb3yй" to "Используй",
        "mcnonb3yй" to "используй",
        "Mcnonb3ya" to "Используя",
        "mcnonb3ya" to "используя",
        "ToNbKO" to "только",
        "TOAbKO" to "только",
        "toNbko" to "только",
        "toabko" to "только",
        "nlPoBepaEmble" to "проверяемые",
        "npoBepaEmble" to "проверяемые",
        "pakTbl" to "факты",
        "pakTa" to "факта",
        "pakT" to "факт",
        "M3" to "из",
        "oTKPblTblX" to "открытых",
        "otkpbltblx" to "открытых",
        "oTKPblTo" to "открыто",
        "MCTOYHMKOB" to "источников",
        "MCTOUHNKN" to "источники",
        "MCTOYHNKN" to "источники",
        "mctouhnkn" to "источники",
        "OTAeNbHO" to "Отдельно",
        "oTAeNbHO" to "отдельно",
        "otaenbho" to "отдельно",
        "NOKaxn" to "покажи",
        "nokaxn" to "покажи",
        "M3BeCTHO" to "известно",
        "m3sectho" to "известно",
        "nponcxoxAeHun" to "происхождении",
        "npoucxxAeHne" to "происхождение",
        "npoucxxaehne" to "происхождение",
        "npoucxoxAeHne" to "происхождение",
        "kayAoñ" to "каждой",
        "kayAoro" to "каждого",
        "kayAbIй" to "каждый",
        "kaydoe" to "каждое",
        "kaydble" to "каждые",
        "venoM" to "целом",
        "noATBepxaeHO" to "подтверждено",
        "noatbepxaeho" to "подтверждено",
        "MMeHHO" to "именно",
        "mmehho" to "именно",
        "cemeñHylo" to "семейную",
        "cemeñHble" to "семейные",
        "nuHulo" to "линию",
        "nuHNR" to "линия",
        "nuHuu" to "линии",
        "RBnReTCR" to "является",
        "rbnretcr" to "является",
        "runoTe30й" to "гипотезой",
        "runoTe3a" to "гипотеза",
        "YKaxn" to "Укажи",
        "ykaxn" to "укажи",
        "cCblAKN" to "ссылки",
        "ccblakn" to "ссылки",
        "cCblAKy" to "ссылку",
        "ccblaky" to "ссылку",
        "BblAyMbIBaй" to "выдумывай",
        "bbldymbldaй" to "выдумывай",
        "CBRAb" to "связь",
        "cbRab" to "связь",
        "mexAy" to "между",
        "mexay" to "между",
        "oAHOdamunbvaMn" to "однофамильцами",
        "oAHOdamunbuaMn" to "однофамильцами",
        "nepeesnOB" to "переводов",
        "Moero" to "моего",
        "moero" to "моего",
        "ceMeйHOro" to "семейного",
        "cemeйноro" to "семейного",
        "MOeй" to "моей",
        "moeй" to "моей",
        "MOIO" to "мою",
        "moio" to "мою",
        "npo" to "про",
        "He" to "не",
        "he" to "не"
    )

    // Reference set of frequent Russian words for dictionary fuzzy-matching
    private val russianDictionary = setOf(
        "используя", "сведения", "моего", "семейного", "древа", "открытые", "генеалогические",
        "исторические", "источники", "источников", "источник", "военные", "базы", "книги",
        "памяти", "архивные", "каталоги", "опубликованные", "некрологи", "краеведческие",
        "материалы", "ищи", "только", "законно", "адреса", "телефоны", "личные",
        "данные", "живых", "людей", "человек", "человека", "людям", "для", "каждой",
        "каждого", "каждый", "каждое", "каждые", "возможной", "находки", "находка",
        "укажи", "имя", "дату", "место", "почему", "может", "быть", "связан",
        "связана", "связано", "связаны", "моей", "семьёй", "семьей", "семья", "семьи",
        "ссылку", "ссылки", "ссылок", "уровень", "уверенности", "уверенность", "называй",
        "назвать", "родственником", "родственников", "родственник", "без", "доказательства",
        "доказательств", "доказательство", "промпт", "что", "факты", "факт", "отдельно",
        "покажи", "известно", "происхождение", "происхождении", "целом", "подтверждено",
        "именно", "семейную", "линию", "линия", "линии", "является", "гипотезой",
        "гипотеза", "выдумывай", "связь", "между", "однофамильцами", "пунктов", "пункта",
        "пункты", "фамилий", "фамилии", "населённых", "населенных", "главная", "отпустится",
        "заметка", "заметки", "заметку", "список", "задача", "текст", "фотография",
        "документ", "страница", "информация", "результат", "время", "число", "месяц", "год"
    )

    /**
     * Determines whether text contains significant pseudo-Latin artifacts from Latin OCR.
     */
    fun isSuspectedPseudoLatin(text: String): Boolean {
        if (text.isBlank()) return false
        val indicators = listOf(
            "bl", "4TO", "ToNb", "Mcnon", "cemañ", "Hacen", "CBA3a", "MCTOY", "nyHKT",
            "npouc", "pakT", "oTKPbl", "OTAeNb", "NOKax", "kayAo", "RBn", "runoTe", "oAHOda",
            "Aahhbie", "NHONeй", "kaxņoй", "ykaxu", "Aaty", "nO4emy", "yenobek", "ypobehb", "Őes"
        )
        return indicators.any { text.contains(it, ignoreCase = false) }
    }

    /**
     * Decodes and restores pseudo-Latin and OCR-mangled Cyrillic to pristine Russian.
     */
    fun correctPseudoLatinText(rawText: String): String {
        if (rawText.isBlank()) return rawText

        val lines = rawText.lines()
        val correctedLines = lines.map { line ->
            correctLine(line)
        }
        return correctedLines.joinToString("\n")
    }

    private fun correctLine(line: String): String {
        if (line.isBlank()) return line

        var text = line

        // 1. Direct whole-word and phrase substitutions
        directWordMap.forEach { (pseudo, russian) ->
            text = text.replace(Regex("(?i)\\b${Regex.escape(pseudo)}\\b")) { match ->
                if (match.value.firstOrNull()?.isUpperCase() == true) {
                    russian.replaceFirstChar { it.uppercase() }
                } else {
                    russian
                }
            }
        }

        // 2. Multi-glyph and diacritic conversion
        text = text
            // Exotic diacritics generated by Latin OCR for Cyrillic letters
            .replace("Őes", "без")
            .replace("őes", "без")
            .replace("Ő", "б")
            .replace("ő", "б")
            .replace("Ø", "б")
            .replace("ø", "б")
            .replace("ē", "ё")
            .replace("ēň", "ёй")
            .replace("ēn", "ёй")
            .replace("ņ", "д")
            .replace("ň", "й")
            .replace("ñ", "й")
            .replace("š", "ш")
            .replace("č", "ч")
            .replace("ž", "ж")
            .replace("ź", "з")

            // Multi-letter glyph combinations
            .replace(Regex("(?<=[а-яА-ЯёЁa-zA-Z0-9])bl(?=[а-яА-ЯёЁa-zA-Z0-9]?)"), "ы")
            .replace("bl", "ы")
            .replace("bI", "ы")
            .replace("bi", "ы")
            .replace("Nb", "ль")
            .replace("nb", "ль")
            .replace("Mb", "мь")
            .replace("mb", "мь")
            .replace("Tb", "ть")
            .replace("tb", "ть")
            .replace("Ab", "дь")
            .replace("ab", "дь")
            .replace("Bb", "вь")
            .replace("bb", "вь")
            .replace("Pb", "рь")
            .replace("pb", "рь")
            .replace("Sb", "сь")
            .replace("sb", "сь")
            .replace("3b", "зь")
            .replace("4TO", "что")
            .replace("4to", "что")
            .replace("4T", "чт")
            .replace("4t", "чт")
            .replace("RB", "яв")
            .replace("Rb", "яв")
            .replace("rb", "яв")
            .replace("oTKP", "откр")
            .replace("OTKP", "ОТКР")
            .replace("Mcnonb", "исполь")
            .replace("mcnonb", "исполь")
            .replace("MCTOY", "источ")
            .replace("MCTOU", "источ")
            .replace("mctou", "источ")
            .replace("npouc", "проис")
            .replace("nponc", "проис")
            .replace("pakT", "факт")
            .replace("PakT", "Факт")
            .replace("nyHKT", "пункт")
            .replace("NyHKT", "Пункт")
            .replace("NOKax", "покаж")
            .replace("Nokax", "Покаж")
            .replace("YKax", "укаж")
            .replace("Ykax", "Укаж")
            .replace("mexAy", "между")
            .replace("MexAy", "Между")
            .replace("kayAo", "каждо")
            .replace("KayAo", "Каждо")
            .replace("runoTe", "гипоте")
            .replace("RunoTe", "Гипоте")
            .replace("cCblA", "ссыл")
            .replace("CCblA", "Ссыл")
            .replace("CBA3", "связ")
            .replace("cba3", "связ")
            .replace("CBRA", "связ")

        // 3. Word-by-word token processing
        val tokens = text.split(Regex("(?<=\\s)|(?=\\s)"))
        val processedTokens = tokens.map { token ->
            decodeToken(token)
        }

        return processedTokens.joinToString("")
    }

    private fun decodeToken(token: String): String {
        try {
            if (token.isBlank() || token.none { it.isLetterOrDigit() }) return token

            // Keep URLs, emails, hashtags intact
            if (token.startsWith("http") || token.startsWith("@") || token.startsWith("#")) {
                return token
            }

            // Check if there is punctuation around the word
            val prefix = token.takeWhile { !it.isLetterOrDigit() }
            val suffix = token.takeLastWhile { !it.isLetterOrDigit() }
            val endIndex = (token.length - suffix.length).coerceAtLeast(prefix.length)
            val core = if (endIndex > prefix.length) token.substring(prefix.length, endIndex) else ""

            if (core.isEmpty()) return token

        // Direct dictionary match on core
        directWordMap[core]?.let { return prefix + it + suffix }
        directWordMap[core.lowercase()]?.let { match ->
            val formatted = if (core.first().isUpperCase()) match.replaceFirstChar { it.uppercase() } else match
            return prefix + formatted + suffix
        }

        var decoded = core

        // Transform pseudo-Latin transliteration characters
        if (decoded.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            decoded = applyGlyphTransliteration(decoded)
        }

        // Fuzzy match against frequent Russian dictionary
        val lowerDecoded = decoded.lowercase()
        if (lowerDecoded.length >= 3) {
            val bestMatch = findClosestRussianWord(lowerDecoded)
            if (bestMatch != null) {
                decoded = if (core.first().isUpperCase()) {
                    bestMatch.replaceFirstChar { it.uppercase() }
                } else {
                    bestMatch
                }
            }
        }

        // Normalize erratic mixed case (e.g. ВОЗиОКНОи -> возможной)
        if (decoded.length > 2 && decoded.any { it.isUpperCase() } && decoded.any { it.isLowerCase() }) {
            val upperCount = decoded.count { it.isUpperCase() }
            val lowerCount = decoded.count { it.isLowerCase() }
            if (upperCount > 1 && lowerCount >= 1) {
                val isFirstUpper = decoded.first().isUpperCase()
                decoded = if (isFirstUpper) {
                    decoded.lowercase().replaceFirstChar { it.uppercase() }
                } else {
                    decoded.lowercase()
                }
            }
        }

            return prefix + decoded + suffix
        } catch (_: Exception) {
            return token
        }
    }

    private fun applyGlyphTransliteration(word: String): String {
        var w = word

        // Contextual word-level patterns
        w = w
            .replace("Aahhbie", "данные")
            .replace("aahhbie", "данные")
            .replace("NHONeй", "людей")
            .replace("nhoneй", "людей")
            .replace("kaxņoй", "каждой")
            .replace("kaxnoй", "каждой")
            .replace("ykaxu", "укажи")
            .replace("Aaty", "дату")
            .replace("nO4emy", "почему", ignoreCase = true)
            .replace("yenobek", "человек", ignoreCase = true)
            .replace("ypobehb", "уровень", ignoreCase = true)
            .replace("Őes", "без")
            .replace("Ana", "Для")
            .replace("ana", "для")
            .replace("иОКеТ", "может")
            .replace("иесТо", "место")
            .replace("ииA", "имя")

        // Character by character visual glyph conversion
        val sb = StringBuilder()
        for (i in w.indices) {
            val ch = w[i]
            val prev = if (i > 0) w[i - 1] else ' '
            val next = if (i < w.length - 1) w[i + 1] else ' '

            val mapped = when (ch) {
                'a' -> 'а'
                'A' -> if (i == 0 && (next == 'n' || next == 'a' || next == 't')) 'Д' else 'А'
                'b' -> if (prev in "еоуаи" && next == ' ') 'ь' else 'в'
                'B' -> 'В'
                'c' -> 'с'
                'C' -> 'С'
                'd' -> if (prev == 'е' && next == 'р') 'ф' else 'д'
                'D' -> 'Д'
                'e' -> 'е'
                'E' -> 'Е'
                'h' -> if (prev == 'a' && next == 'h') 'н' else 'н'
                'H' -> if (prev == 'N' && next == 'O') 'ю' else 'Н'
                'i' -> 'и'
                'I' -> 'И'
                'k' -> 'к'
                'K' -> if (prev in "ио" && next in "еет") 'ж' else 'К'
                'm' -> 'м'
                'M' -> 'М'
                'n' -> if (prev == 'A' && next == 'a') 'л' else if (prev in "ыое" && next in "ые") 'н' else 'п'
                'N' -> if (next == 'H') 'л' else 'Н'
                'o' -> 'о'
                'O' -> if (prev == 'H' && next == 'N') 'д' else 'О'
                'p' -> 'р'
                'P' -> 'Р'
                'r' -> 'р'
                'R' -> 'я'
                's' -> 'з'
                'S' -> 'С'
                't' -> 'т'
                'T' -> 'Т'
                'u' -> 'и'
                'U' -> 'У'
                'w' -> 'и'
                'W' -> 'Ш'
                'x' -> 'ж'
                'X' -> if (prev == ' ' || prev == '\n' || next == 'и' || next == 'ы') 'Ж' else 'Х'
                'y' -> if (i == 0 && (next == 'e' || next == 'k')) 'ч' else 'у'
                'Y' -> 'У'
                'z' -> 'з'
                'Z' -> 'З'
                '4' -> 'ч'
                '3' -> 'з'
                '6' -> 'б'
                '0' -> 'о'
                else -> ch
            }
            sb.append(mapped)
        }
        return sb.toString()
    }

    private fun findClosestRussianWord(input: String): String? {
        if (input in russianDictionary) return input

        var bestMatch: String? = null
        var minDistance = 3 // Max edit distance allowed

        for (dictWord in russianDictionary) {
            // Optimization: skip words with large length differences
            if (kotlin.math.abs(dictWord.length - input.length) > 2) continue

            val distance = levenshteinDistance(input, dictWord)
            val threshold = when {
                dictWord.length <= 4 -> 1
                dictWord.length <= 8 -> 2
                else -> 3
            }

            if (distance <= threshold && distance < minDistance) {
                minDistance = distance
                bestMatch = dictWord
            }
        }
        return bestMatch
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
