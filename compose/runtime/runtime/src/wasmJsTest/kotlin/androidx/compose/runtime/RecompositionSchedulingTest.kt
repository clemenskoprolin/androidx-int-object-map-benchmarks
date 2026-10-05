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

@file:Suppress("DEPRECATION")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package androidx.compose.runtime

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import kotlinx.coroutines.withTimeout

class RecompositionSchedulingTest {
    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) {}

        override fun insertBottomUp(index: Int, instance: Unit) {}

        override fun remove(index: Int, count: Int) {}

        override fun move(from: Int, to: Int, count: Int) {}

        override fun onClear() {}
    }

    // Checks that an opted-in recomposer still uses a custom clock for updates and animation time.
    @Test
    fun aCustomClockControlsBothUpdatesAndAnimationTime() =
        MainScope().promise {
            val clock = BroadcastFrameClock()
            val scope = CoroutineScope(Dispatchers.Default + clock)
            val recomposer =
                Recomposer(scope.coroutineContext + RecompositionFramePolicy.EventCompletion)
            val composition = Composition(EmptyApplier(), recomposer)
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            val state = mutableStateOf(0)
            var rendered = -1
            val animation = CompletableDeferred<Long>()
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect { rendered = value }
                    LaunchedEffect(Unit) { animation.complete(withFrameNanos { it }) }
                }
                state.value = 1
                Snapshot.sendApplyNotifications()
                withTimeout(2000) { while (!clock.hasAwaiters) delay(1) }
                assertEquals(0, rendered)
                assertFalse(animation.isCompleted)
                clock.sendFrame(123456789L)
                withTimeout(2000) { while (rendered != 1) delay(1) }
                assertEquals(123456789L, animation.await())
            } finally {
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }

    // Checks that animations still receive increasing browser frame timestamps after opting in.
    @Test
    fun defaultClockAnimationsReceiveRealIncreasingFrameTimes() =
        MainScope().promise {
            val scope = CoroutineScope(Dispatchers.Default + DefaultMonotonicFrameClock)
            val recomposer =
                Recomposer(scope.coroutineContext + RecompositionFramePolicy.EventCompletion)
            val composition = Composition(EmptyApplier(), recomposer)
            val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
            val state = mutableStateOf(0)
            var rendered = -1
            val stamps = CompletableDeferred<List<Long>>()
            try {
                composition.setContent {
                    val value = state.value
                    SideEffect { rendered = value }
                    LaunchedEffect(Unit) {
                        stamps.complete(List(3) { withFrameNanos { it } })
                    }
                }
                state.value = 1
                Snapshot.sendApplyNotifications()
                val times = withTimeout(3000) { stamps.await() }
                assertTrue(times.first() > 0L)
                assertTrue(times.zipWithNext().all { (a, b) -> b > a })
                assertEquals(1, rendered)
            } finally {
                composition.dispose()
                recomposer.cancel()
                runner.cancelAndJoin()
                scope.cancel()
            }
        }
}
