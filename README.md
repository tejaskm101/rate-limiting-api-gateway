# Distributed Rate Limiting API Gateway

A lightweight Spring Boot API Gateway that protects backend services using API-key-based client identification and a Redis-backed Token Bucket rate-limiting algorithm.

The project uses PostgreSQL to store persistent client rate-limit configurations and Redis to maintain the live state of each client's token bucket.

---

## 1. Project Overview

The project demonstrates how an API Gateway can control the number of requests a client is allowed to make.

Each client is identified using an API key:

```http
X-API-Key: user-1
```

The Gateway:

1. Identifies the client using the API key.
2. Fetches the client's rate-limit configuration from PostgreSQL.
3. Checks the client's current token bucket in Redis.
4. Replenishes tokens based on elapsed time.
5. Allows the request if a token is available.
6. Removes one token from the bucket.
7. Forwards the request to the backend service.
8. Returns `429 Too Many Requests` if no token is available.

---

## 2. Architecture

```text
                         PostgreSQL
                    Client Configuration
                           |
                           |
                           v
Client -----> Gateway :8080
               |
               | API Key
               v
        Client Identification
               |
               v
        Rate Limiter Service
               |
               v
             Redis
        Token Bucket State
          /           \
       Allowed       Rejected
          |              |
          v              v
 Backend :8081          429
          |
          v
       Response
```

---

## 3. Technologies Used

- Java 26
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL
- Spring Data Redis
- Redis
- Maven
- Postman
- Git/GitHub

---

# 4. Project Structure

The project consists of two separate Spring Boot applications.

### Gateway

```text
RateLimitingAPIGateway/
│
├── src/main/java/com/example/RateLimitingAPIGateway/
│   │
│   ├── controller/
│   │   └── GatewayController.java
│   │
│   ├── service/
│   │   ├── ClientIdentificationService.java
│   │   └── RateLimiterService.java
│   │
│   ├── entity/
│   │   └── Client.java
│   │
│   ├── repository/
│   │   └── ClientRepository.java
│   │
│   ├── config/
│   │   └── RedisConfig.java
│   │
│   └── RateLimitingApiGatewayApplication.java
│
└── src/main/resources/
    └── application.properties
```

### Backend

```text
RateLimiterBackend/
│
└── src/main/java/
    └── Backend controller/classes
```

The Gateway runs on:

```text
http://localhost:8080
```

The Backend runs on:

```text
http://localhost:8081
```

---

# 5. Gateway Classes

## 5.1 GatewayController

**Package:**

```text
com.example.RateLimitingAPIGateway.controller
```

### Responsibility

`GatewayController` is the entry point for incoming client requests.

It receives the API key, identifies the client, asks the rate limiter whether the request is allowed, and forwards allowed requests to the backend.

### Request Flow

```text
Client Request
      |
      v
GatewayController
      |
      v
ClientIdentificationService
      |
      v
RateLimiterService
      |
      +---- Rejected ---> HTTP 429
      |
      v
Backend :8081
```

The controller receives:

```http
X-API-Key: user-1
```

It then calls `ClientIdentificationService` to identify the client.

If the request is allowed, the controller uses Spring's `RestClient` to communicate with the backend.

If the rate limit has been exceeded, the controller returns:

```text
HTTP 429 Too Many Requests
```

The backend is not called for rejected requests.

---

# 6. Client Entity

## Client.java

### Responsibility

The `Client` entity represents a client registered with the Gateway.

It stores the configuration required by the rate limiter.

Conceptually:

```text
Client
-------------------------
id
apiKey
capacity
refillRate
```

Example:

```text
apiKey     = user-1
capacity   = 10
refillRate = 10
```

This means the client can have a maximum of 10 tokens in its bucket and the bucket is replenished according to the configured refill rate.

The entity is mapped to the PostgreSQL `clients` table using JPA.

---

# 7. ClientRepository

## ClientRepository.java

### Responsibility

`ClientRepository` provides database access for the `Client` entity.

