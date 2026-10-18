package com.aiyu.findex.core.vault

import android.os.Build
import android.os.Environment
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.aiyu.findex.core.db.VaultDao
import com.aiyu.findex.core.db.VaultMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

class WrongPasswordException : Exception()
class LockedOutException(val until: Long) : Exception()
/** The Keystore key died (new fingerprint enrolled); the biometric wrap has been dropped. */
class BiometricInvalidatedException : Exception()
/** Vault data is on disk but `vault.key` is missing or unreadable; never offer setup over it. */
class VaultUnreadableException(cause: Throwable? = null) : Exception("vault.key missing or unreadable", cause)

/** Wraps and unwraps the vault DEK with the password (PBKDF2 KEK) and an optional Keystore biometric key. */
@Singleton
class VaultKeyManager @Inject constructor(private val dao: VaultDao) {

    companion object {
        const val DIR_NAME = ".FindexVault"
        const val KEY_FILE = "vault.key"
        const val BLOB_EXT = ".fdxv"
        private const val BIO_ALIAS = "findex_vault_bio"
        private const val FREE_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 30_000L

        fun vaultDir(): File = File(Environment.getExternalStorageDirectory(), DIR_NAME)

        /** Lockout after [failed] wrong passwords: none for the first 5, then 30 s doubling. */
        fun lockoutMs(failed: Int): Long =
            if (failed < FREE_ATTEMPTS) 0 else BASE_LOCKOUT_MS shl (failed - FREE_ATTEMPTS).coerceAtMost(16)

        internal fun hasBlobs(dir: File): Boolean = dir.listFiles { f -> f.name.endsWith(BLOB_EXT) }?.isNotEmpty() == true

        /** The key file in [dir]; null only when [dir] holds no vault at all. */
        internal fun readKeyFileIn(dir: File): VaultFormat.KeyFile? {
            val file = File(dir, KEY_FILE)
            if (!file.exists()) {
                if (hasBlobs(dir)) throw VaultUnreadableException()
                return null
            }
            return try {
                file.inputStream().use { VaultFormat.readKeyFile(it) }
            } catch (e: Exception) {
                throw VaultUnreadableException(e)
            }
        }
    }

    val biometricSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * The Room meta row; after a reinstall it is rebuilt from `vault.key` (biometric off).
     * Null means no vault exists. Throws [VaultUnreadableException] when vault data exists without a usable key.
     */
    suspend fun meta(): VaultMetaEntity? = dao.meta() ?: withContext(Dispatchers.IO) {
        val key = readKeyFileIn(vaultDir())
        if (key == null && dao.itemCount() > 0) throw VaultUnreadableException()
        key?.let {
            VaultMetaEntity(
                formatVersion = 1,
                kdfSalt = VaultFormat.b64(key.salt),
                kdfIterations = key.iterations,
                passwordWrappedKey = VaultFormat.b64(key.wrapped),
                biometricWrappedKey = null
            ).also { dao.upsertMeta(it) }
        }
    }

    /** Refuses with [VaultUnreadableException] when any vault data exists: a new DEK would orphan every blob. */
    suspend fun create(password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val dir = vaultDir()
        if (dao.meta() != null || File(dir, KEY_FILE).exists() || hasBlobs(dir) || dao.itemCount() > 0) throw VaultUnreadableException()
        val dek = VaultFormat.randomBytes(32)
        val iterations = VaultFormat.calibrateIterations()
        val meta = passwordWrap(password, dek, iterations).copy(createdAt = System.currentTimeMillis())
        save(meta)
        deleteBiometricKey()
        dek
    }

