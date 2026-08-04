# Phase 2A — Detector NL Coverage

**Status:** Implemented (grammar freeze after this phase)  
**Pipeline:** Unchanged (7B orchestration intact)  
**Scope:** `DetectionGrammar`, `FieldClassificationCatalog`, `BusinessInformationDetector`, `ValueShapeValidator` tests

---

## Connector bridge rule

> A connector may only **bridge** the keyword and value. It must **never** change sentence semantics.

**Allowed (2A):** short bridges such as `is`, `with`, `named`, `called`, `ref`, `code`, `with number`, `having number`, `identified by`, `identified as`.

**Forbidden growth pattern:** `belongs to`, `associated with`, `related to`, `connected with`, `which is`, `that is`, `I am referring to`, …

**After 2A:** do **not** expand connectors without Phase **2C** production evidence.

Separators: `=`, `:`, `#` only. Arrow `->` deferred. Simple parens: `customer (45678)` only (not nested).

---

## Keywords vs aliases

| Kind | Role | Examples |
|------|------|----------|
| Detection Keywords | Natural language | `customer`, `purchase order`, `account number` |
| Detection Aliases | System abbreviations | `cust`, `cuno`, `po`, `po#` |

Never bare `account` or bare `material`. Prefer `account number` / `material number`.

---

## Diagnostic confidence bands

Deterministic only — **does not** change REPLACE/ALLOW/BLOCK:

| Band | When |
|------|------|
| EXACT | Keyword; no connector; no paren |
| ALIAS | Alias match |
| GRAMMAR | Connector bridge and/or simple paren |
| WEAK | Soft keywords (`… reference`, `… code`, `… ref`) |

---

## Miss classification

Logged as keyword + reason (avoid raw IDs at INFO):

| Reason | Example |
|--------|---------|
| `VALUE_MISSING` | `customer` alone |
| `SHAPE_INVALID` | `customer banana` |
| `CONNECTOR_INVALID` | `customer should be 45678`, `customer, 45678` |
| `VALUE_BEFORE_KEYWORD` | `45678 this customer` |
| `RESERVED_VALUE` | value token is reserved |

---

## M3 identifier value shapes

Business IDs (CUNO, SUNO, ORNO, PUNO, MFNO, TRNR, PRNO) use catalog shape **`M3_IDENTIFIER`**:

| Metadata | Source | Rule |
|----------|--------|------|
| `maxLength` | Catalog (10 or 15) | Required; upper bound only: `1 <= length <= maxLength` |
| `characterSet` | Catalog enum `ALPHANUMERIC` | Required; validator maps to `[A-Z0-9]` after uppercasing |
| Digit | Validator | At least one digit (rejects `CUSTOMER`, `banana`) |

No silent defaults: missing `maxLength` or `characterSet` → invalid.

Deprecated aliases `M3_PARTY_ID` / `M3_ORDER_ID` still exist and delegate to the same identifier logic (no longer digits-only for orders). Remove them only after a later cleanup.

Site / person / generic shapes are unchanged.

---

## Supported vs unsupported (2A)

**Supported:** `customer 45678`, `customer=45678`, `customer (45678)`, `customer with number 45678`, `cust#45678`, multi-entity `customer` + `purchase order`, order-family longest keyword.

**Unsupported (deferred):** value-before-keyword (`45678 customer`), `->`, nested parens, long NLP connectors, bare IDs without keywords.

---

## Roadmap

| Phase | Focus |
|-------|--------|
| **2A** | This document — then freeze speculative grammar |
| **2B** | Value-before-keyword strategy |
| **2C** | Production mining → regression → evidence-based grammar adds |

---

## Tests

- `BusinessInformationDetectorTest` — ambiguity, punctuation contract, bands, misses  
- `BusinessInformationDetectorCoverageTest` — 100+ parameterized positives/negatives  
- `BusinessInformationDetectorCatalogCompletenessTest` — every catalog entity: POSITIVE + ALIAS (or N/A) + NL before expanding aliases/grammar  
- `ValueShapeValidatorTest` — shape negatives  
