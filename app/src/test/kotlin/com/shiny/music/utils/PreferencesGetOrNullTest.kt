package com.shiny.music.utils

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesGetOrNullTest {
    @Test
    fun `a value of the right type is returned`() {
        val prefs = preferencesOf(
            stringPreferencesKey("name") to "shiny",
            booleanPreferencesKey("on") to true,
            intPreferencesKey("count") to 3,
            stringSetPreferencesKey("tags") to setOf("a", "b"),
        )
        assertEquals("shiny", prefs.getOrNull(stringPreferencesKey("name")))
        assertEquals(true, prefs.getOrNull(booleanPreferencesKey("on")))
        assertEquals(3, prefs.getOrNull(intPreferencesKey("count")))
        assertEquals(setOf("a", "b"), prefs.getOrNull(stringSetPreferencesKey("tags")))
    }

    @Test
    fun `a missing key gives null`() {
        assertNull(preferencesOf().getOrNull(intPreferencesKey("missing")))
    }

    @Test
    fun `a value stored under the same name with another type gives null instead of throwing`() {
        val prefs = preferencesOf(
            stringPreferencesKey("on") to "yes",
            intPreferencesKey("size") to 512,
            booleanPreferencesKey("name") to false,
        )
        assertNull(prefs.getOrNull(booleanPreferencesKey("on")))
        assertNull(prefs.getOrNull(longPreferencesKey("size")))
        assertNull(prefs.getOrNull(floatPreferencesKey("size")))
        assertNull(prefs.getOrNull(stringPreferencesKey("name")))
        // The fallback a caller writes after it still applies.
        assertEquals(false, prefs.getOrNull(booleanPreferencesKey("on")) ?: false)
        assertEquals(1024L, prefs.getOrNull(longPreferencesKey("size")) ?: 1024L)
    }
}
