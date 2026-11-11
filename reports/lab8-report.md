# Lab 8 - Estimation and effort retrospective

The completed Library API contains **84 unadjusted function points (UFP)** and **1,430 physical production Java lines in 32 files**. Reported active human effort across the eight labs is **10.25 hours**, including the original scaffold contribution. The assignment's function-point reference gives **70 person-hours**; the separate course lab budgets total **20.50 hours**. These are different estimation references, not interchangeable forecasts.

The application verified for this report is the completed Lab 7 implementation on `main`. Lab 8 adds estimation and retrospective documentation; the Fine Payment module below is hypothetical.

## 1. Function-point boundary and counting rules

The boundary is the library's business API: book/member maintenance, borrowing/returns, and reservations. Generated OpenAPI lists **14 business operations**. Four logical data groups are owned and maintained inside that boundary: `Book`, `Member`, `Borrowing`, and `Reservation`.

The count uses the five categories and weights in Chapter 8, with the assignment's field-and-business-rule complexity judgments:

- **Simple:** one small record group and ordinary validation, lookup, uniqueness, or defaults.
- **Average:** a constrained update, a cross-record integrity condition, or a member-scoped history query.
- **Complex:** a transactional lifecycle operation coordinating related records, dates/amounts, conflicts, and concurrent changes.
- **Logical files:** rate their data structure separately. Each current group has one small record structure and fewer than 20 fields. Transactional rules are not counted again as data complexity.

This is an instructional count using documented judgments, not a certified count using formal data-element, file-reference, and record-element matrices. Relationships count as reference fields rather than recursively embedding every related entity field.

### Transaction inventory

| Operation | Type | Complexity | FP | Fields and business-rule justification |
| --- | --- | --- | ---: | --- |
| `POST /books` | EI | Simple | 3 | Six writable fields; one book group, validation, unique ISBN, and availability default. |
| `PUT /books/{id}` | EI | Average | 4 | Six writable fields plus ID; shared book lock and active-loan check prevent an update from reopening a borrowed book. |
| `DELETE /books/{id}` | EI | Average | 4 | One ID; deletion must preserve borrowing/reservation references. Database foreign keys enforce this integrity condition. |
| `POST /members` | EI | Simple | 3 | Four writable fields; one member group, unique email, membership-date and active defaults. |
| `POST /borrowings` | EI | Complex | 6 | Two IDs; book/member existence, availability, fourteen-day due date, and atomic creation plus book-state change. |
| `PUT /borrowings/{id}/return` | EI | Complex | 6 | One ID; loan/book locks, repeated-return guard, return date, overdue amount, and restored availability. |
| `POST /reservations` | EI | Complex | 6 | Two IDs; existence, unavailable-book and active-pair rules, dates/history, and coordinated locking. |
| `PUT /reservations/{id}/cancel` | EI | Average | 4 | One ID; guarded status/date transition, retained history, and shared book locking. |
| `GET /books` | EQ | Simple | 3 | Retrieve stored book records with seven fields per record. |
| `GET /books/{id}` | EQ | Simple | 3 | ID lookup returning seven stored fields. |
| `GET /members` | EQ | Simple | 3 | Retrieve stored member records with five fields per record. |
| `GET /members/{id}` | EQ | Simple | 3 | ID lookup returning five stored fields. |
| `GET /members/{id}/borrowings` | EQ | Average | 4 | Check member existence, select unreturned loans, and return seven-field records ordered by ID. |
| `GET /members/{memberId}/reservations` | EQ | Average | 4 | Check member existence and retrieve that member's active/cancelled history as ordered six-field records. |

### Logical data inventory

| Logical group | Type | Complexity | FP | Record structure |
| --- | --- | --- | ---: | --- |
| `Book` | ILF | Simple | 7 | Seven fields: ID, ISBN, title, author, genre, published year, availability. |
| `Member` | ILF | Simple | 7 | Five fields: ID, name, email, membership date, active status. |
| `Borrowing` | ILF | Simple | 7 | Seven fields: ID, book/member references, borrow/due/return dates, assessed fine. |
| `Reservation` | ILF | Simple | 7 | Six fields: ID, book/member references, reservation date, status, cancellation date. |

There are **no separate external outputs (EO)** in the current business boundary: all six GET operations retrieve stored data, with selection/ordering where applicable. Returning a stored `fineAmount` does not calculate a new report. The fine calculation belongs to the return EI; its response, other write responses, and ordinary errors are not counted as extra elementary functions. The hypothetical monthly payment summary would be an EO, but it is outside the current count.

