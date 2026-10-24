package org.opendnevnik.android.auth

import android.content.Context
import org.opendnevnik.shared.mesh.oauth.OAuthTokens
import org.opendnevnik.shared.mesh.oauth.TokenStore

/** Platform template, not an implemented encrypted store. Use application context only. */
class AndroidTokenStore(context: Context) : TokenStore {
    private val applicationContext = context.applicationContext

    override suspend fun read(): OAuthTokens? {
        TODO("Read and decrypt the session using Android Keystore")
    }

    override suspend fun save(tokens: OAuthTokens) {
        TODO("Encrypt the session with Keystore-backed AES-GCM and write to private storage")
    }

    override suspend fun clear() {
        TODO("Remove the encrypted session and its key")
    }
}
