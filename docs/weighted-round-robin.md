# Weighted Round Robin Load Balancing with Configurable Routing Strategy

In the earlier implementation of our custom Load Balancer, we used the **Round Robin** algorithm to distribute incoming requests across backend servers.

Round Robin works well when all backend servers have similar processing capabilities. However, in a real-world environment, backend instances may have different CPU, memory, network capacity, or overall processing capabilities.

For example:

```text
Server 1 -> 8 CPU cores, 16 GB RAM
Server 2 -> 2 CPU cores, 4 GB RAM
```

Treating both servers equally may not be the best approach.

To address this problem, we introduce **Weighted Round Robin**.

Weighted Round Robin allows us to assign a weight to each server. Servers with a higher weight receive a proportionally larger share of requests.

Along with implementing Weighted Round Robin, this version also improves the architecture of our Load Balancer.

Instead of tightly coupling the Load Balancer with one routing algorithm, we introduce a **configurable Load Balancing Strategy**.

This allows us to switch between algorithms such as:

```text
ROUND_ROBIN
WEIGHTED_ROUND_ROBIN
```

through configuration rather than modifying the core Load Balancer service.

# 1. Round Robin

Before understanding Weighted Round Robin, let's review the existing Round Robin approach.

Suppose we have two backend servers:

```text
Server 1 -> localhost:8080
Server 2 -> localhost:8081
```

Round Robin distributes requests sequentially:

```text
Request 1 -> 8080
Request 2 -> 8081
Request 3 -> 8080
Request 4 -> 8081
Request 5 -> 8080
```

The pattern is:

```text
8080 -> 8081 -> 8080 -> 8081 -> ...
```

The main characteristic of Round Robin is that each server receives approximately the same number of requests.

This makes Round Robin simple and effective when the backend servers have similar capabilities.

# 2. Limitation of Round Robin

The main limitation is that Round Robin treats every server equally.

Consider the following servers:

```text
Server 1
8 CPU cores
16 GB RAM

Server 2
2 CPU cores
4 GB RAM
```

With normal Round Robin, both servers still receive approximately 50% of the traffic:

```text
8080
8081
8080
8081
```

This may not be ideal because the servers don't have the same capacity.

The more powerful server may be able to process significantly more requests than the smaller server.

This is where Weighted Round Robin can be useful.

# 3. What is Weighted Round Robin?

**Weighted Round Robin** is an extension of the traditional Round Robin algorithm.

Instead of treating every server equally, we assign a **weight** to each server.

The weight represents the relative preference or capacity of a server.

For example:

```text
Server 8080 -> Weight 3
Server 8081 -> Weight 1
```

This means that server `8080` should receive approximately three times as much traffic as server `8081`.

A simple distribution can therefore look like:

```text
8080
8080
8080
8081
```

The ratio is:

```text
8080 : 8081 = 3 : 1
```

# 4. Weighted Round Robin Example

Consider:

```text
Server 1 -> Weight 3
Server 2 -> Weight 1
```

The total weight is:

```text
3 + 1 = 4
```

Therefore, approximately:

```text
Server 1 -> 3/4 = 75%
Server 2 -> 1/4 = 25%
```

For four requests:

```text
Request 1 -> Server 1
Request 2 -> Server 1
Request 3 -> Server 1
Request 4 -> Server 2
```

For eight requests, the approximate distribution becomes:

```text
Server 1 -> 6 requests
Server 2 -> 2 requests
```

The important concept is that the **relative distribution follows the configured weights**.

# 5. Example with Multiple Servers

Weighted Round Robin can also be used with more than two servers.

For example:

```text
Server A -> Weight 5
Server B -> Weight 3
Server C -> Weight 2
```

Total weight:

```text
5 + 3 + 2 = 10
```

The approximate traffic distribution becomes:

```text
Server A -> 50%
Server B -> 30%
Server C -> 20%
```

This allows the Load Balancer to distribute traffic according to the relative capacity or preference of each backend instance.

# 6. Weight Does Not Mean a Fixed Number of Requests

