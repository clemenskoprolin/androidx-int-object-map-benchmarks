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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalComposeRuntimeApi::class)
class SinglePassSnapshotModeJvmTest {
    @Test
    fun wrapperRejectsCrossThreadEntryWhileOwnerScopeIsActive() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            withSinglePassSnapshotMode {
                val wrapper = Snapshot.takeMutableSnapshot()
                try {
                    executor
                        .submit {
                            assertFailsWith<IllegalStateException> { wrapper.enter {} }
                            assertFailsWith<IllegalStateException> {
                                wrapper.takeNestedMutableSnapshot()
                            }
                        }
                        .get(30, TimeUnit.SECONDS)
                } finally {
                    wrapper.dispose()
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
