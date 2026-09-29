package com.example.util

import android.content.Context
import com.example.BuildConfig
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.AiAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiAcademicAndSecretaryService {

    private val REASONING_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-3.1-pro-preview",
        "gemini-flash-latest",
        "gemini-3.1-flash-lite-preview"
    )

    enum class AssistantRole(
        val title: String,
        val subtitle: String,
        val systemPrompt: String
    ) {
        PROFESSOR(
            title = "Профессор всех наук",
            subtitle = "Математика, физика, химия, конспекты, рефераты и дипломные",
            systemPrompt = """
                Ты — выдающийся университетский профессор, доктор наук, академик и председатель государственной аттестационной комиссии ведущих университетов (МГУ им. Ломоносова, СПбГУ, МГТУ им. Баумана, НИУ ВШЭ, СПбПУ, МФТИ).
                Твоя цель — создавать фундаментальные, глубокие, академически безупречные, максимально развернутые и полностью готовые работы на русском языке в строгом соответствии с государственными стандартами РФ:
                - ГОСТ 7.32-2017 «Отчет о научно-исследовательской работе. Структура и правила оформления»
                - ГОСТ Р 7.0.11-2011 «Диссертация и автореферат диссертации»
                - ГОСТ 2.105-2019 ЕСКД «Общие требования к текстовым документам»
                - ГОСТ 7.0.5-2008 «Библиографическая ссылка. Общие требования и правила составления»

                КАТЕГОРИЧЕСКИЕ ТРЕБОВАНИЯ К ОБЪЕМУ, РАЗВЕРНУТОСТИ И ПРОФЕССИОНАЛИЗМУ:
                1. СТРОЖАЙШИЙ ЗАПРЕТ НА КРАТКИЕ И ПОВЕРХНОСТНЫЕ ОТВЕТЫ:
                   - Любой реферат, курсовая работа или научный отчет должен представлять собой ПОЛНОЦЕННЫЙ, МОНУМЕНТАЛЬНЫЙ И ВЫСОКОПРОФЕССИОНАЛЬНЫЙ ДОКУМЕНТ (эквивалент 15–25 страниц формата А4 при стандартной верстке ГОСТ).
                   - Запрещены краткие конспекты, тезисные выжимки, реферативные отписки по 2-3 предложения на раздел!
                   - Каждый пункт и подраздел (1.1, 1.2, 2.1, 2.2 и т.д.) должен быть написан развернуто, насыщенно и обстоятельно (не менее 5–8 плотных академических абзацев, от 1500 до 3500 символов связного профессионального текста на каждый подраздел).

                2. СТРУКТУРА И СОДЕРЖАНИЕ АКАДЕМИЧЕСКОГО ДОКУМЕНТА (РЕФЕРАТ / КУРСОВАЯ):

                   1) ТИТУЛЬНЫЙ ЛИСТ:
                      МИНИСТЕРСТВО НАУКИ И ВЫСШЕГО ОБРАЗОВАНИЯ РОССИЙСКОЙ ФЕДЕРАЦИИ
                      Федеральное государственное бюджетное образовательное учреждение высшего образования
                      Факультет естественных и инженерных наук
                      Кафедра фундаментальных дисциплин

                      РЕФЕРАТ (или КУРСОВАЯ РАБОТА / НАУЧНЫЙ ДОКЛАД)
                      по дисциплине: «[Наименование дисциплины]»
                      на тему: «[Точная формулировка темы работы заглавными буквами]»

                      Выполнил: студент 3 курса группы ИВТ-301 Иванов И.И.
                      Проверил: научный руководитель, д.т.н., профессор Петров П.П.

                      Москва, 2026
                      --- РАЗРЫВ СТРАНИЦЫ ---

                   2) СОДЕРЖАНИЕ (ОГЛАВЛЕНИЕ):
                      Полный перечень всех разделов и подразделов с точечным заполнителем и реальными номерами страниц по ГОСТ:
                      - Титульный лист занимает стр. 1 (в содержании не пишется).
                      - СОДЕРЖАНИЕ занимает стр. 2.
                      - ВВЕДЕНИЕ начинается на стр. 3.
                      - Номера страниц должны точно отражать реальный объем разделов: если подразделы умещаются на одной странице, у них должен быть ОДИН И ТОТ ЖЕ номер страницы (например: 1.1 — стр. 4, 1.2 — стр. 4; или 2.1 — стр. 7, 2.2 — стр. 7).
                      - Итоговый номер страниц в содержании (Заключение, Список литературы) должен соответствовать реальному объему работы (например, для реферата на 10-15 страниц он не должен превышать 15, ни в коем случае не пиши выдуманные 22+ страницы!).
                      Пример правильной структуры содержания:
                      ВВЕДЕНИЕ .......................................................................................... 3
                      1. ТЕОРЕТИЧЕСКИЕ ОСНОВЫ И ИСТОРИОГРАФИЯ .............................. 4
                         1.1. Понятийный аппарат и генезис научных концепций ................. 4
                         1.2. Анализ теоретических подходов отечественных и зарубежных школ 4
                      2. ПРАКТИЧЕСКАЯ И АНАЛИТИЧЕСКАЯ ЧАСТЬ ................................. 6
                         2.1. Методология исследования и математическая модель ......... 6
                         2.2. Анализ полученных результатов и сравнительная таблица ..... 8
                      ЗАКЛЮЧЕНИЕ ..................................................................................... 11
                      СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ ................................... 13
                      ПЕРЕЧЕНЬ СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ ................. 14
                      ПРИЛОЖЕНИЯ .................................................................................... 15
                      --- РАЗРЫВ СТРАНИЦЫ ---

                   3) ПЕРЕЧЕНЬ СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ:
                      Алфавитный список всех сокращений, аббревиатур и физико-математических обозначений с полной расшифровкой:
                      ГОСТ — Государственный стандарт
                      ЕГЭ — Единый государственный экзамен
                      СИ — Международная система единиц (Système International)
                      ЭВМ — Электронно-вычислительная машина
                      --- РАЗРЫВ СТРАНИЦЫ ---

                   4) ВВЕДЕНИЕ (ОБЪЕМНОЕ И ВСЕОБЪЕМЛЮЩЕЕ — не менее 1.5–2 страниц):
                      Обязательные атрибуты по ГОСТ:
                      - Актуальность темы исследования (глубокое обоснование с опорой на современное состояние науки, техники и общества)
                      - Степень научной разработанности проблемы в фундаментальных трудах отечественных и зарубежных ученых (с указанием конкретных фамилий исследователей и их ключевых открытий)
                      - Объект исследования (процесс или явление, порождающее проблемную ситуацию)
                      - Предмет исследования (конкретные свойства, закономерности и характеристики объекта)
                      - Цель работы и 4–6 конкретных взаимосвязанных исследовательских задач
                      - Методологическая и теоретическая база исследования (методы научного познания: системный анализ, дедукция, математическое и имитационное моделирование, сравнительно-исторический метод)
                      - Научная новизна и теоретическая значимость результатов
                      - Практическая значимость и прикладная ценность
                      - Апробация результатов и структура работы
                      --- РАЗРЫВ СТРАНИЦЫ ---

                   5) ОСНОВНАЯ ЧАСТЬ (Разделы 1, 2, ... и подразделы 1.1, 1.2...):
                      - Каждый крупный раздел отделяй строкой: --- РАЗРЫВ СТРАНИЦЫ ---
                      - Глубочайшее научное содержание:
                        * Разверни сущность каждого понятия, классификации, типологии и механизмы.
                        * Сопоставь альтернативные научные концепции и точки зрения ученых.
                        * Приводи конкретные математические, физические или экономические формулы с полным выводом и расшифровкой каждого символа в единицах СИ ('где X — ..., Y — ...').
                        * Обязательно включай наглядные сравнительные аналитические таблицы (в формате Markdown | ... |) с подробными многофакторными критериями и параметрами (не менее 4–6 колонок и подробных строк).
                        * Численные расчеты, алгоритмы, технологические схемы, практические кейсы и эмпирические данные.
                        * Графические материалы, схемы, архитектуры и диаграммы: оформляй в специальном формате с описанием блоков и этапов через стрелку '->':
                          [Рисунок 1 — Структурная схема системы: Модуль сбора данных -> Модуль обработки и фильтрации -> Аналитический модуль -> База данных и отчеты]
                          или в приложениях:
                          [Рисунок 2 — Блок-схема алгоритма: Инициализация -> Проверка условий -> Итерационные вычисления -> Вывод результата]
                        * Обязательные промежуточные аналитические выводы в конце каждого подраздела («Таким образом, подводя итог анализу...»).

                   6) ЗАКЛЮЧЕНИЕ (РАЗВЕРНУТОЕ — не менее 1.5–2 страниц):
                      --- РАЗРЫВ СТРАНИЦЫ ---
                      - Обстоятельные, аргументированные выводы по КАЖДОЙ из задач, поставленных во введении.
                      - Оценка достижения цели исследования.
                      - Практические научно обоснованные рекомендации по внедрению результатов.
                      - Перспективы дальнейшей научной разработки темы.

                   7) СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ:
                      --- РАЗРЫВ СТРАНИЦЫ ---
                      Оформление строго по ГОСТ 7.0.5-2008 (не менее 15–25 солидных академических источников: законодательные акты РФ, государственные стандарты, фундаментальные научные монографии, статьи из журналов ВАК и Scopus последних лет с авторами, названиями, годом, номерами страниц, электронные научные ресурсы с датами обращения).

                   8) ПРИЛОЖЕНИЯ (при наличии графиков, спецификаций, объемных таблиц или графических материалов):
                      Каждое приложение оформляй как 'ПРИЛОЖЕНИЕ А' или 'ПРИЛОЖЕНИЕ Б' с заголовком и графическим материалом / таблицей:
                      ПРИЛОЖЕНИЕ А. Архитектурная модель
                      [Рисунок А.1 — Архитектурная диаграмма сервиса: Клиентский интерфейс -> API Gateway -> Микросервисы -> СУБД]

                3. ЕСЛИ ПОЛЬЗОВАТЕЛЬ ПРОСИТ РЕШИТЬ ЗАДАЧУ (или прикрепил фото/скан/документ с задачей):
                   - Внимательно изучи все прикрепленные изображения, условия, графики, чертежи, формулы и текст.
                   - Оформи решение со строгой академической структурой:
                     ### Условие и постановка задачи (полная формулировка с расшифровкой)
                     ### Дано и Найти (все величины в СИ)
                     ### Необходимые формулы, теоремы и законы (с обоснованием их применимости)
                     ### Пошаговое подробное решение (каждый шаг с формулами, выкладками и пояснениями к действиям)
                     ### Анализ полученного результата, физический/экономический смысл и проверка
                     ### Окончательный ответ (выдели жирным шрифтом)

                4. ЕСЛИ ПОЛЬЗОВАТЕЛЬ ПРОСИТ СОСТАВИТЬ КОНСПЕКТ ЛЕКЦИИ:
                   - Тема, цель и задачи изучения
                   - Основные термины, определения и понятийный аппарат
                   - Ключевые положения, теоремы и законы с доказательствами
                   - Наглядная сравнительная таблица (| ... |)
                   - Формулы и алгоритмы
                   - Итоговые выводы и контрольные вопросы для самопроверки

                5. СТРОГИЕ ПРАВИЛА ЧИСТОТЫ ТЕКСТА И ОФОРМЛЕНИЯ ФОРМУЛ:
                   - Не используй хаотичные или избыточные знаки разметки.
                   - ЗАПРЕЩЕНО выводить сырой синтаксис LaTeX (такой как \frac{...}{...}, \cdot, \times, \approx, \sqrt{...}, \text{...}, $$, \[, \]).
                   - Все формулы, выкладки и расчеты записывай стандартными, наглядными и чистыми математическими символами Unicode:
                     * Умножение: знак точки по центру '·' (или '×')
                     * Дробь: со скобками через косую черту '/', например: '(a + b) / (c + d)' или 'Q / (c · m)'
                     * Степени и индексы: стандартные надстрочные и подстрочные символы Unicode: ², ³, ⁿ, ₀, ₁, ₂, ᵢ (или '^2', '_1')
                     * Корень: '√(x)' или '∛(x)'
                     * Греческие буквы: α, β, γ, δ, Δ, ε, η, θ, λ, μ, π, ρ, σ, Σ, τ, φ, ω, Ω
                     * Знаки сравнения и отношений: ≤, ≥, ≈, ≠, ±, ∞, ∑, ∫, ∈
                     * После каждой формулы оформляй пояснения 'где ...' согласно ГОСТ: 'где E — кинетическая энергия, Дж; m — масса тела, кг; v — скорость, м/с'.
                   - Форматируй заголовки и разделы аккуратно, чтобы документ выглядел безупречно.
                   - СТРОЖАЙШИЙ ЗАПРЕТ НА КИТАЙСКИЕ, АЗИАТСКИЕ И ПОСТОРОННИЕ ИЕРОГЛИФЫ: В русскоязычном тексте категорически запрещены любые китайские, японские или корейские иероглифы (такие как 的, 发, 会, 是, 文 и любые другие). Текст должен быть на 100% чистом русском языке без артефактов токенизации!
                   - Запрещено использовать заглушки вроде '[текст раздела]', 'текст продолжается', 'и т.д.'. Работа должна быть абсолютно полной, глубокой и готовой к защите.
            """.trimIndent()
        ),
        SECRETARY(
            title = "Умный секретарь-делопроизводитель",
            subtitle = "Идеальное составление документов по ГОСТ, договоров, служебных записок",
            systemPrompt = """
                Ты — главный управляющий делами, корпоративный юрист и эксперт по документационному обеспечению управления (делопроизводству) высшей квалификации.
                Ты в совершенстве владеешь требованиями государственного стандарта ГОСТ Р 7.0.97-2016 «Организационно-распорядительная документация» и законодательством РФ (ТК РФ, ГК РФ).

                Твоя цель — составлять идеальные, юридически выверенные официальные документы с полным комплектом реквизитов, безупречными формулировками и готовым чистовиком.

                ПРАВИЛА ОФОРМЛЕНИЯ ПО ГОСТ Р 7.0.97-2016:

                1. ОБЯЗАТЕЛЬНЫЕ РЕКВИЗИТЫ ДОКУМЕНТА:
                   - Адресат и Заявитель (блок «Кому:» и «От кого:» в правом верхнем углу с указанием должностей в дательном падеже, организаций и ФИО)
                   - Название вида документа заглавными буквами по центру (например: СЛУЖЕБНАЯ ЗАПИСКА, ЗАЯВЛЕНИЕ, АКТ, ДОГОВОР, ПРЕТЕНЗИЯ, ПРИКАЗ, РАСПОРЯЖЕНИЕ)
                   - Дата документа и регистрационный номер
                   - Место составления или издания документа (например: г. Москва)
                   - Заголовок к тексту (краткое содержание документа, отвечающее на вопрос 'О чем?')
                   - Преамбула (констатирующая часть со ссылками на ТК РФ, ГК РФ, приказы или договорные обязательства)
                   - Основная распорядительная / содержательная часть с четкими, недвусмысленными пунктами
                   - Таблицы спецификаций, расчетов или актов (в формате Markdown | ... |)
                   - Отметка о наличии приложений (с указанием наименования, количества листов и экземпляров)
                   - Подписи уполномоченных лиц с указанием должности, личной подписи и расшифровки (инициалы, фамилия)
                   - Визы согласования и отметки об исполнителе

                2. АНАЛИЗ ПРИКРЕПЛЕННЫХ ДОКУМЕНТОВ И СКАНОВ:
                   - Если пользователь прикрепил договор, скан заявления, акт или накладную:
                     * Проверь юридическую чистоту, соответствие ГОСТ, наличие всех обязательных реквизитов.
                     * Исправь ошибки и выдай полный, готовый чистовик документа.

                3. ТРЕБОВАНИЯ К ТЕКСТУ, ОБЪЕМУ И ПОЛНОТЕ:
                   - Стиль: строго официальный, дипломатичный, деловой, емкий, юридически безупречный, исключающий двусмысленность толкования.
                   - Ответ должен быть исчерпывающим, максимально подробным, полностью готовым к печати развернутым документом без сокращений, пропусков и плейсхолдеров.
                   - Расписывай все разделы, условия, права, обязанности и ответственность сторон, перечни приложений и реквизиты досконально.
                   - Категорически запрещены любые китайские, восточные иероглифы и случайные посторонние символы (такие как 的, 发). Весь текст составляй строго на чистом литературном русском языке.
            """.trimIndent()
        )
    }

    data class DialogueMessage(
        val role: String, // "user" or "model"
        val text: String,
        val attachments: List<AiAttachment> = emptyList()
    )

    suspend fun askAssistant(
        context: Context,
        role: AssistantRole,
        userPrompt: String,
        contextText: String? = null,
        attachments: List<AiAttachment> = emptyList(),
        customApiKey: String? = null
    ): Result<String> {
        return askAssistantDialogue(
            context = context,
            role = role,
            history = emptyList(),
            userPrompt = userPrompt,
            contextText = contextText,
            attachments = attachments,
            customApiKey = customApiKey
        )
    }

    suspend fun askAssistantDialogue(
        context: Context,
        role: AssistantRole,
        history: List<DialogueMessage>,
        userPrompt: String,
        contextText: String? = null,
        attachments: List<AiAttachment> = emptyList(),
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: GeminiOcrService.getEffectiveApiKey(context)

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("API-ключ Gemini не найден. Укажите ключ в настройках или диалоге.")
            )
        }

        val promptBuilder = StringBuilder()

        if (!contextText.isNullOrBlank() && history.isEmpty()) {
            promptBuilder.append("КОНТЕКСТ ТЕКУЩЕГО ДОКУМЕНТА / ЗАМЕТКИ:\n")
            promptBuilder.append("\"\"\"\n")
            promptBuilder.append(contextText)
            promptBuilder.append("\n\"\"\"\n\n")
        }

        // Add text extracted from text-based or OCR attachments
        val textAttachments = attachments.filter { !it.extractedText.isNullOrBlank() }
        if (textAttachments.isNotEmpty()) {
            promptBuilder.append("ПРИКРЕПЛЕННЫЕ МАТЕРИАЛЫ И ДОКУМЕНТЫ (ИЗВЛЕЧЕННЫЙ ТЕКСТ):\n")
            for (att in textAttachments) {
                promptBuilder.append("--- Документ: ${att.name} (${att.typeLabel}) ---\n")
                promptBuilder.append(att.extractedText)
                promptBuilder.append("\n-----------------------------------------------\n\n")
            }
        }

        if (attachments.any { it.isImage || it.isPdf }) {
            promptBuilder.append("К запросу прикреплены файлы изображений/сканов/PDF (переданы во вложении). Тщательно изучи их содержимое.\n\n")
        }

        val cleanUserPrompt = userPrompt.trim()
        promptBuilder.append(cleanUserPrompt)

        val isAcademicWork = cleanUserPrompt.contains("реферат", ignoreCase = true) ||
            cleanUserPrompt.contains("курсов", ignoreCase = true) ||
            cleanUserPrompt.contains("доклад", ignoreCase = true) ||
            cleanUserPrompt.contains("диплом", ignoreCase = true) ||
            cleanUserPrompt.contains("отчет", ignoreCase = true) ||
            cleanUserPrompt.contains("стать", ignoreCase = true) ||
            cleanUserPrompt.contains("диссертаци", ignoreCase = true) ||
            (role == AssistantRole.PROFESSOR && (cleanUserPrompt.contains("напиши", ignoreCase = true) || cleanUserPrompt.contains("тема", ignoreCase = true) || history.isEmpty()))

        if (isAcademicWork) {
            promptBuilder.append("\n\n[МАНДАТ НА МАКСИМАЛЬНЫЙ АКАДЕМИЧЕСКИЙ ОБЪЕМ И ГЛУБИНУ]: Составь монументальный, максимально развернутый, глубокий и высокопрофессиональный документ (эквивалент 15–25 страниц по ГОСТ). Категорически запрещены краткие конспекты, сжатые выжимки и поверхностные тезисы! Детально распиши каждый подраздел (по 5–8 плотных академических абзацев), включи подробнейший обзор научных школ, исследователей, математические/физические выкладки с формулами, развернутые сравнительные таблицы данных, практические кейсы, численные расчеты и детальные выводы по всем задачам.")
        } else {
            promptBuilder.append("\n\n[ТРЕБОВАНИЕ К МАКСИМАЛЬНОЙ ПОДРОБНОСТИ, ТОЧНОСТИ И ДЕТАЛИЗАЦИИ]: Предоставь максимально подробный, развернутый, точный и обстоятельный ответ. Не сокращай и не урезай информацию. Представь все необходимые данные, факты, классификации, пошаговые алгоритмы, формулы, сравнительные таблицы и подробные пояснения в максимально полном объеме без кратких отписок.")
        }

        val latestUserText = promptBuilder.toString()

        var lastError = "Не удалось получить ответ от ИИ"
        for (model in REASONING_MODELS) {
            val result = executeDialogueRequest(model, apiKey, role.systemPrompt, history, latestUserText, attachments)
            if (result.isSuccess) {
                return@withContext result
            }
            lastError = result.exceptionOrNull()?.localizedMessage ?: lastError
        }

        Result.failure(Exception(lastError))
    }

    private fun executeDialogueRequest(
        model: String,
        apiKey: String,
        systemInstruction: String,
        history: List<DialogueMessage>,
        latestUserText: String,
        latestAttachments: List<AiAttachment>
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 60000
                readTimeout = 90000
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })

                val contentsArray = JSONArray()

                // Add past dialogue turns
                for (msg in history) {
                    val contentObj = JSONObject().apply {
                        put("role", if (msg.role == "model") "model" else "user")
                        val parts = JSONArray()
                        parts.put(JSONObject().apply {
                            put("text", msg.text)
                        })
                        for (att in msg.attachments) {
                            if (!att.base64Data.isNullOrBlank()) {
                                parts.put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", att.mimeType)
                                        put("data", att.base64Data)
                                    })
                                })
                            }
                        }
                        put("parts", parts)
                    }
                    contentsArray.put(contentObj)
                }

                // Add the latest user turn
                val latestContentObj = JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray()
                    parts.put(JSONObject().apply {
                        put("text", latestUserText)
                    })
                    for (att in latestAttachments) {
                        if (!att.base64Data.isNullOrBlank()) {
                            parts.put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", att.mimeType)
                                    put("data", att.base64Data)
                                })
                            })
                        }
                    }
                    put("parts", parts)
                }
                contentsArray.put(latestContentObj)

                put("contents", contentsArray)

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("topP", 0.95)
                    put("maxOutputTokens", 8192)
                })
            }

            connection.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsed = parseCandidate(responseText)
                if (parsed != null && parsed.text.isNotBlank()) {
                    var fullText = parsed.text
                    var currentFinishReason = parsed.finishReason

                    // If document was cut off because of token limit, auto-continue up to 5 times
                    // to generate the entire, complete academic/official document without abrupt ending!
                    var continuationCount = 0
                    while ((currentFinishReason.equals("MAX_TOKENS", ignoreCase = true) || currentFinishReason.equals("LENGTH", ignoreCase = true)) && continuationCount < 5) {
                        continuationCount++
                        val continuationResult = executeContinuationRequest(
                            model = model,
                            apiKey = apiKey,
                            systemInstruction = systemInstruction,
                            history = history,
                            initialUserPrompt = latestUserText,
                            generatedSoFar = fullText
                        )
                        if (continuationResult.isSuccess) {
                            val nextPart = continuationResult.getOrThrow()
                            if (nextPart.text.isNotBlank()) {
                                val nextChunk = nextPart.text.trim()
                                fullText = if (fullText.endsWith("\n") || fullText.endsWith(" ")) {
                                    fullText + nextChunk
                                } else {
                                    fullText + "\n\n" + nextChunk
                                }
                                currentFinishReason = nextPart.finishReason
                            } else {
                                break
                            }
                        } else {
                            break
                        }
                    }

                    Result.success(fullText)
                } else {
                    Result.failure(Exception("Сервер ИИ вернул пустой ответ."))
                }
            } else {
                val errorStream = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val errorMessage = GeminiOcrService.parseErrorMessage(errorStream, responseCode)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Сетевая ошибка при обращении к ИИ."))
        } finally {
            connection?.disconnect()
        }
    }

    private fun executeContinuationRequest(
        model: String,
        apiKey: String,
        systemInstruction: String,
        history: List<DialogueMessage>,
        initialUserPrompt: String,
        generatedSoFar: String
    ): Result<ParsedCandidate> {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 60000
                readTimeout = 90000
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })

                val contentsArray = JSONArray()

                for (msg in history) {
                    val contentObj = JSONObject().apply {
                        put("role", if (msg.role == "model") "model" else "user")
                        val parts = JSONArray()
                        parts.put(JSONObject().apply {
                            put("text", msg.text)
                        })
                        put("parts", parts)
                    }
                    contentsArray.put(contentObj)
                }

                // Initial user turn
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", initialUserPrompt) })
                    })
                })

                // Partial model response
                contentsArray.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", generatedSoFar) })
                    })
                })

                // Continuation request
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put(
                                "text",
                                "Текст документа прервался из-за лимита токенов. " +
                                "Продолжай строго с места прерывания. " +
                                "НЕ повторяй уже написанное, НЕ пиши вводных фраз или комментариев. " +
                                "Допиши следующие разделы, выводы, список использованных источников и приложения по ГОСТ в полном объеме до логического завершения."
                            )
                        })
                    })
                })

                put("contents", contentsArray)

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.35)
                    put("topP", 0.95)
                    put("maxOutputTokens", 8192)
                })
            }

            connection.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsed = parseCandidate(responseText)
                if (parsed != null && parsed.text.isNotBlank()) {
                    Result.success(parsed)
                } else {
                    Result.failure(Exception("Пустой ответ при авто-продолжении документа"))
                }
            } else {
                val errorStream = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val errorMessage = GeminiOcrService.parseErrorMessage(errorStream, responseCode)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    data class ParsedCandidate(
        val text: String,
        val finishReason: String
    )

    private fun parseCandidate(responseJson: String): ParsedCandidate? {
        return try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.getJSONObject(0)
            val finishReason = firstCandidate.optString("finishReason", "")
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                sb.append(text)
            }
            val rawText = sb.toString()
            val sanitized = FormulaSanitizer.cleanFormulasAndText(rawText)
            ParsedCandidate(text = sanitized, finishReason = finishReason)
        } catch (_: Exception) {
            null
        }
    }
}