There are **no external interface files (EIF)**. H2 stores the application's own four ILFs; it is a database engine, not a logical business dataset maintained by another application. OpenAPI and Swagger are generated technical documentation, not externally maintained business records. The handout's parenthetical examples of “GET endpoints” as outputs and “H2, OpenAPI” as interfaces are interpreted using the lecture's logical-data boundary. This avoids double counting retrievals or treating infrastructure as additional business functionality.

### UFP summary

| Element | Simple count × weight | Average count × weight | Complex count × weight | Count | Weighted FP |
| --- | ---: | ---: | ---: | ---: | ---: |
| External inputs (EI) | 2 × 3 | 3 × 4 | 3 × 6 | 8 | 36 |
| External outputs (EO) | 0 × 4 | 0 × 5 | 0 × 7 | 0 | 0 |
| External inquiries (EQ) | 4 × 3 | 2 × 4 | 0 × 6 | 6 | 20 |
| Internal logical files (ILF) | 4 × 7 | 0 × 10 | 0 × 15 | 4 | 28 |
| External interface files (EIF) | 0 × 5 | 0 × 7 | 0 × 10 | 0 | 0 |
| **Total UFP** | | | | **18** | **84** |

The 18 counted elements comprise 14 transactions and four data groups. No adjustment factor is applied. Book deletion's Average rating reflects its referential-history constraint rather than a bespoke service algorithm; rating that one operation Simple instead would produce 83 UFP, illustrating the small judgment sensitivity.

## 2. Convert function points to reference effort

The assignment supplies a reference of 8–10 FP per person-day and 7.5 hours per day. Using its midpoint of 9:

```text
Estimated person-days = 84 / 9 = 9.333333
Estimated person-hours = 9.333333 × 7.5 = 70.00
At 10 FP/day: 84 / 10 × 7.5 = 63.00 hours
At  8 FP/day: 84 /  8 × 7.5 = 78.75 hours
```

Thus the reference estimate is **70.00 hours**, with a **63.00–78.75-hour** range. This numerical rate is an assignment assumption, not a rate measured on this project. The course's scheduled exercise budgets below describe a different reference and are not substituted for this FP estimate.

## 3. Estimated versus actual effort

Actual values are **reported retrospective estimates of active human effort**, excluding unattended AI/build execution. Lab 1 includes the original scaffold contribution; Labs 2–8 include the continuation work. The whole-project total therefore covers both contributions, rather than attributing all delivered code to one person's work from scratch.

No separate personal forecasts for all eight complete labs were recorded. The comparison therefore preserves the handout's **course reference budgets**, clearly labeled below. The genuine original Lab 2 story estimates are compared separately with their matching implementation/testing scope.

| Lab | Course reference (h) | Retrospective actual (h) | Actual / reference |
| --- | ---: | ---: | ---: |
| 1 - Initial application | 2.00 | 1.00 | 0.5000 |
| 2 - Agile borrowing sprint | 2.50 | 2.75 | 1.1000 |
| 3 - Reservation specification and feature | 2.50 | 1.00 | 0.4000 |
| 4 - Design, API contracts, and deployment | 3.00 | 1.50 | 0.5000 |
| 5 - Refactoring | 2.50 | 0.75 | 0.3000 |
| 6 - Metrics and static analysis | 2.50 | 1.00 | 0.4000 |
| 7 - Test review and fault detection | 3.00 | 1.25 | 0.4167 |
| 8 - Estimation and retrospective | 2.50 | 1.00 | 0.4000 |
| **Total** | **20.50** | **10.25** | **0.5000** |

The aggregate ratio is `10.25 / 20.50 = 0.50`. Its reciprocal, `20.50 / 10.25 = 2.00`, is a **2.00× course-budget-to-actual-effort multiplier**. It is the requested velocity-style comparison for this assisted workflow, not XP story-point velocity or a controlled measurement of AI speedup. Scheduled course budgets, reuse of the original scaffold/framework, and retrospective measurement all limit that interpretation.

### Lab 2: preserved story estimates

| Story | Original estimate (h) | Retrospective actual (h) | Actual / estimate |
| --- | ---: | ---: | ---: |
| US-01 - Record borrowing | 0.75 | 1.00 | 1.3333 |
| US-04 - Calculate fine | 0.25 | 0.25 | 1.0000 |
| US-02 - Record return | 0.50 | 0.75 | 1.5000 |
| US-03 - List active borrowings | 0.50 | 0.50 | 1.0000 |
| **Total** | **2.00** | **2.50** | **1.2500** |

