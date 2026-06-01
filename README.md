# 📦 CourierMod (Kurye Modu)

<p align="center">
  <img src="logo.png" width="300" alt="CourierMod Logo" />
</p>

Fabric tabanlı, Minecraft 1.20.1 için özel olarak geliştirilmiş **RP Sunucusu Kurye Sistemi** modudur. Oyuncuların dağıtım noktalarından kurye paketi teslim alıp, müşteri konumlarına taşıyarak sunucu para birimini kazanmalarını sağlar.

---

## 🚀 Öne Çıkan Özellikler

* **Mesafe Sınırlaması (Yeni - v1.1.0):** Görev üretici, dağıtım noktalarını maksimum **500 metre** uzaklıktaki müşteri konumlarıyla eşleştirir. Yakında uygun iş yoksa oyuncu bilgilendirilir.
* **Aktivite Günlüğü ve Log Sistemi (Yeni - v1.1.0):** Sunucu başlangıcından itibaren tüm kurye hareketleri (görev alma, tamamlama, iptal etme, admin işlemleri) kayıt altına alınır. `/kurye log` komutu ile son 15 log oyunda gösterilir ve tüm geçmiş `config/kurye_activity.log` dosyasına yazılır.
* **Görsel Parçacık Efektleri:** Dağıtım veya teslimat noktasına yaklaşıldığında (100 blok) gökyüzüne doğru uzanan `END_ROD` efekt kolonu ve yerde `HAPPY_VILLAGER` yeşil halkası oluşur.
* **JourneyMap Entegrasyonu:** Sohbet satırında tıklanabilir linkler aracılığıyla JourneyMap waypoint'leri otomatik olarak oluşturulabilir.
* **Scoreboard Para Sistemi:** Sunucu scoreboard'undaki `para` değişkenini doğrudan okur ve ödülleri bu değişkene ekler.
* **TAB Modu Entegrasyonu:** TAB eklentisi için placeholder'lar tanımlanmıştır (`%kurye_aktif%`, `%kurye_hedef%`, `%kurye_durum%`, `%kurye_para%`).

---

## 🎮 Komutlar

### 🧑 Oyuncu Komutları
* `/kurye` - Kurye yardım menüsünü görüntüler.
* `/kurye al` - Yakındaki (maks. 500m) uygun noktalardan bir kurye görevi alır.
* `/kurye iptal` - Mevcut görevi iptal eder ve kurye paketini envanterden siler.
* `/kurye wp dagitim` - Dağıtım noktasının JourneyMap waypoint'ini sohbet ekranına yansıtır.
* `/kurye wp musteri` - Müşteri noktasının JourneyMap waypoint'ini sohbet ekranına yansıtır.

### 🛡️ Yetkili (OP - Seviye 2) Komutları
* `/kurye log` - Son 15 kurye hareketini oyunda gösterir ve tüm hareketleri `config/kurye_activity.log` dosyasına kaydeder.
* `/kurye admin` - Admin yardım menüsünü görüntüler.
* `/kurye admin dagitim-ekle <isim>` - Ayakta durduğunuz konumu belirlediğiniz isimle bir Dağıtım Noktası olarak kaydeder.
* `/kurye admin musteri-ekle <isim>` - Ayakta durduğunuz konumu belirlediğiniz isimle bir Müşteri Noktası olarak kaydeder.
* `/kurye admin dagitim-sil <isim>` - Belirtilen dağıtım noktasını siler.
* `/kurye admin musteri-sil <isim>` - Belirtilen müşteri noktasını siler.
* `/kurye admin listele` - Kayıtlı tüm noktaları ve koordinatlarını listeler.

---

## 📊 TAB Modu Placeholder'ları

Mod, **TAB** eklentisiyle entegre çalışacak şu dinamik placeholder'ları sunar:
* `%kurye_aktif%` - Aktif bir görevi varsa `1`, yoksa `0` döner.
* `%kurye_hedef%` - Görevin şu anki hedef durak adını döner (örneğin: `Metu AVM`).
* `%kurye_durum%` - Görev durumunu döner (`Paketi Al` veya `Teslim Et`).
* `%kurye_para%` - Kuryenin anlık para miktarını döner.

---

## 🛠️ Derleme ve Kurulum

### Derleme Adımları
Modu sıfırdan derlemek için CourierModSrc klasörü içinde terminalde şu komutu çalıştırın:
```bash
gradlew clean build
```
Derleme tamamlandıktan sonra hazır mod dosyası şu dizinde oluşacaktır:
`build/libs/couriermod-1.1.0.jar`

### Sunucuya Kurulum
1. Derlenen `.jar` dosyasını sunucunuzun `mods` klasörünün içerisine yerleştirin.
2. Sunucuda kurulu olan eski kurye modu sürümünü silin.
3. Sunucuyu yeniden başlatın. Dağıtım ve müşteri verileri otomatik olarak `config/kurye_data.json` dosyasında saklanacaktır.
