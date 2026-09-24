package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MathSymbolBar(
    onInsertSymbol: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val categories = listOf(
        "Операторы" to listOf(
            "±", "×", "÷", "≠", "≈", "≤", "≥", "∞",
            "√", "∛", "∫", "∬", "∑", "∏", "∂", "∇", "°", "‰"
        ),
        "Греческий" to listOf(
            "α", "β", "γ", "δ", "ε", "θ", "λ", "μ", "π", "ρ", "σ", "τ", "φ", "ω",
            "Δ", "Ω", "Σ", "Φ", "Ψ", "Ξ", "Λ"
        ),
        "Степени & Индексы" to listOf(
            "²", "³", "ⁿ", "⁰", "¹", "⁴", "⁵",
            "₀", "₁", "₂", "₃", "₄", "ₙ", "ᵢ",
            "x²", "x³", "x₀", "x₁", "½", "⅓", "¼"
        ),
        "Логика & Множества" to listOf(
            "→", "⇒", "⇔", "←", "↑", "↓",
            "∈", "∉", "⊂", "⊆", "∪", "∩", "∀", "∃", "∅", "¬", "∧", "∨", "≡"
        ),
        "LaTeX" to listOf(
            "$$ $$", "$ $",
            "\\frac{a}{b}", "\\sqrt{x}", "\\int_{a}^{b} f(x) dx",
            "\\sum_{i=1}^{n}", "\\lim_{x \\to 0}", "\\vec{v}",
            "\\alpha", "\\beta", "\\pi", "\\infty",
            "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}"
        )
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            // Category Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories.indices.toList()) { index ->
                        val (title, _) = categories[index]
                        val isSelected = selectedCategoryIndex == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryIndex = index },
                            label = { Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.height(28.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Скрыть панель формул", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Symbols Grid / Horizontal Scroll Row
            val currentSymbols = categories[selectedCategoryIndex].second
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(currentSymbols) { symbol ->
                    val isLatex = categories[selectedCategoryIndex].first == "LaTeX"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .defaultMinSize(minWidth = if (isLatex) 64.dp else 36.dp, minHeight = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onInsertSymbol(symbol) }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = if (isLatex) 8.dp else 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = symbol,
                                fontSize = if (isLatex) 12.sp else 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
