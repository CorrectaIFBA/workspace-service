package io.correcta.workspace.security.models

/** Authenticated caller, built from the [IdentityHeaders]. Only [profileId] is required. */
data class ProfilePrincipal(
  val profileId: String,
  val personId: String?,
  val profileType: String?,
  val login: String?,
  val email: String?,
)