package io.correcta.workspace.security.annotations

/** Restricts the endpoint to authenticated callers; enforced by `SecurityAspect`. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IsLoggedUser