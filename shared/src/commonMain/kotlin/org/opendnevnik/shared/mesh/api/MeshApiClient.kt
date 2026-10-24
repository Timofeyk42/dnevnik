package org.opendnevnik.shared.mesh.api

import org.opendnevnik.shared.mesh.api.dto.MeshDiaryResponse

/** Template: implement transport here, for example with a Ktor client injected in the constructor. */
class MeshApiClient(private val config: MeshApiConfig) : MeshApi {
    override suspend fun fetchDiary(studentId: String, accessToken: String): MeshDiaryResponse {
        // TODO: confirmed path, request headers, timeout and response/error handling.
        // Keep authorization on the configured host; do not log headers or response bodies.
        TODO("Implement the confirmed МЭШ API request")
    }
}
