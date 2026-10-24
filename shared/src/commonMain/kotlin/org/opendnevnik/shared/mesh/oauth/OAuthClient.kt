package org.opendnevnik.shared.mesh.oauth

/** Shared OAuth boundary; platform UI opens the returned URL in the system browser. */
interface OAuthClient {
    fun createAuthorizationRequest(): OAuthAuthorizationRequest
    suspend fun completeAuthorization(callbackUrl: String, pending: OAuthAuthorizationRequest): OAuthTokens
    suspend fun refresh(tokens: OAuthTokens): OAuthTokens
    suspend fun revoke(tokens: OAuthTokens)
}
