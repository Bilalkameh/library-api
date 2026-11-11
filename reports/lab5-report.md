# Lab 5 - Refactoring

Three small refactorings preserve the borrowing and reservation contracts: a named return-state helper, named loan-policy constants, and a dedicated reservation mapper. The unchanged regression suite passed **129 tests before the changes and 129 after each of the three steps**, with no failures, errors, or skipped tests. The final clean verification also built the executable application JAR.

The starting point is `lab-4`, commit `99c6500007006e2b0c06c761372794fbe5e294ab`. The initial review covers all 31 production Java files. The final implementation changes three existing Java files and adds one mapper. All 15 existing test/support files, entities, repositories, seed data, dependencies, and the Lab 3 specification remain unchanged.

## 1. Smell catalog and selection

The following is the complete initial Step B review, recorded before any production edits. It identifies six occurrences across four named smell types, with code evidence, severity ratings, proposed patterns, and reasons for selecting three. Its line numbers refer to the starting revision. The public fine calculation already exists; the review explains the corresponding adaptation to Step C.

<details>
<summary>Initial Step B analysis output</summary>

# Lab 5 initial smell review

Baseline: `lab-4`, commit `99c6500007006e2b0c06c761372794fbe5e294ab`. Scope: all **31 Java files** under `src/main/java`, including configuration, controllers, DTOs, entities, exception handling, mappers, repositories, and services. This review precedes the Lab 5 production edits. The baseline test run passed 129 tests with no failures, errors, or skipped tests.

Line references below refer to the baseline, not the later edited files. For the handout's Long Method threshold, physical lines are counted inclusively from the declaration through the closing brace, including blank lines. This is a screening convention, not a claim that every method above fifteen lines is difficult to understand. No high-severity smell or demonstrated behavior defect was found.

## Prioritized catalog

### S1. Magic Numbers — medium — selected

`src/main/java/com/library/api/service/BorrowingService.java`, lines 48 and 81:

```java
borrowing.setDueDate(today.plusDays(14));
return new BigDecimal("0.50").multiply(BigDecimal.valueOf(overdueDays));
```

These encode two library-policy values without names identifying the policy: default loan duration and daily fine rate. This is one smell occurrence spanning two related literals, not two different smell types. **Replace Magic Number with Symbolic Constant** using `private static final int DEFAULT_LOAN_DAYS = 14` and `private static final BigDecimal DAILY_FINE_RATE = new BigDecimal("0.50")` makes the assumptions explicit and gives each a named definition. Medium severity reflects their effect on dates and monetary results if maintained incorrectly; the current values are correct and occur once each in the business calculation. This does not introduce configuration or a new tariff policy.

### S2. Long Method — low — selected

`src/main/java/com/library/api/service/BorrowingService.java`, `returnBook`, lines 54–69: **16 physical lines**, comprising 14 body lines, of which 13 are nonblank. The distinct settlement block is at lines 64–67:

```java
LocalDate today = LocalDate.now(clock);
borrowing.setReturnDate(today);
borrowing.setFineAmount(calculateFine(borrowing.getDueDate(), today));
book.setAvailable(true);
```

The method crosses the handout's numerical threshold narrowly. It already has one coherent use case and is not highly complex. The small **Extract Method** improvement is to name this date/fine/availability state transition in a private helper, while keeping resource lookup, the duplicate-return guard, locking, and saving visible in the public operation. Low severity acknowledges that the present code is readable; the gain is a clearer distinction between transaction orchestration and settlement details, not a repaired algorithm.

### S3. Feature Envy — low — selected

`src/main/java/com/library/api/dto/ReservationResponse.java`, lines 20–24:

```java
public static ReservationResponse from(Reservation reservation) {
    return new ReservationResponse(reservation.getId(), reservation.getBook().getId(),
            reservation.getMember().getId(), reservation.getReservationDate(),
            reservation.getStatus(), reservation.getCancelledDate());
}
```

