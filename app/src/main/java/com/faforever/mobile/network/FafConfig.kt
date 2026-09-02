package com.faforever.mobile.network

object FafConfig {
    const val API_BASE_URL = "https://api.faforever.com/"
    const val USER_API_BASE_URL = "https://user.faforever.com/"
    const val IRC_HOST = "chat.faforever.com"
    const val IRC_PORT = 443
    const val CONTENT_URL = "https://content.faforever.com"

    const val OAUTH_HOST = "https://hydra.faforever.com"
    const val OAUTH_AUTH_ENDPOINT = "$OAUTH_HOST/oauth2/auth"
    const val OAUTH_TOKEN_ENDPOINT = "$OAUTH_HOST/oauth2/token"
    const val OAUTH_DEVICE_AUTH_ENDPOINT = "$OAUTH_HOST/oauth2/device/auth"
    const val OAUTH_CLIENT_ID = "faf-mobile-client"
    const val OAUTH_DEVICE_CLIENT_ID = "2e8808cf-5889-469b-b2c3-01f0cc58c4af"
    const val OAUTH_USERINFO_ENDPOINT = "$OAUTH_HOST/userinfo"
    const val OAUTH_REDIRECT_URI = "com.faforever.mobile://oauth/callback"
    const val OAUTH_SCOPES = "openid offline public_profile lobby"

    const val IRC_DEFAULT_CHANNEL = "#aeolus"
}
