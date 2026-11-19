package com.edgeedu.app

import com.edgeedu.app.tutor.MathEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The step-by-step solver: it must show worked, verified steps for the problem
 * classes it understands (arithmetic, one-variable linear equations) and return
 * *unsolved* for anything else so the tutor falls back to the model — never a
 * guessed or wrong answer.
 */
class MathEngineStepsTest {

    @Test
    fun solvesArithmeticWithWorkedSteps() {
        val s = MathEngine.solveSteps("what is 15*3 + 7")
        assertEquals("52", s.answer)
        assertTrue(s.verified)
        // Final step shows the verified result.
        assertTrue(s.steps.last().verified)
        assertTrue(s.steps.any { it.latex.contains("= 52") })
    }

    @Test
    fun solvesLinearEquationStepByStep() {
        val s = MathEngine.solveSteps("solve 2x + 3 = 7")
        assertEquals("x = 2", s.answer)
        assertTrue(s.verified)
        // The working is shown, not just the answer.
        assertTrue(s.steps.size >= 4)
        assertTrue(s.steps.any { it.latex.contains("2x + 3 = 7") }) // restated cleanly
        assertTrue(s.steps.any { it.latex.contains("x = 2") })      // final answer
    }

    @Test
    fun detectsTheVariableAndStripsCommandWords() {
        // "find" is dropped; the variable is n, not the default x.
        val s = MathEngine.solveSteps("find 3n - 6 = 0")
        assertEquals("n = 2", s.answer)
    }

    @Test
    fun solvesQuadraticEquationsStepByStep() {
        // "x*x = 4" used to be out of scope; the quadratic solver now takes it.
        val s = MathEngine.solveSteps("x*x = 4")
        assertEquals("x = 2 or x = -2", s.answer)
        assertTrue(s.verified)
        assertTrue(s.steps.last().verified)
    }

    @Test
    fun returnsUnsolvedBeyondQuadratics() {
        // Cubic and transcendental equations stay out of scope: the solver
        // must bow out (never guess) so the tutor falls back to the model.
        val s = MathEngine.solveSteps("x^3 = 8")
        assertNull(s.answer)
        assertTrue(s.steps.isEmpty())
    }

    @Test
    fun solvesRatioValueQuestions() {
        // The Std 9 Ratio & Proportion classic: a/b = 7/3, find (a²+b²)/(a²−b²).
        val s = MathEngine.solveSteps("If a/b = 7/3 then find the value of (a² + b²)/(a² − b²)")
        assertEquals("29/20", s.answer)
        assertTrue(s.verified)
        assertTrue(s.steps.size >= 4)
        assertTrue(s.steps.any { it.latex.contains("7k") })   // teaches a = 7k, b = 3k
        assertTrue(s.steps.last().verified)                    // scale check shown

        // Degree-1 form works too: (2a+b)/(2a-b) with a/b = 7/3 → 17/11.
        assertEquals("17/11", MathEngine.solveSteps("a/b = 7/3 find (2a + b)/(2a - b)").answer)
    }

    @Test
    fun ratioSolverRefusesScaleDependentExpressions() {
        // a + b = 10k depends on k — answering would be a guess, so it must
        // bow out and let the tutor fall back to the explanation model.
        val s = MathEngine.solveSteps("If a/b = 7/3 then find the value of a + b")
        assertNull(s.answer)
    }

    @Test
    fun toleratesSentencePunctuationAroundTheMaths() {
        // Students type the doubt as a sentence — the parser must survive it.
        assertEquals("x = 2", MathEngine.solveSteps("solve for x: 2x + 3 = 7").answer)
        assertEquals("52", MathEngine.solveSteps("what is 15*3 + 7?").answer)
        assertEquals("x = 2", MathEngine.solveSteps("find the value of x. 2x + 3 = 7.").answer)
    }

    @Test
    fun returnsUnsolvedForWordedPhysicsProblems() {
        // No clean equation -> the solver bows out so the model can teach it.
        val s = MathEngine.solveSteps("a car accelerates from 0 to 20 in 4 seconds")
        assertNull(s.answer)
    }
}
