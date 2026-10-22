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

package androidx.collection

import kotlin.js.unsafeCast

internal actual const val useSmallIntObjectMapStorage: Boolean = true

@PublishedApi
internal actual inline var IntObjectMap<*>.smallMapMetadata: ByteArray?
    get() = asDynamic().androidxCollectionSmallMapMetadata.unsafeCast<ByteArray?>()
    set(value) {
        asDynamic().androidxCollectionSmallMapMetadata = value
    }

@PublishedApi
internal actual inline fun IntObjectMap<*>.forEachJsIndex(block: (Int) -> Unit): Boolean {
    val occupied = smallMapMetadata
    if (occupied != null) {
        for (index in occupied.indices) {
            if (occupied[index] != 0.toByte()) block(index)
        }
        return true
    }
    val m = metadata
    // JavaScript emulates Long operations. Scan four control bytes with Int operations.
    for (baseIndex in 0 until capacity step 4) {
        val group = m[baseIndex ushr 3]
        var bytes = if ((baseIndex and 4) == 0) group.toInt() else (group ushr 32).toInt()
        if ((bytes and 0x80808080.toInt()) == 0x80808080.toInt()) continue
        for (offset in 0..3) {
            // The sentinel has its high bit set, so it is skipped like an empty slot.
            if ((bytes and 0x80) == 0) block(baseIndex + offset)
            bytes = bytes ushr 8
        }
    }
    return true
}

internal actual inline fun IntObjectMap<*>.findJsKeyIndex(key: Int): Int {
    if (size == 0) return -1
    val occupied = smallMapMetadata ?: return -2
    val keys = keys
    for (index in occupied.indices) {
        if (occupied[index] != 0.toByte() && keys[index] == key) return index
    }
    return -1
}
