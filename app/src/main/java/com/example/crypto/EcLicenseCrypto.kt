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
 * Pure JVM ECDSA P-256 (secp256r1 / prime256v1) license signing & verification engine
 * compatible 100% with SmartLedger's SLK1 offline license protocol.
 *
 * Signing input: `"SLK1." + Base64UrlNoPadding(compactJsonUtf8)`
 * Signature format: IEEE P1363 64-byte (`r[32] || s[32]`) encoded as Base64UrlNoPadding.
 * Wire token: `"SLK1.<payloadB64>.<sigB64>"`
 */
@OptIn(ExperimentalEncodingApi::class)
object EcLicenseCrypto {

    const val TOKEN_PREFIX = "SLK1"
    const val EC_CURVE = "secp256r1"
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    const val EXPECTED_PRODUCT = "SMARTLEDGER"
    const val EXPECTED_TYPE = "LOCAL"
    const val EXPECTED_KEY_ID = "LOCAL2"
    const val RESERVED_LEGACY_KEY_ID = "LOCAL1"
    const val EXPECTED_VERSION = 1
    val KEY_ID_REGEX = Regex("^LOCAL[A-Z0-9_-]{1,24}$")
    private const val P256_COORD_BYTES = 32
    private const val P256_P1363_SIG_BYTES = 64