This static method obtains all input state from `Reservation` and its associated entities; it uses no existing response instance. Under the handout's definition this is a Feature Envy candidate, but such reads are intentional and normal in conversion code. The concrete issue is placement: this DTO imports the JPA `Reservation` type to perform conversion, while the other resource families already keep conversion in dedicated mappers. **Move Method** to `ReservationMapper.toDTO(Reservation)` removes that entity-conversion responsibility from the transport record and makes the mapping location consistent. Low severity is appropriate: the existing JSON is already flat and correct. Moving conversion into the JPA entity would create a dependency in the wrong direction and is not proposed.

### S4. Duplicate Code — low — deferred

`src/main/java/com/library/api/exception/GlobalExceptionHandler.java`, lines 69–70 and 76–77 contain the same statements in two framework overrides:

```java
return handleExceptionInternal(exception, problem(status, "Malformed request body or invalid parameter type"),
        headers, status, request);
```

The malformed-body and invalid-parameter paths intentionally share one response contract. **Extract Method** into a shared malformed-request response helper would let both overrides delegate to one definition of that contract. This is genuine duplication, but only two short statements; the separate override methods are required by the framework. Severity is low and the change is deferred because the selected mapper move addresses a clearer responsibility-placement inconsistency without adding an abstraction around a small handler expression.

### S5. Long Method — low — deferred

`src/main/java/com/library/api/service/BorrowingService.java`, `borrowBook`, lines 34–51: **18 physical lines**. It combines lookup/eligibility orchestration with this initialization block at lines 43–48:

```java
LocalDate today = LocalDate.now(clock);
Borrowing borrowing = new Borrowing();
borrowing.setBook(book);
borrowing.setMember(member);
borrowing.setBorrowDate(today);
borrowing.setDueDate(today.plusDays(14));
```

**Extract Method** could name creation of an initialized borrowing, leaving book locking, eligibility, availability, and saving in the application operation. This is a second occurrence of the Long Method type, not an additional distinct type. It is low severity because the method is still short and cohesive. Defer it rather than extracting every setter sequence merely because it crosses the screening threshold.

### S6. Long Method — low — deferred

`src/main/java/com/library/api/service/ReservationService.java`, `reserveBook`, lines 33–51: **19 physical lines**. The initialization block at lines 45–49 is:

```java
Reservation reservation = new Reservation();
reservation.setBook(book);
reservation.setMember(member);
reservation.setReservationDate(LocalDate.now(clock));
reservation.setStatus(ReservationStatus.ACTIVE);
```

**Extract Method** could name active-reservation initialization. As with S5, the method remains a cohesive application use case; preserving the visible order of book/member lookup, availability, and duplicate checks matters more than reducing its line count. Severity is low. Defer this additional extraction to keep the three selected changes bounded.

## Selection and implementation boundaries

The catalog contains six concrete occurrences across four named types: Magic Numbers, Long Method, Feature Envy, and Duplicate Code. The selected three are S2, S1, and S3, in that order, covering three distinct smell names and three transformations:

1. **Extract Method:** move only `returnBook`'s settlement statements into a private, intention-revealing helper. Keep `@Transactional` on the public method, preserve borrowing-lock then book-lock order, run the already-returned guard before any mutation, and retain the existing save operation. The helper executes inside the existing transaction.
2. **Replace Magic Number with Symbolic Constant:** introduce the two private class constants and replace only their corresponding business literals. Preserve `BigDecimal("0.50")`, its scale, the zero clamp, and the fourteen-day calculation exactly.
3. **Move Method:** create the small `ReservationMapper` component, move the existing conversion body unchanged to `toDTO`, remove `ReservationResponse.from` and its `Reservation` import, and inject the mapper into `ReservationController`. Update create, history-list, and cancel call sites. Retain the DTO's field names, types, schema annotations, enum, and null semantics. Add no reverse mapper or persistence behavior that this change does not need.

