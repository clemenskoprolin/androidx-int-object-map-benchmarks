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

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Controls when a [Recomposer] processes pending changes.
 *
 * Add a policy to the effect coroutine context passed to [Recomposer]. If no policy is supplied,
 * the recomposer uses [FrameAligned]. The policy is fixed when the recomposer is created.
 *
 * @sample androidx.compose.runtime.samples.eventCompletionRecomposerSample
 */
public class RecompositionFramePolicy
private constructor(internal val recomposeAfterBrowserEvent: Boolean) :
    AbstractCoroutineContextElement(Key) {
    /** Key for [RecompositionFramePolicy] in a [CoroutineContext]. */
    public companion object Key : CoroutineContext.Key<RecompositionFramePolicy> {
        /** Wait for the next frame before processing pending changes. This is the default. */
        public val FrameAligned: RecompositionFramePolicy = RecompositionFramePolicy(false)

        /**
         * Allow pending changes to run after the current browser event finishes, using a zero-delay
         * timer instead of waiting for the next animation frame.
         *
         * This policy applies only to the default Wasm browser frame clock. Other platforms and
         * custom clocks retain frame-aligned scheduling. Animations and frame-end callbacks still
         * wait for an actual frame. Early passes are limited to at most one per 16 milliseconds so
         * that repeated invalidations allow the browser to render.
         */
        public val EventCompletion: RecompositionFramePolicy = RecompositionFramePolicy(true)
    }
}

/** Limits repeated early passes so that self-invalidating compositions let the browser render. */
internal class ImmediateRecompositionLimiter {
    private var lastImmediateMillis = Double.NEGATIVE_INFINITY

    fun canRunImmediately(
        hasFrameAwaiters: Boolean,
        nowMillis: Double,
    ): Boolean {
        if (hasFrameAwaiters) return false
        // Self-invalidating compositions must give the browser another frame. This caps early
        // passes, rather than creating an unbounded microtask loop that could starve rendering.
        if (nowMillis - lastImmediateMillis < 16.0) return false
        lastImmediateMillis = nowMillis
        return true
    }
}

internal expect fun isDefaultBrowserFrameClock(clock: MonotonicFrameClock): Boolean

internal expect fun browserRecompositionTimeMillis(): Double

internal expect suspend fun awaitBrowserEventCompletion()
