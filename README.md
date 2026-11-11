# library-api

A Spring Boot REST API for books, members, borrowing transactions, and reservations.
Books can be borrowed for 14 days, returned with an overdue fine of EUR 0.50 per
day, and queried through each member's active borrowings. Reservations record
interest in unavailable books and retain cancellation history. All fourteen API
operations have OpenAPI documentation, and a multi-stage Docker build packages the
application with a Java 17 runtime.

## Requirements and build

- JDK 17 or newer (the project targets Java 17; JDK 21 can build and run it).
- Maven 3.6.3 or newer.
- Spring Boot 3.5.16 and SpringDoc 2.10.0.
- Maven coordinates: `com.library:library-api:0.0.1-SNAPSHOT`.
- Base package: `com.library.api`.

Run these commands from the repository root, where `pom.xml` lives:

```powershell
java -version
mvn -version
mvn clean verify
```

The build compiles with `--release 17` and runs the original seed-count smoke tests,
fine-calculation unit tests, service persistence tests, MockMvc API tests, and
transaction-contention tests for borrowing and reservations.
Separate in-memory databases isolate each integration test suite from the running
application and from the original five-book, three-member seed checks.

## Run

```powershell
mvn spring-boot:run
```

Or run the packaged application after building:

```powershell
java -jar target/library-api-0.0.1-SNAPSHOT.jar
```

The server listens on `http://localhost:8080`. Stop it with Ctrl+C.
Hibernate creates the schema before `data.sql` seeds five books and three members.
All data is in memory and resets when the application restarts.

## Check initial data

Check these counts before sending requests that create, update, or delete data.
Restart the application to restore the initial seed data before checking counts again.

```powershell
$books = @(Invoke-RestMethod http://localhost:8080/books)
$members = @(Invoke-RestMethod http://localhost:8080/members)
$books.Count   # 5
$members.Count # 3
$books | ConvertTo-Json
$members | ConvertTo-Json
Invoke-RestMethod http://localhost:8080/books/1
Invoke-RestMethod http://localhost:8080/members/1
```

Lists are ordered by ID. Book JSON fields are `id`, `isbn`, `title`, `author`,
`genre`, `publishedYear`, and `available`. Member fields are `id`, `name`,
`email`, `membershipDate`, and `active`. Dates use ISO format `yyyy-MM-dd`.

## API

| Method | Path | Success |
| --- | --- | --- |
| GET | /books | 200 with array |
| GET | /books/{id} | 200 with book |
| POST | /books | 201 with book and Location header |
| PUT | /books/{id} | 200 with updated book |
| DELETE | /books/{id} | 204 |
| GET | /members | 200 with array |
| GET | /members/{id} | 200 with member |
| POST | /members | 201 with member and Location header |
| POST | /borrowings | 201 with borrowing |
| PUT | /borrowings/{id}/return | 200 with returned borrowing and fine |
| GET | /members/{id}/borrowings | 200 with active borrowings ordered by ID |
| POST | /reservations | 201 with active reservation |
| GET | /members/{memberId}/reservations | 200 with all reservation history ordered by ID |
| PUT | /reservations/{id}/cancel | 200 with retained cancelled reservation |

Missing records return 404. Validation failures or malformed requests return 400;
validation responses include field errors. Duplicate ISBN/email or foreign-key
conflicts return 409. Borrowing an unavailable book, repeated returns, and attempts to make a
book available while it has an active loan also return 409. Error responses use
Spring ProblemDetail JSON with a consistent `error` message and UTC `timestamp`.
The existing `detail` repeats the public message, `status` matches the HTTP status,
and invalid fields appear in `errors`. Unexpected failures return a generic 500
message without exposing internal exception details.

ISBN, title, author, genre, name, and email must be nonblank; email must be valid.
Publication year is a required positive integer. ISBN and member email are unique.
Each new resource's own `id` is database-generated. Borrowing and reservation
requests supply the existing `bookId` and `memberId` they refer to.
Omitted `available` and `active` default to true; omitted `membershipDate` defaults
to today. PUT replaces all editable book fields and requires the same fields as POST.
For a book with an active borrowing, PUT must explicitly keep `available` false;
return the borrowing to make that book available again.

After checking the initial counts, try:

```powershell
$bookBody = @{
    isbn = '9780134685991'
    title = 'Effective Java'
    author = 'Joshua Bloch'
    genre = 'Programming'
    publishedYear = 2018
} | ConvertTo-Json
$book = Invoke-RestMethod http://localhost:8080/books -Method Post -ContentType 'application/json' -Body $bookBody

$updatedBody = @{
    isbn = $book.isbn
    title = 'Effective Java (Updated)'
    author = $book.author
    genre = $book.genre
    publishedYear = $book.publishedYear
    available = $false
} | ConvertTo-Json
Invoke-RestMethod "http://localhost:8080/books/$($book.id)" -Method Put -ContentType 'application/json' -Body $updatedBody
Invoke-RestMethod "http://localhost:8080/books/$($book.id)" -Method Delete

$memberBody = @{ name = 'Samir Saleh'; email = 'samir@example.com' } | ConvertTo-Json
Invoke-RestMethod http://localhost:8080/members -Method Post -ContentType 'application/json' -Body $memberBody
```

## Borrowing lifecycle

`POST /borrowings` accepts positive integer `bookId` and `memberId` values:

```json
{"bookId": 1, "memberId": 1}
```

