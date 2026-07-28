# Optimistic Locking Isn't Enough | Implementing Retry Mechanism in Spring Boot

## Introduction

In the previous section, we learned how **Optimistic Locking** prevents the **Lost Update Problem** using the `@Version` annotation.

At first glance, the solution looks complete.

If two users try to purchase the same product simultaneously, one transaction succeeds while the other receives an `OptimisticLockException`.

For an inventory containing **only one item**, this behavior is correct because only one customer should be able to purchase the product.

However, let's consider a different scenario.

```text
Product : iPhone 17

Available Stock = 2
Version = 1
```

Now two customers purchase the product at exactly the same time.

Logically, **both customers should succeed** because there are two products available.

Surprisingly, one of them still receives an `OptimisticLockException`.

Why?

This is the problem we'll solve in this section.

# Understanding the Problem

Suppose the inventory contains:

```text
Product

Name : iPhone 17

Available Stock : 2

Version : 1
```

Two customers click **Buy Now** simultaneously.

Both transactions read the same data.

```text
Customer A

Stock = 2

Version = 1


Customer B

Stock = 2

Version = 1
```

Both decide that stock is available.

Customer A updates the record.

```text
Stock = 1

Version = 2
```

Update succeeds.

Customer B now tries to update the same row.

Hibernate executes:

```sql
UPDATE products
SET available_stock = 1, version = 2
WHERE id = 1 AND version = 1;
```

However, the database row already contains:

```text
Version = 2
```

No rows are updated.

Hibernate throws

```text
ObjectOptimisticLockingFailureException
```

# But There Is Still Stock Available!

This is the interesting part.

The inventory now contains

```text
Available Stock = 1
```

One iPhone is still available.

The second customer could have purchased it.

Instead, the application returned an exception.

The system prevented **overselling**, but it also rejected a perfectly valid purchase.

# This Is Known as a False Conflict

The conflict wasn't caused because inventory was exhausted.

It occurred because two transactions attempted to modify the same row at the same time.

This is called a **False Conflict**.

```text
      Business State

        Stock = 1

           ↓

    Purchase Possible

--------------------------

    Database State

    Version Changed

           ↓

   Conflict Detected
```

Optimistic Locking detects **data conflicts**, not **business conflicts**.

# How Optimistic Locking Works

Optimistic Locking simply asks ***Has another transaction modified this row since I read it?***

If the answer is **Yes**, it throws an exception.

It does **not** ask ***Is there still stock available?***

That business decision belongs to your application.

# Why This Happens

Let's visualize the execution.

```text
    Customer A

   Read Product

    Stock = 2

   Version = 1

       ↓

  Update Product

    Stock = 1

   Version = 2

       ↓

    Success

------------------------------------------------

    Customer B

   Read Product

    Stock = 2

   Version = 1

       ↓

  Update Product

 WHERE Version = 1

       ↓

  0 Rows Updated

       ↓

OptimisticLockException
```

Although one item is still available, Customer B fails because the version changed.

# The Correct Solution

Instead of immediately returning an error, the application should retry.

The flow becomes:

```text
    Read Product

        ↓

      Update

        ↓

     Conflict?

        ↓

       YES

        ↓

Reload Latest Product

        ↓

  Stock Available?

        ↓
        
       YES
        
        ↓
        
  Retry Purchase
        
        ↓
        
     Success
```

If stock is no longer available:

```text
Reload Product

      ↓

  Stock = 0

      ↓

Return Out Of Stock
```

# Retry Mechanism

The retry process is simple.

```text
    Attempt 1

       ↓

    Conflict

       ↓

  Reload Product

       ↓

    Attempt 2

       ↓

    Conflict?

       ↓

   Retry Again

       ↓

    Success

       or

   Out Of Stock
```

Most production systems limit the number of retries.

For example:

```text
Maximum Attempts = 3
```

If all retries fail:

```text
Return Error
```

