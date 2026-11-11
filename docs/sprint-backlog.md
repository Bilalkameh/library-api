# Sprint 1 - Borrowing

- Planning date: 11 November 2026.
- Starting milestone: `lab-1`, commit `1935e14747819734c2b81098971e8e45c81a1acc`.
- Scope: borrowing, returns, active-borrowing queries, and overdue fines.

## Initial estimates

Estimates cover implementation and testing for each story. Environment setup, report preparation, and the final review are outside this total. The original estimates are retained for comparison with the completed sprint.

All four stories passed verification on 11 November 2026.

| ID | User story | Estimate (h) | Order | Status |
| --- | --- | ---: | ---: | --- |
| US-01 | As a librarian, I can record a borrowing so that the system tracks which member has which book. | 0.75 | 1 | DONE |
| US-02 | As a librarian, I can record a book return so that the book becomes available again. | 0.50 | 3 | DONE |
| US-03 | As a member, I can see my active borrowings so that I know which books I currently have. | 0.50 | 4 | DONE |
| US-04 | As a librarian, I can calculate the fine for an overdue book so that I can charge the right amount, at EUR 0.50 per overdue day. | 0.25 | 2 | DONE |
| **Total** | | **2.00** | | |

US-01 establishes borrowing records and book availability. US-04 follows because returns depend on fine calculation. US-02 completes the borrowing lifecycle, and US-03 queries the resulting active records.

## Tasks and acceptance criteria

| Story | Implementation tasks | Acceptance criteria |
| --- | --- | --- |
| US-01 | Implement `borrowBook(Long bookId, Long memberId)` and `POST /borrowings`. | Persist the borrowing, set the book unavailable, use today's borrow date and a due date 14 days later; reject unavailable books and missing records. |
| US-04 | Implement pure `calculateFine(LocalDate dueDate, LocalDate returnDate)` using `BigDecimal`. | Early and on-due-date returns cost 0.00; one overdue day costs 0.50; seven overdue days cost 3.50. |
| US-02 | Implement `returnBook(Long borrowingId)` and `PUT /borrowings/{id}/return`. | Persist today's return date and the fine; restore availability atomically; reject missing borrowings and repeated returns. |
| US-03 | Implement `GET /members/{id}/borrowings`. | Return only the requested member's unreturned borrowings; exclude returned records and other members' records; return an empty list for a member with none and 404 for a missing member. |

## Verification

- Write a test before each story's implementation and record its observed RED/GREEN result.
- Verify saved state through fresh database reads and keep fixtures isolated from the original seed-count tests.
- Run the relevant tests after each change and the complete regression suite before finishing the sprint.
- Review error handling, edge cases, test coverage, readability, and XP practices; apply at least two substantive review suggestions.

## Initial test-first results

| Check | Command | Observed result |
| --- | --- | --- |
| Unchanged Lab 1 baseline | `mvn -B -ntp test` | Exit 0; 2 tests passed, with no failures, errors, or skipped tests. |
| First borrowing test before implementation | `mvn -B -ntp -Dtest=BorrowingServiceTest test` | Exit 1 during test compilation because `BorrowingService` did not exist. No test methods or assertions executed. |

Both checks ran with Java 17.0.21.0.3 and Maven 3.10.0. The first test was committed in `cdb4624` before production borrowing behavior was implemented. It checks persisted availability and borrowing dates after flushing and clearing the persistence context.

## Completion results

- `mvn -B -ntp clean verify`: 34 tests passed; no failures, errors, or skipped tests. The executable application JAR was built successfully.
- Fine tests verified exact amounts of 0.00, 0.50, and 3.50; return tests also verified a three-day overdue fine of 1.50.
- HTTP tests verified creation, returns, active-list filtering, validation, missing records, duplicate operations, and book-edit availability protection.
- Review changes reject fractional identifiers, assert fixed local dates in responses and persisted records, and exercise competing transactions against H2.
- The [Lab 2 report](../reports/lab2-report.md) records the test-first sequence, review output, decisions, and verification details.
