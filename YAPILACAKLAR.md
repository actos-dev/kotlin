# Yapılacaklar — Kotlin SDK

> Durum: **Faz 0–17 tamamlandı**, PLAN.md 105/105 işaretli. `./gradlew build`
> yeşil, **101 test 0 başarısız**, çalışma ağacı temiz.
> Bu SDK dört SDK içinde **tek eksiksiz olanı**: inbox dahil her kaynak yazılı.
>
> Son kontrol: 2026-09-03.

## 1. Doğrulanan kritik noktalar

Plana özellikle uyarı olarak konan dört tuzağın hepsi doğru ele alınmış —
manuel kontrol edildi:

| Tuzak | Durum |
|---|---|
| `verifications()` yazılmamalı | ✅ Yok. Üstelik **var olmadığını doğrulayan bir test** var (`src/contractTest/.../ContractSixteenPointsTest.kt:67`) |
| `avatar` üç durumlu olmalı | ✅ `src/main/kotlin/dev/actos/Patch.kt` — `sealed interface Patch<out T>` / `Unchanged` / `Clear` / `Value`. Düz `String?` tuzağına düşülmemiş |
| Yorum ağacı `fields` almaz | ✅ `CommentsResource.kt:59` KDoc'ta açıkça yazılı; ağaç `bodyHtml: Boolean = false` bayrağı alıyor, `body_html` query'sine eşleniyor |
| `watch()` push değil yoklama | ✅ `InboxResource.kt:124-129` — "NETWORK / BATTERY NOTICE", Android arka plan sınırı ve `Retry-After` uyumu yazılı |

## 2. Kalan iş — yalnızca yayın

- [ ] **Maven Central / JitPack yayını.** Bilinçli olarak bekletiliyor:
      **backend prod'a çıkana kadar hiçbir paket yayınlanmayacak** (kullanıcı
      kararı, tüm SDK'lar için geçerli). `build.gradle.kts`'te yayın bloğu
      henüz yok, yalnızca `mavenCentral()` **repository** tanımı var — bu
      bağımlılık indirmek için, yayınlamak için değil. Karar anı geldiğinde:
      JitPack mı Maven Central mı (Central imzalama ve `sonatype` hesabı ister).
- [ ] **`minSdk 26` yeterince düşük mü?** Düşürmek `java.time` için
      desugaring gerektirir. Gerçek hedef kitle belli olunca karara bağlanır.

## 3. Küçük borç

- [ ] **Sözleşme testleri CI'da koşmuyor.** `.github/workflows/ci.yml`'de
      `contractTest` görevi geçmiyor; testler canlı backend istediği için
      (`ACTOS_BASE_URL` + `docker compose up`) varsayılan koşudan ayrı
      tutulmuş — bu tasarım gereği doğru, ama **hiçbir yerde otomatik
      koşmuyor** demek. Faz 19'da backend CI'ı kurulurken servisleri ayağa
      kaldıran bir iş olarak eklenmeli.
- [ ] **`build/` dizini repoda duruyor** (derleme çıktısı). `.gitignore`
      kapsıyor mu kontrol edilmeli — `git status` temiz olduğuna göre
      kapsıyor, ama repoda fiziksel olarak duruyor; klonlayan için sorun değil.

## 4. Diğer SDK'lardan farkı — ders

Node, Python ve Rust SDK'ları backend Faz 18.A'dan **önce** kodlandığı için
üçünde de inbox kaynağı eksik ve spec kopyaları eskimiş durumda
(bkz. o repoların `YAPILACAKLAR.md`'leri). Kotlin en son yazıldığı ve planı
18.A sonrası revize edildiği için bu borcun hiçbirini taşımıyor.

Pratik sonuç: **diğer üç SDK'yı hizaya çekerken referans bu repo olmalı** —
özellikle `Patch<T>` deseni, `verifications()`'ın yokluğunu doğrulayan test
ve yorum ağacının `bodyHtml` bayrağı.