A weight should not be interpreted as ***This server can process exactly this many requests.***

Instead, it represents a **relative traffic ratio**.

For example:

```text
Server A -> Weight 3
Server B -> Weight 1
```

means that over a sufficiently large number of requests, Server A should receive approximately three times the traffic of Server B.

The actual number of requests can be much larger.

# 7. When Weighted Round Robin Can Be Useful

Weighted Round Robin can be useful in situations where backend servers have different capabilities.

### Different hardware capabilities

For example:

```text
Server A -> 8 CPU cores
Server B -> 4 CPU cores
Server C -> 2 CPU cores
```

Possible weights:

```text
Server A -> 4
Server B -> 2
Server C -> 1
```

The stronger server receives more traffic.

### Different instance sizes

Cloud environments may have backend instances with different resource capacities.

Weights can be used to distribute more traffic toward larger instances.

### Gradual introduction of a server

Suppose an existing server is already handling most of the traffic:

```text
Server A -> Weight 9
```

and a new server is introduced:

```text
Server B -> Weight 1
```

The new server initially receives a smaller share of traffic.

# 8. Simple Weighted Round Robin Implementation

For this custom Load Balancer, we use a simple implementation of Weighted Round Robin.

Suppose we have:

```text
8080 -> Weight 3
8081 -> Weight 1
```

We can conceptually create a weighted sequence:

```text
8080
8080
8080
8081
```

The Load Balancer then selects an entry from this sequence using a counter.

The selection can use the same `Math.floorMod()` technique used in our existing Round Robin implementation:

```java
int index = Math.floorMod(
        counter.getAndIncrement(),
        weightedServers.size()
);
```

This allows the sequence to repeat continuously.

> **Note:** This is a simple weighted implementation for demonstrating the concept. Production-grade load balancers can use more sophisticated algorithms such as Smooth Weighted Round Robin.

# 9. Problem with Hardcoding the Algorithm

After introducing Weighted Round Robin, we now have two algorithms:

```text
Round Robin
Weighted Round Robin
```

If the server-selection logic is directly implemented inside `LoadBalancerService`, the service can become tightly coupled to a particular algorithm.

For example:

```java
if (algorithm.equals("ROUND_ROBIN")) {
    // Round Robin logic
} else if (algorithm.equals("WEIGHTED_ROUND_ROBIN")) {
    // Weighted Round Robin logic
}
```

As more algorithms are added, this class becomes increasingly difficult to maintain.

Future algorithms could include:

```text
Least Connections
IP Hash
Random
```

We don't want to keep modifying the core request-forwarding service every time a new algorithm is introduced.

# 10. Strategy Pattern

To solve this problem, we introduce the **Strategy Pattern**.

The main idea is to separate:

> **How a server is selected**

from:

> **How the Load Balancer processes the request**

We introduce a common interface:

```java
public interface LoadBalancingStrategy {

    Server selectServer(
            List<Server> servers,
            Set<String> excludedServers
    );
}
```

Different algorithms implement this interface.

Our architecture becomes:

```text
LoadBalancerService
        |
        ↓
LoadBalancingStrategy
        |
        +---------------------------+
        |                           |
        ↓                           ↓
RoundRobinStrategy       WeightedRoundRobinStrategy
```

The Load Balancer does not need to know the internal implementation of the selected strategy.

It simply asks the strategy to select a server.

# 11. RoundRobinStrategy

The existing Round Robin logic can be moved into:

```text
RoundRobinStrategy
```

which implements:

```text
LoadBalancingStrategy
```

The strategy maintains its own counter:

```java
private final AtomicInteger counter =
        new AtomicInteger();
```

The next server can be selected using:

```java
int index = Math.floorMod(
        counter.getAndIncrement(),
        servers.size()
);
```

This keeps the Round Robin implementation isolated from the main Load Balancer.

# 12. WeightedRoundRobinStrategy

Similarly, Weighted Round Robin is implemented as:

```text
WeightedRoundRobinStrategy
```

which also implements:

