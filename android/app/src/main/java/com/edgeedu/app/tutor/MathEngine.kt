package com.edgeedu.app.tutor

import com.edgeedu.app.data.SolutionStep
import org.mariuszgromada.math.mxparser.Argument
import org.mariuszgromada.math.mxparser.Expression
import org.mariuszgromada.math.mxparser.License
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Verified computation via mXparser. The LLM never calculates: it emits a
 * structured call, this engine computes, and the result is verified by
 * substitution before it reaches the student.
 */
object MathEngine {

    init {
        // mXparser >= 5 requires explicit license confirmation.
        License.iConfirmNonCommercialUse("EdgeEdu portfolio build")
    }

    sealed class CalcResult {
        /** [display] is plain text, [latex] is renderable. Verified = checked by substitution. */
        data class Success(val display: String, val latex: String, val verified: Boolean) : CalcResult()
        data class Failure(val reason: String) : CalcResult()
    }

    /** Map the symbol-bar / Unicode maths characters to what mXparser parses. */
    private fun toAscii(expr: String): String =
        expr.replace("²", "^2").replace("³", "^3").replace("⁴", "^4")
            .replace("×", "*").replace("·", "*").replace("÷", "/")
            .replace("−", "-").replace("–", "-").replace("—", "-")
            .replace("√", "sqrt").replace("∛", "cbrt").replace("π", "pi")

    /** Students write "2x + 3"; mXparser needs "2*x + 3". */
    private fun normalize(expr: String): String =
        toAscii(expr)
            .replace(Regex("""(\d)\s*([a-zA-Z(])"""), "$1*$2")
            .replace(Regex("""(\))\s*([a-zA-Z(\d])"""), "$1*$2")

