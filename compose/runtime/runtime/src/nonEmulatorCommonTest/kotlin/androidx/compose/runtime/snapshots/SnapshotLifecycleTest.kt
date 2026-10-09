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

package androidx.compose.runtime.snapshots

import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(InternalComposeApi::class)
class SnapshotLifecycleTest {
    @Test
    fun emptySnapshotsCloseWithoutLeakingIds() {
        val before = Snapshot.openSnapshotCount()
        repeat(1000) {
            val snapshot = Snapshot.takeMutableSnapshot()
            snapshot.dispose()
            snapshot.dispose()
        }
        assertEquals(before, Snapshot.openSnapshotCount())
    }

    @Test
    fun oldSnapshotRetainsItsValueWhileEmptySnapshotsAdvanceGlobalState() {
        val state = mutableStateOf(0)
        Snapshot.notifyObjectsInitialized()
        val old = Snapshot.takeSnapshot()
        try {
            repeat(100) { index ->
                Snapshot.withMutableSnapshot { state.value = index + 1 }
                Snapshot.takeMutableSnapshot().dispose()
                assertEquals(0, old.enter { state.value })
                assertEquals(index + 1, state.value)
            }
        } finally {
            old.dispose()
        }
    }

    @Test
    fun disposedParentKeepsChildRecordsAliveUntilChildCloses() {
        val before = Snapshot.openSnapshotCount()
        val state = mutableStateOf(0)
        val parent = Snapshot.takeMutableSnapshot()
        parent.enter { state.value = 1 }
        val child = parent.takeNestedMutableSnapshot()
        parent.dispose()
        try {
            repeat(100) { Snapshot.takeMutableSnapshot().dispose() }
            assertEquals(1, child.enter { state.value })
            assertFalse(child.apply().succeeded)
        } finally {
            child.dispose()
        }
        assertEquals(0, state.value)
        assertEquals(before, Snapshot.openSnapshotCount())
    }

    @Test
    fun takingSnapshotStillNotifiesPendingGlobalChanges() {
        val state = mutableStateOf(0)
        Snapshot.notifyObjectsInitialized()
        var calls = 0
        val handle = Snapshot.registerApplyObserver { states, _ ->
            if (state in states) calls++
        }
        try {
            state.value = 1
            Snapshot.takeMutableSnapshot().dispose()
            assertEquals(1, calls)
            Snapshot.takeMutableSnapshot().dispose()
            assertEquals(1, calls)
        } finally {
            handle.dispose()
        }
    }
}
