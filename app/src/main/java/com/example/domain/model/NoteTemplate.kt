package com.example.domain.model

enum class NoteTemplate(
    val title: String,
    val description: String,
    val defaultTitle: String,
    val checklistItems: List<String>,
    val defaultFolder: String?,
    val defaultTags: List<String>,
    val colorHex: String
) {
    BLANK(
        title = "Обычная заметка",
        description = "Чистый лист для свободных записей",
        defaultTitle = "",
        checklistItems = emptyList(),
        defaultFolder = null,
        defaultTags = emptyList(),
        colorHex = "#FFFFFF"
    ),
    SHOPPING_LIST(
        title = "Список покупок",
        description = "Чек-лист продуктов и необходимых вещей",
        defaultTitle = "Покупки на неделю",
        checklistItems = listOf(
            "Молоко и сыр",
            "Свежий хлеб",
            "Овощи и фрукты",
            "Яйца",
            "Чай / Кофе"
        ),
        defaultFolder = "Покупки",
        defaultTags = listOf("покупки"),
        colorHex = "#FEF3C7" // Теплый желтый
    ),
    DAILY_PLAN(
        title = "План на день",
        description = "Расписание ключевых задач и фокуса",
        defaultTitle = "План на день",
        checklistItems = listOf(
            "Главная цель дня",
            "Утренняя разминка / завтрак",
            "Рабочие задачи и созвоны",
            "Личные дела",
            "Итоги дня и отдых"
        ),
        defaultFolder = "Планы",
        defaultTags = listOf("план", "фокус"),
        colorHex = "#E0E7FF" // Индиго
    ),
    MEETING_NOTES(
        title = "Заметка встречи",
        description = "Повестка, участники и договоренности",
        defaultTitle = "Встреча: обсуждение проекта",
        checklistItems = listOf(
            "Зафиксировать повестку и цели",
            "Записать ключевые решения",
            "Определить ответственных и дедлайны"
        ),
        defaultFolder = "Работа",
        defaultTags = listOf("встреча", "работа"),
        colorHex = "#D1FAE5" // Нежно-зеленый
    ),
    PROJECT_IDEA(
        title = "Идея проекта",
        description = "Концепт, шаги реализации и ресурсы",
        defaultTitle = "Идея: новый проект",
        checklistItems = listOf(
            "Сформулировать ценность и суть идеи",
            "Исследовать аналоги",
            "Собрать MVP план",
            "Первые практические шаги"
        ),
        defaultFolder = "Идеи",
        defaultTags = listOf("идея", "проект"),
        colorHex = "#FCE7F3" // Розовый
    )
}
