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

package androidx.compose.runtime

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecompositionFramePolicyTest {
    // Checks that early recomposition is disabled unless EventCompletion is explicitly selected.
    @Test
    fun eventCompletionRequiresAnExplicitPolicy() {
        val defaultPolicy =
            EmptyCoroutineContext[RecompositionFramePolicy] ?: RecompositionFramePolicy.FrameAligned
        assertFalse(defaultPolicy.recomposeAfterBrowserEvent)
        assertFalse(RecompositionFramePolicy.FrameAligned.recomposeAfterBrowserEvent)
        assertTrue(RecompositionFramePolicy.EventCompletion.recomposeAfterBrowserEvent)
    }

    // Checks that a child context can restore frame-aligned scheduling after inheriting an opt-in.
    @Test
    fun frameAlignedPolicyCanOverrideAnInheritedOptIn() {
        val context =
            RecompositionFramePolicy.EventCompletion + RecompositionFramePolicy.FrameAligned
        assertSame(RecompositionFramePolicy.FrameAligned, context[RecompositionFramePolicy])
    }

    // Checks that the limiter allows early passes only without frame awaiters.
    @Test
    fun animationsAlwaysWaitForAFrame() {
        val policy = ImmediateRecompositionLimiter()
        assertFalse(policy.canRunImmediately(true, 0.0))
        assertTrue(policy.canRunImmediately(false, 0.0))
    }

    // Checks that early passes are throttled to give the browser time to render.
    @Test
    fun selfInvalidationCannotProduceUnboundedEarlyPasses() {
        val policy = ImmediateRecompositionLimiter()
        assertTrue(policy.canRunImmediately(false, 0.0))
        for (time in listOf(0.0, 0.1, 1.0, 15.99)) {
            assertFalse(policy.canRunImmediately(false, time))
        }
        assertTrue(policy.canRunImmediately(false, 16.0))
        assertFalse(policy.canRunImmediately(false, 16.1))
        assertFalse(policy.canRunImmediately(true, 32.0))
        assertTrue(policy.canRunImmediately(false, 32.0))
    }
}
