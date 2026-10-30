package com.identityos.audit_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.identityos.audit_service.config.AuditProperties;
import com.identityos.audit_service.model.ServerOrigin;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuditRecordWriter {
  private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private final JdbcTemplate jdbcTemplate;
  private final AuditProperties properties;
  private final ObjectMapper objectMapper;

  public AuditRecordWriter(
      JdbcTemplate jdbcTemplate, AuditProperties properties, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void write(Map<String, Object> message) {
    AuditPayload payload = new AuditPayload(message, objectMapper);
    ServerOrigin origin = ServerOrigin.from(payload.requiredString("origin"));
    try {
      switch (origin) {
        case CLIENT_APPLICATION -> writeServiceAudit(payload, "client-application");
        case ONBOARDING_AND_IDENTITY_SERVICE ->
            writeServiceAudit(payload, "onboarding-and-identity-service");
        case CMS_SERVICE -> writeServiceAudit(payload, "cms-service");
        case CONSENT_MANAGEMENT_SERVICE -> writeServiceAudit(payload, "consent-management-service");
      }
    } catch (DuplicateKeyException ignored) {
      // Duplicate audit IDs are treated as idempotent retries from RabbitMQ.
    }
  }

  private void writeServiceAudit(AuditPayload payload, String serviceName) {
    String table = table(properties.getTables().getRecords());
    String actorUserId = firstString(payload, "actorUserId", "actor_user_id", "userid", "userId");
    String action = requiredFirstString(payload, "action", "event", "operation");
    String entityType = firstString(payload, "entityType", "entity_type", "resourceType", "type");
    String entityId =
        firstString(
            payload,
            "entityId",
            "entity_id",
            "id",
            "organizationId",
            "applicationId",
            "schemaId",
            "versionId",
            "contentId");

    jdbcTemplate.update(
        "insert into "
            + table
            + " (id, origin, service_name, actor_user_id, actor_role, action, http_method,"
            + " endpoint, entity_type, entity_id, organization_id, application_id,"
            + " schema_id, schema_version_id, status, decision, request_body,"
            + " response_body, metadata, ip_address, user_agent, epoch_time, occurred_at)"
            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb,"
            + " ?::jsonb, ?::jsonb, ?, ?, ?, ?)",
        auditId(payload),
        payload.requiredString("origin"),
        serviceName,
        actorUserId,
        firstString(payload, "actorRole", "actor_role", "userRole", "role"),
        action,
        firstString(payload, "httpMethod", "http_method", "method"),
        firstString(payload, "endpoint", "api", "path"),
        entityType,
        entityId,
        firstString(payload, "organizationId", "organization_id", "orgId"),
        firstString(payload, "applicationId", "application_id", "appId"),
        firstString(payload, "schemaId", "schema_id"),
        firstString(payload, "schemaVersionId", "schema_version_id", "versionId"),
        firstString(payload, "status", "result"),
        firstString(payload, "decision"),
        jsonFromAny(payload, "requestBody", "request_body", "body", "request", "request_json"),
        jsonFromAny(payload, "responseBody", "response_body", "response"),
        metadata(payload),
        firstString(payload, "ipAddress", "ip_address", "clientIp"),
        firstString(payload, "userAgent", "user_agent"),
        epoch(payload),
        occurredAt(payload));
  }

  public List<Map<String, Object>> findRecent(String tableName, int limit) {
    String table = table(tableName);
    return jdbcTemplate.queryForList(
        "select * from " + table + " order by occurred_at desc limit ?", limit);
  }
  public List<Map<String,Object>> findForApplication(String organization,String application,int limit) {
    return jdbcTemplate.queryForList("select id,origin,service_name,actor_user_id,action,entity_id,status,metadata,occurred_at from "
        +table(properties.getTables().getRecords())+" where organization_id=? and application_id=? order by occurred_at desc limit ?",
        organization,application,Math.max(1,Math.min(limit,100)));
  }

  public String table(String tableName) {
    if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
      throw new IllegalArgumentException("Invalid audit table name: " + tableName);
    }
    return tableName;
  }

  private String requiredFirstString(AuditPayload payload, String... keys) {
    String value = firstString(payload, keys);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Missing required audit field. Expected one of: " + String.join(", ", keys));
    }
    return value;
  }

  private String auditId(AuditPayload payload) {
    String value = firstString(payload, "primaryKey", "auditId", "idempotencyKey");
    return value == null ? UUID.randomUUID().toString().replace("-", "") : value;
  }

  private String firstString(AuditPayload payload, String... keys) {
    for (String key : keys) {
      String value = payload.string(key);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String jsonFromAny(AuditPayload payload, String... keys) {
    for (String key : keys) {
      if (payload.values().containsKey(key)) {
        return payload.json(key);
      }
    }
    return "{}";
  }

  private String metadata(AuditPayload payload) {
    if (payload.values().containsKey("metadata")) {
      return payload.json("metadata");
    }
    if (payload.values().containsKey("info")) {
      return payload.json("info");
    }
    Map<String, Object> metadata = new LinkedHashMap<>(payload.values());
    metadata.remove("requestBody");
    metadata.remove("request_body");
    metadata.remove("body");
    metadata.remove("request");
    metadata.remove("request_json");
    metadata.remove("responseBody");
    metadata.remove("response_body");
    metadata.remove("response");
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not serialize audit metadata", e);
    }
  }

  private long epoch(AuditPayload payload) {
    long epoch = payload.longValue("epochTime");
    if (epoch > 0) {
      return epoch;
    }
    return occurredAt(payload).toEpochSecond(ZoneOffset.UTC);
  }

  private LocalDateTime occurredAt(AuditPayload payload) {
    String isoTime = firstString(payload, "isoTime", "occurredAt", "createdAt", "timestamp");
    if (isoTime != null) {
      return OffsetDateTime.parse(isoTime).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
    long epoch = payload.longValue("epochTime");
    if (epoch > 0) {
      return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
    }
    return LocalDateTime.now(ZoneOffset.UTC);
  }
}
