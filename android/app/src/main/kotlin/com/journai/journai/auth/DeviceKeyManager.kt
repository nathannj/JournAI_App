package com.journai.journai.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import android.util.Base64

class DeviceKeyManager(private val alias: String = "journai_device_key") {
    fun ensureKeypair(): java.security.KeyPair {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getCertificate(alias)?.let {
            val pub = ks.getCertificate(alias).publicKey
            val priv = ks.getKey(alias, null) as java.security.PrivateKey
            return java.security.KeyPair(pub, priv)
        }
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    fun getPublicJwk(): Map<String, Any?> {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val pub = ks.getCertificate(alias).publicKey as java.security.interfaces.ECPublicKey
        val params = pub.params.generator
        val x = pub.w.affineX
        val y = pub.w.affineY
        return mapOf(
            "kty" to "EC",
            "crv" to "P-256",
            "x" to base64Url(x.toByteArray()),
            "y" to base64Url(y.toByteArray())
        )
    }

    private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE)
}


