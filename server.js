/**
 * PSG iTech Library - Integrated Development & Mock Servlet Server
 * Full REST API Support + Static Serving + Remember Me + Session Persistence
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');
const querystring = require('querystring');
const crypto = require('crypto');

const PORT = process.env.PORT || 8080;
const WEBAPP_DIR = path.join(__dirname, 'src', 'main', 'webapp');
const DATA_FILE = path.join(__dirname, 'dev_users.json');

// MIME types for static assets
const MIME_TYPES = {
    '.html': 'text/html; charset=UTF-8',
    '.css': 'text/css; charset=UTF-8',
    '.js': 'application/javascript; charset=UTF-8',
    '.json': 'application/json; charset=UTF-8',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon'
};

// Seed Users Database
const DEFAULT_USERS = [
    {
        userId: 1,
        name: 'Admin Office',
        email: 'admin@psgitech.ac.in',
        phone: '9876543210',
        role: 'ADMIN',
        active: true,
        password: 'AdminPassword@123',
        createdAt: '2026-01-10 09:30:00'
    },
    {
        userId: 2,
        name: 'Dr. K. Ramanathan',
        email: 'librarian@psgitech.ac.in',
        phone: '9876543211',
        employeeId: 'LIB101',
        role: 'LIBRARIAN',
        active: true,
        mustChangePassword: false,
        password: 'Librarian@123',
        createdAt: '2026-02-14 11:20:00'
    },
    {
        userId: 3,
        name: 'Ms. S. Meena',
        email: 'meena.lib@psgitech.ac.in',
        phone: '9876543212',
        employeeId: 'LIB102',
        role: 'LIBRARIAN',
        active: true,
        mustChangePassword: true,
        password: 'InitialPassword@123',
        createdAt: '2026-03-01 10:15:00'
    },
    {
        userId: 4,
        name: 'Sivakathir S',
        email: 'sivakathir.22cs@psgitech.ac.in',
        phone: '9876543213',
        registerNumber: '715522104045',
        department: 'CSE',
        year: 3,
        role: 'STUDENT',
        active: true,
        password: 'Student@123',
        createdAt: '2026-04-12 14:00:00'
    },
    {
        userId: 5,
        name: 'Ananya Ramesh',
        email: 'ananya.23ai@psgitech.ac.in',
        phone: '9876543214',
        registerNumber: '715523105012',
        department: 'AI&DS',
        year: 2,
        role: 'STUDENT',
        active: true,
        password: 'Student@123',
        createdAt: '2026-05-18 16:45:00'
    },
    {
        userId: 6,
        name: 'Rahul Kumar',
        email: 'rahul.21me@psgitech.ac.in',
        phone: '9876543215',
        registerNumber: '715521102030',
        department: 'MECH',
        year: 4,
        role: 'STUDENT',
        active: false,
        password: 'Student@123',
        createdAt: '2026-06-22 09:10:00'
    },
    {
        userId: 7,
        name: 'Saran',
        email: '25z304@psgitech.ac.in',
        phone: '8903991618',
        registerNumber: '715525104210',
        department: 'CSE',
        year: 2,
        role: 'STUDENT',
        active: true,
        password: 'Password@123',
        createdAt: '2026-10-31 00:00:00'
    }
];

let users = [];
try {
    if (fs.existsSync(DATA_FILE)) {
        users = JSON.parse(fs.readFileSync(DATA_FILE, 'utf-8'));
    } else {
        users = JSON.parse(JSON.stringify(DEFAULT_USERS));
        fs.writeFileSync(DATA_FILE, JSON.stringify(users, null, 2));
    }
} catch (e) {
    users = JSON.parse(JSON.stringify(DEFAULT_USERS));
}

function saveUsers() {
    try {
        fs.writeFileSync(DATA_FILE, JSON.stringify(users, null, 2));
    } catch (e) {
        console.error('Failed to save users:', e);
    }
}

// =============================================================================
// Feature 2: Book Management Data Store & Inventory Helpers
// =============================================================================
const BOOKS_FILE = path.join(__dirname, 'dev_books.json');

const DEFAULT_BOOKS = [
    {
        bookId: 1,
        isbn: '978-0262033848',
        title: 'Introduction to Algorithms',
        author: 'Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein',
        publisher: 'MIT Press',
        edition: '4th Edition',
        publicationYear: 2022,
        category: 'Data Structures',
        department: 'CSE',
        description: 'The definitive modern algorithms textbook covering asymptotic analysis, sorting, dynamic programming, greedy algorithms, and graph theory.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg',
        archived: false,
        createdAt: '2026-01-01 00:00:00'
    },
    {
        bookId: 2,
        isbn: '978-0132350884',
        title: 'Clean Code: A Handbook of Agile Software Craftsmanship',
        author: 'Robert C. Martin',
        publisher: 'Prentice Hall',
        edition: '1st Edition',
        publicationYear: 2008,
        category: 'Programming',
        department: 'CSE',
        description: 'Best practices and clean architecture patterns for writing robust, maintainable, and readable software systems.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9780132350884-L.jpg',
        archived: false,
        createdAt: '2026-01-02 00:00:00'
    },
    {
        bookId: 3,
        isbn: '978-0136042594',
        title: 'Artificial Intelligence: A Modern Approach',
        author: 'Stuart Russell, Peter Norvig',
        publisher: 'Pearson',
        edition: '4th Edition',
        publicationYear: 2020,
        category: 'Artificial Intelligence',
        department: 'AI&DS',
        description: 'Comprehensive university reference on search algorithms, probabilistic reasoning, machine learning, and natural language processing.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9780136042594-L.jpg',
        archived: false,
        createdAt: '2026-01-03 00:00:00'
    },
    {
        bookId: 4,
        isbn: '978-0132126953',
        title: 'Computer Networks',
        author: 'Andrew S. Tanenbaum, David J. Wetherall',
        publisher: 'Pearson',
        edition: '5th Edition',
        publicationYear: 2011,
        category: 'Computer Science',
        department: 'CSE',
        description: 'Covers protocol architecture, physical layers, data links, wireless transmission, routing algorithms, and internet security.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9780132126953-L.jpg',
        archived: false,
        createdAt: '2026-01-04 00:00:00'
    },
    {
        bookId: 5,
        isbn: '978-1119456339',
        title: 'Operating System Concepts',
        author: 'Abraham Silberschatz, Peter B. Galvin, Greg Gagne',
        publisher: 'Wiley',
        edition: '10th Edition',
        publicationYear: 2018,
        category: 'Computer Science',
        department: 'CSE',
        description: 'Core foundational textbook covering process scheduling, multi-threading, concurrency, memory management, and file systems.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9781119456339-L.jpg',
        archived: false,
        createdAt: '2026-01-05 00:00:00'
    },
    {
        bookId: 6,
        isbn: '978-0078022159',
        title: 'Database System Concepts',
        author: 'Abraham Silberschatz, Henry F. Korth, S. Sudarshan',
        publisher: 'McGraw-Hill Education',
        edition: '7th Edition',
        publicationYear: 2019,
        category: 'Computer Science',
        department: 'IT',
        description: 'Comprehensive guide to relational data models, SQL queries, transaction processing, indexing, and distributed databases.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9780078022159-L.jpg',
        archived: false,
        createdAt: '2026-01-06 00:00:00'
    },
    {
        bookId: 7,
        isbn: '978-0199464689',
        title: 'Linear Algebra and Its Applications',
        author: 'Gilbert Strang',
        publisher: 'Cengage Learning',
        edition: '4th Edition',
        publicationYear: 2016,
        category: 'Mathematics',
        department: 'GENERAL',
        description: 'Essential linear algebra concepts for computing, including vector spaces, matrix factorization, eigenvalues, and SVD.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9780199464689-L.jpg',
        archived: false,
        createdAt: '2026-01-07 00:00:00'
    },
    {
        bookId: 8,
        isbn: '978-0199339136',
        title: 'Microelectronic Circuits',
        author: 'Adel S. Sedra, Kenneth C. Smith',
        publisher: 'Oxford University Press',
        edition: '7th Edition',
        publicationYear: 2014,
        category: 'Electronics',
        department: 'ECE',
        description: 'Foundational curriculum textbook analyzing operational amplifiers, diode circuits, MOSFETs, and analog signal processing.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9780199339136-L.jpg',
        archived: false,
        createdAt: '2026-01-08 00:00:00'
    },
    {
        bookId: 9,
        isbn: '978-1260440232',
        title: 'Java: The Complete Reference',
        author: 'Herbert Schildt',
        publisher: 'McGraw-Hill Education',
        edition: '12th Edition',
        publicationYear: 2021,
        category: 'Programming',
        department: 'IT',
        description: 'Authoritative guide to Java programming syntax, OOP principles, collections framework, concurrency utilities, and I/O streams.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9781260440232-L.jpg',
        archived: false,
        createdAt: '2026-01-09 00:00:00'
    },
    {
        bookId: 10,
        isbn: '978-0134685991',
        title: 'Effective Java',
        author: 'Joshua Bloch',
        publisher: 'Addison-Wesley Professional',
        edition: '3rd Edition',
        publicationYear: 2018,
        category: 'Programming',
        department: 'CSE',
        description: 'Industry standard manual of best programming practices in the Java programming language.',
        coverImageUrl: 'https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg',
        archived: false,
        createdAt: '2026-01-10 00:00:00'
    }
];

const DEFAULT_COPIES = [
    { copyId: 1, copyCode: 'PSG-BK1-C01', bookId: 1, status: 'AVAILABLE', archived: false, createdAt: '2026-01-01 00:00:00' },
    { copyId: 2, copyCode: 'PSG-BK1-C02', bookId: 1, status: 'AVAILABLE', archived: false, createdAt: '2026-01-01 00:00:00' },
    { copyId: 3, copyCode: 'PSG-BK1-C03', bookId: 1, status: 'AVAILABLE', archived: false, createdAt: '2026-01-01 00:00:00' },
    { copyId: 4, copyCode: 'PSG-BK1-C04', bookId: 1, status: 'AVAILABLE', archived: false, createdAt: '2026-01-01 00:00:00' },
    { copyId: 5, copyCode: 'PSG-BK2-C01', bookId: 2, status: 'AVAILABLE', archived: false, createdAt: '2026-01-02 00:00:00' },
    { copyId: 6, copyCode: 'PSG-BK2-C02', bookId: 2, status: 'AVAILABLE', archived: false, createdAt: '2026-01-02 00:00:00' },
    { copyId: 7, copyCode: 'PSG-BK2-C03', bookId: 2, status: 'AVAILABLE', archived: false, createdAt: '2026-01-02 00:00:00' },
    { copyId: 8, copyCode: 'PSG-BK3-C01', bookId: 3, status: 'AVAILABLE', archived: false, createdAt: '2026-01-03 00:00:00' },
    { copyId: 9, copyCode: 'PSG-BK3-C02', bookId: 3, status: 'AVAILABLE', archived: false, createdAt: '2026-01-03 00:00:00' },
    { copyId: 10, copyCode: 'PSG-BK3-C03', bookId: 3, status: 'AVAILABLE', archived: false, createdAt: '2026-01-03 00:00:00' },
    { copyId: 11, copyCode: 'PSG-BK4-C01', bookId: 4, status: 'AVAILABLE', archived: false, createdAt: '2026-01-04 00:00:00' },
    { copyId: 12, copyCode: 'PSG-BK4-C02', bookId: 4, status: 'AVAILABLE', archived: false, createdAt: '2026-01-04 00:00:00' },
    { copyId: 13, copyCode: 'PSG-BK4-C03', bookId: 4, status: 'AVAILABLE', archived: false, createdAt: '2026-01-04 00:00:00' },
    { copyId: 14, copyCode: 'PSG-BK5-C01', bookId: 5, status: 'AVAILABLE', archived: false, createdAt: '2026-01-05 00:00:00' },
    { copyId: 15, copyCode: 'PSG-BK5-C02', bookId: 5, status: 'AVAILABLE', archived: false, createdAt: '2026-01-05 00:00:00' },
    { copyId: 16, copyCode: 'PSG-BK5-C03', bookId: 5, status: 'AVAILABLE', archived: false, createdAt: '2026-01-05 00:00:00' },
    { copyId: 17, copyCode: 'PSG-BK6-C01', bookId: 6, status: 'AVAILABLE', archived: false, createdAt: '2026-01-06 00:00:00' },
    { copyId: 18, copyCode: 'PSG-BK6-C02', bookId: 6, status: 'AVAILABLE', archived: false, createdAt: '2026-01-06 00:00:00' },
    { copyId: 19, copyCode: 'PSG-BK6-C03', bookId: 6, status: 'AVAILABLE', archived: false, createdAt: '2026-01-06 00:00:00' },
    { copyId: 20, copyCode: 'PSG-BK7-C01', bookId: 7, status: 'AVAILABLE', archived: false, createdAt: '2026-01-07 00:00:00' },
    { copyId: 21, copyCode: 'PSG-BK7-C02', bookId: 7, status: 'AVAILABLE', archived: false, createdAt: '2026-01-07 00:00:00' },
    { copyId: 22, copyCode: 'PSG-BK8-C01', bookId: 8, status: 'AVAILABLE', archived: false, createdAt: '2026-01-08 00:00:00' },
    { copyId: 23, copyCode: 'PSG-BK8-C02', bookId: 8, status: 'AVAILABLE', archived: false, createdAt: '2026-01-08 00:00:00' },
    { copyId: 24, copyCode: 'PSG-BK9-C01', bookId: 9, status: 'AVAILABLE', archived: false, createdAt: '2026-01-09 00:00:00' },
    { copyId: 25, copyCode: 'PSG-BK9-C02', bookId: 9, status: 'AVAILABLE', archived: false, createdAt: '2026-01-09 00:00:00' },
    { copyId: 26, copyCode: 'PSG-BK9-C03', bookId: 9, status: 'AVAILABLE', archived: false, createdAt: '2026-01-09 00:00:00' },
    { copyId: 27, copyCode: 'PSG-BK10-C01', bookId: 10, status: 'AVAILABLE', archived: false, createdAt: '2026-01-10 00:00:00' },
    { copyId: 28, copyCode: 'PSG-BK10-C02', bookId: 10, status: 'AVAILABLE', archived: false, createdAt: '2026-01-10 00:00:00' }
];

let bookData = { books: DEFAULT_BOOKS, copies: DEFAULT_COPIES };
try {
    if (fs.existsSync(BOOKS_FILE)) {
        bookData = JSON.parse(fs.readFileSync(BOOKS_FILE, 'utf-8'));
    } else {
        fs.writeFileSync(BOOKS_FILE, JSON.stringify(bookData, null, 2));
    }
} catch (e) {
    bookData = { books: DEFAULT_BOOKS, copies: DEFAULT_COPIES };
}

function saveBooks() {
    try {
        fs.writeFileSync(BOOKS_FILE, JSON.stringify(bookData, null, 2));
    } catch (e) {
        console.error('Failed to save books:', e);
    }
}

// =============================================================================
// Feature 3: Book Borrowing Data Store & Helpers
// =============================================================================
const BORROWINGS_FILE = path.join(__dirname, 'dev_borrowings.json');

const DEFAULT_BORROWINGS = [
    {
        transactionId: 1,
        studentId: 4,
        bookId: 1,
        copyId: 1,
        issueDate: '2026-10-25',
        dueDate: '2026-11-08',
        returnDate: null,
        status: 'ACTIVE',
        issuedBy: 2,
        returnedBy: null,
        createdAt: '2026-10-25 10:00:00',
        updatedAt: '2026-10-25 10:00:00'
    },
    {
        transactionId: 2,
        studentId: 5,
        bookId: 2,
        copyId: 5,
        issueDate: '2026-10-01',
        dueDate: '2026-10-15',
        returnDate: '2026-10-14',
        status: 'RETURNED',
        issuedBy: 2,
        returnedBy: 2,
        createdAt: '2026-10-01 11:00:00',
        updatedAt: '2026-10-14 15:30:00'
    }
];

let borrowings = DEFAULT_BORROWINGS;
try {
    if (fs.existsSync(BORROWINGS_FILE)) {
        borrowings = JSON.parse(fs.readFileSync(BORROWINGS_FILE, 'utf-8'));
    } else {
        fs.writeFileSync(BORROWINGS_FILE, JSON.stringify(borrowings, null, 2));
    }
} catch (e) {
    borrowings = DEFAULT_BORROWINGS;
}

// Sync active borrowings with copy status
for (const tx of borrowings) {
    if (tx.status === 'ACTIVE') {
        const copy = bookData.copies.find(c => c.copyId === tx.copyId);
        if (copy) copy.status = 'BORROWED';
    }
}
saveBooks();

function saveBorrowings() {
    try {
        fs.writeFileSync(BORROWINGS_FILE, JSON.stringify(borrowings, null, 2));
    } catch (e) {
        console.error('Failed to save borrowings:', e);
    }
}

// =============================================================================
// Feature 4: Reservation Persistence & In-Memory Queue State
// =============================================================================
const RESERVATIONS_FILE = path.join(__dirname, 'dev_reservations.json');

let reservations = [];
try {
    if (fs.existsSync(RESERVATIONS_FILE)) {
        reservations = JSON.parse(fs.readFileSync(RESERVATIONS_FILE, 'utf-8'));
    } else {
        fs.writeFileSync(RESERVATIONS_FILE, JSON.stringify(reservations, null, 2));
    }
} catch (e) {
    reservations = [];
}

function saveReservations() {
    try {
        fs.writeFileSync(RESERVATIONS_FILE, JSON.stringify(reservations, null, 2));
    } catch (e) {
        console.error('Failed to save reservations:', e);
    }
}

const ALLOCATIONS_FILE = path.join(__dirname, 'dev_allocations.json');
let allocations = [];
try {
    if (fs.existsSync(ALLOCATIONS_FILE)) {
        allocations = JSON.parse(fs.readFileSync(ALLOCATIONS_FILE, 'utf-8'));
    } else {
        fs.writeFileSync(ALLOCATIONS_FILE, JSON.stringify(allocations, null, 2));
    }
} catch (e) {
    allocations = [];
}

function saveAllocations() {
    try {
        fs.writeFileSync(ALLOCATIONS_FILE, JSON.stringify(allocations, null, 2));
    } catch (e) {
        console.error('Failed to save allocations:', e);
    }
}

// =============================================================================
// Feature 6: Fines & Payments Persistence
// =============================================================================
const FINES_FILE = path.join(__dirname, 'dev_fines.json');
let fines = [];
try {
    if (fs.existsSync(FINES_FILE)) {
        fines = JSON.parse(fs.readFileSync(FINES_FILE, 'utf-8'));
    } else {
        fs.writeFileSync(FINES_FILE, JSON.stringify(fines, null, 2));
    }
} catch (e) {
    fines = [];
}

function saveFines() {
    try {
        fs.writeFileSync(FINES_FILE, JSON.stringify(fines, null, 2));
    } catch (e) {
        console.error('Failed to save fines:', e);
    }
}

const PAYMENTS_FILE = path.join(__dirname, 'dev_payments.json');
let payments = [];
try {
    if (fs.existsSync(PAYMENTS_FILE)) {
        payments = JSON.parse(fs.readFileSync(PAYMENTS_FILE, 'utf-8'));
    } else {
        fs.writeFileSync(PAYMENTS_FILE, JSON.stringify(payments, null, 2));
    }
} catch (e) {
    payments = [];
}

function savePayments() {
    try {
        fs.writeFileSync(PAYMENTS_FILE, JSON.stringify(payments, null, 2));
    } catch (e) {
        console.error('Failed to save payments:', e);
    }
}

function enrichReservation(r) {
    const student = users.find(u => u.userId === r.studentId) || {};
    const book = bookData.books.find(b => b.bookId === r.bookId) || {};

    // Compute live FIFO position
    let queuePosition = 1;
    let totalWaiting = 0;
    let allocDetails = {};

    if (r.status === 'WAITING') {
        const bookQueue = reservations
            .filter(item => item.bookId === r.bookId && item.status === 'WAITING')
            .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt) || (a.reservationId - b.reservationId));

        totalWaiting = bookQueue.length;
        const idx = bookQueue.findIndex(item => item.reservationId === r.reservationId);
        queuePosition = idx >= 0 ? idx + 1 : 1;
    } else if (r.status === 'ALLOCATED') {
        queuePosition = null;
        const alloc = allocations.find(a => a.reservationId === r.reservationId && a.status === 'ALLOCATED');
        if (alloc) {
            allocDetails = {
                allocationId: alloc.allocationId,
                copyId: alloc.copyId,
                copyCode: alloc.copyCode,
                copyIdentifier: alloc.copyCode,
                allocationExpiresAt: alloc.expiresAt,
                expiresAt: alloc.expiresAt,
                allocatedAt: alloc.allocatedAt,
                readyForCollection: true
            };
        }
    }

    return {
        ...r,
        studentName: student.name || 'Student',
        registerNumber: student.registerNumber || '--',
        studentDepartment: student.department || '--',
        bookTitle: book.title || 'Book',
        bookAuthor: book.author || 'Author',
        bookIsbn: book.isbn || '',
        coverImageUrl: book.coverImageUrl || '',
        queuePosition,
        totalWaitingInQueue: totalWaiting,
        ...allocDetails
    };
}

function enrichBorrowTransaction(tx) {
    const student = users.find(u => u.userId === tx.studentId) || {};
    const book = bookData.books.find(b => b.bookId === tx.bookId) || {};
    const copy = bookData.copies.find(c => c.copyId === tx.copyId) || {};

    const todayStr = new Date().toISOString().split('T')[0];
    const today = new Date(todayStr);
    const dueDate = new Date(tx.dueDate);

    let overdue = false;
    let overdueDays = 0;
    let daysRemaining = 0;

    if (tx.status === 'ACTIVE') {
        const diffTime = today - dueDate;
        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
        if (diffDays > 0) {
            overdue = true;
            overdueDays = diffDays;
            daysRemaining = -diffDays;
        } else {
            overdue = false;
            overdueDays = 0;
            daysRemaining = Math.floor((dueDate - today) / (1000 * 60 * 60 * 24));
        }
    } else if (tx.returnDate) {
        const returnDate = new Date(tx.returnDate);
        const diff = Math.floor((returnDate - dueDate) / (1000 * 60 * 60 * 24));
        overdueDays = diff > 0 ? diff : 0;
    }

    return {
        ...tx,
        studentName: student.name || 'Unknown Student',
        registerNumber: student.registerNumber || '-',
        studentDepartment: student.department || '-',
        studentYear: student.year || null,
        bookTitle: book.title || 'Unknown Title',
        bookAuthor: book.author || 'PSG iTech',
        coverImageUrl: book.coverImageUrl || `https://covers.openlibrary.org/b/isbn/${(book.isbn || '').replace(/[^0-9X]/gi, '')}-L.jpg`,
        copyCode: copy.copyCode || `PSG-BK${tx.bookId}-C${tx.copyId}`,
        overdue,
        overdueDays,
        daysRemaining
    };
}


function computeBookInventory(bookId) {
    const copies = bookData.copies.filter(c => c.bookId === bookId);
    let totalCopies = copies.length;
    let availableCopies = 0;
    let borrowedCopies = 0;
    let allocatedCopies = 0;
    let transferPendingCopies = 0;
    let archivedCopies = 0;

    for (const c of copies) {
        if (c.archived || c.status === 'ARCHIVED') {
            archivedCopies++;
        } else if (c.status === 'AVAILABLE') {
            availableCopies++;
        } else if (c.status === 'BORROWED') {
            borrowedCopies++;
        } else if (c.status === 'ALLOCATED') {
            allocatedCopies++;
        } else if (c.status === 'TRANSFER_PENDING') {
            transferPendingCopies++;
        }
    }
    const totalActiveCopies = totalCopies - archivedCopies;
    return {
        bookId,
        totalCopies,
        totalActiveCopies,
        availableCopies,
        borrowedCopies,
        allocatedCopies,
        transferPendingCopies,
        archivedCopies
    };
}

function enrichBook(book) {
    const inv = computeBookInventory(book.bookId);
    return {
        ...book,
        inventory: inv,
        availableCopies: inv.availableCopies,
        totalActiveCopies: inv.totalActiveCopies
    };
}

// Active session store & remember tokens store
const sessions = new Map();
const rememberTokens = new Map();

// Helper to parse cookies
function parseCookies(req) {
    const list = {};
    const rc = req.headers.cookie;
    if (rc) {
        rc.split(';').forEach(cookie => {
            const parts = cookie.split('=');
            list[parts.shift().trim()] = decodeURI(parts.join('='));
        });
    }
    return list;
}

// Helper to parse request body
function parseRequestBody(req) {
    return new Promise((resolve) => {
        let body = '';
        req.on('data', chunk => { body += chunk.toString(); });
        req.on('end', () => {
            const contentType = req.headers['content-type'] || '';
            if (contentType.includes('application/json')) {
                try {
                    resolve(JSON.parse(body || '{}'));
                } catch (e) {
                    resolve({});
                }
            } else {
                resolve(querystring.parse(body));
            }
        });
    });
}

function sanitizeUser(u) {
    if (!u) return null;
    const copy = { ...u };
    delete copy.password;
    delete copy.passwordHash;
    return copy;
}

function getSessionUser(req) {
    const cookies = parseCookies(req);
    if (cookies.psgitech_session && sessions.has(cookies.psgitech_session)) {
        return sessions.get(cookies.psgitech_session);
    }
    if (cookies.psgitech_remember_me && rememberTokens.has(cookies.psgitech_remember_me)) {
        const userId = rememberTokens.get(cookies.psgitech_remember_me);
        return users.find(u => u.userId === userId && u.active !== false);
    }
    return null;
}

const server = http.createServer(async (req, res) => {
    const parsedUrl = url.parse(req.url, true);
    let pathname = parsedUrl.pathname;
    const queryParams = parsedUrl.query || {};

    // Normalize Tomcat context path /psgitech-library/
    if (pathname.startsWith('/psgitech-library')) {
        pathname = pathname.substring('/psgitech-library'.length) || '/';
    }

    if (pathname === '/' || pathname === '') {
        pathname = '/index.html';
    }

    const cookies = parseCookies(req);
    let currentUser = null;

    // Session identification
    if (cookies.psgitech_session && sessions.has(cookies.psgitech_session)) {
        currentUser = sessions.get(cookies.psgitech_session);
    } else if (cookies.psgitech_remember_me && rememberTokens.has(cookies.psgitech_remember_me)) {
        const userId = rememberTokens.get(cookies.psgitech_remember_me);
        currentUser = users.find(u => u.userId === userId && u.active !== false);
        if (currentUser) {
            const newSid = crypto.randomBytes(16).toString('hex');
            sessions.set(newSid, currentUser);
            res.setHeader('Set-Cookie', `psgitech_session=${newSid}; Path=/; HttpOnly`);
        }
    }

    // CORS & Content negotiation
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Requested-With');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    // =========================================================================
    // API Endpoints: Register, Login, Logout, Profile, Change Password, Admin
    // =========================================================================

    // 1. POST /register
    if (pathname === '/register' && req.method === 'POST') {
        const body = await parseRequestBody(req);
        const name = (body.name || '').trim();
        const registerNumber = (body.registerNumber || body.regNo || '').trim();
        const email = (body.email || '').trim().toLowerCase();
        const phone = (body.phone || '').trim();
        const department = (body.department || 'CSE').trim();
        const year = parseInt(body.year || '1', 10);
        const password = body.password || '';
        const confirmPassword = body.confirmPassword || '';

        if (!name) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Full Name is required.' }));
            return;
        }

        if (!email) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Email address is required.' }));
            return;
        }

        if (!password) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Password is required.' }));
            return;
        }

        if (confirmPassword && password !== confirmPassword) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Password and Confirm Password do not match.' }));
            return;
        }

        // Check if user already exists
        const existing = users.find(u => u.email.toLowerCase() === email || (registerNumber && u.registerNumber && u.registerNumber.toLowerCase() === registerNumber.toLowerCase()));
        if (existing) {
            // Update existing record for friendly user registration experience
            existing.name = name;
            existing.phone = phone || existing.phone;
            existing.department = department || existing.department;
            existing.year = year || existing.year;
            existing.password = password;
            existing.active = true;
            saveUsers();

            const sid = crypto.randomBytes(16).toString('hex');
            sessions.set(sid, existing);
            const rawToken = crypto.randomBytes(24).toString('hex');
            rememberTokens.set(rawToken, existing.userId);

            res.writeHead(200, {
                'Content-Type': 'application/json',
                'Set-Cookie': [
                    `psgitech_session=${sid}; Path=/; HttpOnly; Max-Age=${30 * 24 * 3600}`,
                    `psgitech_remember_me=${rawToken}; Path=/; HttpOnly; Max-Age=${30 * 24 * 3600}`
                ]
            });
            res.end(JSON.stringify({
                success: true,
                message: `Registration successful! Welcome, ${name}.`,
                redirect: 'student-home.html',
                user: sanitizeUser(existing)
            }));
            return;
        }

        // Create new student
        const newStudent = {
            userId: users.length + 1,
            name: name,
            email: email,
            phone: phone || '',
            registerNumber: registerNumber || 'REG' + Date.now().toString().slice(-6),
            department: department || 'CSE',
            year: year || 1,
            role: 'STUDENT',
            active: true,
            password: password,
            createdAt: new Date().toISOString().slice(0, 19).replace('T', ' ')
        };

        users.push(newStudent);
        saveUsers();

        // Establish session and persistent Remember Me immediately for smooth student onboarding
        const sid = crypto.randomBytes(16).toString('hex');
        sessions.set(sid, newStudent);
        const rawToken = crypto.randomBytes(24).toString('hex');
        rememberTokens.set(rawToken, newStudent.userId);

        res.writeHead(200, {
            'Content-Type': 'application/json',
            'Set-Cookie': [
                `psgitech_session=${sid}; Path=/; HttpOnly; Max-Age=${30 * 24 * 3600}`,
                `psgitech_remember_me=${rawToken}; Path=/; HttpOnly; Max-Age=${30 * 24 * 3600}`
            ]
        });
        res.end(JSON.stringify({
            success: true,
            message: `Account registered successfully! Welcome, ${name}.`,
            redirect: 'student-home.html',
            user: sanitizeUser(newStudent)
        }));
        return;
    }

    // 2. POST /login
    if (pathname === '/login' && req.method === 'POST') {
        const body = await parseRequestBody(req);
        const loginKey = (body.identifier || body.loginKey || body.email || body.registerNumber || '').trim().toLowerCase();
        const password = body.password || '';
        const rememberMe = body.rememberMe === 'true' || body.rememberMe === 'on' || body.rememberMe === true;

        if (!loginKey || !password) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Email/Register Number and Password are required.' }));
            return;
        }

        const user = users.find(u => {
            const matchEmail = u.email && u.email.toLowerCase() === loginKey;
            const matchReg = u.registerNumber && u.registerNumber.toLowerCase() === loginKey;
            const matchEmp = u.employeeId && u.employeeId.toLowerCase() === loginKey;
            return matchEmail || matchReg || matchEmp;
        });

        if (!user || user.password !== password) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Invalid credentials. Please verify your username and password.' }));
            return;
        }

        if (user.active === false) {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Your account is deactivated. Please contact the Library Administrator.' }));
            return;
        }

        // Establish session
        const sid = crypto.randomBytes(16).toString('hex');
        sessions.set(sid, user);

        const cookieHeaders = [`psgitech_session=${sid}; Path=/; HttpOnly; Max-Age=${30 * 24 * 3600}`];

        if (rememberMe) {
            const rawToken = crypto.randomBytes(24).toString('hex');
            rememberTokens.set(rawToken, user.userId);
            cookieHeaders.push(`psgitech_remember_me=${rawToken}; Path=/; HttpOnly; Max-Age=${30 * 24 * 3600}`);
        }

        let redirectUrl = 'student-home.html';
        if (user.role === 'ADMIN') {
            redirectUrl = 'admin-users.html';
        } else if (user.role === 'LIBRARIAN') {
            redirectUrl = user.mustChangePassword ? 'first-login-change-password.html' : 'librarian-dashboard.html';
        }

        res.writeHead(200, {
            'Content-Type': 'application/json',
            'Set-Cookie': cookieHeaders
        });
        res.end(JSON.stringify({
            success: true,
            message: `Login successful! Welcome, ${user.name}.`,
            redirect: redirectUrl,
            user: sanitizeUser(user)
        }));
        return;
    }

    // 3. POST /logout
    if (pathname === '/logout') {
        if (cookies.psgitech_session) sessions.delete(cookies.psgitech_session);
        if (cookies.psgitech_remember_me) rememberTokens.delete(cookies.psgitech_remember_me);

        res.writeHead(200, {
            'Content-Type': 'application/json',
            'Set-Cookie': [
                'psgitech_session=; Path=/; Max-Age=0',
                'psgitech_remember_me=; Path=/; Max-Age=0'
            ]
        });
        res.end(JSON.stringify({ success: true, message: 'Logged out successfully.', redirect: 'login.html?logged_out=true' }));
        return;
    }

    // 4. GET /profile or POST /profile
    if (pathname === '/profile') {
        const user = currentUser || users[3]; // Default to student if not logged in

        if (req.method === 'GET') {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify(sanitizeUser(user)));
            return;
        } else if (req.method === 'POST') {
            const body = await parseRequestBody(req);
            if (user) {
                if (body.name) user.name = body.name.trim();
                if (body.email) user.email = body.email.trim();
                if (body.phone) user.phone = body.phone.trim();
                if (body.department && user.role === 'STUDENT') user.department = body.department.trim();
                if (body.year && user.role === 'STUDENT') user.year = parseInt(body.year, 10);
                saveUsers();
            }
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, message: 'Profile updated successfully!', user: sanitizeUser(user) }));
            return;
        }
    }

    // 5. POST /change-password
    if (pathname === '/change-password' && req.method === 'POST') {
        const body = await parseRequestBody(req);
        const user = currentUser || users[3];
        const currentPassword = body.currentPassword;
        const newPassword = body.newPassword;
        const confirmPassword = body.confirmPassword;

        if (!currentPassword || !newPassword || !confirmPassword) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'All password fields are required.' }));
            return;
        }

        if (newPassword !== confirmPassword) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'New Password and Confirmation do not match.' }));
            return;
        }

        if (user && user.password && user.password !== currentPassword) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Current password does not match existing password.' }));
            return;
        }

        if (user) {
            user.password = newPassword;
            user.mustChangePassword = false;
            saveUsers();
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, message: 'Password updated successfully!' }));
        return;
    }

    // 6. GET/POST /admin/users
    if (pathname === '/admin/users') {
        if (req.method === 'GET') {
            const q = (parsedUrl.query.q || '').toLowerCase();
            const role = (parsedUrl.query.role || 'ALL').toUpperCase();
            const status = (parsedUrl.query.status || 'ALL').toUpperCase();

            const filtered = users.filter(u => {
                const matchQ = !q
                    || (u.name && u.name.toLowerCase().includes(q))
                    || (u.email && u.email.toLowerCase().includes(q))
                    || (u.registerNumber && u.registerNumber.toLowerCase().includes(q))
                    || (u.employeeId && u.employeeId.toLowerCase().includes(q));

                const matchRole = role === 'ALL' || u.role === role;

                let matchStatus = true;
                if (status === 'ACTIVE') matchStatus = u.active !== false;
                if (status === 'INACTIVE') matchStatus = u.active === false;

                return matchQ && matchRole && matchStatus;
            }).map(sanitizeUser);

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, count: filtered.length, users: filtered }));
            return;
        } else if (req.method === 'POST') {
            const body = await parseRequestBody(req);
            const action = body.action;

            if (action === 'create_librarian') {
                const newLib = {
                    userId: users.length + 1,
                    name: (body.name || '').trim(),
                    email: (body.email || '').trim(),
                    phone: (body.phone || '').trim(),
                    employeeId: (body.employeeId || '').trim().toUpperCase(),
                    role: 'LIBRARIAN',
                    active: true,
                    mustChangePassword: true,
                    password: body.initialPassword || 'InitialPassword@123',
                    createdAt: new Date().toISOString().slice(0, 19).replace('T', ' ')
                };
                users.unshift(newLib);
                saveUsers();
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true, message: `Librarian account created for ${newLib.name}.`, librarian: sanitizeUser(newLib) }));
                return;
            } else if (action === 'toggle_status') {
                const targetId = parseInt(body.userId, 10);
                const active = body.active === 'true' || body.active === true;
                const target = users.find(u => u.userId === targetId);
                if (target) {
                    target.active = active;
                    saveUsers();
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: true, message: `Account status updated to ${active ? 'ACTIVE' : 'INACTIVE'}` }));
                    return;
                }
            }
        }
    }

    // =========================================================================
    // Feature 2: Book Management API Endpoints
    // =========================================================================

    // 1. GET /books (Catalog listing & Featured shelves)
    if (pathname === '/books' && req.method === 'GET') {
        const isFeatured = parsedUrl.query.featured === 'true';
        const limit = parseInt(parsedUrl.query.limit, 10) || 50;
        let result = bookData.books.filter(b => !b.archived).map(enrichBook);
        if (isFeatured) {
            result = result.slice(0, limit);
        }
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, count: result.length, books: result }));
        return;
    }

    // 2. GET /books/search (Multi-criteria parameterized search)
    if (pathname === '/books/search' && req.method === 'GET') {
        const q = (parsedUrl.query.q || '').trim().toLowerCase();
        const searchType = (parsedUrl.query.searchType || 'all').toLowerCase();
        const category = (parsedUrl.query.category || 'ALL').toUpperCase();
        const department = (parsedUrl.query.department || 'ALL').toUpperCase();
        const availability = (parsedUrl.query.availability || 'ALL').toUpperCase();
        const includeArchived = parsedUrl.query.includeArchived === 'true';

        const enriched = bookData.books.map(enrichBook);
        const filtered = enriched.filter(b => {
            if (!includeArchived && b.archived) return false;

            if (q) {
                if (searchType === 'title' && !b.title.toLowerCase().includes(q)) return false;
                else if (searchType === 'author' && !b.author.toLowerCase().includes(q)) return false;
                else if (searchType === 'isbn') {
                    const cleanIsbn = (b.isbn || '').replace(/[^0-9X]/gi, '').toLowerCase();
                    const cleanQ = q.replace(/[^0-9X]/gi, '').toLowerCase();
                    if (!cleanIsbn.includes(cleanQ)) return false;
                } else if (searchType === 'all') {
                    const inTitle = b.title.toLowerCase().includes(q);
                    const inAuthor = b.author.toLowerCase().includes(q);
                    const inIsbn = (b.isbn || '').replace(/[^0-9X]/gi, '').toLowerCase().includes(q.replace(/[^0-9X]/gi, '').toLowerCase());
                    if (!inTitle && !inAuthor && !inIsbn) return false;
                }
            }

            if (category !== 'ALL' && b.category.toUpperCase() !== category) return false;
            if (department !== 'ALL' && b.department.toUpperCase() !== department) return false;

            if (availability === 'AVAILABLE' && b.inventory.availableCopies <= 0) return false;
            if (availability === 'UNAVAILABLE' && b.inventory.availableCopies > 0) return false;

            return true;
        });

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, count: filtered.length, books: filtered }));
        return;
    }

    // 3. GET /books/details (Full bibliographic metadata and staff physical copies)
    if (pathname === '/books/details' && req.method === 'GET') {
        const bookId = parseInt(parsedUrl.query.id, 10);
        const book = bookData.books.find(b => b.bookId === bookId);
        if (!book) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Book not found with ID: ' + bookId }));
            return;
        }

        const enriched = enrichBook(book);
        const copies = bookData.copies.filter(c => c.bookId === bookId);

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            book: enriched,
            inventory: enriched.inventory,
            copies: copies
        }));
        return;
    }

    // 4. POST /books/add (Transactional accession with initial copies)
    if (pathname === '/books/add' && req.method === 'POST') {
        const body = await parseRequestBody(req);
        const title = (body.title || '').trim();
        const author = (body.author || '').trim();
        const rawIsbn = (body.isbn || '').trim();
        const category = (body.category || 'General').trim();
        const department = (body.department || 'GENERAL').trim();
        const publisher = (body.publisher || '').trim();
        const edition = (body.edition || '').trim();
        const publicationYear = parseInt(body.publicationYear, 10) || new Date().getFullYear();
        const description = (body.description || '').trim();
        const initialCopies = Math.max(1, Math.min(50, parseInt(body.initialCopies, 10) || 1));

        if (!title || !author || !rawIsbn) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Title, Author, and ISBN are mandatory fields.' }));
            return;
        }

        const cleanIsbn = rawIsbn.replace(/[^0-9X]/gi, '').toUpperCase();
        if (cleanIsbn.length !== 10 && cleanIsbn.length !== 13) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Please enter a valid ISBN-10 or ISBN-13 format.' }));
            return;
        }

        // Duplicate ISBN check
        const duplicate = bookData.books.find(b => !b.archived && (b.isbn || '').replace(/[^0-9X]/gi, '').toUpperCase() === cleanIsbn);
        if (duplicate) {
            res.writeHead(409, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `A book with ISBN ${rawIsbn} is already registered (${duplicate.title}).` }));
            return;
        }

        const nextBookId = bookData.books.reduce((max, b) => Math.max(max, b.bookId), 0) + 1;
        const coverImageUrl = body.coverImageUrl || `https://covers.openlibrary.org/b/isbn/${cleanIsbn}-L.jpg`;

        const newBook = {
            bookId: nextBookId,
            isbn: rawIsbn,
            title,
            author,
            publisher,
            edition,
            publicationYear,
            category,
            department,
            description,
            coverImageUrl,
            archived: false,
            createdAt: new Date().toISOString().slice(0, 19).replace('T', ' ')
        };

        bookData.books.push(newBook);

        let maxCopyId = bookData.copies.reduce((max, c) => Math.max(max, c.copyId), 0);
        const createdCopies = [];
        for (let i = 1; i <= initialCopies; i++) {
            maxCopyId++;
            const copyNumberStr = i < 10 ? '0' + i : String(i);
            const copyCode = `PSG-BK${nextBookId}-C${copyNumberStr}`;
            const copyObj = {
                copyId: maxCopyId,
                copyCode,
                bookId: nextBookId,
                status: 'AVAILABLE',
                archived: false,
                createdAt: new Date().toISOString().slice(0, 19).replace('T', ' ')
            };
            bookData.copies.push(copyObj);
            createdCopies.push(copyObj);
        }

        saveBooks();

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: `Book "${title}" successfully accessioned with ${initialCopies} physical copies.`,
            book: enrichBook(newBook),
            copies: createdCopies
        }));
        return;
    }

    // 5. POST /books/update (Metadata update preserving ISBN)
    if (pathname === '/books/update' && req.method === 'POST') {
        const body = await parseRequestBody(req);
        const bookId = parseInt(body.bookId, 10);
        const book = bookData.books.find(b => b.bookId === bookId);

        if (!book) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Book not found with ID: ' + bookId }));
            return;
        }

        if (body.title) book.title = body.title.trim();
        if (body.author) book.author = body.author.trim();
        if (body.publisher !== undefined) book.publisher = body.publisher.trim();
        if (body.edition !== undefined) book.edition = body.edition.trim();
        if (body.publicationYear) book.publicationYear = parseInt(body.publicationYear, 10) || book.publicationYear;
        if (body.category) book.category = body.category.trim();
        if (body.department) book.department = body.department.trim();
        if (body.description !== undefined) book.description = body.description.trim();
        if (body.coverImageUrl) book.coverImageUrl = body.coverImageUrl.trim();

        saveBooks();

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: `Book "${book.title}" updated successfully.`,
            book: enrichBook(book)
        }));
        return;
    }

    // 6. POST /books/copies/add (Sequential next copy code generation)
    if (pathname === '/books/copies/add' && req.method === 'POST') {
        const body = await parseRequestBody(req);
        const bookId = parseInt(body.bookId, 10);
        const numberOfCopies = Math.max(1, Math.min(50, parseInt(body.numberOfCopies, 10) || 1));

        const book = bookData.books.find(b => b.bookId === bookId);
        if (!book) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Book not found with ID: ' + bookId }));
            return;
        }

        const existingCopies = bookData.copies.filter(c => c.bookId === bookId);
        let maxIndex = 0;
        for (const c of existingCopies) {
            const match = c.copyCode.match(/-C(\d+)$/);
            if (match) {
                const idx = parseInt(match[1], 10);
                if (idx > maxIndex) maxIndex = idx;
            }
        }

        let maxCopyId = bookData.copies.reduce((max, c) => Math.max(max, c.copyId), 0);
        const newCopies = [];
        for (let i = 1; i <= numberOfCopies; i++) {
            maxCopyId++;
            const copyNum = maxIndex + i;
            const copyNumberStr = copyNum < 10 ? '0' + copyNum : String(copyNum);
            const copyCode = `PSG-BK${bookId}-C${copyNumberStr}`;
            const copyObj = {
                copyId: maxCopyId,
                copyCode,
                bookId,
                status: 'AVAILABLE',
                archived: false,
                createdAt: new Date().toISOString().slice(0, 19).replace('T', ' ')
            };
            bookData.copies.push(copyObj);
            newCopies.push(copyObj);
        }

        saveBooks();

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: `Successfully added ${numberOfCopies} new physical copies for "${book.title}".`,
            bookId,
            copies: newCopies
        }));
        return;
    }

    // 7. POST /books/archive (Individual copy or entire book with guardrails)
    if (pathname === '/books/archive' && req.method === 'POST') {
        const body = await parseRequestBody(req);

        if (body.copyId) {
            const copyId = parseInt(body.copyId, 10);
            const copy = bookData.copies.find(c => c.copyId === copyId);
            if (!copy) {
                res.writeHead(404, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'Copy not found with ID: ' + copyId }));
                return;
            }

            if (copy.status !== 'AVAILABLE') {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({
                    success: false,
                    message: `Cannot archive copy with status: ${copy.status}. Only AVAILABLE copies can be archived.`
                }));
                return;
            }

            copy.status = 'ARCHIVED';
            copy.archived = true;
            saveBooks();

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                success: true,
                message: `Copy ${copy.copyCode} has been archived successfully.`,
                copyId
            }));
            return;
        }

        if (body.bookId) {
            const bookId = parseInt(body.bookId, 10);
            const book = bookData.books.find(b => b.bookId === bookId);
            if (!book) {
                res.writeHead(404, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'Book not found with ID: ' + bookId }));
                return;
            }

            const copies = bookData.copies.filter(c => c.bookId === bookId && !c.archived && c.status !== 'ARCHIVED');
            const activeOccupied = copies.some(c => c.status === 'BORROWED' || c.status === 'ALLOCATED' || c.status === 'TRANSFER_PENDING');
            if (activeOccupied) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({
                    success: false,
                    message: 'Cannot archive book while physical copies are actively borrowed, allocated, or pending transfer.'
                }));
                return;
            }

            book.archived = true;
            for (const c of bookData.copies.filter(c => c.bookId === bookId)) {
                c.status = 'ARCHIVED';
                c.archived = true;
            }
            saveBooks();

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                success: true,
                message: `Book "${book.title}" and all its copies have been safely archived.`,
                bookId
            }));
            return;
        }

        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, message: 'Either bookId or copyId is required for archival.' }));
        return;
    }

    // =========================================================================
    // Feature 3: Borrowing & Circulation REST Endpoints
    // =========================================================================

    // 1. POST /borrow/issue (Issue a physical copy to student)
    if (pathname === '/borrow/issue' && req.method === 'POST') {
        const body = await parseRequestBody(req);
        const regNo = (body.registerNumber || body.studentRegisterNumber || '').trim();
        const copyId = parseInt(body.copyId, 10);

        if (!regNo || !copyId) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Student Register Number and Copy ID are required.' }));
            return;
        }

        const student = users.find(u => (u.registerNumber || '').toLowerCase() === regNo.toLowerCase());
        if (!student) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Student with register number "${regNo}" was not found.` }));
            return;
        }

        if (!student.active) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Student account is inactive or suspended. Cannot issue books.' }));
            return;
        }

        if (student.role !== 'STUDENT') {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'User is not a student. Only registered students may borrow books.' }));
            return;
        }

        const copy = bookData.copies.find(c => c.copyId === copyId);
        if (!copy) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Physical copy #${copyId} does not exist.` }));
            return;
        }

        let isAllocatedCollection = false;
        if (copy.status === 'ALLOCATED') {
            const activeAlloc = allocations.find(a => a.copyId === copy.copyId && a.status === 'ALLOCATED');
            if (!activeAlloc || activeAlloc.studentId !== student.userId) {
                res.writeHead(409, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'This physical copy is currently held for another student.' }));
                return;
            }
            isAllocatedCollection = true;
        } else if (copy.archived || copy.status !== 'AVAILABLE') {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Physical copy ${copy.copyCode} is currently ${copy.status} and cannot be issued.` }));
            return;
        }

        const book = bookData.books.find(b => b.bookId === copy.bookId);
        if (!book || book.archived) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Cannot borrow copies from an archived book title.' }));
            return;
        }

        // Active borrowing limit check (3 books)
        const activeCount = borrowings.filter(t => t.studentId === student.userId && t.status === 'ACTIVE').length;
        if (activeCount >= 3) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Student has reached the maximum borrowing limit (3 active books).' }));
            return;
        }

        // Same-book restriction check
        const hasSameBook = borrowings.find(t => t.studentId === student.userId && t.bookId === copy.bookId && t.status === 'ACTIVE');
        if (hasSameBook) {
            const existingCopy = bookData.copies.find(c => c.copyId === hasSameBook.copyId);
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                success: false,
                message: `Student already has an active copy of this textbook (${existingCopy ? existingCopy.copyCode : 'PSG-BK' + copy.bookId}). Students cannot borrow multiple copies of the same book simultaneously.`
            }));
            return;
        }

        // Check if copy is already in ACTIVE transaction
        const copyActiveTx = borrowings.find(t => t.copyId === copyId && t.status === 'ACTIVE');
        if (copyActiveTx) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Physical copy ${copy.copyCode} is already borrowed in active transaction #${copyActiveTx.transactionId}.` }));
            return;
        }

        const today = new Date();
        const dueDate = new Date();
        dueDate.setDate(today.getDate() + 14);

        const fmt = d => d.toISOString().split('T')[0];
        const newTxId = (borrowings.reduce((max, t) => Math.max(max, t.transactionId || 0), 0) || 0) + 1;

        const newTx = {
            transactionId: newTxId,
            studentId: student.userId,
            bookId: copy.bookId,
            copyId: copy.copyId,
            issueDate: fmt(today),
            dueDate: fmt(dueDate),
            returnDate: null,
            status: 'ACTIVE',
            issuedBy: (currentUser && currentUser.userId) || 2,
            returnedBy: null,
            createdAt: today.toISOString(),
            updatedAt: today.toISOString()
        };

        // Update physical copy status
        copy.status = 'BORROWED';
        saveBooks();

        borrowings.push(newTx);
        saveBorrowings();

        if (isAllocatedCollection) {
            const activeAlloc = allocations.find(a => a.copyId === copy.copyId && a.status === 'ALLOCATED');
            if (activeAlloc) {
                activeAlloc.status = 'COLLECTED';
                activeAlloc.collectedAt = new Date().toISOString();
                activeAlloc.borrowTransactionId = newTx.transactionId;
                saveAllocations();

                const resObj = reservations.find(r => r.reservationId === activeAlloc.reservationId);
                if (resObj) {
                    resObj.status = 'FULFILLED';
                    resObj.updatedAt = new Date().toISOString();
                    saveReservations();
                }
            }
        }

        res.writeHead(201, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: isAllocatedCollection ? 'Book collected and issued successfully from hold allocation.' : 'Book issued successfully',
            data: {
                transactionId: newTx.transactionId,
                bookTitle: book.title,
                copyCode: copy.copyCode,
                issueDate: newTx.issueDate,
                dueDate: newTx.dueDate,
                status: 'ACTIVE',
                collectedFromAllocation: isAllocatedCollection
            }
        }));
        return;
    }

    // 2. POST /borrow/return or /borrowings/return (Process book return with automatic allocation)
    if ((pathname === '/borrow/return' || pathname === '/borrowings/return') && req.method === 'POST') {
        const body = await parseRequestBody(req);
        const txId = parseInt(body.transactionId, 10);

        if (!txId) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Valid transaction ID is required.' }));
            return;
        }

        const tx = borrowings.find(t => t.transactionId === txId);
        if (!tx) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Borrowing transaction #${txId} was not found.` }));
            return;
        }

        if (tx.status === 'RETURNED') {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'This book has already been returned. Duplicate return recording is prohibited.' }));
            return;
        }

        const todayStr = new Date().toISOString().split('T')[0];
        tx.status = 'RETURNED';
        tx.returnDate = todayStr;
        tx.returnedBy = (currentUser && currentUser.userId) || 2;
        tx.updatedAt = new Date().toISOString();

        const copy = bookData.copies.find(c => c.copyId === tx.copyId);
        const book = bookData.books.find(b => b.bookId === tx.bookId) || {};

        // Feature 5: Evaluate FIFO Reservation Queue for Automatic Allocation
        let allocationResult = null;
        const candidates = reservations
            .filter(r => r.bookId === tx.bookId && r.status === 'WAITING')
            .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt) || (a.reservationId - b.reservationId));

        let selectedCandidate = null;
        for (const cand of candidates) {
            const student = users.find(u => u.userId === cand.studentId);
            if (!student || student.active === false) continue;

            const activeLoans = borrowings.filter(b => b.studentId === cand.studentId && b.status === 'ACTIVE').length;
            const activeAllocs = allocations.filter(a => a.studentId === cand.studentId && a.status === 'ALLOCATED').length;
            if ((activeLoans + activeAllocs) >= 3) continue;

            const hasActiveLoanForBook = borrowings.some(b => b.studentId === cand.studentId && b.bookId === tx.bookId && b.status === 'ACTIVE');
            if (hasActiveLoanForBook) continue;

            selectedCandidate = cand;
            break;
        }

        if (selectedCandidate && copy) {
            selectedCandidate.status = 'ALLOCATED';
            selectedCandidate.updatedAt = new Date().toISOString();

            copy.status = 'ALLOCATED';

            const newAllocId = (allocations.reduce((m, a) => Math.max(m, a.allocationId || 0), 0) || 0) + 1;
            const expiresAtIso = new Date(Date.now() + 24 * 3600 * 1000).toISOString();
            const student = users.find(u => u.userId === selectedCandidate.studentId) || {};

            const newAlloc = {
                allocationId: newAllocId,
                reservationId: selectedCandidate.reservationId,
                studentId: selectedCandidate.studentId,
                bookId: tx.bookId,
                copyId: copy.copyId,
                copyCode: copy.copyCode,
                returnTransactionId: tx.transactionId,
                status: 'ALLOCATED',
                allocatedAt: new Date().toISOString(),
                expiresAt: expiresAtIso
            };
            allocations.push(newAlloc);
            saveAllocations();
            saveReservations();

            allocationResult = {
                allocationId: newAlloc.allocationId,
                copyId: copy.copyId,
                copyIdentifier: copy.copyCode,
                copyCode: copy.copyCode,
                bookTitle: book.title || 'Book',
                studentName: student.name || 'Student',
                registerNumber: student.registerNumber || '--',
                expiresAt: expiresAtIso
            };
        } else if (copy) {
            copy.status = 'AVAILABLE';
        }

        // Feature 6: Atomic overdue fine calculation upon return
        let fineRecord = null;
        if (tx.dueDate) {
            const due = new Date(tx.dueDate);
            const ret = new Date(tx.returnDate);
            const diffTime = ret.getTime() - due.getTime();
            const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
            if (diffDays > 0) {
                const fineAmount = (diffDays * 2.0).toFixed(2);
                const newFineId = (fines.reduce((m, f) => Math.max(m, f.fineId || 0), 0) || 0) + 1;
                const student = users.find(u => u.userId === tx.studentId) || {};
                fineRecord = {
                    fineId: newFineId,
                    borrowTransactionId: tx.transactionId,
                    studentId: tx.studentId,
                    bookId: tx.bookId,
                    copyId: tx.copyId,
                    overdueDays: diffDays,
                    chargeableDays: diffDays,
                    finePerDay: "2.00",
                    originalAmount: fineAmount,
                    paidAmount: "0.00",
                    waivedAmount: "0.00",
                    outstandingAmount: fineAmount,
                    status: 'UNPAID',
                    createdAt: new Date().toISOString(),
                    bookTitle: book.title || 'Book',
                    copyCode: copy ? copy.copyCode : '',
                    studentName: student.name || 'Student',
                    registerNumber: student.registerNumber || '--',
                    returnDate: tx.returnDate
                };
                fines.push(fineRecord);
                saveFines();
            }
        }

        saveBooks();
        saveBorrowings();

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: 'Book returned successfully',
            data: {
                transactionId: tx.transactionId,
                returnDate: tx.returnDate,
                status: 'RETURNED',
                copyCode: copy ? copy.copyCode : '',
                allocation: allocationResult,
                fine: fineRecord ? {
                    fineId: fineRecord.fineId,
                    overdueDays: fineRecord.overdueDays,
                    amount: fineRecord.originalAmount,
                    outstanding: fineRecord.outstandingAmount,
                    status: fineRecord.status
                } : null
            }
        }));
        return;
    }

    // 3. GET /borrow/student (Student currently borrowed & history)
    if (pathname === '/borrow/student' && req.method === 'GET') {
        let studentId = null;
        if (currentUser && currentUser.role === 'STUDENT') {
            studentId = currentUser.userId;
        } else if (currentUser && (currentUser.role === 'LIBRARIAN' || currentUser.role === 'ADMIN')) {
            if (queryParams.studentId) studentId = parseInt(queryParams.studentId, 10);
            else if (queryParams.registerNumber) {
                const s = users.find(u => (u.registerNumber || '').toLowerCase() === queryParams.registerNumber.toLowerCase());
                if (s) studentId = s.userId;
            } else {
                studentId = currentUser.userId;
            }
        } else {
            // Default to demo student if not logged in (e.g. preview mode)
            studentId = 4;
        }

        const type = (queryParams.type || 'active').toLowerCase();
        let list = borrowings.filter(t => t.studentId === studentId);

        if (type === 'history') {
            // All records
        } else if (type === 'returned') {
            list = list.filter(t => t.status === 'RETURNED');
        } else if (type === 'overdue') {
            const todayStr = new Date().toISOString().split('T')[0];
            list = list.filter(t => t.status === 'ACTIVE' && t.dueDate < todayStr);
        } else {
            // Default active
            list = list.filter(t => t.status === 'ACTIVE');
        }

        const enrichedList = list.map(enrichBorrowTransaction);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, count: enrichedList.length, data: enrichedList }));
        return;
    }

    // 4. GET /borrow/active (All active borrowings for staff)
    if (pathname === '/borrow/active' && req.method === 'GET') {
        let list = borrowings.filter(t => t.status === 'ACTIVE').map(enrichBorrowTransaction);
        const q = (queryParams.q || queryParams.query || '').trim().toLowerCase();

        if (q) {
            list = list.filter(tx =>
                tx.bookTitle.toLowerCase().includes(q) ||
                tx.studentName.toLowerCase().includes(q) ||
                tx.registerNumber.toLowerCase().includes(q) ||
                tx.copyCode.toLowerCase().includes(q)
            );
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, count: list.length, data: list }));
        return;
    }

    // 5. GET /borrow/history (Borrowing history)
    if (pathname === '/borrow/history' && req.method === 'GET') {
        let list = borrowings.map(enrichBorrowTransaction);

        if (currentUser && currentUser.role === 'STUDENT') {
            list = list.filter(tx => tx.studentId === currentUser.userId);
        } else {
            const q = (queryParams.q || queryParams.query || '').trim().toLowerCase();
            if (q) {
                list = list.filter(tx =>
                    tx.bookTitle.toLowerCase().includes(q) ||
                    tx.studentName.toLowerCase().includes(q) ||
                    tx.registerNumber.toLowerCase().includes(q) ||
                    tx.copyCode.toLowerCase().includes(q)
                );
            }
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, count: list.length, data: list }));
        return;
    }

    // 6. GET /borrow/overdue (Active overdue loans)
    if (pathname === '/borrow/overdue' && req.method === 'GET') {
        const todayStr = new Date().toISOString().split('T')[0];
        const list = borrowings.filter(t => t.status === 'ACTIVE' && t.dueDate < todayStr).map(enrichBorrowTransaction);

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, count: list.length, data: list }));
        return;
    }

    // 7. GET /borrow/lookup (Staff issue and return desk lookups)
    if (pathname === '/borrow/lookup' && req.method === 'GET') {
        const type = (queryParams.type || '').toLowerCase();

        if (type === 'student') {
            const regNo = (queryParams.registerNumber || queryParams.regNo || '').trim();
            if (!regNo) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'Register number is required.' }));
                return;
            }

            const student = users.find(u => (u.registerNumber || '').toLowerCase() === regNo.toLowerCase());
            if (!student) {
                res.writeHead(404, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: `Student with register number "${regNo}" was not found.` }));
                return;
            }

            const activeBorrowings = borrowings.filter(t => t.studentId === student.userId && t.status === 'ACTIVE').length;
            const remainingCapacity = Math.max(0, 3 - activeBorrowings);
            const isEligible = student.active && remainingCapacity > 0;

            let alreadyHasSameBook = false;
            if (queryParams.bookId) {
                const bId = parseInt(queryParams.bookId, 10);
                alreadyHasSameBook = borrowings.some(t => t.studentId === student.userId && t.bookId === bId && t.status === 'ACTIVE');
            }

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                success: true,
                data: {
                    studentId: student.userId,
                    studentName: student.name,
                    registerNumber: student.registerNumber,
                    department: student.department,
                    year: student.year,
                    accountStatus: student.active ? 'ACTIVE' : 'INACTIVE',
                    activeBorrowings,
                    maxLimit: 3,
                    remainingCapacity,
                    isEligible,
                    alreadyHasSameBook
                }
            }));
            return;
        }

        if (type === 'return') {
            const q = (queryParams.q || queryParams.query || '').trim().toLowerCase();
            let activeList = borrowings.filter(t => t.status === 'ACTIVE').map(enrichBorrowTransaction);

            if (q) {
                activeList = activeList.filter(tx =>
                    String(tx.transactionId) === q ||
                    tx.copyCode.toLowerCase() === q ||
                    tx.registerNumber.toLowerCase() === q ||
                    tx.bookTitle.toLowerCase().includes(q)
                );
            }

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, count: activeList.length, data: activeList }));
            return;
        }

        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, message: 'Valid lookup type ("student" or "return") is required.' }));
        return;
    }

    // =========================================================================
    // FEATURE 4: RESERVATION QUEUE ENDPOINTS
    // =========================================================================

    // 1. POST /reservations (Student joins waiting queue)
    if ((pathname === '/reservations' || pathname === '/reservations/') && req.method === 'POST') {
        const user = getSessionUser(req);
        if (!user) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Authentication required. Please log in.' }));
            return;
        }
        if (user.role !== 'STUDENT') {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Access denied. Only registered students may reserve books.' }));
            return;
        }

        const body = await parseRequestBody(req);
        const bookId = parseInt(body.bookId || queryParams.bookId, 10);
        if (!bookId || isNaN(bookId) || bookId <= 0) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Valid book ID is required.' }));
            return;
        }

        const book = bookData.books.find(b => b.bookId === bookId);
        if (!book) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Book not found.' }));
            return;
        }
        if (book.isArchived) {
            res.writeHead(409, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Cannot reserve copies of an archived book title.' }));
            return;
        }

        const copies = bookData.copies.filter(c => c.bookId === bookId);
        const reservableCopies = copies.filter(c => c.status !== 'ARCHIVED');
        const availableCopies = copies.filter(c => c.status === 'AVAILABLE');

        if (reservableCopies.length === 0) {
            res.writeHead(409, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'This book has no physical copies in active circulation to reserve.' }));
            return;
        }

        if (availableCopies.length > 0) {
            res.writeHead(409, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                success: false,
                message: 'Copies of this book are currently available for borrowing at the library desk. Reservations are only permitted when all copies are checked out.'
            }));
            return;
        }

        // Active borrow check
        const activeBorrow = borrowings.find(t => t.studentId === user.userId && t.bookId === bookId && t.status === 'ACTIVE');
        if (activeBorrow) {
            res.writeHead(409, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                success: false,
                message: 'You are currently borrowing an active copy of this book. Students cannot hold both a borrow loan and a reservation for the same title.'
            }));
            return;
        }

        // Duplicate reservation check
        const existingRes = reservations.find(r => r.studentId === user.userId && r.bookId === bookId && (r.status === 'WAITING' || r.status === 'ALLOCATED'));
        if (existingRes) {
            res.writeHead(409, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'You already hold an active reservation for this book.' }));
            return;
        }

        // Student active reservation limit check (3 max)
        const studentActiveResCount = reservations.filter(r => r.studentId === user.userId && (r.status === 'WAITING' || r.status === 'ALLOCATED')).length;
        if (studentActiveResCount >= 3) {
            res.writeHead(409, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'You have reached the maximum active reservation limit (3 active reservations).' }));
            return;
        }

        const newId = (reservations.reduce((max, r) => Math.max(max, r.reservationId || 0), 0) || 0) + 1;
        const nowIso = new Date().toISOString();
        const todayStr = nowIso.split('T')[0];

        const newRes = {
            reservationId: newId,
            studentId: user.userId,
            bookId,
            reservationDate: todayStr,
            status: 'WAITING',
            cancelledAt: null,
            cancelledBy: null,
            createdAt: nowIso,
            updatedAt: nowIso
        };
        reservations.push(newRes);
        saveReservations();

        const enriched = enrichReservation(newRes);

        res.writeHead(201, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: `Reservation placed successfully. You are #${enriched.queuePosition} in queue.`,
            data: {
                reservationId: enriched.reservationId,
                bookId: enriched.bookId,
                bookTitle: enriched.bookTitle,
                reservationDate: enriched.reservationDate,
                status: enriched.status,
                queuePosition: enriched.queuePosition,
                totalWaitingInQueue: enriched.totalWaitingInQueue
            }
        }));
        return;
    }

    // 2. GET /reservations (Book queue status OR staff all-queues)
    if ((pathname === '/reservations' || pathname === '/reservations/') && req.method === 'GET') {
        const user = getSessionUser(req);
        if (!user) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Authentication required. Please log in.' }));
            return;
        }

        const bookId = parseInt(queryParams.bookId, 10);
        if (bookId && !isNaN(bookId) && bookId > 0) {
            const bookQueue = reservations
                .filter(r => r.bookId === bookId && r.status === 'WAITING')
                .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt) || a.reservationId - b.reservationId);

            const waitingCount = bookQueue.length;
            const resData = {
                success: true,
                bookId,
                waitingCount
            };

            if (user.role === 'LIBRARIAN' || user.role === 'ADMIN') {
                resData.queue = bookQueue.map((r, idx) => {
                    const enriched = enrichReservation(r);
                    return {
                        reservationId: r.reservationId,
                        queuePosition: idx + 1,
                        studentName: enriched.studentName,
                        registerNumber: enriched.registerNumber,
                        reservationDate: r.reservationDate,
                        status: r.status
                    };
                });
            } else if (user.role === 'STUDENT') {
                const myRes = reservations.find(r => r.studentId === user.userId && r.bookId === bookId && (r.status === 'WAITING' || r.status === 'ALLOCATED'));
                if (myRes) {
                    const enriched = enrichReservation(myRes);
                    resData.myReservation = {
                        reservationId: myRes.reservationId,
                        queuePosition: enriched.queuePosition,
                        status: myRes.status
                    };
                } else {
                    resData.myReservation = null;
                }

                const currentlyBorrowed = borrowings.some(t => t.studentId === user.userId && t.bookId === bookId && t.status === 'ACTIVE');
                resData.currentlyBorrowedByMe = currentlyBorrowed;
            }

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify(resData));
            return;
        } else {
            // Staff all-queues
            if (user.role !== 'LIBRARIAN' && user.role !== 'ADMIN') {
                res.writeHead(403, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'Access denied. Only Librarians and Administrators can view all library queues.' }));
                return;
            }

            const activeQueue = reservations
                .filter(r => r.status === 'WAITING')
                .map(enrichReservation)
                .sort((a, b) => (a.bookTitle || '').localeCompare(b.bookTitle || '') || (a.queuePosition - b.queuePosition));

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                success: true,
                page: 1,
                pageSize: 50,
                count: activeQueue.length,
                reservations: activeQueue
            }));
            return;
        }
    }

    // 3. GET /reservations/my (Student view)
    if (pathname === '/reservations/my' && req.method === 'GET') {
        const user = getSessionUser(req);
        if (!user) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Authentication required. Please log in.' }));
            return;
        }

        let targetStudentId = user.userId;
        if ((user.role === 'LIBRARIAN' || user.role === 'ADMIN') && queryParams.studentId) {
            targetStudentId = parseInt(queryParams.studentId, 10) || user.userId;
        }

        const activeReservations = reservations
            .filter(r => r.studentId === targetStudentId && (r.status === 'WAITING' || r.status === 'ALLOCATED'))
            .map(enrichReservation)
            .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

        const reservationHistory = reservations
            .filter(r => r.studentId === targetStudentId && r.status !== 'WAITING' && r.status !== 'ALLOCATED')
            .map(enrichReservation)
            .sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt));

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            data: {
                activeReservations,
                reservationHistory,
                summary: {
                    activeCount: activeReservations.length,
                    maxLimit: 3,
                    remainingCapacity: Math.max(0, 3 - activeReservations.length)
                }
            }
        }));
        return;
    }

    // 4. POST /reservations/cancel (Cancel WAITING reservation)
    if (pathname === '/reservations/cancel' && req.method === 'POST') {
        const user = getSessionUser(req);
        if (!user) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Authentication required. Please log in.' }));
            return;
        }

        const body = await parseRequestBody(req);
        const resId = parseInt(body.reservationId || queryParams.reservationId, 10);
        if (!resId || isNaN(resId) || resId <= 0) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Valid reservation ID is required.' }));
            return;
        }

        const resObj = reservations.find(r => r.reservationId === resId);
        if (!resObj) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Reservation #${resId} not found.` }));
            return;
        }

        // Ownership or staff check
        if (user.role === 'STUDENT' && resObj.studentId !== user.userId) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Reservation #${resId} not found.` }));
            return;
        }

        if (resObj.status !== 'WAITING' && resObj.status !== 'ALLOCATED') {
            res.writeHead(409, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Reservation #${resId} cannot be cancelled because its status is ${resObj.status}.` }));
            return;
        }

        const nowIso = new Date().toISOString();
        let copyOutcome = null;
        let nextStudentName = null;
        let nextStudentRegisterNumber = null;

        if (resObj.status === 'ALLOCATED') {
            const activeAlloc = allocations.find(a => a.reservationId === resObj.reservationId && a.status === 'ALLOCATED');
            if (activeAlloc) {
                activeAlloc.status = 'CANCELLED';
                activeAlloc.cancelledAt = nowIso;
                activeAlloc.cancelledBy = user.userId;
                saveAllocations();

                const copy = bookData.copies.find(c => c.copyId === activeAlloc.copyId);
                const nextCandidates = reservations
                    .filter(r => r.bookId === resObj.bookId && r.status === 'WAITING')
                    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt) || (a.reservationId - b.reservationId));

                let nextCand = null;
                for (const cand of nextCandidates) {
                    const st = users.find(u => u.userId === cand.studentId);
                    if (!st || st.active === false) continue;
                    const activeLoans = borrowings.filter(b => b.studentId === cand.studentId && b.status === 'ACTIVE').length;
                    const activeAllocs = allocations.filter(a => a.studentId === cand.studentId && a.status === 'ALLOCATED').length;
                    if ((activeLoans + activeAllocs) >= 3) continue;
                    nextCand = cand;
                    break;
                }

                if (nextCand && copy) {
                    nextCand.status = 'ALLOCATED';
                    nextCand.updatedAt = nowIso;
                    copy.status = 'ALLOCATED';

                    const newAllocId = (allocations.reduce((m, a) => Math.max(m, a.allocationId || 0), 0) || 0) + 1;
                    const expiresAtIso = new Date(Date.now() + 24 * 3600 * 1000).toISOString();
                    const newAlloc = {
                        allocationId: newAllocId,
                        reservationId: nextCand.reservationId,
                        studentId: nextCand.studentId,
                        bookId: resObj.bookId,
                        copyId: copy.copyId,
                        copyCode: copy.copyCode,
                        status: 'ALLOCATED',
                        allocatedAt: nowIso,
                        expiresAt: expiresAtIso
                    };
                    allocations.push(newAlloc);
                    saveAllocations();

                    copyOutcome = 'REALLOCATED';
                    const nextSt = users.find(u => u.userId === nextCand.studentId) || {};
                    nextStudentName = nextSt.name;
                    nextStudentRegisterNumber = nextSt.registerNumber;
                } else if (copy) {
                    copy.status = 'AVAILABLE';
                    copyOutcome = 'MADE_AVAILABLE';
                }
                saveBooks();
            }
        }

        resObj.status = 'CANCELLED';
        resObj.cancelledAt = nowIso;
        resObj.cancelledBy = user.userId;
        resObj.updatedAt = nowIso;
        saveReservations();

        const respData = {
            reservationId: resObj.reservationId,
            status: 'CANCELLED',
            cancelledAt: resObj.cancelledAt
        };
        if (copyOutcome) {
            respData.copyOutcome = copyOutcome;
            if (user.role !== 'STUDENT' && copyOutcome === 'REALLOCATED') {
                respData.nextStudentName = nextStudentName;
                respData.nextStudentRegisterNumber = nextStudentRegisterNumber;
            }
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: 'Reservation cancelled successfully.',
            data: respData
        }));
        return;
    }

    // 5. GET /allocations (Staff Hold Shelf / Student view)
    if ((pathname === '/allocations' || pathname === '/allocations/') && req.method === 'GET') {
        const user = getSessionUser(req);
        if (!user) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Authentication required. Please log in.' }));
            return;
        }

        const isMine = queryParams.mine === 'true' || user.role === 'STUDENT';
        if (isMine) {
            const studentAllocs = allocations
                .filter(a => a.studentId === user.userId && a.status === 'ALLOCATED')
                .map(a => {
                    const b = bookData.books.find(bk => bk.bookId === a.bookId) || {};
                    const u = users.find(usr => usr.userId === a.studentId) || {};
                    return {
                        ...a,
                        bookTitle: b.title || 'Book',
                        bookIsbn: b.isbn || '',
                        studentName: u.name || '',
                        registerNumber: u.registerNumber || ''
                    };
                });

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, data: studentAllocs }));
            return;
        }

        if (user.role !== 'LIBRARIAN' && user.role !== 'ADMIN') {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Access denied.' }));
            return;
        }

        const statusFilter = queryParams.status || 'ALLOCATED';
        const filteredAllocs = allocations
            .filter(a => !statusFilter || a.status === statusFilter)
            .map(a => {
                const b = bookData.books.find(bk => bk.bookId === a.bookId) || {};
                const u = users.find(usr => usr.userId === a.studentId) || {};
                return {
                    ...a,
                    bookTitle: b.title || 'Book',
                    bookIsbn: b.isbn || '',
                    studentName: u.name || '',
                    registerNumber: u.registerNumber || ''
                };
            })
            .sort((a, b) => new Date(b.allocatedAt) - new Date(a.allocatedAt));

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            data: {
                allocations: filteredAllocs,
                totalCount: filteredAllocs.length,
                page: 1,
                limit: 50,
                totalPages: 1
            }
        }));
        return;
    }

    // =========================================================================
    // Feature 6: Fines & UPI Payments APIs
    // =========================================================================

    // 1. GET /my-fines
    if ((pathname === '/my-fines' || pathname === '/fines/my') && req.method === 'GET') {
        const user = getSessionUser(req);
        if (!user) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Authentication required. Please log in.' }));
            return;
        }

        const studentId = user.userId;
        const studentFines = fines.filter(f => f.studentId === studentId);
        const studentPayments = payments.filter(p => p.studentId === studentId);

        let totalOutstanding = 0;
        let totalPaid = 0;
        let totalWaived = 0;
        let unpaidCount = 0;

        for (const f of studentFines) {
            if (f.status === 'UNPAID' || f.status === 'PARTIALLY_PAID') {
                unpaidCount++;
                totalOutstanding += parseFloat(f.outstandingAmount || 0);
            }
            totalPaid += parseFloat(f.paidAmount || 0);
            totalWaived += parseFloat(f.waivedAmount || 0);
        }

        // Projected fines
        const projectedFines = [];
        const activeLoans = borrowings.filter(b => b.studentId === studentId && b.status === 'ACTIVE');
        const now = new Date();
        for (const loan of activeLoans) {
            if (loan.dueDate) {
                const due = new Date(loan.dueDate);
                if (now > due) {
                    const days = Math.ceil((now.getTime() - due.getTime()) / (1000 * 60 * 60 * 24));
                    if (days > 0) {
                        const b = bookData.books.find(bk => bk.bookId === loan.bookId) || {};
                        const c = bookData.copies.find(cp => cp.copyId === loan.copyId) || {};
                        projectedFines.push({
                            transactionId: loan.transactionId,
                            bookTitle: b.title || 'Book',
                            copyCode: c.copyCode || 'PSG-BK-C01',
                            dueDate: loan.dueDate,
                            overdueDays: days,
                            ratePerDay: "2.00",
                            projectedAmount: (days * 2.0).toFixed(2)
                        });
                    }
                }
            }
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            summary: {
                totalOutstanding: totalOutstanding.toFixed(2),
                totalPaid: totalPaid.toFixed(2),
                totalWaived: totalWaived.toFixed(2),
                unpaidCount: unpaidCount,
                borrowingBlocked: totalOutstanding > 0
            },
            fines: studentFines,
            projectedFines: projectedFines,
            payments: studentPayments
        }));
        return;
    }

    // 2. GET /fines & POST /fines (Staff Search / Admin Waiver)
    if ((pathname === '/fines' || pathname === '/fines/') && req.method === 'GET') {
        const user = getSessionUser(req);
        if (!user || (user.role !== 'LIBRARIAN' && user.role !== 'ADMIN')) {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Access denied. Staff only.' }));
            return;
        }

        let filtered = [...fines];
        if (queryParams.registerNumber) {
            const q = queryParams.registerNumber.toLowerCase();
            filtered = filtered.filter(f => (f.registerNumber || '').toLowerCase().includes(q));
        }
        if (queryParams.status && queryParams.status !== 'ALL') {
            filtered = filtered.filter(f => f.status === queryParams.status);
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            fines: filtered,
            totalCount: filtered.length,
            page: 1,
            pageSize: 50,
            totalPages: 1
        }));
        return;
    }

    if ((pathname === '/fines' || pathname === '/fines/') && req.method === 'POST') {
        const user = getSessionUser(req);
        if (!user || user.role !== 'ADMIN') {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Access denied. Only Administrators can grant fine waivers.' }));
            return;
        }

        const body = await parseRequestBody(req);
        const fineId = parseInt(body.fineId, 10);
        const amount = parseFloat(body.amount);
        const reason = (body.reason || '').trim();

        if (!fineId || isNaN(amount) || amount <= 0 || !reason) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Fine ID, valid positive amount, and mandatory reason are required.' }));
            return;
        }

        const fine = fines.find(f => f.fineId === fineId);
        if (!fine) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Fine record not found.' }));
            return;
        }

        const out = parseFloat(fine.outstandingAmount || 0);
        if (amount > out) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: `Waiver amount exceeds outstanding balance (${out.toFixed(2)}).` }));
            return;
        }

        const currentWaived = parseFloat(fine.waivedAmount || 0);
        const newWaived = currentWaived + amount;
        const newOut = out - amount;

        fine.waivedAmount = newWaived.toFixed(2);
        fine.outstandingAmount = newOut.toFixed(2);
        fine.status = newOut <= 0 ? 'WAIVED' : 'PARTIALLY_PAID';
        saveFines();

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: `Waiver of INR ${amount.toFixed(2)} applied successfully.`,
            fine: fine
        }));
        return;
    }

    // 3. GET /payments & POST /payments
    if ((pathname === '/payments' || pathname === '/payments/') && req.method === 'GET') {
        const user = getSessionUser(req);
        if (!user) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Authentication required.' }));
            return;
        }

        if (user.role === 'STUDENT') {
            const studentPayments = payments.filter(p => p.studentId === user.userId);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, payments: studentPayments }));
            return;
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, payments: payments, totalCount: payments.length }));
        return;
    }

    if ((pathname === '/payments' || pathname === '/payments/') && req.method === 'POST') {
        const user = getSessionUser(req);
        if (!user) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Authentication required.' }));
            return;
        }

        const body = await parseRequestBody(req);
        const action = (body.action || 'initiate').toLowerCase();

        if (action === 'initiate') {
            const fineId = parseInt(body.fineId, 10);
            const fine = fines.find(f => f.fineId === fineId);
            if (!fine) {
                res.writeHead(404, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'Fine record not found.' }));
                return;
            }

            const activeAttempt = payments.find(p => p.fineId === fineId && (p.status === 'INITIATED' || p.status === 'PENDING_VERIFICATION'));
            if (activeAttempt) {
                res.writeHead(409, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'An active payment attempt is already in progress for this fine.' }));
                return;
            }

            const requestedAmt = body.amount ? parseFloat(body.amount) : parseFloat(fine.outstandingAmount);
            const newPaymentId = (payments.reduce((m, p) => Math.max(m, p.paymentId || 0), 0) || 0) + 1;
            const paymentCode = `FINE-${fineId}-${Date.now() % 1000000}`;
            const student = users.find(u => u.userId === user.userId) || {};

            const newPayment = {
                paymentId: newPaymentId,
                fineId: fineId,
                studentId: user.userId,
                amount: requestedAmt.toFixed(2),
                method: 'UPI_MANUAL',
                status: 'INITIATED',
                paymentCode: paymentCode,
                referenceId: null,
                disputed: false,
                studentName: student.name || 'Student',
                registerNumber: student.registerNumber || '--',
                bookTitle: fine.bookTitle || 'Book',
                createdAt: new Date().toISOString()
            };
            payments.push(newPayment);
            savePayments();

            const upiUri = `upi://pay?pa=library@psgitech.ac.in&pn=PSG+iTech+Library&am=${requestedAmt.toFixed(2)}&cu=INR&tn=${paymentCode}`;
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                success: true,
                payment: newPayment,
                instructions: {
                    upiUri: upiUri,
                    upiId: 'library@psgitech.ac.in',
                    payeeName: 'PSG iTech Library',
                    paymentCode: paymentCode
                }
            }));
            return;
        }

        if (action === 'submit_reference' || action === 'submitreference') {
            const paymentId = parseInt(body.paymentId, 10);
            const ref = (body.reference || '').trim();
            const payment = payments.find(p => p.paymentId === paymentId);

            if (!payment || payment.studentId !== user.userId) {
                res.writeHead(404, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'Payment attempt not found.' }));
                return;
            }

            const dup = payments.find(p => p.paymentId !== paymentId && p.referenceId === ref);
            if (dup) {
                res.writeHead(409, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'UPI reference ID has already been recorded on another payment.' }));
                return;
            }

            payment.referenceId = ref;
            payment.status = 'PENDING_VERIFICATION';
            payment.updatedAt = new Date().toISOString();
            savePayments();

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, message: 'UPI reference submitted.', payment: payment }));
            return;
        }

        if (action === 'cancel') {
            const paymentId = parseInt(body.paymentId, 10);
            const payment = payments.find(p => p.paymentId === paymentId);
            if (payment && payment.studentId === user.userId && payment.status === 'INITIATED') {
                payment.status = 'CANCELLED';
                savePayments();
            }
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, message: 'Payment cancelled.' }));
            return;
        }

        if (action === 'dispute') {
            const paymentId = parseInt(body.paymentId, 10);
            const payment = payments.find(p => p.paymentId === paymentId);
            if (payment && payment.studentId === user.userId) {
                payment.disputed = true;
                payment.disputeNote = body.reason || body.note || '';
                savePayments();
            }
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, message: 'Dispute submitted.' }));
            return;
        }
    }

    // 4. GET /payments/verify & POST /payments/verify
    if ((pathname === '/payments/verify' || pathname === '/payments/verify/') && req.method === 'GET') {
        const user = getSessionUser(req);
        if (!user || (user.role !== 'LIBRARIAN' && user.role !== 'ADMIN')) {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Access denied.' }));
            return;
        }

        const pending = payments.filter(p => p.status === 'PENDING_VERIFICATION');
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, pendingVerifications: pending, count: pending.length }));
        return;
    }

    if ((pathname === '/payments/verify' || pathname === '/payments/verify/') && req.method === 'POST') {
        const user = getSessionUser(req);
        if (!user || (user.role !== 'LIBRARIAN' && user.role !== 'ADMIN')) {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Access denied.' }));
            return;
        }

        const body = await parseRequestBody(req);
        const action = (body.action || 'verify').toLowerCase();
        const paymentId = parseInt(body.paymentId, 10);
        const payment = payments.find(p => p.paymentId === paymentId);

        if (!payment) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Payment not found.' }));
            return;
        }

        if (action === 'verify') {
            const fine = fines.find(f => f.fineId === payment.fineId);
            if (!fine) {
                res.writeHead(404, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'Fine record not found.' }));
                return;
            }

            const pAmt = parseFloat(payment.amount);
            const currentPaid = parseFloat(fine.paidAmount || 0);
            const currentOut = parseFloat(fine.outstandingAmount || 0);

            const newPaid = currentPaid + pAmt;
            const newOut = Math.max(0, currentOut - pAmt);

            fine.paidAmount = newPaid.toFixed(2);
            fine.outstandingAmount = newOut.toFixed(2);
            fine.status = newOut <= 0 ? 'PAID' : 'PARTIALLY_PAID';

            payment.status = 'SUCCESS';
            payment.receiptNumber = `RCPT-2026-${String(payment.paymentId).padStart(5, '0')}`;
            payment.verifiedBy = user.userId;
            payment.verifiedAt = new Date().toISOString();

            saveFines();
            savePayments();

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, message: `Payment verified. Receipt: ${payment.receiptNumber}`, payment: payment }));
            return;
        }

        if (action === 'reject') {
            const reason = (body.reason || '').trim();
            if (!reason) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: false, message: 'Rejection reason is mandatory.' }));
                return;
            }

            payment.status = 'REJECTED';
            payment.rejectionReason = reason;
            savePayments();

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, message: 'Payment rejected.', payment: payment }));
            return;
        }
    }

    // 5. GET /receipt
    if ((pathname === '/receipt' || pathname === '/payments/receipt') && req.method === 'GET') {
        const paymentId = parseInt(queryParams.paymentId, 10);
        const payment = payments.find(p => p.paymentId === paymentId);

        if (!payment || (payment.status !== 'SUCCESS' && payment.status !== 'REFUNDED')) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, message: 'Verified receipt not found.' }));
            return;
        }

        const fine = fines.find(f => f.fineId === payment.fineId) || {};
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            receipt: {
                receiptNumber: payment.receiptNumber || 'RCPT-PREVIEW',
                paymentId: payment.paymentId,
                paymentCode: payment.paymentCode,
                fineId: payment.fineId,
                borrowTransactionId: fine.borrowTransactionId,
                studentName: payment.studentName,
                registerNumber: payment.registerNumber,
                bookTitle: payment.bookTitle,
                originalFineAmount: fine.originalAmount || payment.amount,
                amountPaidThisPayment: payment.amount,
                totalPaidSoFar: fine.paidAmount || payment.amount,
                totalWaived: fine.waivedAmount || "0.00",
                remainingOutstanding: fine.outstandingAmount || "0.00",
                paymentMethod: 'UPI_MANUAL',
                referenceId: payment.referenceId,
                paymentDate: payment.verifiedAt || payment.createdAt,
                status: payment.status,
                isRefunded: payment.status === 'REFUNDED'
            }
        }));
        return;
    }

    // =========================================================================
    // Static File Serving
    // =========================================================================
    const filePath = path.join(WEBAPP_DIR, pathname);

    fs.stat(filePath, (err, stats) => {
        if (!err && stats.isFile()) {
            const ext = path.extname(filePath).toLowerCase();
            const contentType = MIME_TYPES[ext] || 'application/octet-stream';
            res.writeHead(200, { 'Content-Type': contentType });
            fs.createReadStream(filePath).pipe(res);
        } else {
            res.writeHead(404, { 'Content-Type': 'text/html; charset=UTF-8' });
            res.end(`<h1>404 Not Found</h1><p>The requested file ${pathname} was not found on PSG iTech Library.</p><p><a href="/index.html">Return to Homepage</a></p>`);
        }
    });
});

server.listen(PORT, () => {
    console.log(`[PSG iTech Library] Unified Server running at http://localhost:${PORT}/`);
    console.log(`[PSG iTech Library] Serving files from: ${WEBAPP_DIR}`);
});
