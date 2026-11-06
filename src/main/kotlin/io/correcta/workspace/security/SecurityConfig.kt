package io.correcta.workspace.security

import io.correcta.workspace.security.filter.HeaderAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

  @Bean
  fun filterChain(http: HttpSecurity): SecurityFilterChain {
    // CORS is handled by the gateway; endpoint access is enforced by SecurityAspect annotations.
    http.authorizeHttpRequests {
      it.anyRequest().permitAll()
    }
    http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    http.httpBasic { it.disable() }
    http.formLogin { it.disable() }
    http.anonymous { it.disable() }
    http.csrf { it.disable() }
    http.addFilterBefore(HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)

    return http.build()
  }
}