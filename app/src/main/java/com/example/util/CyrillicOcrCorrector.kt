package com.example.util

/**
 * Intelligent on-device heuristic decoder and normalizer for Cyrillic texts
 * scanned via Latin-only OCR models (e.g. ML Kit default text recognition).
 *
 * ML Kit's Latin model substitutes Cyrillic glyphs with visually or phonetically
 * similar Latin letters, digits and diacritics (e.g., 'bl' for 'ы', '4TO' for 'что',
 * 'Mcnonbsyй' for 'Используй', 'Apesa' for 'древа', 'Nb' for 'ль', etc.).
 */
object CyrillicOcrCorrector {

    private val directWordMap = mapOf(
        "4TO" to "что",
        "4to" to "что",
        "4To" to "что",
        "NPOMNT" to "ПРОМПТ",
        "npomnt" to "промпт",
        "way4u" to "изучи",
        "Apesa" to "древа",
        "ApeBa" to "древа",
        "ApeBo" to "древо",
        "ApeBa" to "древа",
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
        "CBA3aHHbIX" to "связанных",
        "cBa3aHHbIX" to "связанных",
        "CBA3aH" to "связан",
        "ceMbēй" to "семьёй",
        "ceMbeй" to "семьей",
        "ceMbM" to "семьи",
        "ceMbR" to "семья",
        "Mcnonbsyй" to "Используй",
        "mcnonbsyй" to "используй",
        "Mcnonb3yй" to "Используй",
        "ToNbKO" to "только",
        "TOAbKO" to "только",
        "toNbko" to "только",
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
        "venoM" to "целом",
        "noATBepxaeHO" to "подтверждено",
        "noatbepxaeho" to "подтверждено",
        "MMeHHO" to "именно",
        "mmehho" to "именно",
        "cemeñHylo" to "семейную",
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
        "ceMeйHOro" to "семейного",
        "MOeй" to "моей",
        "MOIO" to "мою",
        "npo" to "про",
        "He" to "не"
    )

    /**
     * Checks if the text looks like corrupted pseudo-Latin ML Kit OCR output.
     */
    fun isSuspectedPseudoLatin(text: String): Boolean {
        if (text.isBlank()) return false
        val indicators = listOf(
            "bl", "4TO", "ToNb", "Mcnon", "cemañ", "Hacen", "CBA3a", "MCTOY", "nyHKT",
            "npouc", "pakT", "oTKPbl", "OTAeNb", "NOKax", "kayAo", "RBn", "runoTe", "oAHOda"
        )
        val matchCount = indicators.count { text.contains(it, ignoreCase = false) }
        return matchCount >= 2 || (matchCount >= 1 && text.contains("bl"))
    }

    /**
     * Decodes and restores pseudo-Latin ML Kit OCR text to natural Russian Cyrillic.
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
            text = text.replace(Regex("\\b${Regex.escape(pseudo)}\\b"), russian)
        }

        // 2. Specific multi-character Cyrillic patterns
        text = text
            // "bl" -> "ы"
            .replace(Regex("(?<=[а-яА-ЯёЁa-zA-Z0-9])bl(?=[а-яА-ЯёЁa-zA-Z0-9]?)"), "ы")
            .replace("bl", "ы")
            .replace("bI", "ы")
            // Soft-sign combinations: Nb -> ль, Mb -> мь, Tb -> ть, Bb -> вь, etc.
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
            // Common prefix/stem visual patterns
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

        // 3. Word-by-word token processing for mixed Latin/Cyrillic words
        val words = text.split(" ")
        val processedWords = words.map { word ->
            decodeWord(word)
        }

        return processedWords.joinToString(" ")
    }

    private fun decodeWord(word: String): String {
        if (word.isBlank()) return word

        // Keep URLs, emails, hashtags, numbers intact
        if (word.startsWith("http") || word.startsWith("@") || word.startsWith("#")) {
            return word
        }

        // Direct map check
        directWordMap[word]?.let { return it }

        var result = word

        // Check if word contains mixed script or typical pseudo-latin transliterations
        val hasCyrillic = result.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val hasLatin = result.any { it in 'a'..'z' || it in 'A'..'Z' }

        if (hasLatin) {
            // Contextual Latin homoglyphs in Russian words
            // 'H' -> 'Н' / 'н', 'P' -> 'Р' / 'р', 'C' -> 'С' / 'с', 'B' -> 'В' / 'в', 'O' -> 'О' / 'о'
            // 'E' -> 'Е' / 'е', 'A' -> 'А' / 'а', 'T' -> 'Т' / 'т', 'M' -> 'М' / 'м', 'X' -> 'Х' / 'х', 'K' -> 'К' / 'к'
            result = result
                .replace("Moero", "моего")
                .replace("moero", "моего")
                .replace("ceMeйHOro", "семейного")
                .replace("cemeйноro", "семейного")
                .replace("cemañ", "семей")
                .replace("nuHulo", "линию")
                .replace("cemeñHylo", "семейную")
                .replace("Ha", "На")
                .replace("ocHOBe", "основе")
                .replace("ocHose", "основе")
                .replace("MOeй", "моей")
                .replace("MOIO", "мою")
                .replace("He", "не")
                .replace("C", "с")
                .replace("M", "и")
                .replace("D", "в")

            // If word still has mixed casing or Latin leftovers after substitutions,
            // harmonize visual homoglyphs if word context is overwhelmingly Cyrillic
            if (hasCyrillic || result.any { it in 'а'..'я' || it in 'А'..'Я' }) {
                result = convertLatinHomoglyphsToCyrillic(result)
            }
        }

        // Normalise erratic capitalization (e.g., "ceMeйHOro" -> "семейного", "noATBepxaeHO" -> "подтверждено")
        if (result.length > 2 && result.any { it.isUpperCase() } && result.any { it.isLowerCase() }) {
            val upperCount = result.count { it.isUpperCase() }
            val lowerCount = result.count { it.isLowerCase() }
            // If it's not a standard Capitalized word (first letter uppercase, rest lowercase)
            if (upperCount > 1 && lowerCount >= upperCount) {
                val isFirstUpper = result.first().isUpperCase()
                result = if (isFirstUpper) {
                    result.lowercase().replaceFirstChar { it.uppercase() }
                } else {
                    result.lowercase()
                }
            }
        }

        return result
    }

    private fun convertLatinHomoglyphsToCyrillic(token: String): String {
        val sb = StringBuilder()
        for (ch in token) {
            val mapped = when (ch) {
                'a' -> 'а'
                'A' -> 'А'
                'b' -> 'ь'
                'B' -> 'В'
                'c' -> 'с'
                'C' -> 'С'
                'e' -> 'е'
                'E' -> 'Е'
                'h' -> 'н'
                'H' -> 'Н'
                'k' -> 'к'
                'K' -> 'К'
                'm' -> 'м'
                'M' -> 'М'
                'o' -> 'о'
                'O' -> 'О'
                'p' -> 'р'
                'P' -> 'Р'
                'r' -> 'р'
                't' -> 'т'
                'T' -> 'Т'
                'u' -> 'и'
                'x' -> 'х'
                'X' -> 'Х'
                'y' -> 'у'
                'Y' -> 'У'
                'n' -> 'п'
                'ñ' -> 'й'
                else -> ch
            }
            sb.append(mapped)
        }
        return sb.toString()
    }
}
