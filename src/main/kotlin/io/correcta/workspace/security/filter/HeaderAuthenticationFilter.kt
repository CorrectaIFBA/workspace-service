package io.correcta.workspace.security.filter

import io.correcta.workspace.security.models.IdentityHeaders
import io.correcta.workspace.security.models.ProfilePrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Builds the authentication from the identity headers set by the gateway. This service never validates
 * tokens itself, so it must only be reachable through the gateway.
 */
class HeaderAuthenticationFilter : OncePerRequestFilter() {

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    val profileId = request.readHeader(IdentityHeaders.PROFILE_ID)

    if (profileId != null) {
      val traits = request.readHeader(IdentityHeaders.TRAITS)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

      val principal = ProfilePrincipal(
        profileId = profileId,
        personId = request.readHeader(IdentityHeaders.PERSON_ID),
        profileType = request.readHeader(IdentityHeaders.PROFILE_TYPE),
        login = request.readHeader(IdentityHeaders.LOGIN),
        email = request.readHeader(IdentityHeaders.EMAIL),
        traits = traits,
      )

      SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken(principal, null, traits.map(::SimpleGrantedAuthority))
    }

    filterChain.doFilter(request, response)
  }

  override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.method.equals("OPTIONS", ignoreCase = true)

  private fun HttpServletRequest.readHeader(name: String): String? = getHeader(name)?.trim()?.takeIf { it.isNotEmpty() }
}