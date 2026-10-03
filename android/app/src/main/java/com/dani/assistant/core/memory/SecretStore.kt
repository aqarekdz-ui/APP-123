package com.dani.assistant.core.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted storage (AES-GCM, Android Keystore) for passwords, ID numbers, etc. */
object SecretStore {
    private const val PREFS = "dani_secrets"
    private const val KEY = "items"
    private const val ALIAS = "dani_secrets_key"

    private val triggers = listOf(
        "كلمة السر", "كلمة سر", "كلمة المرور", "password", "mot de passe", "mdp", "pin", "code secret",
        "رقم الهوية", "بطاقة", "جواز", "passport", "carte", "هوية", "رقم الحساب", "iban", "rib", "ccp"
    )

    fun looksSensitive(text: String): Boolean {
        val l = text.lowercase()
        return triggers.any { l.contains(it) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String? = try {
        val bytes = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(c.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    } catch (e: Exception) { null }

    private fun rawList(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try { JSONArray(raw).let { a -> List(a.length()) { a.getString(it) } } } catch (e: Exception) { emptyList() }
    }

    fun getAll(context: Context): List<String> = rawList(context).mapNotNull { decrypt(it) }

    private fun saveAll(context: Context, plain: List<String>) {
        val enc = plain.map { encrypt(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, JSONArray(enc).toString()).apply()
    }

    fun add(context: Context, item: String) {
        val clean = item.trim()
        if (clean.isEmpty()) return
        val items = getAll(context)
        if (items.contains(clean)) return
        saveAll(context, items + clean)
    }

    fun remove(context: Context, item: String) = saveAll(context, getAll(context).filter { it != item })
}
