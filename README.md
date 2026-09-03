# Actos Kotlin SDK

Official Kotlin SDK for [Actos](https://actos.dev), designed for JVM 17+ and Android (`minSdk 26`+).

## Features

- **Pure Kotlin & Multi-Environment**: Clean separation with **zero Android dependencies (`android.*`)** in the core library, enabling seamless usage in both server-side JVM applications and Android mobile apps.
- **Strict Public API**: Built with Kotlin's `explicitApi()` mode to prevent accidental API surface expansion.
- **Idiomatic Coroutines & Flows**: All asynchronous operations use Kotlin coroutines (`suspend`), and paginated/streaming endpoints return reactive `Flow<T>` streams.
- **Main-Thread Safe**: All network operations are automatically dispatched on `Dispatchers.IO`.
- **Resilient & Idempotent**: Automatic `Idempotency-Key` generation for write requests, exponential backoff with full jitter, and strict RFC 9457 problem detail error hierarchy.
- **Credential Protection**: Automatic masking of API credentials in logging (`HttpLoggingInterceptor`) and `toString()` outputs.

## Requirements

- JVM 17 or higher
- Android `minSdk 26` or higher (if used on Android)

## Installation

### Gradle (Kotlin DSL)

Artifact coordinates: `dev.actos:actos`

```kotlin
repositories {
    mavenCentral()
    // or maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("dev.actos:actos:0.1.0")
}
```

## Quick Start

```kotlin
import dev.actos.ActosVersion

fun main() {
    println("Actos SDK version: ${ActosVersion.VERSION}")
}
```

## Development & Verification

To run tests and verifications:

```bash
./gradlew check
```

This executes:
- Unit tests with JUnit 5 (`./gradlew test`)
- Android dependency prohibition check (`./gradlew checkNoAndroidImports`)
- Linter checks (`./gradlew ktlintCheck`)
- Static code analysis (`./gradlew detekt`)

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
