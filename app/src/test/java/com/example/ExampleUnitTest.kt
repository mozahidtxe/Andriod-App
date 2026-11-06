package com.example

import com.example.model.ReadingTheme
import com.example.model.TranslationStyle
import com.example.model.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun translationStyles_haveValidPrompts() {
        TranslationStyle.values().forEach { style ->
            assertNotNull(style.promptInstruction)
            assertNotNull(style.titleBn)
            assertNotNull(style.descriptionBn)
        }
    }

    @Test
    fun readingThemes_haveBengaliNames() {
        ReadingTheme.values().forEach { theme ->
            assertNotNull(theme.displayNameBn)
            assertNotNull(theme.displayNameEn)
        }
    }

    @Test
    fun viewModes_exist() {
        assertEquals(3, ViewMode.values().size)
    }
}
