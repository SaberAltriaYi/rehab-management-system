# Rehab SaaS Platform V2 Beta

V2 is the default SaaS product path. V1 Enterprise remains frozen at v1-enterprise-beta
(dcfe0e06041fc1328e54ef4b35026cae81dfdf5f) for private deployment and offline licensing.

## Platform boundaries

- One user belongs to one tenant.
- Tenant lifecycle is pending, active, suspended, expired, deleted.
- Platform administrators manage tenants, plans and usage.
- Tenant administrators manage therapists and synthetic patients.
- Patient portal exposes only reports, plans and home exercises permitted by the tenant.
- Payment processing is intentionally out of scope for this beta; subscription state is operator-managed.
- Existing Ed25519 licensing remains enabled only for Enterprise deployment profiles.

## Migration and deployment

Apply the migration ledger through deploy/internal/migrations.manifest; V2 adds migration 027
sql/mysql/rehab-saas-platform-v2.sql. Use the existing isolated Docker Compose deployment and
the synthetic fixture profile. Never apply the synthetic fixture to a production database.

## Synthetic acceptance identities

synthetic-tenant, synthetic-admin, synthetic-therapist, synthetic-patient, and
SYNTHETIC-MOTION-001 are test-only identifiers. No real patient or credential is packaged.

## Current release gates

Backend and frontend production builds, migration execution, desktop packaging, and the complete
registration-to-patient-portal flow must be recorded as evidence before this beta is called
verified. Domain, certificate, MFA and payment integration remain commercial launch blockers.