It uses Spring Data JPA to communicate with PostgreSQL.

The main operation is finding a client using its API key.

Conceptually:

```text
API Key
   |
   v
ClientRepository
   |
   v
PostgreSQL
   |
   v
Client configuration
```

For example:

```text
user-1
   |
   v
PostgreSQL
   |
   +-- capacity = 10
   +-- refillRate = 10
```

The rate-limit configuration is therefore stored outside the Gateway's business logic.

---

# 8. ClientIdentificationService

## ClientIdentificationService.java

### Responsibility

This service converts an API key into a client configuration.

For example:

```http
X-API-Key: user-1
```

is passed to:

```text
ClientIdentificationService
```

The service uses:

```text
ClientRepository
```

to retrieve the corresponding client from PostgreSQL.

### Flow

```text
API Key
   |
   v
ClientIdentificationService
   |
   v
ClientRepository
   |
   v
PostgreSQL
   |
   v
Client
```

This keeps client identification separate from the rate-limiting logic.

---

# 9. RateLimiterService

## RateLimiterService.java

This is the **core class of the project**.

### Responsibility

`RateLimiterService` implements the Token Bucket rate-limiting algorithm.

It uses Redis to store the current state of each client's bucket.

The important Redis keys are:

```text
rate_limit:user-1:tokens
rate_limit:user-1:lastRefill
```

The first stores the current number of tokens.

The second stores the timestamp of the last refill calculation.

---

# 10. Token Bucket Algorithm

Imagine every client has a bucket containing tokens.

For example:

```text
Capacity = 10
```

The bucket can contain a maximum of 10 tokens.

Every accepted request consumes one token.

```text
10 tokens
    |
 Request
    v
 9 tokens
    |
 Request
    v
 8 tokens
```

When there are no tokens left:

```text
0 tokens
    |
 Request
    v
HTTP 429
```

The request is rejected before reaching the backend.

---

# 11. Token Replenishment

The bucket does not remain empty forever.

The service stores the time of the last refill calculation:

```text
lastRefill
```

When a new request arrives, the service calculates how much time has elapsed.

Conceptually:

```text
Elapsed Time
     |
     v
Calculate New Tokens
     |
     v
Add Tokens to Bucket
     |
     v
Consume One Token
```

For example, if the refill rate is:

```text
10 tokens / minute
```

then approximately one token becomes available every:

```text
6 seconds
```

The bucket cannot exceed its configured capacity.

Therefore, the system continuously replenishes tokens rather than resetting the bucket every minute.

---

# 12. RedisConfig

## RedisConfig.java

### Responsibility

`RedisConfig` configures the Redis template used by the application.

The configuration makes Redis store keys and values as strings instead of using Java's default object serialization.

For example:

```text
rate_limit:user-1:tokens
```

stores:

```text
"9"
```

This also makes the Redis state easy to inspect using `redis-cli`.

---

# 13. PostgreSQL

PostgreSQL stores **persistent client configuration**.

Example:

```text
clients
------------------------------------------------
id | api_key | capacity | refill_rate
------------------------------------------------
1  | user-1  | 10       | 10
2  | user-2  | 5        | 5
```

PostgreSQL answers:

> "What rate limit does this client have?"

It stores relatively stable information such as:

```text
API Key
Capacity
Refill Rate
```

---

# 14. Redis

Redis stores the **live state** of the Token Bucket.

Example:

```text
rate_limit:user-1:tokens
        |
        v
       "7"
```

and:

```text
rate_limit:user-1:lastRefill
        |
        v
   timestamp
```

Redis is appropriate for this state because the token count can change frequently as requests arrive.

---

# 15. Why Do We Need Both PostgreSQL and Redis?

They serve different purposes.

### PostgreSQL

Stores persistent configuration:

```text
Client
API Key
Capacity
Refill Rate
```

### Redis

Stores frequently changing runtime state:

```text
Current Token Count
Last Refill Timestamp
```

