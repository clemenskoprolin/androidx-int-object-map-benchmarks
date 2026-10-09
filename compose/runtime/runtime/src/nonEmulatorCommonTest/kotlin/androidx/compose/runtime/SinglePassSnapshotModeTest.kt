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

import androidx.compose.runtime.snapshots.MutableSnapshot
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.StateObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeRuntimeApi::class, InternalComposeApi::class)
class SinglePassSnapshotModeTest {
    @Test
    fun ordinaryMutableSnapshotLifecycleDoesNotOpenAnIsolatedSnapshot() {
        val before = Snapshot.openSnapshotCount()
        val caller = Snapshot.current
        withSinglePassSnapshotMode {
            val wrapper = Snapshot.takeMutableSnapshot()
            try {
                assertEquals(before, Snapshot.openSnapshotCount())
                wrapper.enter {
                    assertEquals(wrapper, Snapshot.current)
                    val state = mutableStateOf(0)
                    assertFalse(state is StateObject)
                    state.value = 1
                    assertEquals(1, state.value)
                }
                assertFailsWith<IllegalStateException> { wrapper.apply() }
            } finally {
                wrapper.dispose()
            }
        }
        assertEquals(caller, Snapshot.current)
        assertEquals(before, Snapshot.openSnapshotCount())
        assertTrue(mutableStateOf(0) is StateObject)
    }

    @Test
    fun writesToExistingStateHaveExplicitlyNoRollback() {
        val state = mutableStateOf(0)
        withSinglePassSnapshotMode {
            val wrapper = Snapshot.takeMutableSnapshot()
            try {
                wrapper.enter { state.value = 1 }
            } finally {
                wrapper.dispose()
            }
        }
        assertEquals(1, state.value)
    }

    @Test
    fun wrappersDoNotApplyOrDisposeTheCallersSnapshot() {
        val state = mutableStateOf(0)
        val caller = Snapshot.takeMutableSnapshot()
        try {
            caller.enter {
                withSinglePassSnapshotMode {
                    val wrapper = Snapshot.takeMutableSnapshot()
                    wrapper.enter { state.value = 1 }
                    assertFailsWith<IllegalStateException> { wrapper.apply() }
                    wrapper.dispose()
                    assertEquals(1, state.value)
                    val child = caller.takeNestedMutableSnapshot()
                    child.dispose()
                }
            }
        } finally {
            caller.dispose()
        }
        assertEquals(0, state.value)
    }

    @Test
    fun nestedWrappersRestoreContextAndDoNotCreateSnapshotIds() {
        val before = Snapshot.openSnapshotCount()
        withSinglePassSnapshotMode {
            val parent = Snapshot.takeMutableSnapshot()
            try {
                parent.enter {
                    val child = parent.takeNestedMutableSnapshot()
                    try {
                        child.enter { assertEquals(child, Snapshot.current) }
                        assertEquals(parent, Snapshot.current)
                        assertEquals(before, Snapshot.openSnapshotCount())
                    } finally {
                        child.dispose()
                    }
                }
            } finally {
                parent.dispose()
            }
        }
    }

    @Test
    fun globalWrapperFollowsGlobalAdvancement() {
        val state = mutableStateOf(0)
        Snapshot.notifyObjectsInitialized()
        withSinglePassSnapshotMode {
            val wrapper = Snapshot.takeMutableSnapshot()
            try {
                wrapper.enter { assertEquals(0, state.value) }
                state.value = 1
                Snapshot.sendApplyNotifications()
                wrapper.enter {
                    assertEquals(1, state.value)
                    state.value = 2
                }
                assertEquals(2, state.value)
            } finally {
                wrapper.dispose()
            }
        }
    }

    @Test
    fun escapedAndDisposedWrappersRejectEntry() {
        lateinit var escaped: MutableSnapshot
        withSinglePassSnapshotMode { escaped = Snapshot.takeMutableSnapshot() }
        try {
            assertFailsWith<IllegalStateException> { escaped.enter {} }
        } finally {
            escaped.dispose()
        }
        withSinglePassSnapshotMode {
            val wrapper = Snapshot.takeMutableSnapshot()
            wrapper.dispose()
            assertFailsWith<IllegalArgumentException> { wrapper.enter {} }
        }
    }

    @Test
    fun wrapperRejectsEntryAfterCallerDisposal() {
        val caller = Snapshot.takeMutableSnapshot()
        try {
            caller.enter {
                withSinglePassSnapshotMode {
                    val wrapper = Snapshot.takeMutableSnapshot()
                    caller.dispose()
                    try {
                        assertFailsWith<IllegalArgumentException> { wrapper.enter {} }
                    } finally {
                        wrapper.dispose()
                    }
                }
            }
        } finally {
            caller.dispose()
        }
    }

    @Test
    fun nestedWrapperRejectsEntryAfterAncestorDisposal() {
        val caller = Snapshot.takeMutableSnapshot()
        try {
            caller.enter {
                withSinglePassSnapshotMode {
                    val parent = Snapshot.takeMutableSnapshot()
                    val child = parent.takeNestedMutableSnapshot()
                    try {
                        caller.dispose()
                        assertFailsWith<IllegalArgumentException> { child.enter {} }
                    } finally {
                        child.dispose()
                        parent.dispose()
                    }
                }
            }
        } finally {
            caller.dispose()
        }
    }

    @Test
    fun readOnlyCallerStillRejectsMutableSnapshotCreation() {
        val snapshot = Snapshot.takeSnapshot()
        try {
            snapshot.enter {
                withSinglePassSnapshotMode {
                    assertFailsWith<IllegalStateException> { Snapshot.takeMutableSnapshot() }
                }
            }
        } finally {
            snapshot.dispose()
        }
    }

    @Test
    fun observersStillSeeExistingStateButNotLocalHolders() {
        val existing = mutableStateOf(0)
        Snapshot.notifyObjectsInitialized()
        val reads = mutableListOf<Any>()
        val writes = mutableListOf<Any>()
        withSinglePassSnapshotMode {
            val wrapper = Snapshot.takeMutableSnapshot({ reads.add(it) }, { writes.add(it) })
            try {
                wrapper.enter {
                    assertEquals(0, existing.value)
                    existing.value = 1
                    val local = mutableStateOf(0)
                    local.value = 2
                    assertEquals(2, local.value)
                }
            } finally {
                wrapper.dispose()
            }
        }
        assertEquals(listOf<Any>(existing), reads)
        assertEquals(listOf<Any>(existing), writes)
    }

    @Test
    fun exceptionsRestoreOrdinaryFactoryAndSnapshotBehavior() {
        val before = Snapshot.openSnapshotCount()
        assertFailsWith<IllegalArgumentException> {
            withSinglePassSnapshotMode { throw IllegalArgumentException("expected") }
        }
        val snapshot = Snapshot.takeMutableSnapshot()
        try {
            assertEquals(before + 1, Snapshot.openSnapshotCount())
            assertTrue(mutableStateOf(0) is StateObject)
        } finally {
            snapshot.dispose()
        }
    }
}
