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

package androidx.compose.runtime.samples

import androidx.annotation.Sampled
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withNonSnapshotState
import androidx.compose.runtime.withSinglePassSnapshotMode

@Sampled
@OptIn(ExperimentalComposeRuntimeApi::class)
fun nonSnapshotStateSample() {
    val inputs = listOf("First", "Second")
    val output = withNonSnapshotState {
        val count = mutableIntStateOf(0)
        buildString {
            for (input in inputs) {
                count.intValue++
                append(count.intValue).append(": ").append(input).append('\n')
            }
        }
        // The result is a String; the temporary state stays inside this synchronous scope.
    }
    println(output)
}

@Sampled
@OptIn(ExperimentalComposeRuntimeApi::class)
fun singlePassSnapshotModeSample() {
    val inputs = listOf("First", "Second")
    val output = withSinglePassSnapshotMode {
        // A one-pass renderer can keep its existing snapshot lifecycle calls.
        val snapshot = Snapshot.takeMutableSnapshot()
        try {
            snapshot.enter {
                val count = mutableIntStateOf(0)
                buildString {
                    for (input in inputs) {
                        count.intValue++
                        append(count.intValue).append(": ").append(input).append('\n')
                    }
                }
            }
        } finally {
            // This wrapper provides no rollback. Applying it would throw.
            snapshot.dispose()
        }
    }
    println(output)
}
