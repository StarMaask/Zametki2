package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Дизайн-токены приложения заметок:
 * - Скругления (12, 16, 24 dp)
 * - Отступы по сетке 8dp
 * - Тени и возвышения (Elevation)
 * - Спецификации длительности и кривых анимаций
 */
/**
 * Дизайн-токены приложения заметок:
 * - Скругления (Corner Radii: xs, sm, md, lg, xl, full)
 * - Отступы по сетке 8dp (Spacing Grid)
 * - Настройки теней и тонального возвышения (M3 Tonal & Shadow Elevation)
 * - Спецификации длительности и кривых анимаций (Easing & Durations)
 */
object NoteDimens {
    // 1. Сетка отступов (8-point grid)
    val spacingXSmall = 4.dp     // Внутренние бейджи, компактные иконки
    val spacingSmall = 8.dp      // Зазор между чипами, отступ внутри списков
    val spacingMediumSmall = 12.dp // Расстояние между тегами и датой в карточке
    val spacingMedium = 16.dp    // Стандартный экранный padding, отступы карточек
    val spacingLarge = 24.dp     // Разделители секций, отступы диалогов
    val spacingXLarge = 32.dp    // Отступы заголовков экрана
    val spacingHero = 48.dp      // Пространство для пустых состояний (empty states)

    // 2. Скругления (Radius tokens)
    val radiusXS = 4.dp          // Индикаторы, мелкие бейджи
    val radiusSmall = 8.dp       // Всплывающие подсказки (Tooltips), снэкбары
    val radiusMedium = 12.dp     // Чипы (Chips), поля ввода (TextFields), кнопки действий
    val radiusCard = 16.dp       // Карточки заметок (NoteCard), всплывающие меню
    val radiusLarge = 24.dp      // Модальные диалоги, BottomSheet, карточки-баннеры
    val radiusFAB = 16.dp        // M3 FAB форма (Squircle/Rounded)
    val radiusFull = 100.dp      // Pill-формы (поисковая строка, аватарки, теги фильтров)

    // Формы (Shapes)
    val shapeSmall = RoundedCornerShape(radiusSmall)
    val shapeMedium = RoundedCornerShape(radiusMedium)
    val shapeCard = RoundedCornerShape(radiusCard)
    val shapeLarge = RoundedCornerShape(radiusLarge)
    val shapeFAB = RoundedCornerShape(radiusFAB)
    val shapePill = RoundedCornerShape(radiusFull)

    // 3. Возвышения (Elevation & Tonal Elevation по гайдлайнам Material 3)
    val elevationLevel0 = 0.dp   // Фон, базовый слой
    val elevationLevel1 = 1.dp   // Карточки в покое (ElevatedCard)
    val elevationLevel2 = 3.dp   // TopAppBar при прокрутке
    val elevationLevel3 = 6.dp   // Карточка при удержании/перетаскивании, FAB в покое
    val elevationLevel4 = 8.dp   // FAB при нажатии
    val elevationLevel5 = 12.dp  // Модальные окна, BottomSheet, диалоги

    val elevationNone = elevationLevel0
    val elevationLow = elevationLevel1
    val elevationMedium = elevationLevel3
    val elevationHigh = elevationLevel5

    // Традиционные тени (Shadow elevation)
    val shadowCardRest = 2.dp
    val shadowCardHover = 6.dp
    val shadowFAB = 6.dp
    val shadowBottomSheet = 16.dp
}

/**
 * Спецификации анимаций интерфейса с точным временем и кривыми (Material 3 Motion System)
 */
object NoteAnimationSpecs {
    // Длительности (ms)
    const val DURATION_FAST = 150             // Микро-взаимодействия (чекбокс, кнопка лайка/закрепления)
    const val DURATION_MEDIUM = 250           // Раскрытие меню, появление фильтров
    const val DURATION_NORMAL = 300           // FAB morph, появление карточек, BottomSheet expand
    const val DURATION_SHARED_ELEMENT = 350   // Переход со списка заметок в экран редактора
    const val DURATION_STAGGER_INTERVAL = 35  // Задержка между каскадным появлением карточек в сетке
    const val DURATION_THEME_CROSSFADE = 400  // Плавное переключение темы оформления

    // Кривые Безье (Easing)
    // 1. Emphasized (Акцентная: быстрое начало, плавное нелинейное торможение для входов/выходов)
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    // 2. Emphasized Accelerate (для исчезновения элементов)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    // 3. Emphasized Decelerate (для плавного входа элементов на экран)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    // 4. Standard (FastOutSlowIn для симметричных трансформаций)
    val StandardEasing: Easing = FastOutSlowInEasing
}
