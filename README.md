# library-api

Software Engineering Lab 1: a Spring Boot REST API scaffold for books and members.
Borrowing has a JPA entity and repository; borrowing operations, reservations,
Docker, and static analysis are reserved for later labs.

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

The build compiles with `--release 17` and runs two MockMvc smoke tests:
GET /books returns 200 with five books; GET /members returns 200 with three members.
Tests use a separate in-memory database named `libraryapitest` in the test JVM,
independent of requests sent to the running application.

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

Missing records return 404. Validation failures or malformed requests return 400;
validation responses include field errors. Duplicate ISBN/email or foreign-key
conflicts return 409. Error responses use Spring ProblemDetail JSON.

ISBN, title, author, genre, name, and email must be nonblank; email must be valid.
Publication year is a required positive integer. ISBN and member email are unique.
IDs are database-generated and are not part of request DTOs.
Omitted `available` and `active` default to true; omitted `membershipDate` defaults
to today. PUT replaces all editable book fields and requires the same fields as POST.

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
```

Swagger UI is at `http://localhost:8080/swagger-ui.html` (redirects to the UI).
Confirm all eight operations appear and use "Try it out" to exercise the API.
The generated OpenAPI document is at `http://localhost:8080/v3/api-docs`.

## Persistence structure

Entities use Long identity IDs and Jakarta Persistence. Borrowing references Book
and Member through required lazy ManyToOne relationships, without cascading deletes.
Dates use LocalDate; fineAmount uses BigDecimal stored as DECIMAL(10,2), initially
0.00. returnDate may be null. Tables are books, members, and borrowings.
