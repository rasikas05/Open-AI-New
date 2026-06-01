# Function Validation API Documentation

## Overview
This API endpoint validates multiple Function IDs (FNID) against the `FUNCTION_MASTER` table and returns both valid and invalid programs.

## Implementation Summary

### Architecture Components

#### 1. DTOs (Data Transfer Objects)

**FunctionValidationRequest.java**
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FunctionValidationRequest {
    private List<String> functionIds;
}
```

**FunctionDetailsResponse.java**
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FunctionDetailsResponse {
    private String fnid;          // Function ID
    private String description;   // From FunctionMaster.tx40 (40-char description)
    private String category;      // From FunctionMaster.fnt3 (3-char category)
    private String mnid;          // Module ID
}
```

**FunctionValidationResponse.java**
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FunctionValidationResponse {
    private int totalRequested;                      // Total FNIDs requested
    private int totalFound;                          // Total valid FNIDs found
    private List<FunctionDetailsResponse> validPrograms;   // Valid functions
    private List<String> invalidPrograms;            // Invalid function IDs
}
```

#### 2. Entity

**FunctionMaster.java** (Already exists)
- Uses `tx40` field for description (40-character limit)
- Uses `fnt3` field for category (3-character limit)
- Uses `fnid` field for Function ID
- Uses `mnid` field for Module ID

#### 3. Repository

**FunctionMasterRepository.java**
```java
@Repository
public interface FunctionMasterRepository extends JpaRepository<FunctionMaster, Long> {
    Optional<FunctionMaster> findByMnidAndMnvrAndFnid(String mnid, String mnvr, String fnid);
    boolean existsByMnidAndMnvrAndFnid(String mnid, String mnvr, String fnid);
    List<FunctionMaster> findByFnidIn(List<String> fnids);  // Custom query for batch lookup
}
```

#### 4. Service

**FunctionValidationService.java** (Interface)
```java
public interface FunctionValidationService {
    FunctionValidationResponse validateFunctionIds(List<String> functionIds);
}
```

**FunctionValidationServiceImpl.java** (Implementation)
```java
@Service
public class FunctionValidationServiceImpl implements FunctionValidationService {

    private static final Logger log = LoggerFactory.getLogger(FunctionValidationServiceImpl.class);
    private final FunctionMasterRepository repository;

    public FunctionValidationServiceImpl(FunctionMasterRepository repository) {
        this.repository = repository;
    }

    @Override
    public FunctionValidationResponse validateFunctionIds(List<String> functionIds) {
        int totalRequested = functionIds == null ? 0 : functionIds.size();
        if (functionIds == null || functionIds.isEmpty()) {
            return new FunctionValidationResponse(totalRequested, 0, List.of(), List.of());
        }

        // Filter null and blank values
        List<String> searchIds = functionIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Single database query for all FNIDs
        List<FunctionMaster> foundFunctions = searchIds.isEmpty()
                ? List.of()
                : repository.findByFnidIn(searchIds);

        // Create map for O(1) lookup
        Map<String, FunctionMaster> foundMap = foundFunctions.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(FunctionMaster::getFnid, fm -> fm, (first, second) -> first));

        List<FunctionDetailsResponse> validPrograms = new ArrayList<>();
        Set<String> validIdsAdded = new LinkedHashSet<>();
        Set<String> invalidPrograms = new LinkedHashSet<>();

        // Process each FNID and separate valid/invalid
        for (String fnid : functionIds) {
            if (fnid == null || fnid.isBlank()) {
                invalidPrograms.add(fnid);
                continue;
            }

            FunctionMaster match = foundMap.get(fnid);
            if (match != null) {
                if (validIdsAdded.add(fnid)) {
                    validPrograms.add(new FunctionDetailsResponse(
                            match.getFnid(),
                            match.getTx40(),      // Description
                            match.getFnt3(),      // Category
                            match.getMnid()       // Module ID
                    ));
                }
            } else {
                invalidPrograms.add(fnid);
            }
        }

        return new FunctionValidationResponse(
                totalRequested,
                validPrograms.size(),
                validPrograms,
                new ArrayList<>(invalidPrograms)
        );
    }
}
```

#### 5. Controller

**FunctionValidationController.java**
```java
@RestController
@RequestMapping("/api/functions")
public class FunctionValidationController {

    private static final Logger logger = LoggerFactory.getLogger(FunctionValidationController.class);
    private final FunctionValidationService validationService;

