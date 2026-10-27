package com.innovify.skillswap.viewmodel

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// US03 · Acceso con huella o rostro.
// El backend no tiene refresh token y el JWT vence, así que lo que se guarda es el usuario y la contraseña,
// cifrados con una llave AES del Android Keystore que solo se puede usar tras validar la huella o el rostro
// (BiometricPrompt con CryptoObject). Fuera del teléfono no sirven. La contraseña nunca queda en texto plano.
object BiometricVault {

    private const val KEY_ALIAS = "skillswap_biometric_key"
    private const val PREFS = "skillswap_biometric"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // true si el teléfono tiene un sensor biométrico fuerte y una huella o rostro registrados
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun savedUsername(context: Context): String? = prefs(context).getString("username", null)

    // true si hay un acceso guardado (y, si se indica un usuario, es de ese usuario)
    fun isEnabled(context: Context, username: String? = null): Boolean {
        val p = prefs(context)
        val saved = p.getString("username", null)
        val stored = saved != null && p.contains("iv") && p.contains("secret")
        return stored && (username == null || username == saved)
    }

    fun disable(context: Context) {
        prefs(context).edit().clear().apply()
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // la llave ya no estaba
        }
    }

    // Guarda usuario y contraseña cifrados. Pide la huella o el rostro para autorizar el cifrado.
    fun enable(
        activity: FragmentActivity,
        username: String,
        password: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        } catch (e: Exception) {
            onError("No se pudo preparar el cifrado de este dispositivo")
            return
        }
        prompt(activity, "Activar acceso biométrico", "Confirma tu huella o rostro para guardar tu acceso",
            "Cancelar", cipher, onError) { done ->
            try {
                val encrypted = done.doFinal(password.toByteArray(Charsets.UTF_8))
                prefs(activity).edit()
                    .putString("username", username)
                    .putString("iv", Base64.encodeToString(done.iv, Base64.NO_WRAP))
                    .putString("secret", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply()
                onDone()
            } catch (e: Exception) {
                disable(activity)
                onError("No se pudo guardar el acceso biométrico")
            }
        }
    }

    // Pide la huella o el rostro y devuelve el usuario y la contraseña guardados para iniciar sesión.
    fun unlock(activity: FragmentActivity, onSuccess: (String, String) -> Unit, onError: (String) -> Unit) {
        val p = prefs(activity)
        val username = p.getString("username", null)
        val iv = p.getString("iv", null)
        val secret = p.getString("secret", null)
        if (username == null || iv == null || secret == null) {
            onError("Todavía no activaste el acceso biométrico")
            return
        }
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, loadKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Se registró o quitó una huella: Android invalida la llave por seguridad
            disable(activity)
            onError("Cambió la biometría del dispositivo. Entra con tu contraseña y vuelve a activar el acceso")
            return
        } catch (e: Exception) {
            disable(activity)
            onError("No se pudo leer el acceso guardado. Entra con tu contraseña y vuelve a activarlo")
            return
        }
        prompt(activity, "Entrar a SkillSwap", "Usa tu huella o rostro", "Usar contraseña", cipher, onError) { done ->
            try {
                val password = String(done.doFinal(Base64.decode(secret, Base64.NO_WRAP)), Charsets.UTF_8)
                onSuccess(username, password)
            } catch (e: Exception) {
                onError("No se pudo validar tu huella o rostro")
            }
        }
    }

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        cipher: Cipher,
        onError: (String) -> Unit,
        onAuthenticated: (Cipher) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authorized = result.cryptoObject?.cipher
                if (authorized != null) onAuthenticated(authorized) else onError("No se pudo validar tu huella o rostro")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancelar o elegir la contraseña no es un error que haya que mostrar
                val quiet = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                if (!quiet) onError(errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    // Llave nueva en cada activación; exige huella o rostro en cada uso
    @Suppress("DEPRECATION")
    private fun createKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun loadKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(KEY_ALIAS, null) as SecretKey
    }
}
