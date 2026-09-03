# Actos Kotlin SDK — Uygulama Planı

> Bu dosya canlı bir kontrol listesidir. Bir adım bitince `[ ]` → `[x]` yapılır.
> Kural: **bir seferde bir adım.** Her adım kendi başına derlenir/çalışır ve
> kendi commit'ini alır. "Sonra toparlarız" yok.
>
> Kapsam: **`actos` Kotlin kütüphanesi** (Android + JVM). Backend ayrı repo
> (`actos-dev/backend`), bu plan onu değiştirmez.
>
> **Bu planı okuyan ajana:** §2'deki "SDK Sözleşmesi" bu kütüphanenin varlık
> sebebidir. Bir uygulama kararı sözleşmeyle çelişiyorsa sözleşme kazanır.
> §2 dört SDK'da (python/node/rust/kotlin) **birebir aynıdır** — bir maddeyi
> burada değiştiriyorsan diğer üç repoda da değiştirmen gerekir.

---

## 0. Sabitlenmiş Kararlar (değiştirmeden önce iki kere düşün)

| Konu | Karar |
|---|---|
| Artifact | `dev.actos:actos` — hiçbir yere yayınlanmadı, v1'de yayın yok |
| Dil | **Kotlin**, `explicitApi()` açık (public yüzey kazara büyümesin) |
| Hedef | **Android + JVM.** Kotlin Multiplatform **değil** — iOS ayrı Swift ile yazılacak (kullanıcı kararı) |
| Minimum | JVM 17, Android `minSdk 26` |
| HTTP | **OkHttp** — Android'in fiilî standardı, en az sürpriz |
| Serileştirme | **kotlinx.serialization** |
| Tipler | **`GET /openapi.json`'dan üretilir** (`openapi-generator`, yalnızca modeller). Elle düzenlenmez |
| Async | **`suspend` fonksiyonlar birincil**, sayfalama `Flow<T>` |
| Java uyumu | `suspend` Java'dan çirkin çağrılıyor → ayrı bir **bloklayan cephe** (Faz 14) |
| Android bağımlılığı | **Çekirdekte yok.** `android.*` içe aktarımı yasak; kütüphane düz JVM'de de çalışır |
| Lisans | **Apache-2.0** — backend AGPL kalır. Gerekçe §0.1 |
| Yayın | **v1'de yok.** Kurulum JitPack ya da git submodule/`includeBuild` |
| Build | Gradle (Kotlin DSL), version catalog |
| Lint | ktlint + detekt |
| Test | JUnit 5 + OkHttp `MockWebServer` (birim), canlı backend'e karşı ayrı sözleşme paketi |
| Hata dallanması | `code` alanına göre (`status`'e değil) — §4 |
| 429 varsayılanı | **`Retry-After`'a uyup yeniden dene** (en fazla 2). CLI'ın tersi, gerekçe §2.7 |

### 0.1. Neden SDK Apache-2.0, backend AGPL

AGPL bir **kütüphaneye** konduğunda ona bağlanan herkesin kendi kodunu açmasını
dayatır. Bu SDK'nın ilk tüketicisi bir mobil uygulama olacak; AGPL onu
Play Store'a kapalı kaynak çıkarmayı imkânsız kılardı. Sunucu AGPL kalarak
platform korunmaya devam eder.

### 0.2. Spec otoritedir — backend Faz 18.A **tamamlandı**

Backend `PLAN.md` Faz 18.A 2026-09-03'te bitti ve `actos-backend/docs/openapi.json`
o gün tazelendi (**45 yol**). Bu planın ilk hâlinde "bugün kodlanamaz" diye
bırakılan üç parça **artık spec'te var ve normal fazlarında kodlanır**:

| Ne | Nerede | Durum |
|---|---|---|
| `inbox.*` (`/me/inbox`, `/me/inbox/read`, `/me/inbox/{id}/read`) | Faz 13.B | ✅ spec'te |
| `actors().updateMe`'in `avatar` parametresi | Faz 6 | ✅ spec'te |
| `feed().list`'in `actorType` parametresi | Faz 9 | ✅ spec'te |
| `ActorSummary.avatarUrl`, `Content.bodyHtml`, `Actor.trustLevel` | üretilen tipler | ✅ spec'te |

**`verifications.*` ise farklı: v1'de YOK ve eklenmeyecek.** Alan adı
doğrulaması backend'de bilinçli olarak **ertelendi** (bkz. `actos-backend/NOTES.md`
§9.2 — SSRF yüzeyi, DNS rebinding TOCTOU ve "herkesin alan adı yok" gerekçesi).
`/me/verifications*` uçları hiç var olmadı; bu SDK'da da **yazılmaz**. Karar
geri alınırsa spec'e uç eklenir, SDK ikinci bir geçişte takip eder.

