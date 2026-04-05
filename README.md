# Centralized Configuration Service

A production-ready demonstration of **centralized, Git-backed configuration management** using **Spring Cloud Config**. This project implements the [Twelve-Factor App](https://12factor.net/) configuration principle across a microservices architecture, with support for multiple environments and zero-downtime dynamic refresh.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Environment Variables](#environment-variables)
- [Running the System](#running-the-system)
- [Verifying the Endpoints](#verifying-the-endpoints)
- [Dynamic Configuration Refresh](#dynamic-configuration-refresh)
- [Switching Profiles (dev → prod)](#switching-profiles-dev--prod)
- [How It Works](#how-it-works)
- [Stopping the System](#stopping-the-system)

---

## Architecture Overview

```
┌──────────────────────────────────────────────┐
│               Docker Compose                 │
│                                              │
│  ┌─────────────────┐    ┌──────────────────┐ │
│  │  config-server  │◄───│   config-repo    │ │
│  │  (port 8888)    │    │  (volume mount)  │ │
│  │                 │    │  ├── dev.yml     │ │
│  │ @EnableConfig   │    │  └── prod.yml    │ │
│  │    Server       │    └──────────────────┘ │
│  └────────┬────────┘                         │
│           │  serves config on startup        │
│           ▼                                  │
│  ┌─────────────────┐                         │
│  │inventory-service│                         │
│  │  (port 8081)    │                         │
│  │ Config Client + │                         │
│  │ @RefreshScope   │                         │
│  └─────────────────┘                         │
└──────────────────────────────────────────────┘
```

**Config flow:**
1. `config-server` reads YAML files from the mounted `config-repo` (a local Git repository).
2. `inventory-service` fetches its configuration from `config-server` on startup via `bootstrap.yml`.
3. On `POST /actuator/refresh`, `inventory-service` re-fetches config and updates `@RefreshScope` beans — **no restart required**.

---

## Project Structure

```
Centralized-Configuration-Service/
├── config-server/                  # Spring Cloud Config Server (port 8888)
│   ├── src/main/java/...
│   │   └── CentralizedConfigurationServiceApplication.java
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── Dockerfile                  # Multi-stage build
│   └── pom.xml
│
├── inventory-service/              # Config Client microservice (port 8081)
│   ├── src/main/java/com/example/inventoryservice/
│   │   ├── InventoryServiceApplication.java
│   │   ├── InventoryConfig.java    # @ConfigurationProperties bean
│   │   ├── ConfigController.java   # @RefreshScope REST controller
│   │   └── HealthController.java   # Custom config-server health probe
│   ├── src/main/resources/
│   │   └── bootstrap.yml           # Config Server URI & actuator config
│   ├── Dockerfile                  # Multi-stage build
│   └── pom.xml
│
├── config-repo/                    # Git-backed configuration source
│   ├── inventory-service-dev.yml   # Dev profile: maxStock=100, port=8081
│   └── inventory-service-prod.yml  # Prod profile: maxStock=10000, port=8082
│
├── docker-compose.yml              # Full-stack orchestration
├── .env.example                    # Documented environment variables
└── README.md                       # This file
```

---

## Technology Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.13 |
| Config Server | Spring Cloud Config Server 2025.0.2 |
| Config Client | Spring Cloud Config Client + Bootstrap |
| Actuator | Spring Boot Actuator (health, refresh) |
| Containerization | Docker + Docker Compose |
| Base Image | `eclipse-temurin:17-jdk-jammy` (build) / `eclipse-temurin:17-jre-jammy` (runtime) |
| Config Backend | Local Git repository |

---

## Prerequisites

- **Docker** (v20+) and **Docker Compose** (v2+)
- No local Java or Maven installation required — the multi-stage Dockerfiles handle the build entirely inside Docker.

Verify:
```bash
docker --version
docker compose version
```

---

## Environment Variables

Copy `.env.example` to `.env` if you want to override defaults:

```bash
cp .env.example .env
```

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile for the inventory-service (`dev` or `prod`) |
| `SPRING_CLOUD_CONFIG_SERVER_GIT_URI` | `file:///etc/config-repo` | URI of the Git-backed config repo inside the container |
| `SPRING_CLOUD_CONFIG_SERVER_GIT_DEFAULT_LABEL` | `master` | Git branch the config server reads from |
| `SPRING_CLOUD_CONFIG_URI` | `http://config-server:8888` | Config server URI used by the inventory-service |

---

## Running the System

From the **project root**, run:

```bash
DOCKER_BUILDKIT=0 docker compose up --build -d
```

> **Note:** `DOCKER_BUILDKIT=0` is used to ensure compatibility with the classic Docker builder required by this compose configuration.

Both services will be **built from source inside Docker** — no pre-built JARs needed.

Wait for both containers to reach `healthy` status (up to ~3 minutes on first build):

```bash
docker compose ps
```

Expected output:
```
NAME                STATUS
config-server       Up (healthy)
inventory-service   Up (healthy)
```

You can also tail the logs:
```bash
docker compose logs -f
```

---

## Verifying the Endpoints

### 1. Config Server — Serve dev configuration

```bash
curl -s http://localhost:8888/inventory-service/dev | jq .
```

**Expected response (200 OK):**
```json
{
  "name": "inventory-service",
  "profiles": ["dev"],
  "propertySources": [
    {
      "name": "file:///etc/config-repo/inventory-service-dev.yml",
      "source": {
        "inventory.maxStock": 100,
        "inventory.replenishThreshold": 10,
        "server.port": 8081
      }
    }
  ]
}
```

### 2. Inventory Service — Active configuration values

```bash
curl -s http://localhost:8081/api/inventory/config | jq .
```

**Expected response (200 OK):**
```json
{
  "profile": "dev",
  "maxStock": 100,
  "replenishThreshold": 10
}
```

### 3. Inventory Service — Custom health indicator

```bash
curl -s http://localhost:8081/api/inventory/health | jq .
```

**Expected response (200 OK):**
```json
{
  "status": "UP",
  "configServer": "connected"
}
```

### 4. Verify config-repo files are mounted inside the container

```bash
docker compose exec -T config-server ls -la /etc/config-repo
```

---

## Dynamic Configuration Refresh

Demonstrates zero-downtime configuration updates — no service restart required.

**Step 1:** Check the current `maxStock` value:
```bash
curl -s http://localhost:8081/api/inventory/config | jq .maxStock
# Output: 100
```

**Step 2:** Update the Git-backed config file and commit:
```bash
cd config-repo
sed -i 's/maxStock: 100/maxStock: 250/' inventory-service-dev.yml
git add inventory-service-dev.yml
git commit -m "Testing refresh - bump maxStock to 250"
cd ..
```

**Step 3:** Trigger the refresh endpoint on the running service:
```bash
curl -s -X POST http://localhost:8081/actuator/refresh | jq .
```

**Expected response (200 OK):**
```json
["config.client.version", "inventory.maxStock", "spring.application.version"]
```

**Step 4:** Verify the value was updated without a restart:
```bash
curl -s http://localhost:8081/api/inventory/config | jq .maxStock
# Output: 250
```

> **How it works:** `@RefreshScope` annotates the `ConfigController` bean. On refresh, Spring Cloud re-fetches the config from the server and re-initialises all `@RefreshScope`-scoped beans with the new property values.

---

## Switching Profiles (dev → prod)

To run with the `prod` profile (port 8082, `maxStock: 10000`):

1. Stop the running containers:
   ```bash
   docker compose down
   ```

2. Edit `docker-compose.yml` — change under `inventory-service > environment`:
   ```yaml
   SPRING_PROFILES_ACTIVE: prod
   ```
   And update the port mapping:
   ```yaml
   ports:
     - "8082:8082"
   ```

3. Restart:
   ```bash
   DOCKER_BUILDKIT=0 docker compose up -d
   ```

4. Verify on port 8082:
   ```bash
   curl -s http://localhost:8082/api/inventory/config | jq .
   # Expected: maxStock: 10000, replenishThreshold: 100
   ```

---

## How It Works

### Config Server
- Annotated with `@EnableConfigServer` — enables Spring Cloud Config Server auto-configuration.
- Points to the `config-repo` directory (mounted as a Docker volume at `/etc/config-repo`) via `SPRING_CLOUD_CONFIG_SERVER_GIT_URI`.
- Serves configuration at `/{application}/{profile}` — e.g., `GET /inventory-service/dev`.

### Inventory Service
- Uses `bootstrap.yml` to connect to the Config Server **before** the main application context starts.
- `fail-fast: true` ensures the service won't start with missing configuration.
- `InventoryConfig` uses `@ConfigurationProperties(prefix = "inventory")` to bind `maxStock` and `replenishThreshold`.
- `ConfigController` is annotated with `@RefreshScope` so its beans are re-created on `/actuator/refresh`.
- `HealthController` actively probes the Config Server to report connectivity status.

### Docker Compose Orchestration
- `inventory-service` uses `depends_on: config-server: condition: service_healthy` — it won't start until the Config Server passes its health check.
- The `config-repo` directory is bind-mounted so config changes on the host are immediately visible inside the container.

---

## Stopping the System

```bash
docker compose down
```