Run the complete existing `mvn test` suite after each transformation before starting the next one. Record the individual observed results and diffs. A failed step must be diagnosed before continuing; a final combined green result alone would not establish the required sequence.

## Handout discrepancy

Step C assumes an inline fine expression in `returnBook` and requests extraction into a private `calculateFine`. The actual baseline already has a public `calculateFine(LocalDate, LocalDate)` at `BorrowingService.java:79–82`, introduced in Lab 2 and called directly by existing unit tests. Its use in `returnBook` is already visible at line 66. Making it private would break the established Java contract, and inlining it merely to extract it again would manufacture a refactoring. The selected settlement extraction performs the required Extract Method transformation on code that really remains inline, while preserving the existing fine method unchanged.

## Findings not asserted

The audit did not establish a God Class or Inappropriate Intimacy: controllers already delegate, repositories are behind services, and entity access uses public accessors. Plain identifiers and a small number of dates alone do not justify a Primitive Obsession diagnosis or a new value-object hierarchy. JPA accessors, schema bounds, status codes, schema examples, and the zero boundary in `Math.max` are not automatically harmful magic numbers. Existing mapper classes are not reported as defective merely because mapping reads source-object data.

These findings describe the current code after earlier design work. They do not support a causal claim that its origin introduced more debt than a careful human implementation would have introduced; there is no controlled comparison. The evidence supports limited preventive maintenance: clarify two policy values, name a state transition, and place one conversion method consistently.

## Course basis

The supplied *SE Lab Assignment 2026–2027 Codex*, PDF pp. 17–20, defines the catalog, named smells, three transformations, and before/after test requirement. *MyChap5_Refactoring_2627_EN 1 (2).pdf*, p. 2 defines behavior-preserving internal change; pp. 14–15 warn about metric false positives and call for cost/gain/risk assessment; p. 72 names Extract Method and Move Method; p. 89 requires an automated test suite; pp. 94–95 describe extraction and immediate compilation/testing; p. 100 illustrates moving a method according to its data dependencies; p. 143 emphasizes small changes followed by tests. PDF page numbers are used rather than the slides' printed counters.

</details>

## 2. Refactoring log

The diffs below compare consecutive tested source snapshots. They show each transformation independently, in execution order, including the new mapper file. No test assertion or business rule was changed to obtain a passing result.

### 2.1 Extract Method: return settlement

**Smell:** S2, Long Method, low severity. **Pattern:** Extract Method.

The four existing date/fine/availability statements move into private `applyReturn(Borrowing, Book)`. `returnBook` keeps its borrowing lock, duplicate-return guard, book lookup and lock, helper call, and save in the original order. The clock is still read once after both locks are acquired. The private helper runs inside the existing public method's transaction; it needs no separate transactional annotation.

`returnBook` shrinks from 16 to 13 physical lines under the catalog's inclusive counting convention. The entire class becomes slightly longer because the helper needs a declaration and braces. The gain is that transaction orchestration now names the state transition, making future review of the guard/lock/save sequence easier. The improvement is modest and introduces one extra method to follow.

The fine calculation already existed as a public, directly tested method introduced in Lab 2. Its signature and behavior are retained. The genuine new extraction concerns the settlement statements still inline in `returnBook`; no code was first inlined to manufacture a before/after example.

```diff
--- a/src/main/java/com/library/api/service/BorrowingService.java
+++ b/src/main/java/com/library/api/service/BorrowingService.java
@@ -64,4 +64 @@
-        LocalDate today = LocalDate.now(clock);
-        borrowing.setReturnDate(today);
-        borrowing.setFineAmount(calculateFine(borrowing.getDueDate(), today));
-        book.setAvailable(true);
+        applyReturn(borrowing, book);
@@ -82,0 +80,7 @@
+
+    private void applyReturn(Borrowing borrowing, Book book) {
+        LocalDate today = LocalDate.now(clock);
+        borrowing.setReturnDate(today);
+        borrowing.setFineAmount(calculateFine(borrowing.getDueDate(), today));
+        book.setAvailable(true);
+    }
```

