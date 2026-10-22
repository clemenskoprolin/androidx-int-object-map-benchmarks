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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntObjectMapStorageTest {
    private fun assertContents(expected: Map<Int, String?>, actual: MutableIntObjectMap<String?>) {
        assertEquals(expected.size, actual.size)
        val entries = mutableMapOf<Int, String?>()
        actual.forEach { key, value ->
            assertFalse(entries.containsKey(key), "Duplicate key $key")
            entries[key] = value
        }
        assertEquals(expected, entries)
        expected.forEach { (key, value) ->
            assertTrue(actual.containsKey(key))
            assertEquals(value, actual[key])
            assertEquals(value, actual.getOrDefault(key, "missing"))
        }
        assertEquals(actual.hashCode(), entries.hashCode())
        val copy = MutableIntObjectMap<String?>().also { it.putAll(actual) }
        assertEquals(actual, copy)
    }

    @Test
    fun growsAndTrimsAcrossEverySmallStorageBoundary() {
        for (initialCapacity in
            listOf(0, 1, 6, 7, 14, 15, 30, 31, 62, 63, 110, 111, 112, 127, 128, 255)) {
            val actual = MutableIntObjectMap<String?>(initialCapacity)
            val expected = mutableMapOf<Int, String?>()
            for (key in 0 until 300) {
                val value = if (key % 3 == 0) null else "value$key"
                assertEquals(expected.put(key, value), actual.put(key, value))
                if (key in listOf(5, 6, 13, 14, 27, 28, 55, 56, 110, 111, 127, 128, 255, 299)) {
                    assertContents(expected, actual)
                }
            }
            for (key in 299 downTo 0) {
                assertEquals(expected.remove(key), actual.remove(key))
                actual.trim()
                if (key in listOf(256, 129, 128, 112, 111, 57, 56, 29, 28, 15, 14, 7, 6, 1, 0)) {
                    assertContents(expected, actual)
                }
            }
            assertEquals(0, actual.capacity)
            for (key in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) actual[key] = null
            assertEquals(5, actual.size)
            actual.clear()
            assertEquals(0, actual.size)
            actual[0] = "reused"
            assertContents(mapOf(0 to "reused"), actual)
        }
    }

    @Test
    fun reusesRemovedSlotsAndPreservesNullMappings() {
        val actual = MutableIntObjectMap<String?>(110)
        val expected = mutableMapOf<Int, String?>()
        for (round in 0 until 30) {
            for (key in 0 until 110) {
                val shifted = (key shl 20) xor round
                expected[shifted] = null
                actual[shifted] = null
            }
            val capacity = actual.capacity
            assertContents(expected, actual)
            val keys = expected.keys.toList()
            for (key in keys) {
                assertTrue(actual.remove(key, null))
                expected.remove(key)
                assertFalse(actual.containsKey(key))
                assertNull(actual[key])
            }
            assertEquals(capacity, actual.capacity)
            assertContents(expected, actual)
        }
    }

    @Test
    fun randomizedOperationsAgreeWithStandardMap() {
        for (seed in 0 until 8) {
            val random = Random(seed)
            val actual = MutableIntObjectMap<String?>(if (seed % 2 == 0) 0 else 300)
            val expected = mutableMapOf<Int, String?>()
            repeat(8000) { step ->
                val key =
                    when (random.nextInt(10)) {
                        0 -> Int.MIN_VALUE
                        1 -> Int.MAX_VALUE
                        2 -> 0
                        else -> random.nextInt(-220, 220)
                    }
                val value = if (random.nextInt(4) == 0) null else "v${random.nextInt(100)}"
                when (random.nextInt(12)) {
                    0,
                    1,
                    2,
                    3 -> assertEquals(expected.put(key, value), actual.put(key, value))
                    4 -> assertEquals(expected.remove(key), actual.remove(key))
                    5 -> {
                        val shouldRemove = expected.containsKey(key) && expected[key] == value
                        assertEquals(shouldRemove, actual.remove(key, value))
                        if (shouldRemove) expected.remove(key)
                    }
                    6 ->
                        assertEquals(
                            expected.getOrPut(key) { value },
                            actual.getOrPut(key) { value },
                        )
                    7 -> {
                        expected[key] = value
                        actual[key] = value
                        assertEquals(value, actual[key])
                    }
                    8 -> actual.trim()
                    9 -> {
                        assertEquals(expected[key], actual[key])
                        assertEquals(expected.containsKey(key), actual.containsKey(key))
                    }
                    10 ->
                        if (step % 97 == 0) {
                            expected.clear()
                            actual.clear()
                        }
                    11 -> {
                        val predicate: (Int, String?) -> Boolean = { k, _ -> k % 11 == 0 }
                        expected.keys.filter { it % 11 == 0 }.forEach { expected.remove(it) }
                        actual.removeIf(predicate)
                    }
                }
                if (step % 100 == 0) assertContents(expected, actual)
            }
            assertContents(expected, actual)
        }
    }

    @Test
    fun packedStorageIterationSkipsDeletedAndEmptySlots() {
        for (initialCapacity in listOf(113, 256, 1024)) {
            val actual = MutableIntObjectMap<String?>(initialCapacity)
            val expected = mutableMapOf<Int, String?>()
            for (key in -initialCapacity..initialCapacity step 3) {
                val value = if (key % 2 == 0) null else "v$key"
                actual[key] = value
                expected[key] = value
            }
            assertContents(expected, actual)
            for (key in expected.keys.filter { it % 2 == 0 }) {
                actual.remove(key)
                expected.remove(key)
            }
            assertContents(expected, actual)
            actual.clear()
            assertContents(emptyMap(), actual)
        }
    }

    @Test
    fun publicInlineIterationSupportsNonLocalReturns() {
        for (initialCapacity in listOf(0, 300)) {
            val map =
                MutableIntObjectMap<String?>(initialCapacity).apply {
                    this[0] = null
                    this[Int.MIN_VALUE] = "min"
                    this[Int.MAX_VALUE] = "max"
                }
            fun find(key: Int): String? {
                map.forEach { k, v -> if (k == key) return v }
                return "absent"
            }
            assertNull(find(0))
            assertEquals("min", find(Int.MIN_VALUE))
            assertEquals("max", find(Int.MAX_VALUE))
            assertEquals("absent", find(42))
            assertFalse(map.all { key, _ -> key != 0 })
            assertTrue(map.any { key, value -> key == 0 && value == null })
            assertEquals(1, map.count { _, value -> value == null })
        }
    }
}
