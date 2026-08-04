# Business Data Protection Policy

**Version:** 1.0  
**Status:** Frozen baseline  
**Audience:** Security, Business, Architecture, Engineering  

---

## 1. Philosophy

> **Information is classified once, globally. APIs, prompts, and future integrations inherit the classification rather than redefining it.**

> **Business functionality and information protection are independent concerns.**  
> A field may participate fully in business processing (search criteria, Lex slots, MI execution) while simultaneously being protected when content is prepared for an LLM.

```text
Information meaning (global)
  → Category
  → LLM Exposure Policy
  → Future implementation

Classification is immutable.
Implementation is replaceable.
```

If the LLM vendor changes (OpenAI, Azure OpenAI, Claude, Bedrock, Gemini, or an internal model), the **classification catalog does not change**. Only the implementation that prepares **LLM-bound content** changes.

**Existing API-specific catalogs** (`IntentApiCatalog`, `SearchFieldCatalog`, `ApiFieldCatalog`, `InformationRequestCatalog`, `RequestedInformationResolver`, `FieldDefinitionRegistry`) remain the **system of record for routing and M3 execution**. This policy does not redefine intents, programs, or return-column mappings.

---

## 2. Architectural Invariants

These rules must **never** change unless **Version 2** of this policy is formally approved.

1. Information is classified globally, never per API.
2. One field code has one primary classification.
3. APIs inherit classification; they do not redefine it.
4. Live M3 always uses original business values.
5. LLM-bound content follows the exposure policy.
6. Unknown information is blocked until classified (Unclassified → BLOCK → Review Required).
7. Classification changes require a decision-log entry.

See [04-decision-log.md](04-decision-log.md).

---

## 3. Category definitions

| Category | Name | Meaning |
|----------|------|---------|
| **PII** | Personally Identifiable Information | Data that identifies or relates to a natural person (name, email, phone, address, employee identifiers). |
| **SPI** | Sensitive Personal / regulated identifiers | Tax IDs, VAT registration, organization registration numbers, tax exemption contracts. |
| **BDI** | Business Data Identifier | Stable business keys that identify parties, orders, products, or address/payer references (e.g. CUNO, ORNO). |
| **BFI** | Business Financial Information | Commercial amounts, credit limits, outstanding balances, order values, quantities with commercial sensitivity. |
| **BCI** | Business Confidential Information | Accounts, insurance numbers, giro/clearing, and similar confidential business payload. |
| **OMD** | Operational Metadata | Safe operational context: facility, warehouse, division, status, currency, order type, dates used as process context. |
| **SYS** | System Metadata | Pure system bookkeeping (program name, file, change number, change/entry dates as system stamps). |
| **TECH** | Technical / Internal | Technical payloads that may embed identifiers (e.g. key string). Never auto-ALLOW. |
| **UDF** | User Defined Fields | Free/custom fields and “your reference” style fields; blocked until explicitly approved. |
| **Unclassified** | Temporary | Code not yet in the catalog. **Not** a business meaning. Fail closed. |

### Alignment with legal / privacy labels

Legal labels (PII, SPI, NPI, MNPI, Private Information under statutes such as NY SHIELD) inform this model but are **not** 1:1 mapped. This catalog is a **business-aware** governance model for an M3 assistant: it covers personal privacy **and** business confidentiality.

---

## 4. LLM Exposure Policy

Applies to **LLM-bound content** (prompts, grounded context, gap-fill, general assistant messages). Vendor-neutral.

| Policy | Meaning |
|--------|---------|
| **ALLOW** | Value may pass through to the LLM. |
| **MASK** | Redact without a typed semantic placeholder. |
| **REPLACE** | Substitute using the implementation’s format for the row’s **Placeholder Type**. |
| **BLOCK** | Do not send the value to the LLM (omit / refuse that span). |
| **REVIEW** | Do not auto-ALLOW; requires business/security confirmation and a decision-log entry before ALLOW/REPLACE/etc. |

### Default policy by category

| Category | Default LLM Exposure Policy | Typical Placeholder Type |
|----------|-----------------------------|---------------------------|
| PII | REPLACE | Email, Phone, Person Name, Employee Id, … |
| SPI | BLOCK or REPLACE | Vat Registration, Organization Number, … |
| BDI | REPLACE | Customer Number, Order Number, … |
| BFI | BLOCK | Credit Limit, Order Value, … |
| BCI | BLOCK | Account Number, Insurance Number, … |
| OMD | ALLOW | — |
| SYS | REVIEW until verified; then ALLOW only if proven safe | — |
| TECH | BLOCK or REVIEW (never auto-ALLOW) | Technical Payload |
| UDF | BLOCK until explicitly approved | User Defined Field |
| Unclassified | BLOCK | — (Requires Classification) |

Per-row overrides are allowed only via the [Decision Log](04-decision-log.md).

---

## 5. Path boundaries

| Path | Behaviour |
|------|-----------|
| **Live Lex + M3** | Always uses **original** business values. Classification does **not** strip Lex slots or MI parameters. |
| **LLM-bound content** | Future implementations consult the [Global Information Classification Catalog](01-global-information-classification.md) and apply LLM Exposure Policy. |
| **Persistence / logs** | Today the application may store original and sanitized user text. Constraining logs under this policy is a **future** concern and is out of scope for Version 1.0 documentation. |

---

## 6. Placeholder Type vs placeholder format

- **Placeholder Type** (catalog): semantic label such as `Customer Number` or `Email`.
- **Placeholder format** (implementation config): e.g. `<CUSTOMER_NUMBER>`, `[ORDER]`, `<ID_1>`.

The catalog must not hardcode token syntax. See Decision #9.

---

## 7. Change control

1. Propose a classification or policy change.
2. Add a Decision Log entry.
3. Update [01-global-information-classification.md](01-global-information-classification.md).
4. Do not change Architectural Invariants without a Version 2 policy approval.

New M3 fields or information codes: classify **once** globally; all APIs that expose them inherit the row.

---

## 8. Non-goals (Version 1.0)

- No Java/Python runtime changes in this documentation phase.
- No replacement of SearchFieldCatalog / ApiFieldCatalog / Intent routing.
- No Custom Comprehend NER / Bedrock for M3 entities in this phase.
- No widget or UI changes.
- No binding to a single LLM vendor.

---

## 9. Related documents

| Document | Role |
|----------|------|
| [01-global-information-classification.md](01-global-information-classification.md) | Per-code catalog |
| [03-business-information-detection-spec.md](03-business-information-detection-spec.md) | How detection would resolve codes |
| [04-decision-log.md](04-decision-log.md) | Why decisions were made |
| [README.md](README.md) | Index and compatibility principles |
