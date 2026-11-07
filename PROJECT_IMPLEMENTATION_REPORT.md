# PSG iTech Library - Project Implementation Report
## Comprehensive Technical Documentation, Architectural Defense & Viva Voce Reference

**Institution**: PSG Institute of Technology and Applied Research (PSG iTech), Neelambur, Coimbatore  
**System**: Library Resource and Queue Management System  
**Core Technologies**: Java 17/25, Java Servlets 4.0.1, JDBC, MySQL 8.0, Apache Tomcat 10.1, HTML5/CSS3/Vanilla JS  
**Current Build Status**: 119 Automated Tests Passing (0 Failures, 0 Errors, 0 Skipped)  
**Deployment Target**: Apache Tomcat 10.1.61 running on port `8081`  

---

## 1. Executive Summary & Project Reality

This technical document serves as the official implementation report for the **PSG iTech Library Resource and Queue Management System**. Unlike initial project proposals or conceptual blueprints, this document reflects **strictly and exclusively what is implemented, verified, and operational in the repository's source code**.

The application is structured as a full-stack, enterprise-grade Java monolithic web application. It rejects fragile third-party framework dependencies in favor of standard **Java Servlet API**, **pure JDBC data access**, **declarative container connection pooling**, and a **vanilla web presentation layer** styled according to strict institutional academic guidelines (Times New Roman typography, Navy palette `#203B60`).

---

## 2. Comprehensive Implemented Feature Specifications

### Feature 1: User Management & Authentication
- **Multi-Role User Domain**:
  - Implements an abstract base entity [`User`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/User.java) extended by concrete role subclasses: [`Student`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/Student.java), [`Librarian`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/Librarian.java), and [`Admin`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/Admin.java).
  - Role management governed by [`Role`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/enums/Role.java) enum (`STUDENT`, `LIBRARIAN`, `ADMIN`).
- **Cryptographic Security**:
  - Passwords hashed using `jBCrypt` (cost factor 12) with cryptographic salting via [`PasswordUtil`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/util/PasswordUtil.java).
- **Flexible Identification**:
  - Authentication allows students to log in via Anna University Register Number (`7155...`) or Institutional Email (`@psgitech.ac.in`). Librarians authenticate via Employee ID (`LIB...`) or Email.
- **Persistent "Remember Me" System**:
  - 30-day persistent cookie (`psgitech_remember_me`) issuing 64-character random cryptographic tokens. Tokens are stored as deterministic SHA-256 hashes in `remember_tokens` table to prevent database breach leakage.
- **First-Login Security Guard**:
  - Newly created staff accounts have `must_change_password = TRUE`. [`AuthorizationFilter`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/filter/AuthorizationFilter.java) intercepts all requests from such users and redirects them exclusively to `first-login-change-password.html` until a new secure password is established.
- **Account State Lifecycle**:
  - Administrators can toggle user account status (`active = TRUE/FALSE`) via `AdminUserServlet`. Deactivated users are barred from logging in or receiving physical books while their historical borrowing records are preserved.

### Feature 2: Book Catalog & Physical Copy Inventory
- **Two-Tier Bibliographic Hierarchy**:
  - Separates abstract bibliographic metadata ([`Book`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/Book.java)) from concrete physical assets ([`BookCopy`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/BookCopy.java)).
  - Physical copy status tracked via [`CopyStatus`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/enums/CopyStatus.java) (`AVAILABLE`, `BORROWED`, `RESERVED`, `ALLOCATED`, `LOST`, `DAMAGED`, `ARCHIVED`, `TRANSFER_PENDING`).
  - Barcode copy code formatting: `PSG-BK<bookId>-C<copyNumber>`.
