export default async function registerDeviceAdminRoutes(app, config, state) {
  state.devices ||= new Map()

  app.get('/devices', async (req, reply) => {
    const list = Array.from(state.devices.values()).map(d => ({
      deviceId: d.deviceId,
      revoked: !!d.revoked,
      createdAt: d.createdAt,
      updatedAt: d.updatedAt
    }))
    return { devices: list }
  })

  app.post('/devices/:id/revoke', async (req, reply) => {
    const id = req.params.id
    const dev = state.devices.get(id)
    if (!dev) { reply.code(404); return { error: 'not_found' } }
    dev.revoked = true
    dev.updatedAt = Date.now()
    state.devices.set(id, dev)
    return { ok: true }
  })
}


