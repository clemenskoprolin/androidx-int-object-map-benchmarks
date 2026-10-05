/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(
    androidx.compose.runtime.InternalComposeApi::class,
    kotlin.js.ExperimentalWasmJsInterop::class,
)
@file:Suppress("DEPRECATION")

package androidx.compose.runtime

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import kotlinx.coroutines.withTimeout

// Hold native frames while allowing event tasks to run. Restore the global in finally.
private fun holdFrames(): Unit =
    js(
        """(() => {
    globalThis.__frameTestOriginal = window.requestAnimationFrame;
    globalThis.__frameTestQueue = [];
    window.requestAnimationFrame = callback => { globalThis.__frameTestQueue.push(callback); return 1; };
})()"""
    )

private fun releaseFrame(): Unit =
    js(
        """(() => {
    const callbacks = globalThis.__frameTestQueue.splice(0);
    callbacks.forEach(callback => callback(performance.now()));
})()"""
    )

private fun restoreFrames(): Unit =
    js(
        """(() => {
    window.requestAnimationFrame = globalThis.__frameTestOriginal;
    delete globalThis.__frameTestOriginal;
    delete globalThis.__frameTestQueue;
})()"""
    )

class FrameEndSchedulingTest {
    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) {}

        override fun insertBottomUp(index: Int, instance: Unit) {}

        override fun remove(index: Int, count: Int) {}

        override fun move(from: Int, to: Int, count: Int) {}

        override fun onClear() {}
    }

    // Holds browser frames to verify that the default policy delays updates until a frame arrives.
    @Test
    fun defaultPolicyWaitsForAnAnimationFrame() =
        MainScope().promise {
            val scope = CoroutineScope(Dispatchers.Unconfined + DefaultMonotonicFrameClock)
            val recomposer = Recomposer(scope.coroutineContext)
            val composition = Composition(EmptyApplier(), recomposer)
            val state = mutableStateOf(0)
            var rendered = -1
            holdFrames()
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect { rendered = value }
                }
                state.value = 1
                Snapshot.sendApplyNotifications()
                delay(30)
                assertEquals(0, rendered)
                releaseFrame()
                withTimeout(2000) { while (rendered != 1) delay(1) }
            } finally {
                restoreFrames()
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }

    // Checks that multiple changes in one event turn produce one early update with the latest
    // value.
    @Test
    fun changesInOneTurnCoalesceBeforeAnEarlyPass() =
        MainScope().promise {
            val scope = CoroutineScope(Dispatchers.Default + DefaultMonotonicFrameClock)
            val recomposer =
                Recomposer(scope.coroutineContext + RecompositionFramePolicy.EventCompletion)
            val composition = Composition(EmptyApplier(), recomposer)
            val state = mutableStateOf(0)
            val rendered = mutableListOf<Int>()
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            holdFrames()
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect { rendered += value }
                }
                state.value = 1
                Snapshot.sendApplyNotifications()
                state.value = 2
                Snapshot.sendApplyNotifications()
                assertEquals(listOf(0), rendered)
                withTimeout(2000) { while (rendered.last() != 2) delay(1) }
                assertEquals(listOf(0, 2), rendered)
            } finally {
                restoreFrames()
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }

    // Cancels a pending event-completion timer and checks that its coroutine does not resume.
    @Test
    fun cancelledEventCompletionDoesNotResumeWork() =
        MainScope().promise {
            var resumed = false
            val waiter =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    awaitBrowserEventCompletion()
                    resumed = true
                }
            waiter.cancelAndJoin()
            delay(30)
            assertFalse(resumed)
        }

    // Checks that a frame-end callback queued by an early update waits for a real browser frame.
    @Test
    fun callbackAddedByEarlyRecompositionWaitsForAnActualFrame() =
        MainScope().promise {
            val scope = CoroutineScope(Dispatchers.Default + DefaultMonotonicFrameClock)
            val recomposer =
                Recomposer(scope.coroutineContext + RecompositionFramePolicy.EventCompletion)
            val composition = Composition(EmptyApplier(), recomposer)
            val state = mutableStateOf(0)
            var rendered = -1
            var callbackRan = false
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            holdFrames()
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect {
                        rendered = value
                        if (value == 1) recomposer.scheduleFrameEndCallback { callbackRan = true }
                    }
                }
                state.value = 1
                Snapshot.sendApplyNotifications()
                withTimeout(2000) { while (rendered != 1) delay(1) }
                // Recomposition happened despite held rAF callbacks; frame-end must remain pending.
                assertFalse(callbackRan)
                delay(30)
                assertFalse(callbackRan)
                releaseFrame()
                withTimeout(2000) { while (!callbackRan) delay(1) }
            } finally {
                restoreFrames()
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }

    // Checks that an existing frame-end callback makes both the update and callback wait for a
    // frame.
    @Test
    fun queuedFrameEndCallbackPreventsAnEarlyPass() =
        MainScope().promise {
            val scope = CoroutineScope(Dispatchers.Default + DefaultMonotonicFrameClock)
            val recomposer =
                Recomposer(scope.coroutineContext + RecompositionFramePolicy.EventCompletion)
            val composition = Composition(EmptyApplier(), recomposer)
            val state = mutableStateOf(0)
            var rendered = -1
            var callbackRan = false
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            holdFrames()
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect { rendered = value }
                }
                recomposer.scheduleFrameEndCallback { callbackRan = true }
                state.value = 1
                Snapshot.sendApplyNotifications()
                delay(30)
                assertEquals(0, rendered)
                assertFalse(callbackRan)
                releaseFrame()
                withTimeout(2000) { while (!callbackRan) delay(1) }
                assertEquals(1, rendered)
            } finally {
                restoreFrames()
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }

    // Registers an animation while the timer is pending and checks that the update waits for a
    // frame.
    @Test
    fun animationRegisteredDuringEventCompletionPreventsEarlyRecomposition() =
        MainScope().promise {
            val scope = CoroutineScope(Dispatchers.Unconfined + DefaultMonotonicFrameClock)
            val recomposer =
                Recomposer(scope.coroutineContext + RecompositionFramePolicy.EventCompletion)
            val composition = Composition(EmptyApplier(), recomposer)
            val state = mutableStateOf(0)
            var rendered = -1
            val timestamp = CompletableDeferred<Long>()
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            holdFrames()
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect { rendered = value }
                }
                state.value = 1
                Snapshot.sendApplyNotifications()
                // Unconfined resumes the runner inline, so it is now suspended in event completion.
                scope.launch(
                    recomposer.effectCoroutineContext,
                    start = CoroutineStart.UNDISPATCHED,
                ) {
                    timestamp.complete(withFrameNanos { it })
                }
                delay(30)
                assertEquals(0, rendered)
                assertFalse(timestamp.isCompleted)
                releaseFrame()
                withTimeout(2000) { while (rendered != 1) delay(1) }
                assertTrue(timestamp.await() > 0L)
            } finally {
                restoreFrames()
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }

    // Queues a frame-end callback while the timer is pending and checks that it prevents an early
    // pass.
    @Test
    fun frameEndCallbackRegisteredDuringEventCompletionPreventsEarlyRecomposition() =
        MainScope().promise {
            val scope = CoroutineScope(Dispatchers.Unconfined + DefaultMonotonicFrameClock)
            val recomposer =
                Recomposer(scope.coroutineContext + RecompositionFramePolicy.EventCompletion)
            val composition = Composition(EmptyApplier(), recomposer)
            val state = mutableStateOf(0)
            var rendered = -1
            var completed = false
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            holdFrames()
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect { rendered = value }
                }
                state.value = 1
                Snapshot.sendApplyNotifications()
                recomposer.scheduleFrameEndCallback { completed = true }
                delay(30)
                assertEquals(0, rendered)
                assertFalse(completed)
                releaseFrame()
                withTimeout(2000) { while (!completed) delay(1) }
                assertEquals(1, rendered)
            } finally {
                restoreFrames()
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }

    // Cancels the recomposer while its event-completion timer is pending and checks that no update
    // runs.
    @Test
    fun cancellingTheRecomposerDuringEventCompletionDoesNotApplyChanges() =
        MainScope().promise {
            val scope = CoroutineScope(Dispatchers.Unconfined + DefaultMonotonicFrameClock)
            val recomposer =
                Recomposer(scope.coroutineContext + RecompositionFramePolicy.EventCompletion)
            val composition = Composition(EmptyApplier(), recomposer)
            val state = mutableStateOf(0)
            var rendered = -1
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            holdFrames()
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect { rendered = value }
                }
                state.value = 1
                Snapshot.sendApplyNotifications()
                recomposer.cancel()
                runner.cancelAndJoin()
                delay(30)
                assertEquals(0, rendered)
            } finally {
                restoreFrames()
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }
}
