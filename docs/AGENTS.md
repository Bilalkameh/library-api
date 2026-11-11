# Library API: Book Reservation Specification

## Project context

This Spring Boot 3 REST API manages `Book`, `Member`, and `Borrowing` records. This specification adds reservations to the completed Lab 2 application and applies to the Lab 3 reservation feature; existing borrowing and return contracts remain unchanged.

## Feature: Book Reservation

### Purpose

A member can record interest in a book that is currently unavailable, including a book borrowed by another member. The reservation preserves that request and its cancellation history so library staff can follow up; it does not grant an exclusive right to borrow the book.

### Business rules

- **BR-1 - Existing records:** Creation requires an existing book and member. Any existing member is eligible, regardless of `Member.active` or whether that member currently holds the book; this feature adds no membership or borrower-identity restriction.
- **BR-2 - Availability at creation:** A new reservation is permitted only when `Book.available == false`. A true value causes HTTP 409 with detail exactly `Book is available for direct borrowing.` Availability is determined by this flag; an associated active `Borrowing` is not required.
- **BR-3 - One active request per pair:** At most one `ACTIVE` reservation may exist for the same book/member pair. A second active request causes HTTP 409 with detail exactly `An active reservation already exists for this book and member.` Different members may reserve the same unavailable book.
- **BR-4 - Initial state and dates:** A new reservation has status `ACTIVE`, `reservationDate = LocalDate.now(clock)`, and `cancelledDate = null`. Dates come from the existing injected `Clock` and its zone, not from client input. The reservation does not change book availability or any existing member or borrowing record.
- **BR-5 - Cancellation and history:** Cancelling an `ACTIVE` reservation changes its status to `CANCELLED` and sets `cancelledDate = LocalDate.now(clock)`. Its ID, book, member, and original reservation date are preserved. The row is retained; cancellation never deletes it or changes book availability. Cancelling an already cancelled row returns HTTP 409 with detail exactly `Reservation has already been cancelled.` and preserves the first cancellation date.
- **BR-6 - A later request is a new record:** A cancelled reservation does not prevent a new reservation for the same pair, provided BR-2 still holds. The new record receives a new ID. Multiple cancelled historical rows for the same pair are allowed.
- **BR-7 - Member history:** A member's reservation list contains all that member's reservations, including `ACTIVE` and `CANCELLED` records, ordered by ascending reservation ID. A valid member with no reservations receives an empty JSON array. A missing member receives HTTP 404. Records belonging to other members are excluded.
- **BR-8 - Book returns and feature limits:** The unavailable-book condition applies only when creating a reservation. Returning a book does not cancel, complete, allocate, expire, or otherwise modify existing reservations. Such a reservation remains `ACTIVE` until explicitly cancelled, even if the book becomes available or is borrowed again. Cancellation remains allowed in either availability state. There is no automatic allocation, notification, expiry, queue priority, or borrowing restriction in this feature.

### New entity: Reservation

Use a new `Reservation` entity mapped to the `reservations` table and a new `ReservationStatus` enum. Relationships are unidirectional; existing entities require no back-reference collections.

| Field | Java type | Persistence and meaning |
| --- | --- | --- |
| `id` | `Long` | Generated primary key using `GenerationType.IDENTITY`. |
| `book` | `Book` | Required lazy `@ManyToOne`; non-null `book_id` foreign key, with no cascading entity changes or deletion. |
| `member` | `Member` | Required lazy `@ManyToOne`; non-null `member_id` foreign key, with no cascading entity changes or deletion. |
| `reservationDate` | `LocalDate` | Required creation date; immutable through the API after creation. |
| `status` | `ReservationStatus` | Required; enum values exactly `ACTIVE` and `CANCELLED`, stored as strings. |
| `cancelledDate` | `LocalDate` | Null for `ACTIVE`; set on the first successful cancellation and retained thereafter. |

Creation binds only `bookId` and `memberId`. Other JSON properties, including a reservation's own `id`, `status`, and dates, are ignored and cannot override server-generated values. Do not add a unique constraint on `(book_id, member_id, status)`: that would incorrectly prohibit multiple cancelled historical records. The transaction policy below enforces the active-pair invariant for reservation operations.

### REST endpoints required

Add all three routes to a new `ReservationController`.