Therefore:

```text
PostgreSQL = Configuration
Redis      = Runtime State
```

A useful interview explanation is:

> PostgreSQL tells us **what the client's limit is**, while Redis tells us **what the client's current bucket state is**.

---

# 16. Backend Service

The backend is intentionally implemented as a separate Spring Boot application.

It runs on:

```text
8081
```

The Gateway runs on:

```text
8080
```

This separation allows the project to demonstrate the role of the Gateway clearly.

The client communicates with the Gateway rather than directly with the backend.

```text
Client
   |
   v
Gateway :8080
   |
   | Allowed
   v
Backend :8081
```

If the request is rejected:

```text
Client
   |
   v
Gateway :8080
   |
   v
429 Too Many Requests

Backend is never contacted.
```

---

# 17. Complete Request Flow

Suppose the client sends:

```http
GET /api/backend
X-API-Key: user-1
```

### Step 1 — Gateway receives request

`GatewayController` receives the request.

```text
Client
   |
   v
GatewayController
```

---

### Step 2 — Identify the client

The API key is passed to:

```text
ClientIdentificationService
```

The service retrieves the client from PostgreSQL.

```text
user-1
   |
   v
PostgreSQL
   |
   v
capacity   = 10
refillRate = 10
```

---

### Step 3 — Check the Token Bucket

`RateLimiterService` checks Redis.

```text
rate_limit:user-1:tokens
```

Suppose Redis contains:

```text
7
```

The request is allowed.

---

### Step 4 — Consume a token

One token is removed:

```text
7 → 6
```

Redis is updated.

---

### Step 5 — Forward the request

The Gateway forwards the request to:

```text
Backend :8081
```

---

### Step 6 — Backend responds

The backend generates its response.

The Gateway then returns that response to the original client.

---

# 18. Rejected Request Flow

Suppose Redis contains:

```text
rate_limit:user-1:tokens = 0
```

The client sends another request.

```text
Client
   |
   v
Gateway
   |
   v
Redis
   |
   v
0 Tokens
   |
   v
Reject
```

The Gateway returns:

```text
HTTP 429 Too Many Requests
```

The backend is not called.

---

# 19. Example

Assume:

```text
user-1
capacity   = 10
refillRate = 10/min
```

Initially:

```text
tokens = 10
```

Requests arriving quickly consume tokens:

```text
Request 1 → 9
Request 2 → 8
Request 3 → 7
Request 4 → 6
Request 5 → 5
...
Request 10 → 0
```

If the bucket remains empty:

```text
Request → 429 Too Many Requests
```

However, tokens are continuously replenished based on elapsed time.

For:

```text
10 tokens / minute
```

approximately one token becomes available every:

```text
6 seconds
```

Therefore, the actual number of requests allowed before a `429` can be greater than the initial capacity if enough time passes between requests.

---

# 20. How to Run the Project

## Start PostgreSQL

Make sure PostgreSQL is running and the project database exists.

Example:

```text
rate_limiting_gateway
```

The Gateway connects to this database through the configuration in:

```text
application.properties
```

---

## Start Redis

Make sure Redis is running locally.

Verify the connection:

```bash
redis-cli
```

Then:

```redis
PING
```

Expected:

```text
PONG
```

---

## Start Backend

Navigate to the backend project:

```bash
cd RateLimiterBackend
```

Run:

```bash
mvn spring-boot:run
```

The backend should start on:

```text
8081
```

---

## Start Gateway

Open another terminal:

```bash
cd RateLimitingAPIGateway
```

Run:

```bash
mvn spring-boot:run
```

The Gateway should start on:

```text
8080
```

Both services must be running for the complete request flow to work.

---

# 21. Testing with Postman

Send:

```http
GET http://localhost:8080/api/backend
```

Add the following header:

```text
X-API-Key: user-1
```

An allowed request should return:

```text
200 OK
```

The request is forwarded to the backend.

After the Token Bucket is exhausted, the Gateway returns:

