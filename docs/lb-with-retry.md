# Retry + Fallback Strategy

In the previous implementation of our Load Balancer, we introduced **periodic health checks** to identify healthy backend servers.

However, health checks are periodic. This means there can be a small window where a server is marked as healthy by the Load Balancer but becomes unavailable immediately afterward.

For example:

```text
Health Check
     |
     v
8080 -> UP
8081 -> UP
```

A few seconds later:

```text
8080 -> DOWN
```

But the Load Balancer may not know about the failure until the next health-check cycle.

If a request is routed to `8080` during this period, the request can fail.

To handle this situation, we improve our existing exception-handling/failover mechanism by introducing a **Retry + Fallback strategy**.

## 1. What is Retry?

Retry means attempting the same logical request again after an initial attempt fails.

In our Load Balancer, the retry is performed against **another healthy backend server** rather than repeatedly calling the same failed server.

For example:

```text
       Client
         |
         v
   Load Balancer
         |
         v
        8080
         |
         X
         |
         v
       Retry
         |
         v
        8081
         |
         v
      Success
```

The client doesn't need to know that the first backend server failed.

The Load Balancer handles the failure internally and attempts to serve the request using another available server.

# 2. Why Do We Need Retry if We Already Have Health Checks?

Health checks and retries solve different problems.

### Health Check

Health checks are **proactive**.

The Load Balancer periodically asks:

> "Is this server currently healthy?"

For example:

```text
Health Check
     |
     +---- 8080 -> UP
     |
     +---- 8081 -> UP
```

### Retry

Retry is **reactive**.

It handles a failure that occurs while processing an actual client request.

For example:

```text
Health Check
     |
     v
8080 -> UP
     |
     | Server crashes
     v
Client Request
     |
     v
8080 -> FAILURE
     |
     v
Retry -> 8081
```

Therefore:

```text
Health Check -> Detect server health periodically

Retry -> Handle request failure immediately
```

Using both mechanisms gives the Load Balancer an additional layer of fault tolerance.

# 3. Existing Failover vs Retry + Fallback

Our earlier implementation already had a basic failover mechanism.

The basic flow was:

```text
     Request
        |
        v
  Select Server
        |
        v
   Server fails
        |
        v
Try another server
        |
        v
Response / Exception
```

This works for a simple two-server scenario.

However, it has some limitations.

For example, if we have:

```text
8080 -> UP
8081 -> UP
8082 -> UP
```

and:

```text
8080 -> FAILURE
8081 -> FAILURE
8082 -> SUCCESS
```

we should ideally be able to continue trying available servers.

We also need a controlled response when all retry attempts fail.

The improved flow is:

```text
Request
   |
   v
Select Server
   |
   v
Attempt Request
   |
   +---- SUCCESS ----> Response
   |
   +---- FAILURE
            |
            v
          Retry
            |
            v
      Another Server
            |
       +----+----+
       |         |
    SUCCESS    FAILURE
       |         |
       v         v
   Response    Retry
                 |
                 v
              Fallback
```

# 4. What is Fallback?

Fallback defines what the application should do when the normal processing path cannot successfully complete.

In our Load Balancer, if all allowed retry attempts fail, we return a controlled response instead of allowing an unhandled exception to propagate.

For example:

```text
8080 -> FAILURE
   |
   v
Retry
   |
   v
8081 -> FAILURE
   |
   v
Fallback
```

The fallback response could be:

```text
Service temporarily unavailable.
Please try again later.
```

The exact fallback behavior depends on the application.

A production application could return an appropriate HTTP status such as `503 Service Unavailable` along with a structured error response.

# 5. Retry + Fallback Flow

The complete flow of our implementation is:

```text
                         Client
                           |
                           v
                    Load Balancer
                           |
                           v
                  Get Healthy Servers
                           |
                           v
                     Round Robin
                           |
                           v
                    Select Server
                           |
                           v
                    Attempt Request
                           |
                    +------+------+
                    |             |
                 Success        Failure
                    |             |
                    v             v
                Response        Retry
                                  |
                                  v
                           Another Healthy
                              Server
                                  |
                           +------+------+
                           |             |
                        Success        Failure
                           |             |
                           v             v
                       Response       Retry /
                                      Fallback
```

# 6. Maximum Retry Attempts

Retries should not continue indefinitely.

Imagine a Load Balancer that keeps retrying every available server without any limit:

```text
8080 -> FAIL
8081 -> FAIL
8082 -> FAIL
8080 -> FAIL
8081 -> FAIL
...
```

This can increase the load on the system and make an already unhealthy situation worse.

Therefore, we introduce a maximum retry limit.

For our implementation:

```java
private static final int MAX_RETRY_ATTEMPTS = 1;
```

This means:

```text
Initial attempt = 1
Retry attempts  = 1
--------------------
Total attempts  = 2
```

