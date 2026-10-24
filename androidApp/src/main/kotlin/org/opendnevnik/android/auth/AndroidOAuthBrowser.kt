package org.opendnevnik.android.auth

import android.app.Activity
import org.opendnevnik.shared.mesh.oauth.OAuthAuthorizationRequest

/** Activity-scoped platform template. Do not retain this object in a singleton or shared ViewModel. */
class AndroidOAuthBrowser(private val activity: Activity) {
    fun launch(request: OAuthAuthorizationRequest) {
        // TODO: open request.authorizationUrl in Custom Tabs and receive a verified callback.
        // Register callback handling in the manifest only once the actual redirect URI is known.
        TODO("Implement the system-browser authorization handoff")
    }
}