```text
LoadBalancingStrategy
```

Its responsibility is to:

1. Read the weight of each server.
2. Build the weighted selection sequence.
3. Maintain its selection counter.
4. Select the appropriate server.

For example:

```text
8080 -> Weight 3
8081 -> Weight 1
```

can produce:

```text
8080
8080
8080
8081
```

The strategy then selects from this sequence.

The important point is that the Weighted Round Robin logic remains isolated from the core Load Balancer.

# 13. Server Weight

The `Server` model needs to store the weight.

Conceptually:

```java
private int weight;
```

For example:

```text
Server 8080 -> weight = 3
Server 8081 -> weight = 1
```

The weight is then used by `WeightedRoundRobinStrategy`.

# 14. Configurable Algorithm

Now that we have multiple strategies, we need a way to choose which one the Load Balancer should use.

Instead of changing Java code, we use configuration.

For example:

```properties
load-balancer.algorithm=ROUND_ROBIN
```

or:

```properties
load-balancer.algorithm=WEIGHTED_ROUND_ROBIN
```

This means we can switch the routing algorithm by changing configuration.

The core Load Balancer service does not need to be rewritten.

# 15. Load Balancing Algorithm Enum

We can represent the supported algorithms using an enum:

```java
public enum LoadBalancingAlgorithm {

    ROUND_ROBIN,
    WEIGHTED_ROUND_ROBIN
}
```

This provides a strongly typed representation of the supported algorithms.

If another strategy is introduced later, it can be added to the enum.

# 16. Strategy Factory

We then introduce a Strategy Factory.

Its responsibility is to return the appropriate implementation based on configuration.

Conceptually:

```text
Configuration
      |
      ↓
load-balancer.algorithm
      |
      ↓
Strategy Factory
      |
      +--------------------+
      |                    |
      ↓                    ↓
RoundRobinStrategy   WeightedRoundRobinStrategy
```

If the configuration is:

```properties
load-balancer.algorithm=ROUND_ROBIN
```

the factory returns:

```text
RoundRobinStrategy
```

If the configuration is:

```properties
load-balancer.algorithm=WEIGHTED_ROUND_ROBIN
```

the factory returns:

```text
WeightedRoundRobinStrategy
```

The `LoadBalancerService` only interacts with the common interface.

# 17. Interaction with Retry and Fallback

This design is particularly useful because our Load Balancer already has **Retry and Fallback** functionality.

Suppose the first request is routed to:

```text
8080
```

and that server fails.

We need to retry another server.

The retry mechanism should not need to know whether we are using:

```text
Round Robin
```

or:

```text
Weighted Round Robin
```

Instead, the retry process asks the configured strategy to select the next server.

Conceptually:

```text
Request
   |
   ↓
LoadBalancerService
   |
   ↓
LoadBalancingStrategy
   |
   ↓
Select Server
   |
   ↓
Request fails
   |
   ↓
Retry
   |
   ↓
Same Strategy
   |
   ↓
Select another eligible server
```

This keeps the retry mechanism independent of the routing algorithm.

# 18. Excluding Already Attempted Servers

To support Retry and Fallback, the strategy can also receive a set of servers that have already been attempted:

```java
Set<String> excludedServers
```

For example:

```text
Available Servers:

8080
8081
```

First attempt:

```text
8080 -> FAILURE
```

The attempted server is recorded:

```text
Attempted:
8080
```

When the retry occurs, the strategy should avoid selecting the same server again and select another eligible server.

This is especially important when implementing retry with multiple routing algorithms.

# 19. Maximum Attempts

We also need to make sure that retry does not repeatedly call the same backend when only one server is available.

For example:

```properties
load-balancer.max-attempts=2
```

If there is only one available server:

```text
Available servers = 1
Maximum attempts = 2
```

We calculate:

```java
int attempts = Math.min(
        maxAttempts,
        availableServers.size()
);
```

Therefore:

```text
Math.min(2, 1) = 1
```

Only one attempt is made.

If two servers are available:

```text
Math.min(2, 2) = 2
```

