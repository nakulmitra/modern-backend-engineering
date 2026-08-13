# Build Your Own Load Balancer Using Java & Spring Boot

## 1. Introduction

A load balancer is a component that distributes incoming client requests across multiple instances of an application.

When an application receives a large number of requests, running a single instance may not be sufficient. Instead, multiple instances of the same application can be deployed so that incoming traffic can be distributed among them.

For example:

```text
                         Client
                           |
                           v
                    +--------------+
                    | Load Balancer|
                    +--------------+
                       /        \
                      /          \
                     v            v
              +-----------+  +-----------+
              | Instance 1 |  | Instance 2 |
              |   :8080    |  |   :8081    |
              +-----------+  +-----------+
```

The client communicates with the Load Balancer instead of directly communicating with a particular application instance.

The Load Balancer decides which instance should process the request, forwards the request, receives the response, and sends the response back to the client.

This project demonstrates the basic concept by building a simple Load Balancer using Java and Spring Boot.

# 2. Why Do We Need a Load Balancer?

Suppose we have a Spring Boot application running on a single server:

```text
Client
   |
   v
Application
```

As traffic increases, the application may start experiencing:

* Higher CPU utilization
* Higher memory consumption
* Increased response time
* More concurrent requests
* Increased probability of failure

One possible solution is to create multiple instances of the same application.

```text
             Client
                |
       +--------+--------+
       |        |        |
       v        v        v
   Instance  Instance  Instance
      1          2         3
```

However, now another question appears:

> Which instance should receive each request?

This is where the Load Balancer comes into the picture.

# 3. Local Development vs Production

For this project, two instances are running on the same machine.

Therefore, they cannot listen on the same port.

The demonstration uses:

```text
Instance 1 -> localhost:8080

Instance 2 -> localhost:8081
```

Both instances contain the same application and expose the same APIs.

The only difference is the port and instance name.

```text
Instance 1
server.port=8080
instance.name=INSTANCE-1
```

```text
Instance 2
server.port=8081
instance.name=INSTANCE-2
```

This is primarily a limitation of the local development setup.

In a production environment, the application instances can run on different machines, virtual machines, containers, or Pods. Therefore, multiple instances can use the same application port.

For example:

```text
10.0.1.10:8080
10.0.1.11:8080
10.0.1.12:8080
```

The IP addresses are different, so there is no port conflict.

The important concept is:

> The Load Balancer distributes requests between different application instances. The instances do not necessarily need to use different ports.

# 4. Architecture of This Project

The architecture used in this project is:

```text
                       Client
                         |
                         |
                  localhost:9090
                         |
                         v
                 +---------------+
                 | Load Balancer |
                 +---------------+
                         |
                 Round Robin Logic
                         |
                 +-------+-------+
                 |               |
                 v               v
          localhost:8080   localhost:8081
          Instance 1        Instance 2
```

The flow is:

1. Client sends a request to the Load Balancer.
2. Load Balancer receives the request.
3. Load Balancer selects a backend instance.
4. Request is forwarded to that instance.
5. Backend instance processes the request.
6. Backend sends the response to the Load Balancer.
7. Load Balancer returns the response to the client.

The client does not need to know which backend instance processed the request.

# 5. Backend Application Instances

The project uses the same Spring Boot application for both instances.

The first instance runs on:

```text
localhost:8080
```

The second instance runs on:

```text
localhost:8081
```

Both expose the same API.

For example:

```text
GET /hello
```

Instance 1 returns:

```text
Response from INSTANCE-1
```

Instance 2 returns:

```text
Response from INSTANCE-2
```

This makes it easy to visually verify which instance handled a request.

# 6. What Is a Load Balancing Algorithm?

Once the Load Balancer receives a request, it needs a mechanism to decide which backend instance should receive it.

This mechanism is called a **load balancing algorithm**.

There are several common approaches:

* Round Robin
* Weighted Round Robin
* Least Connections
* Random
* IP Hash
* Consistent Hashing

For this project, we use **Round Robin** because it is simple and easy to understand.

# 7. Round Robin Algorithm

Round Robin distributes requests sequentially among available servers.

