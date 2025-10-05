package com.journai.journai.auth

import android.content.Context
import com.journai.journai.network.ProxyApi
import com.journai.journai.network.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class AuthManager(
    private val context: Context,
    private val api: ProxyApi,
    private val securePrefs: SecurePrefs,
    private val keyManager: DeviceKeyManager,
    private val integrityService: IntegrityService
) {
    suspend fun ensureToken(): String = withContext(Dispatchers.IO) {
        securePrefs.getJwt() ?: register()
        return@withContext securePrefs.getJwt() ?: ""
    }

    suspend fun register(): String = withContext(Dispatchers.IO) {
        val nonce = sha256Hex(System.currentTimeMillis().toString())
        val integrity = integrityService.getIntegrityToken(nonce)
        val jwk = keyManager.getPublicJwk()
        val resp = api.register(RegisterRequest(attestation = integrity, devicePublicKeyJwk = jwk))
        securePrefs.setJwt(resp.token)
        securePrefs.setDeviceId(resp.deviceId)
        return@withContext resp.token
    }

    fun clear() { securePrefs.setJwt(null); securePrefs.setDeviceId(null) }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}


