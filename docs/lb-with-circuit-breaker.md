# Circuit Breaker in Java & Spring Boot

## 1. Introduction

In a distributed application, requests are often distributed across multiple instances of the same application.

For example:

```text
              Load Balancer
                   |
          +--------+--------+
          |                 |
          ↓                 ↓
     Application 1      Application 2
       :8080              :8081
```

If one application instance starts failing, the Load Balancer may continue sending requests to that instance.

For example:

```text
Request 1 -> 8080 -> Failure
Request 2 -> 8081 -> Success

Request 3 -> 8080 -> Failure
Request 4 -> 8081 -> Success

Request 5 -> 8080 -> Failure
Request 6 -> 8081 -> Success
```

Although the Retry + Fallback mechanism can recover the individual requests, we are still repeatedly attempting to communicate with the failing instance.

This creates unnecessary:

* Network calls
* Response delays
* Resource consumption
* Retry overhead
* Load on an already-failing server

A **Circuit Breaker** helps solve this problem.

[![](https://markdown-videos-api.jorgenkh.no/youtube/s46RgLjE2HE)](https://youtu.be/s46RgLjE2HE)

# 2. What is a Circuit Breaker?

A Circuit Breaker is a resilience pattern used to prevent an application from repeatedly calling a service that is currently failing.

The basic idea is similar to an electrical circuit breaker.

When too many failures occur, the circuit "opens" and temporarily prevents further requests from being sent to the failing service.

Instead of:

```text
Request
   ↓
Failing Server
   ↓
Failure
   ↓
Retry
   ↓
Failing Server
   ↓
Failure
   ↓
Retry
   ↓
Failing Server
```

we can do:

```text
Request
   ↓
Circuit Breaker
   ↓
Circuit OPEN
   ↓
Don't call failing server
```

This allows the failing service some time to recover.

# 3. Why Do We Need a Circuit Breaker?

Consider two application instances:

```text
Server 1 -> localhost:8080
Server 2 -> localhost:8081
```

Initially both servers are working:

```text
8080 -> UP
8081 -> UP
```

Our Load Balancer distributes requests between them.

Now suppose `8080` crashes.

Without a Circuit Breaker:

```text
Request
   ↓
Round Robin
   ↓
8080
   ↓
Failure
   ↓
Retry
   ↓
8081
   ↓
Success
```

The request eventually succeeds because of our Retry + Fallback mechanism.

But the next request may again select `8080`.

```text
Request
   ↓
Round Robin
   ↓
8080
   ↓
Failure
   ↓
Retry
   ↓
8081
   ↓
Success
```

This can continue indefinitely.

The Circuit Breaker solves this by remembering that `8080` has repeatedly failed.

After reaching a configured failure threshold:

```text
8080
 ↓
Repeated failures
 ↓
Circuit OPEN
```

The Load Balancer can temporarily remove that server from the list of available servers.

# 4. Circuit Breaker States

A standard Circuit Breaker generally has three states:

1. CLOSED
2. OPEN
3. HALF_OPEN

# 5. CLOSED State

`CLOSED` is the normal operating state.

When the Circuit Breaker is `CLOSED`, requests are allowed to reach the server.

```text
Circuit = CLOSED

Request
   ↓
Circuit Breaker
   ↓
Server
   ↓
Response
```

The Circuit Breaker keeps track of failures.

For example, suppose our failure threshold is `3`.

```text
Failure 1 -> Failure Count = 1
Failure 2 -> Failure Count = 2
Failure 3 -> Failure Count = 3
```

Once the configured threshold is reached:

```text
CLOSED
   ↓
Failure threshold reached
   ↓
OPEN
```

### Example

```text
8080 -> CLOSED
```

Requests are allowed:

```text
Request -> 8080 -> Success
Request -> 8080 -> Success
Request -> 8080 -> Failure
```

The Circuit Breaker records the failure.

# 6. OPEN State

When the failure threshold is reached, the Circuit Breaker moves to the `OPEN` state.

```text
CLOSED
   ↓
Repeated failures
   ↓
OPEN
```

When the circuit is `OPEN`, requests are not sent to that server.

For example:

```text
8080 -> OPEN
8081 -> CLOSED
```

Our Load Balancer can create an available-server list:

```text
All Servers
    ↓
Health Check
    ↓
Circuit Breaker
    ↓
Available Servers

8080 -> OPEN   -> Removed
8081 -> CLOSED -> Available
```

Therefore, Round Robin works only with:

```text
8081
```

This prevents repeatedly sending traffic to the failing server.

# 7. OPEN State Timeout

The circuit should not remain `OPEN` forever.

We normally configure a timeout.

For example:

```text
Open Duration = 15 seconds
```

When the circuit becomes `OPEN`, we record the time:

```text
openedAt = current time
```

For the next 15 seconds:

```text
Circuit = OPEN
      ↓
Request rejected
```

After 15 seconds, the Circuit Breaker can allow the server to be considered again.

# 8. HALF_OPEN State

`HALF_OPEN` is the third state in a standard Circuit Breaker implementation.

After the `OPEN` timeout expires, instead of immediately assuming that the server has recovered, the Circuit Breaker enters:

```text
HALF_OPEN
```

The purpose of this state is to test the server.

Conceptually:

```text
OPEN
 ↓
Timeout expires
 ↓
HALF_OPEN
 ↓
Send test request
```

If the test request succeeds:

```text
HALF_OPEN
     ↓
Success
     ↓
CLOSED
```

The server is considered healthy again.

If the test request fails:

```text
HALF_OPEN
     ↓
Failure
     ↓
OPEN
```

The circuit opens again.

# 9. Why HALF_OPEN is Important

Suppose a server was failing because of a temporary problem.

After 15 seconds, we don't necessarily know whether it has recovered.

If we immediately move:

```text
OPEN -> CLOSED
```

we may suddenly send normal traffic to a server that is still failing.

That's why `HALF_OPEN` exists.

It allows a small number of test requests before fully restoring traffic.

The standard flow is:

```text
             Failure threshold
CLOSED ---------------------------> OPEN
  ↑                                  |
  |                                  |
  |                            Timeout expires
  |                                  |
  |                                  ↓
  +------------- Success -------- HALF_OPEN
                                     |
                                     |
                                  Failure
                                     |
                                     ↓
                                    OPEN
```

# 10. HALF_OPEN and Concurrent Requests

A production-quality Circuit Breaker needs to carefully handle concurrent requests in `HALF_OPEN`.

Typically, we don't want hundreds of requests to hit a server simultaneously just because the timeout expired.

Instead, we may allow only one or a small number of requests to act as probes.

For example:

```text
HALF_OPEN

Request 1 -> Allowed -> Test server
Request 2 -> Rejected
Request 3 -> Rejected
Request 4 -> Rejected
```

If Request 1 succeeds:

```text
HALF_OPEN -> CLOSED
```

If Request 1 fails:

```text
HALF_OPEN -> OPEN
```

Handling this correctly requires additional synchronization/concurrency considerations.

# 11. Our Custom Implementation

For this tutorial, we are building our own Circuit Breaker rather than using an external Circuit Breaker library.

The standard Circuit Breaker has:

```text
CLOSED
OPEN
HALF_OPEN
```

However, implementing all three states correctly introduces additional complexity, particularly around concurrent requests during `HALF_OPEN`.

To keep this tutorial focused and easy to understand, our custom implementation intentionally uses only:

```text
CLOSED
OPEN
```

Therefore, our implementation follows:

```text
CLOSED
   ↓
Failure threshold reached
   ↓
OPEN
   ↓
Timeout expires
   ↓
CLOSED
```

This is a simplified educational implementation.

A production implementation should generally consider the `HALF_OPEN` state as well.

# 12. Our Circuit Breaker Flow

The implementation uses the following flow:

```text
              Request
                 |
                 ↓
          Load Balancer
                 |
                 ↓
          Healthy Servers
                 |
                 ↓
          Circuit Breaker
                 |
        +--------+--------+
        |                 |
     CLOSED             OPEN
        |                 |
        ↓                 ↓
    Available          Remove
     Server             Server
        |                 |
        ↓                 |
   Round Robin            |
        |                 |
        +--------+--------+
                 |
                 ↓
              Server
```

The Circuit Breaker is therefore another filtering layer before Round Robin chooses a server.

# 13. Circuit Breaker per Server

Because our Load Balancer contains multiple application instances, each server should have its own Circuit Breaker.

For example:

```text
8080 -> Circuit Breaker 1
8081 -> Circuit Breaker 2
```

If `8080` repeatedly fails:

```text
8080 -> Circuit OPEN
8081 -> Circuit CLOSED
```

Only `8080` is removed from the available-server list.

`8081` continues receiving traffic.

This is important because a failure in one server should not automatically make all other servers unavailable.

# 14. Failure Threshold

Our implementation maintains a failure count.

For example:

```text
Failure Threshold = 3
```

The sequence is:

```text
Failure 1
    ↓
Count = 1

Failure 2
    ↓
Count = 2

Failure 3
    ↓
Count = 3

Threshold reached
    ↓
Circuit OPEN
```

The threshold should be configurable rather than hardcoded in a production system.

# 15. Recording Successful Requests

A successful request should reset the failure count.

For example:

```text
Failure
Count = 1

Failure
Count = 2

Success
Count = 0
```

This prevents occasional failures from permanently pushing the server toward an open circuit.

Conceptually:

```text
Failure -> Failure Count increases
Success -> Failure Count resets
```

# 16. Recording Failed Requests

When an HTTP request fails, the Load Balancer informs the Circuit Breaker.

For example:

```java
circuitBreaker.recordFailure();
```

The Circuit Breaker increases its failure count.

When the threshold is reached:

```text
failureCount >= failureThreshold
```

the state changes:

```text
CLOSED -> OPEN
```

The time at which the circuit opened is also recorded.

# 17. Checking Server Availability

Before adding a server to the list used by Round Robin, we check:

1. Is the server healthy?
2. Is its Circuit Breaker available?

Conceptually:

```text
Server
  |
  +--- Health Check
  |
  +--- Circuit Breaker
```

Only if both conditions are satisfied do we include the server.

For example:

```java
List<Server> availableServers = servers.stream()
        .filter(Server::isHealthy)
        .filter(server ->
                circuitBreakers
                        .get(server.getUrl())
                        .isAvailable())
        .toList();
```

The resulting list contains only servers that can currently receive traffic.

# 18. Integration with Round Robin

The existing Round Robin implementation can continue working with very little modification.

Previously:

```java
int index = Math.floorMod(
        counter.getAndIncrement(),
        healthyServers.size()
);

Server server = healthyServers.get(index);
```

After adding the Circuit Breaker:

```java
int index = Math.floorMod(
        counter.getAndIncrement(),
        availableServers.size()
);

Server server = availableServers.get(index);
```

The important point is that Round Robin does not need to understand the Circuit Breaker logic.

It simply receives a list of available servers.

# 19. Integration with Retry and Fallback

The Circuit Breaker does not replace our existing Retry + Fallback mechanism.

Instead, the two patterns work together.

### Retry

Retry handles a request that failed by attempting another available server.

```text
Server 8080
    ↓
Failure
    ↓
Retry
    ↓
Server 8081
    ↓
Success
```

### Circuit Breaker

Circuit Breaker prevents repeatedly sending traffic to a server that has demonstrated repeated failures.

```text
8080
 ↓
Failure
 ↓
Failure
 ↓
Failure
 ↓
Circuit OPEN
 ↓
Stop sending traffic
```

Therefore, the combined architecture is:

```text
                 Request
                    |
                    ↓
              Load Balancer
                    |
                    ↓
             Health Check
                    |
                    ↓
          Circuit Breaker Filter
                    |
                    ↓
          Available Servers
                    |
                    ↓
              Round Robin
                    |
                    ↓
              Selected Server
                    |
              +-----+-----+
              |           |
           Success      Failure
              |           |
              ↓           ↓
           Return     Record Failure
                          |
                          ↓
                        Retry
                          |
                          ↓
                   Another Server
                          |
                          ↓
                       Success
                          |
                          ↓
                        Return
```

# 20. Circuit Breaker vs Health Check

Circuit Breaker and Health Check solve different problems.

### Health Check

Health Check answers:

> "Is this server currently reachable/healthy?"

For example:

```text
8080 -> DOWN
8081 -> UP
```

### Circuit Breaker

Circuit Breaker answers:

> "Should we temporarily stop sending requests to this server because of repeated failures?"

For example:

```text
8080 -> Healthy but repeated request failures
     -> Circuit OPEN
```

A server can therefore be technically reachable but still have an OPEN Circuit Breaker because requests to it are repeatedly failing.

# 21. Circuit Breaker vs Retry

These two mechanisms are complementary.

### Retry

Retry is a request-level recovery mechanism.

```text
Request fails
     ↓
Try another server
```

### Circuit Breaker

Circuit Breaker is a server-level protection mechanism.

```text
Repeated failures
     ↓
Temporarily stop using server
```

Together:

```text
Request
   ↓
Server 8080
   ↓
Failure
   ↓
Circuit records failure
   ↓
Retry another server
```

After enough failures:

```text
8080
 ↓
Circuit OPEN
 ↓
No new requests
```

This prevents unnecessary requests to the failing server.

# 22. What Happens When a Server's Circuit Opens?

Suppose:

```text
8080 -> CLOSED
8081 -> CLOSED
```

After repeated failures:

```text
8080 -> OPEN
8081 -> CLOSED
```

The available-server list becomes:

```text
[8081]
```

Round Robin therefore sends traffic only to:

```text
8081
```

After the configured open duration expires, our simplified implementation makes `8080` available again.

The next request may then attempt to use `8080`.

If it succeeds:

```text
8080 -> CLOSED
```

If it fails repeatedly again, the Circuit Breaker can open again.

# 23. Advantages of Circuit Breaker

### 1. Prevents repeated calls to failing services

The biggest benefit is avoiding unnecessary requests to a known failing server.

### 2. Reduces latency

Instead of waiting for a failing server to timeout repeatedly, the request can quickly move to another available server.

### 3. Reduces resource consumption

Fewer unnecessary requests mean less consumption of:

* Threads
* Connections
* CPU
* Network resources

### 4. Improves system resilience

A failure in one application instance does not necessarily result in failure for the entire system.

### 5. Works well with Retry and Fallback

Circuit Breaker can prevent repeated failures while Retry and Fallback provide alternative paths for handling requests.

# 24. Limitations of Our Current Implementation

Although this implementation is useful for learning, it has several limitations.

## 24.1 No HALF_OPEN State

Our implementation intentionally uses only:

```text
CLOSED
OPEN
```

A production-ready implementation would generally benefit from:

```text
CLOSED
OPEN
HALF_OPEN
```

The HALF_OPEN state provides a controlled way to test whether a failed server has recovered.

## 24.2 Fixed Failure Threshold

Our tutorial uses a fixed failure threshold.

For example:

```text
3 failures -> OPEN
```

In a real application, the threshold should be configurable and based on the application's requirements.

## 24.3 Fixed Open Duration

We use a fixed duration such as:

```text
15 seconds
```

A production implementation may require a configurable duration and potentially more sophisticated recovery strategies.

## 24.4 No Failure Window

Our implementation counts failures without considering a time window.

For example:

```text
Failure
Failure
Success
Failure
Failure
```

A more advanced Circuit Breaker may use failure rates over a rolling time window rather than simply counting failures.

## 24.5 No Metrics

The current implementation does not expose metrics such as:

* Number of failures
* Number of successful requests
* Number of rejected requests
* Number of times the circuit opened
* Time spent in OPEN state

Production systems should generally monitor these values.

## 24.6 No Timeout Configuration for HTTP Requests

A Circuit Breaker does not replace request timeouts.

If an HTTP request hangs for a long time, the Circuit Breaker should work together with properly configured connection and response timeouts.

## 24.7 Thread-Safety and Concurrency

A production implementation needs careful consideration of concurrent requests.

Our implementation uses synchronization around Circuit Breaker state changes, but a more sophisticated implementation may require additional concurrency controls, particularly when implementing `HALF_OPEN`.

# 25. Complete Architecture

Our Load Balancer has now evolved through multiple improvements.

The overall architecture is:

```text
                         Client
                           |
                           ↓
                    Load Balancer
                           |
                           ↓
                     Health Check
                           |
                           ↓
                  Healthy Server List
                           |
                           ↓
                   Circuit Breaker
                           |
                           ↓
                  Available Servers
                           |
                           ↓
                    Round Robin
                           |
                           ↓
                   Selected Server
                           |
                  +--------+--------+
                  |                 |
               Success            Failure
                  |                 |
                  ↓                 ↓
               Response       Record Failure
                                    |
                                    ↓
                                  Retry
                                    |
                                    ↓
                             Another Server
                                    |
                              +-----+-----+
                              |           |
                           Success      Failure
                              |           |
                              ↓           ↓
                           Response     Fallback
```

---

# 26. Summary

A Circuit Breaker is a resilience pattern that prevents repeated requests from being sent to a failing service.

The standard Circuit Breaker has three states:

```text
CLOSED
OPEN
HALF_OPEN
```

Their responsibilities are:

| State     | Behavior                                                          |
| --------- | ----------------------------------------------------------------- |
| CLOSED    | Requests are allowed normally                                     |
| OPEN      | Requests are temporarily blocked                                  |
| HALF_OPEN | Limited test requests determine whether the service has recovered |

For this tutorial, we intentionally implement a simplified two-state Circuit Breaker:

```text
CLOSED -> OPEN -> CLOSED
```

This keeps the implementation easy to understand while demonstrating the core idea of Circuit Breaker.

The Circuit Breaker also works together with the improvements implemented in the previous tutorials:

```text
Health Check
     ↓
Circuit Breaker
     ↓
Round Robin
     ↓
Retry
     ↓
Fallback
```

Each component has a specific responsibility:

* **Health Check** determines whether a server is healthy.
* **Circuit Breaker** prevents repeated calls to a failing server.
* **Round Robin** distributes requests among available servers.
* **Retry** attempts another server when a request fails.
* **Fallback** handles the situation when no server can successfully process the request.

This layered approach makes the Load Balancer more resilient and provides a good foundation for further improvements such as `HALF_OPEN`, timeouts, metrics, backoff, and more advanced failure detection.