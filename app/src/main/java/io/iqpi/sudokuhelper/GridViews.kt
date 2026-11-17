package io.iqpi.sudokuhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min

class SquareFrame(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val exact = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        super.onMeasure(exact, exact)
    }
}

class CameraGuide(context: Context) : View(context) {
    var hintCell = -1
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val length = min(width, height).toFloat()
        val left = (width - length * 0.90f) / 2
        val top = (height - length * 0.90f) / 2
        val unit = length * 0.90f / 6
        paint.style = Paint.Style.STROKE
        for (i in 0..6) {
            paint.strokeWidth = if (i % 3 == 0) 3.8f else 1.2f
            paint.color = if (i % 3 == 0) Color.rgb(51, 216, 203) else 0xAAFFFFFF.toInt()
            canvas.drawLine(left + i * unit, top, left + i * unit, top + 6 * unit, paint)
            paint.strokeWidth = if (i % 2 == 0) 3.8f else 1.2f
            paint.color = if (i % 2 == 0) Color.rgb(51, 216, 203) else 0xAAFFFFFF.toInt()
            canvas.drawLine(left, top + i * unit, left + 6 * unit, top + i * unit, paint)
        }
        if (hintCell in 0..35) {
            val r = hintCell / 6; val c = hintCell % 6
            paint.style = Paint.Style.FILL
            paint.color = 0x6633D8CB
            canvas.drawRoundRect(RectF(left+c*unit, top+r*unit, left+(c+1)*unit, top+(r+1)*unit), 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = 0xFF33D8CB.toInt()
            canvas.drawRect(left+c*unit, top+r*unit, left+(c+1)*unit, top+(r+1)*unit, paint)
        }
    }
}

class SudokuBoardView(context: Context) : View(context) {
    var digits: IntArray = IntArray(36)
    var solution: IntArray? = null
    var uncertainty: Set<Int> = emptySet()
    var hintCell: Int = -1
    var selected: Set<Int> = emptySet()
    var cages: List<SudokuSolver.Cage> = emptyList()
    var onCellTapped: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val s = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(s, s)
    }
    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val cell = s / 6
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(17, 36, 52)
        canvas.drawRect(0f,0f,s,s,paint)
        for (i in 0..35) {
            val x=(i%6)*cell; val y=(i/6)*cell
            paint.color= when {
                i in selected -> 0xFF2A6094.toInt()
                i==hintCell -> 0xFF126C69.toInt()
                i in uncertainty -> 0xFF744436.toInt()
                else -> if (((i/6)/2+(i%6)/3)%2==0) 0xFF152B3C.toInt() else 0xFF102231.toInt()
            }
            canvas.drawRect(x,y,x+cell,y+cell,paint)
            val displayed = if (digits[i] != 0) digits[i] else solution?.get(i) ?: 0
            if (displayed != 0) {
                paint.color=if (digits[i] != 0) Color.WHITE else 0xFF33D8CB.toInt()
                paint.textAlign=Paint.Align.CENTER
                paint.textSize=cell*.56f
                paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
                canvas.drawText(displayed.toString(),x+cell/2,y+cell*.70f,paint)
            }
        }
        cages.forEachIndexed { idx, cage ->
            paint.style=Paint.Style.STROKE
            paint.strokeWidth=3f
            paint.color= Color.HSVToColor(floatArrayOf(((idx*53)%360).toFloat(),0.6f,0.95f))
            for (i in cage.cells) {
                val x=(i%6)*cell; val y=(i/6)*cell
                canvas.drawRoundRect(x+4,y+4,x+cell-4,y+cell-4,8f,8f,paint)
            }
            val first=cage.cells.minOrNull() ?: 0
            paint.style=Paint.Style.FILL
            paint.textAlign=Paint.Align.LEFT
            paint.textSize=cell*.19f
            paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(cage.sum.toString(),(first%6)*cell+8,(first/6)*cell+17,paint)
        }
        paint.style=Paint.Style.STROKE
        for (i in 0..6) {
            paint.strokeWidth= if (i%3==0) 4f else 1f
            paint.color=Color.rgb(127,160,177)
            canvas.drawLine(i*cell,0f,i*cell,s,paint)
            paint.strokeWidth= if (i%2==0) 4f else 1f
            canvas.drawLine(0f,i*cell,s,i*cell,paint)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val cell=width.toFloat()/6
        val r=(event.y/cell).toInt().coerceIn(0,5)
        val c=(event.x/cell).toInt().coerceIn(0,5)
        onCellTapped?.invoke(r*6+c)
        return true
    }
}
