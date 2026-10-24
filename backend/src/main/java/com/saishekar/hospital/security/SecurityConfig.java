package com.saishekar.hospital.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    // Patient records are readable by every clinical and front-desk role,
    // writable by the staff who register and update them, and deletable
    // only by administrators. The staff directory follows the same shape,
    // except department structure is an administrator's decision alone.
    // Appointments split one step further: the front desk books and
    // reschedules them, but every role that works a clinic session can
    // move one through its status lifecycle (check-in, completion,
    // no-show), because that happens at the point of care, not the desk.
    // Rules live here, in one place, rather than scattered across
    // controllers.
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/patients/**", "/api/doctors/**", "/api/departments/**", "/api/appointments/**")
                            .hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/patients/**", "/api/doctors/**", "/api/appointments/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.PUT, "/api/patients/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "NURSE")
                        .requestMatchers(HttpMethod.PUT, "/api/doctors/**", "/api/appointments/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.PATCH, "/api/appointments/**")
                            .hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.DELETE, "/api/patients/**", "/api/doctors/**", "/api/appointments/**")
                            .hasRole("ADMIN")
                        .requestMatchers("/api/departments/**")
                            .hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
