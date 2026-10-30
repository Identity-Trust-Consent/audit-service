package com.identityos.audit_service.web;
import com.identityos.audit_service.service.AuditRecordWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/audits/integration")
public class IntegrationAuditController {
  public record Event(@NotNull UUID eventId,@NotBlank @Size(max=100) String action,
                      @NotBlank @Size(max=255) String subject,@NotBlank @Size(max=100) String outcome,
                      @Size(max=255) String resourceId,@Size(max=100) String decisionId,
                      @NotBlank @Size(max=100) String correlationId) {}
  private final AuditRecordWriter writer;
  public IntegrationAuditController(AuditRecordWriter writer) { this.writer=writer; }
  @GetMapping public Object records(@AuthenticationPrincipal Jwt caller,@RequestParam(defaultValue="25") int limit) {
    String app=caller.getClaimAsString("application_id"),org=caller.getClaimAsString("organization_id");
    if(app==null || org==null || !("identityos-"+app+"-service").equals(caller.getClaimAsString("azp")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Registered service required");
    return writer.findForApplication(org,app,limit);
  }
  @PostMapping public ResponseEntity<?> write(@AuthenticationPrincipal Jwt caller,@Valid @RequestBody Event event) {
    String app=caller.getClaimAsString("application_id"),org=caller.getClaimAsString("organization_id");
    if(app==null || org==null || !("identityos-"+app+"-service").equals(caller.getClaimAsString("azp")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Registered service required");
    var payload=new LinkedHashMap<String,Object>();
    payload.put("primaryKey",event.eventId().toString());payload.put("origin","client-application");
    payload.put("applicationId",app);payload.put("organizationId",org);payload.put("actorUserId",event.subject());
    payload.put("action",event.action());payload.put("status",event.outcome());payload.put("entityId",event.resourceId());
    payload.put("metadata",Map.of("correlationId",event.correlationId(),"decisionId",event.decisionId()==null?"":event.decisionId()));
    writer.write(payload);return ResponseEntity.accepted().body(Map.of("status","stored","eventId",event.eventId()));
  }
  @PostMapping("/platform") public ResponseEntity<?> platform(@RequestBody Map<String,Object> event) {
    if(!Set.of("onboarding-and-identity-service","consent-management-service").contains(event.get("origin")))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported platform event origin");
    writer.write(event);return ResponseEntity.accepted().body(Map.of("status","stored"));
  }
}
