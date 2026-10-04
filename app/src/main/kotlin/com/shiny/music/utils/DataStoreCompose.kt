package com.shiny.music.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.shiny.music.extensions.toEnum
import kotlinx.coroutines.launch

/**
 * Observes [key], writing through to DataStore on assignment.
 *
 * Reads come from [PreferencesSnapshot] — one collector for the whole app — instead of
 * a per-key `dataStore.data` collector plus a blocking `runBlocking` read of the initial
 * value on every recomposition. Nothing on this path touches the disk from the main
 * thread; before the file has been read the snapshot yields [defaultValue] and the
 * composable recomposes once when the real value arrives.
 *
 * The `derivedStateOf` is what keeps one preference write from recomposing every reader
 * of every *other* preference: it recomputes whenever the snapshot changes, but only
 * notifies its own readers when this key's value actually changed.
 */
@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state = remember(key, defaultValue) {
        derivedStateOf { PreferencesSnapshot.read(key, defaultValue) }
    }

    return remember(key, state, coroutineScope, context) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

/**
 * The enum flavour of [rememberPreference]. Decodes the stored name on read and stores
 * [Enum.name] on write, exactly as before; only the source of the value has changed.
 */
@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state = remember(key, defaultValue) {
        derivedStateOf { PreferencesSnapshot.readString(key).toEnum(defaultValue = defaultValue) }
    }

    return remember(key, state, coroutineScope, context) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