Suppose we have:

```text
Server 1 -> localhost:8080

Server 2 -> localhost:8081
```

The requests are distributed as:

```text
Request 1 -> Server 1
Request 2 -> Server 2
Request 3 -> Server 1
Request 4 -> Server 2
Request 5 -> Server 1
Request 6 -> Server 2
```

In other words:

```text
8080 -> 8081 -> 8080 -> 8081 -> ...
```

This provides a simple and predictable distribution of requests.

# 8. Using AtomicInteger

The project uses `AtomicInteger` to keep track of the next server.

```java
private final AtomicInteger counter = new AtomicInteger();
```

For every incoming request:

```java
int index = counter.getAndIncrement() % servers.size();
```

The counter is incremented atomically.

For two servers:

```text
Counter       Modulo 2       Selected Server

   0              0              8080
   1              1              8081
   2              0              8080
   3              1              8081
   4              0              8080
```

Therefore, the modulo operation creates the circular Round Robin behavior.

# 9. Why AtomicInteger Instead of int?

The Load Balancer can receive multiple requests concurrently.

If a normal integer were used:

```java
private int counter;
```

multiple threads could read and modify the value at the same time.

This could result in race conditions.

`AtomicInteger` provides atomic operations such as:

```java
counter.getAndIncrement();
```

This makes incrementing the counter thread-safe.

Therefore, multiple requests can safely update the counter.

# 10. Important Integer Overflow Problem

The initial implementation uses:

```java
counter.getAndIncrement() % servers.size()
```

This works correctly for normal operation.

However, `AtomicInteger` internally uses a 32-bit signed integer.

Eventually, the value can reach:

```text
2,147,483,647
```

which is `Integer.MAX_VALUE`.

The next increment causes integer overflow:

```text
2,147,483,647
          |
          v
-2,147,483,648
```

Now consider:

```java
int index = counter.getAndIncrement() % servers.size();
```

The modulo operation can produce a negative value.

For example:

```text
-2 % 3 = -2
```

If this negative value is used as a List index:

```java
servers.get(index);
```

the application can throw:

```text
IndexOutOfBoundsException
```

This is an important consideration for a long-running application.

# 11. Using Math.floorMod()

A safer approach is:

```java
int index = Math.floorMod(
    counter.getAndIncrement(),
    servers.size()
);
```

Unlike the `%` operator, `Math.floorMod()` guarantees a non-negative result when the divisor is positive.

For example:

```text
Math.floorMod(-2, 3)
```

returns:

```text
1
```

Therefore, the calculated index remains valid even if the counter becomes negative because of integer overflow.

This is a small but important improvement for a long-running application.

# 12. Generic URL Forwarding

A Load Balancer should not need to know every REST endpoint exposed by the backend application.

For example, suppose the backend exposes:

```text
GET    /hello
GET    /users
GET    /users/1
POST   /orders
PUT    /orders/10
DELETE /products/5
```

A poor design would be to create a separate Load Balancer controller for every endpoint.

For example:

```java
@GetMapping("/hello")
```

then:

```java
@GetMapping("/users")
```

then:

```java
@PostMapping("/orders")
```

and so on.

This would mean every time a new backend API is created, the Load Balancer would also need to be modified.

Instead, the Load Balancer can act as a generic proxy and capture the incoming URL.

Conceptually:

```text
Client

GET /users/10

       |

       v

Load Balancer

       |

       v

Selected Backend

GET /users/10
```

The Load Balancer does not need to understand what `/users/10` means.

It simply forwards the request to the selected backend.

A generic mapping such as:

```java
@RequestMapping("/**")
```

allows the Load Balancer to handle different paths without creating individual controller methods for each API.

This makes the architecture much more scalable from an API-routing perspective.

# 13. Request Forwarding

After selecting a server, the Load Balancer needs to forward the request.

This project uses Spring's `RestClient`.

Conceptually:

```text
Incoming Request

GET /hello

        |

        v

Load Balancer

        |

        v

Select Server

        |

        v

http://localhost:8080/hello

        |

        v

Backend Application
```

The backend processes the request and returns the response.

