package org.opendnevnik.shared.mesh.api.dto

import kotlinx.serialization.json.JsonElement

/** Replace the raw payload with @Serializable DTOs after confirming the real API response. */
class MeshDiaryResponse(val payload: JsonElement)
