package org.opendnevnik.shared.mesh.oauth

/** Keep the pending state/verifier in memory for one authorization attempt. */
class OAuthAuthorizationRequest(
    val authorizationUrl: String,
    val state: String,
    val codeVerifier: String,
) {
    override fun toString(): String = "OAuthAuthorizationRequest(<redacted>)"
}

/** Local session model, not a DTO with presumed provider field names. */
class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
) {
    override fun toString(): String = "OAuthTokens(<redacted>)"
}
