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

internal expect val useSmallIntObjectMapStorage: Boolean

// Only JavaScript stores this property on the map. The other targets inline null reads
// and no-op writes, keeping their object layout and packed-metadata path unchanged.
@PublishedApi internal expect var IntObjectMap<*>.smallMapMetadata: ByteArray?
