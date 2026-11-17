package io.iqpi.sudokuhelper

import org.junit.Assert.*
import org.junit.Test

class SudokuSolverTest {
    @Test fun screenshot6x6Unique() {
        val input = intArrayOf(
            0,3,0,4,1,0,
            4,1,2,0,0,0,
            0,5,0,3,0,0,
            0,0,3,0,2,0,
            0,0,0,2,0,1,
            0,2,4,0,3,0)
        val expected = intArrayOf(
            5,3,6,4,1,2,
            4,1,2,6,5,3,
            2,5,1,3,6,4,
            6,4,3,1,2,5,
            3,6,5,2,4,1,
            1,2,4,5,3,6)
        val result = SudokuSolver.solve(input)
        assertEquals(1, result.solutionCount)
        assertArrayEquals(expected, result.solution)
    }

    @Test fun conflictingGivensRejected() {
        val g = IntArray(36); g[0]=3; g[1]=3
        assertEquals(0, SudokuSolver.solve(g).solutionCount)
    }

    @Test fun emptyPuzzleIsAmbiguous() {
        assertEquals(2, SudokuSolver.solve(IntArray(36)).solutionCount)
    }

    @Test fun killerNeedsGroups() {
        assertNull(SudokuSolver.solve(IntArray(36), killer = true).solution)
    }

    private val solved = intArrayOf(
        5,3,6,4,1,2, 4,1,2,6,5,3, 2,5,1,3,6,4,
        6,4,3,1,2,5, 3,6,5,2,4,1, 1,2,4,5,3,6)

    private fun singles() = solved.indices.map { SudokuSolver.Cage(setOf(it), solved[it]) }

    @Test fun classicIgnoresSavedKillerCages() {
        val wrong = listOf(SudokuSolver.Cage(setOf(0, 999), 1))
        assertArrayEquals(solved, SudokuSolver.solve(solved, false, wrong).solution)
    }

    @Test fun invalidSizeAndDigitsRejected() {
        assertEquals(0, SudokuSolver.solve(IntArray(35)).solutionCount)
        for (bad in listOf(-1, 7)) {
            val board = IntArray(36); board[0] = bad
            assertEquals(0, SudokuSolver.solve(board).solutionCount)
        }
    }

    @Test fun columnAndTwoByThreeBoxConflictsRejected() {
        for (other in listOf(6, 7)) {
            val board = IntArray(36); board[0] = 2; board[other] = 2
            assertEquals(0, SudokuSolver.solve(board).solutionCount)
        }
    }

    @Test fun inputIsNotModified() {
        val board = solved.copyOf(); board[0] = 0
        val before = board.copyOf()
        SudokuSolver.solve(board)
        assertArrayEquals(before, board)
    }

    @Test fun killerSolvesWithoutGivensAndWithMixedCageSizes() {
        val groups = listOf(setOf(0,1,2), setOf(3,4), setOf(6,7), setOf(12,18),
            setOf(14,15,16), setOf(25,26), setOf(28,29,35))
        val used = groups.flatten().toSet()
        val cages = groups.map { cells -> SudokuSolver.Cage(cells, cells.sumOf { solved[it] }) } +
            singles().filter { it.cells.single() !in used }
        val result = SudokuSolver.solve(IntArray(36), true, cages)
        assertEquals(1, result.solutionCount)
        assertArrayEquals(solved, result.solution)
    }

    @Test fun killerRejectsMissingOverlappingAndOutOfRangeCells() {
        val valid = singles()
        assertEquals(0, SudokuSolver.solve(IntArray(36), true, valid.dropLast(1)).solutionCount)
        assertEquals(0, SudokuSolver.solve(IntArray(36), true, valid + valid[0]).solutionCount)
        val outside = valid.toMutableList(); outside[0] = SudokuSolver.Cage(setOf(36), 5)
        assertEquals(0, SudokuSolver.solve(IntArray(36), true, outside).solutionCount)
    }

    @Test fun killerRejectsWrongAndImpossibleSums() {
        val wrong = singles().toMutableList(); wrong[0] = SudokuSolver.Cage(setOf(0), 4)
        assertEquals(0, SudokuSolver.solve(solved, true, wrong).solutionCount)
        val impossible = singles().drop(2) + SudokuSolver.Cage(setOf(0,1), 2)
        assertEquals(0, SudokuSolver.solve(IntArray(36), true, impossible).solutionCount)
    }

    @Test fun killerRejectsRepeatedDigitsEvenOutsideRowColumnAndBox() {
        // Cells 0 and 13 are both 5, but do not share a row, column or box.
        val cages = singles().filter { it.cells.single() !in setOf(0,13) } +
            SudokuSolver.Cage(setOf(0,13), 10)
        assertEquals(0, SudokuSolver.solve(solved, true, cages).solutionCount)
    }

    @Test fun killerAmbiguousCagesDoNotClaimUniqueness() {
        val cages = (0..5).map { row -> SudokuSolver.Cage((0..5).map { row*6+it }.toSet(), 21) }
        assertEquals(2, SudokuSolver.solve(IntArray(36), true, cages).solutionCount)
    }
}