Two attempts can be made.

This keeps the retry behavior simple and prevents unnecessary repeated calls to the same server.

# 20. Overall Architecture

The final architecture can be represented as:

```text
                         Client
                            |
                            ↓
                  LoadBalancerService
                            |
              +-------------+-------------+
              |             |             |
              ↓             ↓             ↓
         Health Check  Circuit Breaker  Retry
                                           |
                                           ↓
                              LoadBalancingStrategy
                                           |
                         +-----------------+-----------------+
                         |                                   |
                         ↓                                   ↓
                RoundRobinStrategy              WeightedRoundRobinStrategy
```

The algorithm is selected through configuration:

```text
application.properties
        |
        ↓
load-balancer.algorithm
        |
        ↓
Strategy Factory
        |
        ↓
Selected Strategy
```

# 21. Advantages of This Design

## 21.1 Easy algorithm switching

We can change:

```properties
load-balancer.algorithm=ROUND_ROBIN
```

to:

```properties
load-balancer.algorithm=WEIGHTED_ROUND_ROBIN
```

without changing the core request-forwarding logic.

## 21.2 Separation of responsibilities

The Load Balancer handles:

* Health checks
* Circuit Breaker
* Retry
* Timeouts
* Request forwarding

The strategy handles:

* Server selection

This makes each component easier to understand and maintain.

## 21.3 Easy to extend

If we want to add:

```text
Least Connections
IP Hash
Random
```

we can create new implementations of:

```text
LoadBalancingStrategy
```

without rewriting the existing request-forwarding logic.

## 21.4 Existing functionality remains reusable

Our existing:

```text
Health Check
Retry
Fallback
Circuit Breaker
Timeout
```

logic can continue to work regardless of which server-selection strategy is configured.

# 22. Limitations of the Current Implementation

Although this implementation demonstrates the concept effectively, there are some limitations.

### 22.1 Simple Weighted Sequence

Our implementation creates a repeated weighted sequence.

For example:

```text
Weight 3 -> A A A
Weight 1 -> B
```

This is easy to understand but may not provide the smoothest possible distribution.

More sophisticated algorithms such as **Smooth Weighted Round Robin** can provide a more evenly distributed sequence.

### 22.2 Dynamic Server Changes

If servers are added, removed, or their weights change dynamically, the weighted sequence may need to be rebuilt.

A production implementation would need to handle dynamic configuration carefully.

### 22.3 Static Weights

In this implementation, weights are configured statically.

A production load balancer could potentially derive routing decisions from:

* CPU utilization
* Memory utilization
* Active connections
* Response latency
* Server capacity

This could lead to more dynamic routing approaches.

### 22.4 Weighted Round Robin Is Not Always the Best Algorithm

Weighted Round Robin considers configured server weights, but it does not necessarily consider the server's **current load**.

For example, a server with weight `5` may currently be overloaded.

A load-aware algorithm such as Least Connections or another dynamic strategy may be more appropriate in such situations.

# 23. Summary

In this improvement, we introduced two major concepts.

### Weighted Round Robin

Weighted Round Robin allows us to distribute traffic according to the relative capacity or preference of backend servers.

For example:

```text
8080 -> Weight 3
8081 -> Weight 1
```

results in approximately:

```text
8080 -> 75%
8081 -> 25%
```

of the traffic over time.

### Configurable Load Balancing Strategy

Instead of hardcoding the routing algorithm inside `LoadBalancerService`, we introduced a strategy abstraction.

```text
LoadBalancingStrategy
        |
        +---- RoundRobinStrategy
        |
        +---- WeightedRoundRobinStrategy
```

The algorithm can then be selected through configuration:

```properties
load-balancer.algorithm=ROUND_ROBIN
```

or:

```properties
load-balancer.algorithm=WEIGHTED_ROUND_ROBIN
```

This makes our Load Balancer easier to maintain and extend.

The main responsibility of the Load Balancer remains request processing and resilience, while the Strategy is responsible for deciding **which backend server should receive the request**.