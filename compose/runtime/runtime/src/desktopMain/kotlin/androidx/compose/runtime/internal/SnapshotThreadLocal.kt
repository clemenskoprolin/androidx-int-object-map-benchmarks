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

package androidx.compose.runtime.internal

internal actual class SnapshotThreadLocal<T> actual constructor() {
    private val local = ThreadLocal<T>()

    actual fun get(): T? = local.get()

    actual fun set(value: T?) {
        // Retain the empty entry to avoid allocating a new entry on each outermost enter.
        // A null value clears the snapshot reference; entries are owned by their thread.
        local.set(value)
    }

    actual fun setIfEmpty(value: T?): Boolean {
        if (local.get() != null) return false
        local.set(value)
        return true
    }

    actual fun replace(value: T?): T? {
        val previous = local.get()
        local.set(value)
        return previous
    }
}
