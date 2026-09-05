# Yayın (publish) — Kotlin SDK

> Durum: **yayınlanmadı, engelli.** Kod tarafı hazır (`./gradlew build`
> yeşil, 101 test, PLAN.md 105/105). Kalan iş yayın kanalı ve bir namespace
> kararı. Aceleye gerek yok — SDK'ların yayını backend prod'a çıkana kadar
> zaten bekliyor.
>
> Son güncelleme: 2026-09-05.

## Hazır olanlar

GitHub secret'ları `actos-dev/kotlin` reposuna kondu:

| Secret | Ne | Nereden geldi |
|---|---|---|
| `MAVEN_CENTRAL_USERNAME` | Sonatype Central Portal user token (kullanıcı adı) | `~/Documents/maven_key.txt` içindeki `<server>` bloğu |
| `MAVEN_CENTRAL_PASSWORD` | Aynı token'ın parola kısmı | aynı dosya |
| `GPG_PRIVATE_KEY` | Armored private key, **parolasız** | aşağıdaki yeni anahtar |
| `GPG_KEY_ID` | `B3900A47FF6DFEC7` | aynı |

### GPG anahtarı — neden yenisi üretildi

Yerelde zaten bir anahtar vardı (`38470C2D0B67A008`, 2026-08-24) ve
keyserver'lara yüklüydü, ama:

1. **Parolası bilinmiyor.** Bir ajan tarafından üretilmiş ve parola
   kaydedilmemiş; `gpg-agent` önbelleği boş, diskte hiçbir yerde yazılı
   değil. Parolasız `--export-secret-keys` çalışmıyor, yani secret'a
   konamıyor.
2. **O anahtar ccharts paketinde kullanımda.** Dolayısıyla iptal edilmedi,
   edilmemeli — ona hiç dokunulmadı.

Bu yüzden Actos'a **ayrı** bir anahtar üretildi. Ayrı olması zaten daha
doğru: iki projenin imza anahtarı birbirine bağlı kalmasın, birini
ileride iptal etmek diğerini düşürmesin.

```
sec   rsa4096/B3900A47FF6DFEC7  2026-09-05 [SC] [expires: 2029-09-04]
uid   Actos (release signing) <dethrandir@users.noreply.github.com>
```

Yayın durumu: `keyserver.ubuntu.com` → **var** (Maven Central'ın
doğruladığı yer burası). `keys.openpgp.org` → anahtar yüklendi ama kimlik
"unpublished"; o servis e-posta doğrulaması istiyor, Central için gerekli
değil.

Parolasız üretildi — bilinçli. CI için parolanın güvenlik katkısı yok:
private key de parola da aynı GitHub secret kasasında dururdu. Bedeli
`~/.gnupg`'deki yerel kopyanın korumasız olması; makineye erişen biri Actos
adına imza atabilir. Bu kabul edildi.

## ENGEL: namespace

`build.gradle.kts:10`:

```kotlin
group = "dev.actos"
```

**Bu namespace ile yayın yapılamaz.** Maven Central yalnızca sahipliğini
kanıtlayabildiğin bir namespace'e izin veriyor; `dev.actos`, `actos.dev`
alan adına sahip olmak demek. Elimizde `actos.com.tr` var, `actos.dev` yok.

Üç seçenek:

### A. `io.github.actos-dev` — en kolay

Central Portal'da GitHub org sahipliğiyle doğrulanıyor (kısa bir doğrulama
akışı, DNS gerekmiyor). Yeni bir alan adı almaya gerek yok.

Bedeli: paket koordinatı `io.github.actos-dev:actos-kotlin` olur —
`dev.actos:actos-kotlin` kadar temiz görünmez.

### B. `tr.com.actos` — sahip olduğumuz alan adından

`actos.com.tr`'nin ters çevrilmiş hâli. Central Portal'a DNS TXT kaydıyla
doğrulanır (Cloudflare'de bir kayıt eklemek yeterli, zaten oradayız).

Bedeli: `tr.com.actos` alışılmadık görünüyor; çoğu JVM geliştiricisi
`tr.com.*` biçimini ilk defa görecek. Teknik olarak tamamen geçerli.

### C. `actos.dev` alan adını al

Namespace olduğu gibi kalır, `build.gradle.kts` değişmez. Yıllık bir maliyet
ve `.dev` alan adının müsait olup olmadığına bağlı.

**Karar verilmedi.** Backend bittikten sonra bakılacak.

## Alternatif: Maven Central yerine JitPack

Bütün yukarıdaki iş (GPG, Sonatype token'ı, namespace doğrulaması) Maven
Central'ın şartları. JitPack hiçbirini istemiyor: bir git tag atarsın,
JitPack repoyu kendi derler ve servis eder.

Bedeli tüketiciye yansıyor — kullanan herkes `build.gradle`'ına ayrıca
JitPack deposunu eklemek zorunda kalır:

```kotlin
repositories {
    maven("https://jitpack.io")
}
```

Maven Central'da böyle bir şey gerekmez, `mavenCentral()` zaten herkeste
tanımlı. Bu yüzden Central tercih ediliyor — ama kanal hâlâ kesinleşmedi,
ve GPG/token tarafı bittiği için Central'a geçiş maliyeti artık düşük.

## Yayın iş akışı henüz yazılmadı

`.github/workflows/` altında yalnızca `ci.yml` var; publish adımı yok.
Namespace kararı verildikten sonra yazılacak. İhtiyaç duyacağı şeyler
(hepsi hazır): dört secret, `signing` + `maven-publish` Gradle eklentileri,
ve tag tetikleyicisi.

## Sıradaki adım

1. Backend prod'a çıksın (SDK yayınları ona bağlı).
2. Namespace kararı: A / B / C.
3. Publish workflow'u + ilk `v0.1.0` tag'i.