| Method | Path | Request body | Successful response | HTTP status codes |
| --- | --- | --- | --- | --- |
| POST | `/reservations` | `{"bookId":6,"memberId":4}` | One `ReservationResponse` for the new active record. | `201`; `400` invalid body; `404` missing book/member; `409` available book or duplicate active pair. |
| GET | `/members/{memberId}/reservations` | None required. | Array of `ReservationResponse` values for all of the member's reservation history, ordered by ID. | `200`; `400` malformed path ID; `404` missing member. |
| PUT | `/reservations/{id}/cancel` | None required. | One `ReservationResponse` for the retained, cancelled record. | `200`; `400` malformed path ID; `404` missing reservation; `409` already cancelled. |

`ReservationRequest` is a Java record containing only `Long bookId` and `Long memberId`, both annotated `@NotNull @Positive`. `ReservationResponse` is a flat Java record with exactly these fields:

```json
{
  "id": 1,
  "bookId": 6,
  "memberId": 4,
  "reservationDate": "2024-02-29",
  "status": "ACTIVE",
  "cancelledDate": null
}
```

Dates use ISO `YYYY-MM-DD`; status uses the uppercase enum value. The response includes `cancelledDate`, including when null, and never embeds `Book` or `Member` objects. No `Location` header is required because this feature does not expose a single-reservation GET route.

### Validation and error precedence

- Preserve existing request binding: absent/null/nonpositive body IDs fail validation with HTTP 400; malformed JSON, nonconvertible values, overflowing 64-bit numbers, and floating-point numeric tokens fail with HTTP 400. Preserve the existing acceptance of quoted integer values that bind to a positive `Long`; do not change global JSON configuration.
- Creation checks run in this order: body conversion/validation, book existence, member existence, book availability, then duplicate active pair. Thus, a missing member takes precedence over an available book, and the available-book message takes precedence over an existing active reservation.
- A path value that cannot bind to `Long` returns HTTP 400. Numeric zero or negative path IDs refer to nonexistent records and return HTTP 404, consistent with the existing lookup endpoints.
- Cancellation checks reservation existence before its current status. Listing checks member existence before querying reservations.
- Reuse `ResourceNotFoundException` with resource names `Book`, `Member`, or `Reservation`. Its detail is `<Resource> with id <id> was not found`. Reuse `IllegalStateException` for the specified 409 conflicts.
- Errors use the existing `ProblemDetail` response and `application/problem+json` content type. Check the specified messages in the `detail` field. Body validation errors retain the existing `errors` map; no map-entry ordering is required.
- Rejected operations leave reservation counts, reservation history, and existing entities unchanged.

### Transactions and concurrent requests

- **TC-1:** Creation and cancellation each run in one service transaction. Reuse `BookRepository.findByIdForUpdate` to lock the affected book row before checking or changing reservation state. This also coordinates creation with the existing borrowing, return, and book-update transactions.
- **TC-2:** All reservation mutations for a book take the book lock first. Cancellation may obtain the book ID through a scalar reservation lookup before acquiring that lock, but it must read the current reservation status after acquiring the lock; a stale entity loaded before waiting must not decide the result.
- **TC-3:** Two overlapping creations for the same unavailable book/member pair yield one committed reservation and one duplicate conflict. Two overlapping cancellations yield one successful cancellation and one already-cancelled conflict. Cancellation and creation for the same pair serialize: creation succeeds if it observes the committed cancellation while the book remains unavailable; otherwise it rejects the still-active duplicate. No path may commit two active reservations for one pair.
- **TC-4:** If a return commits before reservation creation acquires the book lock, creation observes availability and rejects it. If creation commits first while the book is unavailable, the later return leaves the reservation active as required by BR-8. Listing uses a read-only service transaction.

### Acceptance criteria

