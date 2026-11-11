package io.correcta.workspace.security.components

import io.correcta.workspace.security.models.ProfilePrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/** Reads the authenticated [ProfilePrincipal] from the security context. */
@Component("scopeEvaluator")
class ScopeEvaluatorComponent {

  fun isLoggedUser(): Boolean = currentProfile() != null

  fun isProfileType(profileType: String): Boolean = currentProfile()?.profileType.equals(profileType, ignoreCase = true)

  fun currentProfile(): ProfilePrincipal? = currentAuthentication()?.principal as? ProfilePrincipal

  private fun currentAuthentication(): Authentication? = SecurityContextHolder.getContext().authentication
    ?.takeIf { it.isAuthenticated }
}