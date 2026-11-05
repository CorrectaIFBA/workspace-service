package io.correcta.workspace.security.components

import io.correcta.workspace.security.models.ProfilePrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component("scopeEvaluator")
class ScopeEvaluatorComponent {

    fun isLoggedUser(): Boolean {
        return currentProfile() != null
    }

    fun isProfileType(profileType: String): Boolean {
        return currentProfile()?.profileType.equals(profileType, ignoreCase = true)
    }

    fun isTraitedWith(trait: String): Boolean {
        val authentication = currentAuthentication() ?: return false
        if (authentication.principal !is ProfilePrincipal) return false

        return authentication.authorities.any { it.authority == trait }
    }

    fun isNotTraitedWith(trait: String): Boolean {
        return currentProfile() != null && !isTraitedWith(trait)
    }

    fun currentProfile(): ProfilePrincipal? {
        return currentAuthentication()?.principal as? ProfilePrincipal
    }

    private fun currentAuthentication(): Authentication? {
        return SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated }
    }
}