    suspend fun unlockWithPassword(password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val meta = meta() ?: throw IllegalStateException("no vault")
        val now = System.currentTimeMillis()
        if (meta.lockoutUntil > now) throw LockedOutException(meta.lockoutUntil)
        val kek = VaultFormat.pbkdf2(password, VaultFormat.unb64(meta.kdfSalt), meta.kdfIterations)
        try {
            val dek = VaultFormat.open(kek, VaultFormat.unb64(meta.passwordWrappedKey))
            if (meta.failedAttempts != 0) dao.upsertMeta(meta.copy(failedAttempts = 0, lockoutUntil = 0))
            dek
        } catch (e: VaultCorruptException) {
            val failed = meta.failedAttempts + 1
            val lockout = lockoutMs(failed)
            dao.upsertMeta(meta.copy(failedAttempts = failed, lockoutUntil = if (lockout > 0) now + lockout else 0))
            throw if (lockout > 0) LockedOutException(now + lockout) else WrongPasswordException()
        } finally {
            kek.fill(0)
        }
    }

    /** Rewraps the DEK only; blobs are untouched. */
    suspend fun changePassword(old: CharArray, new: CharArray) {
        val dek = unlockWithPassword(old)
        try {
            val meta = meta()!!
            save(passwordWrap(new, dek, meta.kdfIterations).copy(biometricWrappedKey = meta.biometricWrappedKey, createdAt = meta.createdAt))
        } finally {
            dek.fill(0)
        }
    }

    private fun passwordWrap(password: CharArray, dek: ByteArray, iterations: Int): VaultMetaEntity {
        val salt = VaultFormat.randomBytes(16)
        val kek = VaultFormat.pbkdf2(password, salt, iterations)
        try {
            return VaultMetaEntity(
                formatVersion = 1,
                kdfSalt = VaultFormat.b64(salt),
                kdfIterations = iterations,
                passwordWrappedKey = VaultFormat.b64(VaultFormat.seal(kek, dek)),
                biometricWrappedKey = null
            )
        } finally {
            kek.fill(0)
        }
    }

    /** File first: if it fails, Room and `vault.key` both keep the old wrap. */
    private suspend fun save(meta: VaultMetaEntity) {
        withContext(Dispatchers.IO) {
            val dir = vaultDir().apply { mkdirs() }
            File(dir, ".nomedia").createNewFile()
            val tmp = File(dir, "$KEY_FILE.part")
            tmp.outputStream().use { out ->
                VaultFormat.writeKeyFile(out, VaultFormat.KeyFile(meta.kdfIterations, VaultFormat.unb64(meta.kdfSalt), VaultFormat.unb64(meta.passwordWrappedKey)))
                out.fd.sync()
            }
            if (!tmp.renameTo(File(dir, KEY_FILE))) throw java.io.IOException("cannot write $KEY_FILE")
        }
        dao.upsertMeta(meta)
    }

    // region Biometric

    /** Cipher to authenticate with BiometricPrompt before [enableBiometric]. */
    fun biometricEnrollCipher(): Cipher {
        deleteBiometricKey()
        val spec = KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                else @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(-1)
            }
            .build()
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    suspend fun enableBiometric(authenticated: Cipher, dek: ByteArray) {
        val wrapped = authenticated.iv + authenticated.doFinal(dek)
        dao.upsertMeta(meta()!!.copy(biometricWrappedKey = VaultFormat.b64(wrapped)))
    }

    suspend fun disableBiometric() {
        meta()?.let { dao.upsertMeta(it.copy(biometricWrappedKey = null)) }
        deleteBiometricKey()
    }

    /** Decrypt cipher for BiometricPrompt, or null when biometric unlock is off. */
    suspend fun biometricUnlockCipher(): Cipher? {
        val wrapped = meta()?.biometricWrappedKey?.let(VaultFormat::unb64) ?: return null
        val key = keyStore().getKey(BIO_ALIAS, null) as? SecretKey
        if (key == null) { disableBiometric(); throw BiometricInvalidatedException() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped, 0, 12)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disableBiometric()
            throw BiometricInvalidatedException()
        }
    }

    suspend fun unlockWithBiometric(authenticated: Cipher): ByteArray {
        val wrapped = VaultFormat.unb64(meta()!!.biometricWrappedKey!!)
        return authenticated.doFinal(wrapped, 12, wrapped.size - 12)
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun deleteBiometricKey() {
        runCatching { keyStore().deleteEntry(BIO_ALIAS) }
    }

    // endregion
}