    val SMARTLEDGER_DEFAULT_PUBLIC_KEY_PEM: String = """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE5508ADz4Kv/5UyjfV5VDO3hgfiRA
        IhvR+kxY7Yu5si9U6yk+62nEEwva3sN0KW6zDbVlZCLTzGpE0ZevF6llQQ==
        -----END PUBLIC KEY-----
    """.trimIndent() + "\n"

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
        val randomBytes = ByteArray(3)
        secureRandom.nextBytes(randomBytes)
        val hex = randomBytes.joinToString("") { "%02X".format(Locale.US, it) }
        val timePart = (nowMillis / 1000L).toString(36).uppercase(Locale.US).takeLast(5)
        return "LIC-$timePart$hex"
    }

    fun smartLedgerDefaultFingerprintSha256(): String {
        val pub = decodePublicKeyPem(SMARTLEDGER_DEFAULT_PUBLIC_KEY_PEM)
        return publicKeyFingerprintSha256(pub)
    }

    fun matchesSmartLedgerDefaultKey(publicKeyPem: String?): Boolean {
        if (publicKeyPem.isNullOrBlank()) return false
        return runCatching {
            val active = decodePublicKeyPem(publicKeyPem)
            val expected = decodePublicKeyPem(SMARTLEDGER_DEFAULT_PUBLIC_KEY_PEM)
            MessageDigest.isEqual(active.encoded, expected.encoded)
        }.getOrDefault(false)
    }

    fun normalizeKeyId(raw: String): String = raw.trim().uppercase(Locale.US)

    fun isValidKeyId(rawKid: String): Boolean {
        val kid = normalizeKeyId(rawKid)
        return kid != RESERVED_LEGACY_KEY_ID && KEY_ID_REGEX.matches(kid)
    }

    fun suggestNextKeyId(existingKids: Collection<String>): String {
        val normalized = existingKids.map { normalizeKeyId(it) }.toSet()
        if (normalized.isEmpty()) return EXPECTED_KEY_ID
        val maxNum = normalized.mapNotNull { kid ->
            kid.removePrefix("LOCAL").toIntOrNull()
        }.maxOrNull() ?: 1
        var candidateNum = maxOf(2, maxNum + 1)
        while ("LOCAL$candidateNum" in normalized) {
            candidateNum++
        }
        return "LOCAL$candidateNum"
    }

    fun formatKeyringPem(entries: List<Pair<String, String>>): String {
        return entries.joinToString("\n\n") { (kid, pem) ->
            "# kid: ${normalizeKeyId(kid)}\n${pem.trim()}"
        } + if (entries.isNotEmpty()) "\n" else ""
    }

    fun buildPayload(
        deviceCode: String,
        plan: LicensePlan,
        kid: String = EXPECTED_KEY_ID,
        issuedAtMillis: Long = System.currentTimeMillis(),
        trialDays: Int? = null,
        customTrialEndsAtMillis: Long? = null,
        licenseId: String = generateLicenseId(issuedAtMillis),
        nowMillis: Long = System.currentTimeMillis()
    ): LicensePayload {
        val normalizedKid = normalizeKeyId(kid)
        require(isValidKeyId(normalizedKid)) {
            "معرف المفتاح ($normalizedKid) غير صالح. يجب أن يطابق ^LOCAL[A-Z0-9_-]{1,24}$ ويُمنع LOCAL1."
        }
        require(issuedAtMillis > 0L) { "وقت الإصدار (iat) يجب أن يكون أكبر من صفر بالميلي ثانية" }
        require(licenseId.isNotBlank()) { "معرف الترخيص (licenseId) مطلوب" }

        val validation = DeviceCodeValidator.validate(deviceCode)
        require(validation.isFullyValid) {
            "رمز الجهاز غير صالح أو يحتوي على خطأ إملائي في حرف التحقق"
        }

        val resolvedTrialEndsAt: Long? = when (plan) {
            LicensePlan.LIFETIME -> null
            LicensePlan.TRIAL -> {
                val endMillis = when {
                    customTrialEndsAtMillis != null -> customTrialEndsAtMillis
                    trialDays != null -> {
                        require(trialDays > 0) { "عدد أيام الفترة التجريبية يجب أن يكون أكبر من صفر" }
                        issuedAtMillis + trialDays.toLong() * 86_400_000L
                    }
                    else -> error("خطة TRIAL تتطلب تحديد عدد الأيام أو تاريخ انتهاء مخصص")
                }
                require(endMillis > issuedAtMillis) {
                    "تاريخ انتهاء الفترة التجريبية (trialEndsAt) يجب أن يكون بعد وقت الإصدار (iat)"
                }
                require(endMillis > nowMillis) {
                    "تاريخ انتهاء الفترة التجريبية (trialEndsAt) يجب أن يكون في المستقبل"
                }
                endMillis
            }
        }

        return LicensePayload(
            v = EXPECTED_VERSION,
            kid = normalizedKid,
            product = EXPECTED_PRODUCT,
            type = EXPECTED_TYPE,
            licenseId = licenseId.trim(),
            deviceCode = validation.normalizedCode,
            plan = plan.wireName,
            iat = issuedAtMillis,
            trialEndsAt = resolvedTrialEndsAt,
            maxDevices = 1,
            active = true
        )
    }

    fun issueAndSign(
        payload: LicensePayload,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): SignedLicenseResult {
        val payloadJson = payload.toCanonicalJson()
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        val payloadB64 = b64UrlNoPadding.encode(payloadBytes)
        val signingInput = "$TOKEN_PREFIX.$payloadB64"
        val derSignature = signBytes(signingInput.toByteArray(StandardCharsets.UTF_8), privateKey)
        val p1363Signature = derToP1363(derSignature, P256_COORD_BYTES)
        val sigB64 = b64UrlNoPadding.encode(p1363Signature)
        val token = "$signingInput.$sigB64"

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
        if (parts[1].isBlank() || parts[2].isBlank()) return null

        return try {
            val payloadBytes = b64UrlNoPadding.decode(parts[1])
            val rawSigBytes = b64UrlNoPadding.decode(parts[2])
            val derSigBytes = if (rawSigBytes.size == P256_P1363_SIG_BYTES) {
                p1363ToDer(rawSigBytes, P256_COORD_BYTES)
            } else {
                rawSigBytes
            }

            val signingInputBytes = "$TOKEN_PREFIX.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(signingInputBytes)
            if (!verifier.verify(derSigBytes)) return null

            val payloadJson = String(payloadBytes, StandardCharsets.UTF_8)
            val payload = jsonParser.decodeFromString<LicensePayload>(payloadJson)
            if (!isPayloadContractValid(payload)) {
                return null
            }
            VerifiedLicense(payload = payload, payloadJson = payloadJson)
        } catch (_: Exception) {
            null
        }
    }

    fun extractPayloadUnsafe(token: String): LicensePayload? {
        val parts = token.trim().split('.')
        if (parts.size != 3 || parts[0] != TOKEN_PREFIX || parts[1].isBlank()) return null
        return runCatching {
            val payloadBytes = b64UrlNoPadding.decode(parts[1])
            val payloadJson = String(payloadBytes, StandardCharsets.UTF_8)
            jsonParser.decodeFromString<LicensePayload>(payloadJson)
        }.getOrNull()
    }

    fun isPayloadContractValid(payload: LicensePayload): Boolean {
        if (payload.v < EXPECTED_VERSION) return false
        if (!isValidKeyId(payload.kid)) return false
        if (payload.product != EXPECTED_PRODUCT) return false
        if (payload.type != EXPECTED_TYPE) return false
        if (payload.licenseId.isBlank()) return false
        if (!DeviceCodeValidator.validate(payload.deviceCode).isFullyValid) return false
        if (payload.iat <= 0L) return false
        if (!payload.active || payload.revoked == true) return false
        return when (payload.plan) {
            LicensePlan.LIFETIME.wireName -> payload.trialEndsAt == null
            LicensePlan.TRIAL.wireName -> payload.trialEndsAt != null && payload.trialEndsAt > payload.iat
            else -> false
        }
    }

    /**
     * Converts an ASN.1 DER-encoded ECDSA signature to fixed-width IEEE P1363 (`r || s`, 64 bytes for P-256).
     */
    fun derToP1363(der: ByteArray, coordinateLength: Int = P256_COORD_BYTES): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte()) { "Invalid ASN.1 DER ECDSA signature" }
        var offset = 1
        val seqLenByte = der[offset++].toInt() and 0xFF
        if ((seqLenByte and 0x80) != 0) {
            val lenBytes = seqLenByte and 0x7F
            require(lenBytes in 1..2 && offset + lenBytes <= der.size) { "Invalid DER length" }
            offset += lenBytes
        }

        require(offset < der.size && der[offset++] == 0x02.toByte()) { "Expected INTEGER tag for r" }
        val rLen = der[offset++].toInt() and 0xFF
        require(offset + rLen <= der.size) { "Invalid r length" }
        val rBytes = der.copyOfRange(offset, offset + rLen)
        offset += rLen

        require(offset < der.size && der[offset++] == 0x02.toByte()) { "Expected INTEGER tag for s" }
        val sLen = der[offset++].toInt() and 0xFF
        require(offset + sLen <= der.size) { "Invalid s length" }
        val sBytes = der.copyOfRange(offset, offset + sLen)

        val out = ByteArray(coordinateLength * 2)
        copyUnsignedCoordinate(rBytes, out, 0, coordinateLength)
        copyUnsignedCoordinate(sBytes, out, coordinateLength, coordinateLength)
        return out
    }

    /**
     * Converts a 64-byte IEEE P1363 (`r || s`) ECDSA signature to ASN.1 DER for JCA verification.
     */
    fun p1363ToDer(p1363: ByteArray, coordinateLength: Int = P256_COORD_BYTES): ByteArray {
        require(p1363.size == coordinateLength * 2) {
            "Expected ${coordinateLength * 2}-byte IEEE P1363 signature, got ${p1363.size}"
        }
        val rInt = toAsn1PositiveInteger(p1363.copyOfRange(0, coordinateLength))
        val sInt = toAsn1PositiveInteger(p1363.copyOfRange(coordinateLength, coordinateLength * 2))
        val totalLen = 2 + rInt.size + 2 + sInt.size
        val der = ByteArray(2 + totalLen)
        var pos = 0
        der[pos++] = 0x30
        der[pos++] = totalLen.toByte()
        der[pos++] = 0x02
        der[pos++] = rInt.size.toByte()
        System.arraycopy(rInt, 0, der, pos, rInt.size)
        pos += rInt.size
        der[pos++] = 0x02
        der[pos++] = sInt.size.toByte()
        System.arraycopy(sInt, 0, der, pos, sInt.size)
        return der
    }

    private fun copyUnsignedCoordinate(src: ByteArray, dest: ByteArray, destOffset: Int, targetLen: Int) {
        var firstNonZero = 0
        while (firstNonZero < src.size - 1 && src[firstNonZero] == 0.toByte()) {
            firstNonZero++
        }
        val significantLen = src.size - firstNonZero
        require(significantLen <= targetLen) {
            "ECDSA coordinate exceeds $targetLen bytes ($significantLen)"
        }
        System.arraycopy(
            src,
            firstNonZero,
            dest,
            destOffset + (targetLen - significantLen),
            significantLen
        )
    }

    private fun toAsn1PositiveInteger(raw: ByteArray): ByteArray {
        var firstNonZero = 0
        while (firstNonZero < raw.size - 1 && raw[firstNonZero] == 0.toByte()) {
            firstNonZero++
        }
        val trimmedLen = raw.size - firstNonZero
        val needsLeadingZero = (raw[firstNonZero].toInt() and 0x80) != 0
        return if (needsLeadingZero) {
            ByteArray(trimmedLen + 1).also { out ->
                out[0] = 0
                System.arraycopy(raw, firstNonZero, out, 1, trimmedLen)
            }
        } else {
            raw.copyOfRange(firstNonZero, raw.size)
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
