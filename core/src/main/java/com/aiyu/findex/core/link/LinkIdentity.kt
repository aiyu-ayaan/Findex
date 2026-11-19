package com.aiyu.findex.core.link

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * This phone's Findex Link identity: an EC P-256 key generated inside the Android Keystore (it never
 * leaves secure hardware) with its self-signed certificate. SHA-256 of the certificate is the device id.
 */
@Singleton
class LinkIdentity @Inject constructor() {

    private val store: KeyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    val certificate: X509Certificate by lazy { (store.getCertificate(ALIAS) ?: generate()) as X509Certificate }

    val fingerprint: ByteArray get() = LinkCrypto.sha256(certificate.encoded)

    val id: String get() = LinkCrypto.hex(fingerprint)

    private fun generate(): java.security.cert.Certificate {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            // TLS signs pre-hashed data through Conscrypt, which needs NONE.
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setCertificateSubject(X500Principal("CN=Findex"))
            .setCertificateSerialNumber(BigInteger(63, SecureRandom()))
            .setCertificateNotBefore(Date(now - DAY_MS))
            .setCertificateNotAfter(Date(now + 100L * 365 * DAY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply { initialize(spec) }.generateKeyPair()
        return store.getCertificate(ALIAS)
    }

    /** Server sockets that present this identity. */
    fun serverSocketFactory(): SSLServerSocketFactory =
        SSLContext.getInstance("TLS").apply { init(arrayOf(KeyManager()), null, SecureRandom()) }.serverSocketFactory

    private inner class KeyManager : X509ExtendedKeyManager() {
        private fun ec(keyType: String?) = if (keyType == null || keyType.contains("EC")) ALIAS else null
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = ec(keyType)?.let { arrayOf(it) }
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = ec(keyType)
        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = ec(keyType)
        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(certificate)
        override fun getPrivateKey(alias: String?): PrivateKey = store.getKey(ALIAS, null) as PrivateKey
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "findex_link_identity"
        private const val DAY_MS = 86_400_000L

        /**
         * Client sockets that accept exactly the certificate with fingerprint [pin], or any certificate while
         * pairing ([pin] null; the pairing code then proves who is on the other end). [seen] gets the
         * fingerprint the server showed. TLS still checks that the server holds the certificate's key.
         */
        fun clientSocketFactory(pin: ByteArray?, seen: (ByteArray) -> Unit = {}): SSLSocketFactory {
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                    throw CertificateException("client certificates are not used")

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
                    val fp = LinkCrypto.sha256(leaf.encoded)
                    if (pin != null && !java.security.MessageDigest.isEqual(pin, fp)) {
                        throw CertificateException("certificate does not match the paired computer")
                    }
                    seen(fp)
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }.socketFactory
        }
    }
}
