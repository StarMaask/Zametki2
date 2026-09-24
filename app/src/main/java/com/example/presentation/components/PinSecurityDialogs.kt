package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun PinVerifyDialog(
    correctPin: String,
    onSuccess: () -> Unit,
    onDismissRequest: () -> Unit,
    onBiometricClick: (() -> Unit)? = null
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Заметка защищена",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isError) "Неверный PIN-код, попробуйте снова" else "Введите 4-значный PIN-код для доступа",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4 PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isError -> MaterialTheme.colorScheme.error
                                        isFilled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Custom Numeric Keypad
                PinKeypad(
                    onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val nextPin = enteredPin + digit
                            enteredPin = nextPin
                            isError = false
                            if (nextPin.length == 4) {
                                if (nextPin == correctPin) {
                                    onSuccess()
                                } else {
                                    isError = true
                                    enteredPin = ""
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            isError = false
                        }
                    },
                    onBiometricClick = onBiometricClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismissRequest) {
                    Text("Отмена")
                }
            }
        }
    }
}

@Composable
fun PinSetupDialog(
    onPinConfigured: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Enter, 2: Confirm
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (step == 1) "Задать PIN-код" else "Повторите PIN-код",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = errorMessage ?: if (step == 1) "Придумайте 4 цифры для защиты заметок" else "Введите те же 4 цифры для подтверждения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                val currentInput = if (step == 1) firstPin else confirmPin

                // 4 Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < currentInput.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        errorMessage != null -> MaterialTheme.colorScheme.error
                                        isFilled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                PinKeypad(
                    onDigitClick = { digit ->
                        if (step == 1) {
                            if (firstPin.length < 4) {
                                val next = firstPin + digit
                                firstPin = next
                                errorMessage = null
                                if (next.length == 4) {
                                    step = 2
                                }
                            }
                        } else {
                            if (confirmPin.length < 4) {
                                val next = confirmPin + digit
                                confirmPin = next
                                errorMessage = null
                                if (next.length == 4) {
                                    if (next == firstPin) {
                                        onPinConfigured(firstPin)
                                    } else {
                                        errorMessage = "PIN-коды не совпадают. Попробуйте снова."
                                        confirmPin = ""
                                        step = 1
                                        firstPin = ""
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        if (step == 1 && firstPin.isNotEmpty()) {
                            firstPin = firstPin.dropLast(1)
                            errorMessage = null
                        } else if (step == 2 && confirmPin.isNotEmpty()) {
                            confirmPin = confirmPin.dropLast(1)
                            errorMessage = null
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismissRequest) {
                    Text("Отмена")
                }
            }
        }
    }
}

@Composable
private fun PinKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onBiometricClick: (() -> Unit)? = null
) {
    val digits = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "del")
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in digits) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (item in row) {
                    when (item) {
                        "bio" -> {
                            if (onBiometricClick != null) {
                                IconButton(
                                    onClick = onBiometricClick,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = "Биометрия",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(56.dp))
                            }
                        }
                        "del" -> {
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Backspace,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        else -> {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable { onDigitClick(item) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
