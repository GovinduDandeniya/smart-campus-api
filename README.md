# Smart Campus Sensor & Room Management API

A RESTful API built with **JAX-RS (Jersey 2.39.1)** for managing university campus rooms, sensors, and sensor readings. Developed as coursework for **5COSC022W — Client-Server Architectures** at the University of Westminster.

## Technology Stack

- **Java 11** with **JAX-RS** (Jersey 2.39.1 reference implementation)
- **Apache Tomcat 9** embedded via Cargo Maven plugin
- **Jackson** for JSON serialization/deserialization
- **Maven** for build management (WAR packaging)
- **In-memory storage** using `ConcurrentHashMap` (no database)

## Project Structure

```
src/main/java/com/smartcampus/
├── Main.java                          # Base URI constant (server runs via mvn cargo:run)
├── SmartCampusApplication.java        # JAX-RS Application class (extends ResourceConfig)
├── model/
│   ├── Room.java                      # Room entity
│   ├── Sensor.java                    # Sensor entity
│   └── SensorReading.java            # Sensor reading entity
├── resource/
│   ├── DiscoveryResource.java         # GET /api/v1 — API metadata & HATEOAS links
│   ├── RoomResource.java              # CRUD for /api/v1/rooms
│   ├── SensorResource.java           # CRUD for /api/v1/sensors + filtering
│   └── SensorReadingResource.java    # Sub-resource for /api/v1/sensors/{id}/readings
├── storage/
│   └── DataStore.java                 # Thread-safe in-memory singleton
├── exception/
│   ├── RoomNotEmptyException.java             # 409 Conflict
│   ├── RoomNotEmptyExceptionMapper.java
│   ├── LinkedResourceNotFoundException.java   # 422 Unprocessable Entity
│   ├── LinkedResourceNotFoundExceptionMapper.java
│   ├── SensorUnavailableException.java        # 403 Forbidden
│   ├── SensorUnavailableExceptionMapper.java
│   └── GenericExceptionMapper.java            # 500 catch-all (no stack trace leak)
└── filter/
    └── LoggingFilter.java             # Request/response logging filter
src/main/webapp/WEB-INF/
└── web.xml                            # Jersey servlet mapping (/api/v1/*)
```

## Build & Run

### Prerequisites
- Java 11+
- Maven 3.6+

### Build
```bash
mvn clean package
```

### Run (Embedded Tomcat 9)
```bash
mvn cargo:run
```

The server starts at `http://localhost:8080/api/v1/`.

## API Endpoints

### Discovery
| Method | URI | Description |
|--------|-----|-------------|
| GET | `/api/v1` | API metadata with HATEOAS links |

### Rooms
| Method | URI | Description | Status |
|--------|-----|-------------|--------|
| GET | `/api/v1/rooms` | List all rooms | 200 |
| POST | `/api/v1/rooms` | Create a room | 201 |
| GET | `/api/v1/rooms/{id}` | Get room by ID | 200 / 404 |
| PUT | `/api/v1/rooms/{id}` | Update room | 200 / 404 |
| DELETE | `/api/v1/rooms/{id}` | Delete room | 204 / 409 |

### Sensors
| Method | URI | Description | Status |
|--------|-----|-------------|--------|
| GET | `/api/v1/sensors` | List all sensors | 200 |
| GET | `/api/v1/sensors?type=temperature` | Filter by type | 200 |
| POST | `/api/v1/sensors` | Create a sensor | 201 / 422 |
| GET | `/api/v1/sensors/{id}` | Get sensor by ID | 200 / 404 |
| PUT | `/api/v1/sensors/{id}` | Update sensor | 200 / 404 |
| DELETE | `/api/v1/sensors/{id}` | Delete sensor | 204 / 404 |

### Sensor Readings (Sub-resource)
| Method | URI | Description | Status |
|--------|-----|-------------|--------|
| GET | `/api/v1/sensors/{id}/readings` | List readings for sensor | 200 |
| POST | `/api/v1/sensors/{id}/readings` | Add reading | 201 / 403 |

