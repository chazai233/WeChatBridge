package com.xiangming.wechatbridge.core

import org.junit.Assert.*
import org.junit.Test

class SceneLanguageTest {
    @Test fun untouchedStartersFollowTheExplicitLanguageAndKeepTheDefault() {
        val english = SceneDefaults.library(Lang.EN)
        val chinese = english.localizingUneditedStarters(Lang.ZH)
        assertEquals(SceneDefaults.library(Lang.ZH), chinese)
        assertEquals(english, chinese.localizingUneditedStarters(Lang.EN))
    }

    @Test fun editedCustomAndDeletedScenesRemainUnchanged() {
        val english = SceneDefaults.library(Lang.EN)
        val edited = english.scenes.first().copy(instruction = "My own instructions")
        val custom = Scene(name = "My custom scene", instruction = "Keep the original language")
        val stored = english.upsert(edited).upsert(custom).remove(SceneDefaults.DAILY_SUMMARY)
        val chinese = stored.localizingUneditedStarters(Lang.ZH)
        assertEquals(edited, chinese.scene(edited.id))
        assertEquals(custom, chinese.scene(custom.id))
        assertNull(chinese.scene(SceneDefaults.DAILY_SUMMARY))
        assertEquals(stored.defaultSceneId, chinese.defaultSceneId)
    }
}