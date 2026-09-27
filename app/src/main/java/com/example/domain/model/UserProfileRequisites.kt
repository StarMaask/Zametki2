package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Requisites of the author / applicant for official paperwork (ГОСТ Р 7.0.97-2016).
 * Used for 1-click auto-filling the "От кого:" header block and bottom signature line.
 */
@Serializable
data class UserProfileRequisites(
    val fullName: String = "",              // e.g. "Иванов Иван Иванович"
    val fullNameGenitive: String = "",      // e.g. "Иванова Ивана Ивановича" (кого/от кого)
    val shortName: String = "",             // e.g. "Иванов И.И." (для подписи)
    val position: String = "",              // e.g. "Ведущий инженер", "Студент гр. ИВТ-41"
    val positionGenitive: String = "",      // e.g. "ведущего инженера", "студента гр. ИВТ-41"
    val organization: String = "",          // e.g. "ООО «Технологии Будущего»"
    val department: String = "",            // e.g. "Отдел мобильной разработки"
    val phone: String = "",                 // e.g. "+7 (999) 000-00-00"
    val email: String = "",                 // e.g. "ivanov@company.ru"
    val address: String = "",               // e.g. "г. Москва, ул. Тверская, д. 1"
    val passport: String = ""               // e.g. "паспорт РФ 4510 № 123456"
) {
    val isConfigured: Boolean
        get() = fullName.isNotBlank() || shortName.isNotBlank() || position.isNotBlank()

    /**
     * Builds formatted lines for the official Russian document "От кого:" header block.
     */
    fun buildHeaderFromLines(): List<String> {
        val lines = mutableListOf<String>()
        val pos = positionGenitive.ifBlank { position }
        if (pos.isNotBlank()) {
            lines.add("От кого: $pos")
        } else {
            lines.add("От кого:")
        }

        val name = fullNameGenitive.ifBlank { fullName }
        if (name.isNotBlank()) {
            lines.add(name)
        }

        if (organization.isNotBlank() || department.isNotBlank()) {
            val orgPart = buildString {
                if (department.isNotBlank()) append(department)
                if (department.isNotBlank() && organization.isNotBlank()) append(", ")
                if (organization.isNotBlank()) append(organization)
            }
            lines.add(orgPart)
        }

        if (address.isNotBlank()) {
            lines.add("прож. по адресу: $address")
        }

        if (passport.isNotBlank()) {
            lines.add("паспортные данные: $passport")
        }

        if (phone.isNotBlank()) {
            lines.add("тел.: $phone")
        }

        if (email.isNotBlank()) {
            lines.add("email: $email")
        }

        return lines
    }

    /**
     * Builds the official signature line, e.g. "Подпись: ____________ / Иванов И.И. /"
     */
    fun buildSignatureLine(): String {
        val displayShort = shortName.ifBlank {
            // Auto-compute short name from full name if possible
            autoShortName(fullName)
        }
        return if (displayShort.isNotBlank()) {
            "Подпись: ____________ / $displayShort /"
        } else {
            "Подпись: ____________ / ____________ /"
        }
    }

    companion object {
        fun autoGenitiveName(fullName: String): String {
            val parts = fullName.trim().split(Regex("\\s+"))
            if (parts.isEmpty()) return fullName
            // Basic Russian surname/name inflection helpers
            val inflected = parts.mapIndexed { idx, part ->
                when (idx) {
                    0 -> inflectSurname(part)
                    1 -> inflectGivenName(part)
                    2 -> inflectPatronymic(part)
                    else -> part
                }
            }
            return inflected.joinToString(" ")
        }

        fun autoShortName(fullName: String): String {
            val parts = fullName.trim().split(Regex("\\s+"))
            if (parts.size >= 3) {
                return "${parts[0]} ${parts[1].first().uppercase()}." +
                        "${parts[2].first().uppercase()}."
            } else if (parts.size == 2) {
                return "${parts[0]} ${parts[1].first().uppercase()}."
            }
            return fullName
        }

        private fun inflectSurname(surname: String): String {
            return when {
                surname.endsWith("ов") || surname.endsWith("ев") || surname.endsWith("ин") || surname.endsWith("ын") -> surname + "а"
                surname.endsWith("ова") || surname.endsWith("ева") || surname.endsWith("ина") || surname.endsWith("ына") -> surname.dropLast(1) + "ой"
                surname.endsWith("ий") || surname.endsWith("ый") -> surname.dropLast(2) + "ого"
                surname.endsWith("ая") -> surname.dropLast(2) + "ой"
                else -> surname
            }
        }

        private fun inflectGivenName(name: String): String {
            return when {
                name.endsWith("й") -> name.dropLast(1) + "я"
                name.endsWith("ь") -> name.dropLast(1) + "я"
                name.endsWith("а") -> name.dropLast(1) + "ы"
                name.endsWith("я") -> name.dropLast(1) + "и"
                name.isNotEmpty() && name.last() in "бвгджзклмнпрстфхцчшщ" -> name + "а"
                else -> name
            }
        }

        private fun inflectPatronymic(patronymic: String): String {
            return when {
                patronymic.endsWith("ич") -> patronymic + "а"
                patronymic.endsWith("на") -> patronymic.dropLast(1) + "ны"
                else -> patronymic
            }
        }
    }
}