The Load Balancer then returns that response to the original client.

Therefore:

```text
Client
   |
   | GET /hello
   v
Load Balancer
   |
   | GET /hello
   v
Backend Instance
   |
   | Response
   v
Load Balancer
   |
   | Response
   v
Client
```

# 14. Handling a Down Instance

The current implementation also introduces basic failure handling.

Suppose the Round Robin algorithm selects:

```text
Instance 2 -> localhost:8081
```

but that instance is down.

The request to that server can fail.

The implementation catches the exception:

```java
catch (Exception ex) {
    // try another instance
}
```

It then attempts to use another backend instance.

Conceptually:

```text
Client
  |
  v
Load Balancer
  |
  v
Round Robin
  |
  v
Instance 2
  |
  X
DOWN
  |
  v
Fallback
  |
  v
Instance 1
  |
  v
Response
```

This provides a basic form of failover.

The important idea is:

> If the selected backend cannot process the request, the Load Balancer can attempt another available backend.

# 15. Current Failure Handling Is Basic

The fallback mechanism in this project is intentionally simple.

It assumes that another server can be used when the selected server fails.

However, this approach has several limitations.

For example, if there are three servers:

```text
Server A
Server B
Server C
```

and Server A fails, the implementation should ideally try another healthy server rather than simply assuming the first alternative is available.

A production implementation should maintain proper server health information and select only healthy servers.

# 16. Current System Limitations

This project demonstrates the fundamental concept of load balancing, but it is not a production-ready implementation.

The following are some important limitations.

## 16.1 Static Server List

The backend servers are currently defined in code:

```text
localhost:8080
localhost:8081
```

If a new server is added:

```text
localhost:8082
```

the Load Balancer needs to be updated.

A production system should ideally support dynamic server discovery or configuration.

Possible solutions include:

* Service discovery
* Kubernetes service discovery
* Configuration management
* Dynamic registration
* Cloud service discovery

## 16.2 No Proper Health Check

The current implementation discovers that a server is unavailable only when a request fails.

For example:

```text
Request
   |
   v
8081
   |
   X
Connection failed
```

A better design is to continuously monitor backend instances.

For example:

```text
Health Check

8080 -> UP
8081 -> DOWN
8082 -> UP
```

The Load Balancer should send traffic only to healthy servers.

A future implementation could periodically call:

```text
/actuator/health
```

and maintain the health status of every backend instance.

# 17. Repeated Requests to a Failed Server

With basic Round Robin, the Load Balancer may continue selecting a failed instance.

For example:

```text
Request 1 -> 8080
Request 2 -> 8081 (DOWN)
Request 3 -> 8080
Request 4 -> 8081 (DOWN)
Request 5 -> 8080
```

Every time 8081 is selected, the request fails first and the fallback mechanism needs to run.

This creates unnecessary overhead.

A better implementation would mark 8081 as unhealthy:

```text
8080 -> UP
8081 -> DOWN
```

and temporarily remove it from the active server list.

# 18. No Recovery Mechanism

If an instance goes down and later comes back:

```text
8081 -> DOWN
```

then:

```text
8081 -> UP
```

the current implementation does not have a sophisticated mechanism to detect and re-register the instance.

A production implementation should periodically perform health checks and automatically add recovered instances back into the pool.

# 19. Only Basic Round Robin

Round Robin assumes that all servers are equally capable.

Consider:

```text
Server A -> 2 CPU cores
Server B -> 16 CPU cores
```

Round Robin still distributes:

```text
A
B
A
B
A
B
```

This may not be optimal.

A stronger implementation could use **Weighted Round Robin**.

For example:

```text
Server A -> Weight 1
Server B -> Weight 3
```

Traffic could then be approximately:

```text
A
B
B
B
A
B
B
B
```

This allows stronger servers to handle more traffic.

# 20. No Least-Connection Strategy

Round Robin does not consider how many active requests each server is currently processing.

For example:

```text
Server A -> 100 active requests

Server B -> 2 active requests
```

A Round Robin algorithm may still send the next request to Server A.

A **Least Connections** algorithm would prefer Server B.

