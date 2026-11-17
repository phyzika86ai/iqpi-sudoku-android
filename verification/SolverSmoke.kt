import io.iqpi.sudokuhelper.SudokuSolver

fun main() {
    val sample=intArrayOf(
        0,3,0,4,1,0,
        4,1,2,0,0,0,
        0,5,0,3,0,0,
        0,0,3,0,2,0,
        0,0,0,2,0,1,
        0,2,4,0,3,0)
    val expected=intArrayOf(
        5,3,6,4,1,2,
        4,1,2,6,5,3,
        2,5,1,3,6,4,
        6,4,3,1,2,5,
        3,6,5,2,4,1,
        1,2,4,5,3,6)
    val solution=SudokuSolver.solve(sample)
    check(solution.solutionCount==1 && solution.solution!!.contentEquals(expected))
    check(SudokuSolver.solve(IntArray(36)).solutionCount==2)
    val invalid=sample.copyOf(); invalid[0]=3
    check(SudokuSolver.solve(invalid).solutionCount==0)
    val cages=expected.indices.map { i -> SudokuSolver.Cage(setOf(i), expected[i]) }
    val killer=SudokuSolver.solve(IntArray(36), killer=true, cages=cages)
    check(killer.solutionCount==1 && killer.solution!!.contentEquals(expected))
    println("OK: 6x6 classic, uniqueness, invalid clues, killer cages")
}
