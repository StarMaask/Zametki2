package com.example.domain.model

enum class PageFormat(
    val title: String,
    val subtitle: String
) {
    BOOK(
        title = "Книга",
        subtitle = "Тёплая книжная бумага, переплёт и книжная верстка"
    ),
    RULED(
        title = "В линейку",
        subtitle = "Тетрадь в строчку с красным полем слева"
    ),
    GRID(
        title = "В клеточку",
        subtitle = "Классическая тетрадь в клетку с полями"
    ),
    KRAFT(
        title = "Крафт",
        subtitle = "Фактурная крафт-бумага с винтажным зажимом"
    ),
    VINTAGE(
        title = "Пергамент",
        subtitle = "Состаренный манускрипт с обожжёнными краями и виньетками"
    ),
    MIDNIGHT(
        title = "Грифель",
        subtitle = "Тёмная матовая доска для письма белым мелом"
    ),
    BLUEPRINT(
        title = "Чертёж",
        subtitle = "Инженерная миллиметровка синего цвета"
    ),
    BLANK(
        title = "Чистый лист",
        subtitle = "Гладкая страница с индивидуальным цветом"
    )
}