So if the first server fails:

```text
Attempt 1 -> 8080
Retry     -> 8081
```

If both fail:

```text
8080 -> FAILURE
8081 -> FAILURE
       |
       v
    Fallback
```

The retry count should normally be configurable in a production system rather than hard-coded.

# 7. Selecting Another Server for Retry

We already use Round Robin to select the initial server.

For example:

```text
Healthy Servers:

[8080, 8081]
```

Suppose Round Robin selects:

```text
8080
```

The initial index is:

```text
index = 0
```

For the retry, we move to the next server.

```java
int serverIndex = (index + attempt) % healthyServers.size();
```

For two servers:

### Initial attempt

```text
attempt = 0

(0 + 0) % 2 = 0

-> 8080
```

### Retry

```text
attempt = 1

(0 + 1) % 2 = 1

-> 8081
```

Therefore:

```text
8080 -> FAILURE
   |
   v
8081 -> RETRY
```

This prevents us from immediately retrying the same server.

# 8. Why Use the Healthy Server List?

Our Load Balancer maintains a list of configured servers, but not every configured server is necessarily healthy.

Therefore, before forwarding the request, we create the list of currently healthy servers:

```java
List<Server> healthyServers = servers.stream()
        .filter(Server::isHealthy)
        .toList();
```

For example, if the configured servers are:

```text
servers:

8080
8081
8082
```

but the health-check mechanism determines:

```text
8080 -> UP
8081 -> DOWN
8082 -> UP
```

then:

```text
healthyServers:

8080
8082
```

Retry should operate on this healthy-server list.

This is important because we don't want the retry mechanism to intentionally select a server that our health-check mechanism has already identified as unhealthy.

# 9. Handling the No-Healthy-Server Scenario

Before attempting a request, we check whether any healthy servers are available.

```java
if (healthyServers.isEmpty()) {
    return fallback();
}
```

The flow is:

```text
             Request
                |
                v
        Healthy Servers?
           /          \
         YES           NO
          |             |
          v             v
      Forward        Fallback
      Request
```

This prevents the Load Balancer from attempting to select a server from an empty list.

# 10. Retry Implementation

The retry logic can be implemented using a loop.

Conceptually:

```java
for (int attempt = 0; attempt < maxAttempts; attempt++) {

    int serverIndex =
            (index + attempt) % healthyServers.size();

    Server server = healthyServers.get(serverIndex);

    try {

        return client.get()
                .uri(server.getUrl() + path)
                .retrieve()
                .body(String.class);

    } catch (Exception ex) {

        System.err.println(
                "Server " + server.getUrl()
                + " failed..."
        );
    }
}
```

If an attempt succeeds, we immediately return the response.

If an attempt fails, the loop moves to the next server.

If all attempts fail, execution reaches the fallback.

# 11. Complete Request Flow

A simplified implementation looks like:

```java
public String fwdRequest(String path) {

    List<Server> healthyServers = servers.stream()
            .filter(Server::isHealthy)
            .toList();

    if (healthyServers.isEmpty()) {
        return fallback();
    }

    int index = Math.floorMod(
            counter.getAndIncrement(),
            healthyServers.size()
    );

    int maxAttempts = Math.min(
            MAX_RETRY_ATTEMPTS + 1,
            healthyServers.size()
    );

    for (int attempt = 0; attempt < maxAttempts; attempt++) {

        int serverIndex = (index + attempt) % healthyServers.size();

        Server server =
                healthyServers.get(serverIndex);

        try {

            System.out.println(
                    "Trying server: "
                    + server.getUrl()
            );

            return client.get()
                    .uri(server.getUrl() + path)
                    .retrieve()
                    .body(String.class);

        } catch (Exception ex) {

            System.err.println(
                    "Server "
                    + server.getUrl()
                    + " failed..."
            );
        }
    }

    return fallback();
}
```

The fallback method can be:

```java
private String fallback() {
    return "Service temporarily unavailable. Please try again later.";
}
```

# 12. Why `Math.min()` is Used

Consider:

```java
int maxAttempts = Math.min(
        MAX_RETRY_ATTEMPTS + 1,
        healthyServers.size()
);
```

Suppose:

```text
MAX_RETRY_ATTEMPTS = 3
```

but we only have:

```text
2 healthy servers
```

There is no reason to attempt more than two different servers during this request.

Therefore:

```text
MAX_RETRY_ATTEMPTS + 1 = 4
healthyServers.size()  = 2

Math.min(4, 2) = 2
```

So we make at most two attempts.

This also prevents the simple implementation from repeatedly cycling through the same servers.

# 13. Example: Successful Retry

Suppose:

```text
Healthy Servers:

8080 -> UP
8081 -> UP
```

Round Robin selects `8080`.

The request fails:

```text
        Client
          |
          v
    Load Balancer
          |
          v
        8080
          |
          X
          |
          v
        Retry
          |
          v
        8081
          |
          v
       Success
```

The client receives the successful response from `8081`.

The client does not need to know that `8080` failed.

# 14. Example: Retry Also Fails

Now suppose:

```text
8080 -> FAILURE
8081 -> FAILURE
```

The flow becomes:

```text
    Client
      |
      v
Load Balancer
      |
      v
     8080
      |
      X
      |
      v
    Retry
      |
      v
     8081
      |
      X
      |
      v
   Fallback
```

The fallback response is returned to the client.

This gives the application a controlled failure path.

# 15. Why Fallback Is Important

Without fallback, an exception may propagate through multiple layers:

```text
Backend Failure
      |
      v
 Load Balancer
      |
      v
  Exception
      |
      v
    Client
```

With fallback:

```text
Backend Failure
      |
      v
    Retry
      |
      v
Another Backend
      |
      X
      |
      v
   Fallback
      |
      v
Controlled Response
```

This makes failure behavior explicit.

Instead of allowing an unexpected exception to determine the response, the Load Balancer decides what should happen after all attempts are exhausted.

# 16. Retry Should Be Bounded

Retry is useful, but retrying too much can be harmful.

Consider a backend that is already overloaded.

Without a retry limit:

```text
100 requests
    |
    v
Backend fails
    |
    v
100 retries
    |
    v
Another backend receives 100 additional requests
```

This can create additional load.

This phenomenon is sometimes referred to as a **retry storm**.

Therefore, retries should generally be:

* Limited
* Carefully configured
* Combined with timeouts
* Used only where retrying is appropriate

Our tutorial uses a small fixed retry count to keep the implementation simple.

# 17. Retry Is Not Suitable for Every Request

Another important consideration is whether an operation is safe to repeat.

For example, consider:

```text
POST /orders
```

Suppose the Load Balancer sends the request to a server and the server actually creates the order, but the response is lost.

The Load Balancer might think:

```text
Request failed
```

and retry the POST request.

The backend could potentially create the order twice.

Therefore, retrying requests is closely related to **idempotency**.

For operations that can have side effects, a production system should carefully consider:

* Idempotency
* Idempotency keys
* Request semantics
* Whether the failure happened before or after the operation completed

Our simple implementation does not attempt to solve these concerns; it is intended to demonstrate the retry/fallback concept.

# 18. Health Check + Retry Together

The combination of health checks and retry provides two layers of protection.

### Layer 1 - Health Check

Periodically identify unhealthy servers.

```text
Health Check
     |
     v
8080 -> DOWN
     |
     v
Remove from healthy-server selection
```

### Layer 2 - Retry

Handle failures that happen before the next health check.

```text
8080 -> UP according to last health check
     |
     v
Server suddenly crashes
     |
     v
Request fails
     |
     v
Retry -> 8081
```

Therefore:

```text
             Load Balancer
                   |
          +--------+--------+
          |                 |
          v                 v
     Health Check          Retry
          |                 |
      Proactive          Reactive
       detection         handling
```

This is one of the most important concepts in this implementation.

# 19. Why We Don't Immediately Mark a Server as Unhealthy

When a request fails, it may be tempting to immediately execute:

```java
server.setHealthy(false);
```

However, a single request failure doesn't necessarily mean that the server is completely unavailable.

For example:

```text
Request 1 -> FAILURE
Request 2 -> SUCCESS
Request 3 -> SUCCESS
```

The first failure could have been caused by a temporary network problem or another transient issue.

Therefore, in this implementation:

```text
                Health Check
                     |
                     v
   Responsible for determining server health

                   Retry
                     |
                     v
Responsible for handling the current request failure
```

This keeps the responsibilities separated.

More sophisticated implementations can introduce failure thresholds and circuit breakers later.

# 20. Current Implementation Limitations

Although Retry + Fallback improves our Load Balancer, this implementation is still intentionally simple.

## 20.1 Fixed Retry Count

Currently:

```java
private static final int MAX_RETRY_ATTEMPTS = 1;
```

The value is hard-coded.

A better implementation would make it configurable:

```properties
load-balancer.max-retry-attempts=2
```

## 20.2 No Retry Backoff

Our implementation retries immediately.

For example:

```text
8080 -> FAILURE
       ↓
Immediate Retry
       ↓
      8081
```

In a production system, repeated immediate retries can increase system load.

A better strategy may introduce a delay between attempts.

For example:

```text
    Attempt 1
       ↓
      wait
       ↓
    Attempt 2
       ↓
   wait longer
       ↓
    Attempt 3
```

This is commonly implemented using a backoff strategy such as exponential backoff.

## 20.3 No Request Timeout

If a backend server doesn't fail immediately but simply takes a very long time to respond, the retry mechanism may not help.

