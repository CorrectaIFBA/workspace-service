package io.correcta.workspace.components

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class VaryHeaderSuppressingFilter : OncePerRequestFilter() {
  @Throws(ServletException::class, IOException::class)
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    val wrapper: HttpServletResponseWrapper = object : HttpServletResponseWrapper(response) {
      override fun addHeader(name: String?, value: String?) {
        if (!"Vary".equals(name, ignoreCase = true)) {
          super.addHeader(name, value)
        }
      }

      override fun setHeader(name: String?, value: String?) {
        if (!"Vary".equals(name, ignoreCase = true)) {
          super.setHeader(name, value)
        }
      }

      override fun getHeaderNames(): MutableCollection<String> {
        val headers: MutableList<String> = ArrayList(super.getHeaderNames())
        headers.removeIf { anotherString -> "Vary".equals(anotherString, ignoreCase = true) }
        return headers
      }
    }

    filterChain.doFilter(request, wrapper)
  }
}