package io.correcta.workspace.security.models

/**
 * Identity headers injected by the gateway (local proxy or forward auth) after it resolves the caller
 * through identity-service `GET /account/me`. The gateway strips client-supplied copies of these headers.
 */
object IdentityHeaders {
  const val PROFILE_ID = "X-User-Id"
  const val PERSON_ID = "X-User-Person-Id"
  const val PROFILE_TYPE = "X-User-Profile-Type"
  const val LOGIN = "X-User-Login"
  const val EMAIL = "X-User-Email"

  /** Comma-separated trait values. */
  const val TRAITS = "X-User-Traits"
}