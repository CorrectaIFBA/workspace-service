package io.correcta.workspace.security

import io.correcta.workspace.exceptions.UnauthorizedException
import io.correcta.workspace.security.annotations.IsLoggedUser
import io.correcta.workspace.security.annotations.IsProfileType
import io.correcta.workspace.security.components.ScopeEvaluatorComponent
import io.correcta.workspace.security.components.aspects.SecurityAspect
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.Signature
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.authorization.AuthorizationDeniedException
import java.lang.reflect.Method
import kotlin.test.assertFailsWith

interface SecuredApi {
  @IsLoggedUser
  @IsProfileType("STUDENT")
  fun secured(id: String)

  fun open(id: String)
}

class SecuredApiImpl : SecuredApi {
  override fun secured(id: String) = Unit

  override fun open(id: String) = Unit
}

/** Same method name as [SecuredApi.secured] but another signature, so it must not match. */
class LookAlike {
  fun secured(id: Int) = Unit
}

class SecurityAspectTest {

  private val scopeEvaluator = mock(ScopeEvaluatorComponent::class.java)
  private val aspect = SecurityAspect(scopeEvaluator)

  private val secured: Method = SecuredApi::class.java.getMethod("secured", String::class.java)
  private val open: Method = SecuredApi::class.java.getMethod("open", String::class.java)

  @Test
  fun `allows a student on an endpoint annotated on the interface`() {
    givenCaller(loggedIn = true, profileType = "STUDENT")

    aspect.authorize(joinPoint(SecuredApiImpl::class.java.getMethod("secured", String::class.java), SecuredApiImpl()))
  }

  @Test
  fun `rejects anonymous callers with 401`() {
    givenCaller(loggedIn = false, profileType = "STUDENT")

    assertFailsWith<UnauthorizedException> { aspect.authorize(joinPoint(secured, SecuredApiImpl())) }
  }

  @Test
  fun `rejects other profile types with 403`() {
    givenCaller(loggedIn = true, profileType = "FACULTY")

    assertFailsWith<AuthorizationDeniedException> { aspect.authorize(joinPoint(secured, null)) }
  }

  @Test
  fun `endpoints without annotations are open`() {
    aspect.authorize(joinPoint(open, SecuredApiImpl()))

    verify(scopeEvaluator, never()).isLoggedUser()
  }

  @Test
  fun `a target without a matching method falls back to the declared method`() {
    givenCaller(loggedIn = false, profileType = "STUDENT")

    assertFailsWith<UnauthorizedException> { aspect.authorize(joinPoint(secured, LookAlike())) }
  }

  @Test
  fun `join points that are not methods are not checked`() {
    val joinPoint = mock(JoinPoint::class.java)
    `when`(joinPoint.signature).thenReturn(mock(Signature::class.java))

    aspect.authorize(joinPoint)

    verify(scopeEvaluator, never()).isLoggedUser()
  }

  private fun givenCaller(loggedIn: Boolean, profileType: String) {
    `when`(scopeEvaluator.isLoggedUser()).thenReturn(loggedIn)
    `when`(scopeEvaluator.isProfileType("STUDENT")).thenReturn(profileType == "STUDENT")
  }

  private fun joinPoint(method: Method, target: Any?): JoinPoint {
    val signature = mock(MethodSignature::class.java)
    `when`(signature.method).thenReturn(method)
    val joinPoint = mock(JoinPoint::class.java)
    `when`(joinPoint.signature).thenReturn(signature)
    `when`(joinPoint.target).thenReturn(target)
    return joinPoint
  }
}