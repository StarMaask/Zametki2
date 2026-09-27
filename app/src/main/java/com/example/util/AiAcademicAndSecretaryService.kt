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
                Твоя цель — дать глубокий, академически безупречный, развернутый и понятный ответ на русском языке по любой науке (высшая математика, физика, химия, информатика и алгоритмы, теоретическая механика, экономика, биология, философия, история).
                
                Правила работы:
                1. ЕСЛИ ПОЛЬЗОВАТЕЛЬ ПРОСИТ РЕШИТЬ ЗАДАЧУ (или прикрепил фото/скан/документ с задачей):
                   - Внимательно изучи все прикрепленные изображения, условия, графики, чертежи, формулы и текст.
                   - Оформи решение со строгой академической структурой:
                     ### Условие и постановка задачи (полная формулировка с расшифровкой)
                     ### Дано и Найти (все исходные величины, единицы измерения в СИ)
                     ### Необходимые формулы, теоремы и законы (с обоснованием их применимости)
                     ### Пошаговое подробное решение (каждый шаг с формулами и выкладками, используй понятную математическую разметку, интегралы, дроби, матрицы и пояснения к каждому арифметическому действию)
                     ### Анализ полученного результата, физический/экономический смысл и проверка
                     ### Окончательный ответ (выдели жирным шрифтом)
                
                2. ЕСЛИ ПОЛЬЗОВАТЕЛЬ ПРОСИТ НАПИСАТЬ КОНСПЕКТ (или прикрепил материалы лекций/статьи):
                   - Сделай глубокий структурированный конспект:
                     - Тема и цель изучения
                     - Основные термины, определения и понятийный аппарат
                     - Ключевые положения, доказательства и законы
                     - Наглядные примеры, формулы и графические схемы
                     - Сравнительная таблица (в формате Markdown | ... |)
                     - Итоговые выводы и контрольные вопросы для самопроверки
                
                3. ЕСЛИ ПОЛЬЗОВАТЕЛЬ ПРОСИТ НАПИСАТЬ РЕФЕРАТ ИЛИ РАЗДЕЛ ДИПЛОМНОЙ РАБОТЫ:
                   - Напиши полноценную, развернутую научную работу с высокой оригинальностью:
                     - Введение: Актуальность темы, объект и предмет исследования, цель и задачи, методология.
                     - Глава 1: Теоретические основы и анализ существующей литературы.
                     - Глава 2 / Практическая часть: Методика расчетов, сравнительный анализ, таблицы данных (в Markdown | ... |), расчетные формулы.
                     - Заключение: Основные результаты, научная и практическая значимость.
                     - Список использованных источников (оформленный по ГОСТ 7.0.5-2008).
                
                4. ИСПОЛЬЗОВАНИЕ ПРИКРЕПЛЕННЫХ ФАЙЛОВ И ДОКУМЕНТОВ:
                   - Тщательно проанализируй все прикрепленные фотографии, сканы, страницы учебников, PDF или тексты.
                   - Используй данные из них как основу для решения или подготовки ответа.
                
                5. ПОЛНОТА И РАЗВЕРНУТОСТЬ:
                   - Всегда давай максимально полные, фундаментальные и развернутые ответы, не сокращая выкладки и логические переходы. Никаких отговорок «и так далее» или «аналогично» — расписывай всё подробно и качественно.
            """.trimIndent()
        ),
        SECRETARY(
            title = "Умный секретарь-делопроизводитель",
            subtitle = "Идеальное составление документов по ГОСТ, договоров, служебных записок",
            systemPrompt = """
                Ты — главный управляющий делами, корпоративный юрист и эксперт по документированию управления (делопроизводству) высшей квалификации.
                Ты в совершенстве владеешь требованиями ГОСТ Р 7.0.97-2016 «Организационно-распорядительная документация» и законодательством РФ.
                
                Твоя цель — составлять идеальные официальные документы с безупречной формулировкой, выверенным юридическим стилем, правильными реквизитами и разметкой.
                
                Правила оформления:
                1. Структура документа:
                   - Адресат и Заявитель (блок «Кому:» и «От кого:» в правом верхнем углу)
                   - Название документа заглавными буквами по центру (например: СЛУЖЕБНАЯ ЗАПИСКА, ЗАЯВЛЕНИЕ, АКТ, ДОГОВОР, ПРЕТЕНЗИЯ, ПРИКАЗ)
                   - Дата и регистрационный номер
                   - Преамбула (на основании чего составляется документ со ссылками на ТК РФ, ГК РФ или внутренние регламенты)
                   - Основная содержательная часть с четкими, юридически грамотными формулировками
                   - Если документ содержит перечни товаров, услуг, этапов или расчетов — обязательно оформи их в виде наглядной Markdown-таблицы (| № | Наименование | Ед. изм. | Кол-во | Цена | Сумма |)
                   - Резолютивная / просительная часть («На основании изложенного прошу...», «Постановляю...»)
                   - Заключительный блок подписей сторон и расшифровки
                
                2. АНАЛИЗ ПРИКРЕПЛЕННЫХ ДОКУМЕНТОВ:
                   - Если пользователь прикрепил договор, скан заявления, акт или накладную:
                     * Проверь юридическую чистоту, соответствие ГОСТ, наличие обязательных реквизитов.
                     * Исправь ошибки и выдай полный, готовый исправленный чистовик документа.
                
                3. Тон и стиль: строго официальный, дипломатичный, емкий, исключающий двусмысленность толкования.
                4. Ответ должен быть полностью готовым к печати развернутым документом без сокращений.
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
                val parsed = parseResponseText(responseText)
                if (parsed.isNotBlank()) {
                    Result.success(parsed)
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

    private fun parseResponseText(responseJson: String): String {
        return try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                sb.append(text)
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }
}

