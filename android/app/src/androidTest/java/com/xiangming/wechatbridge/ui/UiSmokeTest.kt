package com.xiangming.wechatbridge.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises real navigation, IME, sheet dismissal and clipboard delivery on an emulator. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun pastePreviewDeliverAndManageScenes() {
        shell("cmd uimode night no")
        waitFor("home")
        screenshot("01-home-light", dark = false)

        ui.onNodeWithTag("paste-entry").performClick()
        ui.onNodeWithTag("paste-input").performTouchInput { click() }
        ui.onNodeWithTag("paste-input").performTextInput(CHAT)
        waitForKeyboard()
        ui.onNodeWithTag("paste-confirm").performScrollTo().assertIsDisplayed().assertIsEnabled()
        screenshot("02-paste-keyboard-light", dark = false)
        Espresso.pressBack() // First back dismisses the native keyboard.
        ui.onNodeWithTag("paste-confirm").performScrollTo().performClick()
        waitFor("send-form")
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("logo-04-chatgpt.png", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("send-confirm").assertIsDisplayed().assertIsEnabled()
        screenshot("03-send-light", dark = false)
        ui.onNodeWithTag("preview-toggle").performScrollTo().performClick()
        ui.onNodeWithText("Alex: Let us ship the Android update on Friday.", substring = true).assertExists()
        screenshot("04-preview-light", dark = false)

        shell("cmd uimode night yes")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
        waitFor("send-form") // The selected batch and draft survive activity recreation.
        ui.onNodeWithTag("send-confirm").assertIsDisplayed()
        ui.onNodeWithText("Alex: Let us ship the Android update on Friday.", substring = true).assertExists()
        screenshot("05-send-dark", dark = true)
        ui.onNodeWithTag("send-confirm").performClick() // Default destination: clipboard.
        waitFor("home")
        screenshot("06-home-dark", dark = true)
        ui.onNodeWithTag("nav-history").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Copied").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Copied").assertIsDisplayed()
        screenshot("07-history-dark", dark = true)

        ui.onNodeWithTag("nav-scenes").performClick()
        ui.onNodeWithText("New scene").performClick()
        ui.onNodeWithText("Name").performTextInput("Release notes")
        ui.onNodeWithText("Instruction").performTextInput("Summarize the decisions and next steps.")
        Espresso.pressBack()
        ui.onNodeWithText("Save").performScrollTo().performClick()
        ui.waitUntil(10_000) {
            runCatching { ui.onNodeWithTag("scenes-list").performScrollToNode(hasText("Release notes")) }.isSuccess
        }
        ui.onNodeWithText("Release notes").assertIsDisplayed()
        screenshot("08-scenes-dark", dark = true)

        // 320 dp wide, with larger text, to catch clipped actions and IME overlap.
        shell("wm size 640x1280")
        shell("wm density 320")
        shell("settings put system font_scale 1.3")
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("paste-entry").performClick()
        ui.onNodeWithTag("paste-input").performTextReplacement(CHAT)
        ui.onNodeWithTag("paste-input").performTouchInput { click() }
        waitForKeyboard()
        ui.onNodeWithTag("paste-confirm").performScrollTo().assertIsDisplayed().assertIsEnabled()
        screenshot("09-compact-keyboard-dark", dark = true)
        Espresso.pressBack()
        Espresso.pressBack() // Next back dismisses the sheet and leaves Home usable.
        waitFor("home")
        ui.onNodeWithTag("paste-input").assertDoesNotExist()
        ui.onNodeWithTag("paste-entry").assertIsDisplayed()
        shell("settings put global animator_duration_scale 0")
        ui.onNodeWithTag("nav-settings").performClick()
        ui.onNodeWithText("Saving notes").assertIsDisplayed()
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("paste-entry").assertIsDisplayed()
        screenshot("10-motion-disabled-home", dark = true)
        shell("settings put global animator_duration_scale 1")
        shell("dumpsys gfxinfo ${instrumentation.targetContext.packageName}", "gfxinfo.txt")
        shell("settings put system font_scale 1.0")
        shell("wm size reset")
        shell("wm density reset")
        shell("cmd uimode night no")
    }

    private fun waitFor(tag: String) {
        ui.waitUntil(15_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }
        ui.waitForIdle()
    }

    private fun waitForKeyboard() {
        ui.waitUntil(10_000) {
            var visible = false
            instrumentation.runOnMainSync {
                visible = WindowInspector.getGlobalWindowViews().any {
                    it.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
                }
            }
            visible
        }
    }

    private fun screenshot(name: String, dark: Boolean) {
        ui.waitForIdle()
        // Configuration alone can update before the actual frame. Check the empty toolbar pixel.
        ui.waitUntil(10_000) {
            val frame = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val level = Color.red(frame.getPixel(frame.width - 24, frame.height / 12))
            frame.recycle()
            if (dark) level < 80 else level > 140
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(outputDirectory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        exportCapture("$name.png")
    }

    private fun outputDirectory() = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    private fun shell(command: String, outputName: String? = null) {
        val output = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        if (outputName != null) {
            File(outputDirectory(), outputName).writeBytes(output)
            exportCapture(outputName)
        }
        instrumentation.waitForIdleSync()
    }

    // Downloads survives AGP uninstalling the test app at the end of connected tests.
    private fun exportCapture(name: String) {
        shell("mkdir -p /sdcard/Download/wechatbridge-ui")
        shell("cp ${File(outputDirectory(), name).absolutePath} /sdcard/Download/wechatbridge-ui/$name")
    }

    private companion object {
        const val CHAT = "Alex: Let us ship the Android update on Friday.\nSam: I will check the sharing flow and send the release notes.\nAlex: Please include the decisions and next steps."
    }
}