This can be more suitable when requests have significantly different processing times.

# 21. No Sticky Sessions

Some applications maintain session-specific state.

For example:

```text
User A -> Session Data
```

If the user's first request goes to Instance 1 and the next request goes to Instance 2, the second instance may not have the required session information.

A technique called **Sticky Session** or **Session Affinity** can be used to keep a client associated with the same backend instance.

For example:

```text
User A -> Instance 1
User A -> Instance 1
User A -> Instance 1
```

while:

```text
User B -> Instance 2
```

However, a better long-term architecture is often to keep application instances stateless and store shared session/state information externally when appropriate.

# 22. Limited HTTP Method Handling

The simple implementation demonstrates forwarding a request to the backend, but a production reverse proxy must correctly handle all relevant HTTP methods.

These can include:

```text
GET
POST
PUT
PATCH
DELETE
HEAD
OPTIONS
```

It should also correctly forward:

* Request headers
* Query parameters
* Request body
* Content type
* Authentication headers
* Cookies
* Response status codes
* Response headers

For example:

```text
POST /orders?source=mobile

Headers
    Authorization
    Content-Type

Body
    {
        "productId": 10,
        "quantity": 2
    }
```

A production-grade proxy must preserve the appropriate parts of the original request when forwarding it.

# 23. No Connection Pooling / Timeout Strategy

The current implementation is intentionally simple.

A production Load Balancer needs to carefully manage outbound HTTP connections.

Important considerations include:

* Connection pooling
* Connection timeout
* Read timeout
* Connection timeout to backend
* Maximum connections
* Keep-alive
* Request timeout

Without proper timeout configuration, a slow or unresponsive backend can consume resources for a long time.

# 24. No Retry Policy

The current implementation performs a basic fallback when the selected server fails.

However, retries need to be designed carefully.

For example, retrying a failed:

```text
GET /users
```

may be relatively safe in many cases.

But blindly retrying:

```text
POST /payment
```

could potentially create duplicate operations if the first request actually reached the backend but the response was lost.

Therefore, production retry logic should consider:

* HTTP method
* Idempotency
* Error type
* Number of retries
* Timeout
* Backoff strategy

# 25. No Circuit Breaker

Suppose a backend is continuously failing.

Without a circuit breaker:

```text
Request
   |
   v
Server A
   |
   X
Request fails

Request
   |
   v
Server A
   |
   X
Request fails

Request
   |
   v
Server A
   |
   X
Request fails
```

The Load Balancer continues trying the unhealthy server.

A circuit breaker can temporarily stop requests from being sent to a repeatedly failing backend.

For example:

```text
CLOSED
   |
   | repeated failures
   v
OPEN
   |
   | wait
   v
HALF-OPEN
   |
   | successful test
   v
CLOSED
```

This can prevent repeated failures from consuming resources.

# 26. No Rate Limiting

The current Load Balancer does not restrict how many requests a client can send.

A malicious or unexpectedly busy client could send a large number of requests.

A production system may implement rate limiting:

```text
Client A -> 100 requests/minute
Client B -> 100 requests/minute
```

Excess requests can then be rejected or throttled.

Rate limiting can be implemented at the Load Balancer, API Gateway, or another infrastructure layer depending on the architecture.

# 27. No Monitoring or Metrics

The current implementation primarily uses logging to understand which instance handled a request.

A production system needs much richer observability.

Useful metrics include:

```text
Total Requests
Requests per Second
Response Time
Error Rate
Backend Health
Active Connections
Requests per Instance
```

For example:

```text
Instance 1
Requests: 50,000
Average Latency: 120 ms
Errors: 0.4%

Instance 2
Requests: 48,000
Average Latency: 130 ms
Errors: 0.5%
```

These metrics help operators understand whether the Load Balancer and backend instances are behaving correctly.

# 28. No Distributed State

The Load Balancer in this project is itself a single application.

Therefore:

```text
             Client
                |
                v
         Load Balancer
                |
        +-------+-------+
        |               |
      App 1            App 2
```

If the Load Balancer itself goes down, clients cannot reach the backend instances through it.

