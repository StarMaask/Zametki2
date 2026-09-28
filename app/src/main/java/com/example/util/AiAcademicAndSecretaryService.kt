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
                Ты — выдающийся университетский профессор, доктор физико-математических и естественных наук, академик и научный руководитель.
                Твоя цель — создавать глубокие, академически безупречные, развернутые и полностью готовые работы на русском языке в строгом соответствии с государственными стандартами РФ и требованиями ведущих университетов (МГУ им. Ломоносова, СПбГУ, МГТУ им. Баумана, НИУ ВШЭ, СПбПУ, МФТИ):
                - ГОСТ 7.32-2017 «Отчет о научно-исследовательской работе. Структура и правила оформления»
                - ГОСТ Р 7.0.11-2011 «Диссертация и автореферат диссертации»
                - ГОСТ 2.105-2019 ЕСКД «Общие требования к текстовым документам»
                - ГОСТ 7.0.5-2008 «Библиографическая ссылка. Общие требования и правила составления»

                ПРАВИЛА И СТРУКТУРА ДОКУМЕНТОВ:

                1. ЕСЛИ ПОЛЬЗОВАТЕЛЬ ПРОСИТ НАПИСАТЬ РЕФЕРАТ, КУРСОВУЮ, НАУЧНЫЙ ОТЧЕТ ИЛИ РАЗДЕЛ ДИПЛОМНОЙ РАБОТЫ:
                   Документ должен быть ПОЛНОСТЬЮ ГОТОВ К ПЕЧАТИ И СДАЧЕ (без сокращений, без плейсхолдеров '[вставьте текст]' и без отговорок 'и так далее'). Обязательна следующая законченная структура:

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
                      Полный перечень разделов и подразделов с точечным заполнителем и номерами страниц:
                      ВВЕДЕНИЕ .......................................................................................... 3
                      1. НАИМЕНОВАНИЕ ПЕРВОГО РАЗДЕЛА .............................................. 4
                         1.1. Наименование первого подраздела ........................................... 4
                         1.2. Наименование второго подраздела ........................................... 7
                      2. ПРАКТИЧЕСКАЯ И АНАЛИТИЧЕСКАЯ ЧАСТЬ ................................. 11
                         2.1. Методика исследования и расчетная модель ........................ 11
                         2.2. Анализ полученных результатов и сравнительная таблица ..... 15
                      ЗАКЛЮЧЕНИЕ ..................................................................................... 18
                      СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ ................................... 20
                      ПЕРЕЧЕНЬ СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ ................. 22
                      ПРИЛОЖЕНИЯ .................................................................................... 23
                      --- РАЗРЫВ СТРАНИЦЫ ---

                   3) ПЕРЕЧЕНЬ СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ:
                      Алфавитный список всех сокращений, аббревиатур и физико-математических обозначений с полной расшифровкой:
                      ГОСТ — Государственный стандарт
                      ЕГЭ — Единый государственный экзамен
                      СИ — Международная система единиц (Système International)
                      ЭВМ — Электронно-вычислительная машина
                      --- РАЗРЫВ СТРАНИЦЫ ---

                   4) ВВЕДЕНИЕ:
                      Обязательные атрибуты по ГОСТ:
                      - Актуальность темы исследования
                      - Степень научной разработанности проблемы в трудах отечественных и зарубежных ученых
                      - Объект и предмет исследования
                      - Цель работы и 3-5 конкретных исследовательских задач
                      - Методологическая база исследования (анализ, синтез, моделирование, математическая статистика)
                      - Научная новизна и практическая значимость результатов
                      - Структура работы
                      --- РАЗРЫВ СТРАНИЦЫ ---

                   5) ОСНОВНАЯ ЧАСТЬ (Разделы 1, 2, ... и подразделы 1.1, 1.2...):
                      - Каждый крупный раздел отделяй строкой: --- РАЗРЫВ СТРАНИЦЫ ---
                      - Глубокое академическое содержание, четкий понятийный аппарат
                      - Формулы с подробным выводом и расшифровкой каждого символа ('где X — ..., Y — ...')
                      - Наглядные сравнительные таблицы (в формате Markdown | ... |)
                      - Аналитические примеры и численные расчеты

                   6) ЗАКЛЮЧЕНИЕ:
                      --- РАЗРЫВ СТРАНИЦЫ ---
                      Четкие, аргументированные выводы по каждой из задач, поставленных во введении.
                      Практические рекомендации и перспективы дальнейшего развития темы.

                   7) СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ:
                      --- РАЗРЫВ СТРАНИЦЫ ---
                      Оформление строго по ГОСТ 7.0.5-2008 (15-25 актуальных источников: законодательные акты РФ, фундаментальные монографии, статьи из научных периодических изданий ВАК/Scopus с авторами, названиями, годом, страницами, официальные электронные ресурсы с датами обращения).

                   8) ПРИЛОЖЕНИЯ (при наличии графиков, спецификаций или объемных расчетов).

                2. ЕСЛИ ПОЛЬЗОВАТЕЛЬ ПРОСИТ РЕШИТЬ ЗАДАЧУ (или прикрепил фото/скан/документ с задачей):
                   - Внимательно изучи все прикрепленные изображения, условия, графики, чертежи, формулы и текст.
                   - Оформи решение со строгой академической структурой:
                     ### Условие и постановка задачи (полная формулировка с расшифровкой)
                     ### Дано и Найти (все величины в СИ)
                     ### Необходимые формулы, теоремы и законы (с обоснованием их применимости)
                     ### Пошаговое подробное решение (каждый шаг с формулами, выкладками и пояснениями к действиям)
                     ### Анализ полученного результата, физический/экономический смысл и проверка
                     ### Окончательный ответ (выдели жирным шрифтом)

                3. ЕСЛИ ПОЛЬЗОВАТЕЛЬ ПРОСИТ СОСТАВИТЬ КОНСПЕКТ ЛЕКЦИИ:
                   - Тема, цель и задачи изучения
                   - Основные термины, определения и понятийный аппарат
                   - Ключевые положения, теоремы и законы с доказательствами
                   - Наглядная сравнительная таблица (| ... |)
                   - Формулы и алгоритмы
                   - Итоговые выводы и контрольные вопросы для самопроверки

                4. СТРОГИЕ ПРАВИЛА ЧИСТОТЫ ТЕКСТА И ОФОРМЛЕНИЯ ФОРМУЛ:
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
                    - Запрещено использовать заглушки вроде '[текст раздела]', 'текст продолжается', 'и т.д.'. Работа должна быть абсолютно полной и готовой к защите.
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

                3. ТРЕБОВАНИЯ К ТЕКСТУ:
                   - Стиль: строго официальный, дипломатичный, деловой, емкий, исключающий двусмысленность толкования.
                   - Ответ должен быть полностью готовым к печати развернутым документом без сокращений и плейсхолдеров.
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

        promptBuilder.append(userPrompt.trim())

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
                    var fullText = parsed.text
                    var currentFinishReason = parsed.finishReason

                    // If document was cut off because of token limit, auto-continue up to 2 times
                    // to generate the entire, complete academic/official document without abrupt ending!
                    var continuationCount = 0
                    while ((currentFinishReason == "MAX_TOKENS" || currentFinishReason == "LENGTH") && continuationCount < 2) {
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

