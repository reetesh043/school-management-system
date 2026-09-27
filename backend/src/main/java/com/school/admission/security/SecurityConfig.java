package com.school.admission.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * HTTP Basic against the app_user table, which keeps the demo easy to call from curl, Swagger UI or an .http file.
 * For production put an OIDC provider in front and switch to a JWT resource server.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())                       // stateless API, no browser session
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))  // lets the H2 console render in a frame
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // The payment webhook authenticates itself with an HMAC signature.
                        .requestMatchers(ant("/h2-console/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/info", "/", "/index.html", "/prototype.html",
                                "/css/**", "/js/**", "/favicon.svg",
                                "/api/v1/public/**", "/api/v1/webhooks/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/**", HttpMethod.OPTIONS.name())).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }


    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.List.of("http://localhost:5173"));
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "If-Match", "Idempotency-Key"));
        config.setExposedHeaders(java.util.List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Understands the {bcrypt} prefix used by the demo users, and {noop} for quick experiments.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Ant matchers on purpose: with the H2 console servlet registered, string patterns are ambiguous
     * between Spring MVC and servlet matchers and Spring Security refuses to start.
     */
    private static RequestMatcher[] ant(String... patterns) {
        return Arrays.stream(patterns).map(AntPathRequestMatcher::new).toArray(RequestMatcher[]::new);
    }
}
