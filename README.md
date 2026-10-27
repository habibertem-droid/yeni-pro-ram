# Bizim Ders Programı – Android Studio projesi

Bu proje, web uygulamasını (assets/index.html) bir **WebView** içinde çalıştıran, yüklenebilir bir Android uygulamasıdır.
İnternet bağlantısı olmadan çalışır; tüm veriler, şifre özetleri ve programlar cihazda (WebView localStorage) saklanır.

## Gereksinimler
- Android Studio (Koala / 2024.1 veya daha yenisi), JDK 17 (Android Studio ile birlikte gelir)
- Android 8.0 (API 26) ve üzeri cihaz veya emülatör

## Adımlar
1. Android Studio → **Open** → bu klasörü (`BizimDersAndroid`) seçin. İlk açılışta Gradle senkronizasyonu kendiliğinden başlar.
   (Gradle uyarısı verirse: *File → Settings → Build, Execution, Deployment → Gradle → Use Gradle from: "gradle-wrapper.properties" file*.)
2. **Excel desteğini çevrimdışı yapmak için (önerilir):**
   https://cdnjs.cloudflare.com/ajax/libs/xlsx/0.18.5/xlsx.full.min.js adresindeki dosyayı indirin ve
   `app/src/main/assets/` klasörüne `xlsx.full.min.js` adıyla koyun.
   Dosya yoksa uygulama, Excel işlemlerinde bu kütüphaneyi internetten (yedek olarak) yüklemeye çalışır.
   Dosyayı koyduktan sonra `AndroidManifest.xml` içindeki `INTERNET` iznini silebilirsiniz.
3. Telefonu USB ile bağlayın (Geliştirici seçenekleri → USB hata ayıklama açık) veya bir emülatör seçip **Run ▶** tuşuna basın.
4. **APK almak için:** *Build → Build Bundle(s) / APK(s) → Build APK(s)*.
   **Google Play için:** *Build → Generate Signed Bundle / APK → Android App Bundle* (kendi imza anahtarınızı oluşturun ve saklayın).

## Uygulamanın web sayfasından farkları
- Excel / Word / yedek dosyaları, Android'in "Kaydet" penceresiyle kaydedilir (`AndroidBridge.saveFile`).
- Yazdır / PDF düğmeleri Android yazdırma hizmetini açar; "PDF olarak kaydet" seçilebilir (`AndroidBridge.print`).
- Excel'den veri alma ve yedek yükleme, Android dosya seçicisini kullanır.
- Geri tuşu: açık pencere/menüyü kapatır, sonra ana ekrana döner, en son çıkış sorar.

## Önemli notlar
- Veriler cihaza özeldir. Uygulama silinirse veya "Verileri temizle" yapılırsa programlar ve şifreler silinir.
  **Yedekleme → Tam yedek indir** ile düzenli yedek alın.
- `allowBackup=false` ayarlıdır: veriler Google yedeklemesine gönderilmez.
- Uygulama kimliği (`com.bizimders.app`) ve sürüm numarası `app/build.gradle.kts` içinde değiştirilir.
- Web kodunu güncellemek için yeni HTML'yi `app/src/main/assets/index.html` üzerine yazın (aşağıdaki iki değişikliği korumanız gerekir:
  `xlsx.full.min.js` script etiketi ve `dl()` içindeki `AndroidBridge` bloğu).

## Bilgisayarsız / Android Studio'suz APK almak (GitHub Actions)
1. github.com'da ücretsiz bir hesapla yeni bir depo (repository) oluşturun ve bu klasörün **içindekileri** (`app`, `gradle`, `.github`, `build.gradle.kts` vb.) yükleyin.
2. Depoda **Actions** sekmesi → **APK Olustur** → **Run workflow**.
3. 3-5 dakika sonra iş bitince, çalışmanın sayfasında **Artifacts → BizimDers-APK** indirin; içinden `app-debug.apk` çıkar.
4. APK'yı telefona gönderip açın (ayarlardan "bilinmeyen kaynaklardan yüklemeye" izin vermeniz istenir).
Not: Bu "debug" APK'dır (kişisel kurulum için uygundur). Google Play'e yüklemek için imzalı sürüm gerekir.