Borrowing responses contain `id`, `bookId`, `memberId`, `borrowDate`, `dueDate`,
`returnDate`, and `fineAmount`. Dates follow the server's local calendar day.
Early and on-due-date returns have a fine of 0.00. Each overdue day adds 0.50.
Returned borrowings remain stored but are excluded from the active list.

After a fresh startup, the seeded book and member with ID 1 can be used in this
example. Use the borrowing ID from the POST response for the return request:

```sh
curl -i http://localhost:8080/borrowings \
  -H 'Content-Type: application/json' \
  -d '{"bookId":1,"memberId":1}'
curl http://localhost:8080/members/1/borrowings
curl -i -X PUT http://localhost:8080/borrowings/1/return
curl http://localhost:8080/books/1
```

Borrow and return operations update the borrowing and book in one transaction.
Book-row locks coordinate borrowing, returns, and book edits. A borrowing-row lock
also prevents concurrent return requests from updating the same record twice.

## Reservations

The [reservation specification](docs/AGENTS.md) defines the business rules and
acceptance criteria. Create a reservation with the same positive `bookId` and
`memberId` request fields used for borrowing. The book must be unavailable;
otherwise the API returns 409 with `detail` equal to
`Book is available for direct borrowing.`

Any existing member can reserve an unavailable book, including an inactive member
or the current borrower. Each book/member pair can have only one active reservation,
while different members can reserve the same book. Duplicate active requests return
409. Unknown creation fields are ignored; the reservation ID, status, and dates are
set by the server.

Responses contain `id`, `bookId`, `memberId`, `reservationDate`, `status`, and
`cancelledDate`. A new reservation is `ACTIVE` with a null cancellation date.
Cancelling sets `CANCELLED` and the current local date while retaining the record.
Repeated cancellation returns 409. A later request for the same pair gets a new ID
if the book is still unavailable, so multiple cancelled historical records can exist.

The member reservation endpoint returns both statuses in ascending ID order;
a member with no history receives `[]`. Returning a book leaves its reservations
active until explicitly cancelled. Reservations introduce no automatic allocation,
notification, expiry, queue priority, or restriction on the existing borrowing API.

## H2 and Swagger UI

Open `http://localhost:8080/h2-console` while the application is running:

- Driver: `org.h2.Driver`
- JDBC URL: `jdbc:h2:mem:librarydb;DB_CLOSE_DELAY=-1`
- User: `sa`
- Password: leave blank

After a fresh startup, run:

```sql
SELECT COUNT(*) FROM books;      -- 5
SELECT COUNT(*) FROM members;    -- 3
SELECT COUNT(*) FROM borrowings; -- 0
SELECT COUNT(*) FROM reservations; -- 0
```

Swagger UI is at `http://localhost:8080/swagger-ui.html` (redirects to the UI).
Confirm all fourteen operations appear and use "Try it out" to exercise the API.
The generated OpenAPI document is at `http://localhost:8080/api-docs`.
The UI groups operations into Books, Members, Borrowings, and Reservations and
documents request/response fields, examples, validation, and error responses.

## Docker

A running Docker engine with BuildKit support is required. Run the tests before
building the image; the container build uses the assignment's `-DskipTests` option:

```sh
mvn clean verify
docker build --platform linux/amd64 -t library-api:lab4 .
docker run --rm --platform linux/amd64 --name library-api -p 127.0.0.1:8080:8080 library-api:lab4
```

The required `eclipse-temurin:17-jre-alpine` image was published for `amd64` at
verification time, as listed in the [official image manifest](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin).
The explicit platform also supports building and running this image on Apple
Silicon with Docker's emulation enabled. The Maven stage runs on the builder's
native platform and produces the portable Java 17 JAR.

From another terminal, check the container and stop it when finished:

```sh
curl http://localhost:8080/books
curl http://localhost:8080/api-docs
docker stop library-api
```

Swagger UI remains at `http://localhost:8080/swagger-ui.html`. The final image runs
as the `library` user with `-Xmx256m -Xms128m`, exposes port 8080, and contains the
JAR and JRE without Maven or source files. H2 data remains in memory and resets
with the application; this container does not provide persistent database storage.

The `.dockerignore` allowlist sends only the Maven file, source tree, and Docker
build files. Git history, reports, local notes, and host build output are excluded.
With the Colima profile used for local verification, run `colima start library-lab4`
and use `docker --context colima-library-lab4` in place of `docker` in the commands
above. `colima stop library-lab4` stops that local engine after use.

## Application layers

Book and member controllers use services for persistence and business rules, and
explicit mappers keep their DTO contracts separate from JPA entities. Borrowing
uses a narrow creation request and a flat `BorrowingDTO`; its mapper requires
resolved book/member associations when mapping an internal DTO back to an entity.
Reservation responses retain their flat IDs and existing lifecycle contract.
All controllers return DTOs, and repositories remain behind the service layer.

## Persistence structure

Entities use Long identity IDs and Jakarta Persistence. Borrowing references Book
and Member through required lazy ManyToOne relationships, without cascading deletes.
Dates use LocalDate; fineAmount uses BigDecimal stored as DECIMAL(10,2), initially
0.00. returnDate may be null. Reservation also uses required lazy Book/Member
relationships without cascades, stores its status as a string, and retains its
original reservation date when cancelled. Tables are books, members, borrowings,
and reservations.
