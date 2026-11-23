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
import feign.Request
import feign.codec.ErrorDecoder
import org.reactivestreams.Publisher

/**
 * Executes the HTTP request of an [Sse]-annotated method and publishes the returned Server-Sent
 * Events incrementally.
 *
 * Implementations own the transport: they must not aggregate the response body before emitting
 * events, they should stop reading as soon as the returned [Publisher] is cancelled, and they
 * should respect the subscriber's demand instead of emitting without bound. Event conversion,
 * error signaling and timeouts are the transport's responsibility; events are published in
 * arrival order. A partially delivered stream is never retried by Feign.
 *
 * [feign.webflux.WebClientSseClient] is the standard implementation, built on Spring WebClient's
 * native SSE codec.
 */
@Experimental
fun interface SseTransport {

    /**
     * Sends [request] and returns the server-sent events as they arrive.
     *
     * @param request the request resolved by Feign's normal pipeline: parameters bound, body
     *   encoded, [feign.RequestInterceptor]s and the target already applied
     * @param options the per-request [Request.Options]; a transport that cannot honor them
     *   (streaming has no meaningful total read timeout) must document what it ignores
     * @param methodKey the Feign config key of the invoked method, for use in error reporting
     * @param errorDecoder the [ErrorDecoder] configured on the builder; on a non-2xx response,
     *   implementations should build a [feign.Response] from it and signal the exception returned
     *   by `errorDecoder.decode(methodKey, response)` as the publisher's error, keeping SSE
     *   failures under the same error pipeline as buffered methods
     * @return a cold publisher; each subscription sends a new request, and no event already
     *   delivered is ever replayed
     */
    fun stream(
        request: Request,
        options: Request.Options,
        methodKey: String,
        errorDecoder: ErrorDecoder,
    ): Publisher<SseEvent>
}
