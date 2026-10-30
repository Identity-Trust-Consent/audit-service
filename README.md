# Audit Service

Spring Boot audit service for Identity OS onboarding/identity and CMS audit records.

## Start RabbitMQ

From the workspace root:

```powershell
docker compose -f docker-compose.rabbitmq.yml up -d
```

RabbitMQ endpoints:

```text
AMQP: http://localhost:5672
Management UI: http://localhost:15672
Username: guest
Password: guest
```

## Start Audit Service

From `audit-service`:

```powershell
mvn spring-boot:run
```

The service uses these defaults:

```text
PostgreSQL: jdbc:postgresql://10.212.13.208:5432/auditdb
RabbitMQ: localhost:5672
HTTP: http://localhost:8086
```

## Health Check

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8086/actuator/health
```

## Send A Test Audit Event

```powershell
$body = @{
  origin = "onboarding-and-identity-service"
  action = "ORGANIZATION_REGISTERED"
  actorUserId = "system"
  actorRole = "SERVICE"
  httpMethod = "POST"
  endpoint = "/api/v1/onboarding/organizations"
  entityType = "ORGANIZATION"
  entityId = "org_demo"
  organizationId = "org_demo"
  status = "SUCCESS"
  body = @{ organizationName = "Demo Org" }
} | ConvertTo-Json -Depth 5

Invoke-WebRequest -UseBasicParsing `
  -Uri http://localhost:8086/audits `
  -Method POST `
  -ContentType "application/json" `
  -Body $body
```
