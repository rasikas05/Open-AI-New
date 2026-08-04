# Global M3 Information Classification Catalog

**Version:** 1.0  
**Status:** Frozen baseline  
**Scope:** Unique M3 field codes and information-request codes for Customer Order, Purchase Order, Manufacturing Order, Distribution Order, Customer Basic, and Customer Financial surfaces listed for V1.0.

**Columns**

| Column | Description |
|--------|-------------|
| Code | M3 field code or information-request code |
| Meaning | Human-readable meaning |
| Category | PII / SPI / BDI / BFI / BCI / OMD / SYS / TECH / UDF |
| LLM Exposure Policy | ALLOW / MASK / REPLACE / BLOCK / REVIEW |
| Placeholder Type | Semantic type (not a literal token format) |
| Protection Reason | Why this category/policy |
| Confidence | High / Medium / Unknown |
| Classification Authority | Security / Business / Architecture (or combination) |
| Notes | Edge cases |
| Appears In | APIs / intents |

Related: [02-business-data-protection-policy.md](02-business-data-protection-policy.md), [04-decision-log.md](04-decision-log.md).

---

## Legend — Appears In abbreviations

| Abbrev | Surface |
|--------|---------|
| CO-S | Customer Order search criteria (OIS100MI/OIS300 SearchHead) |
| CO-A | Customer Order available information |
| PO-S / PO-A | Purchase Order PPS200MI SearchHead criteria / available |
| MO-S / MO-A | Manufacturing Order PMS100MI SearchMO |
| DO-S / DO-A | Distribution Order MMS100MI SearchHead |
| CB | CRS610MI GetBasicData |
| CF | CRS610MI GetFinancial |

---

## 1. Business Data Identifiers (BDI)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| CUNO | Customer number | BDI | REPLACE | Customer Number | Identifies a customer | High | Business + Security | | CO-S, CO-A, CB, CF |
| SUNO | Supplier | BDI | REPLACE | Supplier Number | Identifies a supplier | High | Business + Security | | PO-S, PO-A |
| ORNO | Customer order number | BDI | REPLACE | Order Number | Identifies a customer order | High | Business + Security | | CO-S, CO-A |
| PUNO | Purchase order number | BDI | REPLACE | Purchase Order Number | Identifies a purchase order | High | Business + Security | | PO-S, PO-A |
| MFNO | Manufacturing order number | BDI | REPLACE | Manufacturing Order Number | Identifies a manufacturing order | High | Business + Security | | MO-S, MO-A |
| TRNR | Distribution / stock transaction order number | BDI | REPLACE | Distribution Order Number | Identifies a distribution order | High | Business + Security | | DO-S, DO-A |
| PRNO | Product number | BDI | REPLACE | Product Number | Identifies a product | High | Business + Security | | MO-S, MO-A |
| PYNO | Payer | BDI | REPLACE | Payer Number | Identifies a payer party | High | Business + Security | | CO-A |
| ADID | Address number | BDI | REPLACE | Address Number | Identifies an address record | High | Business + Security | | CO-A |
| ALCU | Search key | BDI | REPLACE | Search Key | Alternate customer search identity | High | Business | | CB |
| CUSU | Our supplier number at customer | BDI | REPLACE | Customer Supplier Reference | Cross-party identifier | High | Business | | CB |
| RORN | Reference order number | BDI | REPLACE | Reference Order Number | Identifies a related order | High | Business + Security | | MO-S, MO-A |
| RORL | Reference order line | BDI | REPLACE | Reference Order Line | Line key for related order | High | Business | | MO-A |
| RORX | Line suffix | BDI | REPLACE | Reference Line Suffix | Suffix key for related order | High | Business | | MO-A |
| INRC | Invoice recipient | BDI | REPLACE | Invoice Recipient | Party identifier | High | Business + Security | | CF |
| CCUS | Company group customer number | BDI | REPLACE | Group Customer Number | Identifies group customer | High | Business + Security | | CF |
| PYGR | Group payer | BDI | REPLACE | Group Payer | Identifies group payer | High | Business + Security | | CF |
| CUIC | Customer number at insurance company | BDI | REPLACE | Insurance Customer Number | External party key | High | Business + Security | Also confidential context; primary BDI | CF |

---

