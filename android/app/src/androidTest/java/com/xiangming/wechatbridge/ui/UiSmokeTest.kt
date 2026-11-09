package com.xiangming.wechatbridge.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.content.res.Configuration
import android.content.res.Resources
import android.os.ParcelFileDescriptor
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.bridge
import com.xiangming.wechatbridge.core.Lang
import org.junit.Assert.assertEquals
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
        assertEquals("en", Resources.getSystem().configuration.locales[0].language)
        assertEquals(Lang.ZH, ui.activity.bridge.lang())
        assertEquals("微信流", ui.activity.getString(R.string.app_name))
        ui.onNodeWithText("首页").assertIsDisplayed()
        File(outputDirectory(), "language-check.txt").writeText(
            "system=${Resources.getSystem().configuration.locales[0]}\ninitialAppLocale=${ui.activity.resources.configuration.locales[0]}\ninitialPreference=${ui.activity.bridge.lang()}\ninitialAppName=${ui.activity.getString(R.string.app_name)}\n",
        )
        exportCapture("language-check.txt")
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
        ui.onNodeWithText("小陈：我们周五发布安卓版更新。", substring = true).assertExists()
        screenshot("04-preview-light", dark = false)

        shell("cmd uimode night yes")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
        waitFor("send-form") // The selected batch and draft survive activity recreation.
        ui.onNodeWithTag("send-confirm").assertIsDisplayed()
        ui.onNodeWithText("小陈：我们周五发布安卓版更新。", substring = true).assertExists()
        screenshot("05-send-dark", dark = true)
        ui.onNodeWithTag("send-confirm").performClick() // Default destination: clipboard.
        waitFor("home")
        screenshot("06-home-dark", dark = true)
        ui.onNodeWithTag("nav-history").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("已复制").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("已复制").assertIsDisplayed()
        screenshot("07-history-dark", dark = true)

        ui.onNodeWithTag("nav-scenes").performClick()
        ui.onNodeWithText("新建场景").performClick()
        ui.onNodeWithText("名称").performTextInput("发布纪要")
        ui.onNodeWithText("指令").performTextInput("总结讨论结论与后续行动。")
        Espresso.pressBack()
        ui.onNodeWithText("保存").performScrollTo().performClick()
        ui.waitUntil(10_000) {
            runCatching { ui.onNodeWithTag("scenes-list").performScrollToNode(hasText("发布纪要")) }.isSuccess
        }
        ui.onNodeWithText("发布纪要").assertIsDisplayed()
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
        ui.onNodeWithText("沉淀位置").assertIsDisplayed()
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("paste-entry").assertIsDisplayed()
        screenshot("10-motion-disabled-home", dark = true)
        shell("settings put global animator_duration_scale 1")
        shell("dumpsys gfxinfo ${instrumentation.targetContext.packageName}", "gfxinfo.txt")
        shell("settings put system font_scale 1.0")
        shell("wm size reset")
        shell("wm density reset")
        shell("cmd uimode night no")

        // English is available only when explicitly selected. Restore Chinese before final captures.
        ui.onNodeWithTag("nav-settings").performClick()
        ui.onNodeWithText("选择应用语言").performScrollTo().performClick()
        screenshot("11-language-picker-chinese", dark = false)
        ui.onNodeWithText("English").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Saving notes").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Lang.EN, ui.activity.bridge.lang())
        assertEquals("en", ui.activity.resources.configuration.locales[0].language)
        ui.onNodeWithTag("nav-scenes").performClick()
        ui.waitUntil(10_000) {
            runCatching { ui.onNodeWithTag("scenes-list").performScrollToNode(hasText("Group chat summary")) }.isSuccess
        }
        ui.onNodeWithTag("nav-settings").performClick()
        ui.onNodeWithText("Choose app language").performScrollTo().performClick()
        ui.onNodeWithText("简体中文").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("沉淀位置").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Lang.ZH, ui.activity.bridge.lang())
        ui.onNodeWithTag("nav-home").performClick()
        waitFor("home")
        ui.onNodeWithText("首页").assertIsDisplayed()
        screenshot("12-chinese-restored-home", dark = false)
        File(outputDirectory(), "language-check.txt").appendText("explicitEnglishVerified=true\nrestoredChineseVerified=true\n")
        exportCapture("language-check.txt")
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
        const val CHAT = "小陈：我们周五发布安卓版更新。\n小林：我来检查分享流程，并整理发布说明。\n小陈：请把讨论结论和后续行动一起发出来。"
    }
}