    private fun round(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else "%.6g".format(value).trimEnd('0').trimEnd('.')

    /** Evaluate a plain numeric expression, e.g. "15*3 + 7". */
    fun evaluate(rawExpr: String): CalcResult {
        val expr = normalize(rawExpr)
        val e = Expression(expr)
        if (!e.checkSyntax()) return CalcResult.Failure("syntax: ${e.errorMessage.trim()}")
        val value = e.calculate()
        if (value.isNaN()) return CalcResult.Failure("could not evaluate '$expr'")
        return CalcResult.Success(
            display = "$expr = ${round(value)}",
            latex = "$expr = ${round(value)}",
            verified = true,
        )
    }

    /**
     * Solve a linear equation "lhs = rhs" in one variable.
     *
     * Works algebraically for any linear form: with f(t) = lhs - rhs, linearity
     * means f(t) = f(0) + (f(1) - f(0)) t, so the root is -f(0) / (f(1) - f(0)).
     * Linearity itself is checked via f(2), and the root is verified by
     * substitution — a non-linear or unsolvable input returns Failure rather
     * than a wrong answer.
     */
    fun solveLinear(rawEquation: String, variable: String = "x"): CalcResult {
        val equation = normalize(rawEquation)
        val sides = equation.split("=")
        if (sides.size != 2) return CalcResult.Failure("expected one '=' in '$equation'")
        val f = "(${sides[0]}) - (${sides[1]})"

        fun at(value: Double): Double {
            val arg = Argument("$variable = $value")
            val e = Expression(f, arg)
            return if (e.checkSyntax()) e.calculate() else Double.NaN
        }

        val f0 = at(0.0)
        val f1 = at(1.0)
        val f2 = at(2.0)
        if (f0.isNaN() || f1.isNaN()) return CalcResult.Failure("could not parse '$equation'")
        val slope = f1 - f0
        if (abs(f2 - (2 * f1 - f0)) > 1e-9) {
            return CalcResult.Failure("'$equation' is not linear in $variable")
        }
        if (abs(slope) < 1e-12) return CalcResult.Failure("$variable cancels out in '$equation'")

        val root = -f0 / slope
        val residual = at(root)
        val verified = abs(residual) < 1e-9
        if (!verified) return CalcResult.Failure("solution failed verification")
        return CalcResult.Success(
            display = "$variable = ${round(root)}",
            latex = "$variable = ${round(root)}",
            verified = true,
        )
    }

    // ---- Step-by-step solving (PRD §7: teach the working, don't just answer) ----

    /** A worked solution: ordered [steps] plus the final [answer] (null if unsolved). */
    data class StepSolution(
        val steps: List<SolutionStep>,
        val answer: String?,
        val verified: Boolean,
    )

    private val UNSOLVED = StepSolution(emptyList(), null, false)

    private fun step(text: String, latex: String, verified: Boolean = false) =
        SolutionStep(text = text, latex = latex, verified = verified)

    /** Drop the explicit '*' between a number and a variable/paren for display. */
    private fun pretty(expr: String): String =
        expr.replace(Regex("""(\d)\*([a-zA-Z(])"""), "$1$2").replace(Regex("""\s+"""), " ").trim()

    /** Render a coefficient·variable term: "2x", "x", "-x". */
    private fun coefVar(coef: String, variable: String): String = when (coef) {
        "1" -> variable
        "-1" -> "-$variable"
        else -> "$coef$variable"
    }

    private fun detectVariable(equation: String): String {
        val funcs = setOf("sin", "cos", "tan", "cot", "sec", "log", "ln", "sqrt", "cbrt", "exp", "abs", "pi")
        val tokens = Regex("[a-zA-Z]+").findAll(equation).map { it.value }.toList()
        return tokens.firstOrNull { it.length == 1 && it.lowercase() !in funcs }
            ?: tokens.firstOrNull { it.lowercase() !in funcs }
            ?: "x"
    }

    /**
     * Solve [rawInput] showing every step, each verified. Handles a one-variable
     * linear or quadratic equation ("3x + 4 = 19"), a pure arithmetic expression
     * ("15*3 + 7"), or a ratio-value question ("if a/b = 7/3 find (a²+b²)/(a²−b²)");
     * anything else returns an unsolved [StepSolution] so the caller can fall
     * back to the explanation model (e.g. a physics word problem).
     */
    fun solveSteps(rawInput: String): StepSolution {
        val input = extractMath(rawInput)
        val hasEquals = input.count { it == '=' } == 1
        val hasVariable = input.any { it.isLetter() }
        val variable = detectVariable(normalize(input))
        return when {
            // "Given a/b = p/q, find <expression in a and b>" — the ratio
            // pattern is unmistakable, so try it before the one-variable solvers.
            hasEquals && hasVariable -> {
                ratioSteps(input).takeUnless { it === UNSOLVED }?.let { return it }
                val sides = input.split("=")
                quadraticSteps(sides[0], sides[1], variable).takeUnless { it === UNSOLVED }
                    ?: linearSteps(input, variable)
            }
            // A bare polynomial with a power, like "x^2 - 2x - 1" (no '='): solve
            // it set to 0. Requiring '^' keeps worded questions (which can reduce
            // to a stray "a 2") from being mistaken for an equation to solve.
            !hasEquals && hasVariable && input.contains('^') ->
                quadraticSteps(input, "0", variable).takeUnless { it === UNSOLVED }
                    ?: linearSteps("$input = 0", variable)
            // Pure arithmetic.
            input.none { it.isLetter() } && input.any { it.isDigit() } &&
                input.any { it in "+-*/^" } -> arithmeticSteps(input)
            else -> UNSOLVED
        }
    }

    /**
     * Reduce a typed question to the bare maths so command words don't break the
     * parser: drop every alphabetic run of length ≥ 2 ("solve", "find", "value"),
     * keeping single letters as variables — so "solve 2x + 3 = 7" → "2x + 3 = 7".
     * A worded problem with no clean equation (a physics numerical in prose) is
     * left without a solvable form, so [solveSteps] returns unsolved and the
     * caller hands it to the explanation model.
     */
    private fun extractMath(question: String): String =
        question
            // Convert powers/operators first (these add no letters), so the
            // classifier sees "x^2"; √, ∛, π are single chars that survive the
            // word-strip and are expanded later by normalize().
            .replace("²", "^2").replace("³", "^3").replace("⁴", "^4")
            .replace("×", "*").replace("·", "*").replace("÷", "/")
            .replace("−", "-").replace("–", "-").replace("—", "-")
            // Drop command words in any script: Latin runs ≥2 and Devanagari
            // (Hindi/Marathi), e.g. "हल करें 2x+3=7" → "2x+3=7".
            .replace(Regex("""[A-Za-z]{2,}"""), " ")
            .replace(Regex("""[ऀ-ॿ]+"""), " ")
            // Drop sentence punctuation students type around the maths —
            // "solve for x: 2x+3=7?" must not reach the parser with ':' or '?'.
            // A '.' survives only as a decimal point (digits on both sides).
            .replace(Regex("""[?!,:;'"।]"""), " ")
            .replace(Regex("""(?<![0-9])\.|\.(?![0-9])"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
            // "solve for x: 2x+3=7" leaves a lone "x" at the front (and "find
            // 2x+3=7 for x" one at the back). Drop a stray single-letter token
            // at either end when that variable also appears in the maths left.
            .let { s ->
                var t = s
                while (true) {
                    val lead = Regex("""^([A-Za-z])\s+(.*)$""").find(t)
                    if (lead != null && lead.groupValues[2].contains(lead.groupValues[1])) {
                        t = lead.groupValues[2]; continue
                    }
                    val tail = Regex("""^(.*)\s+([A-Za-z])$""").find(t)
                    if (tail != null && tail.groupValues[1].contains(tail.groupValues[2])) {
                        t = tail.groupValues[1]; continue
                    }
                    break
                }
                t
            }

    private fun arithmeticSteps(rawExpr: String): StepSolution {
        val expr = normalize(rawExpr)
        val e = Expression(expr)
        if (!e.checkSyntax()) return UNSOLVED
        val value = e.calculate()
        if (value.isNaN()) return UNSOLVED
        val answer = round(value)
        val shown = pretty(expr)
        return StepSolution(
            steps = listOf(
                step(
                    "Read the expression. By the BODMAS rule we work out brackets and powers first, " +
                        "then multiplication and division, and finally addition and subtraction — left to right.",
                    shown,
                ),
                step("Working through it in that order gives the result.", "$shown = $answer", verified = true),
            ),
            answer = answer,
            verified = true,
        )
    }

    private fun linearSteps(rawEquation: String, variable: String): StepSolution {
        val equation = normalize(rawEquation)
        val sides = equation.split("=")
        if (sides.size != 2) return UNSOLVED
        val f = "(${sides[0]}) - (${sides[1]})"
        fun fAt(v: Double): Double {
            val e = Expression(f, Argument("$variable = $v"))
            return if (e.checkSyntax()) e.calculate() else Double.NaN
        }
        val f0 = fAt(0.0)
        val f1 = fAt(1.0)
        val f2 = fAt(2.0)
        if (f0.isNaN() || f1.isNaN()) return UNSOLVED
        val slope = f1 - f0
        if (abs(f2 - (2 * f1 - f0)) > 1e-9) return UNSOLVED // not linear
        if (abs(slope) < 1e-12) return UNSOLVED             // variable cancels out
        val root = -f0 / slope
        if (abs(fAt(root)) >= 1e-9) return UNSOLVED         // failed verification

        val lhs = pretty(sides[0].trim())
        val rhs = pretty(sides[1].trim())
        val coef = round(slope)
        val varTerm = coefVar(coef, variable)
        val rootStr = round(root)
        val moveConst = round(-f0)
        // Both sides evaluated at the answer — a concrete check the student can see.
        val lhsAtRoot = round(evalAt(sides[0], variable, root))
        val rhsAtRoot = round(evalAt(sides[1], variable, root))
        return StepSolution(
            steps = listOf(
                step(
                    "Start with the equation exactly as given. Our goal is to get $variable by itself on one side.",
                    "$lhs = $rhs",
                ),
                step(
                    "Collect the $variable-terms on the left and the plain numbers on the right. " +
                        "Remember: a term flips its sign (+ ↔ −) when it crosses the = sign.",
                    "$varTerm = $moveConst",
                ),
                step(
                    "$variable is multiplied by $coef, so divide BOTH sides by $coef to undo that and " +
                        "leave $variable alone — doing the same thing to both sides keeps the equation balanced.",
                    "$variable = $moveConst / $coef",
                ),
                step("Work out the division — that is the answer.", "$variable = $rootStr", verified = true),
                step(
                    "Check it: put $variable = $rootStr back into the original equation. " +
                        "Both sides come out equal, so the answer is correct.",
                    "$lhsAtRoot = $rhsAtRoot  ✓",
                    verified = true,
                ),
            ),
            answer = "$variable = $rootStr",
            verified = true,
        )
    }

    /** Evaluate one side of an equation with [variable] set to [value]. */
    private fun evalAt(expr: String, variable: String, value: Double): Double {
        val e = Expression(expr, Argument("$variable = $value"))
        return if (e.checkSyntax()) e.calculate() else Double.NaN
    }

    // ---- Ratio-value questions: "if a/b = 7/3, find (a² + b²)/(a² − b²)" ----

    private val ratioGiven =
        Regex("""([a-zA-Z])\s*/\s*([a-zA-Z])\s*=\s*(-?\d+(?:\.\d+)?)\s*/\s*(-?\d+(?:\.\d+)?)""")

    /** x as an exact-looking fraction "29/20" when one exists, else null. */
    private fun asFraction(x: Double): String? {
        if (x == x.toLong().toDouble()) return null // whole numbers need no fraction
        var n0 = 0L; var d0 = 1L; var n1 = 1L; var d1 = 0L
        var v = kotlin.math.abs(x)
        repeat(24) {
            val a = kotlin.math.floor(v).toLong()
            val n2 = a * n1 + n0
            val d2 = a * d1 + d0
            if (d2 > 10_000) return null
            if (abs(kotlin.math.abs(x) - n2.toDouble() / d2) < 1e-9) {
                val sign = if (x < 0) "-" else ""
                return "$sign$n2/$d2"
            }
            n0 = n1; d0 = d1; n1 = n2; d1 = d2
            val frac = v - a
            if (frac < 1e-12) return null
            v = 1.0 / frac
        }
        return null
    }

    /**
     * "Given a/b = p/q, find the value of f(a, b)". The trick taught in class:
     * write a = pk, b = qk — for a scale-free expression every k cancels, so
     * evaluating at (p, q) gives the value. Scale-freeness is *checked* by
     * also evaluating at (2p, 2q); an expression where k does not cancel
     * (like a + b) returns [UNSOLVED] instead of a wrong answer.
     */
    private fun ratioSteps(input: String): StepSolution {
        val m = ratioGiven.find(input) ?: return UNSOLVED
        val (v1, v2, pStr, qStr) = m.destructured
        if (v1.equals(v2, ignoreCase = true)) return UNSOLVED
        val p = pStr.toDouble()
        val q = qStr.toDouble()
        if (abs(q) < 1e-12) return UNSOLVED
        val target = input.removeRange(m.range).trim().trim('=').trim()
        if (target.isBlank() || !target.contains(v1) || !target.contains(v2)) return UNSOLVED

        val expr = normalize(target)
        fun at(scale: Double): Double {
            val e = Expression(expr, Argument("$v1 = ${p * scale}"), Argument("$v2 = ${q * scale}"))
            return if (e.checkSyntax()) e.calculate() else Double.NaN
        }
        val value = at(1.0)
        val check = at(2.0)
        if (value.isNaN() || check.isNaN()) return UNSOLVED
        // Not scale-free (k doesn't cancel): the value would depend on k.
        if (abs(value - check) > 1e-9 * maxOf(1.0, abs(value))) return UNSOLVED

        val pShow = round(p)
        val qShow = round(q)
        val shown = pretty(target)
        val fraction = asFraction(value)
        val answer = fraction ?: round(value)
        return StepSolution(
            steps = listOf(
                step(
                    "We are given the ratio $v1/$v2 = $pShow/$qShow. Any two numbers in this ratio " +
                        "can be written as $v1 = ${pShow}k and $v2 = ${qShow}k for the same k.",
                    "$v1 = ${pShow}k,\\ \\ $v2 = ${qShow}k",
                ),
                step(
                    "Substitute $v1 = ${pShow}k and $v2 = ${qShow}k into the expression. Because the " +
                        "top and bottom compare like powers of $v1 and $v2, every k cancels out.",
                    shown,
                ),
                step(
                    "So we can simply take $v1 = $pShow and $v2 = $qShow (k = 1) and work it out.",
                    "$shown = $answer",
                    verified = true,
                ),
                step(
                    "Check it: with k = 2 (so $v1 = ${round(2 * p)}, $v2 = ${round(2 * q)}) the value " +
                        "comes out the same — the answer doesn't depend on k, so it is correct.",
                    "= $answer  ✓",
                    verified = true,
                ),
            ),
            answer = answer,
            verified = true,
        )
    }

    /**
     * Solve a one-variable quadratic "lhs = rhs" with the quadratic formula,
     * showing every step. The polynomial a·x² + b·x + c is recovered by sampling
     * f(x) = lhs − rhs at a few points (f(0)=c, and a,b from f(1),f(−1)), then a
     * fourth sample confirms it really is degree ≤ 2. a≈0 (linear) or a higher
     * degree returns [UNSOLVED] so the caller can fall back. Roots are verified
     * by substitution; a negative discriminant reports "no real roots".
     */
    private fun quadraticSteps(lhsRaw: String, rhsRaw: String, variable: String): StepSolution {
        val f = "(${normalize(lhsRaw)}) - (${normalize(rhsRaw)})"
        fun fAt(v: Double): Double {
            val e = Expression(f, Argument("$variable = $v"))
            return if (e.checkSyntax()) e.calculate() else Double.NaN
        }
        val c = fAt(0.0); val fp1 = fAt(1.0); val fm1 = fAt(-1.0); val f2 = fAt(2.0)
        if (listOf(c, fp1, fm1, f2).any { it.isNaN() }) return UNSOLVED
        val a = (fp1 + fm1) / 2.0 - c
        val b = (fp1 - fm1) / 2.0
        // Confirm degree ≤ 2: a·4 + b·2 + c must match the sampled f(2).
        if (abs((4 * a + 2 * b + c) - f2) > 1e-7) return UNSOLVED
        if (abs(a) < 1e-9) return UNSOLVED // linear — let linearSteps handle it

        val std = standardForm(a, b, c, variable)
        val disc = b * b - 4 * a * c
        val coeffStep = step(
            "Read off the coefficients a (the $variable² term), b (the $variable term) and c (the number).",
            "a = ${round(a)},\\ \\ b = ${round(b)},\\ \\ c = ${round(c)}",
        )
        val discStep = step(
            "Find the discriminant D = b² − 4ac. Its sign tells us how many real answers there are.",
            "D = ${round(b)}^2 - 4(${round(a)})(${round(c)}) = ${round(disc)}",
        )
        val intro = step(
            "Write the equation in standard form a$variable² + b$variable + c = 0.",
            std,
        )

        if (disc < -1e-9) {
            return StepSolution(
                steps = listOf(intro, coeffStep, discStep, step(
                    "D is negative, so the curve never touches the $variable-axis — there are no real solutions.",
                    "D < 0 \\Rightarrow \\text{no real roots}", verified = true,
                )),
                answer = "no real roots", verified = true,
            )
        }

        val sq = sqrt(abs(disc))
        val r1 = (-b + sq) / (2 * a)
        val r2 = (-b - sq) / (2 * a)
        // Verify by substitution before showing anything.
        if (abs(fAt(r1)) >= 1e-6 || abs(fAt(r2)) >= 1e-6) return UNSOLVED

        val formula = step(
            "Use the quadratic formula. Substitute a, b and the discriminant.",
            "$variable = \\dfrac{-b \\pm \\sqrt{D}}{2a} = \\dfrac{${round(-b)} \\pm \\sqrt{${round(disc)}}}{${round(2 * a)}}",
        )
        return if (abs(r1 - r2) < 1e-9) {
            StepSolution(
                steps = listOf(intro, coeffStep, discStep, formula, step(
                    "D = 0, so both roots are the same (the curve just touches the $variable-axis).",
                    "$variable = ${round(r1)}", verified = true,
                )),
                answer = "$variable = ${round(r1)}", verified = true,
            )
        } else {
            StepSolution(
                steps = listOf(intro, coeffStep, discStep, formula, step(
                    "Take the + and − cases separately to get the two answers.",
                    "$variable = ${round(r1)} \\quad \\text{or} \\quad $variable = ${round(r2)}",
                    verified = true,
                )),
                answer = "$variable = ${round(r1)} or $variable = ${round(r2)}", verified = true,
            )
        }
    }

    /** Build "a x^2 + b x + c = 0" with tidy signs for display (LaTeX). */
    private fun standardForm(a: Double, b: Double, c: Double, v: String): String {
        val sb = StringBuilder()
        sb.append(coefTerm(a, "$v^2", first = true))
        sb.append(coefTerm(b, v, first = sb.isEmpty()))
        sb.append(coefTerm(c, "", first = sb.isEmpty()))
        if (sb.isEmpty()) sb.append("0")
        sb.append(" = 0")
        return sb.toString().trim()
    }

    private fun coefTerm(coef: Double, suffix: String, first: Boolean): String {
        if (abs(coef) < 1e-12) return ""
        val mag = round(abs(coef))
        val magStr = if (mag == "1" && suffix.isNotEmpty()) "" else mag
        val sign = when {
            first && coef < 0 -> "-"
            first -> ""
            coef < 0 -> " - "
            else -> " + "
        }
        return "$sign$magStr$suffix"
    }
}
