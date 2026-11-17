package io.iqpi.sudokuhelper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DigitRecognizerTest {
    private val expected = intArrayOf(
        0,3,0,4,1,0, 4,1,2,0,0,0, 0,5,0,3,0,0,
        0,0,3,0,2,0, 0,0,0,2,0,1, 0,2,4,0,3,0)
    private fun recognizer() = DigitRecognizer(RuntimeEnvironment.getApplication())

    @Test fun actualAndroidRecognizerReadsScreenshotAtFiveResolutions() {
        val source = javaClass.classLoader!!.getResourceAsStream("sample_grid_6x6.png")!!.use {
            BitmapFactory.decodeStream(it)!!
        }
        val reader = recognizer()
        for (size in listOf(730,540,400,320,270)) {
            val frame = Bitmap.createScaledBitmap(source, size, size, true)
            val scan = reader.read(frame)
            assertArrayEquals("Grid size $size", expected, scan.digits)
            assertTrue("Unexpected uncertainty at $size: ${scan.uncertain}", scan.uncertain.isEmpty())
            if (frame !== source) frame.recycle()
        }
        source.recycle()
    }

    @Test fun allSixDigitTemplatesAreRecognizedInEveryRow() {
        val frame = Bitmap.createBitmap(720,720,Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.rgb(17,36,52))
        val canvas = Canvas(frame)
        for (digit in 1..6) {
            val template = RuntimeEnvironment.getApplication().assets.open("digits/$digit.png").use {
                BitmapFactory.decodeStream(it)!!
            }
            val scaled = Bitmap.createScaledBitmap(template,64,64,false)
            for (row in 0..5) canvas.drawBitmap(scaled, ((digit-1)*120+28).toFloat(), (row*120+28).toFloat(), null)
            scaled.recycle(); template.recycle()
        }
        val scan = recognizer().read(frame)
        assertArrayEquals(IntArray(36) { it%6+1 }, scan.digits)
        assertTrue(scan.uncertain.isEmpty())
        frame.recycle()
    }

    @Test fun blankGridAndSmallCornerTotalsAreIgnored() {
        val frame = Bitmap.createBitmap(720,720,Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.rgb(17,36,52))
        val canvas = Canvas(frame)
        val paint = Paint().apply { color=Color.WHITE; textSize=12f }
        for (row in 0..5) for (col in 0..5) canvas.drawText("12", col*120+3f,row*120+14f,paint)
        val scan = recognizer().read(frame)
        assertEquals(0,scan.found)
        assertTrue(scan.uncertain.isEmpty())
        frame.recycle()
    }

    @Test fun unrecognizedBrightShapeIsFlaggedForCorrection() {
        val frame = Bitmap.createBitmap(720,720,Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.BLACK)
        Canvas(frame).drawRect(40f,40f,80f,80f,Paint().apply { color=Color.WHITE })
        assertTrue(0 in recognizer().read(frame).uncertain)
        frame.recycle()
    }
}
