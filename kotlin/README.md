# feign-kotlin

Kotlin support for Feign, including coroutine (suspend function) clients.

## CoroutineFeign

`CoroutineFeign` builds clients whose `suspend` functions are executed asynchronously:

```kotlin
val api = CoroutineFeign.builder()
  .client(AsyncClient.Default(Client.Default(null, null), Executors.newCachedThreadPool()))
  .target(GitHubApi::class.java, "https://api.github.com")

val repositories = api.listRepositories("openfeign") // suspend fun
```

## Using suspend functions with the blocking builder

`CoroutineFeign` already resolves the declared Kotlin return type for `suspend`
functions. If you use the plain blocking `Feign.builder()` with an interface that
contains `suspend` functions, the JVM signature reports `java.lang.Object` as the
return type, and decoders would receive `Object` instead of the declared type
(see [#2019](https://github.com/OpenFeign/feign/issues/2019)).

Wrap your contract with `KotlinDelegatingContract` to fix the resolved type:

```kotlin
val api = Feign.builder()
  .contract(KotlinDelegatingContract(DefaultContract())) // feign.core.DefaultContract
  .decoder(JacksonDecoder())
  .target(GitHubApi::class.java, "https://api.github.com")
```

## Truly non-blocking execution

By default `CoroutineFeign` wraps a blocking `Client` with
`DefaultAsyncClient`, which runs the blocking I/O on a cached thread pool. The
suspending call never blocks the caller, but a thread is still occupied per
in-flight request.

For fully non-blocking execution, plug in any `AsyncClient` implementation that
completes its `CompletableFuture` without an extra thread, e.g.
`feign-http2client` (`Http2Client`, Java 11+ `HttpClient`), `feign-hc5`
(`AsyncApacheHttp5Client`), `feign-vertx`, or `feign-webflux`
(`WebClientAsyncClient`, Spring `WebClient` on Reactor Netty):

```kotlin
val api = CoroutineFeign.builder<GitHubApi>()
  .client(Http2Client() as AsyncClient<GitHubApi>) // or AsyncApacheHttp5Client()
  .decoder(JacksonDecoder())
  .target(GitHubApi::class.java, "https://api.github.com")
```

The HTTP request now runs on the async client's own I/O machinery, and response
decoding happens on the thread that completes the future rather than on a
dedicated pool thread. See `NonBlockingCoroutineFeignTest` for a runnable
example.

## Server-Sent Events (SSE) streaming

Methods annotated `@Sse` return a `Flow<SseEvent>` and are executed as live streams: events are
emitted as they arrive, and the response body is never aggregated in memory. The annotation is
explicit on every streaming method; methods without it keep the regular buffered coroutine
behavior.

```kotlin
data class ChatRequest(val model: String, val messages: List<Map<String, String>>)

interface AiApi {
    @RequestLine("GET /events")
    @Headers("Accept: text/event-stream")
    @Sse
    fun events(): Flow<SseEvent>

    @RequestLine("POST /chat/completions")
    @Headers("Content-Type: application/json", "Accept: text/event-stream")
    @Sse
    suspend fun chatStream(body: ChatRequest): Flow<SseEvent>
}

val api = CoroutineFeign.builder<AiApi>()
    .client(WebClientAsyncClient<Any>())          // buffered methods, unchanged
    .sseTransport(WebClientSseClient())           // @Sse methods stream through this transport
    .encoder(JacksonEncoder(objectMapper))        // native body encoding, reused for @Sse methods
    .target(AiApi::class.java, "https://ai.example.com/v1")

api.chatStream(ChatRequest("gpt", messages))
    .collect { event -> print(event.data ?: "") } // events arrive incrementally
```

`WebClientSseClient` (from [`feign-webflux`](../webflux/README.md)) is the standard transport; any
implementation of the `SseTransport` interface works — it receives the fully resolved
`Request` (parameters bound, body encoded, `RequestInterceptor`s and target applied) and returns
the events as a `org.reactivestreams.Publisher<SseEvent>`.

`SseEvent` carries the parsed fields of a dispatched event: `data` (null for comment-only
keep-alive frames), `event`, `id`, `retry` and `comment`. Parsing is delegated to the transport;
the WebClient transport uses Spring's standard SSE codec.

### Supported signatures and validation

- Both `suspend fun ... : Flow<SseEvent>` and plain `fun ... : Flow<SseEvent>` are supported.
- Anything else annotated with `@Sse` (a `Flow<SseEvent>` element type, a non-`Flow` return type,
  a default method) is rejected with an explanatory `IllegalArgumentException` when the proxy is
  created, before any request is attempted.
- Creating a proxy for an interface that declares `@Sse` methods without registering an
  `SseTransport` fails with an explanatory `IllegalStateException`. Interfaces without `@Sse`
  methods do not need a transport.

### Behavior notes

- The returned flow is cold: every collection re-binds the arguments, re-runs the encoders and
  interceptors, and sends a new request. Partially consumed streams are never retried or replayed.
- Cancelling collection cancels the in-flight request and disposes the connection.
- A `Request.Options` argument overrides the builder options for that invocation, as with any
  Feign method; how much of it a stream can honor is up to the transport (the WebClient transport
  documents what it ignores). Options timeouts do not bound the lifetime of a stream: a live
  stream may stay open indefinitely, and stalled-stream protection is the transport's or
  connector's responsibility.
- Feign's `Logger` and `Retryer` do not run on `@Sse` paths: request logging belongs to the
  transport, and a partially delivered stream is never retried. Error statuses (4xx/5xx) are
  routed through the builder's `ErrorDecoder`: the transport buffers the (bounded) error body into
  a `Response` and signals the decoder's exception as the flow error; see the
  [`feign-webflux`](../webflux/README.md) behavior notes.
- There is no automatic reconnection: the `retry` field of an event is delivered as data on
  `SseEvent.retry`, and no `Last-Event-ID` header is ever sent, because no event id state is
  tracked. Reconnection, if needed, is the consumer's or a custom transport's responsibility.
- Errors during streaming surface as errors of the flow.

The SSE API (`@Sse`, `SseEvent`, `SseTransport`, `sseTransport(...)`) is annotated
`feign.Experimental` and may change before it is stabilized.
