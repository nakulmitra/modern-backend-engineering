# Connection & Read Timeouts - Makes Forwarding Safer

In the previous parts of this series, we built a custom Load Balancer that can:

* Distribute requests using **Round Robin**
* Check whether backend servers are healthy
* Retry a request on another server when the selected server fails
* Use a **Circuit Breaker** to temporarily avoid repeatedly failing servers

However, there is still an important problem.

What happens if a backend server does not completely fail, but simply **takes too long to respond**?

For example:

```text
Client
   |
   v
Load Balancer
   |
   v
Server 8080
   |
   |---- Request received
   |
   |---- Server takes 30 seconds
   |
   v
Response
```

If the Load Balancer waits indefinitely for Server 8080, the request can remain blocked for a long time.

This is where **Connection Timeout** and **Read Timeout** become important.

[![](https://markdown-videos-api.jorgenkh.no/youtube/1y0yLSPpFdM)](https://youtu.be/1y0yLSPpFdM)

# 1. Why Do We Need Timeouts?

A Load Balancer communicates with backend servers over the network.

There are different situations in which communication can become slow or stuck:

* The backend server may be unreachable.
* The server may not accept connections quickly.
* The network may be slow.
* The backend application may be overloaded.
* The backend may accept the request but take too long to produce a response.
* A server may be running but effectively unavailable because it is hanging.

Without a timeout, the Load Balancer may wait for an unnecessarily long period.

A timeout gives the Load Balancer a limit ***If the backend does not respond within the expected time, stop waiting and handle the failure.***

This allows the Load Balancer to move on to another available server when appropriate.

# 2. Two Important Types of Timeout

For HTTP communication, two important timeout concepts are:

1. **Connection Timeout**
2. **Read Timeout**

They solve different problems.

A simple way to remember them is:

```text
Connection Timeout
        |
        v
"Can I connect to the server?"

Read Timeout
        |
        v
"After connecting, is the server responding?"
```

# 3. Connection Timeout

A **Connection Timeout** defines how long the client waits while trying to establish a connection with the backend server.

For example:

```text
Connection Timeout = 1 second
```

If the client cannot establish the connection within that time, the connection attempt fails.

### Example

Suppose the Load Balancer selects:

```text
Server 8080
```

But Server 8080 is unreachable.

```text
Load Balancer
      |
      | Connecting...
      |
      | 1 sec
      | 2 sec
      X
 Connection Timeout
```

The client does not continue waiting indefinitely.

Instead, the request fails with a timeout-related exception.

The Load Balancer can then use its existing error-handling logic, such as Retry/Fallback.

# 4. What Does Connection Timeout Control?

Connection timeout controls the time allowed to establish the connection.

Conceptually:

```text
Load Balancer
      |
      |---- Establish connection ---->
      |
      |       Backend Server
      |
      X
      |
 Connection Timeout
```

If the connection cannot be established within the configured time:

```text
Connection Timeout
        |
        v
Exception
        |
        v
Retry / Fallback
        |
        v
Another available server
```

# 5. Read Timeout

A **Read Timeout** is different.

The connection may be successfully established, but the backend server may take too long to send a response.

For example:

```text
Load Balancer
      |
      | Connection established
      v
Server 8080
      |
      | Processing request...
      |
      | 1 sec
      | 2 sec
      X
   Read Timeout
```

Suppose the Read Timeout is:

```text
2 seconds
```

If the client does not receive the expected response/data within the configured timeout period, the request can fail with a timeout-related exception.

The important distinction is ***Connection Timeout is about establishing the connection*** & ***Read Timeout is about waiting for data/response after the connection has been established***

# 6. Connection Timeout vs Read Timeout

| Timeout            | Purpose                                                  | Example   |
| ------------------ | -------------------------------------------------------- | --------- |
| Connection Timeout | Maximum time allowed to establish a connection           | 1 second  |
| Read Timeout       | Maximum time allowed while waiting to read response/data | 2 seconds |

A simple mental model:

```text
                 HTTP Request
                      |
                      v
             +------------------+
             | Connect to Server|
             +------------------+
                      |
             Connection Timeout
                      |
                      v
             +------------------+
             | Server Processes |
             |     Request      |
             +------------------+
                      |
                 Read Timeout
                      |
                      v
                Get Response
```

# 7. Configuring Timeouts in RestClient

This project uses Spring's `RestClient`.

Initially, we might have:

```java
private final RestClient client = RestClient.create();
```

This creates a `RestClient` without explicitly configuring the connection and read timeout values used by our load balancer.

We can create a request factory and configure the timeouts.

```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

factory.setConnectTimeout(1_000);
factory.setReadTimeout(2_000);

this.client = RestClient.builder().requestFactory(factory).build();
```

Here:

```java
factory.setConnectTimeout(1_000);
```

means:

```text
Connection Timeout = 1000 ms = 1 second
```

And:

```java
factory.setReadTimeout(2_000);
```

means:

```text
Read Timeout = 2000 ms = 2 seconds
```

# 8. Forwarding the Request

The actual forwarding code does not need to change significantly.

For example:

```java
return client.get()
        .uri(server.getUrl() + path)
        .retrieve()
        .body(String.class);
```

The `RestClient` now uses the configured timeout values.

Conceptually:

```text
Load Balancer
      |
      v
   RestClient
      |
      +---- Connection Timeout: 1 sec
      |
      +---- Read Timeout: 2 sec
      |
      v
Backend Server
```

# 9. Example: Slow Backend Server

To demonstrate a Read Timeout, we can intentionally make a backend server slow.

For example:

```java
@GetMapping("/hello")
public String hello() throws InterruptedException {

    Thread.sleep(30_000);

    return "Hello from 8080";
}
```

Here:

```java
Thread.sleep(30_000);
```

means the server waits approximately:

```text
30 seconds
```

before returning the response.

But our Load Balancer has:

```text
Read Timeout = 2 seconds
```

Therefore:

```text
Request
   |
   v
Server 8080
   |
   | Processing...
   | 1 sec
   | 2 sec
   X
 Read Timeout
   |
   v
Exception
```

The Load Balancer does not need to wait the full 30 seconds.

# 10. Timeout + Retry/Fallback

This becomes especially useful when combined with the Retry/Fallback mechanism from the previous tutorial.

Suppose we have:

```text
Server 8080 -> Slow
Server 8081 -> Normal
```

The Load Balancer selects Server 8080.

```text
Client
   |
   v
Load Balancer
   |
   v
Server 8080
   |
   | No response within 3 sec
   X
Timeout
```

The timeout results in an exception.

Our existing Retry/Fallback logic can then try another available server:

```text
Server 8080
    |
    X
 Timeout
    |
    v
Retry / Fallback
    |
    v
Server 8081
    |
    v
Success
    |
    v
Response to Client
```

This makes the timeout configuration particularly useful in our custom Load Balancer.

# 11. Timeout Does Not Mean the Server Is Down

An important distinction is that a timeout does **not necessarily mean that the backend server is permanently down**.

For example:

```text
Server 8080
    |
    | Request takes 30 seconds
    |
    v
Read Timeout after 2 seconds
```

The server might still be running.

The problem is that it did not respond within the time allowed by the Load Balancer.

Therefore:

```text
Timeout
   ≠
Server permanently down
```

A timeout can happen because:

* The server is overloaded.
* The application is processing a slow operation.
* The database query is slow.
* A downstream service is slow.
* The network is experiencing delays.
* The server is stuck or unhealthy.
* The configured timeout is too aggressive for that operation.

# 12. Timeout vs Health Check

Timeouts and health checks solve different problems.

### Health Check

A health check asks ***Is this server healthy/reachable?***

For example:

```text
Health Check
     |
     v
Server 8080
     |
     v
Healthy / Unhealthy
```

The result can be used to decide whether the Load Balancer should include that server.

### Timeout

A timeout asks ***How long should I wait for this operation?***

For example:

```text
Request
   |
   v
Server 8080
   |
   | Waiting...
   |
   X
Timeout
```

Therefore:

```text
Health Check
     |
     v
Should I consider this server available?

Timeout
     |
     v
How long should I wait for this request?
```

They complement each other.

# 13. Timeout + Circuit Breaker

Timeouts also work well with the Circuit Breaker introduced earlier.

Suppose Server 8080 repeatedly takes too long to respond.

```text
Request 1 -> Timeout
Request 2 -> Timeout
Request 3 -> Timeout
```

Each timeout can be treated as a failure by the Circuit Breaker.

For example:

```text
Failure Threshold = 3
```

Then:

```text
CLOSED
   |
   | Timeout
   v
Failure Count = 1
   |
   | Timeout
   v
Failure Count = 2
   |
   | Timeout
   v
Failure Count = 3
   |
   v
OPEN
```

Once the Circuit Breaker is OPEN, the Load Balancer can temporarily avoid that server.

So the combination becomes:

```text
Timeout
   |
   v
Failure
   |
   v
Circuit Breaker
   |
   v
Repeated failures?
   |
   v
OPEN
   |
   v
Temporarily skip server
```

This prevents the Load Balancer from repeatedly waiting for the same slow backend.

# 14. Complete Request Flow

Our Load Balancer now has several layers of protection.

```text
Client
  |
  v
Load Balancer
  |
  v
Health Check
  |
  | Healthy servers
  v
Circuit Breaker
  |
  | Available servers
  v
Round Robin
  |
  v
Selected Server
  |
  v
RestClient
  |
  +-------------------------+
  |                         |
  v                         v
Connection Timeout       Read Timeout
  |                         |
  |                         |
  +------------+------------+
               |
               v
        Request Successful?
          /           \
        Yes            No
         |              |
         v              v
      Response       Retry/Fallback
                        |
                        v
                 Another server
```

This gives us a more resilient forwarding flow.

# 15. Why Both Timeouts Are Important

Imagine that we configure only a Read Timeout.

A server that cannot even accept a connection may still cause the Load Balancer to wait while attempting to establish that connection.

Similarly, configuring only a Connection Timeout does not solve the problem of a server that accepts the connection but takes too long to respond.

Therefore, both provide different protections:

```text
Connection Timeout
        |
        v
Protects against slow/unavailable connection establishment

Read Timeout
        |
        v
Protects against slow response/data reading
```

# 16. Choosing Timeout Values

There is no universal value such as:

```text
Connection Timeout = 1 second
Read Timeout = 2 seconds
```

These values are being used in this tutorial simply to demonstrate the concept.

Real applications should choose timeout values based on:

* Expected response time
* API requirements
* Network conditions
* Backend processing time
* Database operations
* Downstream service latency
* Traffic patterns
* User experience requirements

For example, an API expected to respond within 200 ms may use very different timeout values from a report-generation API that legitimately takes several seconds.

The important principle is ***A timeout should be long enough for legitimate requests but short enough to prevent resources from being blocked unnecessarily.***

# 17. Timeout Is Not a Replacement for Retry

A timeout and retry solve different problems.

### Timeout

Controls:

```text
How long should I wait?
```

### Retry/Fallback

Controls:

```text
What should I do when the selected server fails?
```

For example:

```text
Server 8080
     |
     | Read Timeout
     X
     |
     v
Retry/Fallback
     |
     v
Server 8081
     |
     v
Success
```

Therefore, they work together rather than replacing each other.

# 18. Timeout Is Not a Replacement for Circuit Breaker

Similarly:

### Timeout

Handles an individual slow request.

### Circuit Breaker

Remembers repeated failures and prevents continuously sending requests to a problematic server.

For example:

```text
Request 1 -> Server 8080 -> Timeout
Request 2 -> Server 8080 -> Timeout
Request 3 -> Server 8080 -> Timeout
                              |
                              v
                       Circuit OPEN
                              |
                              v
                     Skip Server 8080
```

The timeout provides the failure signal.

The Circuit Breaker provides the memory and protection against repeated failures.

# 19. Connection Timeout and Read Timeout in This Project

The complete architecture can now be viewed as:

```text
                    CLIENT
                       |
                       v
              +----------------+
              | Load Balancer  |
              +----------------+
                       |
                       v
                Health Check
                       |
                       v
             Healthy Servers
                       |
                       v
              Circuit Breaker
                       |
                       v
              Available Servers
                       |
                       v
                 Round Robin
                       |
                       v
                Selected Server
                       |
                       v
                  RestClient
                       |
              +--------+--------+
              |                 |
              v                 v
      Connection Timeout   Read Timeout
              |                 |
              +--------+--------+
                       |
                       v
                  Backend
                       |
             +---------+---------+
             |                   |
             v                   v
          Success             Timeout
             |                   |
             v                   v
          Response         Retry/Fallback
                                 |
                                 v
                         Another Server
```

# 20. Example Configuration

For this tutorial:

```java
SimpleClientHttpRequestFactory factory =
        new SimpleClientHttpRequestFactory();

factory.setConnectTimeout(1000);
factory.setReadTimeout(2000);

RestClient client = RestClient.builder()
        .requestFactory(factory)
        .build();
```

The configuration means:

```text
Connection Timeout -> 1 second
Read Timeout       -> 2 seconds
```

These values are examples for demonstration and should not automatically be copied into production applications.

# 21. Important Takeaways

### Connection Timeout

Defines how long the client waits while establishing a connection.

```text
"Can I connect?"
```

### Read Timeout

Defines how long the client waits while reading the response/data after the connection has been established.

```text
"After connecting, are you responding?"
```

### Retry/Fallback

Provides another server when the current server fails.

```text
"Can I try another server?"
```

### Circuit Breaker

Prevents repeatedly sending requests to a server that keeps failing.

```text
"Should I temporarily stop using this server?"
```

### Health Check

Determines whether a server should be considered healthy/available.

```text
"Is this server healthy?"
```

Together:

```text
Health Check
     ↓
Is server healthy?
     ↓
Circuit Breaker
     ↓
Should server be temporarily skipped?
     ↓
Round Robin
     ↓
Which server should receive request?
     ↓
Timeout
     ↓
How long should we wait?
     ↓
Retry/Fallback
     ↓
Can another server handle the request?
```

# 22. Limitations of This Tutorial Implementation

This implementation intentionally keeps the concept simple.

In a production-grade Load Balancer, additional considerations may include:

### 1. Different timeout values per endpoint

Some APIs may be fast while others legitimately take longer.

### 2. More advanced HTTP clients

Depending on application requirements, other HTTP client implementations can provide additional connection-pool, timeout, and networking configuration.

### 3. Request timeout

A complete production system may need to consider the overall request lifecycle, not just individual connection/read operations.

### 4. Retry limits

Retrying indefinitely can make an overloaded system worse.

Retries should generally have a defined limit.

### 5. Retry backoff

Instead of immediately retrying, systems may use:

```text
Retry
  ↓
Wait
  ↓
Retry
  ↓
Wait longer
  ↓
Retry
```

Techniques such as exponential backoff and jitter can help avoid retry storms.

### 6. Monitoring

Production systems should monitor:

* Timeout count
* Connection failures
* Response latency
* Retry count
* Circuit Breaker state
* Backend error rate
* Request success rate

This makes it possible to identify problematic backend servers.

# 23. Summary

A Load Balancer should not wait indefinitely for a backend server.

Connection and Read Timeouts provide boundaries around HTTP communication.

```text
Connection Timeout
        ↓
Time allowed to establish connection

Read Timeout
        ↓
Time allowed while waiting for response/data
```

When a timeout occurs, it can work together with the existing resilience mechanisms:

```text
Timeout
   ↓
Exception
   ↓
Retry/Fallback
   ↓
Another available server
```

If the same server repeatedly causes failures:

```text
Timeout
   ↓
Circuit Breaker
   ↓
Failure Threshold Reached
   ↓
Circuit OPEN
   ↓
Temporarily Skip Server
```

Therefore, the overall Load Balancer becomes more resilient:

```text
             HEALTH CHECK
                  ↓
          Healthy Servers
                  ↓
          CIRCUIT BREAKER
                  ↓
          Available Servers
                  ↓
            ROUND ROBIN
                  ↓
          Selected Server
                  ↓
              RESTCLIENT
                  ↓
       +----------+----------+
       ↓                     ↓
 CONNECTION TIMEOUT      READ TIMEOUT
       ↓                     ↓
       +----------+----------+
                  ↓
             SUCCESS?
              /     \
            YES      NO
             |        |
             v        v
          Response  RETRY/FALLBACK
                       |
                       v
                 Another Server
```

Timeouts prevent the Load Balancer from waiting too long, Retry/Fallback gives the request another chance and Circuit Breaker prevents repeatedly sending requests to a problematic server.