    public FunctionValidationController(FunctionValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/validate")
    public ResponseEntity<FunctionValidationResponse> validateFunctionIds(
            @RequestBody FunctionValidationRequest request) {
        try {
            if (request == null || request.getFunctionIds() == null) {
                return ResponseEntity.ok(new FunctionValidationResponse(0, 0, List.of(), List.of()));
            }

            FunctionValidationResponse response = validationService.validateFunctionIds(
                    request.getFunctionIds()
            );
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            logger.error("Failed to validate function IDs", ex);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to validate function IDs",
                    ex
            );
        }
    }
}
```

---

## API Endpoint

### Request

**Endpoint:** `POST /api/functions/validate`

**Content-Type:** `application/json`

#### Sample Request Body
```json
{
  "functionIds": [
    "MMS001",
    "MMS200",
    "OIS100",
    "INVALID001"
  ]
}
```

### Response

**HTTP Status:** `200 OK`

**Content-Type:** `application/json`

#### Sample Response
```json
{
  "totalRequested": 4,
  "totalFound": 3,
  "validPrograms": [
    {
      "fnid": "MMS001",
      "description": "Item. Open",
      "category": "WRK",
      "mnid": "M3"
    },
    {
      "fnid": "MMS200",
      "description": "Item. Open Toolbox",
      "category": "WRK",
      "mnid": "M3"
    },
    {
      "fnid": "OIS100",
      "description": "Customer Order. Open",
      "category": "WRK",
      "mnid": "M3"
    }
  ],
  "invalidPrograms": [
    "INVALID001"
  ]
}
```

---

## Implementation Features

### ✅ Completed Requirements

1. **DTOs Created**
   - ✅ FunctionValidationRequest - Accepts list of function IDs
   - ✅ FunctionValidationResponse - Returns validation results
   - ✅ FunctionDetailsResponse - Details for each valid function

2. **Repository**
   - ✅ `findByFnidIn(List<String> fnids)` - Batch lookup method
   - ✅ Uses Spring Data JPA for efficiency

3. **Service Layer**
   - ✅ FunctionValidationService interface defined
   - ✅ FunctionValidationServiceImpl implements validation logic
   - ✅ Single database query using findByFnidIn
   - ✅ Separates valid and invalid programs
   - ✅ Handles null and blank values
   - ✅ Preserves order with LinkedHashSet

4. **Controller**
   - ✅ POST /api/functions/validate endpoint
   - ✅ Constructor injection for FunctionValidationService
   - ✅ Proper exception handling with ResponseStatusException
   - ✅ Logging for debugging
   - ✅ Validates request body

5. **Code Quality**
   - ✅ Uses Lombok annotations for cleaner code
   - ✅ Proper logging with SLF4J
   - ✅ Constructor-based dependency injection
   - ✅ Handles edge cases (null, blank, duplicates)
   - ✅ Efficient O(1) map-based lookup after database query

---

## Performance Characteristics

- **Database Queries:** 1 (single `findByFnidIn` call)
- **Time Complexity:** O(n) where n = number of requested FNIDs
- **Space Complexity:** O(m) where m = number of valid functions found
- **Optimizations:**
  - Single batch query for all FNIDs
  - HashMap for O(1) lookup
  - LinkedHashSet to prevent duplicates while preserving order

---

## Error Handling

- **Invalid Request:** Returns 200 OK with empty validPrograms list
- **Null/Blank FNIDs:** Automatically moved to invalidPrograms
- **Database Errors:** Returns 500 INTERNAL_SERVER_ERROR with error message and logging
- **All exceptions:** Logged with stack trace for debugging

---

## Usage Example (cURL)

```bash
curl -X POST http://localhost:8080/api/functions/validate \
  -H "Content-Type: application/json" \
  -d '{
    "functionIds": ["MMS001", "MMS200", "OIS100", "INVALID001"]
  }'
```

---

## Field Mapping

| Response Field | Source Field | Type | Description |
|---|---|---|---|
| fnid | FunctionMaster.fnid | String (10) | Function ID |
| description | FunctionMaster.tx40 | String (40) | Function Description |
| category | FunctionMaster.fnt3 | String (3) | Function Category |
| mnid | FunctionMaster.mnid | String (10) | Module ID |

---

## Testing Recommendations

1. **Valid Functions:** Test with known FNIDs in database
2. **Invalid Functions:** Test with non-existent FNIDs
3. **Mixed List:** Test with combination of valid and invalid IDs
4. **Edge Cases:** Test with empty list, null values, blank strings
5. **Duplicates:** Test with repeated FNIDs in request

---

## Notes

- The implementation uses constructor injection for better testability
- Logging is implemented for monitoring and debugging
- Response maintains order of valid programs as they appear in results
- Invalid programs are tracked separately for client notification
- The service gracefully handles all null/empty scenarios
