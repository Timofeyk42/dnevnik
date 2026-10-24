package org.opendnevnik.shared.mesh.oauth

/** Intentionally unimplemented. Nothing here is connected to the current login button. */
class OAuthClientTemplate(private val config: OAuthConfig) : OAuthClient {
    override fun createAuthorizationRequest(): OAuthAuthorizationRequest {
        // TODO: secure random state/verifier, PKCE S256 and encoded authorization URL.
        TODO("Build the provider-supported authorization request")
    }

    override suspend fun completeAuthorization(callbackUrl: String, pending: OAuthAuthorizationRequest): OAuthTokens {
        // TODO: exact redirect URI, one-time state check, then exchange code + verifier.
        TODO("Validate the callback and exchange the authorization code")
    }

    override suspend fun refresh(tokens: OAuthTokens): OAuthTokens {
        // TODO: provider-supported refresh and token rotation; never loop on 401/403.
        TODO("Implement session refresh")
    }

    override suspend fun revoke(tokens: OAuthTokens) {
        // TODO: revocation only if supported by the provider.
        TODO("Implement provider-supported revocation")
    }
}
