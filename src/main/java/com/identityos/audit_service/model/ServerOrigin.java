package com.identityos.audit_service.model;

import java.util.Arrays;

public enum ServerOrigin {
  CLIENT_APPLICATION("client-application"),
  ONBOARDING_AND_IDENTITY_SERVICE("onboarding-and-identity-service"),
  CMS_SERVICE("cms-service"),
  CONSENT_MANAGEMENT_SERVICE("consent-management-service");

  private final String value;

  ServerOrigin(String value) {
    this.value = value;
  }

  public static ServerOrigin from(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase();
    if (normalized.equals("onboarding-service")
        || normalized.equals("identity-service")
        || normalized.equals("onboarding-identity-service")) {
      return ONBOARDING_AND_IDENTITY_SERVICE;
    }
    if (normalized.equals("catalogue-service") || normalized.equals("catalog-service")) {
      return CMS_SERVICE;
    }
    return Arrays.stream(values())
        .filter(origin -> origin.value.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported audit origin: " + value));
  }

  public String value() {
    return value;
  }
}
