/*
 * Blipwave ESP32 UART örneği
 *
 * Nordic UART Service (NUS) açar:
 *   - "PING" gelirse "PONG" döner (büyük/küçük harf ve satır sonu önemsiz)
 *   - diğer her mesajı olduğu gibi geri gönderir (echo)
 *
 * Kart: herhangi bir ESP32 (ESP32, S3, C3). Arduino-ESP32 çekirdeğindeki
 * yerleşik BLE kütüphanesiyle derlenir; ek kütüphane gerekmez.
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define DEVICE_NAME "Blipwave-ESP32"

#define UART_SERVICE_UUID "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define UART_RX_UUID      "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // telefon -> ESP32 (write)
#define UART_TX_UUID      "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  // ESP32 -> telefon (notify)

static BLECharacteristic* txCharacteristic = nullptr;
static bool deviceConnected = false;
static bool restartAdvertising = false;

// Bildirim tek pakette MTU - 3 bayt taşıyabilir; uzun cevapları parçala
static void notifyChunked(const uint8_t* data, size_t length) {
  const size_t chunkSize = 20;  // varsayılan MTU (23) için güvenli boyut
  for (size_t offset = 0; offset < length; offset += chunkSize) {
    size_t size = min(chunkSize, length - offset);
    txCharacteristic->setValue(const_cast<uint8_t*>(data + offset), size);
    txCharacteristic->notify();
    delay(5);  // BLE yığınının kuyruğu boşaltmasına izin ver
  }
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override {
    deviceConnected = true;
    Serial.println("Bağlantı kuruldu");
  }

  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    restartAdvertising = true;  // yeniden keşfedilebilir ol
    Serial.println("Bağlantı koptu");
  }
};

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    String value = characteristic->getValue();
    if (value.length() == 0) return;

    Serial.print("RX: ");
    Serial.println(value);

    String command = value;
    command.trim();
    if (command.equalsIgnoreCase("PING")) {
      const char* reply = "PONG";
      notifyChunked(reinterpret_cast<const uint8_t*>(reply), strlen(reply));
    } else {
      notifyChunked(reinterpret_cast<const uint8_t*>(value.c_str()), value.length());
    }
  }
};

void setup() {
  Serial.begin(115200);
  Serial.println("Blipwave ESP32 UART başlıyor");

  BLEDevice::init(DEVICE_NAME);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* service = server->createService(UART_SERVICE_UUID);

  txCharacteristic = service->createCharacteristic(UART_TX_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  txCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic* rxCharacteristic = service->createCharacteristic(
      UART_RX_UUID,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rxCharacteristic->setCallbacks(new RxCallbacks());

  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(UART_SERVICE_UUID);
  advertising->setScanResponse(true);  // isim scan response içinde gider
  BLEDevice::startAdvertising();

  Serial.println("Yayın başladı: " DEVICE_NAME);
}

void loop() {
  if (restartAdvertising) {
    delay(500);  // yığının bağlantıyı temizlemesini bekle
    BLEDevice::startAdvertising();
    restartAdvertising = false;
    Serial.println("Yayın yeniden başladı");
  }
  delay(20);
}
