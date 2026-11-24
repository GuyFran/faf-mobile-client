package com.faforever.mobile.ui.state

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull

/** Provided by MainActivity; every screen reads the UI state store through it. */
val LocalUiState = staticCompositionLocalOf<UiStatePrefs> {
    error("LocalUiState not provided")
}

/**
 * A [LazyListState] whose scroll position is saved under [key] (debounced, on stop and on
 * dispose) and restored once [ready] is true (i.e. once the list has its items).
 * Within a process (rotation, tab switch) Compose's own saveable state keeps it; the store
 * covers cold starts after process death.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberPersistedLazyListState(key: String, ready: Boolean = true): LazyListState {
    val store = LocalUiState.current
    val state = rememberSaveable(key, saver = LazyListState.Saver) { LazyListState() }
    var restored by rememberSaveable(key) { mutableStateOf(false) }

    LaunchedEffect(key, ready) {
        if (!restored && ready) {
            val (index, offset) = store.getScroll(key)
            if (index > 0 || offset > 0) {
                val last = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                state.scrollToItem(index.coerceAtMost(last), offset)
            }
            restored = true
        }
    }
    LaunchedEffect(key) {
        snapshotFlow {
            if (restored) state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset else null
        }
            .filterNotNull()
            .debounce(400)
            .collect { (i, o) -> store.putScroll(key, i, o) }
    }
    SaveOnStop(key) {
        if (restored) store.putScroll(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    }
    return state
}

/** A [ScrollState] (Column.verticalScroll) whose position is saved under [key] and restored. */
@OptIn(FlowPreview::class)
@Composable
fun rememberPersistedScrollState(key: String): ScrollState {
    val store = LocalUiState.current
    val state = rememberSaveable(key, saver = ScrollState.Saver) { ScrollState(0) }
    var restored by rememberSaveable(key) { mutableStateOf(false) }

    // Content may load asynchronously: scroll as far as the saved position once it is measured.
    LaunchedEffect(key) {
        if (restored) return@LaunchedEffect
        val target = store.getScroll(key).second
        if (target <= 0) {
            restored = true
            return@LaunchedEffect
        }
        snapshotFlow { state.maxValue }
            .debounce(150)
            .collect { max ->
                if (!restored && max > 0) {
                    state.scrollTo(target.coerceAtMost(max))
                    if (max >= target) restored = true
                }
            }
    }
    LaunchedEffect(key) {
        // Stop waiting if the content never grows to the saved position (data shrank, error...).
        delay(3000)
        restored = true
    }
    LaunchedEffect(key) {
        snapshotFlow { if (restored) state.value else null }
            .filterNotNull()
            .debounce(400)
            .collect { store.putScroll(key, 0, it) }
    }
    SaveOnStop(key) { if (restored) store.putScroll(key, 0, state.value) }
    return state
}

/** Runs [save] when the host lifecycle stops and when the composable leaves composition. */
@Composable
fun SaveOnStop(key: Any, save: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(key, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) save()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            save()
        }
    }
}
