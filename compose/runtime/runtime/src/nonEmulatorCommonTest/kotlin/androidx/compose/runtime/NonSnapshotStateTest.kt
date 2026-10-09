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

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.StateObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeRuntimeApi::class)
class NonSnapshotStateTest {
    @Test
    fun ordinaryFactoryUsesLocalHolderOnlyInOptInScope() {
        assertTrue(mutableStateOf(0) is StateObject)
        withNonSnapshotState {
            val state = mutableStateOf(0)
            assertFalse(state is StateObject)
            state.value = 1
            assertEquals(1, state.value)
        }
        assertTrue(mutableStateOf(0) is StateObject)
    }

    @Test
    fun escapedHoldersRejectReadsAndWrites() {
        val state = withNonSnapshotState { mutableStateOf(0) }
        assertFailsWith<IllegalStateException> { state.value }
        assertFailsWith<IllegalStateException> { state.value = 1 }
    }

    @Test
    fun exceptionsRestoreFactoryMode() {
        assertFailsWith<IllegalArgumentException> {
            withNonSnapshotState { throw IllegalArgumentException("expected") }
        }
        assertTrue(mutableStateOf(0) is StateObject)
    }

    @Test
    fun customMutationPolicyIsPreserved() {
        val policy =
            object : SnapshotMutationPolicy<Int> {
                override fun equivalent(a: Int, b: Int): Boolean = a / 10 == b / 10
            }
        withNonSnapshotState {
            val state = mutableStateOf(11, policy)
            state.value = 12
            assertEquals(11, state.value)
            state.value = 21
            assertEquals(21, state.value)
        }
    }

    @Test
    fun nestedScopesRestoreTheirParentAndKeepItsHoldersAlive() {
        withNonSnapshotState {
            val parent = mutableStateOf(1)
            val child = withNonSnapshotState {
                parent.value = 2
                mutableStateOf(3)
            }
            assertEquals(2, parent.value)
            assertFailsWith<IllegalStateException> { child.value }
            assertFalse(mutableStateOf(4) is StateObject)
        }
    }

    @Test
    fun primitiveFactoriesBypassSnapshotRecords() {
        withNonSnapshotState {
            val int = mutableIntStateOf(0)
            val long = mutableLongStateOf(0)
            val float = mutableFloatStateOf(0f)
            val double = mutableDoubleStateOf(0.0)
            assertFalse(int is StateObject)
            assertFalse(long is StateObject)
            assertFalse(float is StateObject)
            assertFalse(double is StateObject)
            int.intValue = 1
            long.longValue = 2
            float.floatValue = 3f
            double.doubleValue = 4.0
            assertEquals(1, int.intValue)
            assertEquals(2L, long.longValue)
            assertEquals(3f, float.floatValue)
            assertEquals(4.0, double.doubleValue)
        }
    }

    @Test
    fun existingStateRetainsSnapshotRollback() {
        val shared = mutableStateOf(0)
        val snapshot = Snapshot.takeMutableSnapshot()
        try {
            snapshot.enter {
                withNonSnapshotState {
                    shared.value = 1
                    assertEquals(1, shared.value)
                    val local = mutableStateOf(2)
                    local.value = 3
                    assertEquals(3, local.value)
                }
            }
        } finally {
            snapshot.dispose()
        }
        assertEquals(0, shared.value)
    }

    @Test
    fun localStateHasExplicitlyNonTransactionalSemantics() {
        withNonSnapshotState {
            val local = mutableStateOf(0)
            val snapshot = Snapshot.takeMutableSnapshot()
            try {
                snapshot.enter { local.value = 1 }
            } finally {
                snapshot.dispose()
            }
            assertEquals(1, local.value)
        }
    }
}
