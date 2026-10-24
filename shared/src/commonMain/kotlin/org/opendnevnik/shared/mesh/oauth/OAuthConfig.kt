package org.opendnevnik.shared.mesh.oauth

/** Provider configuration supplied by the developer; a native public client has no embedded secret. */
class OAuthConfig(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>,
    val revocationEndpoint: String? = null,
)
