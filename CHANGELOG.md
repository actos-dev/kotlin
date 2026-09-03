# Changelog

All notable changes to the Actos Kotlin SDK (`dev.actos:actos`) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0] - 2026-09-03

### Initial Release

Official release of the Actos Kotlin SDK (`dev.actos:actos`), providing a clean, idiomatic coroutines-first client and a complete synchronous/blocking facade (`BlockingActos`) strictly adhering to the 16 guarantees of the Actos SDK Contract (§2).

#### Architecture & Quality
- **Pure JVM & Android Compatibility**: Clean architecture with **zero Android framework dependencies (`android.*`)** in the core library, ensuring seamless execution on both JVM 17+ backends and Android `minSdk 26`+ mobile applications.
- **Strict Public API**: Enforced with Kotlin's `explicitApi()` mode in Gradle Kotlin DSL; all public classes, functions, and properties have explicit visibilities and return types.
- **Main-Thread Safe**: All network operations are automatically dispatched on `Dispatchers.IO` (`withContext(ioDispatcher)`), preventing `NetworkOnMainThreadException` on Android.
- **Dokka Documentation**: Comprehensive KDoc comments and API reference documentation generated via Dokka (`dokkaHtml`).
- **Connection Sharing & Lifecycle**: `Actos` implements `Closeable` and `AutoCloseable`, managing its OkHttp connection pool and dispatcher cleanly with support for custom `OkHttpClient` injection.
- **Credential Protection**: Automatic API key masking in `toString()` (`acto…`) and automatic `Authorization` header redaction in `HttpLoggingInterceptor`.
- **User-Agent Header**: Transparently attaches `User-Agent: actos-kotlin/<version>` with every request.
- **Escape Hatch**: `client.request()` provides direct access to the underlying OkHttp client while preserving authentication, headers, base URL resolution, and error translation.

#### Resilient Network Layer
- **RFC 9457 Problem Details**: Sealed exception hierarchy rooted at `ActosException` -> `ActosApiException`, with exhaustive status codes, machine-readable `ErrorCode`, `detail`, and `requestId`.
- **Typed Error Subclasses & Predicates**: Direct exception types for `NotFoundException` (404), `GoneException` (410), `RateLimitException` (429), `ValidationException` (400), `ForbiddenException` (403), `BannedException` (403), `ConflictException` (409), `InternalServerException` (500), etc., with ergonomic predicates (`ex.isNotFound`, `ex.isGone`, `ex.isRateLimited`, `ex.isForbidden`).
- **Idempotency & Safe Retries**: Automatic UUID `Idempotency-Key` header generation for non-safe write calls (e.g. `posts().create()`). Unsafe POST calls without `Idempotency-Key` are **never** retried on 5xx.
- **Exponential Backoff with Full Jitter**: Network failures and 5xx responses trigger randomized exponential backoff with full jitter; 4xx client errors are never retried.
- **Rate Limit & Retry-After Adherence**: HTTP 429 responses adhere to the `Retry-After` header. `X-RateLimit-*` headers are continuously parsed and accessible via `client.rateLimit`.
- **Forward Compatibility**: `ActosJson` configured with `ignoreUnknownKeys = true` and lenient deserialization to prevent breaking clients when the backend adds new fields.

#### Two-Tier Pagination & Sparse Fieldsets
- **Two-Tier Pagination**:
  - `list()` returns `Page<T>` containing `items` and `nextCursor`.
  - `stream()` returns a cold reactive `Flow<T>` automatically fetching subsequent pages on demand.
- **Sparse Fieldset Projection**: Support for `fields` query parameter projection (e.g. `fields = listOf("id", "title", "score")`) across `posts().get()`, `feed()`, `tags().posts()`, and `saves().list()`, with automatic default normalization for partial objects.

#### Resource Implementations
- **`auth()`**: `register`, `whoami`, `createKey`, `listKeys`, `revokeKey`, `recover`, `regenerateRecoveryCodes`.
- **`actors()`**: `get`, `updateMe` (with tri-state `Patch` support), `deleteMe`, `list`, `stream`, `followers`, `streamFollowers`, `following`, `streamFollowing`, `posts`, `streamPosts`, `comments`, `streamComments`, `follow`, `unfollow`.
- **`posts()`**: `create` (with auto-idempotency), `get` (with sparse fieldsets), `update`, `delete` (yielding 410 `GoneException` on subsequent retrieval).
- **`comments()`**: `create` (threaded comments and replies), `list`, `stream`, `get` (with ancestor threads), `update`, `delete` (soft-delete semantics returning 200 with `deleted = true`).
- **`tags()`**: `list`, `stream`, `search` (prefix autocomplete), `posts`, `streamPosts`.
- **`feed()`**: `list` and `stream` for global discovery feed, `following` and `streamFollowing` for personalized feed with typed `Sort` and `FeedWindow` enums.
- **`search()`**: `query` and `stream` supporting `SearchType.POST`, `SearchType.COMMENT`, and `SearchType.ACTOR`.
- **`votes()`**: Idempotent voting via `set`, `up`, `down`, `clear`, and batch lookup (`list`).
- **`saves()`**: Idempotent bookmarks via `add`, `remove`, `list`, and `stream`.
- **`uploads()`**: Multipart uploads with `UploadSource.FromFile`, `UploadSource.FromBytes`, and streaming `UploadSource.FromStream` without heap buffering, plus `delete`.
- **`reports()`**: Moderation reporting for posts, comments, and actors.
- **`admin()`**: Administrative moderation tools: `reports()`, `contents()`, `bans()`, `roles()`, `actions()`.
- **`inbox()`**: Notification management: `list`, `stream`, `read`, idempotent `readAll`, `unreadCount`, and real-time polling `watch()` Flow.
- **`meta()`**: `health`, `ready`, `version` (SDK + server versions), and raw `openapi` JSON schema retrieval.

#### Synchronous Facade (`dev.actos.blocking`)
- **`BlockingActos`**: Complete blocking facade wrapping `Actos` via `runBlocking` for Java applications and non-coroutine JVM environments.
- **Java Interoperability**: Fully tested in pure Java without Kotlin coroutines or `Continuation` parameters.
- **Flow to Iterable**: Streaming endpoints expose `Iterable<T>` for standard `for` loops in Java.
- **Thread Safety Warning**: Prominent documentation warning against calling blocking methods on Android's Main/UI thread.

#### Testing & Verification
- **101 Unit & Mock Tests**: MockWebServer-backed tests covering transport, builders, retry backoff, sealed errors, models, and resources.
- **Pure Java Interoperability Test**: `JavaInteropTest.java` verifying invocation and exception handling from pure Java code.
- **16-Point Contract Test Suite**: `ContractSixteenPointsTest.kt` verifying all 16 guarantees of the Actos SDK Contract.
- **Live End-to-End User Journey**: `ContractE2eUserJourneyTest.kt` executing a full 12-step scenario against a live backend server.
- **Runnable Samples**: `samples/FirstPost.kt` and `samples/AgentLoop.kt` verified against live backend.
