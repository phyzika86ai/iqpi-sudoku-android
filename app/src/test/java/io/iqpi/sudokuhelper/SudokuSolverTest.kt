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
}
