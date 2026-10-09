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

import androidx.compose.runtime.internal.SnapshotThreadLocal
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotThreadLocalTest {
    @Test
    fun localOperationsRestoreAndClearValues() {
        val local = SnapshotThreadLocal<Any>()
        val first = Any()
        val second = Any()
        assertNull(local.get())
        assertTrue(local.setIfEmpty(first))
        assertFalse(local.setIfEmpty(second))
        assertEquals(first, local.replace(second))
        assertEquals(second, local.replace(null))
        assertNull(local.get())
        assertTrue(local.setIfEmpty(null))
        assertNull(local.get())
    }

    @Test
    fun valuesAreIsolatedOnConcurrentWorkers() {
        val local = SnapshotThreadLocal<Int>()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures =
                (0 until 64).map { value ->
                    executor.submit {
                        assertNull(local.get())
                        repeat(1000) {
                            local.set(value)
                            assertEquals(value, local.get())
                            assertEquals(value, local.replace(null))
                        }
                    }
                }
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
            assertNull(local.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun snapshotContextRestoresAfterExceptionOnWorker() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor
                .submit {
                    val previous = Snapshot.current
                    val snapshot = Snapshot.takeMutableSnapshot()
                    try {
                        try {
                            snapshot.enter { error("expected") }
                        } catch (_: IllegalStateException) {}
                        assertEquals(previous, Snapshot.current)
                    } finally {
                        snapshot.dispose()
                    }
                }
                .get(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }
}
