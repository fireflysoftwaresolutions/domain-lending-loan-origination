# Managed application evidence

This operation stores the supplied content in the Document Core and attaches the verified reference to the current customer's managed loan application in the Loan Core. The Domain owns orchestration only. It has no business database or local durable action store.

The Loan owner checks the current application, primary borrower, document type, and allowed application state when committing the association. The Document owner retains the immutable bytes and their original action. A returned `ATTACHED` receipt has `PENDING` verification; it is not a verified document, credit decision, signed agreement, or disbursement.

## Upload

Send `POST /api/v1/managed-loan-evidence/applications/{applicationId}/actions/{actionId}` with `actionId`, `fileName`, `documentTypeCode`, `mediaType`, and base64-encoded `content`. The body action must equal the route action. Keep that caller-generated UUID for the entire operation, including ambiguous responses and recovery.

Content must contain 1 through 2,097,152 decoded bytes. The JSON envelope is bounded to 3,145,728 bytes. The filename is at most 200 characters and cannot contain path separators, control characters, or malformed Unicode. Document type codes use uppercase catalogue codes. No party or resource tenant is accepted from the body: those identities come from signed customer authority and the configured owner namespace.

The Domain first observes the current Loan association, stores content when needed, then independently reads and hashes the retained Document bytes before requesting the Loan association. It observes the resulting association before returning success. The receipt includes distinct `documentId` and `associationId`, content length and SHA-256, both owner intent digests, absolute `storedAt` and `attachedAt` timestamps, and the original action. HTTP 201 indicates a newly observed association; HTTP 200 indicates an exact replay.

## Recover the original operation

Send `POST /api/v1/managed-loan-evidence/applications/{applicationId}/actions/{actionId}/recovery` without a new upload body. Recovery requires fresh authority for the same original application and action. It reads the retained Document content and either returns the existing matching Loan association or completes the missing association.

Recovery never creates another Document upload, chooses another action, or deletes retained custody. Changed immutable upload terms on an existing action conflict. The Domain does not pretend that independent Core transactions roll back together. Durable cancellation remains a separate operation that is not implemented by this recovery endpoint.

If completion cannot be observed, the typed response preserves the original action and distinguishes unavailable observation, an unknown Document outcome, stored Document content with unavailable or uncertain association, and a committed association whose response is unavailable. Known owner IDs and digests are returned only while current authority can still be established. A timeout is not evidence that no write occurred. Do not automatically retry with a new action.

## Authentication and deployment

Enable the operation explicitly with `lending.evidence.enabled=true`. Configure the following properties under `lending.evidence`:

| Properties | Purpose |
| --- | --- |
| `issuer`, `incoming-audience`, `incoming-client-id`, `platform-tenant` | Exact accepted customer identity boundary |
| `loan-resource-tenant-id`, `document-resource-tenant-id`, `namespace-not-after` | Explicit owner namespaces and their finite admission window |
| `loan-audience`, `loan-client-id`, `loan-endpoint`, `loan-client-secret` | Loan Core token-exchange target |
| `document-audience`, `document-client-id`, `document-endpoint`, `document-client-secret` | Document Core token-exchange target |
| `token-endpoint`, `target-jwk-set-uri` | Trusted OAuth token exchange and target token verification |
| `owned-loopback-sandbox` | Explicit permission for owned loopback HTTP endpoints in a test deployment; otherwise use HTTPS |

Supply secrets through deployment configuration or a secret manager. Keep global native JWT validation and the configured `PolicyEvaluationPort` active. The incoming signed HUMAN principal requires `lending:applications:evidence:upload`; current policy is checked before and after owner interactions. Exchanged tokens preserve the original subject, party, tenant, and expiry while narrowing to the owner's operation scope. Recovery can attach the missing association and therefore requires upload authority too.

All steps share the original five-second wall-clock and monotonic execution bound. Domain and Experience technical caches must remain separate from Core business persistence. Configure the actual owner adapters and restricted cache credentials; disabling a boundary or substituting an in-memory success does not complete deployment.

## SDK and validation scope

The controller's complete OpenAPI document feeds the repository's original generator. Consumers use `ManagedLoanEvidenceControllerApi`; receipt timestamps are `java.time.Instant` and upload content is `byte[]`. Never maintain a parallel handwritten SDK or edit generated Java.

The hosted source qualification passed the maintained suite, managed orchestration tests, complete controller producer, and original SDK generation. Controlled tests cover partial outcomes, immutable replay conflicts, current authority, and deadlines. They do not prove the full Experience → Domain → both Core deployment. That four-process integration, actual token-exchange deployment, restart recovery, and durable cancellation remain separate validation requirements.