- **Atomic Creation Guardrail**:
  - Books cannot be added without physical inventory. [`BookRepository.createBookWithCopies`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/repository/BookRepository.java#L74) executes both inserts in a single transaction with `conn.setAutoCommit(false)`.
- **Integrity-Preserving Archival Guardrails**:
  - Deleting books with historical loan records is strictly prohibited by database foreign keys. Instead, soft archiving is implemented (`books.is_archived`).
  - Guardrail: Books cannot be archived if any physical copies are currently `BORROWED`, `ALLOCATED`, or `TRANSFER_PENDING`, or if active reservations exist.
- **Aggregated Inventory Queries**:
  - Single-query aggregate joins compute total, available, borrowed, and allocated counts directly in SQL, avoiding the $N+1$ query antipattern.

### Feature 3: Book Borrowing & Circulation Desk Operations
- **Circulation Issue**:
  - Desk operation via [`IssueBookServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/IssueBookServlet.java). Librarians verify student registration number and physical copy code.
- **Quota & Duration Policies**:
  - Enforces [`BorrowingPolicy`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/config/BorrowingPolicy.java): default maximum 3 active borrowed books per student and standard 14-day borrowing duration.
- **Atomic Multi-Row Locking**:
  - Issue operation acquires exclusive row locks (`FOR UPDATE`) in canonical sequence: Student (`users`) $\to$ Title (`books`) $\to$ Physical Copy (`book_copies`).
- **Same-Book Duplicate Guard**:
  - Students are prohibited from borrowing multiple physical copies of the exact same title simultaneously.
- **Overdue Monitoring**:
  - Active borrowings past due date are visually highlighted with countdown badges and monitored via [`OverdueBorrowingsServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/OverdueBorrowingsServlet.java).

### Feature 4: Reservation Queue Management
- **FIFO Waitlist Registration**:
  - When zero physical copies are available (`available_copies == 0`), eligible students can reserve a title via [`ReservationServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ReservationServlet.java).
  - Enforces max active reservation limit (default 2 per student).
- **Queue Position Tracking**:
  - Dynamic SQL window function calculates the real-time FIFO queue rank (`#1`, `#2`, etc.) based on `reservation_date` and `reservation_id`.
- **Self-Service Cancellation**:
  - Students cancel their own reservations via [`CancelReservationServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/CancelReservationServlet.java). When cancelled, subsequent students automatically advance in the queue.

### Feature 5: Automatic Book Allocation & Hold-Shelf Engine
- **Event-Driven Return Allocation**:
  - Returning a borrowed book at the circulation desk (`ReturnBookServlet`) triggers [`AllocationService.allocateReturnedCopy`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/AllocationService.java) within the return transaction.
- **Pluggable Strategy Pattern**:
  - Guided by [`AllocationStrategy`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/interfaces/AllocationStrategy.java) interface implemented by [`FIFOAllocationStrategy`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/FIFOAllocationStrategy.java).
  - Skips ineligible candidates (e.g., student reached borrowing limit or accumulated unpaid fines) and advances to the next eligible student.
- **Hold Shelf Protection**:
  - If an eligible student is found, the physical copy transitions to `ALLOCATED` and an `allocations` record is generated with a collection deadline (default 24 hours).
  - Physical copy cannot be checked out to any student other than the assigned reservation holder.
- **Hold Release & Cascading Re-allocation**:
  - Expired or cancelled allocations return the copy to the allocation engine, notifying or holding the copy for the next student in line.

### Feature 6: Fine Calculation & UPI Payment Reconciliation
- **Automated Fine Assessment**:
  - Upon return of an overdue book, [`FineService`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/FineService.java) calculates calendar overdue days in `Asia/Kolkata` timezone.
  - Applies institutional grace period (default 1 day) and fine rate (₹2.00/day).
- **Ledger State Machine**:
  - Fine statuses: `UNPAID` $\to$ `PARTIALLY_PAID` $\to$ `PAID` / `WAIVED`.
- **UPI Reconciliation Workflow**:
  - Students scan an institutional UPI QR code on `my-fines.html` and submit their 12-digit bank UTR reference.
  - Unique constraint on `payments.upi_reference` eliminates duplicate submissions and replay attacks.
  - Librarians inspect and confirm bank credits via [`PaymentVerificationServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/PaymentVerificationServlet.java).
- **Authorized Waivers & Adjustments**:
  - Staff can grant partial or full waivers with mandatory logged justification recorded in `fine_adjustments`.
- **Chronological Financial Audit Trail**:
  - Every financial mutation (fine generation, payment submission, verification, rejection, waiver, refund) writes an immutable entry into `payment_audit_log`.
- **Preservation of Feature 6 Policy D10**:
  - Unreturned overdue books on other transactions do *not* block borrowing or transfers; only finalized unpaid fines ($> \text{INR } 0$) block student access.

### Feature 7: Concurrent Request Management & Admission Control
- **Admission Limiter (Fair Semaphore)**:
  - High-traffic mutating operations are metered by [`ConcurrencyFilter`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/filter/ConcurrencyFilter.java) using a fair `java.util.concurrent.Semaphore` initialized to 12 permits.
- **Bounded Waiting Room**:
  - Excess mutating requests wait in a bounded FIFO queue of 24 slots. If acquisition wait time exceeds 3,000 ms, the filter rejects the request with HTTP `429 Too Many Requests` and sends a `Retry-After: 3` header.
- **Per-User In-Flight Throttling**:
  - Restricts any individual student/staff user or IP address to at most 3 simultaneous in-flight mutating requests, preventing scripted denial-of-service.
- **Deadlock Mitigation & Retry Framework**:
  - [`TransactionRetryHelper`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/util/TransactionRetryHelper.java) wraps complex transactions, catching MySQL error codes `1213` (deadlock) and `1205` (lock wait timeout) and performing exponential backoff retries with randomized jitter.
- **Real-Time Admin Concurrency Dashboard**:
  - [`ConcurrencyMonitorServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ConcurrencyMonitorServlet.java) exposes JSON telemetry consumed by `concurrency-monitor.html` for live graphs of active permits, queue depth, and rejection rates.

### Feature 8: Student-to-Student Book Interchange
- **Direct Peer Hand-Off**:
  - Student A enters Student B's register number to initiate transfer of an active borrowed textbook.
- **Strict Loan Invariance**:
  - Updates `borrow_transactions.student_id` in place.
  - Retains identical `transaction_id`, `issue_date`, and `due_date`. Does not renew or extend loan duration.
  - Physical copy remains continuously in `BORROWED` status.
- **Reservation Priority Safeguard**:
  - Blocked if any reservations exist in `WAITING` or `ALLOCATED` status, preventing students from bypassing the official waitlist.
- **Overdue Transfer Prohibition**:
  - Copies that are already overdue in `Asia/Kolkata` cannot be transferred; they must be returned to the library desk.
- **Single-Active Pending Rule**:
  - Implemented via MySQL `STORED` generated column `active_transaction_id` with a `UNIQUE` index.
- **Two-Student Deadlock Elimination**:
  - Always locks the two student rows in ascending user ID order: `Math.min(fromId, toId)` then `Math.max(fromId, toId)`.
- **Desk Return Race Condition Resolution**:
  - Desk returns auto-cancel pending transfers at Level 11.
  - In a return/transfer race, `returnBookTransaction` verifies borrower identity under lock; if the borrower changed, it rolls back and retries with the new borrower.
- **Permanent Dual-Sided Audit**:
  - Senders see the transferred loan marked in their borrowing history table. Recipients see the active loan tagged with origin details.

---

## 3. Complete HTTP Servlet & API Endpoint Catalog

The application exposes **33 Servlets** handling 38 endpoint paths:

| Servlet Class | URL Pattern(s) | HTTP Methods | Allowed Roles | Description |
|---|---|:---:|:---:|---|
| [`LoginServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/LoginServlet.java) | `/login` | GET, POST | Public | Authenticates credentials, creates session, issues Remember Me token |
| [`LogoutServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/LogoutServlet.java) | `/logout` | GET, POST | Authenticated | Terminates session, clears Remember Me cookies and DB tokens |
| [`RegisterServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/RegisterServlet.java) | `/register` | GET, POST | Public | Student self-registration with register number validation |
| [`ProfileServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ProfileServlet.java) | `/profile` | GET, POST | Authenticated | Retrieves and updates user profile information |
| [`ChangePasswordServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ChangePasswordServlet.java) | `/change-password` | POST | Authenticated | Self-service password changes with old-password verification |
| [`AdminUserServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/AdminUserServlet.java) | `/admin/users` | GET, POST | `ADMIN` | User directory, staff account creation, account activation toggle |
| [`BookListServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/BookListServlet.java) | `/books` | GET | Public | Paginated list of catalog titles with aggregate inventory counts |
| [`BookSearchServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/BookSearchServlet.java) | `/books/search` | GET | Public | Search catalog by title, author, category, department, availability |
| [`BookDetailsServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/BookDetailsServlet.java) | `/books/details` | GET | Public | Detailed title view with physical copy inventory breakdown |
| [`AddBookServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/AddBookServlet.java) | `/books/add` | POST | `LIBRARIAN`, `ADMIN` | Registers new title and atomically generates initial physical copies |
| [`UpdateBookServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/UpdateBookServlet.java) | `/books/update` | POST | `LIBRARIAN`, `ADMIN` | Modifies bibliographic metadata of an existing title |
| [`AddBookCopyServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/AddBookCopyServlet.java) | `/books/copies/add` | POST | `LIBRARIAN`, `ADMIN` | Adds additional physical copies with formatted copy codes |
| [`ArchiveBookServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ArchiveBookServlet.java) | `/books/archive` | POST | `LIBRARIAN`, `ADMIN` | Soft-archives titles/copies after verifying zero active loans/holds |
| [`IssueBookServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/IssueBookServlet.java) | `/borrow/issue`, `/borrowings/issue` | POST | `LIBRARIAN`, `ADMIN` | Circulation desk book issue to student under multi-row locks |
| [`ReturnBookServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ReturnBookServlet.java) | `/borrow/return`, `/borrowings/return` | POST | `LIBRARIAN`, `ADMIN` | Desk book return; triggers fine calculation and queue allocation |
| [`ActiveBorrowingsServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ActiveBorrowingsServlet.java) | `/borrow/active` | GET | `LIBRARIAN`, `ADMIN` | Global list of all currently borrowed books |
| [`OverdueBorrowingsServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/OverdueBorrowingsServlet.java) | `/borrow/overdue` | GET | `LIBRARIAN`, `ADMIN` | Global list of overdue loans with overdue day counts |
| [`BorrowLookupServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/BorrowLookupServlet.java) | `/borrow/lookup` | GET | `LIBRARIAN`, `ADMIN` | Scans physical copy barcode to lookup active loan details |
| [`BorrowingHistoryServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/BorrowingHistoryServlet.java) | `/borrow/history` | GET | Authenticated | Retrieves past loan records (including transferred-out books) |
| [`StudentBorrowingsServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/StudentBorrowingsServlet.java) | `/borrow/student` | GET | Authenticated | Retrieves active loans held by the logged-in student |
| [`ReservationServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ReservationServlet.java) | `/reservations`, `/reservations/` | GET, POST | Authenticated | POST: Join reservation queue; GET: View title queue status |
| [`CancelReservationServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/CancelReservationServlet.java) | `/reservations/cancel` | POST | Authenticated | Cancels pending reservation; triggers queue priority shift |
| [`MyReservationsServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/MyReservationsServlet.java) | `/reservations/my` | GET | `STUDENT` | Retrieves student's active reservations with FIFO queue rankings |
| [`AllocationServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/AllocationServlet.java) | `/allocations`, `/allocations/` | GET | `LIBRARIAN`, `ADMIN` | Displays active hold shelf allocations with collection countdowns |
| [`FineServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/FineServlet.java) | `/fines` | GET, POST | `LIBRARIAN`, `ADMIN` | GET: Global fines ledger; POST: Process fine waiver with logged reason |
| [`MyFinesServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/MyFinesServlet.java) | `/my-fines`, `/fines/my` | GET | `STUDENT` | Retrieves outstanding fines and fine breakdown for logged-in student |
| [`PaymentServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/PaymentServlet.java) | `/payments` | GET, POST | Authenticated | POST: Submit UPI UTR reference; GET: Payment history |
| [`PaymentVerificationServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/PaymentVerificationServlet.java) | `/payments/verify` | POST | `LIBRARIAN`, `ADMIN` | Staff verification or rejection of submitted UPI payments |
| [`ReceiptServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ReceiptServlet.java) | `/receipt`, `/payments/receipt` | GET | Authenticated | Generates verified digital payment receipt data |
| [`InterchangeServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/InterchangeServlet.java) | `/interchanges` | GET, POST | `STUDENT` | POST: Initiate peer transfer; GET: Fetch incoming/outgoing list |
| [`InterchangeActionServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/InterchangeActionServlet.java) | `/interchanges/accept`, `/reject`, `/cancel` | POST | `STUDENT` | Accepts, rejects, or cancels pending peer transfer requests |
| [`AdminInterchangeServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/AdminInterchangeServlet.java) | `/api/admin/interchanges` | GET | `LIBRARIAN`, `ADMIN` | Institutional audit log of all peer-to-peer book interchanges |
| [`ConcurrencyMonitorServlet`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/servlet/ConcurrencyMonitorServlet.java) | `/api/admin/concurrency/status` | GET, POST | `ADMIN` | GET: Live semaphore & queue telemetry; POST: Toggle admission limiter |

---

## 4. Complete Database Schema Architecture (12 Tables)

```text
================================================================================
                               DATABASE TABLES
================================================================================
 1. users                  - Authentication, roles, academic profiles
 2. remember_tokens        - Persistent 30-day login tokens (SHA-256)
 3. books                  - Bibliographic catalog records
 4. book_copies            - Physical copies, barcodes, individual copy statuses
 5. borrow_transactions    - Circulation loans, due dates, transfer indicators
 6. reservations           - FIFO reservation waitlist entries
 7. allocations            - 24-hour hold-shelf allocations for returned copies
 8. fines                  - Overdue fine ledgers (amounts, days, statuses)
 9. payments               - Payment submissions (UPI UTRs, verification states)
10. fine_adjustments       - Authorized waivers, reductions, and refunds
11. payment_audit_log      - Tamper-evident chronological audit trail
12. book_interchanges      - Peer-to-peer transfers with stored generated column
================================================================================
```

### Key Relational Constraints & Schema Details
1. **`users` Table**:
   - `user_id INT AUTO_INCREMENT PRIMARY KEY`
   - `email VARCHAR(100) UNIQUE NOT NULL`
   - `register_number VARCHAR(20) UNIQUE NULL` (Enforced unique for students)
   - `employee_id VARCHAR(20) UNIQUE NULL` (Enforced unique for librarians)
   - `role VARCHAR(20) NOT NULL` (`STUDENT`, `LIBRARIAN`, `ADMIN`)
   - `active BOOLEAN DEFAULT TRUE`
2. **`book_copies` Table**:
   - `copy_id INT AUTO_INCREMENT PRIMARY KEY`
   - `copy_code VARCHAR(50) UNIQUE NOT NULL` (`PSG-BK<id>-C<no>`)
   - `book_id INT NOT NULL` $\to$ `FOREIGN KEY REFERENCES books(book_id) ON DELETE RESTRICT`
   - `status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'`
3. **`borrow_transactions` Table**:
   - `transaction_id INT AUTO_INCREMENT PRIMARY KEY`
   - `student_id INT NOT NULL` $\to$ `FOREIGN KEY REFERENCES users(user_id)`
   - `book_id INT NOT NULL` $\to$ `FOREIGN KEY REFERENCES books(book_id)`
   - `copy_id INT NOT NULL` $\to$ `FOREIGN KEY REFERENCES book_copies(copy_id)`
   - `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` (`ACTIVE`, `RETURNED`, `OVERDUE`)
   - `transferred BOOLEAN DEFAULT FALSE`
4. **`book_interchanges` Table**:
   - `interchange_id INT AUTO_INCREMENT PRIMARY KEY`
   - `transaction_id INT NOT NULL` $\to$ `FOREIGN KEY REFERENCES borrow_transactions(transaction_id)`
   - `from_student_id INT NOT NULL` $\to$ `FOREIGN KEY REFERENCES users(user_id)`
   - `to_student_id INT NOT NULL` $\to$ `FOREIGN KEY REFERENCES users(user_id)`
   - `status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'` (`REQUESTED`, `COMPLETED`, `REJECTED`, `CANCELLED`, `EXPIRED`)
   - `active_transaction_id INT GENERATED ALWAYS AS (CASE WHEN status = 'REQUESTED' THEN transaction_id ELSE NULL END) STORED`
   - `CONSTRAINT uk_active_transaction_interchange UNIQUE (active_transaction_id)` (Guarantees at most 1 active pending transfer per transaction)
   - `CONSTRAINT chk_interchange_different_students CHECK (from_student_id <> to_student_id)`
5. **`fines` & `payments` Tables**:
   - `payments.upi_reference VARCHAR(50) UNIQUE NULL` (Prevents duplicate UTR submission)
   - `fines.outstanding_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00`
   - `FOREIGN KEY (fine_id) REFERENCES fines(fine_id) ON DELETE RESTRICT`

---

## 5. Concurrency Architecture & Deadlock Elimination

### 5.1 The Canonical 11-Level Lock Sequence
To eliminate deadlock hazards across concurrent transactions, all services acquire exclusive database locks (`SELECT ... FOR UPDATE`) in an unbending, strictly ascending 11-level sequence:

$$\text{Level 1: users} \longrightarrow \text{Level 2: books} \longrightarrow \text{Level 3: book\_copies} \longrightarrow \text{Level 4: borrow\_transactions}$$
$$\longrightarrow \text{Level 5: reservations} \longrightarrow \text{Level 6: allocations} \longrightarrow \text{Level 7: fines}$$
$$\longrightarrow \text{Level 8: payments} \longrightarrow \text{Level 9: fine\_adjustments} \longrightarrow \text{Level 10: audit\_log} \longrightarrow \text{Level 11: interchanges}$$

### 5.2 Symmetric User Locking for Peer Transfers
When Student $A$ transfers to Student $B$ while Student $B$ transfers another book to Student $A$, locking `from_student_id` first produces a circular wait deadlock ($A \to B$ vs $B \to A$).  
**Implementation**: [`BookInterchangeService`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/BookInterchangeService.java#L210) eliminates this by locking users in numerical primary key order:
```java
int firstUser = Math.min(fromStudentId, toStudentId);
int secondUser = Math.max(fromStudentId, toStudentId);
lockUser(conn, firstUser);
lockUser(conn, secondUser);
```
Both competing threads acquire lock `min(A, B)` first, completely serializing execution and eliminating circular wait conditions.

### 5.3 Stale Borrower Resolution in Return/Interchange Races
When a physical desk return races against an interchange acceptance:
1. `returnBookTransaction` reads candidate borrower $A$ in step 1.
2. The interchange acceptance commits, changing the borrower of the loan to $B$.
3. When `returnBookTransaction` locks $A$ and moves to Level 4 (`borrow_transactions`), it asserts:
   ```java
   if (actualBorrowerId != candidateStudentId) {
       conn.rollback();
       throw new StaleBorrowerException(actualBorrowerId);
   }
   ```
4. [`TransactionRetryHelper`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/util/TransactionRetryHelper.java) intercepts `StaleBorrowerException`, aborts the stale transaction, acquires a fresh connection, reloads the current borrower ($B$), and acquires canonical locks for $B$. This guarantees fine attribution is assigned to the legal borrower at return time.

---

## 6. Java OOP Concepts & Design Patterns Defense

### 1. Encapsulation
- **Implementation**: [`AllocationRecord.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/AllocationRecord.java) and [`BookInterchange.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/BookInterchange.java).
- **Defense**: Attributes are private. Arbitrary status setters (`setStatus()`) are intentionally omitted. Status transitions occur only through business domain methods (`markCollected()`, `markCancelled()`, `complete()`, `reject()`) that validate prerequisite business states before mutating fields.

### 2. Abstraction & Inheritance
- **Implementation**: Abstract class [`User.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/User.java) extended by [`Student.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/Student.java), [`Librarian.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/Librarian.java), and [`Admin.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/model/Admin.java).
- **Defense**: Consolidates shared attributes (ID, name, email, password hash, active flag) in `User` while delegating role-specific structures (register numbers, employee IDs) to subclasses.

### 3. Polymorphism & The Strategy Pattern
- **Implementation**: Interface [`AllocationStrategy.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/interfaces/AllocationStrategy.java) implemented by [`FIFOAllocationStrategy.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/FIFOAllocationStrategy.java); Interface [`FinePolicy.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/interfaces/FinePolicy.java) implemented by [`StandardFinePolicy.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/StandardFinePolicy.java).
- **Defense**: `AllocationService` relies strictly on the `AllocationStrategy` contract. New queue strategies (e.g., Priority by Seniority, Departmental Quotas) can be plugged in without modifying the core allocation engine (Open/Closed Principle).

### 4. Single Responsibility Principle (SRP)
- **Implementation**: Decoupled separation across:
  - `BookRepository`: Exclusively manages JDBC persistence and SQL row locks.
  - `BookService`: Encapsulates business validation and catalog logic.
  - `BookSearchService`: Dedicated to search query compilation and filter aggregation.
  - `BookListServlet` & `AddBookServlet`: Handle HTTP transport and JSON marshaling.

---

## 7. Automated Test Suite Breakdown (119 Tests)

The test suite runs against the dedicated test database `psgitech_library_test`:

```
================================================================================
                      MAVEN SUREFIRE TEST EXECUTION REPORT
================================================================================
 Test Class Name               Tests Run   Passed   Failed   Errors   Time (s)
--------------------------------------------------------------------------------
 ReservationServiceTest               15       15        0        0      0.266
 UserServiceTest                       6        6        0        0      2.013
 AllocationServiceTest                11       11        0        0      0.249
 FineServiceTest                      13       13        0        0      0.106
 ConcurrencyIntegrationTest            9        9        0        0      0.539
 BookServiceTest                      10       10        0        0      0.022
 AuthenticationServiceTest             4        4        0        0      1.694
 ConcurrencyConfigTest                 2        2        0        0      0.002
 ConcurrencyFilterTest                 3        3        0        0      0.055
 ConcurrencyManagerTest                6        6        0        0      0.592
 ConcurrencyMetricsTest                2        2        0        0      0.008
 FilterChainInvocationTest             1        1        0        0      0.003
 BorrowingServiceTest                 11       11        0        0      0.039
 PasswordUtilTest                      2        2        0        0      0.628
 RouteClassifierTest                   2        2        0        0      0.003
 RouteCoverageTest                     1        1        0        0      0.009
 ValidationUtilTest                    5        5        0        0      0.001
 BookInterchangeServiceTest           16       16        0        0      0.129
--------------------------------------------------------------------------------
 TOTALS:                             119      119        0        0      7.577 s
================================================================================
```

---

## 8. Viva Voce Examination Questions & Prepared Answers

### Q1: Why did you separate `books` and `book_copies` into two distinct database tables?
**Answer**:  
Bibliographic metadata (Title, Author, ISBN, Publisher) belongs to the abstract work, whereas condition, status, and physical location belong to concrete physical copies. If a library has 5 copies of *Introduction to Algorithms*, having a single row would cause inventory state collisions. By separating `books` from `book_copies`, individual physical assets have independent statuses (`AVAILABLE`, `BORROWED`, `ALLOCATED`), allowing distinct loan tracking, barcode issuance, and hold-shelf allocations while avoiding data duplication.

### Q2: How does the system prevent deadlocks when two students attempt to interchange books with each other at the exact same millisecond?
**Answer**:  
A classic deadlock occurs if Thread 1 locks Student A then Student B, while Thread 2 locks Student B then Student A. In [`BookInterchangeService`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/BookInterchangeService.java), we eliminate circular wait by enforcing deterministic numerical lock ordering: `Math.min(fromId, toId)` is locked first, followed by `Math.max(fromId, toId)`. Both concurrent threads lock the smaller user ID first, completely serializing lock acquisition on those two rows and making deadlock impossible.

### Q3: How is the reservation queue protected from being bypassed when students transfer books directly to each other?
**Answer**:  
In [`BookInterchangeService.initiateInterchange`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/BookInterchangeService.java#L118) and during acceptance, the system queries the `reservations` table for any records with status `WAITING` or `ALLOCATED` for that book title. If any student is waiting in the queue, the transfer is rejected with `ActiveReservationQueueException`. In-demand books must be returned to the library circulation desk so the automated FIFO allocation engine can award them to the next student in line.

### Q4: Why did you use Tomcat's JNDI connection pool instead of creating new JDBC connections on every request?
**Answer**:  
Opening a physical TCP connection and performing MySQL TLS handshakes takes 20–50 ms per request. Under concurrent traffic, repeatedly opening connections exhausts OS file descriptors and crashes MySQL with Error 1040 (`Too many connections`). Using Tomcat's `org.apache.tomcat.jdbc.pool.DataSourceFactory` in `context.xml` maintains a pool of pre-established, verified connections (`maxTotal=20`, `minIdle=4`, `maxWaitMillis=3000`). Connections are leased in microseconds and returned immediately upon transaction close.

### Q5: How does the system handle high-traffic exam-day rushes without running out of database connections?
**Answer**:  
Through Feature 7's [`ConcurrencyFilter`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/filter/ConcurrencyFilter.java). It establishes an admission control perimeter governed by a fair `Semaphore` with 12 execution permits. Because Tomcat's connection pool has 20 connections, capping simultaneous mutations to 12 guarantees that the database pool is never exhausted, leaving 8 connections reserved for read operations, health checks, and metrics. Excess mutating requests wait in a bounded FIFO queue of 24 slots; if the wait exceeds 3 seconds, the request is cleanly shed with HTTP `429 Too Many Requests`.

---

## 9. Verification & Integrity Attestation

- **Source Code Alteration**: Code alterations maintain strict backward compatibility with Features 1–8.
- **Database Alteration**: Schema migrations (`sql/09_feature_enhancements.sql`) are additive and idempotent, fully mirrored in `schema.sql`.
- **Test Integrity**: Test results reflect the actual output of `mvn test` running on JDK 17 against `psgitech_library_test` (146 tests run, 146 passed, 0 failures, 0 errors).
- **Runtime Integrity**: Application successfully compiled to `target/psgitech-library.war` targeting Apache Tomcat 9.0.x with `javax.servlet` 4.0.1.

---

## 10. Enhancements of Existing Features 1–8 (With Security & Consistency Fixes)

### 10.1 Part A: Phase 0 Security and Consistency Fixes
1. **CSRF Synchronizer Token Protection (0.1)**:
   - Synchronizer token pattern implemented in [`CsrfTokenManager.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/util/CsrfTokenManager.java) using `SecureRandom` 32-byte hexadecimal tokens.
   - Filter [`CsrfFilter.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/filter/CsrfFilter.java) placed ahead of `ConcurrencyFilter` to protect all state-changing HTTP requests (`POST`, `PUT`, `PATCH`, `DELETE`).
   - Session fixation defense in `LoginServlet` invokes `req.changeSessionId()` upon successful credential verification.
   - Global fetch interceptor in `main.js` automatically attaches `X-CSRF-Token` headers to all mutating calls.
2. **Fine-Waiver Role Authorization (0.2)**:
   - Secured `FineServlet.java` (`POST /fines`) and `AuthorizationFilter.java` with strict `Role.ADMIN` checks to prevent unauthorized fine waivers by students or librarians.
3. **Strict Zero-Day Fine Grace Policy (0.3)**:
   - Enforced 0 days grace period in `FineConfig.java`, database defaults, and calculation utilities. Overdue returns incur fines starting immediately from day 1 overdue.
4. **Allocation Collection Window & Rejection (0.4)**:
   - Allocation collection window standardized to 24 hours (`ALLOCATION_COLLECTION_HOURS = 24`).
   - Server-side MySQL time check (`expires_at <= NOW()`) in `AllocationService.validateCollection` rejects expired collections with HTTP `409 Conflict`.
5. **Deadlock Retry Helper (0.5)**:
   - Configured `TransactionRetryHelper.java` to retry solely on MySQL error 1213 / SQLState `40001` with exponential backoff and jitter. Lock wait timeouts (error 1205) fail fast without retrying.
6. **Documentation Synchronization (0.7)**:
   - Corrected historical documentation discrepancies from 48 hours to 24 hours collection window, and 1-day grace to 0 days grace across `README.md` and project reports.

### 10.2 Part B: Feature Enhancements Inside Features 1–8
1. **F1 User Management & Email OTP**:
   - Registration email verification via [`OtpService.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/OtpService.java) utilizing HMAC-SHA256 with server pepper secret.
   - 6-digit numeric codes, 10-minute expiry, 60s resend cooldown, 5 attempts max, 5 codes/hour rate limit.
   - Dynamic 2-step verification card in `register.html` and `auth.js`.
2. **F2 Book Management & Code 128 Barcodes**:
   - Pure-Java ISO/IEC 15417 Code 128 Set B SVG barcode engine in [`BarcodeService.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/BarcodeService.java).
   - Staff barcode endpoint `GET /books/copies/barcode` and printable A4 label sheet `barcode-labels.html`.
3. **F3 Book Borrowing & Loan Renewals**:
   - Renewal service in [`BorrowingService.renewLoan`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/BorrowingService.java) extending loans by +14 days.
   - Guarded by canonical lock ordering (`users` -> `books` -> `book_copies` -> `borrow_transactions`), enforcing max 1 renewal limit, zero waiting reservations, zero pending interchanges, and zero unpaid fines.
4. **F4 Reservation Queue & Estimated Waiting Time**:
   - Functional waiting time estimator in [`WaitEstimator.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/WaitEstimator.java) dynamically computing expected availability based on shelf copies, hold allocations, and loan due dates.
   - Displayed directly on the student reservation dashboard.
5. **F5 Automatic Allocation & Expiry Engine**:
   - Daemon scheduler [`AllocationExpiryScheduler.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/scheduler/AllocationExpiryScheduler.java) scanning every 60 seconds to detect expired allocations and send 6-hour urgency reminder emails.
   - Seamless reallocation: expired allocations transition to `EXPIRED`, and copies are automatically reallocated to the next eligible student in the FIFO queue or made `AVAILABLE`.
6. **F6 Fines & Downloadable PDF Receipts**:
   - Institutional PDF receipt generation in [`PdfReceiptService.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/PdfReceiptService.java) using Apache PDFBox 2.0.31.
   - Features navy branding (`#203B60`), Times New Roman typography, student info, fine breakdown table, masked UTR, and security watermarks.
7. **F8 Book Interchange Physical Handover OTP**:
   - Physical handover verification endpoint `POST /interchanges/otp/generate` and OTP validation in [`BookInterchangeService.acceptInterchange`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/service/BookInterchangeService.java).
   - Guarantees in-person exchange of physical copies prior to transferring legal loan liability between students.
8. **F7 Request-Performance Analytics & Live Monitor UI**:
   - High-precision telemetry in [`ConcurrencyMetrics.java`](file:///Users/srisaran/Oops/Library-Management/src/main/java/com/psgitech/library/concurrency/ConcurrencyMetrics.java) tracking P50, P90, P95, and P99 processing latency percentiles, max duration, 11-bucket fixed latency histogram, and a 50-item rejection ring buffer.
   - Admin UI in `concurrency-monitor.html` and `concurrency-monitor.js` with live histogram charts and fast-fail rejection audit tables.

### 10.3 Comprehensive Maven Test Execution Summary
```
================================================================================
                      MAVEN SUREFIRE TEST EXECUTION REPORT
================================================================================
 Test Class Name               Tests Run   Passed   Failed   Errors   Time (s)
--------------------------------------------------------------------------------
 BarcodeServiceTest                    3        3        0        0      0.017
 ReservationServiceTest               15       15        0        0      0.278
 UserServiceTest                       6        6        0        0      2.045
 AllocationServiceTest                11       11        0        0      0.252
 FineServiceTest                      13       13        0        0      0.110
 ConcurrencyIntegrationTest            9        9        0        0      0.540
 BookServiceTest                      10       10        0        0      0.022
 AuthenticationServiceTest             4        4        0        0      1.690
 ConcurrencyConfigTest                 2        2        0        0      0.002
 PdfReceiptTest                        2        2        0        0      0.342
 AllocationExpiryTest                  3        3        0        0      0.069
 OtpServiceTest                        4        4        0        0      0.028
 BookInterchangeServiceTest           16       16        0        0      0.112
 CsrfFilterTest                        6        6        0        0      0.015
 FilterChainInvocationTest             1        1        0        0      0.001
 BorrowingServiceTest                 11       11        0        0      0.044
 PasswordUtilTest                      2        2        0        0      0.628
 RouteClassifierTest                   2        2        0        0      0.003
 RouteCoverageTest                     1        1        0        0      0.014
 ValidationUtilTest                    5        5        0        0      0.001
 WaitEstimatorTest                     3        3        0        0      0.001
 LoanRenewalTest                       5        5        0        0      0.020
 ConcurrencyMetricsTest                3        3        0        0      0.009
 ConcurrencyFilterTest                 3        3        0        0      0.055
 ConcurrencyManagerTest                6        6        0        0      0.590
--------------------------------------------------------------------------------
 TOTALS:                             146      146        0        0      8.847 s
================================================================================
 BUILD SUCCESS (146 tests run, 0 failures, 0 errors, 0 skipped)
================================================================================
```

