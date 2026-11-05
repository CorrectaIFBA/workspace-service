package io.correcta.workspace.security.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IsNotTraitedWith(val trait: String)
