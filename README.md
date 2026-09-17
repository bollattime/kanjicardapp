# Kanji Kartları / Kanji Cards

JLPT N5–N1 arası 2.212 kanji için kart uygulaması: Türkçe/İngilizce arayüz, favoriler, radikaller, sesli okuma.
Veriler GitHub'da tutulur; Google Play'deki uygulama açılışta yeni veri olup olmadığına bakar ve indirir.

```
data/ düzenle ──push──▶ GitHub Actions (tools/build.py) ──▶ docs/ ──▶ GitHub Pages
                                                                     │
          Google Play'deki uygulama ◀── version.json + data.json ────┘
          (internet yoksa kendi içindeki veriyle çalışır)
```

## Klasörler

| Klasör / dosya | Ne işe yarar |
|---|---|
| `data/kanji/n5.json … n1.json` | Kanjiler, **her satır bir kanji** |
| `data/vocabulary/n5.json … n1.json` | Örnek kelimeler, **her satır bir kelime** |
| `data/radicals/radicals.json` | 194 radikal |
| `data/i18n/tr.json`, `en.json` | Arayüz metinleri |
| `data/config.json` | GitHub Pages adresi ve gereken en düşük uygulama sürümü |
| `src/app.template.html` | Uygulama arayüzü (veri içermez) |
| `src/privacy.html` | Gizlilik politikası |
| `tools/build.py` | `data/` → `docs/` ve Android `assets/` |
| `docs/` | GitHub Pages'te yayınlanan dosyalar (otomatik üretilir) |
| `android/app/` | Android Studio uygulama modülü |
| `.github/workflows/build.yml` | `data/` değişince `docs/`'u otomatik günceller |
| `store/` | Play mağazası simgesi (512×512) ve tanıtım görseli (1024×500) |

---

## 1. GitHub kurulumu

