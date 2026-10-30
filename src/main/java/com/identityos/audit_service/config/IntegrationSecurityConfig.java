package com.identityos.audit_service.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class IntegrationSecurityConfig {
  @Bean JwtDecoder jwtDecoder(@Value("${integration.issuer:http://localhost:8085/realms/identity-os}") String issuer,
      @Value("${integration.jwks-uri:http://localhost:8085/realms/identity-os/protocol/openid-connect/certs}") String jwks) {
    var decoder=NimbusJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),jwt ->
      jwt.getSubject()!=null && jwt.getExpiresAt()!=null && "Bearer".equals(jwt.getClaimAsString("typ")) && jwt.getAudience().contains("identity-os-services")
      ? OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));return decoder;
  }
  @Bean SecurityFilterChain integrationSecurity(HttpSecurity http,@Value("${integration.legacy-api-enabled:true}") boolean legacy) throws Exception {
    http.securityMatcher(legacy?"/audits/integration/**":"/**")
      .csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/health/**").permitAll()
        .requestMatchers("/audits/integration/platform").hasAuthority("SCOPE_identityos:internal")
        .requestMatchers("/audits/integration/**").hasAuthority("SCOPE_identityos:audit").anyRequest().denyAll())
      .oauth2ResourceServer(o->o.jwt(j->{}));return http.build();
  }
}