### 2.2 Replace Magic Number with Symbolic Constant

**Smell:** S1, Magic Numbers, medium severity. **Pattern:** Replace Magic Number with Symbolic Constant.

`DAILY_FINE_RATE` and `DEFAULT_LOAN_DAYS` are private `static final` policy definitions. Their values remain `new BigDecimal("0.50")` and `14`. The calculation still clamps negative overdue days to zero, multiplies with exact decimal arithmetic, and produces scale-two fines. The loan still ends fourteen calendar days after borrowing.

The two values already appeared once each in the business calculation; this change names their purpose rather than claiming to remove repeated calculations. A future policy edit now has a clearly identified definition. Documentation and independent test expectations still require deliberate review when policy changes. Test oracles retain literal expected dates and amounts, so an accidental wrong production constant would remain detectable.

```diff
--- a/src/main/java/com/library/api/service/BorrowingService.java
+++ b/src/main/java/com/library/api/service/BorrowingService.java
@@ -19,0 +20,3 @@
+    private static final BigDecimal DAILY_FINE_RATE = new BigDecimal("0.50");
+    private static final int DEFAULT_LOAN_DAYS = 14;
+
@@ -48 +51 @@
-        borrowing.setDueDate(today.plusDays(14));
+        borrowing.setDueDate(today.plusDays(DEFAULT_LOAN_DAYS));
@@ -78 +81 @@
-        return new BigDecimal("0.50").multiply(BigDecimal.valueOf(overdueDays));
+        return DAILY_FINE_RATE.multiply(BigDecimal.valueOf(overdueDays));
```

### 2.3 Move Method: reservation response conversion

**Smell:** S3, qualified Feature Envy / misplaced conversion, low severity. **Pattern:** Move Method.

The existing conversion body moves from static `ReservationResponse.from` into `ReservationMapper.toDTO`. `ReservationController` receives the mapper through constructor injection and uses it for creation, member history, and cancellation. The six record components and all schema annotations remain identical. Related IDs remain flat, dates and enum values retain their meaning, and an active reservation still serializes a null cancellation date.

This removes the DTO's dependency on the JPA `Reservation` class and gives conversion the same location as book, member, and borrowing mapping. The shared `ReservationStatus` enum remains a DTO field; no claim is made that all dependencies on the entity package disappear. Reading entity getters is the mapper's intended job. The gain is responsibility placement and a predictable place for mapping changes, with the small cost of one additional component and constructor dependency. No repository access, reverse conversion, lifecycle change, or new endpoint is introduced.

```diff
--- a/src/main/java/com/library/api/controller/ReservationController.java
+++ b/src/main/java/com/library/api/controller/ReservationController.java
@@ -5,0 +6 @@
+import com.library.api.mapper.ReservationMapper;
@@ -25,0 +27 @@
+    private final ReservationMapper mapper;
@@ -27 +29 @@
-    public ReservationController(ReservationService reservationService) {
+    public ReservationController(ReservationService reservationService, ReservationMapper mapper) {
@@ -28,0 +31 @@
+        this.mapper = mapper;
@@ -43 +46 @@
-        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(
+        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDTO(
@@ -56 +59 @@
-                .map(ReservationResponse::from).toList();
+                .map(mapper::toDTO).toList();
@@ -69 +72 @@
-        return ReservationResponse.from(reservationService.cancelReservation(id));
+        return mapper.toDTO(reservationService.cancelReservation(id));
--- a/src/main/java/com/library/api/dto/ReservationResponse.java
+++ b/src/main/java/com/library/api/dto/ReservationResponse.java
@@ -3 +2,0 @@
-import com.library.api.entity.Reservation;
@@ -19,6 +17,0 @@
-
-    public static ReservationResponse from(Reservation reservation) {
-        return new ReservationResponse(reservation.getId(), reservation.getBook().getId(),
-                reservation.getMember().getId(), reservation.getReservationDate(),
-                reservation.getStatus(), reservation.getCancelledDate());
-    }
--- /dev/null
+++ b/src/main/java/com/library/api/mapper/ReservationMapper.java
@@ -0,0 +1,14 @@
+package com.library.api.mapper;
+
+import com.library.api.dto.ReservationResponse;
+import com.library.api.entity.Reservation;
+import org.springframework.stereotype.Component;
+
+@Component
+public class ReservationMapper {
+    public ReservationResponse toDTO(Reservation reservation) {
+        return new ReservationResponse(reservation.getId(), reservation.getBook().getId(),
+                reservation.getMember().getId(), reservation.getReservationDate(),
+                reservation.getStatus(), reservation.getCancelledDate());
+    }
+}
```

