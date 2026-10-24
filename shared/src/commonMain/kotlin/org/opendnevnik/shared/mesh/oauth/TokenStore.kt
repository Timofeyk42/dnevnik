package org.opendnevnik.shared.mesh.oauth

/** Platform storage: Android Keystore + encrypted file, or iOS Keychain. */
interface TokenStore {
    suspend fun read(): OAuthTokens?
    suspend fun save(tokens: OAuthTokens)
    suspend fun clear()
}
