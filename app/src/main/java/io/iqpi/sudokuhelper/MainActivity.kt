package io.iqpi.sudokuhelper

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Companion phone app: physical camera aimed at the computer display.
 * No integration with the game, no remote control and no network permission.
 */
class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var cameraGuide: CameraGuide
    private lateinit var boardView: SudokuBoardView
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var scanButton: Button
    private lateinit var modeButton: Button
    private lateinit var cageButton: Button
    private lateinit var clearCagesButton: Button
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var recognizer: DigitRecognizer
    private val currentBoard = IntArray(36)
    private var cameraReady = false
    private var scanning = true
    private var busy = false
    private var killer = false
    private var editingCage = false
    private val selectedCage = mutableSetOf<Int>()
    private val cages = mutableListOf<SudokuSolver.Cage>()
    private var hint = -1
    private var lastRead: IntArray? = null
    private var lastUncertain: Set<Int> = emptySet()
    private var stableReadings = 0
    private var cameraControl: androidx.camera.core.CameraControl? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else status.text = "Нужен доступ к камере для анализа изображения"
    }

    private val captureTick = object : Runnable {
        override fun run() {
            if (scanning && cameraReady && !busy && !editingCage) capturePreview()
            mainHandler.postDelayed(this, 650L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 18, 30)
        window.navigationBarColor = Color.rgb(7, 18, 30)
        recognizer = DigitRecognizer(this)
        makeScreen()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
        mainHandler.post(captureTick)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(captureTick)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun makeText(value: String, size: Float, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

    private fun makeButton(value: String) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(31, 73, 94)))
        minHeight = dp(42)
    }

    private fun addRow(root: LinearLayout, vararg controls: View) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.forEach { view ->
            row.addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(2), dp(1), dp(2), dp(1))
            })
        }
        root.addView(row)
    }

    private fun makeScreen() {
        val scroll = ScrollView(this).apply { fillViewport = true; setBackgroundColor(Color.rgb(7, 18, 30)) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(20))
        }
        scroll.addView(body)
        setContentView(scroll)

        val title = makeText("IQPI • SUDOKU CAMERA 6×6", 19f, 0xFF33D8CB.toInt())
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        body.addView(title)
        body.addView(makeText("Наведи камеру на поле. Совмести края судоку с зелёной сеткой.", 13f, 0xFFC4D7E1.toInt()))

        val frame = SquareFrame(this)
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        frame.addView(preview, android.widget.FrameLayout.LayoutParams(-1, -1))
        cameraGuide = CameraGuide(this)
        frame.addView(cameraGuide, android.widget.FrameLayout.LayoutParams(-1, -1))
        body.addView(frame, LinearLayout.LayoutParams(-1, -2))

        answer = makeText("Подсказка: наведи камеру на поле", 21f, 0xFF33D8CB.toInt())
        answer.setTypeface(null, android.graphics.Typeface.BOLD)
        answer.gravity = Gravity.CENTER
        body.addView(answer)
        status = makeText("Камера запускается...", 13f, 0xFFE0E8ED.toInt())
        status.gravity = Gravity.CENTER
        body.addView(status)

        scanButton = makeButton("Пауза / правка").apply { setOnClickListener { toggleScan() } }
        modeButton = makeButton("Режим: классика").apply { setOnClickListener { switchMode() } }
        addRow(body, scanButton, modeButton)

        val zoomText = makeText("Увеличение камеры", 12f, 0xFFB6CAD3.toInt())
        body.addView(zoomText)
        val zoom = SeekBar(this).apply {
            max = 100; progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) cameraControl?.setLinearZoom(value / 100f)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        body.addView(zoom)

        body.addView(makeText("Белые цифры — увиденные в игре, бирюзовые — решения. Для исправления нажми «Пауза / правка».", 13f))
        boardView = SudokuBoardView(this).apply { onCellTapped = { cell -> boardTapped(cell) } }
        body.addView(boardView, LinearLayout.LayoutParams(-1, -2))

        cageButton = makeButton("Killer: добавить группу").apply { setOnClickListener { addOrFinishCage() } }
        clearCagesButton = makeButton("Сбросить группы").apply { setOnClickListener { cages.clear(); selectedCage.clear(); editingCage = false; updateBoard(); updateHint() } }
        addRow(body, cageButton, clearCagesButton)
        val demo = makeButton("Проверить на примере со скриншота").apply {
            setOnClickListener {
                scanning = false
                scanButton.text = "Продолжить сканирование"
                killer = false
                modeButton.text = "Режим: классика"
                editingCage = false
                cages.clear()
                val example = intArrayOf(
                    0,3,0,4,1,0, 4,1,2,0,0,0, 0,5,0,3,0,0,
                    0,0,3,0,2,0, 0,0,0,2,0,1, 0,2,4,0,3,0)
                example.copyInto(currentBoard)
                lastRead = null
                updateBoard()
                updateHint()
            }
        }
        body.addView(demo, LinearLayout.LayoutParams(-1, -2))
        body.addView(makeText("Для Killer: выбери режим, поставь на паузу, нажми «Добавить группу», отметь её клетки и введи сумму. Повтори для всех 36 клеток. Границы групп камера пока не считывает.", 12f, 0xFFA7B8C4.toInt()))
        body.addView(makeText("Работает локально без передачи видеокадров. Проверь правила турнира: внешние подсказки могут быть запрещены.", 11f, 0xFF8DA4B2.toInt()))
        cageButton.visibility = View.GONE
        clearCagesButton.visibility = View.GONE
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val feed = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                provider.unbindAll()
                val camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, feed)
                cameraControl = camera.cameraControl
                cameraReady = true
                status.text = "Камера готова • считывание каждые 0,65 с"
            } catch (e: Exception) {
                status.text = "Ошибка камеры: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePreview() {
        val shot = preview.bitmap ?: return
        if (shot.width < 150 || shot.height < 150) { shot.recycle(); return }
        val size = (min(shot.width, shot.height) * 0.90f).roundToInt()
        val left = (shot.width - size) / 2
        val top = (shot.height - size) / 2
        val cropped = try { Bitmap.createBitmap(shot, left, top, size, size) }
        catch (_: Exception) { shot.recycle(); return }
        shot.recycle()
        busy = true
        worker.execute {
            try {
                val result = recognizer.read(cropped)
                runOnUiThread { if (scanning) applyScan(result) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Ошибка распознавания: ${e.message}" }
            } finally {
                cropped.recycle()
                runOnUiThread { busy = false }
            }
        }
    }

    private fun applyScan(scan: DigitRecognizer.Scan) {
        if (scan.found < 8) {
            hint = -1
            cameraGuide.hintCell = -1
            boardView.solution = null
            boardView.hintCell = -1
            boardView.invalidate()
            status.text = "Найдено ${scan.found} цифр. Приблизь поле и выровняй рамку."
            lastRead = null
            stableReadings = 0
            return
        }
        if (lastRead?.contentEquals(scan.digits) != true || lastUncertain != scan.uncertain) {
            lastRead = scan.digits.copyOf()
            lastUncertain = scan.uncertain.toSet()
            stableReadings = 1
            status.text = "Распознавание: ${scan.found} цифр. Проверяю второй кадр..."
            return
        }
        stableReadings++
        if (stableReadings < 2) return
        scan.digits.copyInto(currentBoard)
        boardView.uncertainty = scan.uncertain
        updateBoard()
        if (scan.uncertain.isNotEmpty()) {
            hint = -1
            cameraGuide.hintCell = -1
            boardView.hintCell = -1
            boardView.solution = null
            answer.text = "Нужна проверка цифр"
            status.text = "Неуверенные клетки: ${scan.uncertain.size}. Нажми «Пауза / правка»."
        } else {
            updateHint()
            if (hint >= 0) status.text = "Считано ${scan.found} цифр • уверенность ${"%.0f".format(scan.averageSimilarity * 100)}%"
        }
    }

    private fun toggleScan() {
        scanning = !scanning
        if (scanning) {
            editingCage = false
            selectedCage.clear()
            lastRead = null
            stableReadings = 0
            scanButton.text = "Пауза / правка"
            status.text = "Сканирование запущено. Совмести игровое поле с сеткой."
        } else {
            scanButton.text = "Продолжить сканирование"
            status.text = "Пауза. Нажми на клетку в нижнем поле, чтобы исправить цифру."
        }
        updateBoard()
    }

    private fun switchMode() {
        killer = !killer
        modeButton.text = if (killer) "Режим: Killer" else "Режим: классика"
        cageButton.visibility = if (killer) View.VISIBLE else View.GONE
        clearCagesButton.visibility = if (killer) View.VISIBLE else View.GONE
        editingCage = false
        selectedCage.clear()
        updateBoard()
        updateHint()
    }

    private fun boardTapped(index: Int) {
        if (editingCage) {
            if (cages.any { index in it.cells }) {
                Toast.makeText(this, "Эта клетка уже в группе", Toast.LENGTH_SHORT).show()
                return
            }
            if (!selectedCage.add(index)) selectedCage.remove(index)
            updateBoard()
            return
        }
        if (scanning) {
            Toast.makeText(this, "Сначала поставь сканирование на паузу", Toast.LENGTH_SHORT).show()
            return
        }
        val names = arrayOf("Очистить", "1", "2", "3", "4", "5", "6")
        AlertDialog.Builder(this)
            .setTitle("Строка ${index / 6 + 1}, столбец ${index % 6 + 1}")
            .setItems(names) { _, n ->
                currentBoard[index] = n
                boardView.uncertainty = boardView.uncertainty - index
                updateBoard()
                updateHint()
            }
            .show()
    }

    private fun addOrFinishCage() {
        if (!killer) return
        if (!editingCage) {
            if (scanning) toggleScan()
            editingCage = true
            selectedCage.clear()
            cageButton.text = "Закончить группу"
            status.text = "Отметь клетки одной группы на нижней сетке, затем нажми «Закончить группу»."
            return
        }
        if (selectedCage.isEmpty()) {
            editingCage = false
            cageButton.text = "Killer: добавить группу"
            status.text = "Группа не выбрана"
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Сумма группы"
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("Сумма для ${selectedCage.size} клеток")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val total = input.text.toString().toIntOrNull()
                val count = selectedCage.size
                val minSum = count * (count + 1) / 2
                val maxSum = (7-count..6).sum()
                if (count > 6 || total == null || total !in minSum..maxSum) {
                    Toast.makeText(this, "Недопустимая сумма для группы из $count клеток", Toast.LENGTH_LONG).show()
                } else {
                    cages.add(SudokuSolver.Cage(selectedCage.toSet(), total))
                }
                editingCage = false
                selectedCage.clear()
                cageButton.text = "Killer: добавить группу"
                updateBoard()
                updateHint()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun updateBoard() {
        boardView.digits = currentBoard.copyOf()
        boardView.selected = selectedCage.toSet()
        boardView.cages = if (killer) cages.toList() else emptyList()
        boardView.hintCell = hint
        boardView.invalidate()
    }

    private fun updateHint() {
        val result = SudokuSolver.solve(currentBoard, killer, cages)
        hint = -1
        boardView.solution = if (result.solutionCount == 1) result.solution?.copyOf() else null
        if (result.solutionCount == 1 && result.solution != null) {
            hint = currentBoard.indices.firstOrNull { currentBoard[it] == 0 } ?: -1
            answer.text = if (hint >= 0) {
                val r = hint / 6 + 1
                val c = hint % 6 + 1
                "СТРОКА $r · СТОЛБЕЦ $c → ${result.solution[hint]}"
            } else "Поле решено!"
            status.text = if (hint >= 0) "Есть единственное решение — подсказка готова" else "Все клетки заполнены"
        } else {
            answer.text = if (killer && cages.sumOf { it.cells.size } < 36) "Killer: укажи все группы" else "Подсказка пока недоступна"
            status.text = result.error ?: "Проверь распознанные цифры"
        }
        cameraGuide.hintCell = hint
        boardView.hintCell = hint
        boardView.invalidate()
    }
}