## 2. Personally Identifiable Information (PII)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| CUNM | Customer name | PII | REPLACE | Person Name | Personally identifiable name | High | Security | | CB, CF |
| CUA1 | Customer address 1 | PII | REPLACE | Address | Personal / party address | High | Security | | CB |
| CUA2 | Customer address 2 | PII | REPLACE | Address | Personal / party address | High | Security | | CB |
| CUA3 | Customer address 3 | PII | REPLACE | Address | Personal / party address | High | Security | | CB |
| CUA4 | Customer address 4 | PII | REPLACE | Address | Personal / party address | High | Security | | CB |
| PONO | Postal code | PII | REPLACE | Postal Code | Address component | High | Security | | CB |
| TOWN | City | PII | REPLACE | City | Address component | High | Security | | CB |
| PHNO | Telephone number 1 | PII | REPLACE | Phone | Personally identifiable | High | Security | | CB |
| PHN2 | Telephone number 2 | PII | REPLACE | Phone | Personally identifiable | High | Security | | CB |
| TFNO | Facsimile number | PII | REPLACE | Fax | Contact identifier | High | Security | | CB |
| MAIL | E-mail address | PII | REPLACE | Email | Personally identifiable | High | Security | | CB |
| CESA | SMS id | PII | REPLACE | Sms Id | Contact identifier | High | Security | | CB |
| RESP | Responsible | PII | REPLACE | Employee Id | Employee identifier (Decision #3) | High | Security + Business | | CO-S, CO-A, DO-S, DO-A |
| SMCD | Salesperson | PII | REPLACE | Employee Id | Employee identifier (Decision #3) | High | Security + Business | | CO-S, CO-A |
| BUYE | Buyer | PII | REPLACE | Employee Id | Employee identifier (Decision #3) | High | Security + Business | | PO-S, PO-A |
| PURC | Requisition by | PII | REPLACE | Employee Id | Person identifier (Decision #3) | High | Security + Business | | PO-S, PO-A |
| LSID | User | PII | REPLACE | User Id | User identity | High | Security | | CB |
| LSAD | Address (user) | PII | REPLACE | User Address | User-linked address key | High | Security | | CB |

---

## 3. Sensitive Personal / regulated identifiers (SPI)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| VRNO | VAT registration number | SPI | BLOCK | Vat Registration | Regulated tax identifier | High | Security | | CF |
| CORG | Organization number 1 | SPI | BLOCK | Organization Number | Registration / org identity | High | Security + Business | | CF |
| COR2 | Organization number 2 | SPI | BLOCK | Organization Number | Registration / org identity | High | Security + Business | | CF |
| TECN | Tax exemption contract number | SPI | BLOCK | Tax Exemption Contract | Regulated tax-related contract | High | Security | | CF |

---

## 4. Business Financial Information (BFI)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| CRLM | Credit limit 1 | BFI | BLOCK | Credit Limit | Commercial financial exposure | High | Business + Security | | CF |
| CRL2 | Credit limit 2 | BFI | BLOCK | Credit Limit | Commercial financial exposure | High | Business + Security | | CF |
| CRL3 | Credit limit 3 | BFI | BLOCK | Credit Limit | Commercial financial exposure | High | Business + Security | | CF |
| ODUD | Credit limit 4 — max days | BFI | BLOCK | Credit Limit Days | Credit policy metric | High | Business + Security | | CF |
| TDIN | Overdue invoice amount | BFI | BLOCK | Overdue Amount | Financial exposure | High | Business + Security | | CF |
| TOIN | Outstanding invoice amount | BFI | BLOCK | Outstanding Amount | Financial exposure | High | Business + Security | | CF |
| TBLG | Order value not invoiced | BFI | BLOCK | Uninvoiced Order Value | Financial exposure | High | Business + Security | | CF |
| ODUE | Number of overdue days | BFI | BLOCK | Overdue Days | Credit risk metric | High | Business | | CF |
| INLI | Insurance limit | BFI | BLOCK | Insurance Limit | Financial exposure | High | Business + Security | | CF |
| NALI | Unapproved limit | BFI | BLOCK | Unapproved Limit | Financial exposure | High | Business + Security | | CF |
| NTAM | Net order value | BFI | BLOCK | Order Value | Commercial order value | High | Business + Security | | CO-A |
| ORQT | Ordered quantity | BFI | BLOCK | Ordered Quantity | Commercial volume | High | Business | Sensitive volume | MO-A |
| MAQT | Manufactured quantity | BFI | BLOCK | Manufactured Quantity | Commercial volume | High | Business | | MO-A |

---

## 5. Business Confidential Information (BCI)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| AGAC | Account number — post giro | BCI | BLOCK | Account Number | Payment account confidential | High | Security + Business | | CF |
| AACB | Account number — bank giro | BCI | BLOCK | Account Number | Payment account confidential | High | Security + Business | | CF |
| AGPY | Payer — post giro | BCI | BLOCK | Giro Payer | Payment confidential | High | Security + Business | | CF |
| AGCP | Clearing number — post giro | BCI | BLOCK | Clearing Number | Payment confidential | High | Security | | CF |
| AGBP | Payer — bank giro | BCI | BLOCK | Giro Payer | Payment confidential | High | Security + Business | | CF |
| AGLB | Clearing number — bank giro | BCI | BLOCK | Clearing Number | Payment confidential | High | Security | | CF |
| AGBG | Bank giro number | BCI | BLOCK | Bank Giro Number | Payment confidential | High | Security + Business | | CF |
| AGPG | Post giro number | BCI | BLOCK | Post Giro Number | Payment confidential | High | Security + Business | | CF |
| INSN | Insurance number | BCI | BLOCK | Insurance Number | Confidential contract identity | High | Security + Business | | CF |
| INCO | Insurance company | BCI | BLOCK | Insurance Company | Confidential commercial relationship | High | Business | | CF |
| EALO | EAN location code | BCI | BLOCK | Ean Location Code | Location identity confidential | Medium | Business | | CB |
| TXID | Text identity | BCI | BLOCK | Text Identity | May reference confidential text | Medium | Architecture | | CB |

---

## 6. Operational Metadata (OMD)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| FACI | Facility | OMD | ALLOW | — | Operational context only (Decision #4) | High | Architecture | | CO-S, CO-A, PO-S, PO-A, MO-S, MO-A, DO-S, DO-A |
| WHLO | Warehouse | OMD | ALLOW | — | Operational context only | High | Architecture | | PO-S, PO-A, DO-S, DO-A |
| DIVI | Division | OMD | ALLOW | — | Operational context only | High | Architecture | | PO-S, PO-A, CB, CF |
| CONO | Company | OMD | ALLOW | — | Tenant operational context | High | Architecture | | CB, CF |
| CUCD | Currency | OMD | ALLOW | — | Operational / commercial unit code | High | Architecture | | CO-A, CB, CF |
| ORST | Highest status (CO) | OMD | ALLOW | — | Process status | High | Architecture | | CO-S, CO-A |
| ORSL | Lowest status (CO) | OMD | ALLOW | — | Process status | High | Architecture | | CO-S, CO-A |
| OBLC | Customer order stop | OMD | ALLOW | — | Process control flag | High | Business | | CO-A |
| ORTP | Order type (CO) | OMD | ALLOW | — | Operational type | High | Architecture | | CO-S, CO-A |
| ORTY | Order type (PO) | OMD | ALLOW | — | Operational type | High | Architecture | | PO-S, PO-A |
| ORDT | Order date (CO) | OMD | ALLOW | — | Process date context | High | Architecture | | CO-S, CO-A |
| RLDZ | Requested delivery date | OMD | ALLOW | — | Process date context | High | Architecture | | CO-S, CO-A |
| TIZO | Time zone | OMD | ALLOW | — | Operational context | High | Architecture | | CO-A |
| FRE1 | Statistics identity | OMD | REVIEW | — | Tenant-specific meaning (Decision #11) | Medium | Business | Validate before ALLOW | CO-A |
| TEPY | Payment terms | OMD | ALLOW | — | Commercial terms code | High | Business | | CO-A |
| MODL | Delivery method | OMD | ALLOW | — | Logistics method code | High | Business | | CO-A |
| TEDL | Delivery terms | OMD | ALLOW | — | Logistics terms code | High | Business | | CO-A |
| PUST | Highest status (PO) | OMD | ALLOW | — | Process status | High | Architecture | | PO-S, PO-A |
| PUSL | Lowest status (PO) | OMD | ALLOW | — | Process status | High | Architecture | | PO-S, PO-A |
| POTC | Purchase order category | OMD | ALLOW | — | Operational category | High | Architecture | | PO-S, PO-A |
| PUDT | Order date (PO) | OMD | ALLOW | — | Process date context | High | Architecture | | PO-S, PO-A |
| WHST | Status (MO) | OMD | ALLOW | — | Process status | High | Architecture | | MO-S, MO-A |
| STDT | Planned start date | OMD | ALLOW | — | Process date context | High | Architecture | | MO-S, MO-A |
| FIDT | Planned finish date | OMD | ALLOW | — | Process date context | High | Architecture | | MO-S, MO-A |
| MSTI | Planned start time | OMD | ALLOW | — | Process time context | High | Architecture | | MO-A |
| MFTI | Planned finish time | OMD | ALLOW | — | Process time context | High | Architecture | | MO-A |
| PRIO | Priority | OMD | ALLOW | — | Process priority | High | Architecture | | MO-S, MO-A |
| ITDS | Product description | OMD | ALLOW | — | Descriptive operational text | Medium | Business | May contain sensitive product names in some tenants | MO-A |
| RORC | Reference order category | OMD | ALLOW | — | Operational category | High | Architecture | | MO-A |
| TRTP | Order type (DO) | OMD | ALLOW | — | Operational type | High | Architecture | | DO-S, DO-A |
| TRSH | Highest status (DO) | OMD | ALLOW | — | Process status | High | Architecture | | DO-S, DO-A |
| TRSL | Lowest status (DO) | OMD | ALLOW | — | Process status | High | Architecture | | DO-S, DO-A |
| RIDT | Receiving date | OMD | ALLOW | — | Process date context | High | Architecture | | DO-S, DO-A |
| CUTP | Customer type | OMD | ALLOW | — | Operational classification | High | Business | | CB |
| CSCD | Country code | OMD | ALLOW | — | Geographic code (not full address) | High | Architecture | Distinct from street address PII | CB |
| ECAR | Area/state | OMD | ALLOW | — | Geographic subdivision code | High | Architecture | | CB |
| STAT | Customer status | OMD | ALLOW | — | Process status | High | Architecture | | CB |
| LNCD | Language | OMD | ALLOW | — | Locale metadata | High | Architecture | | CB, CF |
| VTCD | VAT code | OMD | ALLOW | — | Tax code (not registration number) | High | Business | VRNO is SPI | CF |
| TXAP | Tax applicable | OMD | ALLOW | — | Flag | High | Business | | CF |
| BLCD | Customer stop | OMD | ALLOW | — | Process control flag | High | Business | | CF |
| CRTP | Exchange rate type | OMD | ALLOW | — | Operational code | High | Business | | CF |
| TECD | Cash discount term | OMD | ALLOW | — | Terms code | High | Business | | CF |
| PYCD | Payment method AR | OMD | ALLOW | — | Method code | High | Business | | CF |
| CLCD | Collection | OMD | ALLOW | — | Process flag | High | Business | | CF |
| BLPR | Payment reminder code | OMD | ALLOW | — | Process code | High | Business | | CF |
| RMCT | Payment reminder rule | OMD | ALLOW | — | Rule code | High | Business | | CF |
| BLAC | Advice code | OMD | ALLOW | — | Process code | High | Business | | CF |
| ADCA | Advice rule | OMD | ALLOW | — | Rule code | High | Business | | CF |
| PYDI | Payment instruction | OMD | ALLOW | — | Instruction code | High | Business | | CF |
| STMR | Statement rule | OMD | ALLOW | — | Rule code | High | Business | | CF |
| BLII | Interest invoicing | OMD | ALLOW | — | Flag | High | Business | | CF |
| IICT | Interest rule | OMD | ALLOW | — | Rule code | High | Business | | CF |
| RAN1 | Fixed due date 1 | OMD | ALLOW | — | Schedule code | High | Business | | CF |
| RAN2 | Fixed due date 2 | OMD | ALLOW | — | Schedule code | High | Business | | CF |
| RAN3 | Fixed due date 3 | OMD | ALLOW | — | Schedule code | High | Business | | CF |
| RAN4 | Fixed due date 4 | OMD | ALLOW | — | Schedule code | High | Business | | CF |
| ENCD | Use code | OMD | ALLOW | — | Operational code | High | Business | | CF |
| SETA | Service tax code | OMD | ALLOW | — | Tax code | High | Business | | CF |
| DUCD | Due date base | OMD | ALLOW | — | Operational code | High | Business | | CF |
| STMS | Statement code | OMD | ALLOW | — | Process code | High | Business | | CF |
| SERC | Service code | OMD | ALLOW | — | Operational code | High | Business | | CF |
| INSS | Insurance status | OMD | ALLOW | — | Status flag | High | Business | | CF |
| OPAY | Accounts payable payout | OMD | ALLOW | — | Flag | High | Business | | CF |
| CASC | Cash customer | OMD | ALLOW | — | Flag | High | Business | | CF |
| RCPP | Payment receipt document | OMD | ALLOW | — | Flag | High | Business | | CF |
| EXDF | Exclude doubtful allowance | OMD | ALLOW | — | Flag | High | Business | | CF |
| MEAL | Valid media | OMD | REVIEW | — | Ambiguous / tenant-specific (Decision #11) | Medium | Business | | CB |
| HAFE | Harbor or airport | OMD | ALLOW | — | Logistics location code | High | Business | | CB |
| GEOC | Geographical code | OMD | ALLOW | — | Geo code | High | Architecture | | CB, CF |
| TXCO | County | OMD | ALLOW | — | Region name/code | Medium | Business | May be sensitive in some jurisdictions | CB |
| EDES | Place | OMD | ALLOW | — | Place code | Medium | Business | | CB |
| FRCO | County ID | OMD | ALLOW | — | Region id | High | Architecture | | CB |
| SPLE | Standard point location code | OMD | ALLOW | — | Logistics code | High | Business | | CB |
| RASN | Rail station | OMD | ALLOW | — | Logistics code | High | Business | | CB |
| MCOS | Customer order number mandatory | OMD | ALLOW | — | Configuration flag | High | Architecture | | CB |
| PYOP | Search path — payer | OMD | ALLOW | — | Configuration flag | High | Architecture | | CB |
| TEEC | Tax exemption expiry date | OMD | ALLOW | — | Date context | High | Business | Contract number TECN is SPI | CF |
| TAXC | Tax code | OMD | ALLOW | — | Tax code | High | Business | | CF |
| CDRC | Credit department reference | OMD | REVIEW | — | May identify internal org unit or person | Medium | Business | Confirm before ALLOW | CF |

---

## 7. System Metadata (SYS)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| PGNM | Program name | SYS | ALLOW | — | Pure system metadata after review | High | Architecture | Decision #6: explicit ALLOW | PO-A, MO-A, DO-A |
| FILE | File | SYS | ALLOW | — | Pure system metadata after review | High | Architecture | | PO-A, MO-A, DO-A |
| CHNO | Change number | SYS | ALLOW | — | System stamp | High | Architecture | | CB, CF |
| LMDT | Change date for customer | SYS | ALLOW | — | System stamp date | High | Architecture | | CB, CF |
| RGDT | Entry date for customer | SYS | ALLOW | — | System stamp date | High | Architecture | | CB |

---

## 8. Technical / Internal (TECH)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| KSTR | Key string | TECH | BLOCK | Technical Payload | May embed business identifiers (Decision #5) | High | Architecture + Security | Never auto-ALLOW | PO-A, MO-A, DO-A |

---

## 9. User Defined Fields (UDF)

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Notes | Appears In |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------|------------|
| CFC1 | Free field 1 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC2 | Free field 2 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC3 | Free field 3 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC4 | Free field 4 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC5 | Free field 5 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC6 | Free field 6 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC7 | Free field 7 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC8 | Free field 8 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC9 | Free field 9 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | | CB |
| CFC0 | Free field 10 | UDF | BLOCK | User Defined Field | Content unknown until approved | Unknown | Business | M3 label Free field 10 | CB |
| YREF | Your reference 1 | UDF | BLOCK | User Defined Field | Free-text reference; content unknown | Unknown | Business | | CB |
| YRE2 | Your reference 2 | UDF | BLOCK | User Defined Field | Free-text reference; content unknown | Unknown | Business | | CB |

---

## Appendix A — Information-request codes (concept layer)

These codes appear in `InformationRequestCatalog` / `RequestedInformationResolver` / `ApiFieldCatalog`. They inherit the same categories as their primary M3 fields.

| Code | Meaning | Category | LLM Exposure Policy | Placeholder Type | Protection Reason | Confidence | Classification Authority | Maps primarily to | Notes |
|------|---------|----------|---------------------|------------------|-------------------|------------|--------------------------|-------------------|-------|
| CUSTOMER | Customer | BDI | REPLACE | Customer Number | Identifies a customer | High | Business + Security | CUNO | |
| SUPPLIER | Supplier | BDI | REPLACE | Supplier Number | Identifies a supplier | High | Business + Security | SUNO | |
| ORDER_NUMBER | Customer order number | BDI | REPLACE | Order Number | Identifies a customer order | High | Business + Security | ORNO | |
| PURCHASE_ORDER_NUMBER | Purchase order number | BDI | REPLACE | Purchase Order Number | Identifies a PO | High | Business + Security | PUNO | |
| MANUFACTURING_ORDER_NUMBER | Manufacturing order number | BDI | REPLACE | Manufacturing Order Number | Identifies an MO | High | Business + Security | MFNO | |
| DISTRIBUTION_ORDER_NUMBER | Distribution order number | BDI | REPLACE | Distribution Order Number | Identifies a DO | High | Business + Security | TRNR | |
| PRODUCT_NUMBER | Product number | BDI | REPLACE | Product Number | Identifies a product | High | Business + Security | PRNO | |
| PAYER | Payer | BDI | REPLACE | Payer Number | Identifies a payer | High | Business + Security | PYNO | |
| FACILITY | Facility | OMD | ALLOW | — | Operational context | High | Architecture | FACI | |
| WAREHOUSE | Warehouse | OMD | ALLOW | — | Operational context | High | Architecture | WHLO | |
| DIVISION | Division | OMD | ALLOW | — | Operational context | High | Architecture | DIVI | |
| BUYER | Buyer | PII | REPLACE | Employee Id | Employee identifier | High | Security + Business | BUYE | Decision #3 |
| SALESPERSON | Salesperson | PII | REPLACE | Employee Id | Employee identifier | High | Security + Business | SMCD | Decision #3 |
| RESPONSIBLE | Responsible | PII | REPLACE | Employee Id | Employee identifier | High | Security + Business | RESP | Decision #3 |
| CREDIT_LIMIT | Credit limit | BFI | BLOCK | Credit Limit | Commercial financial exposure | High | Business + Security | CRLM (and related) | |
| EMAIL | Email | PII | REPLACE | Email | Personally identifiable | High | Security | MAIL | |
| PHONE | Phone | PII | REPLACE | Phone | Personally identifiable | High | Security | PHNO | |
| ADDRESS | Address | PII | REPLACE | Address | Personally identifiable | High | Security | CUA* | |
| STATUS | Status | OMD | ALLOW | — | Process status | High | Architecture | ORST / PUST / WHST / TRSH etc. | |
| CURRENCY | Currency | OMD | ALLOW | — | Operational unit | High | Architecture | CUCD | |

---

## Appendix B — Coverage checklist (V1.0 user lists)

### Customer Order — search criteria

ORNO, CUNO, FACI, ORTP, ORDT, RESP, SMCD, ORST, RLDZ, ORSL — **classified above**.

### Customer Order — available information

ORNO, CUNO, FACI, ORTP, ORDT, RESP, SMCD, ORST, ORSL, OBLC, NTAM, CUCD, RLDZ, TIZO, FRE1, TEPY, MODL, TEDL, ADID, PYNO — **classified above**.

### Purchase Order — search / available

PUNO, DIVI, WHLO, SUNO, PUSL, PUST, PUDT, BUYE, FACI, ORTY, POTC, PURC, PGNM, FILE, KSTR — **classified above**.

### Manufacturing Order — search / available

MFNO, PRNO, FACI, WHST, STDT, FIDT, RORN, PRIO, ITDS, RORC, RORL, RORX, ORQT, MAQT, MSTI, MFTI, PGNM, FILE, KSTR — **classified above**.

### Distribution Order — search / available

TRNR, FACI, WHLO, TRTP, RESP, TRSH, TRSL, RIDT, PGNM, FILE, KSTR — **classified above**.

### Customer Basic (GetBasicData)

All listed CB fields in sections above — **classified**.

### Customer Financial (GetFinancial)

All listed CF fields in sections above — **classified**.

---

## Unclassified

Any code **not** listed in this catalog is **Unclassified**:

- Category: Unclassified (temporary)
- LLM Exposure Policy: **BLOCK**
- Action: Requires Classification via [04-decision-log.md](04-decision-log.md)
- Do **not** auto-assign BCI