![Flow Dig](https://github.com/nakulmitra/modern-backend-engineering/blob/master/images/database-locking/Optimistic%20Locking%20with%20Retry.png)

# Spring Boot Implementation

A simple retry loop looks like this:

```java
public String purchaseUsingOptimisticLock(Long id) {
    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            purchase(id);
            return "Order placed successfully";
        } catch (ObjectOptimisticLockingFailureException ex) {
            if (attempt == 3) {
                throw ex;
            }

            System.out.println("Retry Attempt : " + attempt);
        }
    }
    throw new RuntimeException("Unable to place order");
}
```

The actual database operation remains transactional.

```java
@Transactional
public void purchase(Long id) {

    Product product = repository.findById(id)
            .orElseThrow();

    if (product.getAvailableStock() <= 0) {

        throw new RuntimeException("Out of stock");
    }

    product.setAvailableStock(
            product.getAvailableStock() - 1);

    repository.saveAndFlush(product);
}
```

Using `saveAndFlush()` forces Hibernate to execute the SQL immediately, allowing the version conflict to be detected within the transaction.

# Why Retry Must Use a New Transaction

One common mistake is retrying inside the same transaction.

```text
     Transaction

         ↓

    Read Product

         ↓

      Conflict

         ↓

       Retry

Same Persistence Context
```

The entity state inside the persistence context is already stale.

Instead, every retry should execute in a **new transaction**.

```text
     Attempt 1

   Transaction 1

        ↓

     Conflict

        ↓

  Transaction Ends

        ↓

     Attempt 2

   Transaction 2

        ↓

Read Latest Product

        ↓

     Success
```

This ensures the latest database state is loaded before retrying.

# Production Improvements

A fixed retry interval is simple but not ideal.

Instead of retrying immediately:

```text
Retry

Retry

Retry
```

Production systems commonly use **Exponential Backoff**.

```text
    Attempt 1
    
     100 ms
    
       ↓
    
    Attempt 2
    
     200 ms
    
       ↓
    
    Attempt 3
    
     400 ms
    
       ↓
    
    Attempt 4
    
     800 ms
```

This reduces contention and improves system stability under high load.

# When Should You Retry?

Retry only when the conflict is temporary.

Suitable scenarios include:

* Inventory updates
* Shopping cart checkout
* Ticket booking
* Product reservations
* Wallet balance updates

Do **not** retry automatically for business validation failures such as:

* Out of stock
* Invalid product
* Invalid payment
* Unauthorized access

# Real-World Example

Suppose an online store has:

```text
Available Stock = 5
```

Five customers purchase simultaneously.

Optimistic Locking may cause a few version conflicts.

Instead of rejecting those customers immediately, the application retries.

Each retry loads the latest stock and attempts the update again.

Eventually:

```text
Customer 1 → Success

Customer 2 → Success

Customer 3 → Success

Customer 4 → Success

Customer 5 → Success
```

When stock reaches zero:

```text
    Customer 6
    
        ↓
    
  Reload Product
    
        ↓
    
    Stock = 0
    
        ↓
    
    Out Of Stock
```

This behavior matches the business expectation.

# Relationship with Previous Tutorials

This tutorial builds on the previous videos in the series.

```text
Duplicate API Requests
          │
          ▼
Idempotent APIs
          │
          ▼
Redis Distributed Locking
          │
          ▼
Optimistic Locking
          │
          ▼
Retry Mechanism
```

Each concept solves a different problem:

| Concept                | Solves                                                     |
| ---------------------- | ---------------------------------------------------------- |
| Idempotency            | Duplicate client retries                                   |
| Redis Distributed Lock | Concurrent execution across multiple application instances |
| Optimistic Locking     | Detects concurrent updates                                 |
| Retry Mechanism        | Resolves temporary optimistic locking conflicts            |

# Key Takeaways

* `@Version` prevents lost updates but does not guarantee the best user experience.
* Version conflicts are not always business conflicts.
* A transaction can fail even when stock is still available.
* Retrying after reloading the latest data allows valid requests to succeed.
* Each retry should execute in a new transaction.
* Production systems often combine **Optimistic Locking**, **Retry Logic**, and **Exponential Backoff** for high-concurrency scenarios.
* Optimistic Locking **detects** conflicts; your application must **decide how to resolve them**.

# Conclusion

Optimistic Locking is an excellent concurrency control mechanism, but it is **not a complete solution by itself**.

Its responsibility is to detect concurrent modifications.

Your application's responsibility is to decide what to do next.

In many real-world systems, the best approach is:

1. Detect the conflict using Optimistic Locking.
2. Reload the latest state.
3. Retry the operation if it is still valid.
4. Return an error only when the business operation can no longer be completed.

This combination provides both **data consistency** and a much better **user experience**, making it the preferred approach for many high-concurrency backend applications.
