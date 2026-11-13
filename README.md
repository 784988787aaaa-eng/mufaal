# مُفعّل الدفتر الذكي — SmartLedger License Studio

تطبيق Android مستقل بالكامل (يعمل **100% أوفلاين** بدون أي صلاحية إنترنت في `AndroidManifest.xml`) مخصص للمطور لإصدار وتوقيع وإدارة التراخيص المحلية (`SLK1` — ECDSA P-256) لتطبيق **الدفتر الذكي**.

---

## البنية المعمارية للمشروع

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # نقطة الدخول (FragmentActivity + BiometricPrompt + EdgeToEdge)
├── crypto/
│   ├── DeviceCodeValidator.kt       # استخراج SLD-XXXX-XXXX-XXXC من رسائل واتساب وفحص Check Digit
│   ├── LicenseModels.kt             # نموذج Payload القياسي v2 وعقد التوقيع
│   ├── EcLicenseCrypto.kt           # توليد مفاتيح ECDSA P-256 (secp256r1) وتوقيع/تحقق SLK1
│   ├── BackupCrypto.kt              # تشفير AES-256-GCM + PBKDF2WithHmacSHA256 للنسخ الاحتياطية
│   └── QrMatrixGenerator.kt         # توليد مصفوفة QR أوفلاين عبر ZXing
├── security/
│   ├── KeyVault.kt                  # خزنة المفاتيح المحمية بـ EncryptedSharedPreferences + Android Keystore
│   └── BiometricAuthHelper.kt       # مصادقة بصمة الإصبع (BiometricPrompt)
├── data/
│   ├── IssuedLicenseEntity.kt       # كيان سجل التراخيص في Room
│   ├── IssuedLicenseDao.kt          # استعلامات البحث وكشف التكرار
│   ├── AppDatabase.kt               # قاعدة بيانات Room المحلية
│   └── LicenseRepository.kt         # طبقة البيانات والنسخ الاحتياطي المشفر (.slbak)
├── gateway/
│   └── LicenseBackendGateway.kt     # واجهة مجردة معطلة افتراضياً للتوسع السحابي المستقبلي (Cloudflare Worker)
├── share/
│   └── LicenseShareHelper.kt        # مشاركة واتساب، تصدير .slic و .pem، وصورة QR عبر FileProvider
└── ui/
    ├── SmartLedgerApp.kt            # التوجيه الآمن (Navigation Compose) ودعم RTL والتكيف مع الشاشات
    ├── theme/                       # نظام ألوان Vault الداكن/الفاتح والخطوط المحلية
    ├── viewmodel/                   # LicenseStudioViewModel لإدارة الحالة والتوقيع
    ├── components/                  # مكونات عرض الأكواد، نوافذ كلمات المرور، و QR
    └── screens/                     # شاشات القفل، الإصدار، النتيجة، السجل، وخزنة المفاتيح
```

---

## خطوات ربط المفتاح العام بتطبيق «الدفتر الذكي»

1. افتح تطبيق **مُفعّل الدفتر الذكي** وأنشئ رمز `PIN` (ويمكن تفعيل البصمة).
2. انتقل إلى تبويب **الخزنة (KeyVault)** واضغط **توليد زوج مفاتيح ECDSA P-256 جديد** (أو استورد مفتاحك الخاص الحالي لمرة واحدة).
3. تأكد من ظهور **بصمة SHA-256 Fingerprint** للمفتاح العام.
4. اضغط **مشاركة PEM** أو **حفظ الملف** لتصدير ملف:
   ```text
   license_local_ec_public.pem
   ```
5. ضع هذا الملف داخل مشروع تطبيق **الدفتر الذكي** في المسار:
   ```text
   app/src/main/assets/license_local_ec_public.pem
   ```
6. داخل تطبيق **الدفتر الذكي**، يتم التحقق أوفلاين من أي كود `SLK1.<payloadB64u>.<signatureB64u>` عبر:
   - فك ترميز الجزء الثاني `Base64UrlNoPadding` للحصول على نص JSON الخام.
   - فك ترميز الجزء الثالث `Base64UrlNoPadding` للحصول على توقيع DER الخاص بـ `SHA256withECDSA`.
   - قراءة المفتاح العام X.509 من `assets/license_local_ec_public.pem`.
   - التحقق باستخدام `Signature.getInstance("SHA256withECDSA")`:
     ```kotlin
     val verifier = Signature.getInstance("SHA256withECDSA")
     verifier.initVerify(publicKey)
     verifier.update(payloadBytes)
     val isValid = verifier.verify(signatureBytes)
     ```
   - مطابقة `product == "SMARTLEDGER"` و `v == 2` و `deviceCode` الخاص بجهاز العميل، وفحص `trialEndsAt` إذا كانت الخطة `TRIAL`.

---

## صيغة الحمولة القياسية الموقّعة (`SLK1`)

```json
{
  "v": 2,
  "kid": "LOCAL2",
  "alg": "ES256",
  "product": "SMARTLEDGER",
  "type": "LOCAL",
  "plan": "LIFETIME",
  "licenseId": "SLIC-...",
  "deviceCode": "SLD-XXXX-XXXX-XXXC",
  "iat": 1700000000000,
  "trialEndsAt": null
}
```

- **الخوارزمية:** `ECDSA P-256` (`secp256r1`) مع `SHA256withECDSA`.
- **حرف التحقق (`C`):** محسوب عبر خوارزمية **Luhn mod 36** على الـ 11 خانة الأولى من كود الجهاز (`0-9A-Z`).
- **النسخ الاحتياطي المشفر:** يستخدم `PBKDF2WithHmacSHA256` (120,000 دورة) + `AES-256-GCM` لكلٍ من أرشيف السجل (`.slbak`) ونسخة المفتاح الاحتياطية (`.slkey`).

---

## تشغيل الاختبارات والبناء

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