### Maintenance effects

| Refactoring | Before | After | Maintenance benefit and tradeoff |
| --- | --- | --- | --- |
| Extract Method | Four settlement statements are inline in a 16-line return operation. | A named helper contains those statements; the operation is 13 lines. | Makes guard/locking/persistence order easier to scan; adds one local method indirection. |
| Named constants | Loan duration and daily charge are unnamed business literals. | Two explicit policy constants define the same values. | Clarifies policy intent and where to edit it; related documentation and tests still need review for an intentional policy change. |
| Move Method | The response record both defines transport fields and converts a JPA entity. | The mapper converts; the record defines transport fields. | Aligns conversion responsibilities across resource families; adds one small component. |

No runtime-speed improvement or measured reduction in maintenance hours is claimed. The maintenance effects are structural observations and reasoned expectations; reduced line count alone is not treated as improved design.

## 3. Baseline and regression safety

All runs used Java **17.0.21.0.3** and Maven **3.10.0**. The first three commands execute the full Maven test suite. The final `clean verify` removes prior build output, recompiles production and test code, runs the same suite, packages the JAR, and reaches the verification phase. No skip-tests option was used.

| State tested | Command | Completed on 11 November 2026 (UTC) | Tests | Failures / errors / skipped | Pass rate | Exit |
| --- | --- | --- | ---: | --- | ---: | ---: |
| Unchanged Lab 4 baseline | `mvn -B -ntp test` | 19:17:29 | 129 | 0 / 0 / 0 | 100% | 0 |
| After Extract Method | `mvn -B -ntp test` | 19:22:11 | 129 | 0 / 0 / 0 | 100% | 0 |
| After named constants | `mvn -B -ntp test` | 19:22:47 | 129 | 0 / 0 / 0 | 100% | 0 |
| After Move Method; final verification | `mvn -B -ntp clean verify` | 19:23:52 | 129 | 0 / 0 / 0 | 100% | 0 |

The passing result after one step is the passing baseline before the next. Changes were applied one at a time, and each command completed before the next production edit. Source fingerprints were unchanged during every run. All original test/support files remain byte-for-byte identical to `lab-4`; the final suite contains fourteen test classes and the shared fixed-clock configuration.

The following existing assertions protect the specific changes:

