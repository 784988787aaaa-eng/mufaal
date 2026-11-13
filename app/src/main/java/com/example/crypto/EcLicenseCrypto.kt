package com.example.crypto

import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Pure JVM ECDSA P-256 (secp256r1 / prime256v1) license signing & verification engine.
 *
 * Token wire format:
 * `SLK1.<Base64UrlNoPadding(payloadUtf8)>.<Base64UrlNoPadding(sha256WithEcdsaSignature)>`
 */
@OptIn(ExperimentalEncodingApi::class)
object EcLicenseCrypto {

    const val TOKEN_PREFIX = "SLK1"
    const val EC_CURVE = "secp256r1"
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    const val EXPECTED_PRODUCT = "SMARTLEDGER"
    const val EXPECTED_KEY_ID = "LOCAL2"
    const val EXPECTED_VERSION = 2

    private val jsonParser = Json {
        ignoreUnknownKeys = true
    }

    private val b64UrlNoPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val secureRandom = SecureRandom()

    fun generateEcP256KeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        return generator.generateKeyPair()
    }

    fun generateLicenseId(nowMillis: Long = System.currentTimeMillis()): String {
        val randomBytes = ByteArray(4)
        secureRandom.nextBytes(randomBytes)
        val hex = randomBytes.joinToString("") { "%02X".format(Locale.US, it) }
        val timePart = (nowMillis / 1000L).toString(36).uppercase(Locale.US)
        return "SLIC-$timePart-$hex"
    }

    fun buildPayload(
        deviceCode: String,
        plan: LicensePlan,
        issuedAtMillis: Long = System.currentTimeMillis(),
        trialDays: Int? = null,
        customTrialEndsAtMillis: Long? = null,
        customerHint: String? = null,
        licenseId: String = generateLicenseId(issuedAtMillis)
    ): LicensePayload {
        val normalizedDevice = DeviceCodeValidator.normalizeInput(deviceCode)
        val resolvedTrialEndsAt: Long? = when (plan) {
            LicensePlan.LIFETIME -> null
            LicensePlan.TRIAL -> {
                when {
                    customTrialEndsAtMillis != null -> {
                        require(customTrialEndsAtMillis > issuedAtMillis) {
                            "تاريخ انتهاء الفترة التجريبية يجب أن يكون بعد وقت الإصدار"
                        }
                        customTrialEndsAtMillis
                    }
                    trialDays != null -> {
                        require(trialDays > 0) { "عدد أيام الفترة التجريبية يجب أن يكون أكبر من صفر" }
                        issuedAtMillis + trialDays.toLong() * 24L * 60L * 60L * 1000L
                    }
                    else -> error("خطة TRIAL تتطلب تحديد عدد الأيام أو تاريخ انتهاء مخصص")
                }
            }
        }
        return LicensePayload(
            v = EXPECTED_VERSION,
            kid = EXPECTED_KEY_ID,
            alg = "ES256",
            product = EXPECTED_PRODUCT,
            type = "LOCAL",
            plan = plan.wireName,
            licenseId = licenseId,
            deviceCode = normalizedDevice,
            iat = issuedAtMillis,
            trialEndsAt = resolvedTrialEndsAt,
            customerHint = customerHint?.takeIf { it.isNotBlank() }
        )
    }

    fun issueAndSign(
        payload: LicensePayload,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): SignedLicenseResult {
        val payloadJson = payload.toCanonicalJson()
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        val signatureBytes = signBytes(payloadBytes, privateKey)
        val token = buildString {
            append(TOKEN_PREFIX)
            append('.')
            append(b64UrlNoPadding.encode(payloadBytes))
            append('.')
            append(b64UrlNoPadding.encode(signatureBytes))
        }
        val selfVerified = verifyToken(token, publicKey) != null
        require(selfVerified) { "فشل التحقق الذاتي للترخيص الموقّع بالمفتاح العام!" }
        return SignedLicenseResult(
            token = token,
            payload = payload,
            payloadJson = payloadJson,
            selfVerified = true,
            keyFingerprintSha256 = publicKeyFingerprintSha256(publicKey)
        )
    }

    fun signBytes(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey, secureRandom)
        signature.update(data)
        return signature.sign()
    }

    fun verifyToken(token: String, publicKey: PublicKey): VerifiedLicense? {
        val trimmed = token.trim()
        val parts = trimmed.split('.')
        if (parts.size != 3 || parts[0] != TOKEN_PREFIX) return null
        return try {
            val payloadBytes = b64UrlNoPadding.decode(parts[1])
            val sigBytes = b64UrlNoPadding.decode(parts[2])
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(payloadBytes)
            if (!verifier.verify(sigBytes)) return null

            val payloadJson = String(payloadBytes, StandardCharsets.UTF_8)
            val payload = jsonParser.decodeFromString<LicensePayload>(payloadJson)
            if (payload.product != EXPECTED_PRODUCT || payload.v != EXPECTED_VERSION) {
                return null
            }
            VerifiedLicense(payload = payload, payloadJson = payloadJson)
        } catch (_: Exception) {
            null
        }
    }

    fun encodePublicKeyPem(publicKey: PublicKey): String {
        val b64 = Base64.Default.encode(publicKey.encoded).chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$b64\n-----END PUBLIC KEY-----\n"
    }

    fun decodePublicKeyPem(pem: String): ECPublicKey {
        val clean = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.Default.decode(clean)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(der)) as ECPublicKey
    }

    fun encodePrivateKeyPkcs8Base64(privateKey: PrivateKey): String {
        return Base64.Default.encode(privateKey.encoded)
    }

    fun decodePrivateKey(input: String): ECPrivateKey {
        val clean = input
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.Default.decode(clean)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePrivate(PKCS8EncodedKeySpec(der)) as ECPrivateKey
    }

    fun publicKeyFingerprintSha256(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
        return digest.joinToString(":") { "%02X".format(Locale.US, it) }
    }

    /**
     * Derives the corresponding X.509 EC PublicKey from an ECPrivateKey when importing a private key alone
     * if a matching public key isn't supplied separately, or validates a pair matches by sign+verify challenge.
     */
    fun verifyKeyPairMatch(privateKey: PrivateKey, publicKey: PublicKey): Boolean {
        return try {
            val challenge = ByteArray(32).also { secureRandom.nextBytes(it) }
            val sig = signBytes(challenge, privateKey)
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(challenge)
            verifier.verify(sig)
        } catch (_: Exception) {
            false
        }
    }
}