- **AC-1 (BR-2):** Given an available book and an existing member, when the member reserves it, then the API returns HTTP 409 with `detail` exactly `Book is available for direct borrowing.`, creates no reservation, and leaves the book available.
- **AC-2 (BR-1, BR-4):** Given an unavailable book and an existing member, when a valid request is submitted, then the API returns 201 with a generated ID, the requested book/member IDs, `ACTIVE`, the clock's local date, and a null cancellation date. A fresh database read confirms those values and unchanged book availability. Both inactive members and the current borrower remain eligible.
- **AC-3 (BR-3):** Given an active reservation, a second request for the same pair returns the specified duplicate 409 and preserves the first row. A different member can reserve that same unavailable book successfully.
- **AC-4 (BR-5):** Given an active reservation, cancellation returns 200, preserves the ID and original reservation date, and persists `CANCELLED` with the clock's local date. A repeated cancellation returns the specified 409 without changing its original cancellation date. No book, member, or borrowing state changes.
- **AC-5 (BR-6):** Given a cancelled reservation and a still-unavailable book, the same member can create and cancel a new reservation with a different ID. Both cancelled rows remain stored; the member can subsequently create another active reservation.
- **AC-6 (BR-7):** Given active and cancelled records for a target member and records for another member, the GET route returns exactly the target member's records in ascending ID order with their statuses and dates. A member with no history receives `[]`; a missing member receives 404.
- **AC-7 (BR-1, validation):** Missing book/member IDs produce the specified 404 details. When both are missing, report the book first. When the book is available and the member is missing, report the member first. Missing/null/nonpositive body IDs, fractional numeric IDs, malformed JSON, and numeric overflow return 400 with no stored changes. Valid quoted integer IDs retain the documented binding behavior.
- **AC-8 (BR-5, validation):** Cancelling a missing reservation returns 404. Malformed path IDs return 400; numeric zero/negative path IDs return 404. These failures do not alter existing reservation history.
- **AC-9 (BR-2, BR-8):** Given an active reservation for a borrowed book, returning the book through the existing return endpoint makes the book available but leaves that reservation active. A new reservation request now returns the available-book 409, including when an active duplicate also exists. Cancelling the existing reservation still succeeds and leaves the book available.
- **AC-10 (BR-3, BR-5, TC-1 to TC-4):** Independent overlapping transactions enforce the creation and cancellation outcomes described in the transaction policy, with no duplicate active pair and no overwritten cancellation date. Observe actual contention and inspect committed state; sequential duplicate calls alone are insufficient evidence for this criterion.

### Implementation constraints

- **IC-1:** Do not modify any existing entity or controller. Add a new entity and controller; do not add reservation collections or routes to existing ones.
- **IC-2:** Do not use a library absent from `pom.xml`, add dependencies, or add Flyway or Liquibase migrations. Use the existing JPA/H2 schema-generation setup.
- **IC-3:** Do not change existing borrowing/return behavior, existing seed data, or global serialization/error contracts. Reuse the existing clock, book lock, and exception handler.
- **IC-4:** Add `ReservationServiceTest.java` and `ReservationControllerTest.java` covering the acceptance criteria, using isolated fixtures and the existing Spring/JUnit persistence-testing conventions. Add a separate contention test class if needed. The existing Lab 2 tests must continue to pass.

### Code style conventions observed in the existing code

1. Packages use `com.library.api` with separate `entity`, `repository`, `service`, `controller`, `dto`, `exception`, and `config` responsibilities. Class names use PascalCase; methods and fields use camelCase.
2. JPA entities have a public no-argument constructor, private fields with getters/setters, generated `Long` IDs, and explicit column/relationship constraints. Repositories extend `JpaRepository<Entity, Long>`.
3. Services and controllers use constructor injection into final fields. Service mutations use `@Transactional`; read operations can use `@Transactional(readOnly = true)`.
4. Request/response DTOs are Java records. Controller request bodies use `@Valid`; request-record fields carry Jakarta validation annotations. Borrowing responses use a static `from` mapper and flat related-resource IDs.
5. Missing resources throw `ResourceNotFoundException`; domain conflicts throw `IllegalStateException`. The shared advice translates them into `ProblemDetail` responses with 404 and 409 respectively.
6. Time-dependent services receive `Clock` and use `LocalDate.now(clock)`. Integration tests use separate H2 database names, fixed-clock fixtures where dates matter, and either transactional rollback or explicit committed-fixture cleanup.

### Requirements references

The structure follows the Lab 3 assignment, PDF pp. 11-13. Requirement IDs and acceptance criteria support traceability and testable wording as described in *Chapter 3: Needs Expression and Specification Techniques*, PDF pp. 30-39; functional behavior is separated from implementation constraints following PDF pp. 7-10.
