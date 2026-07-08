# Optimistic Locking vs Pessimistic Locking

## Introduction

Concurrency is one of the biggest challenges in backend systems.

Imagine an e-commerce application where there is only **one iPhone left in stock**. Two customers click the **Buy Now** button at almost the same time.

Without proper concurrency control, both customers may successfully place the order even though only one product exists. This problem is known as **Overselling**, and it occurs because multiple transactions modify the same data concurrently.

In this guide, we'll understand:

* Why concurrency problems occur
* Why `@Transactional` is not enough
* Optimistic Locking
* Pessimistic Locking
* Spring Boot implementation
* Performance comparison
* Production use cases

# The Problem

Consider the following product.

```text
Product
--------------------
Name     : iPhone 17
Stock    : 1
```

Now two users purchase the product simultaneously.

```text
             User A                  User B
                │                       │
                ▼                       ▼
          Read Stock = 1          Read Stock = 1
                │                       │
                ▼                       ▼
          Create Order           Create Order
                │                       │
                ▼                       ▼
       Update Stock = 0        Update Stock = 0
                │                       │
                └──────────┬────────────┘
                           ▼
                  Database Updated
```

Result:

```text
Orders Created : 2

Available Stock : 0
```

The application has sold two products while only one existed. This is known as the **Lost Update Problem** or **Overselling Problem**.

# Why Does This Happen?

Both transactions read the same data before either transaction commits.

Timeline:

```text
Time
│
├─────────────►

Thread A
Read Stock = 1
                 Update Stock = 0

Thread B
Read Stock = 1
                 Update Stock = 0
```

Neither transaction is aware of the other.

# Is @Transactional Enough?

Many developers believe that using `@Transactional` automatically prevents concurrency issues. Unfortunately, this is not true.

`@Transactional` provides:

* Atomicity
* Consistency
* Isolation
* Durability

However, it **does not automatically prevent two transactions from reading the same row simultaneously**. Both transactions can still execute successfully and overwrite each other's changes depending on the isolation level.

# What is Database Locking?

Database locking is a concurrency control mechanism used to ensure data consistency when multiple transactions access the same record.

There are two common strategies:

```text
Database Locking
        │
        ├──────────────► Optimistic Locking
        │
        └──────────────► Pessimistic Locking
```

# Optimistic Locking

## Concept

Optimistic Locking assumes that conflicts are **rare**. Instead of locking the database row, it allows multiple transactions to read the data simultaneously.

Before updating the record, Hibernate checks whether another transaction has already modified it. This is achieved using a **version column**.

## Flow

```text
User A                          User B

Read Version = 1           Read Version = 1

      │                           │
      ▼                           ▼
Update Version = 2      Update WHERE Version = 1

                            No rows updated

                                  ↓

                        OptimisticLockException
```

## Spring Boot Implementation

Entity

```java
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Integer quantity;

    @Version
    private Long version;
}
```

The `@Version` annotation automatically manages the version column.

Hibernate generates SQL similar to:

```sql
UPDATE products
SET quantity = ?, version = version + 1
WHERE id = ?
AND version = ?;
```

If no rows are updated, Hibernate throws:

```text
OptimisticLockException
```

## Advantages

* High performance
* No row locking
* Better scalability
* High throughput
* Excellent for read-heavy systems

## Disadvantages

* Failed transactions must retry
* Retry logic required
* Poor choice when conflicts are frequent

## Best Use Cases

* Product catalog
* User profile updates
* Blog management
* Product reviews
* Configuration management

# Pessimistic Locking

## Concept

Pessimistic Locking assumes that conflicts are **common**. Instead of detecting conflicts later, it prevents them by locking the row immediately.

Other transactions must wait until the lock is released.

## Flow

```text
Thread A

Acquire Lock
      │
      ▼
Read Product
      │
      ▼
Update Product
      │
      ▼
Commit
      │
      ▼
Release Lock

----------------------------

Thread B

Waiting...

Acquire Lock

Read Updated Product

Stock = 0

Out Of Stock
```

## Spring Boot Implementation

Repository

```java
public interface ProductRepository
        extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findById(Long id);
}
```

Service

```java
@Transactional
public void purchase(Long id)
        throws InterruptedException {

    Product product =
            repository.findById(id)
                    .orElseThrow();

    if (product.getQuantity() <= 0) {
        throw new RuntimeException(
                "Out of stock");
    }

    Thread.sleep(10000);

    product.setQuantity(
            product.getQuantity() - 1);
}
```

Hibernate generates SQL similar to:

```sql
SELECT *
FROM products
WHERE id = ?
FOR UPDATE;
```

The `FOR UPDATE` clause locks the selected row until the transaction commits or rolls back.

## Advantages

* Strong consistency
* Prevents lost updates
* No retry logic required
* Suitable for critical business operations

## Disadvantages

* Lower concurrency
* Reduced throughput
* Waiting transactions
* Possibility of deadlocks
* Lower scalability

## Best Use Cases

* Banking
* Payment processing
* Inventory management
* Flight booking
* Hotel reservation
* Wallet transactions

# Optimistic vs Pessimistic Locking

| Feature             | Optimistic Locking | Pessimistic Locking |
| ------------------- | ------------------ | ------------------- |
| Row Lock            | No                 | Yes                 |
| Conflict Detection  | During Update      | Before Update       |
| Performance         | High               | Medium              |
| Throughput          | High               | Lower               |
| Retry Required      | Yes                | No                  |
| Waiting             | No                 | Yes                 |
| Deadlock Risk       | No                 | Possible            |
| Read Heavy Systems  | Excellent          | Poor                |
| Write Heavy Systems | Moderate           | Good                |

# Which One Should You Choose?

### Choose Optimistic Locking when:

* Reads are much more frequent than writes.
* Data conflicts are rare.
* High scalability is important.
* Better throughput is required.

### Choose Pessimistic Locking when:

* Data consistency is critical.
* Conflicts occur frequently.
* Lost updates cannot be tolerated.
* Business operations involve money or inventory.

# How Real Companies Use Them

Most production systems use **both** strategies depending on the use case.

### E-commerce

* Product catalog -> Optimistic Locking
* Inventory updates -> Pessimistic Locking or distributed reservation systems

### Banking Applications

* Account balance updates -> Pessimistic Locking
* Customer profile updates -> Optimistic Locking

### Social Media Platforms

* Profile updates -> Optimistic Locking
* Payment or subscription processing -> Pessimistic Locking

# Relationship with Previous Tutorials

This topic complements other concurrency concepts:

```text
Duplicate Requests
        │
        ▼
Idempotency
        │
        ▼
Distributed Locking (Redis)
        │
        ▼
Database Locking
        │
        ▼
Consistent Data
```

Each mechanism solves a different problem:

| Concept                | Solves                                                              |
| ---------------------- | ------------------------------------------------------------------- |
| Idempotency            | Duplicate API retries                                               |
| Redis Distributed Lock | Prevents concurrent execution across multiple application instances |
| Optimistic Locking     | Detects concurrent updates before saving                            |
| Pessimistic Locking    | Prevents concurrent updates by locking rows                         |

# Key Takeaways

* Concurrency issues can lead to lost updates and overselling.
* `@Transactional` alone does not prevent concurrent modifications.
* Optimistic Locking detects conflicts using a version column.
* Pessimistic Locking prevents conflicts by locking rows.
* Choose the locking strategy based on your application's read/write patterns and consistency requirements.
* In real-world systems, different parts of the application often use different locking strategies to balance performance and correctness.