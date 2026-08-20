# Adding Health Checks to a Java Load Balancer

In the previous part of this section, we built a simple Load Balancer using **Java and Spring Boot** that distributed traffic between multiple backend instances using the **Round Robin algorithm**. We also implemented:

* Generic URL forwarding
* `AtomicInteger` for request counting
* `Math.floorMod()` to prevent negative indexes
* Basic exception handling and fallback

While that implementation successfully distributed requests, it had one major limitation:

> The Load Balancer had no idea whether backend servers were actually healthy.

This section improves our Load Balancer by introducing **Periodic Health Checks**, enabling it to send requests only to healthy backend instances.

# Problem with the Previous Implementation

Suppose we have two instances of the same application:

```text
Instance 1 -> localhost:8080 -> UP
Instance 2 -> localhost:8081 -> DOWN
```

Our original implementation stored server information like this:

```java
List<String> servers = List.of(
    "http://localhost:8080",
    "http://localhost:8081"
);
```

Round Robin simply selected servers one by one:

```text
Request 1 -> 8080
Request 2 -> 8081 Failed
Request 3 -> 8080
Request 4 -> 8081 Failed
```

The problem is:

* The Load Balancer discovers failures only after sending a real request.
* User requests experience delays.
* Exception handling executes frequently.
* Unnecessary traffic is sent to failed servers. 

A better approach is:

> Detect unhealthy servers before sending production traffic.

# New Architecture

We introduce a Health Check mechanism.

```text
                    Load Balancer
                           |
               +-----------+-----------+
               |                       |
               v                       v
       Periodic Health Check      User Request
               |                       |
               v                       v
        Server Health            Healthy Servers
                                        |
                                        v
                                 Round Robin
                                        |
                                        v
                                  Backend Server
```

Now the Load Balancer maintains awareness of backend health instead of relying entirely on request-time failures.

# Why Not Check Health on Every Request?

Imagine receiving:

```text
10,000 requests/sec
```

If every request triggers:

```text
    Request
       ↓
 Health Check
       ↓
Forward Request
```

then the system generates thousands of unnecessary health-check calls.

Instead:

```text
  Every 60 seconds
         ↓
Check backend servers
         ↓
 Update health status
```

User requests simply use already available health information.

# Why Use a Separate `/health` Endpoint?

Business APIs such as:

```text
/hello
/orders
/users
/products
```

exist to perform business operations.

Their responsibility is not to indicate server availability.

Therefore, we create a dedicated endpoint:

```text
/health
```

Example:

```java
@GetMapping("/health")
public ResponseEntity<String> health() {
    return ResponseEntity.ok("UP");
}
```

This endpoint is called periodically by the Load Balancer.

In production systems, Spring Boot Actuator is commonly used:

```text
/actuator/health
```

For learning purposes, a custom endpoint keeps the implementation simple and easier to understand.

# Server Model

Previously we stored only server URLs:

```java
List<String> servers;
```

Now each server also contains health information.

```java
public class Server {

    private String url;

    private volatile boolean healthy;

    public Server(String url) {
        this.url = url;
        this.healthy = true;
    }
}
```

Each server now contains:

```text
Server
├── URL
└── Health Status
```

Example:

```text
Server 1
URL      -> http://localhost:8080
Healthy  -> true

Server 2
URL      -> http://localhost:8081
Healthy  -> false
```

The `volatile` keyword is important because:

* Scheduler thread updates health status.
* Request threads read health status.

`volatile` ensures that all threads see the latest value.

# Health Checker Component

A dedicated component checks server health.

```java
@Component
public class HealthChecker {

    private final RestClient restClient;

    public boolean isHealthy(Server server) {

        try {

            restClient.get()
                    .uri(server.getUrl() + "/health")
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (Exception ex) {
            return false;
        }
    }
}
```

Responsibilities:

```text
Call /health
       |
       +--- Success -> UP
       |
       +--- Failure -> DOWN
```

This keeps health-check logic separate from load-balancing logic, resulting in better code organization.

# Why Not Check Health on Every Request?

Imagine:

```text
10,000 requests/sec
```

If every request performs:

```text
    Request
       ↓
 Health Check
       ↓
Forward Request
```

the Load Balancer itself creates a large amount of unnecessary traffic.

