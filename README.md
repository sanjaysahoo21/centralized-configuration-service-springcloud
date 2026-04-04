# Centralized Configuration Service

This project demonstrates a centralized, Git-backed configuration setup using Spring Cloud Config with dynamic refresh support.

## Project Structure

- `config-server/`: Spring Cloud Config Server
- `inventory-service/`: Config Client microservice
- `config-repo/`: Local Git repository containing environment configs
- `docker-compose.yml`: Full stack orchestration
- `.env.example`: Documented environment variables

## Prerequisites

- Docker and Docker Compose
- Java 17 (for local Maven packaging if needed)

## Environment Variables

Copy from `.env.example` as needed:

- `SPRING_PROFILES_ACTIVE=dev`

## Run The Full System

```bash
# from project root
DOCKER_BUILDKIT=0 docker compose up --build -d
```

## Verify Core Endpoints

### 1) Config Server endpoint

```bash
curl -s http://localhost:8888/inventory-service/dev | jq .
```

Expected: response includes `propertySources` with:
- `inventory.maxStock: 100` (initially)
- `inventory.replenishThreshold: 10`
- `server.port: 8081`

### 2) Inventory config endpoint

```bash
curl -s http://localhost:8081/api/inventory/config | jq .
```

Expected:

```json
{
  "profile": "dev",
  "maxStock": 100,
  "replenishThreshold": 10
}
```

### 3) Custom health endpoint

```bash
curl -s http://localhost:8081/api/inventory/health | jq .
```

Expected:

```json
{
  "status": "UP",
  "configServer": "connected"
}
```

## Dynamic Refresh Demo (No Restart)

1. Check current value:

```bash
curl -s http://localhost:8081/api/inventory/config | jq .
```

2. Update Git-backed config and commit:

```bash
cd config-repo
sed -i 's/maxStock: 100/maxStock: 250/' inventory-service-dev.yml
git add inventory-service-dev.yml
git commit -m "Testing refresh"
```

3. Trigger refresh on running inventory service:

```bash
curl -s -X POST http://localhost:8081/actuator/refresh | jq .
```

Expected refreshed keys include `inventory.maxStock`.

4. Verify updated value without restart:

```bash
curl -s http://localhost:8081/api/inventory/config | jq .
```

Expected `maxStock: 250`.

## Health and Mount Checks

```bash
docker compose ps
docker compose exec -T config-server ls -la /etc/config-repo
```

Expected:
- both containers healthy
- config files visible in mounted `/etc/config-repo`

## Stop

```bash
docker compose down
```
