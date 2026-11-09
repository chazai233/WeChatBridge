package com.xiangming.wechatbridge.ui

import android.graphics.Bitmap
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
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
        screenshot("01-home-light")

        ui.onNodeWithTag("paste-entry").performClick()
        ui.onNodeWithTag("paste-input").performClick().performTextInput(CHAT)
        ui.onNodeWithTag("paste-confirm").performScrollTo().assertIsDisplayed().assertIsEnabled()
        screenshot("02-paste-keyboard-light")
        Espresso.pressBack() // First back dismisses the native keyboard.
        ui.onNodeWithTag("paste-confirm").performScrollTo().performClick()
        waitFor("send-form")
        ui.onNodeWithTag("send-confirm").assertIsDisplayed().assertIsEnabled()
        screenshot("03-send-light")
        ui.onNodeWithTag("preview-toggle").performScrollTo().performClick()
        ui.onNodeWithText("Alex: Let us ship the Android update on Friday.", substring = true).assertExists()
        screenshot("04-preview-light")

        shell("cmd uimode night yes")
        ui.waitUntil(10_000) { ui.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
        waitFor("send-form") // The selected batch and draft survive activity recreation.
        ui.onNodeWithTag("send-confirm").assertIsDisplayed()
        screenshot("05-send-dark")
        ui.onNodeWithTag("send-confirm").performClick() // Default destination: clipboard.
        waitFor("home")
        screenshot("06-home-dark")
        ui.onNodeWithTag("nav-history").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Copied").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Copied").assertIsDisplayed()
        screenshot("07-history-dark")

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
        screenshot("08-scenes-dark")

        // 320 dp wide, with larger text, to catch clipped actions and IME overlap.
        shell("wm size 640x1280")
        shell("wm density 320")
        shell("settings put system font_scale 1.3")
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("paste-entry").performClick()
        ui.onNodeWithTag("paste-input").performTextReplacement(CHAT)
        ui.onNodeWithTag("paste-input").performClick()
        ui.onNodeWithTag("paste-confirm").performScrollTo().assertIsDisplayed().assertIsEnabled()
        screenshot("09-compact-keyboard-dark")
        Espresso.pressBack()
        Espresso.pressBack() // Next back dismisses the sheet and leaves Home usable.
        waitFor("home")
        ui.onNodeWithTag("paste-entry").assertIsDisplayed()
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

    private fun screenshot(name: String) {
        ui.waitForIdle()
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