This creates a **single point of failure**.

A production architecture would typically have multiple Load Balancer instances or use a highly available managed Load Balancer.

For example:

```text
                  Client
                     |
              +------+------+
              |             |
              v             v
          LB Instance 1  LB Instance 2
              |             |
              +------+------+
                     |
             Backend Instances
```

# 29. No Dynamic Service Discovery

In the current implementation, the Load Balancer knows:

```text
localhost:8080
localhost:8081
```

In a large distributed system, instances can be created and removed dynamically.

For example:

```text
Instance 1 -> created
Instance 2 -> created
Instance 3 -> created

Instance 2 -> removed

Instance 4 -> created
```

Hardcoding server URLs does not scale well in such environments.

Service discovery systems can maintain information about available application instances.

The Load Balancer can then discover the current set of healthy instances dynamically.

# 30. Possible Improvements

The current project provides a foundation that can be extended significantly.

A possible evolution is:

```text
Basic Load Balancer
        |
        v
Round Robin
        |
        v
Generic Request Forwarding
        |
        v
Health Checks
        |
        v
Automatic Failover
        |
        v
Weighted Routing
        |
        v
Least Connections
        |
        v
Retry + Backoff
        |
        v
Circuit Breaker
        |
        v
Rate Limiting
        |
        v
Metrics & Monitoring
        |
        v
Dynamic Service Discovery
        |
        v
Highly Available Load Balancer
```

# 31. Complete Conceptual Flow

The complete flow of the current implementation can be summarized as:

```text
                   Client
                     |
                     |
                 HTTP Request
                     |
                     v
              +-------------+
              |    Load     |
              |   Balancer  |
              +-------------+
                     |
                     v
              Round Robin Logic
                     |
                     v
           +---------+---------+
           |                   |
           v                   v
      Instance 1          Instance 2
      localhost:8080      localhost:8081
           |                   |
           +---------+---------+
                     |
                     v
                  Response
                     |
                     v
              Load Balancer
                     |
                     v
                   Client
```

If the selected instance is unavailable:

```text
      Client
        |
        v
   Load Balancer
        |
        v
Select Instance 2
        |
        X
 Instance 2 DOWN
        |
        v
Fallback Instance
        |
        v
    Instance 1
        |
        v
      Response
```

# 32. Key Concepts Demonstrated

This project demonstrates several important backend concepts:

* Load balancing
* Request distribution
* Round Robin algorithm
* HTTP request forwarding
* Thread-safe counter updates
* Integer overflow
* Basic failover
* Generic URL routing
* Multiple application instances

# 33. What This Project Is and Is Not

### This project is:

* A learning implementation
* A demonstration of Round Robin
* A simple reverse proxy concept
* A practical Java/Spring Boot project
* A foundation for understanding production load balancing

### This project is not:

* A replacement for production Load Balancers
* A complete reverse proxy
* A highly available Load Balancer
* A complete API Gateway
* A complete service discovery system

Production systems require considerably more functionality around reliability, security, scalability, observability, networking, and failure handling.

# 34. Conclusion

A Load Balancer solves a fundamental problem in distributed applications:

> How can incoming traffic be distributed across multiple instances of an application?

In this project, we built a simplified Load Balancer using Java and Spring Boot.

The implementation starts with two backend instances and uses the Round Robin algorithm to distribute requests.

```text
Request 1 -> Instance 1
Request 2 -> Instance 2
Request 3 -> Instance 1
Request 4 -> Instance 2
```

We then introduced a generic URL forwarding mechanism so that the Load Balancer does not need a separate controller for every backend endpoint.

We also added basic exception handling so that when the selected instance is unavailable, another instance can be attempted.

Finally, we considered an important long-running application issue with `AtomicInteger` overflow and improved the index calculation using:

```java
Math.floorMod(
    counter.getAndIncrement(),
    servers.size()
);
```

The implementation is intentionally simple, but it provides a strong foundation for understanding how request distribution works.

The next step is to make the Load Balancer more production-ready by adding health checks, automatic failover, better routing algorithms, retries, circuit breakers, monitoring, and dynamic service discovery.