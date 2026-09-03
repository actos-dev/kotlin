# Yapılacaklar — Kotlin SDK

> **Bu repoda henüz kod yok** — `PLAN.md` dışında dosya bulunmuyor.
> Dolayısıyla "eksik" listesi = planın tamamı (105 madde, Faz 0–17).
> Plan 2026-09-03'te backend Faz 18.A sonrası gözden geçirildi ve
> güncellendi, yani **kodlamaya bugün olduğu gibi başlanabilir**.
>
> Son kontrol: 2026-09-03.

## 1. Durum

| Konu | Durum |
|---|---|
| Backend Faz 18.A | ✅ tamamlandı (2026-09-03) |
| Spec (`../actos-backend/docs/openapi.json`) | ✅ tazelendi, **45 yol** |
| Plan güncelliği | ✅ 18.A sonrası revize edildi |
| Bağımlılık engeli | **yok** — plan baştan sona uygulanabilir |

Bu SDK **diğer üçünden sonra** yazılıyor; node/python/rust'ta atlanan
parçalar burada baştan doğru yapılabilir. Aynı hataya düşülmemeli:
o üçü kodlanırken backend'de `/me/inbox` yoktu ve inbox kaynağı hiç
yazılmadı (bkz. `../node/YAPILACAKLAR.md`). **Burada öyle bir mazeret yok.**

## 2. Kodlamaya başlamadan mutlaka okunacaklar

Bunlar planda var ama gözden kaçarsa sessizce yanlış kod üretirler:

- **§0.2 — `verifications.*` YAZILMAYACAK.** Alan adı doğrulaması backend'de
  süresiz ertelendi (`../actos-backend/NOTES.md` §9.2: SSRF yüzeyi, DNS
  rebinding TOCTOU). `/me/verifications*` uçları hiç var olmadı. Planın ilk
  hâlinde bunlar "bloke" görünüyordu; **bloke değil, iptal**. §3 API
  yüzeyinden ve Faz 13'ten çıkarıldı.
- **Faz 6 — `avatar` üç durumlu.** `PATCH /actors/me` gövdesinde alanı hiç
  göndermemek "dokunma", `null` göndermek "kaldır", id göndermek "ata".
  Kotlin'de `null` ile "verilmedi" aynı şey olduğundan düz `String? = null`
  imzası **"kaldır" durumunu ifade edemez**; sarmalayıcı bir tip
  (`Patch<T>` benzeri sealed sınıf) gerekir. Bu, plan uyarmasa kesinlikle
  yanlış yazılacak yer.
- **Faz 7 — `bodyHtml` liste uçlarında `null` gelir**, yalnızca `fields`
  içinde istenirse dolar. Tekil uçlarda hep dolu.
- **Faz 8 — yorum ağacı `fields` almaz**, onun yerine tek amaçlı
  `?body_html=true` bayrağı var. İki mekanizma karıştırılmamalı.
- **Faz 8 — silinmiş yorum `410` değil `200` + maskeli gövde döner**;
  post'un tersi. `deleted` bayrağına dallanılır, metne değil.
- **Faz 13.B — `unreadCount` ayrı istek atmaz** (`InboxResponse.unread_count`)
  ve **toplam** okunmamış sayısıdır. `targetType` post ve yorum için ikisi de
  `"content"`; ayrımı `kind` yapar. `watch()` push değil **yoklamadır**.

## 3. Planda bilerek açık bırakılanlar (karar anı geldiğinde verilecek)

- **Faz 14 — Java uyum cephesinin kapsamı.** Tam paralel mi, yalnızca sık
  kullanılan metotlar mı? Android tarafı Kotlin yazılacaksa bu faz tamamen
  atlanabilir. Karar gerekçesiyle `PLAN.md`'ye yazılacak.
- **`minSdk 26` yeterince düşük mü.** Düşürmek `java.time` için desugaring
  gerektirir.
- **JitPack mı Maven Central mı** — yayın günü gelince. Backend prod'a
  çıkana kadar **yayın yok** (Faz 17'de kayıtlı).
- **`inbox().watch()` Android arka plan kısıtları.** Uygulama arka
  plandayken yoklama sistem tarafından kısılır; SDK bunu çözemez.
  WorkManager önerilecek mi, yoksa tüketiciye mi bırakılacak?

## 4. Sıra önerisi

Plan sırası doğru: Faz 0 → 17. Faz 13.B artık bloke değil, normal sırasında
yapılır. Faz 14 (Java cephesi) kapsam kararı verilmeden başlatılmamalı.
