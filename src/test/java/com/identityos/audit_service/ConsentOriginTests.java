package com.identityos.audit_service;

import com.identityos.audit_service.model.ServerOrigin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsentOriginTests {
  @Test void nativeConsentServiceHasItsOwnAuditOrigin() {
    assertEquals(ServerOrigin.CONSENT_MANAGEMENT_SERVICE,ServerOrigin.from("consent-management-service"));
    assertEquals("consent-management-service",ServerOrigin.CONSENT_MANAGEMENT_SERVICE.value());
  }
}