Story work exceeded its original estimate by **25%**. The reciprocal story multiplier is `2.00 / 2.50 = 0.80`. A separately reported **0.25 hour** for setup, review, and reporting brings whole-Lab-2 effort to **2.75 hours**. That overhead is included once in the eight-lab table and is outside both story totals. The [sprint backlog](../docs/sprint-backlog.md) and [Lab 2 report](lab2-report.md) contain the same figures.

### DeMarco Estimation Quality Factor

Chapter 8 defines EQF as actual area divided by absolute forecast-error area. For final actual effort `A`, successive forecasts `E_j`, and their observed durations `Δt_j`:

```text
EQF = A × ΣΔt_j / Σ(|E_j − A| × Δt_j)
```

The absolute values matter: an overestimate must not cancel an underestimate in the error area. For a single constant reference held for the entire interval, duration cancels and `EQF = A / |E − A|`.

No timed forecast-revision history was recorded here, so a measured chronological DeMarco curve cannot be reconstructed. Applying the constant-reference version to each course budget gives the following **benchmark approximation**:

| Lab | Absolute error (h) | Constant-reference EQF |
| --- | ---: | ---: |
| 1 | 1.00 | 1.0000 |
| 2 | 0.25 | 11.0000 |
| 3 | 1.50 | 0.6667 |
| 4 | 1.50 | 1.0000 |
| 5 | 1.75 | 0.4286 |
| 6 | 1.50 | 0.6667 |
| 7 | 1.75 | 0.7143 |
| 8 | 1.50 | 0.6667 |

Two aggregate summaries answer different questions:

```text
Fixed total-reference EQF
  = 10.25 / |20.50 − 10.25| = 1.0000

Equal-duration per-lab error-area proxy
  = ΣA_i / Σ|E_i − A_i|
  = 10.25 / (1.00 + 0.25 + 1.50 + 1.50 + 1.75 + 1.50 + 1.75 + 1.50)
  = 10.25 / 10.75 = 0.9535
```

The first permits cancellation when lab totals are combined; the second retains each absolute error while assigning every lab one equal interval. Neither is a measured forecast trajectory, and the second is not the arithmetic mean of the individual EQFs. The actual forecast intervals needed for a chronological calculation are unavailable. For the comparable Lab 2 story totals, the fixed-estimate calculation is `2.50 / |2.00 − 2.50| = 5.00`, also without claiming a revision history.

The lecture's printed worked example has an arithmetic inconsistency: its absolute areas give `40.25 / 4.75 = 8.4737`, rather than the printed 8.7. The calculations above use the stated absolute-area definition. A high ratio such as Lab 2's 11 only indicates closeness to that fixed course budget; it does not establish the quality of forecasts made and revised during this project.

## 4. LOC and function-point productivity

The requested production-Java count was taken from `src/main/java`, excluding test code, generated output, dependencies, and documentation:

```sh
find src/main/java -name "*.java" | xargs wc -l | tail -1
find src/main/java -name "*.java" | wc -l
```

| Measure | Observed value |
| --- | ---: |
| Production Java files | 32 |
| Physical lines, including blanks/comments | 1,430 |
| Nonblank lines | 1,222 |
| Comment-only lines | 4 |
| Nonblank, non-comment-only physical lines | 1,218 |

Using the assignment's requested physical-line numerator and the combined 10.25-hour human-effort denominator:

```text
Physical LOC/hour = 1430 / 10.25 = 139.51
FP/hour           =   84 / 10.25 =   8.1951
```

**139.51 physical LOC/hour** can make the workflow appear productive simply because it emits many lines. This code includes DTO declarations, mapping, validation/OpenAPI annotations, and formatting. Those can increase line count without adding another business function. Refactoring can reduce lines while improving design; Lab 6's correctness fix and Lab 7's stronger tests improve assurance without increasing the business UFP count. The 1,430-line figure is therefore not a logical-statement count or a measure of independently authored work.

**8.20 FP/hour** is the fairer of these two indicators of delivered functional scope because it is less sensitive to Java layout and boilerplate. It still depends on the declared boundary and complexity judgments, and does not directly measure maintainability, security, test strength, or defect prevention. Both rates use reported retrospective human hours and include existing scaffold/framework reuse; neither isolates a causal AI effect. Dividing the entire product by only the 9.25 continuation hours would omit the original contribution and misalign numerator and denominator.

## 5. Hypothetical Fine Payment extension

### Proposed requirements and boundaries

The module records full **offline fine settlements** in EUR. It does not transfer money through a payment provider. An assessed fine is a returned borrowing whose stored `fineAmount` is positive, identified by that borrowing's ID. Preserve the historical assessed amount; do not recalculate it at payment time. Active loans and zero-fine returns are not payable fines.

Add a `Payment` record with generated ID, unique required borrowing reference, copied full amount, `paidAt` instant, verified staff identity, and an optional bounded receipt reference. Retain payment history. Recording payment must not change the assessed fine, book availability, member state, or reservations.

| Route | Contract |
| --- | --- |
| `GET /members/{id}/fines` | Return that member's unpaid assessed fines ordered by fine ID: fine/book/member IDs, due date, assessment/return date, amount, and EUR currency. Existing member with none: `200 []`; missing member: `404`. |
| `POST /fines/{id}/pay` | Record the full stored amount and return `201` with an immutable receipt. The request may contain a receipt reference; it cannot set amount, payment ID, actor, or timestamp. Absent/non-assessed fine: `404`; already paid: `409`; invalid request: `400`. |
| `GET /reports/fines?month=YYYY-MM` | Require a valid month and return month, currency, payment count, and total paid. Use local month start inclusive and next-month start exclusive, converted to instants in the configured business zone. Empty month: count `0`, amount `0.00`; invalid/missing month: `400`. |

Payment runs in one transaction: lock the borrowing row, check existing payment, then insert exactly one payment. A unique borrowing constraint provides an additional integrity guard. Concurrent payments must yield one success and one conflict, with one recorded amount in the monthly total. Reuse the existing `Clock`, flat DTOs, OpenAPI descriptions, and error envelope. Acceptance checks must cover exact decimal amounts, month boundaries, duplicate/concurrent requests, permissions, history retention, and unchanged borrowing/reservation behavior.

Members may view only their own fines; librarians may record payments and read reports. The current application has no authentication. This estimate assumes a trusted external identity mechanism supplies verified actor/role context; it includes an adapter and permission checks, but excludes building an identity platform. Client-supplied actor fields or arbitrary unverified headers are insufficient for that assumption.

Other exclusions are partial payments, discounts, refunds, payment processors, notifications, a frontend, and bulk reconciliation or migration of unknown historical settlements. Existing fines cannot safely be assumed unpaid in a real migration without resolving earlier offline payments. The current H2 create/drop configuration is suitable for the lab prototype, not a durable production payment ledger. These boundaries define the estimate; no payment route or entity is implemented by this lab.

### Three-point production-code estimate

The size convention is delivered production Java physical lines excluding blank and comment-only lines. It includes entity/DTO declarations, annotations, mapping, and the stated identity adapter. Test-source lines are excluded from size, while ordinary specification, design, testing, and review remain part of the model's lifecycle effort. This source-line convention is a practical proxy for historical delivered source instructions, and differs from the raw physical-line numerator requested in section 4.

| Component | Minimum LOC | Most likely LOC | Maximum LOC |
| --- | ---: | ---: | ---: |
| Payment entity and repository | 55 | 80 | 115 |
| Fine queries and projections | 30 | 50 | 80 |
| Request/response DTOs and mapper | 45 | 70 | 110 |
| Application services | 85 | 130 | 190 |
| Three routes and OpenAPI declarations | 75 | 105 | 145 |
| Trusted identity/role adapter | 20 | 35 | 60 |
| Exception types and wiring | 15 | 25 | 40 |
| **Total** | **325** | **495** | **740** |

```text
Expected size = (minimum + 4 × most likely + maximum) / 6
              = (325 + 4 × 495 + 740) / 6
              = 507.5 LOC = 0.5075 KLOC
```

The expected value **507.5** is distinct from the most-likely input **495**. The range allows for query and integration complexity within the defined scope; it does not cover building the excluded identity platform or payment processor.

### COCOMO 81, organic mode

Apply the assignment's coefficients and Chapter 8's **152 person-hours per person-month**:

```text
Effort HM = 2.4 × KLOC^1.05
          = 2.4 × 0.5075^1.05
          = 1.1773866 person-months

Duration TDEV = 2.5 × HM^0.38
              = 2.5 × 1.1773866^0.38
              = 2.6600467 calendar months

Average team size = HM / TDEV
                  = 1.1773866 / 2.6600467
                  = 0.4426188 full-time equivalents

Effort in hours = 1.1773866 × 152 = 178.9628 person-hours
```

| Size scenario | LOC | Effort (person-months) | Effort (person-hours) | Duration (calendar months) |
| --- | ---: | ---: | ---: | ---: |
| Minimum | 325 | 0.7374 | 112.08 | 2.2267 |
| Three-point expected | 507.5 | 1.1774 | 178.96 | 2.6600 |
| Maximum | 740 | 1.7495 | 265.92 | 3.0920 |

