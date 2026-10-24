package org.opendnevnik.shared.mesh.api

import org.opendnevnik.shared.mesh.api.dto.MeshDiaryResponse

/** App-level API boundary, not a claim about the provider's endpoints. */
interface MeshApi {
    suspend fun fetchDiary(studentId: String, accessToken: String): MeshDiaryResponse
}
