# Business Data Protection — Documentation Index

**Version:** 1.3 (governance 1.0 + implementation design + Phase 6–7B)  
**Status:** Phase 7B orchestration & audit implemented; flag default **false**  
**Phase:** RAG Business→PII→LLM→Restore; Live unchanged

---

## Compatibility Principles

This documentation does not replace or modify the existing M3 architecture.

The following remain the source of truth:

- IntentApiCatalog
- SearchFieldCatalog
- ApiFieldCatalog
- InformationRequestCatalog
- RequestedInformationResolver
- FieldDefinitionRegistry
- Comprehend / Presidio
- Lex slot collection
- Live M3 execution

This documentation introduces only an information-classification layer that future implementations may consult when preparing LLM-bound content.

No runtime behaviour changes in this phase.

---

## Guiding principles

```text
Information meaning (global)
  → Category
  → LLM Exposure Policy
  → Future implementation

Classification is immutable.
Implementation is replaceable.
```

**Business functionality and information protection are independent concerns.**  
A field may participate fully in business processing (search criteria, Lex slots, MI execution) while simultaneously being protected when content is prepared for an LLM.

Example: `CUNO` can remain a search criterion on Customer Order SearchHead and still be classified BDI → REPLACE for LLM-bound content.

---

## Document set

| # | Document | Purpose |
|---|----------|---------|
| 1 | [01-global-information-classification.md](01-global-information-classification.md) | Per-code catalog (category, LLM Exposure Policy, Placeholder Type, Protection Reason, Confidence, Authority) |
| 2 | [02-business-data-protection-policy.md](02-business-data-protection-policy.md) | Policy, category definitions, Architectural Invariants |
| 3 | [03-business-information-detection-spec.md](03-business-information-detection-spec.md) | How detection would resolve codes using existing catalogs |
| 4 | [04-decision-log.md](04-decision-log.md) | Append-only rationale for classifications |
| 5 | [05-implementation-design.md](05-implementation-design.md) | Phase 5 LLD: OpenAIService egress, components, sequences, contracts, Phase 6 task order |
| 6 | [06-phase6-dev-enablement.md](06-phase6-dev-enablement.md) | Phase 6.5 DEV enablement checklist + Live/RAG/rewrite validation |
| 7 | [07-phase7a-notes.md](07-phase7a-notes.md) | Phase 7A: temporary egress order, Context vs Session, deferred 7B |
| 8 | [08-phase7b-notes.md](08-phase7b-notes.md) | Phase 7B: orchestration invariants, restore, API/DB audit, logging |
| 9 | [09-detector-coverage.md](09-detector-coverage.md) | Phase 2A: detector NL coverage, grammar freeze, miss/confidence bands |

---

## Concern separation

| Concern | Source of truth |
|---------|-----------------|
| Business execution (intent, slots, returncols, M3) | Existing Java catalogs + Lex |
| Information governance | Documents 01–04 |
| LLM preparation / protection design | Document 05 → Phase 6 Java |
| DEV enablement | Document 06 |

```text
Business execution  →  existing M3 architecture (unchanged)
Information governance  →  documents 01–04
Implementation design  →  document 05
LLM protection code  →  Phase 6 (`service.protection`, flag default false)
DEV checklist         →  document 06
```

No runtime behaviour change while `business-information.protection.enabled=false`.

---

## Coverage checklist (V1.0)

Confirm each surface is represented in Document 1:

| Surface | Search criteria / key inputs | Available information / MI payload | Status |
|---------|------------------------------|--------------------------------------|--------|
| Customer Order (OIS SearchHead) | ORNO, CUNO, FACI, ORTP, ORDT, RESP, SMCD, ORST, RLDZ, ORSL | + OBLC, NTAM, CUCD, TIZO, FRE1, TEPY, MODL, TEDL, ADID, PYNO | Covered |
| Purchase Order (PPS200 SearchHead) | PUNO, DIVI, WHLO, SUNO, PUSL, PUST, PUDT, BUYE, FACI, ORTY, POTC, PURC | + PGNM, FILE, KSTR | Covered |
| Manufacturing Order (PMS100 SearchMO) | MFNO, PRNO, FACI, WHST, STDT, FIDT, RORN, PRIO | + ITDS, RORC, RORL, RORX, ORQT, MAQT, MSTI, MFTI, PGNM, FILE, KSTR | Covered |
| Distribution Order (MMS100 SearchHead) | TRNR, FACI, WHLO, TRTP, RESP, TRSH, TRSL, RIDT | + PGNM, FILE, KSTR | Covered |
| Customer Basic (CRS610 GetBasicData) | CUNO (+ full GetBasicData field list) | See Document 1 CB rows | Covered |
| Customer Financial (CRS610 GetFinancial) | CUNO (+ full GetFinancial field list) | See Document 1 CF rows | Covered |

---

## Next steps

1. Follow [06-phase6-dev-enablement.md](06-phase6-dev-enablement.md) for local DEV validation.  
2. Keep `business-information.protection.enabled=false` in main config until QA sign-off.  
3. Architecture changes only via Decision Log. See [08-phase7b-notes.md](08-phase7b-notes.md) for RAG orchestration.

---

## Version history

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-31 | Initial frozen governance baseline (docs 01–04) |
| 1.1 | 2026-07-31 | Added Document 05 — Implementation Design (Phase 5) |
| 1.2 | 2026-07-31 | Phase 6 Java + Document 06 DEV enablement |
| 1.3 | 2026-08-03 | Phase 7B orchestration & audit (Document 08; Decisions #24–#28) |
| 1.4 | 2026-08-03 | Phase 2A detector NL coverage (Document 09) |
