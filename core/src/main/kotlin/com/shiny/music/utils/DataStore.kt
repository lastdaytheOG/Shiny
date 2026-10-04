package com.shiny.music.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.shiny.music.extensions.toEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The value stored under [key], or null when there is none or when what is stored is not a [T].
 *
 * A key can hold a value of another type after a backup from a different version is restored.
 * Reading it then must not crash the screen that asked, so the type is checked here.
 */
inline fun <reified T : Any> Preferences.getOrNull(key: Preferences.Key<T>): T? =
    try {
        (this[key] as Any?) as? T
    } catch (_: ClassCastException) {
        null
    }

/** One blocking read of [key]. Null when it is absent or the store cannot be read; never throws. */
operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    try {
        runBlocking(Dispatchers.IO) { data.first()[key] }
    } catch (_: Exception) {
        null
    }

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = this[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }
