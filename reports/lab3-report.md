# Lab 3 — Spec-Driven Development

The reservation feature implements the [completed specification](../docs/AGENTS.md): creating a request for an unavailable book, listing a member's complete reservation history, and cancelling a request while retaining its history. The first implementation conformed to all eight business rules. Final verification passed 90 tests and 47 checks against the packaged application.

The starting point was the completed `lab-2` revision, `bed73de7edc2579b8f526ef5db350d5bae9d30f3`. The specification was completed and frozen before any reservation Java files were created; the implementation plan was then reviewed before coding. The initial conformance audit was recorded before any audit-driven corrections. It found no blocking mismatch and requested no correction.

## 1. Specification quality audit

The classifications below use the assignment's definitions: **precise enough** means the specified behavior was implemented correctly; **ambiguous** means implementation required an unspecified choice; **missing** means required behavior had to be invented outside the specification. The rule text is reproduced from the specification.

The main implementation references are [ReservationService](../src/main/java/com/library/api/service/ReservationService.java), [ReservationRepository](../src/main/java/com/library/api/repository/ReservationRepository.java), [Reservation](../src/main/java/com/library/api/entity/Reservation.java), and [ReservationController](../src/main/java/com/library/api/controller/ReservationController.java). Acceptance checks are named by criterion in [ReservationServiceTest](../src/test/java/com/library/api/service/ReservationServiceTest.java), [ReservationControllerTest](../src/test/java/com/library/api/controller/ReservationControllerTest.java), and [ReservationConcurrencyTest](../src/test/java/com/library/api/service/ReservationConcurrencyTest.java).

### BR-1 — Existing records

> Creation requires an existing book and member. Any existing member is eligible, regardless of `Member.active` or whether that member currently holds the book; this feature adds no membership or borrower-identity restriction.

**Precise enough.** `reserveBook` looks up the book and then the member. It introduces no activity or current-borrower check. AC-2 exercises active and inactive members, and the current borrower; AC-7 verifies missing-resource precedence. Rejected requests do not create a reservation.

### BR-2 — Availability at creation

> A new reservation is permitted only when `Book.available == false`. A true value causes HTTP 409 with detail exactly `Book is available for direct borrowing.` Availability is determined by this flag; an associated active `Borrowing` is not required.

**Precise enough.** `reserveBook` checks the locked book's flag before the active-pair query and throws the exact conflict message. AC-1 verifies the HTTP status, `detail`, unchanged availability, and absence of a new row. Successful unavailable-book fixtures without a borrowing row confirm that the service does not invent an additional loan requirement.

### BR-3 — One active request per pair

> At most one `ACTIVE` reservation may exist for the same book/member pair. A second active request causes HTTP 409 with detail exactly `An active reservation already exists for this book and member.` Different members may reserve the same unavailable book.

**Precise enough.** `existsByBookIdAndMemberIdAndStatus` filters by both identifiers and `ACTIVE`. AC-3 verifies the exact duplicate conflict, preservation of the original row, and successful creation for another member. Creation holds the shared book lock through commit; AC-10 observes actual contention between two creation transactions and confirms one committed active pair and one duplicate conflict.

### BR-4 — Initial state and dates

> A new reservation has status `ACTIVE`, `reservationDate = LocalDate.now(clock)`, and `cancelledDate = null`. Dates come from the existing injected `Clock` and its zone, not from client input. The reservation does not change book availability or any existing member or borrowing record.

**Precise enough.** `reserveBook` constructs only a new reservation, sets its relationships, date, and status, and leaves its cancellation date null. [ReservationRequest](../src/main/java/com/library/api/dto/ReservationRequest.java) binds only the two related-resource IDs; [ReservationResponse](../src/main/java/com/library/api/dto/ReservationResponse.java) exposes the six specified fields. AC-2 checks server-controlled values despite supplied extra fields, persisted state, and unchanged existing records. The fixed clock's local date is the past leap day `2024-02-29`, making reliance on the system date detectable.

### BR-5 — Cancellation and history

> Cancelling an `ACTIVE` reservation changes its status to `CANCELLED` and sets `cancelledDate = LocalDate.now(clock)`. Its ID, book, member, and original reservation date are preserved. The row is retained; cancellation never deletes it or changes book availability. Cancelling an already cancelled row returns HTTP 409 with detail exactly `Reservation has already been cancelled.` and preserves the first cancellation date.

**Precise enough.** `cancelReservation` retrieves the scalar book ID, locks that book, and then reads the reservation's current status. It changes only status and cancellation date. AC-4 checks retained identity and relationships, an earlier original date, unchanged related records, and rejection of repeated cancellation. A fixture with an earlier cancellation date detects accidental overwriting. AC-10 verifies one successful cancellation and one conflict under contention.

### BR-6 — A later request is a new record

> A cancelled reservation does not prevent a new reservation for the same pair, provided BR-2 still holds. The new record receives a new ID. Multiple cancelled historical rows for the same pair are allowed.

**Precise enough.** The duplicate query considers only `ACTIVE` rows. The entity deliberately has no `(book_id, member_id, status)` uniqueness constraint, which would reject a second cancelled historical row. AC-5 performs two creation/cancellation cycles and then creates a third active record, checking distinct IDs and retained rows. AC-10 tests both cancellation-before-creation and duplicate-creation-before-cancellation orderings.

### BR-7 — Member history

> A member's reservation list contains all that member's reservations, including `ACTIVE` and `CANCELLED` records, ordered by ascending reservation ID. A valid member with no reservations receives an empty JSON array. A missing member receives HTTP 404. Records belonging to other members are excluded.

**Precise enough.** `getReservationsForMember` checks member existence and calls `findByMemberIdOrderByIdAsc` without a status filter. AC-6 checks mixed statuses, another member's excluded records, ordering, empty history, and missing membership. A missing member is therefore distinguishable from an existing member with no reservations.

### BR-8 — Book returns and feature limits

> The unavailable-book condition applies only when creating a reservation. Returning a book does not cancel, complete, allocate, expire, or otherwise modify existing reservations. Such a reservation remains `ACTIVE` until explicitly cancelled, even if the book becomes available or is borrowed again. Cancellation remains allowed in either availability state. There is no automatic allocation, notification, expiry, queue priority, or borrowing restriction in this feature.

**Precise enough.** Existing borrowing and return code is unchanged, and cancellation has no availability guard. AC-9 exercises an actual return: the book becomes available, its reservation remains active, another reservation request receives the availability conflict before any duplicate conflict, and cancellation still succeeds. AC-10 tests both creation/return orderings. Code inspection confirms no reservation restriction was added to borrowing and no automatic fulfillment or expiry mechanism was introduced.

### Gaps, scope choices, and evidence limits

Returning all statuses, permitting inactive members and current borrowers, retaining cancelled rows, and treating availability as a creation-only condition were explicit choices in the frozen specification. They are not ambiguities discovered after implementation. No business-rule defect or ambiguity was manufactured to populate the audit.

The specification is sufficient for this lab's acceptance scope. It leaves broader deployment requirements unspecified:

- **Authorization:** It defines no authenticated identity or ownership policy for viewing or cancelling another member's reservations. Matching the API specification does not demonstrate access control.
- **Durability and retention:** Retention means preserving rows on cancellation. With the existing in-memory H2 configuration, it does not guarantee history across application restarts, backups, or archival retention.
- **Operational targets:** There is no latency or throughput target, history-size limit, availability objective, or response/retry policy for lock timeouts. Bounded test waits are not service-level objectives.
- **Other writers and databases:** The shared book-lock discipline covers the application's service operations. Direct SQL writers and other database engines or isolation levels were not verified. Six observed H2 interleavings provide specific concurrency evidence, not an exhaustive proof.

These omissions did not require inventing additional business behavior in the implemented feature. They identify requirements needed for a wider deployment. The lecture's distinction between verification and validation matters here: the tests verify conformance to the selected rules; they do not establish that those rules satisfy every real library's needs.

## 2. Requirements partner and plan confirmation

The following is the plan confirmation recorded before implementation:

> The completed specification defines reservation tracking with retained cancellation history. The implementation will preserve the existing borrowing, return, seed-data, serialization, and error contracts.
>
> 1. Add `Reservation` with generated `Long id`, required lazy `Book book` and `Member member` associations, `LocalDate reservationDate`, string-backed `ReservationStatus status`, and nullable `LocalDate cancelledDate`. The enum has only `ACTIVE` and `CANCELLED`. No inverse collections, cascading changes, or unique constraint that would prohibit multiple cancelled records will be introduced.
> 2. Add record DTOs `ReservationRequest(bookId, memberId)` with positive non-null IDs and `ReservationResponse(id, bookId, memberId, reservationDate, status, cancelledDate)`. Existing JSON binding continues to reject fractional numeric tokens and ignore extra properties; supplied reservation IDs, statuses, and dates cannot override server values.
> 3. Add a repository with active-pair existence, member-history ordering, and scalar book-ID lookup queries. Use `ReservationService.reserveBook(Long, Long)`, `cancelReservation(Long)`, and `getReservationsForMember(Long)` as the service contract.
> 4. Creation will lock the book, check member existence, reject an available book, then reject an active duplicate in exactly that order. A new row receives the injected clock's local date and ACTIVE status. Inactive members and the current borrower remain eligible; no existing record is changed.
> 5. Cancellation will first retrieve only the reservation's book ID, acquire that book's existing write lock, and then read the reservation entity and its current status. It will retain the row, preserve identity and original date, and set CANCELLED plus the clock's local date exactly once. All reservation mutation paths use the same book-lock order. Listing will check the member and return all statuses ordered by ID within a read-only transaction.
> 6. Add a new `ReservationController` with `POST /reservations` (201), `GET /members/{memberId}/reservations` (200), and `PUT /reservations/{id}/cancel` (200). Reuse the existing 400/404/409 ProblemDetail handling and exact conflict messages. No single-reservation GET or Location header will be added.
> 7. Write `ReservationServiceTest` and `ReservationControllerTest` against the specified acceptance criteria, using isolated databases, fresh persistence reads, and the existing fixed clock. Add bounded contention checks for competing creation/cancellation and their ordering with a book return. Confirm member-history filtering, repeated cancel/recreate cycles, body/path validation, error precedence, and unchanged existing records.
> 8. Run the unchanged baseline, acceptance tests, complete regression build, and packaged HTTP checks. Inspect the first implementation against every rule before making audit-driven fixes; retain that audit as evidence. Produce the required report with this plan, a rule-by-rule classification, functional/non-functional classification, and a comparison grounded in the existing Lab 1 report.
>
> Existing entities, controllers, dependencies, seeds, and global configuration will remain unchanged. No allocation, expiry, notification, priority queue, authentication, or membership policy will be added. The plan needs no additional product decision after the specification's explicit JSON-binding clarification.

The plan was approved against the frozen specification. Review emphasized both cancellation/creation orderings and an earlier cancellation date in repeated-cancellation tests; these are verification consequences of existing rules, not new requirements.

No further clarifying questions were needed after the completed specification was handed over. During specification drafting, the treatment of extra JSON properties had already been made explicit: ignore them and retain server control of reservation state. The absence of subsequent questions indicates that the entity, interfaces, state transitions, exclusions, and error precedence were clear enough to implement. It does not prove completeness for other deployment settings or replace the conformance audit.

## 3. Use cases and requirement classification

The API supports three use cases: **request a reservation**, **view reservation history**, and **cancel a reservation**. A member is the conceptual primary actor; staff follow-up explains the retained history. These are domain roles, not authenticated roles implemented by this API.

| Use case | Preconditions and trigger | Success and alternatives |
| --- | --- | --- |
| Request a reservation | A request identifies a book and member. Creation requires both records, an unavailable book, and no active request for that pair. | Store a new active record with a server date. Invalid input produces 400, missing records 404, and availability/duplicate conflicts 409 in the specified order. |
| View reservation history | A request identifies a member. | Return that member's complete ordered history, including an empty list; a missing member produces 404. |
| Cancel a reservation | A request identifies an existing active reservation. | Retain the row and original date, record cancellation once, and leave related entities unchanged. Missing records produce 404; a repeated cancellation produces 409. |

The Given/When/Then criteria describe concrete main and alternative flows and supply test oracles. BR-8 distinguishes a creation precondition from a continuing invariant: the book must be unavailable when reserving, but need not remain unavailable throughout the reservation's lifetime. A use-case diagram alone would not specify this distinction, exact errors, date rules, or competing-transaction outcomes; the textual rules supply those details.

The assignment asks for a binary classification. Here **functional** covers observable behavior, data semantics, and interface contracts. **Non-functional** includes quality, implementation, and verification constraints. Mixed clauses are separated rather than assigning every detail in a heading to one category.

| Requirement in `docs/AGENTS.md` | Category | Reason |
| --- | --- | --- |
| Project context and purpose: reservation interest, retained history, no exclusive borrowing right | Functional scope | Defines the feature's user goal and relationship to existing behavior. |
| BR-1: existing-record and member eligibility rules | Functional | Determines who can create a request and its failure cases. |
| BR-2: availability flag and exact conflict | Functional | Determines acceptance and the observable error. |
| BR-3: one active pair and different-member eligibility | Functional | Defines an invariant and a conflict response. |
| BR-4: initial state, local date, null cancellation date, unchanged related records | Functional | Defines creation's stored and visible effects. |
| BR-5: retained cancellation, preserved fields, repeated-cancellation conflict | Functional | Defines a state transition and its alternative flow. |
| BR-6: new identity and multiple cancelled histories | Functional | Defines repeat-request behavior and retained data. |
| BR-7: filtering, both statuses, ordering, empty/missing member | Functional | Defines query results and error behavior. |
| BR-8: creation-only availability, return behavior, cancellation in either state, excluded automatic actions | Functional | Defines lifecycle effects and explicit limits on behavior. |
| Entity: six logical fields, required relationships, generated identity, valid states and date/null invariants | Functional data requirements | Describes information the feature must store and preserve. |
| Entity: Java types, table/column mappings, identity strategy, lazy unidirectional JPA relationships without cascades, string enum storage, no incompatible status-based unique constraint | Non-functional design constraints | Prescribes the representation and persistence design supporting those data rules. |
| Creation binds only book/member IDs; other properties cannot override state | Functional | Defines behavior for supplied input. |
| Three methods/paths, request fields, success/error codes, flat six-field responses, ISO dates, uppercase status, explicit null, no required Location header | Functional interface requirements | Specifies observable API contracts. |
| DTOs are Java records; use `@NotNull`, `@Positive`, and `@Valid` | Non-functional implementation constraints | Prescribes how the specified validation and representation are expressed. |
| Validation and precedence: body/path binding, quoted integers, missing-resource order, exact details, ProblemDetail format/content type/errors map, unchanged state on rejection | Functional | Defines expected responses and effects for valid and invalid inputs. |
| Reuse existing JSON configuration, exception classes, and shared error advice | Non-functional compatibility/design constraints | Requires integration with the established implementation. |
| TC-1: one transaction and the existing book lock; TC-2: common lock order and fresh status read after locking | Non-functional integrity/design constraints | Specifies mechanisms for preserving the functional invariants under concurrency. |
| TC-3: overlapping creation/cancellation outcomes; TC-4: both creation/return outcomes | Functional | Defines correct results when operations overlap. |
| TC-4: read-only listing transaction | Non-functional design constraint | Prescribes transaction configuration for reads. |
| AC-1 through AC-9 | Functional verification criteria | Concrete examples for the business, data, validation, and interface rules above. |
| AC-10: committed state and conflict outcomes | Functional verification criterion | Checks the stated concurrent behavior. |
| AC-10: independent transactions and observed contention rather than sequential-only checks | Non-functional verification constraint | Defines the strength of evidence required for the concurrency claim. |
| IC-1: no existing entity/controller modifications; IC-2: no new libraries/dependencies/migrations and reuse JPA/H2 | Non-functional compatibility/technology constraints | Limits implementation scope and dependencies. |
| IC-3: preserve borrowing/return behavior and serialization/error results | Functional regression requirements | Protects existing observable contracts. |
| IC-3: unchanged seeds/global configuration and reuse clock, book lock, and advice | Non-functional compatibility/design constraints | Requires reuse and protects the established baseline. |
| IC-4: named tests, isolated fixtures, existing testing style, acceptance coverage, passing regression tests | Non-functional verification/quality constraints | Establishes how conformance and compatibility are checked. |
| Conventions 1-3: packages, names, entity/repository structure, injection, transaction annotations | Non-functional maintainability/design constraints | Keeps the new implementation consistent with the repository. |
| Conventions 4-5: request validation, flat related IDs, 404/409 error behavior | Functional | Repeats observable contracts already classified above. |
| Conventions 4-5: records, static mapper, annotation and exception-class choices | Non-functional design constraints | Standardizes implementation of those contracts. |
| Convention 6: clock-local dates | Functional | Defines which calendar date is recorded. |
| Convention 6: injected clock, separate H2 databases, fixed fixtures, rollback/cleanup | Non-functional testability/verification constraints | Makes time and persistence checks reproducible. |
| Requirement references and IDs | Non-functional documentation/traceability convention | Connects the specification, course concepts, implementation, and tests. |

