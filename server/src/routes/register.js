import { SignJWT } from 'jose'
import { GoogleAuth } from 'google-auth-library'

export default async function registerRegisterRoute(app, config, state) {
  // In-memory device registry for MVP; replace with durable store
  state.devices ||= new Map()

  app.post('/register', {
    schema: {
      body: {
        type: 'object',
        required: ['attestation'],
        properties: {
          attestation: { type: 'string' },
          devicePublicKeyJwk: { type: 'object' }
        }
      }
    }
  }, async (req, reply) => {
    try {
      const { attestation, devicePublicKeyJwk } = req.body

      if (!attestation || typeof attestation !== 'string') {
        reply.code(400)
        return { error: 'Invalid attestation' }
      }

      // Verify Play Integrity by calling Google's decode endpoint when configured
      const integrityJwt = attestation
      if (config.integrity?.required) {
        const ticket = await verifyPlayIntegrity(integrityJwt, config.integrity.packageName)
        if (!ticket.valid) { reply.code(401); return { error: 'bad_attestation' } }
        if (config.integrity.packageName && ticket.packageName && ticket.packageName !== config.integrity.packageName) {
          reply.code(401); return { error: 'pkg_mismatch' }
        }
        if (config.integrity.certDigest && Array.isArray(ticket.certificateDigests) && !ticket.certificateDigests.includes(config.integrity.certDigest)) {
          reply.code(401); return { error: 'cert_mismatch' }
        }
      }
      // Optional: check package name + certDigest against expected
      // const expectedPkg = process.env.PLAY_PACKAGE_NAME
      // const expectedSha = process.env.PLAY_CERT_DIGEST
      // if (ticket.packageName !== expectedPkg || !ticket.certificateDigests?.includes(expectedSha)) { reply.code(401); return { error: 'pkg_mismatch' } }

      // Derive a deviceId from public key thumbprint
      let deviceId
      if (devicePublicKeyJwk) {
        await importPublicKey(devicePublicKeyJwk)
        deviceId = await jwkThumbprint(devicePublicKeyJwk)
      } else {
        // Derive deviceId from attestation hash as fallback
        deviceId = await jwkThumbprint({ crv: 'P-256', kty: 'EC', x: Buffer.from(integrityJwt).toString('base64').slice(0,43), y: Buffer.from(integrityJwt).toString('base64').slice(0,43) })
      }

      // Persist device
      const now = Date.now()
      state.devices.set(deviceId, { deviceId, devicePublicKeyJwk, createdAt: now, updatedAt: now, revoked: false })

      // Issue JWT
      const kid = 'v1'
      const secret = new TextEncoder().encode(process.env.JWT_SIGNING_SECRET || 'dev-secret-change')
      const token = await new SignJWT({ deviceId, aud: 'journai-prod' })
        .setProtectedHeader({ alg: 'HS256', kid })
        .setIssuedAt()
        .setExpirationTime('7d')
        .sign(secret)

      return { token, deviceId, kid, expiresIn: 7 * 24 * 3600 }
    } catch (err) {
      req.log.error({ err }, '/register error')
      reply.code(500)
      return { error: 'register_failed' }
    }
  })
}

async function importPublicKey(jwk) {
  try {
    return await crypto.subtle.importKey(
      'jwk',
      jwk,
      { name: 'ECDSA', namedCurve: 'P-256' },
      true,
      ['verify']
    )
  } catch {
    return null
  }
}

async function jwkThumbprint(jwk) {
  const data = JSON.stringify({ crv: jwk.crv, kty: jwk.kty, x: jwk.x, y: jwk.y })
  const bytes = new TextEncoder().encode(data)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Buffer.from(new Uint8Array(digest)).toString('hex')
}

async function verifyGoogleJwt(jwt) {
  try {
    // For brevity, we do a simple decode; production should verify signature via Google JWKs
    const parts = jwt.split('.')
    const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString())
    return {
      valid: !!payload,
      packageName: payload?.appIntegrity?.packageName,
      certificateDigests: payload?.appIntegrity?.certificateSha256Digest
    }
  } catch {
    return { valid: false }
  }
}

async function verifyPlayIntegrity(integrityToken, packageName) {
  try {
    // If GOOGLE_APPLICATION_CREDENTIALS is set, use service account to call Google API
    if (process.env.GOOGLE_APPLICATION_CREDENTIALS && packageName) {
      const auth = new GoogleAuth({ scopes: ['https://www.googleapis.com/auth/playintegrity'] })
      const client = await auth.getClient()
      const url = `https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`
      const res = await client.request({
        url,
        method: 'POST',
        data: { integrityToken }
      })
      const result = res.data || {}
      const appIntegrity = result.tokenPayloadExternal?.appIntegrity || {}
      return {
        valid: !!appIntegrity?.appRecognitionVerdict,
        packageName: appIntegrity?.packageName,
        certificateDigests: appIntegrity?.certificateSha256Digest
      }
    }
  } catch (e) {
    return { valid: false }
  }
  // Fallback basic parse if not configured
  return await verifyGoogleJwt(integrityToken)
}


