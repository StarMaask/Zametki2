package com.example.util

/**
 * Utility to sanitize formulas, LaTeX markup, and special characters from text,
 * converting them into clean, human-readable Unicode academic typography.
 */
object FormulaSanitizer {

    /**
     * Cleans math formulas, LaTeX tokens, and unparsed machine markup from text.
     * Converts raw tokens like `$+3,8\ ^\circ\text{C}$` to `+3,8 °C`,
     * and `$$K_{\text{ув}} = \frac{R}{E_0} \approx 1,35$$` to `K_ув = R / E₀ ≈ 1,35`.
     */
    fun cleanFormulasAndText(input: String): String {
        if (input.isBlank()) return input

        var text = input

        // 1. Degree Celsius & degree notations: ^\circ\text{C}, ^\circ C, ^\circ
        text = text.replace(Regex("""\^\\circ\s*\\text\{C\}""", RegexOption.IGNORE_CASE), "°C")
        text = text.replace(Regex("""\^\\circ\s*C"""), "°C")
        text = text.replace(Regex("""\^\\circ"""), "°")
        text = text.replace(Regex("""°\s*\\text\{C\}""", RegexOption.IGNORE_CASE), "°C")
        text = text.replace(Regex("""\^\\circ\s*\\text\{([^{}]+)\}""")) { "°${it.groupValues[1]}" }

        // 2. LaTeX spacing commands: \qquad, \quad, \;, \,, \ , \!
        text = text.replace(Regex("""\\qquad"""), "   ")
        text = text.replace(Regex("""\\quad"""), "  ")
        text = text.replace(Regex("""\\[;, !]"""), " ")

        // 3. LaTeX \text{...} -> extract inner content
        // Run twice to handle any simple nested structures
        text = text.replace(Regex("""\\text\{([^{}]+)\}""")) { it.groupValues[1] }
        text = text.replace(Regex("""\\text\{([^{}]+)\}""")) { it.groupValues[1] }
        text = text.replace(Regex("""\\mathrm\{([^{}]+)\}""")) { it.groupValues[1] }
        text = text.replace(Regex("""\\mathbf\{([^{}]+)\}""")) { it.groupValues[1] }

        // 4. Fractions \frac{A}{B} -> (A) / B or A / B
        text = text.replace(Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")) { match ->
            val num = match.groupValues[1].trim()
            val den = match.groupValues[2].trim()
            val cleanNum = cleanFormulasAndText(num)
            val cleanDen = cleanFormulasAndText(den)
            if (cleanNum.contains(" ") || cleanNum.contains("+") || cleanNum.contains("-")) {
                "($cleanNum) / $cleanDen"
            } else {
                "$cleanNum / $cleanDen"
            }
        }

        // 5. Common math comparison and relational operators
        text = text.replace(Regex("""\\approx"""), "≈")
        text = text.replace(Regex("""\\ge(q)?"""), "≥")
        text = text.replace(Regex("""\\le(q)?"""), "≤")
        text = text.replace(Regex("""\\ne(q)?"""), "≠")
        text = text.replace(Regex("""\\times"""), "×")
        text = text.replace(Regex("""\\cdot"""), "·")
        text = text.replace(Regex("""\\pm"""), "±")
        text = text.replace(Regex("""\\mp"""), "∓")
        text = text.replace(Regex("""\\equiv"""), "≡")
        text = text.replace(Regex("""\\sim"""), "~")

        // 6. Summations, products, integrals, roots, limits
        text = text.replace(Regex("""\\sum_\{[^}]*\}\^\{[^}]*\}"""), "∑")
        text = text.replace(Regex("""\\sum_\{[^}]*\}"""), "∑")
        text = text.replace(Regex("""\\sum"""), "∑")
        text = text.replace(Regex("""\\prod_\{[^}]*\}\^\{[^}]*\}"""), "∏")
        text = text.replace(Regex("""\\prod"""), "∏")
        text = text.replace(Regex("""\\int_\{[^}]*\}\^\{[^}]*\}"""), "∫")
        text = text.replace(Regex("""\\int"""), "∫")
        text = text.replace(Regex("""\\sqrt\{([^{}]+)\}""")) { "√(${cleanFormulasAndText(it.groupValues[1])})" }
        text = text.replace(Regex("""\\sqrt"""), "√")
        text = text.replace(Regex("""\\infty"""), "∞")

        // 7. Greek letters
        text = text.replace(Regex("""\\alpha"""), "α")
            .replace(Regex("""\\beta"""), "β")
            .replace(Regex("""\\gamma"""), "γ")
            .replace(Regex("""\\Gamma"""), "Γ")
            .replace(Regex("""\\delta"""), "δ")
            .replace(Regex("""\\Delta"""), "Δ")
            .replace(Regex("""\\epsilon"""), "ε")
            .replace(Regex("""\\zeta"""), "ζ")
            .replace(Regex("""\\eta"""), "η")
            .replace(Regex("""\\theta"""), "θ")
            .replace(Regex("""\\lambda"""), "λ")
            .replace(Regex("""\\Lambda"""), "Λ")
            .replace(Regex("""\\mu"""), "μ")
            .replace(Regex("""\\nu"""), "ν")
            .replace(Regex("""\\xi"""), "ξ")
            .replace(Regex("""\\pi"""), "π")
            .replace(Regex("""\\rho"""), "ρ")
            .replace(Regex("""\\sigma"""), "σ")
            .replace(Regex("""\\Sigma"""), "Σ")
            .replace(Regex("""\\tau"""), "τ")
            .replace(Regex("""\\phi"""), "φ")
            .replace(Regex("""\\Phi"""), "Φ")
            .replace(Regex("""\\chi"""), "χ")
            .replace(Regex("""\\psi"""), "ψ")
            .replace(Regex("""\\omega"""), "ω")
            .replace(Regex("""\\Omega"""), "Ω")

        // 8. Subscript & superscript formatting
        // e.g. T_{акт} -> T_акт
        text = text.replace(Regex("""_\{([^{}]+)\}""")) { "_${it.groupValues[1]}" }
        text = text.replace(Regex("""\^\{([^{}]+)\}""")) { "^${it.groupValues[1]}" }

        // Clean numeric and index subscripts
        text = text.replace("_0", "₀")
            .replace("_1", "₁")
            .replace("_2", "₂")
            .replace("_3", "₃")
            .replace("_4", "₄")
            .replace("_5", "₅")
            .replace("_i", "ᵢ")
            .replace("_j", "ⱼ")
            .replace("_n", "ₙ")
            .replace("_k", "ₖ")
            .replace("_m", "ₘ")
            .replace("_t", "ₜ")
            .replace("^2", "²")
            .replace("^3", "³")
            .replace("^n", "ⁿ")

        // 9. Remove outer math dollar signs: $$...$$ and $...$
        text = text.replace(Regex("""\$\$(.*?)\$\$""", RegexOption.DOT_MATCHES_ALL)) { match ->
            " " + match.groupValues[1].trim() + " "
        }
        text = text.replace(Regex("""\$(.*?)\$""")) { match ->
            match.groupValues[1].trim()
        }
        // Remove any lone or unmatched dollar signs
        text = text.replace("$", "")

        // 10. Clean stray backslashes before plain words or letters (e.g. `\при` -> `при`)
        text = text.replace(Regex("""\\([a-zA-Zа-яА-ЯёЁ])""")) { it.groupValues[1] }
        text = text.replace(Regex("""\\\s+"""), " ")

        // 11. Normalize multiple spaces (while preserving newlines)
        text = text.replace(Regex("""[ \t]{2,}"""), " ")

        return text
    }
}
