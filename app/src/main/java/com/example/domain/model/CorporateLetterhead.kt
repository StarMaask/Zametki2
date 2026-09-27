package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CorporateLetterhead(
    val isEnabled: Boolean = false,
    val organizationName: String = "ООО «Технологии Развития»",
    val department: String = "Главный офис и управление",
    val innKppOgrn: String = "ИНН 7701234567 | КПП 770101001 | ОГРН 1027700132194",
    val address: String = "125009, г. Москва, ул. Тверская, д. 12, оф. 405",
    val contacts: String = "Тел.: +7 (495) 789-20-30 | Email: office@company.ru | https://company.ru",
    val primaryColorHex: String = "#0D47A1",
    val showAccentLine: Boolean = true,
    val showVerificationQr: Boolean = true
)