Functional rules were easier to specify precisely because they admit concrete inputs, states, and expected responses. Constraints such as using the existing dependencies were also straightforward to verify. Broader quality goals were harder: meaningful performance or availability targets require a workload and deployment assumptions that the lab does not supply. No numerical target was invented. This follows Chapter 3's emphasis on complete, unambiguous, verifiable requirements and measurable wording rather than unspecified qualities such as “fast.”

## 4. Spec-driven versus vibe-driven development

The [actual Lab 1 report](lab1-report.md) records a structured scaffold request covering entities, dependencies, routes, database settings, seed data, conventions, and verification. Its plan was reviewed, and two smoke tests plus initial seed-count checks were explicitly requested. Calling that work entirely open-ended or purely “vibe-driven” would misrepresent the recorded baseline.

The practical difference in Lab 3 is the persistent contract linking named business rules to acceptance criteria and tests. “Reserve an unavailable book” alone leaves questions about cancellation, duplicate histories, eligibility, returned books, competing requests, and error precedence. The completed specification resolves these choices before code is written, so review can identify a concrete rule violation instead of judging whether the result merely seems reasonable.

Lab 3 provides stronger evidence for its feature's behavioral quality: 56 new reservation cases check state transitions, rejected operations, persistence, exact responses, and contention, alongside all 34 existing tests. The first implementation passed its acceptance and contention checks without audit-driven repairs. Lab 1's two tests established seed-loading smoke coverage for a different scope. The larger count does not itself prove superior quality or productivity; the improvement is explicit expectations, traceability, and checks designed to detect specific incorrect behaviors. There was no controlled experiment comparing identical features under different prompting approaches.

## Verification and delivered scope

Checks were executed on 11 November 2026 using Java 17 and Maven 3.10.0.

| Check | Observed result |
| --- | --- |
| Unchanged Lab 2 baseline: `mvn -B -ntp test` | 34 tests passed before reservation implementation. |
| Initial reservation acceptance tests before implementation | Test compilation failed because the new reservation types were absent. No tests executed; this was compilation-stage RED, not an assertion failure. |
| `mvn -B -ntp -Dtest=ReservationServiceTest,ReservationControllerTest test` | 50 cases passed: 17 service and 33 controller cases. |
| `mvn -B -ntp -Dtest=ReservationConcurrencyTest test` | All six contention cases passed. Each observed a blocked `SELECT ... FOR UPDATE` through H2 session metadata before releasing the holding transaction, then checked committed state. |
| `mvn -B -ntp clean verify` | BUILD SUCCESS: 90 tests, zero failures, errors, or skips; executable JAR produced. |
| Packaged application on a local random port | 47 HTTP checks passed, covering seeded counts, reservation lifecycle, validation, errors, borrowing/return interaction, OpenAPI, and Swagger UI. The application was stopped after the checks. |
| Generated API description | All 14 operations were present, including the three new reservation routes. |
| Preservation and specification checks | All 21 preexisting production/resource/Maven files and all existing test files matched `lab-2` byte-for-byte. The specification matched its preimplementation snapshot. |

The three reservation test classes use Spring, JPA/H2, and MockMvc or real service transactions, following the existing borrowing-test style. They are **integration tests**, despite the handout's general “unit tests” wording. The full suite also retains the four isolated fine-calculation unit cases. Separate databases, fixed dates, fresh persistence reads, and fixture cleanup keep the acceptance checks independent of the initial seed-count tests.

The implementation adds seven production files: entity, status enum, repository, service, controller, and two DTOs. Existing entities, controllers, dependencies, seed data, global configuration, and borrowing/return code were not modified. The required specification and this report document the completed scope; the README includes the new API behavior.

**Course references:** *SE Lab Assignment 2026–2027 Codex*, Lab 3, PDF pp. 10–13; *Chapter 3: Needs Expression and Specification Techniques*, PDF pp. 6–9 (needs, requirements, verification and validation), pp. 11–14 (non-functional requirements), and pp. 30–35 (quality, testability, measurable wording, and input/error completeness).
