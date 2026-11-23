/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.kotlin

import feign.Experimental
import java.time.Duration

/**
 * A single Server-Sent Event as dispatched by the server.
 *
 * Field values are populated by the [SseTransport]; the standard WebClient transport maps them
 * from Spring's SSE codec output. Events are delivered in arrival order and never replayed.
 *
 * @property data payload of the event's `data` field, joined across multiple `data` lines the way
 *   the SSE spec prescribes; null when the frame carried no data at all, which happens for
 *   comment-only keep-alive frames.
 * @property event value of the `event` field, or null when the server did not name the event.
 * @property id value of the `id` field, or null when the server did not send one.
 * @property retry reconnection hint carried by the frame's `retry` field, expressed as a
 *   [Duration] of the milliseconds the server sent, or null when the server did not send one.
 *   This library performs no reconnection itself; consumers decide what to do with the hint.
 * @property comment text of the frame's comment lines, or null.
 */
@Experimental
data class SseEvent(
    val data: String?,
    val event: String? = null,
    val id: String? = null,
    val retry: Duration? = null,
    val comment: String? = null,
)