Instead:

```text
    Every 60 seconds
           ↓
 Check backend servers
           ↓
  Update health status
```

Normal requests use cached health information.

This is far more efficient.

# Scheduled Health Checks

Spring Scheduling is used:

```java
@EnableScheduling
@SpringBootApplication
public class LoadBalancerApplication {
}
```

Periodic execution:

```java
@Scheduled(fixedRate = 60000)
public void checkServerHealth() {

    for (Server server : servers) {

        boolean healthy =
                healthChecker.isHealthy(server);

        server.setHealthy(healthy);
    }
}
```

Execution timeline:

```text
0 sec   -> Check servers
60 sec  -> Check servers
120 sec -> Check servers
180 sec -> Check servers
```

This gives the Load Balancer a periodically updated view of server health.

# Healthy Server Selection

The Load Balancer maintains:

```text
Master Server List
```

Example:

```text
8080 -> true
8081 -> false
```

When a request arrives:

```java
List<Server> healthyServers =
        servers.stream()
               .filter(Server::isHealthy)
               .toList();
```

Result:

```text
Healthy Servers:
8080
```

Round Robin operates only on healthy instances.

# Updated Round Robin Logic

Previous implementation:

```java
int index = Math.floorMod(
        counter.getAndIncrement(),
        servers.size()
);
```

New implementation:

```java
int index = Math.floorMod(
        counter.getAndIncrement(),
        healthyServers.size()
);
```

This ensures:

```text
Traffic Distribution
        ↓
Only Healthy Servers
```

If no server is available:

```java
if (healthyServers.isEmpty()) {
    throw new RuntimeException(
        "No healthy servers available"
    );
}
```

# Failure Detection

Suppose:

```text
8080 -> UP
8081 -> DOWN
```

After the next health-check cycle:

```text
Healthy Servers: 8080
```

Traffic becomes:

```text
Request 1 -> 8080
Request 2 -> 8080
Request 3 -> 8080
```

No request is intentionally sent to failed instances.

# Automatic Recovery

Now suppose:

```text
8081 -> DOWN
```

Later, the server starts again.

Next health check:

```text
8081 -> UP
```

Healthy server list becomes:

```text
8080
8081
```

Round Robin resumes:

```text
8080
8081
8080
8081
```

This provides:

```text
  Detect Failure
        ↓
  Remove Server
        ↓
  Detect Recovery
        ↓
  Add Server Back
        ↓
  Resume Traffic
```

Automatic recovery is one of the major advantages of health-check-based load balancing.

# Limitation of This Approach

Health checks provide a snapshot of server state.

Example:

```text
10:00:00 -> Health Check -> UP
10:00:01 -> Server Crashes
10:00:02 -> User Request
10:01:00 -> Next Health Check
```

The Load Balancer still believes:

```text
Server -> UP
```

until the next scheduled check.

Therefore:

```text
Periodic Health Check + Request Time Exception Handling = Better Reliability
```

Health checks and exception handling should work together.

# Production Improvements

Our implementation intentionally keeps things simple.

Production systems generally include:

* Connection timeout
* Read timeout
* Failure threshold
* Recovery threshold
* Consecutive failure count
* Consecutive success count
* Logging
* Metrics
* Circuit Breaker
* Multiple health states

Instead of:

```text
UP
DOWN
```

Production systems may have:

```text
UP
DOWN
RECOVERING
DEGRADED
```

Examples:

* Netflix Ribbon
* Spring Cloud LoadBalancer
* NGINX
* HAProxy
* AWS Application Load Balancer

Our implementation is a learning oriented version of the same concepts. 

# Advantages of Periodic Health Checks

* Avoid sending requests to failed servers
* Automatic server recovery
* Better user experience
* Reduced request failures
* Improved fault tolerance
* More intelligent traffic distribution
* Foundation for advanced load balancing techniques

# Conclusion

In the previous version, our Load Balancer distributed traffic without understanding backend availability. By introducing periodic health checks, our custom Java Load Balancer becomes smarter and more resilient.

Instead of discovering failures after sending user traffic, the Load Balancer proactively maintains server health information and distributes requests only among healthy instances. The system can automatically remove failed servers and add them back when they recover. 

This is an important step toward building a more production-ready Load Balancer using Java and Spring Boot.