## Sample curl Commands

### 1. Discover the API
```bash
curl http://localhost:8080/api/v1
```
Response:
```json
{
  "name": "Smart Campus Sensor & Room Management API",
  "version": "1.0",
  "description": "RESTful API for managing campus rooms and sensors",
  "contact": "admin@smartcampus.university.ac.uk",
  "resources": { "rooms": "/api/v1/rooms", "sensors": "/api/v1/sensors" },
  "_links": { "self": "/api/v1", "rooms": "/api/v1/rooms", "sensors": "/api/v1/sensors" }
}
```

### 2. Create a Room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"room-101","name":"Physics Lab","capacity":30}'
```

### 3. Create a Sensor (linked to a Room)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"sensor-t1","type":"temperature","status":"ACTIVE","roomId":"room-101"}'
```

### 4. Filter Sensors by Type
```bash
curl "http://localhost:8080/api/v1/sensors?type=temperature"
```

### 5. Post a Sensor Reading
```bash
curl -X POST http://localhost:8080/api/v1/sensors/sensor-t1/readings \
  -H "Content-Type: application/json" \
  -d '{"value":22.5}'
```

### 6. Update a Room
```bash
curl -X PUT http://localhost:8080/api/v1/rooms/room-101 \
  -H "Content-Type: application/json" \
  -d '{"id":"room-101","name":"Physics Lab (Renovated)","capacity":40}'
```

### 7. Delete a Sensor
```bash
curl -X DELETE http://localhost:8080/api/v1/sensors/sensor-t1
```