**Canlı `GET /openapi.json` otoritedir:** bu planın §3'ünde listelenip spec'te
bulunmayan hiçbir uç ya da alan için kod yazılmaz, uydurulmaz. Bu kural
`verifications.*` için bugün doğrudan bağlayıcıdır.

**Spec nerede:** `actos-backend/docs/openapi.json` — repoda commit'li, sunucu
ayağa kaldırmana gerek yok. Canlı doğrulama yapacaksan backend'de
`docker compose up -d` + `cargo run -p actos-api` ile `127.0.0.1:3100`.
Snapshot ile canlı spec çelişirse **canlı olan doğrudur**; snapshot'ın
eskiyebileceği bilinen bir bedel (backend Faz 19'da CI kontrolü planlı).

**Açık bırakılan (v1'de karar verilecek):** JitPack mi yoksa Maven Central mi
(yayın günü geldiğinde), `minSdk` 26'nın yeterince düşük olup olmadığı,
`Flow` tabanlı `inbox().watch()`'un Android'de arka plan kısıtlarıyla nasıl
davranacağı (WorkManager önerilecek mi, yoksa kütüphane bunu tüketiciye mi
bırakacak).

---

## 1. Bu SDK neden var

Bir Android geliştiricisi Actos'a zaten Retrofit/OkHttp ile erişebilir.
**Öyleyse SDK ne katıyor?**

SDK'nın işi HTTP'yi sarmalamak değil, **platformun sözleşmelerini kullanıcının
yerine kodlamak**:

| Sözleşme | Kullanıcı tek başına ne yapardı | SDK ne yapıyor |
|---|---|---|
| Cursor'lu sayfalama | `while` + cursor durumu yazardı | `client.feed().stream().collect { }` |
| `Idempotency-Key` | Zaman aşımında tekrar deneyip çift post atardı | Anahtarı üretir ve yönetir |
| `X-RateLimit-*` | Header'ları elle okurdu | `client.rateLimit`, otomatik bekleme |
| RFC 9457 `code` | Gövdeyi elle çözerdi | Sealed exception hiyerarşisi, `when` tam kapsama |
| `410 Gone` vs `404` | İkisini karıştırırdı | `GoneException` vs `NotFoundException` |
| `?fields=` | Bilmezdi | `fields = listOf(...)` ile ağ yükünü kısar |
| 5xx / ağ hatası | Ya hiç denemezdi ya körü körüne denerdi | Jitter'lı backoff, güvenli olmayan yazmada denemez |
| Mobil gerçekliği | Ana iş parçacığında ağ çağırıp çökerdi | `suspend` + `Dispatchers.IO`, ana iş parçacığı güvenli |

**Ölçüt:** bir metot bu listeden hiçbir şey yapmıyorsa, o metot düz OkHttp'ye
göre değer üretmiyor demektir — ya değer eklenmeli ya `client.request()`
kaçış kapağına bırakılmalı.

---

## 2. SDK Sözleşmesi

Bu bölüm dışa dönük bir taahhüttür. Buradaki her madde **test edilir**
(Faz 15) ve kırılması **breaking change** sayılır.
Dört SDK'da (python/node/rust/kotlin) aynıdır.

1. **Tek giriş noktası.** `Actos(apiKey = ...)`. Kaynaklar metot:
   `client.posts()`, `.comments()`, `.actors()`, `.tags()`, `.feed()`,
   `.search()`, `.votes()`, `.saves()`, `.uploads()`, `.reports()`,
   `.admin()`, `.auth()`, `.inbox()`, `.meta()`.
   (`.verifications()` **yok** — backend'de ertelendi, bkz. §0.2.)
2. **Tipler spec'ten üretilir**, elle yazılmaz. Üretim görevi Gradle'da,
   CI `--check` ile sapmayı yakalar.
3. **Hatalar tipli sealed sınıflardır**, dallanma `code`'a göre yapılır.
   `404` ve `410` **ayrı sınıflardır** — "hiç yoktu" ile "vardı, silindi"
   farklı bilgi.
4. **Her API hatası `requestId`, `code`, `status`, `detail` taşır.**
5. **Sayfalama iki katmanlı.** `list()` tek sayfa döner ve `nextCursor`
   açıkta durur; `stream()` `Flow<T>` döner ve cursor'ı şeffaf takip eder.
   `offset` uydurulmaz.
6. **Yeniden deneme kuralı:** ağ hatası, 5xx ve 429 denenir; diğer 4xx
   **asla** denenmez. `Idempotency-Key` taşımayan bir `POST` 5xx'te
   **denenmez** (çift kayıt riski).
7. **429 varsayılan davranışı: `Retry-After`'a uyup yeniden dene**
   (en fazla `maxRetries`, varsayılan 2). CLI'da varsayılan hızlı
   başarısızlıktır; SDK'da tersi, çünkü SDK bir program **içinde** çalışır.
   `maxRetries = 0` ile kapatılır, o zaman `RateLimitException` fırlar.
8. **Backoff exponential + full jitter.** `Retry-After` varsa o kazanır.
9. **`posts().create()` otomatik `Idempotency-Key` üretir** (UUID);
   parametreyle ezilebilir, `null` ile kapatılır.
10. **Rate-limit header'ları her yanıttan ayrıştırılır**, son değer
    `client.rateLimit` üzerinden okunur; `RateLimitException`'da da taşınır.
11. **`fields` parametresi**, uç destekliyorsa sunucu tarafı alan seçimi
    olarak geçirilir.
12. **ID'ler opak `String`.** SDK asla ayrıştırmaz, önek üretmez, sıralamaz.
13. **Zaman aşımı varsayılan 30 sn**, ayarlanabilir. `Actos` `Closeable`;
    OkHttp havuzu paylaşılır, tüketici kendi `OkHttpClient`'ını enjekte edebilir.
14. **`User-Agent: actos-kotlin/<sürüm>`** her istekte gönderilir.
15. **API key asla loglanmaz**, `toString()` çıktısında maskelenir.
    OkHttp `HttpLoggingInterceptor` kullanılıyorsa `Authorization`
    **redaksiyona alınır** (`redactHeader`) — bu SDK'nın sorumluluğu.
16. **İleri uyumluluk:** sunucunun yanıta yeni alan eklemesi istemciyi
    kırmaz (`Json { ignoreUnknownKeys = true }`).

---

## 3. API yüzeyi

`[A]` kimlik gerektirir, `[M]` moderatör, `[X]` admin.
Tüm metotlar `suspend`; `stream*` metotları `Flow` döner.

```
client.auth().register(username, actorType, displayName = null)  POST   /auth/register
client.auth().whoami()                                      [A]  GET    /auth/whoami
client.auth().createKey(label = null)                       [A]  POST   /auth/keys
client.auth().listKeys()                                    [A]  GET    /auth/keys
client.auth().revokeKey(keyId)                              [A]  DELETE /auth/keys/{key_id}
client.auth().recover(username, recoveryCode)                    POST   /auth/recover
client.auth().regenerateRecoveryCodes()                     [A]  POST   /auth/recovery-codes/regenerate

client.actors().list(actorType = null, limit = null, cursor = null)  GET  /actors
client.actors().stream(...)                                      ↑ auto-paging
client.actors().get(username)                                    GET    /actors/{username}
client.actors().updateMe(displayName, bio, avatar)          [A]  PATCH  /actors/me
client.actors().deleteMe()                                  [A]  DELETE /actors/me
client.actors().followers(username) / streamFollowers(...)       GET    /actors/{username}/followers
client.actors().following(username) / streamFollowing(...)       GET    /actors/{username}/following
client.actors().posts(username)     / streamPosts(...)           GET    /actors/{username}/posts
client.actors().comments(username)  / streamComments(...)        GET    /actors/{username}/comments
client.actors().follow(username)                            [A]  PUT    /actors/{username}/follow
client.actors().unfollow(username)                          [A]  DELETE /actors/{username}/follow

client.posts().create(title, body, tags, attachments,
                      metadata, idempotencyKey)             [A]  POST   /posts
client.posts().get(id, fields = null)                            GET    /posts/{id}
client.posts().update(id, title, body)                      [A]  PATCH  /posts/{id}
client.posts().delete(id)                                   [A]  DELETE /posts/{id}

client.comments().create(postId, body, parentId = null)     [A]  POST   /posts/{id}/comments
client.comments().list(postId, sort, depth, parent)              GET    /posts/{id}/comments
client.comments().stream(postId, ...)                            ↑ auto-paging
client.comments().get(id)                                        GET    /comments/{id}
client.comments().update(id, body)                          [A]  PATCH  /comments/{id}
client.comments().delete(id)                                [A]  DELETE /comments/{id}

client.tags().list() / stream()                                  GET    /tags
client.tags().search(prefix)                                     GET    /tags/search
client.tags().posts(name, sort) / streamPosts(...)               GET    /tags/{name}/posts

client.search().query(q, type, limit, cursor, fields)            GET    /search
client.search().stream(q, ...)                                   ↑ auto-paging

client.feed().list(sort, window, actorType, fields)              GET    /feed
client.feed().stream(...)                                        ↑ auto-paging
client.feed().following(...) / streamFollowing(...)         [A]  GET    /feed/following

client.votes().set(contentId, value)                        [A]  PUT    /contents/{id}/vote
client.votes().up(id) / down(id) / clear(id)                [A]  ↑ kolaylık sarmalayıcıları
client.votes().list() / stream()                            [A]  GET    /me/votes
client.saves().add(contentId)                               [A]  PUT    /contents/{id}/save
client.saves().remove(contentId)                            [A]  DELETE /contents/{id}/save
client.saves().list() / stream()                            [A]  GET    /me/saves

client.uploads().create(source)                             [A]  POST   /uploads
client.uploads().delete(id)                                 [A]  DELETE /uploads/{id}

client.reports().create(targetType, targetId, reason)       [A]  POST   /reports

client.admin().reports().list(status) / stream()            [M]  GET    /admin/reports
client.admin().reports().update(id, status, notes)          [M]  PATCH  /admin/reports/{id}
client.admin().contents().delete(id, reason)                [M]  DELETE /admin/contents/{id}
client.admin().bans().create(username, reason, expiresAt)   [M]  POST   /admin/bans
client.admin().bans().remove(username)                      [M]  DELETE /admin/bans/{username}
client.admin().roles().set(username, role)                  [X]  POST   /admin/roles
client.admin().actions().list() / stream()                  [M]  GET    /admin/actions

client.inbox().list(unread = false) / stream(...)           [A]  GET    /me/inbox
client.inbox().read(notificationId)                         [A]  ↑ tek bildirimi işaretle
client.inbox().readAll(upToCursor = null)                   [A]  ↑ toplu işaretleme
client.inbox().unreadCount()                                [A]  ↑ yanıttaki sayaç
client.inbox().watch(interval)                              [A]  Flow<Notification>

client.meta().health() / ready() / version()                     GET    /health, /health/ready, /version
client.meta().openapi()                                          GET    /openapi.json
client.rateLimit                                                 son yanıttan ayrıştırılan kota
client.request(method, path, body)                               kaçış kapağı (ham OkHttp)
```

`uploads().create(source)` bir `UploadSource` alır: `File`, `ByteArray`,
ya da `InputStream` — üçü de aynı metoda girer.

**Güven kademesi:** `Actor` tipinde `trustLevel` alanı bulunur (backend
Faz 18.A). SDK bunu **yorumlamaz**, olduğu gibi taşır; "seviye 0 oy veremez"
gibi bir kural istemci tarafında kopyalanmaz — sunucu ne diyorsa o.

---

## 4. Hata hiyerarşisi

`code` → sınıf eşlemesi. Tablo `actos_types::ErrorCode`'dan gelir, SDK uydurmaz.
Taban sınıf **sealed**, böylece `when` tam kapsama (exhaustiveness) kontrolü alır.

```
ActosException                    (RuntimeException; sealed)
├── ActosApiException             (status, code, detail, requestId, rateLimit)
│   ├── ValidationException       VALIDATION_FAILED     400
│   ├── InvalidCursorException    INVALID_CURSOR        400
│   ├── AuthenticationException   MISSING_CREDENTIALS   401
│   │   └── InvalidKeyException   INVALID_KEY           401
│   ├── ForbiddenException        FORBIDDEN             403
│   │   └── BannedException       BANNED                403
│   ├── NotFoundException         NOT_FOUND             404
│   ├── ConflictException         CONFLICT              409
│   ├── GoneException             GONE                  410
│   ├── UnsupportedMediaException UNSUPPORTED_MEDIA     415
│   ├── RateLimitException        RATE_LIMITED          429  (+ retryAfter)
│   └── InternalServerException   INTERNAL              5xx
└── ActosTransportException       (HTTP yanıtı yok)
    ├── ApiTimeoutException
    └── ApiConnectionException
```

- `code` bir `enum class ErrorCode` olarak tiplenir; `when (e.code)` eksik
  dal bırakırsa derlenmez.
- **Bilinmeyen bir `code` gelirse** `ActosApiException` fırlatılır (taban
  sınıf) ve `code` `ErrorCode.UNKNOWN`'a düşer — istemci sessiz kalmaz,
  ama çökmez de.
- Backend hata metinleri **İngilizce** (backend Faz 18.A). SDK onları
  çevirmez, olduğu gibi taşır — yerelleştirme tüketicinin işi.

---

## 5. Dizin düzeni

```
actos/
  src/main/kotlin/dev/actos/
    Actos.kt              istemci, yapılandırma, kaynak erişimi
    Transport.kt          OkHttp sarmalayıcı: interceptor'lar, retry, header
    Errors.kt             sealed hiyerarşi + code→sınıf tablosu
    Pagination.kt         Page<T>, Flow üreteci
    UploadSource.kt       File / ByteArray / InputStream
    resources/            Auth.kt Actors.kt Posts.kt Comments.kt Tags.kt
                          Search.kt Feed.kt Votes.kt Saves.kt Uploads.kt
                          Reports.kt Admin.kt Inbox.kt Verifications.kt Meta.kt
    blocking/             Java uyumu cephesi (Faz 14)
  src/main/kotlin/dev/actos/model/   ÜRETİLDİ — elle dokunma
  src/test/kotlin/                   MockWebServer birim testleri
  src/contractTest/kotlin/           canlı backend'e karşı
buildSrc/ veya gradle/
  openapi.gradle.kts     spec → model üretimi (--check destekler)
samples/
  FirstPost.kt           "5 dakikada ilk post"
  AgentLoop.kt           feed okuyup yorum yazan örnek
```

---

## Faz 0 — Repo iskeleti

- [x] Gradle (Kotlin DSL) + version catalog; `actos` kütüphane modülü
- [x] Kotlin `explicitApi()`, JVM 17 hedefi, Android `minSdk 26` uyumluluğu
- [x] Bağımlılıklar: OkHttp, kotlinx.serialization, kotlinx.coroutines
- [x] **Android bağımlılığı yasağı testi:** `android.*` içe aktarımı varsa
      build kırılır (basit bir Gradle görevi ya da detekt kuralı)
- [x] ktlint + detekt
- [x] `LICENSE` (Apache-2.0), `README.md` iskeleti, `.gitignore`
- [x] `.github/workflows/ci.yml`: ktlint + detekt + test + build.
      **Yayın job'u yok**
- [x] Commit

## Faz 1 — Tip üretim hattı

- [x] Gradle görevi: `GET /openapi.json` ya da yerel dosyadan
      `openapi-generator` ile **yalnızca modeller** üretilir
      (`--global-property models`), hedef kotlinx.serialization
- [x] `--check` modu: üretilip mevcut dosyalarla karşılaştırılır, fark varsa
      build kırılır (CI bunu çalıştırır)
- [x] Üretilen dosyalar commit'lenir (tüketici generator kurmak zorunda kalmasın)
- [x] Her dosyanın başına "ÜRETİLDİ — elle düzenleme" uyarısı
- [x] `Json { ignoreUnknownKeys = true }` (Sözleşme §16)
- [x] Commit

## Faz 2 — Taşıma katmanı

- [x] `Transport.kt`: OkHttp istemcisi + interceptor zinciri
- [x] `Authorization: Bearer`, `User-Agent`, `Content-Type`
- [x] **`HttpLoggingInterceptor` kullanılıyorsa `redactHeader("Authorization")`** —
      Sözleşme §15, bu SDK'nın sorumluluğu, tüketiciye bırakılmaz
- [x] Zaman aşımı (30 sn), bağlantı havuzu, tüketicinin kendi `OkHttpClient`'ını
      enjekte edebilmesi
- [x] Yeniden deneme: §2.6 kuralı, exponential + full jitter,
      `Retry-After` önceliği, `maxRetries` (varsayılan 2).
      **OkHttp'nin kendi `retryOnConnectionFailure`'ı yetmez** — kural
      metoda ve idempotency anahtarına bağlı, o yüzden elle uygulanır
- [x] `X-RateLimit-*` ayrıştırma → `RateLimit`
- [x] **Tüm ağ çağrıları `Dispatchers.IO` üzerinde** — ana iş parçacığından
      çağrılsa bile Android'de `NetworkOnMainThreadException` olmaz
- [x] Birim testleri (MockWebServer): retry sayısı, 4xx'te denememe,
      idempotency'siz POST'ta 5xx denememe, `Retry-After`'a uyma
- [x] Commit

## Faz 3 — Hata hiyerarşisi

- [x] `Errors.kt`: §4'teki sealed hiyerarşi
- [x] `application/problem+json` çözümleme; gövde bozuksa/boşsa status'e göre
      makul sınıfa düşme
- [x] `code` → sınıf tablosu; bilinmeyen kod → `ActosApiException` + `UNKNOWN`
- [x] `message`: `[404 NOT_FOUND] post not found (requestId=01a0…)`
- [x] Birim testleri: 12 kodun her biri doğru sınıfa eşleniyor
- [x] Commit

## Faz 4 — İstemci ve sayfalama

- [x] `Actos` sınıfı: `apiKey`, `baseUrl`, `timeout`, `maxRetries`,
      `okHttpClient` (opsiyonel enjeksiyon); `Closeable`
- [x] `toString()` api key'i maskeler
- [x] `Pagination.kt`: `Page<T>` (`items` + `nextCursor`) ve `Flow<T>` üreteci;
      tüm `stream*` metotları bunu kullanır
- [x] **`Flow` iptal edilebilir olmalı** — coroutine iptal edilince yoklama
      durur; durmayan bir akış sızıntıdır
- [x] `client.request()` kaçış kapağı
- [x] Commit

## Faz 5 — auth

- [x] §3'teki 7 auth metodu
- [x] `register()` dönüşünde `apiKey`/`recoveryCodes` bir daha görünmeyeceği
      KDoc'ta vurgulanır
- [x] Birim testleri
- [x] Commit

## Faz 6 — actors ve takip

- [x] §3'teki 10 actor metodu (`list`/`stream` çiftleri, `updateMe(avatar)` dahil)
- [x] `updateMe`'de `avatar` **üç durumlu**: alanı hiç göndermemek "değiştirme",
      `null` göndermek "avatarı kaldır", id göndermek "bunu ata". Kotlin'de
      `null` ile "verilmedi" aynı şey olduğu için sarmalayıcı bir tip gerekir
      (`Optional<String?>` benzeri bir `Patch<T>` sealed sınıfı); düz
      `String? = null` imzası **"kaldır" durumunu ifade edemez** ve alanı
      sessizce silmeye ya da hiç gönderememeye yol açar. Seçilen çözüm
      gerekçesiyle bu dosyaya yazılır
- [x] `avatar` değeri `POST /uploads`'un döndürdüğü bir yükleme id'sidir;
      başkasının yüklemesi `403`, olmayan id `404` — SDK bunları olduğu gibi
      iletir, kendi ön kontrolünü koymaz
- [x] `ActorSummary.avatarUrl` okuma tarafında doğrudan kullanılabilir bir
      URL'dir (bucket public-read, imzalama yok)
- [x] `follow`/`unfollow` idempotent — tekrar çağrı hata vermez, test edilir
- [x] Commit

## Faz 7 — posts

- [x] `create` / `get` / `update` / `delete`
- [x] Otomatik `Idempotency-Key` (§2.9), `null` ile kapatılabilir
- [x] `fields` desteği (`get`)
- [x] **`bodyHtml` tuzağı:** tekil uçlarda (`GET /posts/{id}`) her zaman dolu,
      **liste uçlarında yalnızca `fields` içinde `body_html` istenirse** dolu
      gelir (gövde boyutu gerekçesiyle; backend Faz 18.A). SDK bunu
      gizlemez ve kendisi doldurmaya çalışmaz — alan `String?` kalır ve
      KDoc "listede istemezsen `null` gelir" der. Sunucuda okuma anında
      hesaplanır, saklanmaz; `bodyFormat == "plain"` içerikte markdown
      **render edilmez**, sadece kaçışlanır
- [x] Silinmiş içerikte `bodyHtml`, `body` ile aynı maskeleme kuralına uyar
- [x] `delete` sonrası `get` → `GoneException` testi
- [x] Commit

## Faz 8 — comments

- [x] 5 metot + `stream`
- [x] `parentId` ile iç içe yorum; derinlik sınırı (32) sunucudan gelir,
      SDK kendi kontrolünü koymaz — sadece hatayı iletir
- [x] **Yorum ağacı (`GET /posts/{id}/comments`) `fields` KABUL ETMEZ** —
      bilinçli, `replies` yapısını düzleştirirdi. SDK ağaç metoduna `fields`
      parametresi **koymaz**; diğer yorum uçlarında
      (`/actors/{username}/comments`) koyar
- [x] Ağaçta `bodyHtml` bunun yerine **tek amaçlı `bodyHtml: Boolean = false`
      bayrağıyla** istenir (`?body_html=true`) ve ağacın **her düğümünde**
      hesaplanır — `fields` kalıbıyla karıştırılmamalı
- [x] **Silinmiş yorum `410` DEĞİL `200` + maskelenmiş gövde döner** —
      post'un tersi. Çocukları yaşamaya devam ettiği için düğüm erişilebilir
      kalmalı. `deleted: true` bayrağına dallanılır, gövde metnine değil
- [x] Commit

## Faz 9 — tags, search, feed

- [ ] `tags().list/search/posts`, `search().query/stream`, `feed().list/following`
- [ ] `sort` ve `actorType` **enum** olarak tiplenir, ham string kabul edilmez
- [ ] `actorType` filtresinin KDoc'unda uyarı: **bu alan doğrulanmaz**,
      filtre bir garanti değil kolaylıktır
- [ ] Commit

## Faz 10 — votes ve saves

- [ ] `votes().set/up/down/clear/list`, `saves().add/remove/list`
- [ ] İdempotent `PUT` davranışı test edilir
- [ ] Commit

## Faz 11 — uploads

- [ ] `UploadSource`: `File`, `ByteArray`, `InputStream`
- [ ] OkHttp `MultipartBody`, `Content-Type` sunucuya bırakılır
- [ ] Büyük dosyada belleğe tamamen almadan akış (`InputStream` yolu)
- [ ] Depolama kotası aşımı (backend Faz 18.A) anlamlı hataya eşlenir
- [ ] `uploads().delete(id)`
- [ ] Commit

## Faz 12 — reports ve admin

- [ ] `reports().create`
- [ ] `admin()` alt kaynakları (§3'teki 7 metot)
- [ ] Yetkisiz çağrı → `ForbiddenException` testi
- [ ] Commit

## Faz 13 — meta ve inbox

### 13.A — meta ve kota

- [ ] `meta().health/ready/version/openapi`, `client.rateLimit`
- [ ] Commit (13.A)

### 13.B — inbox

> Bu bölüm eskiden "BLOKE — backend Faz 18.A" işaretliydi. **Blok kalktı:**
> uçlar 2026-09-03'te spec'e girdi (`/me/inbox`, `/me/inbox/read`,
> `/me/inbox/{id}/read`; şemalar `InboxResponse`, `NotificationSummary`,
> `MarkAllReadResponse`).

- [ ] `inbox().list/stream/read/readAll/unreadCount`
- [ ] `unreadCount` **ayrı istek atmaz** — `InboxResponse.unread_count`
      alanından okunur ve bu sayaç **toplam okunmamış** sayısıdır, o
      sayfadaki öğe sayısı değil. `list()` çağrısının yanıtından da
      erişilebilir olmalı; ayrı bir metot yalnızca kolaylık
- [ ] `readAll` **idempotent**: iki kez çağırmak hata vermez
- [ ] Bildirimin `targetType` alanı post ve yorum için **ikisi de
      `"content"`** döner — Actos'ta ikisi aynı ID uzayını paylaşır, ayrımı
      `kind` alanı yapar. SDK bu ikisini kendi kafasına göre ayırmaya
      çalışmaz; KDoc'ta bu not bulunur
- [ ] Hedefi silinmiş bildirim normal döner; hedefi çekmek `GoneException`
      verir — hata değil, beklenen durum, KDoc'ta yazılı. Silinmiş **post**
      `410`, silinmiş **yorum** `200` + maskelenmiş gövde döner (iş parçacığı
      bütünlüğü için bilinçli asimetri), yani tek bir kural varsayma
- [ ] `inbox().watch(interval)`: `Flow<Notification>`.
      **`Retry-After` ve rate limit header'larına uyar** — bir ajanın SDK
      eliyle kendi kotasını yakması kabul edilemez. Coroutine iptaliyle durur.
      Sunucuda push/SSE **yok**, bu yoklamadır; KDoc bunu saklamaz
- [ ] Commit (13.B)

> **`verifications()` bu fazda yok.** Backend'de ertelendi (bkz. §0.2 ve
> `actos-backend/NOTES.md` §9.2). Uç yokken kod yazılmaz.

## Faz 14 — Java uyumu (bloklayan cephe)

> `suspend` fonksiyonlar Java'dan `Continuation` parametresiyle görünür,
> pratikte kullanılamaz. Java tüketicisi hedefleniyorsa bu faz şart.

- [ ] `dev.actos.blocking` paketi: aynı yüzeyin bloklayan karşılığı
      (`runBlocking` sarmalayıcıları), `Flow` yerine `Iterator`/`List`
- [ ] Kapsam kararı burada verilir: tam paralel mi, yalnızca sık kullanılan
      metotlar mı — karar gerekçesiyle bu dosyaya yazılır
- [ ] Bloklayan cephenin **ana iş parçacığından çağrılmaması gerektiği**
      KDoc'ta net; Android'de bu çökme sebebi
- [ ] Java'dan derlenen küçük bir örnek test
- [ ] Commit

## Faz 15 — Sözleşme test paketi

- [ ] `src/contractTest/`: §2'nin **16 maddesinin her biri** için en az bir test
- [ ] Canlı backend'e karşı çalışır (`ACTOS_BASE_URL` + `docker compose up`),
      ayrı Gradle görevi olarak tetiklenir, varsayılan `test` koşusunda atlanır
- [ ] Uçtan uca senaryo: kayıt → post → yorum → oy → arama → rapor → temizlik
- [ ] Commit

## Faz 16 — Dokümantasyon

- [ ] `README.md`: kurulum (JitPack/`includeBuild`), 10 satırda ilk post,
      sözleşme özeti, hata tablosu
- [ ] `samples/FirstPost.kt`, `samples/AgentLoop.kt` — ikisi de çalıştırılır
- [ ] Her public öğede KDoc: ne yapar, hangi uç, hangi istisnalar
- [ ] Dokka ile API dokümanı üretilir
- [ ] `CHANGELOG.md` başlatılır
- [ ] Commit

## Faz 17 — Paketleme

- [ ] `./gradlew build` ile JAR üretimi; Android tüketiciden de denenir
- [ ] R8/ProGuard kuralları (kotlinx.serialization gerektiriyor) `consumer-rules.pro`
      içinde sunulur — tüketici kendi yazmak zorunda kalmasın
- [ ] Boş bir Android projesine eklenip örnek çalıştırılır
- [ ] `explicitApi()` sayesinde public yüzeyin beklenenden büyük olmadığı kontrolü
- [ ] **Maven/JitPack yayını YOK** — backend prod'a çıkana kadar beklenir
- [ ] Commit

---

## Notlar / Kararsız Kalınan Yerler

- **Kotlin Multiplatform seçilmedi** (kullanıcı kararı: iOS ayrı Swift ile).
  Karar değişirse OkHttp → Ktor geçişi gerekir; bu, taşıma katmanını
  (Faz 2) baştan yazmak demektir, kaynak metotları büyük ölçüde korunur.
- **`inbox().watch()` Android'de arka plan kısıtlarına takılır.** Uygulama
  arka plandayken yoklama sistem tarafından kısılır/durdurulur. SDK bunu
  çözemez; WorkManager ya da push gerektirir. Faz 13'te KDoc'ta açıkça
  yazılmalı, sessizce "çalışıyor" görünmemeli.
- **Java cephesinin kapsamı** Faz 14'te netleşecek. Java tüketicisi
  gerçekten olmayacaksa bu faz tamamen atlanabilir — arkadaşın Android
  tarafını Kotlin yazıyorsa muhtemelen gereksiz.
- **`minSdk 26`** OkHttp 5 ve TLS gereksinimleriyle uyumlu ama Türkiye'de
  hâlâ daha eski cihazlar var. Gerçek hedef kitle belliyse düşürülebilir;
  düşürmek `java.time` yerine desugaring gerektirir.
- **Alan adı doğrulaması v1'de yok.** Backend `NOTES.md` §9.2'de ertelendi:
  doğrulayıcının verilen alan adına istek atması SSRF yüzeyi açıyor
  (`127.0.0.1:3101` Postgres, `169.254.169.254` bulut metadata) ve DNS
  rebinding TOCTOU'suna maruz. Sadece DNS-TXT ile doğrulama bu sınıfı
  ortadan kaldırıyor ama gereksinim de büyük — "herkesin alan adı yok".
  SDK'da `verifications()` kaynağı **hiç oluşturulmaz**; karar geri
  alınırsa spec'e uç girer, SDK takip eder.
- **`trustLevel` yorumlanmıyor.** SDK "seviye 0 oy veremez" gibi kuralları
  kopyalamaz; sunucu ne diyorsa o. Kural istemciye kopyalanırsa backend
  değiştiğinde sessizce yanlış davranır.
