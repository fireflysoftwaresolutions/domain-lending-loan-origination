<a href="https://fireflysoftware.io/openplatform/">
<picture>
  <source media="(max-width: 640px) and (prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-dark.svg">
  <source media="(max-width: 640px)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-light.svg">
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-dark.svg">
  <img src="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-light.svg" alt="Firefly OpenPlatform - Domain Lending - Loan Origination" width="100%">
</picture>
</a>

# Firefly OpenPlatform - Domain Lending - Loan Origination

Developed by the Firefly Software Foundation. [dev@fireflysoftware.io](mailto:dev@fireflysoftware.io)

[Website](https://fireflysoftware.io/) · [OpenPlatform](https://fireflysoftware.io/openplatform/) · [Module catalogue](https://fireflysoftware.io/openplatform/modules/)

Domain layer microservice for loan origination within the Firefly lending platform. It orchestrates business operations through generated Core SDKs; Core services own persistence. Available endpoints do not establish complete lifecycle or compensation coverage. The operation guides below describe implemented behavior and remaining integration requirements.

## Governed draft intake

See [draft intake runtime and recovery](docs/draft-intake-runtime.md) for the implemented authenticated operation, configuration, generated SDK boundary, and remaining lifecycle requirements.

## Managed application evidence

See [managed evidence upload and recovery](docs/managed-application-evidence.md) for immutable content custody, verified Loan association, authenticated SDK boundaries, and recovery of the original operation. A successful evidence receipt means stored content and an observed association; document verification remains pending.

## Overview

The Lending Domain Loan Origination service acts as the domain orchestration layer that sits between API consumers and the core lending origination persistence layer. It receives high-level business commands (e.g., "submit application", "score application", "approve application") and decomposes them into coordinated multi-step saga workflows executed via the FireflyFramework transactional saga engine.

### Key Features

- **Full application lifecycle management** -- submit, score, approve, reject, and withdraw loan applications through a single unified API
- **Saga-based orchestration** -- every multi-step operation runs as a compensating saga with automatic rollback on failure
- **CQRS architecture** -- strict separation of command and query responsibilities with dedicated buses, configurable timeouts, and query-level caching
- **Event-driven integration** -- each saga step emits domain events to Kafka (`domain-layer` topic) via the FireflyFramework EDA module
- **Reactive non-blocking I/O** -- built on Spring WebFlux with Project Reactor for end-to-end asynchronous processing
- **Virtual threads** -- Java virtual threads are enabled for improved concurrency under load
- **OpenAPI documentation** -- auto-generated Swagger UI for interactive API exploration (non-production profiles)

## Architecture

### Module Structure

| Module | Purpose |
|--------|---------|
| `domain-lending-loan-origination-web` | Spring Boot application entry point, REST controller, actuator, and OpenAPI configuration |
| `domain-lending-loan-origination-core` | Business logic: CQRS commands/queries, command and query handlers, saga workflow definitions, and the `LoanOriginationService` |
| `domain-lending-loan-origination-interfaces` | Interface adapters bridging core logic and the web layer |
| `domain-lending-loan-origination-infra` | Infrastructure concerns: `ClientFactory` for downstream API clients, `LoanOriginationProperties` configuration binding |
| `domain-lending-loan-origination-sdk` | OpenAPI-generated client SDK (WebClient-based, reactive) for consumers of this service |

### Technology Stack

- **Language:** Java 25
- **Framework:** Spring Boot with Spring WebFlux (reactive)
- **Parent POM:** [FireflyFramework Parent](https://github.com/fireflyframework/) (`fireflyframework-parent 1.0.0-SNAPSHOT`)
- **BOM:** FireflyFramework BOM `26.01.01`
- **Key FireflyFramework modules:**
  - `fireflyframework-web` -- common web layer configuration
  - `fireflyframework-domain` -- domain layer abstractions
  - `fireflyframework-utils` -- shared utilities
  - `fireflyframework-validators` -- validation support
- **CQRS:** FireflyFramework CQRS (`CommandBus`, `QueryBus`, `@CommandHandlerComponent`, `@QueryHandlerComponent`)
- **Saga Engine:** FireflyFramework Transactional Saga Engine (`@Saga`, `@SagaStep`, `@StepEvent`, `SagaEngine`, `SagaContext`)
- **Event-Driven Architecture:** FireflyFramework EDA with Kafka publisher
- **Mapping:** MapStruct with Lombok binding
- **API Documentation:** SpringDoc OpenAPI (WebFlux starter)
- **Metrics:** Micrometer with Prometheus registry
- **SDK Generation:** OpenAPI Generator Maven Plugin (Java WebClient library)

### Saga Workflows

| Saga | Description | Steps |
|------|-------------|-------|
| `RegisterApplicationSaga` | Orchestrates full application submission | Register loan application, then in parallel: parties, documents, collaterals, offers, status histories, score, and decision (all with compensation) |
| `RegisterApplicationDocumentSaga` | Attaches a single document to an existing application | Register application document |
| `RegisterScoreSaga` | Records an underwriting score against an application | Register underwriting score |
| `UpdateApplicationStatusSaga` | Transitions application status (approve/reject/withdraw) | Retrieve target status, retrieve loan application, retrieve old status, update application status, record status history |

### Downstream API Clients

The `ClientFactory` provisions the following API clients that call the core lending origination persistence layer:

- `LoanApplicationsApi`
- `ApplicationPartyApi`
- `ApplicationDocumentApi`
- `ApplicationCollateralApi`
- `ProposedOfferApi`
- `LoanApplicationStatusHistoryApi`
- `UnderwritingScoreApi`
- `UnderwritingDecisionApi`
- `ApplicationStatusApi`

## Setup

### Prerequisites

- **Java 25** (or later)
- **Apache Maven 3.9+**
- **Apache Kafka** running on `localhost:9092` (for event publishing)
- **Core Lending Origination service** running on `http://localhost:8082` (downstream persistence layer)

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_ADDRESS` | `localhost` | Bind address for the HTTP server |
| `SERVER_PORT` | `8080` | HTTP server port |

### Configuration Highlights (application.yaml)

```yaml
firefly:
  cqrs:
    enabled: true
    command:
      timeout: 30s
      metrics-enabled: true
      tracing-enabled: true
    query:
      timeout: 15s
      caching-enabled: true
      cache-ttl: 15m

  eda:
    enabled: true
    default-publisher-type: KAFKA
    publishers:
      kafka:
        default:
          default-topic: domain-layer
          bootstrap-servers: localhost:9092

api-configuration:
  core-lending.loan-origination:
    base-path: http://localhost:8082
```

### Spring Profiles

| Profile | Behavior |
|---------|----------|
| `dev` | DEBUG logging for `io.fireflysoftware`, R2DBC, and Flyway; Swagger UI enabled |
| `testing` | DEBUG logging for `io.fireflysoftware`; Swagger UI enabled |
| `prod` | WARN-level logging; Swagger UI and API docs disabled |

### Build

```bash
mvn clean install
```

### Run

```bash
mvn -pl domain-lending-loan-origination-web spring-boot:run
```

Or with a specific profile:

```bash
mvn -pl domain-lending-loan-origination-web spring-boot:run -Dspring-boot.run.profiles=dev
```

## API Endpoints

All endpoints are served under the base path `/api/v1/loan-applications`.

| Method | Path | Summary | Description |
|--------|------|---------|-------------|
| `POST` | `/api/v1/loan-applications` | Submit application | Submit a new loan application with product, amount, currency, channel, parties, documents, collaterals, offers, status histories, score, and decision |
| `POST` | `/api/v1/loan-applications/{appId}/documents` | Attach documents | Attach supporting documents (income statements, bank statements, collateral docs) to an existing application |
| `POST` | `/api/v1/loan-applications/{appId}/score` | Score application | Persist an underwriting model score with model name and version metadata |
| `POST` | `/api/v1/loan-applications/{appId}/approve` | Approve application | Approve the application (transitions status to `APPROVED` with history) |
| `POST` | `/api/v1/loan-applications/{appId}/reject` | Reject application | Reject the application for risk, eligibility, or documentation issues (transitions status to `REJECTED` with history) |
| `POST` | `/api/v1/loan-applications/{appId}/withdraw` | Withdraw application | Withdraw the application by applicant request (transitions status to `CANCELLED` with history) |
| `GET` | `/api/v1/loan-applications/{appId}` | Get application | Retrieve application state, details, and audit log |

### Interactive API Documentation

When running in non-production profiles, Swagger UI is available at:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## Development Guidelines

### Project Conventions

- **CQRS pattern:** All write operations are modeled as `Command` objects dispatched through the `CommandBus`; read operations use `Query` objects dispatched through the `QueryBus`
- **Saga orchestration:** Multi-step operations must be defined as `@Saga` classes with `@SagaStep` methods and corresponding compensate methods
- **Step events:** Every saga step should emit a `@StepEvent` for downstream event consumers
- **Constants:** Saga names, step IDs, compensate method names, and event types are centralized in `RegisterApplicationConstants` and `GlobalConstants`
- **DTOs as command bases:** Command classes extend SDK-generated DTOs (e.g., `RegisterLoanApplicationCommand extends LoanApplicationDTO`) to avoid redundant field definitions
- **Handlers:** Command handlers are annotated with `@CommandHandlerComponent` and extend `CommandHandler<C, R>`; query handlers use `@QueryHandlerComponent` and extend `QueryHandler<Q, R>`

### Adding a New Operation

1. Define a `Command` or `Query` class in the `commands`/`queries` package
2. Create a `CommandHandler` or `QueryHandler` in the `handlers` package
3. If the operation is multi-step, define a new `@Saga` class in the `workflows` package with steps, compensations, and events
4. Add constants for the saga, steps, compensations, and events to the appropriate constants class
5. Expose the operation through `LoanOriginationService` and the controller

## Monitoring

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check with liveness and readiness probes (detailed view) |
| `/actuator/info` | Application information |
| `/actuator/prometheus` | Prometheus-compatible metrics (CQRS command/query metrics included) |

### Health Probes

Kubernetes-compatible liveness and readiness probes are enabled:

- **Liveness:** `/actuator/health/liveness`
- **Readiness:** `/actuator/health/readiness`

### Logging

Structured console logging with pattern: `%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n`

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Links

- **Repository:** [https://github.com/firefly-oss/domain-lending-loan-origination](https://github.com/firefly-oss/domain-lending-loan-origination)
- **FireflyFramework:** [https://github.com/fireflyframework/](https://github.com/fireflyframework/)
- **Team Contact:** [dev@fireflysoftware.io](mailto:dev@fireflysoftware.io)