### 8. Delete a Room (fails if sensors are attached)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/room-101
```

## Error Handling

| HTTP Status | Exception | Trigger |
|-------------|-----------|---------|
| 404 Not Found | `WebApplicationException` | Resource ID does not exist |
| 409 Conflict | `RoomNotEmptyException` | Deleting a room that still has sensors |
| 422 Unprocessable Entity | `LinkedResourceNotFoundException` | Creating a sensor with a non-existent `roomId` |
| 403 Forbidden | `SensorUnavailableException` | Posting a reading to a sensor in MAINTENANCE status |
| 500 Internal Server Error | `GenericExceptionMapper` | Any unhandled exception (no stack trace exposed) |

---

## Coursework Report Answers

### Part 1.1 — JAX-RS Resource Lifecycle: Per-Request vs Singleton

By default, JAX-RS resource classes follow a **per-request lifecycle**: a new instance is created for each incoming HTTP request and discarded after the response is sent. This means resource classes are inherently thread-safe since no instance is shared across concurrent requests, and any instance fields are isolated to a single request.

In contrast, a **singleton** resource (annotated with `@Singleton`) creates one instance for the entire application lifetime. All concurrent requests share this single instance, which means instance fields become shared mutable state and require explicit synchronisation (e.g. `ConcurrentHashMap`, `synchronized` blocks) to be thread-safe.

**In this project**, the resource classes (`RoomResource`, `SensorResource`) use the default per-request scope. They delegate all data storage to `DataStore`, a singleton backed by `ConcurrentHashMap`, which provides thread-safe concurrent access. This cleanly separates the stateless request-handling layer from the shared state layer.

### Part 1.2 — Benefits of HATEOAS in the Discovery Endpoint

HATEOAS (Hypermedia As The Engine Of Application State) means the API response includes hyperlinks that tell the client what actions or resources are available next, instead of requiring the client to hardcode URIs.

**Benefits demonstrated in `/api/v1`:**
1. **Discoverability** — A client only needs to know the root URI. The `_links` section exposes `/api/v1/rooms` and `/api/v1/sensors`, so the client can navigate without prior URI knowledge.
2. **Loose coupling** — If URI structures change (e.g. `/api/v2/rooms`), only the root response changes. Clients that follow links dynamically are unaffected.
3. **Self-documentation** — The response acts as a live map of the API, reducing the need for external documentation.
4. **Evolvability** — New resources can be added to the `_links` map without breaking existing clients, since they simply ignore unknown link relations.

### Part 2.1 — Returning Full Objects vs IDs in Collection Responses

The `GET /api/v1/rooms` endpoint returns **full room objects** in an array, not just IDs.

**Trade-offs:**
- **Full objects** reduce the number of HTTP round-trips: one request gives the client all the data it needs for a list view. This is ideal when the object is small (like our Room with 4 fields) and the collection size is manageable.
- **Returning only IDs** (or summary objects) reduces payload size and is better when objects are large or the collection contains thousands of items. The client then makes follow-up requests for individual details, but this increases latency.

For a campus API where rooms typically number in the tens or low hundreds and the objects are lightweight, returning full objects is the better design choice. It eliminates the N+1 request problem where a client would have to make one additional request per room to get details.

### Part 2.2 — Idempotency of DELETE

An operation is **idempotent** if performing it multiple times produces the same server-side result as performing it once.

`DELETE /api/v1/rooms/{id}` in this API returns **204 No Content** on the first call and **404 Not Found** on subsequent calls. The response code changes, but the server state after any number of DELETE calls is identical: the room does not exist. This is still idempotent because idempotency concerns **server state**, not response codes.

The HTTP specification (RFC 7231 §4.2.2) defines idempotency in terms of the intended effect on the server. Whether the implementation returns 204 or 404 on a repeated DELETE is a design choice — both are valid. Some APIs return 204 on repeated deletes for a simpler client experience, but returning 404 is equally correct and more informative.

### Part 3.1 — What Happens When a Client Sends the Wrong Content-Type

When a resource method is annotated with `@Consumes(MediaType.APPLICATION_JSON)` and the client sends a request with a different Content-Type (e.g. `text/plain` or `application/xml`), the JAX-RS runtime returns **HTTP 415 Unsupported Media Type** before the method body ever executes.

This is because JAX-RS performs content negotiation at the framework level. The runtime matches the request's `Content-Type` header against the `@Consumes` annotations on candidate methods. If no method can consume the given media type, the framework short-circuits with a 415 response.

This design protects resource methods from receiving malformed input — they can safely assume the input has been parsed from the declared media type.

### Part 3.2 — @QueryParam Filtering vs Path-Based Filtering

This API uses `@QueryParam("type")` on `GET /api/v1/sensors?type=temperature` rather than a path-based approach like `GET /api/v1/sensors/type/temperature`.

**@QueryParam advantages:**
- **Optional parameters** — Query parameters are inherently optional. If omitted, the method returns all sensors. With path segments, every segment is mandatory, so you would need separate endpoint methods for filtered and unfiltered responses.
- **Combinability** — Multiple filters can be combined naturally: `?type=temperature&status=ACTIVE`. Path-based filtering becomes deeply nested and rigid: `/sensors/type/temperature/status/ACTIVE`.
- **REST convention** — In RESTful design, the path identifies a resource (`/sensors`), while query parameters filter or modify the representation. Using query parameters for filtering follows this convention.
- **Flexibility** — Adding new filters only requires adding new `@QueryParam` parameters to the same method, rather than defining new path patterns.

Path-based filtering is better suited for hierarchical resources (e.g. `/rooms/{roomId}/sensors`), not for optional attribute-based filtering.

### Part 4.1 — Sub-Resource Locator Pattern Benefits

Sensor readings are accessed via a **sub-resource locator** pattern: `SensorResource` has a method annotated with `@Path("/{sensorId}/readings")` that returns an instance of `SensorReadingResource` rather than handling the request directly.

**Benefits:**
1. **Hierarchical URI modelling** — The URI `/sensors/{id}/readings` naturally expresses that readings belong to a sensor. This relationship is reflected in the code structure.
2. **Separation of concerns** — `SensorResource` handles sensor CRUD while `SensorReadingResource` handles reading operations. Each class has a single responsibility.
3. **Context passing** — The locator method can validate the sensor exists and pass the `sensorId` to `SensorReadingResource`, so the sub-resource operates in a known-valid context.
4. **Reusability** — The sub-resource class could be reused under different parent paths if needed.
5. **Cleaner code** — Without sub-resource locators, all reading endpoints would have to be crammed into `SensorResource`, making it bloated and harder to maintain.

### Part 5.2 — HTTP 422 vs 404: When to Use Each

This API uses **422 Unprocessable Entity** when creating a sensor that references a non-existent `roomId`, rather than returning 404.

**The distinction:**
- **404 Not Found** means the *target resource* of the request does not exist. For `POST /api/v1/sensors`, the target resource is the sensors collection, which does exist. Returning 404 would misleadingly suggest the `/sensors` endpoint itself is missing.
- **422 Unprocessable Entity** means the request syntax is valid JSON, but the semantic content is invalid — the referenced room does not exist. The server understood the request but cannot process it due to a business rule violation.

**When to use each:**
- Use **404** for `GET /rooms/{id}` where the path identifies a specific resource that is not found.
- Use **422** when the request body contains a reference (like `roomId`) to another resource that does not exist. The request itself reached the correct endpoint, but the payload contains invalid cross-references.

This aligns with RFC 4918 §11.2, which defines 422 for cases where the server understands the content type and syntax but cannot process the contained instructions.

### Part 5.4 — Security Risks of Exposing Stack Traces

The `GenericExceptionMapper` catches all unhandled exceptions and returns a generic error message **without** including the stack trace in the response body.

**Risks of exposing stack traces:**
1. **Information disclosure** — Stack traces reveal internal class names, method names, line numbers, library versions, and file paths. Attackers use this to map the application's internal structure.
2. **Dependency exposure** — Traces show exact library versions (e.g. `jersey-2.39.1`, `tomcat-9.0.117`), allowing attackers to search for known CVEs in those specific versions.
3. **Attack surface mapping** — Class names like `DataStore`, `RoomResource`, and package structures help attackers understand the application's architecture and find potential injection points.
4. **SQL/NoSQL injection clues** — If a database were used, stack traces might reveal query structures or ORM mappings.
5. **Path disclosure** — Server file paths in traces can reveal the operating system, deployment structure, and user context.

The secure practice is to log the full exception server-side (as `GenericExceptionMapper` does with `java.util.logging`) and return only a generic message to the client. This follows the OWASP recommendation to never expose internal implementation details through error responses.

### Part 5.5 — JAX-RS Filters vs Manual Logging

This API uses `LoggingFilter`, a JAX-RS `ContainerRequestFilter` and `ContainerResponseFilter`, to log every request and response automatically.

**Advantages over manual logging in each resource method:**
1. **Cross-cutting concern** — Logging applies to all endpoints uniformly. With filters, you write the logic once. Manual logging requires adding log statements to every resource method, which is repetitive and error-prone.
2. **DRY principle** — A single `@Provider`-annotated filter class replaces dozens of scattered log calls. If the log format changes, you update one file instead of every resource.
3. **Completeness** — Filters intercept all requests, including those that result in framework-level errors (404, 415) before reaching resource methods. Manual logging would miss these.
4. **Separation of concerns** — Resource methods focus purely on business logic. They do not need to know about logging, metrics, or auditing.
5. **Ordering and chaining** — Multiple filters can be composed with `@Priority` annotations (e.g. authentication, logging, compression) without modifying business logic.
6. **Maintainability** — Adding or removing logging requires no changes to resource classes. The filter can be disabled by simply removing its `@Provider` annotation or its package from the component scan.

---

## Author

**Govindu Dandeniya**
University of Westminster — 5COSC022W Client-Server Architectures
