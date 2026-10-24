package org.opendnevnik.shared.mesh.data

import org.opendnevnik.shared.mesh.DiaryResult
import org.opendnevnik.shared.mesh.MeshRepository
import org.opendnevnik.shared.mesh.api.MeshApi
import org.opendnevnik.shared.mesh.oauth.OAuthClient
import org.opendnevnik.shared.mesh.oauth.TokenStore

/** Wiring template for the future integration; the current UI does not instantiate it. */
class RemoteMeshRepository(
    private val api: MeshApi,
    private val oauth: OAuthClient,
    private val tokens: TokenStore,
    private val mapper: MeshDiaryMapper,
) : MeshRepository {
    override suspend fun fetchDiary(studentId: String): DiaryResult {
        // TODO: read session, refresh if needed, call API, map response and classify errors.
        TODO("Connect TokenStore, OAuthClient, MeshApi and MeshDiaryMapper")
    }

    override suspend fun signOut() {
        // TODO: cancel work, clear local session/cache even if server revocation fails.
        TODO("Implement logout and local cleanup")
    }
}