| Transformation | Tests and established behavior | Accidental regression these checks would detect |
| --- | --- | --- |
| Extract Method | `ReturnServiceTest` checks early, on-time, and late returns, fixed local dates, exact persisted fines, fresh-read availability, retained loan dates, missing records, and repeated-return preservation. `BorrowingControllerTest` checks the HTTP return contract and an old repeated return after a later loan begins. | Omitting a settlement assignment, calculating against the wrong date, restoring availability incorrectly, changing persisted fine amounts, or mutating state before the duplicate-return guard. |
| Named constants | `FineCalculationTest` expects 0.00 before/on the due date, 0.50 after one day, and 3.50 after seven days; equality checks the decimal scale and the test forbids repository/clock interactions. The borrowing service test bounds the real-clock start date and checks the persisted fourteen-day offset; the HTTP test checks the same offset from a fixed date. | A different loan duration, rate, negative fine, decimal scale, or loss of the fine method's pure calculation contract. |
| Move Method | The 33 `ReservationControllerTest` cases exercise all three routes, exact creation fields, distinct related IDs, original/cancellation dates, ACTIVE/CANCELLED values, nulls, complete ordered member history, and empty lists. The 18 `OpenApiDocumentationTest` cases protect operation/schema contracts. | Missing mapper wiring or a missed call site, switched IDs, omitted fields, incorrect enum/date/null mapping, or changed generated API schemas. |
| Cross-feature safety | Borrowing and reservation contention tests exercise overlapping transactions and inspect committed state. The full suite also includes catalogue operations, active loans, error responses, and initial seed checks. | Regressions in the exercised locking outcomes or neighboring API behavior. The lock-acquisition order itself is additionally checked in the source diff. |

The unchanged suite is evidence for the covered contracts and concurrency scenarios, rather than an exhaustive proof over every possible input or database scheduler. No new test that merely repeats the helper's assignments or mapper getters was added. No failure, rollback, assertion weakening, or corrective production edit was needed during the three transformations.

## 4. AI-generated debt and preventive maintenance

The reviewed code contains limited, concrete maintenance debt: two unnamed policy values, a small return method that can express its settlement intent more clearly, and a reservation conversion method placed differently from the other resource families. The duplicated malformed-request response and two other methods above the handout's line threshold remain documented candidates. Their low severity does not justify adding more abstraction solely to make every catalog entry disappear.

Relative to the standard of a careful developer's review, the policy naming and conversion inconsistency are avoidable. The evidence does **not** establish that AI-assisted implementation produced more total debt than a careful human implementation: there is no comparable human baseline, controlled experiment, or maintenance-cost measurement. The existing separation of services/controllers, exact monetary tests, deterministic clock, DTO contracts, and contention checks show useful structure already present after the earlier labs. It would be inaccurate to label the code a God Class or describe the existing fine calculation method as an outstanding inline algorithm.

The handout discrepancy is itself a concrete review lesson. Applying the suggested prompt literally would remove a public method that the established tests call. Inspecting the actual source first allowed a real extraction while preserving that contract. Automated suggestions require the same assessment of location, cost, benefit, and risk as any proposed change.

These changes are preventive maintenance: name policy before tariff/loan rules grow, make settlement intent visible before more return rules accumulate, and keep conversion in a predictable layer before response mappings expand. Their value comes from clearer responsibilities and preserved tests. Moving the reservation conversion does not eliminate its necessary getter reads, and a 16-line method is not evidence of a serious design failure by itself.

## 5. Course connections and completion

The supplied *Chapter 5: Refactoring* defines the goal as improved internal structure with unchanged external behavior (PDF p. 2). Its analysis guidance warns that numerical thresholds can produce false positives and asks for cost/gain/risk assessment (pp. 14-15); this supports the catalog's restrained severity ratings. The three changes follow the recommended test-before-change and test-after-each-unit rhythm (pp. 18-19 and 143), and use the method-composition/movement techniques discussed on pp. 72, 94-95, and 100. Page 21 connects readability, cohesion, duplication, and separation of concerns with maintainability. PDF page numbers refer to *MyChap5_Refactoring_2627_EN 1 (2).pdf*, rather than its printed slide counters.

The Lab 5 requirements in *SE Lab Assignment 2026-2027 Codex*, PDF pp. 17-20, are covered by the initial catalog, three independent transformations with passing before/after tests, their complete diffs, the maintenance analysis, and the regression-safety explanation. The final application retains the same fourteen HTTP operations and public behavior covered by those tests.
