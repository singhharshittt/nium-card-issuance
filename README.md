# Virtual Card Issuance Platform

A Java 21 + Spring Boot backend service for managing virtual card issuance, top-ups, spends, and transaction tracking with comprehensive support for idempotency, optimistic locking, and concurrent operations.

## Overview

This is a production-grade modular monolith implementation of a Virtual Card Issuance Platform that demonstrates:

- **Clean Architecture**: Layered package structure (controller → service → repository → model)
- **Financial Correctness**: Idempotent operations, optimistic locking, balance constraints
- **Concurrency Safety**: Optimistic locking with retry logic under concurrent load
- **Observability**: Structured logging, correlation IDs, Micrometer metrics, Actuator health checks
- **Comprehensive Testing**: Unit tests, integration tests, and concurrent operation tests
- **RESTful API**: Well-documented OpenAPI/Swagger endpoints
- **Database Persistence**: jOOQ for explicit SQL control with H2

## Features

### Core Functionality

- ✅ Create virtual cards with initial balance
- ✅ Retrieve card details and balance
- ✅ Top-up cards with funds
- ✅ Spend from cards with balance validation
- ✅ Retrieve complete transaction history
- ✅ Idempotent financial operations (retries are safe)
- ✅ Concurrent operation safety (optimistic locking)
- ✅ Transaction ledger with CARD_ISSUANCE, TOP_UP, SPEND types
- ✅ Transaction status tracking (SUCCESSFUL, DECLINED, PENDING)

### Technical Features

- ✅ Optimistic locking for concurrent balance updates
- ✅ Idempotency via idempotency keys on financial operations
- ✅ Request correlation IDs for distributed tracing
- ✅ Structured logging with MDC
- ✅ Comprehensive metrics (Micrometer/Prometheus)
- ✅ Health checks and probes (liveness/readiness)
- ✅ Global exception handling with structured error responses
- ✅ Flyway database migrations
- ✅ Comprehensive API documentation (Swagger/OpenAPI)
- ✅ Rate limiting with Bucket4j for DoS protection

## Quick Start

### Prerequisites

- Java 21 or later
- Gradle 8.8 or later (or use the included Gradle wrapper)

### Build

```bash
# Using Gradle wrapper (Windows)
./gradlew.bat clean build

# Using Gradle wrapper (Linux/Mac)
./gradlew clean build

# Using installed Gradle
gradle clean build
```

### Run

```bash
# Using Gradle wrapper (Windows)
./gradlew.bat bootRun

# Using Gradle wrapper (Linux/Mac)
./gradlew bootRun

# Using installed Gradle
gradle bootRun

# Application will start on http://localhost:8080/api
```

### Access Swagger UI

```
http://localhost:8080/api/swagger-ui.html
```

## API Endpoints

### Create Card
```bash
POST /api/cards
Content-Type: application/json

{
  "cardholderName": "John Doe",
  "initialBalance": 1000.00
}
```

### Get Card Details
```bash
GET /api/cards/{cardId}
```

### Top-up Card (Idempotent)
```bash
POST /api/cards/{cardId}/top-ups
Content-Type: application/json
Idempotency-Key: unique-key-123

{
  "amount": 500.00
}
```

### Spend from Card (Idempotent)
```bash
POST /api/cards/{cardId}/spends
Content-Type: application/json
Idempotency-Key: unique-key-456

{
  "amount": 300.00
}
```

### Get Transaction History
```bash
GET /api/cards/{cardId}/transactions
```

## Architecture

### Package Structure

```
com.nium.virtualcard/
├── controller/       # REST endpoints
├── service/          # Business logic
├── repository/       # Data access (jOOQ)
├── model/           # Domain entities and enums
├── dto/             # Data transfer objects
├── exception/       # Custom exceptions
└── config/          # Configuration and filters
```

### Key Design Decisions

1. **Idempotency**: Financial operations require `Idempotency-Key` header for at-most-once semantics
2. **Optimistic Locking**: Version-based concurrency control for balance updates
3. **jOOQ**: Explicit SQL control for financial operations
4. **Modular Monolith**: Single application with clear service boundaries, scalable and extensible

## Observability

### Logging with Correlation IDs

All logs include a correlation ID for request tracing:

```
2024-04-18 12:34:56.789 [main] [550e8400-e29b-41d4-a716-446655440000] DEBUG CardService - ...
```

### Metrics

```bash
# View metrics
http://localhost:8080/api/actuator/metrics
```

Tracked metrics:
- `card.created.count` - Total cards created
- `card.topup.success.count` - Successful top-ups
- `card.spend.success.count` - Successful spends
- `card.spend.declined.count` - Declined spends (insufficient funds)
- `idempotency.hit.count` - Idempotent retries
- `optimistic.lock.retry.count` - Retry attempts
- `optimistic.lock.failure.count` - Exhausted retries

### Health Check

```bash
http://localhost:8080/api/actuator/health
```

## Scalability

### Horizontal Scaling

Deploy multiple instances behind a load balancer with a shared Postgres database. Optimistic locking ensures correctness under concurrent load.

### Database Evolution

- **Development**: H2 (in-memory, fast)
- **Production**: Postgres (with read replicas, sharding for scale)

### Future Evolution

- Event-driven architecture with Kafka
- Microservices extraction by business capability
- Transaction pagination and archival
- Advanced fraud detection

## Technology Stack

- **Language**: Java 21 LTS
- **Framework**: Spring Boot 3.3.0
- **Database**: H2 (dev) / Postgres (prod)
- **Query**: jOOQ 3.19.8
- **Migrations**: Flyway
- **Testing**: JUnit 5, Mockito, AssertJ
- **API Docs**: Springdoc-OpenAPI 2.3.0
- **Metrics**: Micrometer

## Trade-offs

- **Modular Monolith** over microservices: Simplicity and ACID transactions
- **Optimistic Locking** over pessimistic: Reduced contention
- **jOOQ** over JPA: Explicit control for financial operations
- **H2 default**: Easy setup, switch to Postgres for production

## New Learnings

- Using jOOQ for complex financial operations with explicit SQL control
- Exposing custom metrics with Micrometer for financial operations
- Rate limiting with Bucket4j for DoS protection
