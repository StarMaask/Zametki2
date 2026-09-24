package com.example.domain.model

enum class NoteFontFamily(
    val id: String,
    val title: String,
    val subtitle: String,
    val isHandwriting: Boolean = false
) {
    DEFAULT("DEFAULT", "Стандартный", "Системный шрифт sans-serif"),
    HANDWRITING_CAVEAT("HANDWRITING_CAVEAT", "Рукописный (Caveat)", "Живой плавный почерк", true),
    HANDWRITING_MARCK("HANDWRITING_MARCK", "Каллиграфия (Marck Script)", "Изящный прописной курсив", true),
    SERIF_PLAYFAIR("SERIF_PLAYFAIR", "Книжный (Playfair)", "Классический с засечками"),
    MONOSPACE("MONOSPACE", "Печатная машинка", "Моноширинный Roboto Mono"),
    CUSTOM_DIGITIZED("CUSTOM_DIGITIZED", "Мой оцифрованный шрифт", "Собственный файл или оцифровка почерка", true);

    companion object {
        fun fromId(id: String?): NoteFontFamily =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
