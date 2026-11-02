# Authenticated draft loan intake

Experience calls this Domain's `POST /api/v1/loan-application-intakes`; Domain exchanges the original caller token and uses the genuine generated Core SDK. Core owns the application, primary borrower relationship, immutable receipt, and paired audit/outbox. Domain owns orchestration and no business database.

A successful new action returns 201; an exact replay returns 200. The retained receipt represents DRAFT/PENDING_DOCUMENTS and preserves the original simulation terms. Reusing an action with a different intent conflicts. The endpoint disables generic response caching so each replay rechecks current authority and reaches the owning idempotency boundary.

If Core committed but its borrower response view was unavailable, retain the original action and exact request. Recovery repeats only that original action; it does not generate a replacement command or compensate a known committed application. Caller, party, client, scope, audience, and the bounded original authorization window remain checked across token exchange and receipt delivery.

## Runtime configuration

Enable `lending.intake.enabled` only with the explicit trust settings below. It does not disable or replace the global resource server when false. Use deployment secrets for client and cache credentials.

| Property | Meaning |
| --- | --- |
| `lending.intake.issuer` | Trusted issuer for both incoming and exchanged tokens. |
| `lending.intake.incoming-audience` | Exact Domain audience. |
| `lending.intake.incoming-client-id` | Authorized Experience client. |
| `lending.intake.platform-tenant` | Signed opaque platform namespace. |
| `lending.intake.core-audience` | Exact target Core audience. |
| `lending.intake.core-client-id` | Registered Domain token-exchange client. |
| `lending.intake.core-endpoint` | Trusted Core endpoint. |
| `lending.intake.token-endpoint` | OAuth token-exchange endpoint. |
| `lending.intake.client-secret` | Secret supplied by the deployment secret mechanism. |
| `lending.intake.target-jwk-set-uri` | Trusted exchanged-token verification keys. |
| `lending.intake.jwk-set-uri` | Incoming verification keys when no shared decoder is configured. |
| `lending.intake.role-claim-paths` | Explicit role projection when no shared principal factory is configured. |
| `lending.intake.scope-claim-paths` | Explicit scope projection when no shared principal factory is configured. |
| `lending.intake.authority-prefix` | Explicit authority prefix for that fallback projection. |

HTTPS is required for the exchange endpoints. `lending.intake.owned-loopback-sandbox` permits only the explicitly owned loopback test deployment; it is not a production trust fallback. Existing global receiver and strict policy configuration must be supplied independently.

The technical cache defaults to PostgreSQL and uses `CACHE_DB_HOST`, `CACHE_DB_PORT`, `CACHE_DB_NAME`, `CACHE_DB_USERNAME`, `CACHE_DB_PASSWORD`, `CACHE_DB_SSL_MODE`, and `CACHE_DB_SCHEMA`. It has no authority over application state. Its schema/table must be provisioned before startup; automatic creation is disabled. Event transport retains its separate configuration; the bounded intake proof does not qualify queue delivery.

## Generated contract and validation scope

The canonical SDK specification is the complete actual controller-produced OpenAPI, including the existing operations and this intake operation. Generate clients using the repository's original OpenAPI generator; do not maintain handwritten SDK methods or filtered specifications.

The current three-Main qualification exercises new creation, replay, conflict, negative authorization/input cases, concurrent duplicate requests, committed-response recovery, paired audit accessor refusal, and restart using the same PostgreSQL data. It checks the original signed human/party across Experience, Domain, and Core. Separate configuration cases verify that disabling intake preserves global authentication.

This validates bounded draft intake under controlled issuer/PDP authority. It does not establish institution-specific production authorization, document verification, credit approval, offers, signatures, disbursement, repayments, arrears, external provider acceptance, or the complete loan lifecycle.
