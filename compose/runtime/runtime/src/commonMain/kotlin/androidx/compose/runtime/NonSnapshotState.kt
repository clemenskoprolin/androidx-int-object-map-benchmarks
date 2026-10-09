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

import androidx.compose.runtime.internal.SnapshotThreadLocal
import androidx.compose.runtime.internal.currentThreadId

/**
 * Runs [block] with scalar state factories that create local, unobserved holders.
 *
 * This is an experiment for one-pass rendering. State created here has no snapshot isolation,
 * rollback, read/write observation, derived-state tracking, or recomposition notifications. It must
 * only be accessed by the creating thread while this scope is active. An escaped holder throws on
 * access. Nested snapshot operations do not isolate these local holders.
 *
 * Existing snapshot state and snapshot collections retain their ordinary behavior. This scope does
 * not take or enter a snapshot and does not make access to existing shared state isolated. The
 * caller must provide the isolation required for any shared state it accesses.
 *
 * Nested local-state scopes are supported. The previous factory mode is restored on exceptions.
 * [block] must complete synchronously on this thread. Values created here must stay within this
 * scope. Changes to these values do not invalidate [derivedStateOf] calculations.
 *
 * @sample androidx.compose.runtime.samples.nonSnapshotStateSample
 * @param block synchronous work that uses temporary local state
 * @return the result of [block]
 */
@ExperimentalComposeRuntimeApi
public fun <R> withNonSnapshotState(block: () -> R): R {
    val scope = NonSnapshotStateScope(currentThreadId())
    val previous = nonSnapshotStateScope.replace(scope)
    try {
        return block()
    } finally {
        scope.active = false
        nonSnapshotStateScope.set(previous)
    }
}

/**
 * Runs [block] with local scalar state and lightweight mutable-snapshot contexts.
 *
 * In addition to local scalar state factories,
 * [androidx.compose.runtime.snapshots.Snapshot.takeMutableSnapshot] returns a non-transactional
 * observer wrapper. Taking, entering, and disposing that wrapper do not allocate an isolated
 * snapshot ID or pin records. Applying it throws.
 *
 * Existing snapshot state is read in the caller's snapshot and writes to it are not rolled back
 * when a wrapper is disposed. Global reads may change during the scope. Snapshot creation and
 * disposal tooling events are skipped for these wrappers. Read-only snapshots, snapshot
 * collections, derived state, and explicit notification APIs retain their normal runtime costs.
 * Wrappers and local scalar holders must stay on this thread within the scope. A wrapper also
 * requires its caller snapshot to remain alive. Local scalar holders are unobserved.
 *
 * Use this scope for synchronous, one-pass rendering from immutable inputs. [block] must complete
 * on this thread, and local holders and wrappers must stay within this scope. Changes to local
 * holders do not invalidate [derivedStateOf] calculations. Use ordinary snapshots for transactions,
 * recomposition, or shared-state inputs that require a consistent view.
 *
 * @sample androidx.compose.runtime.samples.singlePassSnapshotModeSample
 * @param block synchronous work that uses temporary local state and snapshot contexts
 * @return the result of [block]
 */
@ExperimentalComposeRuntimeApi
public fun <R> withSinglePassSnapshotMode(block: () -> R): R {
    val scope = NonSnapshotStateScope(currentThreadId(), bypassMutableSnapshots = true)
    val previous = nonSnapshotStateScope.replace(scope)
    try {
        return block()
    } finally {
        scope.active = false
        nonSnapshotStateScope.set(previous)
    }
}

private val nonSnapshotStateScope = SnapshotThreadLocal<NonSnapshotStateScope>()

internal fun currentNonSnapshotStateScope(): NonSnapshotStateScope? = nonSnapshotStateScope.get()

internal class NonSnapshotStateScope(
    private val threadId: Long,
    val bypassMutableSnapshots: Boolean = false,
) {
    var active = true

    fun validateAccess() {
        check(threadId == currentThreadId() && active) {
            "Non-snapshot state can only be used on its creating thread while its scope is active"
        }
    }
}

internal class NonSnapshotMutableState<T>(
    value: T,
    private val policy: SnapshotMutationPolicy<T>,
    private val owner: NonSnapshotStateScope,
) : MutableState<T> {
    private var current = value

    override var value: T
        get() {
            owner.validateAccess()
            return current
        }
        set(value) {
            owner.validateAccess()
            if (!policy.equivalent(current, value)) current = value
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = { value = it }
}

internal class NonSnapshotMutableIntState(
    value: Int,
    private val owner: NonSnapshotStateScope,
) : MutableIntState {
    private var current = value

    override var intValue: Int
        get() {
            owner.validateAccess()
            return current
        }
        set(value) {
            owner.validateAccess()
            if (current != value) current = value
        }

    override fun component1(): Int = intValue

    override fun component2(): (Int) -> Unit = { intValue = it }
}

internal class NonSnapshotMutableLongState(
    value: Long,
    private val owner: NonSnapshotStateScope,
) : MutableLongState {
    private var current = value

    override var longValue: Long
        get() {
            owner.validateAccess()
            return current
        }
        set(value) {
            owner.validateAccess()
            if (current != value) current = value
        }

    override fun component1(): Long = longValue

    override fun component2(): (Long) -> Unit = { longValue = it }
}

internal class NonSnapshotMutableFloatState(
    value: Float,
    private val owner: NonSnapshotStateScope,
) : MutableFloatState {
    private var current = value

    override var floatValue: Float
        get() {
            owner.validateAccess()
            return current
        }
        set(value) {
            owner.validateAccess()
            if (current != value) current = value
        }

    override fun component1(): Float = floatValue

    override fun component2(): (Float) -> Unit = { floatValue = it }
}

internal class NonSnapshotMutableDoubleState(
    value: Double,
    private val owner: NonSnapshotStateScope,
) : MutableDoubleState {
    private var current = value

    override var doubleValue: Double
        get() {
            owner.validateAccess()
            return current
        }
        set(value) {
            owner.validateAccess()
            if (current != value) current = value
        }

    override fun component1(): Double = doubleValue

    override fun component2(): (Double) -> Unit = { doubleValue = it }
}
