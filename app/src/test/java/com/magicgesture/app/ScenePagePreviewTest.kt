package com.magicgesture.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the scene management page on the JVM and writes PNGs to app/build/previews so layout,
 * copy, spacing and every state can be reviewed without installing anything on a phone.
 * Run: .\gradlew.bat testDebugUnitTest --tests "*ScenePagePreviewTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScenePagePreviewTest {

    @Test
    fun rendersEmptyState() {
        render(
            name = "scene-page-empty.png",
            view = page(
                ScenePageState(
                    autoSwitchEnabled = true,
                    currentPackage = null,
                    associations = emptyList()
                )
            )
        )
    }

    @Test
    fun rendersWithAssociations() {
        render(
            name = "scene-page-associated.png",
            view = page(
                ScenePageState(
                    autoSwitchEnabled = true,
                    currentPackage = SHORT_VIDEO_PACKAGE,
                    associations = listOf(
                        SHORT_VIDEO_PACKAGE to GestureScene.SHORT_VIDEO,
                        MUSIC_PACKAGE to GestureScene.MUSIC
                    )
                )
            )
        )
    }

    @Test
    fun rendersWithAutoSwitchDisabled() {
        render(
            name = "scene-page-auto-switch-off.png",
            view = page(
                ScenePageState(
                    autoSwitchEnabled = false,
                    currentPackage = SHORT_VIDEO_PACKAGE,
                    associations = listOf(SHORT_VIDEO_PACKAGE to GestureScene.SHORT_VIDEO)
                )
            )
        )
    }

    @Test
    fun rendersSceneChoiceSheet() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        render(
            name = "scene-choice-sheet.png",
            view = ScenePageUi.buildSceneChoice(
                context = context,
                packageName = SHORT_VIDEO_PACKAGE,
                initial = GestureScene.SHORT_VIDEO,
                firstAssociation = true,
                callbacks = NoopSceneChoiceCallbacks
            )
        )
    }

    private fun page(state: ScenePageState): View {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        return ScenePageUi.build(context, state, NoopScenePageCallbacks)
    }

    private fun render(name: String, view: View, width: Int = 1080, height: Int = 2340) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        val renderedWidth = view.measuredWidth
        val renderedHeight = view.measuredHeight
        view.layout(0, 0, renderedWidth, renderedHeight)
        val bitmap = Bitmap.createBitmap(renderedWidth, renderedHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val target = File("build/previews").apply { mkdirs() }
        val file = File(target, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("preview $name must not be empty", file.length() > 0L)
    }

    private object NoopScenePageCallbacks : ScenePageCallbacks {
        override fun onBack() = Unit
        override fun onAutoSwitchChanged(enabled: Boolean) = Unit
        override fun onSelectScene(scene: GestureScene) = Unit
        override fun onEditSceneGestures(scene: GestureScene) = Unit
        override fun onEditAssociation(packageName: String) = Unit
        override fun onAddAssociation() = Unit
        override fun onDeleteAssociation(packageName: String) = Unit
    }

    private object NoopSceneChoiceCallbacks : SceneChoiceCallbacks {
        override fun onTemporary(scene: GestureScene) = Unit
        override fun onSetDefault(scene: GestureScene) = Unit
        override fun onDismiss() = Unit
    }

    private companion object {
        const val SHORT_VIDEO_PACKAGE = "com.example.shortvideo"
        const val MUSIC_PACKAGE = "com.example.music"
    }
}
