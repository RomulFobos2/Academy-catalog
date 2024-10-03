package com.academy.catalog.security;


import com.academy.catalog.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

@Configuration
@EnableWebSecurity
public class SecurityConfigUser {

    private final UserService userService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public SecurityConfigUser(UserService userService, BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.userService = userService;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    @Bean
    public SecurityFilterChain userSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/static/**", "/images/**", "/css/**", "/js/**", "/", "/image/**", "/profile", "/h2-console/**").permitAll()
                        .requestMatchers("/visitor/**").hasRole("VISITOR")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(httpSecurityFormLoginConfigurer ->
                        httpSecurityFormLoginConfigurer.loginPage("/login")
                                .loginProcessingUrl("/login")
                                .failureUrl("/login?error=loginError")
                                .defaultSuccessUrl("/", true)
                                .permitAll())
                .logout(logout -> logout.permitAll().logoutSuccessUrl("/"))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .accessDeniedHandler(accessDeniedHandler()) // Добавляем обработчик ошибок
                )
                .authenticationProvider(userAuthenticationProvider())
        .headers(headers -> headers.frameOptions().sameOrigin());
        return http.build();
    }

    @Bean
    public AuthenticationProvider userAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(bCryptPasswordEncoder);
        return provider;
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        // Настраиваем обработку доступа 403
        AccessDeniedHandlerImpl accessDeniedHandler = new AccessDeniedHandlerImpl();
        accessDeniedHandler.setErrorPage("/403");
        return accessDeniedHandler;
    }
}
