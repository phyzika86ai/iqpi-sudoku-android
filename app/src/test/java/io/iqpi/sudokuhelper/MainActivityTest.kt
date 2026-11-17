package io.iqpi.sudokuhelper

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    @Before fun start() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
    }
    @After fun stop() { controller.pause().stop().destroy() }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun views() = descendants(activity.findViewById(android.R.id.content))
    private fun button(label: String) = views().filterIsInstance<Button>().single { it.text.toString() == label }
    private fun board() = views().filterIsInstance<SudokuBoardView>().single()
    private fun demo() { button("Проверить на примере со скриншота").performClick() }

    @Test fun startsAndSolvesDemoWithoutCameraPermission() {
        demo()
        assertEquals(5, board().solution!![0])
        assertEquals(0, board().hintCell)
        assertEquals("Продолжить сканирование", button("Продолжить сканирование").text.toString())
    }

    @Test fun switchingBackToClassicIgnoresSavedKillerCage() {
        demo()
        button("Режим: классика").performClick()
        assertNull(board().solution)
        button("Killer: добавить группу").performClick()
        board().onCellTapped!!.invoke(0)
        button("Закончить группу").performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        descendants(dialog.window!!.decorView).filterIsInstance<EditText>().single().setText("1")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        button("Режим: Killer").performClick()
        assertEquals(5, board().solution!![0])
        assertTrue(board().cages.isEmpty())
    }

    @Test fun demoResetsKillerControlsAndUncertainty() {
        button("Режим: классика").performClick()
        board().uncertainty = setOf(0)
        demo()
        assertEquals(View.GONE, button("Killer: добавить группу").visibility)
        assertEquals(View.GONE, button("Сбросить группы").visibility)
        assertTrue(board().uncertainty.isEmpty())
        assertNotNull(board().solution)
    }

    @Test fun modeChangesCannotBypassUncertainDigits() {
        demo()
        board().uncertainty = setOf(0, 1)
        button("Режим: классика").performClick()
        button("Режим: Killer").performClick()
        assertNull(board().solution)
        assertEquals(-1, board().hintCell)
    }
}