Fractional average staffing is a model ratio, not a recommendation to hire part of a person. Calendar duration and person-month effort are different quantities. The 152-hour person-month conversion is separate from the 7.5-hour day used by the FP reference. These results extrapolate the required historical model to a very small framework-based extension and are not a validated delivery schedule.

## 6. Proposed adjustment for assisted development

The reference-budget-to-actual multiplier is **2.00**. As an illustrative coefficient adjustment, retain `β = 1.05` and divide the organic effort coefficient by that multiplier:

```text
α_scenario = 2.4 / 2.00
           = 2.4 × (10.25 / 20.50)
           = 1.20

Adjusted extension effort = 1.20 × 0.5075^1.05
                          = 0.5886933 person-months
                          = 89.4814 person-hours
```

The proposed **α = 1.20** expresses a hypothesis that the reference effort is halved under a comparable assisted workflow. It is not an empirically fitted COCOMO coefficient: the 20.50-hour denominator is a course budget, not an unassisted COCOMO prediction or experimental control. A coefficient validated for future work would require several comparable projects with consistent size definitions, lifecycle scope, actual effort, and prediction errors. The observed Lab 2 story overrun also shows why one aggregate multiplier should not be applied indiscriminately to every task.

## 7. Final lifecycle reflection

Across the eight labs, the lifecycle remained specify → design → implement → test → measure → estimate. The backlog and reservation rules made expected behavior explicit before implementation; DTOs, services, error contracts, and OpenAPI then gave that behavior a coherent design. AI assistance supplied drafts and supported iteration, while observed failures still determined whether a change worked: fractional identifiers exposed a binding defect, static analysis led to a real `ApiError` equality correction, and deliberate fine mutations checked whether tests could detect wrong behavior. Refactoring improved structure while regression tests preserved the contract. Coverage and static-analysis counts supplied evidence with limits, while this final estimate required an explicit boundary, consistent units, and actual effort separate from reference assumptions. Assistance changed how candidate code, tests, and explanations were produced; it left the need for precise requirements, critical review, executable evidence, and honest measurement intact.

## 8. Final verification

The final check ran on `main` with Java **17.0.21.0.3** and Maven **3.10.0**, completing on **11 November 2026 at 20:31 UTC**:

```sh
mvn -B -ntp test pmd:pmd spotbugs:spotbugs pmd:check
```

| Check | Observed result |
| --- | --- |
| Maven tests | **163 passed**, zero failures, errors, or skipped tests; command exit 0. |
| JaCoCo service lines | **109/109 = 100%**, exceeding the required 80%. |
| JaCoCo service branches | **32/32 = 100%**. |
| JaCoCo overall lines | **326/331 = 98.49%**. |
| JaCoCo overall branches | **46/48 = 95.83%**. |
| Static analysis | **9 PMD** and **10 SpotBugs** findings, consistent with the reviewed Lab 6 remainder; no SpotBugs analysis errors or missing classes. A successful command is not a claim of zero findings. |
| Container build | Fresh build of `library-api:lab8` from the repository Dockerfile succeeded for `linux/amd64`. The Docker build skips tests; test results above come from the separate Maven run. |
| Running container | **60 HTTP checks passed**, covering seeded data, CRUD, borrowing, returns, reservations, errors, and OpenAPI. |
| Swagger UI | `/swagger-ui.html` rendered successfully in Chrome with **14 operations** matching generated OpenAPI. |
| Required artifacts | Reports 1–8, reservation specification, backlog with actual story hours, GQM document, Dockerfile, and PMD/SpotBugs/JaCoCo configuration are present. |
| Existing milestones | `lab-1` through `lab-7` identify their completed milestones; this report completes the eighth lab's deliverables. |

The container, temporary browser profile, and verification VM were stopped or removed after checking; Swagger was verified while the application was running. All 56 source/build/specification inputs match the verified Lab 7 baseline and final Lab 8 checks. Later edits affect only documentation and ignore rules.

## References

- *SE_Lab_Assignment_2026_2027_Codex*, PDF pp. 27–30: Lab 8 counting, productivity assumptions, effort comparison, hypothetical routes, required report, and final checklist; each lab's introduction supplies its course time budget.
- *MonChap8_EstimationCout*, PDF pp. 10–11: three-point size estimation; pp. 13–16: COCOMO equations and 152-hour person-month; pp. 23–29: functional categories and weights; p. 30: lifecycle scope of productivity; pp. 31–32: DeMarco EQF definition and example.
- Repository evidence: controller/service/entity implementations; [Lab 6 metrics](lab6-report.md), [Lab 7 testing](lab7-report.md), [GQM definitions](../docs/gqm.md), and [sprint backlog](../docs/sprint-backlog.md).
