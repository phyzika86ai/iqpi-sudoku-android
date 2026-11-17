package io.iqpi.sudokuhelper

/** Rows and columns are 0..5; boxes are 2 rows by 3 columns. */
object SudokuSolver {
    data class Cage(val cells: Set<Int>, val sum: Int)
    data class Result(val solution: IntArray?, val solutionCount: Int, val error: String? = null)

    fun solve(input: IntArray, killer: Boolean = false, cages: List<Cage> = emptyList()): Result {
        if (input.size != 36 || input.any { it !in 0..6 }) return Result(null, 0, "Неверная сетка")
        if (killer) {
            val all = cages.flatMap { it.cells }
            if (all.size != 36 || all.toSet().size != 36 || all.any { it !in 0..35 } ||
                cages.any { it.cells.isEmpty() || it.sum !in 1..21 || it.cells.size > 6 })
                return Result(null, 0, "Для Killer задайте все группы и суммы")
        }
        val board = input.copyOf()
        for (i in 0..35) {
            val v = board[i]
            if (v == 0) continue
            board[i] = 0
            if (!allowed(board, i, v, cages)) return Result(null, 0, "Противоречие: строка ${i / 6 + 1}, столбец ${i % 6 + 1}")
            board[i] = v
        }
        var solutions = 0
        var first: IntArray? = null
        fun recurse() {
            if (solutions >= 2) return
            var bestIndex = -1
            var bestOptions = IntArray(0)
            var smallest = 7
            for (i in 0..35) {
                if (board[i] != 0) continue
                val options = (1..6).filter { allowed(board, i, it, cages) }.toIntArray()
                if (options.isEmpty()) return
                if (options.size < smallest) {
                    bestIndex = i
                    bestOptions = options
                    smallest = options.size
                    if (smallest == 1) break
                }
            }
            if (bestIndex == -1) {
                solutions++
                if (first == null) first = board.copyOf()
                return
            }
            for (v in bestOptions) {
                board[bestIndex] = v
                recurse()
                board[bestIndex] = 0
                if (solutions >= 2) return
            }
        }
        recurse()
        val error = when (solutions) {
            0 -> "Решения нет. Проверьте распознавание"
            1 -> null
            else -> "Решение неоднозначно: проверьте цифры и группы"
        }
        return Result(first, solutions, error)
    }

    private fun allowed(board: IntArray, i: Int, v: Int, cages: List<Cage>): Boolean {
        val r = i / 6; val c = i % 6
        for (p in 0..5) {
            if (board[r * 6 + p] == v || board[p * 6 + c] == v) return false
        }
        for (rr in (r / 2) * 2 until (r / 2) * 2 + 2)
            for (cc in (c / 3) * 3 until (c / 3) * 3 + 3)
                if (board[rr * 6 + cc] == v) return false
        for (cage in cages) {
            if (i !in cage.cells) continue
            var sum = v
            var empties = 0
            val used = mutableSetOf(v)
            for (j in cage.cells) {
                if (j == i) continue
                val digit = board[j]
                if (digit == 0) { empties++; continue }
                if (!used.add(digit)) return false
                sum += digit
            }
            if (sum > cage.sum) return false
            if (empties == 0 && sum != cage.sum) return false
            if (empties > 0) {
                val remaining = (1..6).filter { it !in used }
                if (remaining.size < empties) return false
                if (sum + remaining.take(empties).sum() > cage.sum) return false
                if (sum + remaining.takeLast(empties).sum() < cage.sum) return false
            }
        }
        return true
    }
}