```text
429 Too Many Requests
```

The backend is not contacted for that request.

---

# 22. Useful Redis Commands

Open Redis CLI:

```bash
redis-cli
```

Check the Redis connection:

```redis
PING
```

Check all keys:

```redis
KEYS *
```

Check the current token count:

```redis
GET rate_limit:user-1:tokens
```

Check the last refill timestamp:

```redis
GET rate_limit:user-1:lastRefill
```

Clear the Redis database during local development:

```redis
FLUSHDB
```

---

# 23. Interview Questions

## Why Redis?

Redis provides fast access to the frequently changing Token Bucket state.

The token count can change with almost every request, making Redis suitable for this runtime state.

---

## Why PostgreSQL?

PostgreSQL stores persistent client configuration such as API keys, bucket capacity, and refill rate.

---

## Why not store everything in PostgreSQL?

The token count changes frequently for every request.

Using Redis for this runtime state avoids repeatedly updating persistent database records for every request.

---

## Why use a Token Bucket?

The Token Bucket algorithm controls request throughput while allowing short bursts of traffic up to the configured bucket capacity.

---

## What happens when the bucket is empty?

The Gateway rejects the request with:

```text
HTTP 429 Too Many Requests
```

The backend is not called.

---

## Does the bucket reset every minute?

No.

Tokens are replenished based on elapsed time.

For example:

```text
10 tokens/minute
≈ 1 token every 6 seconds
```

This allows the bucket to gradually recover instead of resetting at fixed one-minute intervals.

---

## Why are the Gateway and Backend on different ports?

They represent two separate services.

```text
Gateway :8080
Backend :8081
```

This demonstrates that the Gateway sits between the client and the backend service.

---

## What is the role of the API key?

The API key identifies which client's rate-limit configuration should be used.

For example:

```text
X-API-Key: user-1
```

allows the Gateway to retrieve the configuration for `user-1`.

---

## What happens if the API key does not exist?

The Gateway cannot identify the client, so the request is rejected rather than being forwarded to the backend.

---

## Why not put the rate limiter inside the backend?

The Gateway allows rate limiting to happen **before the request reaches the backend**.

This prevents unnecessary traffic from reaching the protected service.

```text
Client
   |
   v
Gateway
   |
   +---- Rejected → 429
   |
   +---- Allowed → Backend
```

---

# 24. One-Minute Interview Explanation

If asked to explain the project, say:

> "I built a lightweight Spring Boot API Gateway that implements API-key-based rate limiting using the Token Bucket algorithm. PostgreSQL stores each client's rate-limit configuration, while Redis stores the live token count and refill timestamp. When a request arrives, the Gateway identifies the client, checks and updates their token bucket, and forwards the request to a separate backend service only if a token is available. Once the bucket is exhausted, the Gateway immediately returns HTTP 429 without contacting the backend."

---

# 25. Core Takeaway

Remember these three components:

```text
PostgreSQL
     ↓
"What is this client's limit?"

Redis
     ↓
"How many tokens does the client currently have?"

Gateway
     ↓
"Should I allow or reject this request?"
```

The complete system is:

```text
Client
   ↓
API Key
   ↓
Gateway
   ↓
PostgreSQL → Client Configuration
   ↓
Redis → Token Bucket State
   ↓
Allow / Reject
   ↓
Backend
```

### In one sentence:

> **PostgreSQL stores the rules, Redis stores the live bucket state, and the Gateway enforces those rules before forwarding requests to the backend.**

---

# 26. Project Scope

This project is intentionally designed as a **small backend/filler project** demonstrating the fundamentals of API Gateway rate limiting.

It focuses on:

- API Gateway architecture
- Token Bucket rate limiting
- Redis runtime state management
- PostgreSQL persistence
- API-key-based client identification
- Backend request forwarding
- HTTP 429 handling

It is **not intended to be a production-grade rate-limiting system** and therefore deliberately avoids unnecessary infrastructure and complexity.
