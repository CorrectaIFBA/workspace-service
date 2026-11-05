package io.correcta.workspace.security.annotations

/**
 * Restricts the endpoint to an identity-service profile type (for example "STAFF", "FACULTY" or "STUDENT").
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IsProfileType(val profileType: String)
