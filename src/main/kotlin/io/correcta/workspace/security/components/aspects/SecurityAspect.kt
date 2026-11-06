package io.correcta.workspace.security.components.aspects

import io.correcta.workspace.exceptions.UnauthorizedException
import io.correcta.workspace.security.annotations.IsLoggedUser
import io.correcta.workspace.security.annotations.IsNotTraitedWith
import io.correcta.workspace.security.annotations.IsProfileType
import io.correcta.workspace.security.annotations.IsTraitedWith
import io.correcta.workspace.security.components.ScopeEvaluatorComponent
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.aop.support.AopUtils
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.stereotype.Component
import java.lang.reflect.Method

@Aspect
@Component
class SecurityAspect(
  private val scopeEvaluator: ScopeEvaluatorComponent,
) {

  @Before("execution(* io.correcta.workspace.controllers..*(..))")
  fun authorize(joinPoint: JoinPoint) {
    val isLoggedUser = findSecurityAnnotation<IsLoggedUser>(joinPoint)
    if (isLoggedUser != null && !scopeEvaluator.isLoggedUser()) {
      throwNotLoggedIn()
    }

    val isProfileType = findSecurityAnnotation<IsProfileType>(joinPoint)
    if (isProfileType != null && !scopeEvaluator.isProfileType(isProfileType.profileType)) {
      throwAccessDenied()
    }

    val isTraitedWith = findSecurityAnnotation<IsTraitedWith>(joinPoint)
    if (isTraitedWith != null && !scopeEvaluator.isTraitedWith(isTraitedWith.trait)) {
      throwAccessDenied()
    }

    val isNotTraitedWith = findSecurityAnnotation<IsNotTraitedWith>(joinPoint)
    if (isNotTraitedWith != null && !scopeEvaluator.isNotTraitedWith(isNotTraitedWith.trait)) {
      throwAccessDenied()
    }
  }

  private inline fun <reified T : Annotation> findSecurityAnnotation(joinPoint: JoinPoint): T? = resolveMethodCandidates(joinPoint)
    .firstNotNullOfOrNull { AnnotatedElementUtils.findMergedAnnotation(it, T::class.java) }

  private fun resolveMethodCandidates(joinPoint: JoinPoint): List<Method> {
    val signature = joinPoint.signature as? MethodSignature ?: return emptyList()
    val method = signature.method
    val targetClass = joinPoint.target?.let { AopUtils.getTargetClass(it) }

    val candidates = linkedSetOf<Method>()
    candidates.add(method)

    if (targetClass != null) {
      candidates.add(AopUtils.getMostSpecificMethod(method, targetClass))
      targetClass.findMatchingMethod(method)?.let(candidates::add)

      targetClass.interfaces
        .mapNotNull { it.findMatchingMethod(method) }
        .forEach(candidates::add)
    }

    method.declaringClass.interfaces
      .mapNotNull { it.findMatchingMethod(method) }
      .forEach(candidates::add)

    return candidates.toList()
  }

  private fun Class<*>.findMatchingMethod(method: Method): Method? = methods.firstOrNull {
    it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes)
  }

  private fun throwAccessDenied(): Nothing = throw AuthorizationDeniedException("Você não tem permissão para acessar este recurso.")

  private fun throwNotLoggedIn(): Nothing = throw UnauthorizedException("É necessário estar autenticado para acessar este recurso.")
}