For example:

```text
Request
   |
   v
  8080
   |
   | waiting...
   |
   | waiting...
   |
   v
Very slow response
```

A timeout should be introduced so that the Load Balancer doesn't wait indefinitely.

Timeout handling is therefore a natural future improvement.

## 20.4 Simple Fallback Response

Our fallback currently returns a simple string:

```text
Service temporarily unavailable. Please try again later.
```

A production implementation should generally return a structured HTTP response.

For example:

```json
{
  "status": 503,
  "message": "Service temporarily unavailable",
  "timestamp": "..."
}
```

---

## 20.5 No Circuit Breaker

Suppose `8080` is permanently unavailable.

The Load Balancer could continue discovering failures and retrying requests.

A **Circuit Breaker** can help prevent repeated calls to a backend that is consistently failing.

A future implementation can introduce:

```text
CLOSED
   |
   | failures
   v
OPEN
   |
   | wait
   v
HALF-OPEN
```

Circuit Breaker is therefore a natural next step after Retry + Fallback.

## 20.6 No Metrics or Monitoring

Currently, we print information to the console.

For example:

```text
Trying server: http://localhost:8080
Server failed...
Trying server: http://localhost:8081
```

A production Load Balancer should expose metrics such as:

* Number of requests
* Number of retries
* Retry success rate
* Backend failure count
* Fallback count
* Response time
* Server health

This would make the behavior observable.

# 21. Retry vs Health Check vs Fallback

These three mechanisms have different responsibilities.

| Mechanism    | Responsibility                                              |
| ------------ | ----------------------------------------------------------- |
| Health Check | Determine whether a server is healthy                       |
| Retry        | Try another server when the current request fails           |
| Fallback     | Provide an alternative response when attempts are exhausted |

The relationship can be visualized as:

```text
Health Check
     |
     v
Which servers are healthy?
     |
     v
Round Robin
     |
     v
Select Server
     |
     v
Request
     |
     +---- Success ----> Response
     |
     +---- Failure
             |
             v
           Retry
             |
             +---- Success ----> Response
             |
             +---- Failure
                     |
                     v
                  Fallback
```

# 22. Overall Architecture

After adding Retry + Fallback, our Load Balancer now has the following responsibilities:

```text
                         Client
                           |
                           v
                    Load Balancer
                           |
              +------------+------------+
              |                         |
              v                         v
       Healthy Servers             Health Checker
              |
              v
         Round Robin
              |
              v
        Request Forwarding
              |
        +-----+-----+
        |           |
     Success      Failure
        |           |
        v           v
    Response      Retry
                    |
                    v
             Another Server
                    |
               +----+----+
               |         |
            Success    Failure
               |         |
               v         v
           Response   Fallback
```

# 23. Request Lifecycle

The request lifecycle can therefore be summarized as:

### Step 1 - Receive Request

The Load Balancer receives a request from the client.

### Step 2 - Find Healthy Servers

Only servers currently marked as healthy are considered.

### Step 3 - Select Server

Round Robin selects the initial server.

### Step 4 - Forward Request

The Load Balancer sends the request to the selected backend.

### Step 5 - Handle Success

If the request succeeds, return the backend response.

### Step 6 - Handle Failure

If the request fails, move to the retry mechanism.

### Step 7 - Retry

Attempt the request using another available healthy server.

### Step 8 - Fallback

If all configured attempts fail, return the fallback response.

# 24. Example

Assume:

```text
Servers:

8080 -> UP
8081 -> UP
```

Configuration:

```text
MAX_RETRY_ATTEMPTS = 1
```

A request arrives.

Round Robin selects:

```text
8080
```

But 8080 has crashed since the last health check.

The Load Balancer detects the request failure:

```text
8080 -> FAILURE
```

It retries:

```text
8081 -> SUCCESS
```

The client receives the response from 8081.

If 8081 also fails:

```text
8080 -> FAILURE
8081 -> FAILURE
        |
        v
     Fallback
```

This provides a controlled failure response.

# 25. Key Takeaways

The main lessons from this improvement are:

1. Health checks alone cannot guarantee that a server will remain healthy.
2. A server can fail between two health-check cycles.
3. Retry provides reactive failure handling.
4. Retry should preferably use another available backend rather than repeatedly calling the failed server.
5. Retry attempts should be bounded.
6. Fallback provides a controlled behavior when all attempts fail.
7. Health Check, Retry, and Fallback have different responsibilities.
8. Retries can increase system load, so production systems need careful retry policies.
9. Retrying operations with side effects requires consideration of idempotency.
10. Timeouts, backoff, circuit breakers, and monitoring are natural improvements for a production-ready implementation.

The goal of this project is not to replace mature production load-balancing solutions, but to understand the fundamental concepts behind them by building a simplified Load Balancer using Java and Spring Boot.