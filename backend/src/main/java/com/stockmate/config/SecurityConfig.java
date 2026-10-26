package com.stockmate.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.stockmate.security.ProblemAuthenticationEntryPoint;
import com.stockmate.security.ProblemAccessDeniedHandler;

/**
 * Security setup: stateless JWT authentication with two roles, ADMIN and STAFF.
 * إعداد الأمان: مصادقة JWT بدون جلسات (stateless) مع دورين: ADMIN و STAFF.
 *
 * <p>We use Spring Security's built-in OAuth2 Resource Server support to validate tokens instead of a
 * hand-written filter: it checks the signature and expiry for us and is well tested.</p>
 * <p>نستخدم دعم OAuth2 Resource Server المدمج في Spring Security للتحقق من الرموز بدل كتابة فلتر يدوي:
 * يتحقق من التوقيع والانتهاء بنفسه ومختبر جيدًا.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Name of the JWT claim that holds the user's role.
    // اسم الحقل داخل رمز JWT الذي يحمل صلاحية المستخدم.
    public static final String ROLE_CLAIM = "role";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProblemAuthenticationEntryPoint entryPoint,
                                            ProblemAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // CSRF protection is for cookie sessions; we send the token in the Authorization header, so it is not needed.
                // حماية CSRF مخصصة لجلسات الكوكيز، ونحن نرسل الرمز في ترويسة Authorization فلا نحتاجها.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                // Never create an HTTP session: every request must carry its own token.
                // لا ننشئ جلسة HTTP أبدًا: كل طلب لازم يحمل رمزه.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints: login, health check and the API docs.
                        // روابط عامة: تسجيل الدخول، فحص الصحة، وتوثيق الـ API.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // User management is admin-only.
                        // إدارة المستخدمين للمدير فقط.
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        // Deleting master data is admin-only; staff can still create and edit.
                        // حذف البيانات الأساسية للمدير فقط، والموظف يقدر يضيف ويعدّل.
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        // Everything else under /api needs a valid token.
                        // باقي روابط /api تحتاج رمزًا صالحًا.
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // Return our ProblemDetail JSON instead of an empty 401 body.
                        // نرجّع JSON بصيغة ProblemDetail بدل رد 401 فاضي.
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Maps the "role" claim (e.g. "ADMIN") to the Spring authority "ROLE_ADMIN" used by hasRole().
     * يحوّل حقل "role" (مثل "ADMIN") إلى الصلاحية "ROLE_ADMIN" التي يفهمها hasRole().
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Builds the HMAC-SHA256 signing key from the configured secret.
     * يبني مفتاح التوقيع HMAC-SHA256 من السر الموجود في الإعدادات.
     */
    @Bean
    SecretKey jwtSecretKey(AppProperties properties) {
        byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    // Signs new tokens at login.
    // يوقّع الرموز الجديدة عند تسجيل الدخول.
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    // Verifies the signature and expiry of incoming tokens.
    // يتحقق من توقيع وانتهاء الرموز القادمة مع كل طلب.
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    // BCrypt hashes passwords with a random salt; plain passwords are never stored.
    // BCrypt يشفّر كلمات المرور مع salt عشوائي، ولا نخزّن كلمة المرور الأصلية أبدًا.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Checks username + password against the database during login.
    // يتحقق من اسم المستخدم وكلمة المرور مقابل قاعدة البيانات عند الدخول.
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    // Lets the Angular app (another origin in development) call the API from the browser.
    // يسمح لتطبيق Angular (مصدر مختلف أثناء التطوير) بالاتصال بالـ API من المتصفح.
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