### 1.1 Depoyu oluştur
1. GitHub'da **New repository** → ad örn. `kanji-kartlari` → **Public** → oluştur.
   (GitHub Pages'in ücretsiz planda çalışması için depo herkese açık olmalı.)
2. Bu klasörü yükle:
   ```bash
   cd kanji-kartlari
   git init
   git add .
   git commit -m "İlk sürüm"
   git branch -M main
   git remote add origin https://github.com/KULLANICI/kanji-kartlari.git
   git push -u origin main
   ```
   `.gitignore` imza anahtarlarını (`*.jks`, `*.keystore`) ve derleme çıktılarını dışarıda bırakır.

### 1.2 Adresi ayarla
`data/config.json` dosyasında `KULLANICI` ve `DEPO` kısımlarını değiştir:
```json
{
  "remoteBaseUrl": "https://KULLANICI.github.io/kanji-kartlari/",
  "minAppVersion": 1
}
```
Sonra bilgisayarında bir kez `python3 tools/build.py` çalıştırıp commit + push yap.
Bu adres Android uygulamasının içine de yazılır, bu yüzden **AAB almadan önce** yapılmalı.

### 1.3 GitHub Pages'i aç
Depo → **Settings → Pages** → *Build and deployment*:
- Source: **Deploy from a branch**
- Branch: **main**, klasör: **/docs** → **Save**

Birkaç dakika sonra şu adresler çalışmalı:
- `https://KULLANICI.github.io/kanji-kartlari/` → web sürümü
- `https://KULLANICI.github.io/kanji-kartlari/version.json` → veri sürümü
- `https://KULLANICI.github.io/kanji-kartlari/privacy.html` → gizlilik politikası (Play için)

### 1.4 Otomatik derlemeye izin ver
Depo → **Settings → Actions → General → Workflow permissions** → **Read and write permissions** → Save.

Artık `data/` altındaki bir dosyayı GitHub web arayüzünde düzenleyip kaydettiğinde (commit) Actions otomatik çalışır, `docs/` güncellenir ve veri sürümü bir artar. Kullanıcıların uygulaması bir sonraki açılışta yeni veriyi indirir.
Hatalı bir satır varsa Actions kırmızı olur ve hangi dosyanın hangi satırında sorun olduğunu gösterir; yayındaki veri bozulmaz.

---

## 2. Android Studio'da proje ve AAB

### 2.1 Projeyi oluştur
1. Android Studio → **New Project → No Activity** (veya *Empty Views Activity*)
   - Name: `Kanji Kartları`
   - Package name: `com.ugur.kanjikart`
   - Language: **Java**
   - Minimum SDK: **API 24**
2. Oluşan projede **`app/src` klasörünü tamamen sil**, yerine bu depodaki `android/app/src` klasörünü kopyala.
3. `app/build.gradle.kts` dosyasını bu depodaki `android/app/build.gradle.kts` ile değiştir.
   - Şablonun `plugins { … }` ve `compileSdk` satırları farklı yazılmışsa (yeni Android Studio sürümlerinde olabilir) şablondakini bırak; değerlerin aynı olduğundan emin ol: `compileSdk 36`, `targetSdk 36`, `minSdk 24`, `buildConfig = true`.
   - `dependencies { … }` bloğunu silebilirsin; uygulama harici kütüphane kullanmıyor.
4. **File → Sync Project with Gradle Files**. Android Studio güncelleme önerirse (AGP, Gradle) kabul et.
5. Telefonu bağlayıp ▶ ile çalıştır ve dene.

> İpucu: Android Studio projesini doğrudan bu deponun `android/` klasörüne oluşturursan `tools/build.py` asset'leri doğrudan projeye yazar, kopyalama gerekmez.

### 2.2 Veri değişince uygulamayı güncellemek gerekir mi?
Hayır. Uygulama yeni veriyi GitHub'dan kendisi alır. Play'e yeni sürüm yüklemen yalnızca **kod** değiştiğinde gerekir. O durumda:
1. `app/build.gradle.kts` içinde `versionCode`'u 1 artır.
2. Eski uygulamanın okuyamayacağı bir veri değişikliği yaptıysan `data/config.json` → `minAppVersion`'u yeni `versionCode` yap. Eski sürümdeki kullanıcılar "Google Play'den güncelle" mesajı görür ve eski veriyle devam eder.

### 2.3 İmzalı AAB (App Bundle) al
1. **Build → Generate Signed App Bundle or APK → Android App Bundle → Next**
2. **Create new…** ile bir *upload key* oluştur (ör. `kanji-upload.jks`).
   Dosyayı ve şifresini **güvenli bir yerde yedekle**, depoya koyma.
3. Build variant: **release** → **Create**
4. Çıktı: `app/release/app-release.aab`

---

## 3. Google Play'e yükleme

1. **Play Console** hesabı aç (tek seferlik kayıt ücreti vardır) ve kimlik doğrulamasını tamamla.
2. **Create app** → ad, varsayılan dil, *App / Free*.
3. **Play App Signing**'i kabul et (varsayılan). Google uygulamayı kendi anahtarıyla imzalar; upload key'in kaybolursa sıfırlatabilirsin.
4. **Uygulama içeriği (App content)** formları:
   - *Gizlilik politikası*: `https://KULLANICI.github.io/kanji-kartlari/privacy.html`
     (`src/privacy.html` içindeki `ORNEK@EPOSTA.COM` adresini kendi e-postanla değiştir.)
   - *Veri güvenliği (Data safety)*: Kullanıcı verisi toplanmıyor ve paylaşılmıyor; internet yalnızca uygulama verisini indirmek için kullanılıyor.
   - *Reklam*: yok. *İçerik derecelendirme* anketini doldur.
5. **Mağaza kaydı**: kısa/uzun açıklama, `store/icon-512.png`, `store/feature-graphic-1024x500.png`, en az 2 telefon ekran görüntüsü.
   Açıklamanın sonuna atıf ekle:
   > Sözlük verileri: KANJIDIC2 ve JMdict (EDRDG, CC BY-SA 4.0); kanji listesi: kanji-data (MIT). Ayrıntılar uygulamada Ayarlar → Kaynaklar ve lisans bölümündedir.
6. **Kapalı test**: 13 Kasım 2023'ten sonra açılmış **kişisel** hesaplarda, üretime başvurmadan önce en az **12 test kullanıcısının 14 gün boyunca kesintisiz** kayıtlı kaldığı bir kapalı test gerekir.
   *Test → Closed testing* → yeni kanal → AAB'yi yükle → test kullanıcılarının e-postalarını ekle → katılım linkini paylaş.
7. 14 gün dolunca **Dashboard → Apply for production** → soruları yanıtla → onaydan sonra **Production** kanalına AAB'yi yükle.

---

## 4. Günlük kullanım

| Yapmak istediğin | Nasıl |
|---|---|
| Bir kelimenin anlamını düzeltmek | GitHub'da `data/vocabulary/nX.json` → ilgili satırı düzenle → Commit. Gerisi otomatik. |
| Kanjiye kelime eklemek | Aynı dosyada o kanjinin satırlarının arasına yeni satır ekle. Son satırdan sonra virgül olmamalı. |
| Yerelde kontrol | `python3 tools/build.py --check` |
| Arayüz metnini değiştirmek | `data/i18n/tr.json` / `en.json` |
| Web sürümünü görmek | `docs/index.html`'i tarayıcıda aç veya Pages adresine git |

### Veri biçimi
```json
{"kanji": "日", "level": "N5", "radical": "日", "on": ["ニチ", "ジツ"], "kun": ["ひ", "か"], "easy": "ひ", "meaning": {"tr": "gün, güneş", "en": "day, sun, Japan"}},
{"kanji": "日", "word": "毎日", "reading": "まいにち", "meaning": {"tr": "her gün", "en": "every day"}},
```
- `radical`: `radicals.json` içindeki `key` değerlerinden biri.
- `easy`: favorilerde gösterilen ve sesli okunan okunuş.
- Kelimeler kartta dosyadaki sırayla görünür.

## Güncelleme mekanizması (teknik)
- `tools/build.py` veri içeriğinin SHA-256 özetini çıkarır; içerik değiştiyse `dataVersion`'u bir artırır.
- Uygulama açılıştan ~1,5 sn sonra `version.json`'u indirir. `dataVersion` elindekinden büyükse `data.json`'u indirir, SHA-256 özetini ve yapısını doğrular, cihazda saklar ve ekranı yeniler.
- Doğrulama başarısız olursa veya internet yoksa eldeki veri kullanılmaya devam eder.
- Ayarlar → Veri bölümünden sürüm görülebilir ve elle denetlenebilir.

## Lisans
- Veriler: CC BY-SA 4.0 (`KAYNAKLAR.md`, `LICENSE-DATA.md`)
- Kod: proje sahibine ait; istersen `LICENSE` dosyası olarak MIT ekleyebilirsin.
