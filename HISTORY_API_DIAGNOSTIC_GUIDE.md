# Session History API - Empty Results Diagnostic Guide

**Problem**: `GET /api/chat/sessions/{sessionId}/messages` returns `[]` even after successful chat API calls.

## 📋 Overview of Added Diagnostics

The following logs have been added to trace the complete flow:

### 1. **Controller Level** (Entry point)
- ✅ Both `/api/chat` and `/api/chat/comprehend` endpoints already have logging

### 2. **Service Layer - Persistence Calls**
- ✅ **OpenAIService.java** (for `/api/chat` flow):
  - `LOG: "OpenAIService: About to call persistChat()..."`
  - `LOG: "OpenAIService: persistChat() completed successfully..."`

- ✅ **ComprehendChatService.java** (for `/api/chat/comprehend` flow):
  - `LOG: "ComprehendChatService: About to call persistChat()..."`
  - `LOG: "ComprehendChatService: persistChat() completed successfully..."`

### 3. **Persistence Layer - Early Exit Detection**
- ✅ **ChatPersistenceService.persistChat()** - Enhanced with critical diagnostics:
  - `LOG: "EARLY_EXIT: Tenant not found..."` ← If tenant lookup fails
  - `LOG: "Tenant found: tenantId={}, dbId={}"`
  - `LOG: "EARLY_EXIT: User not found..."` ← If user lookup fails
  - `LOG: "User found: userId={}, dbId={}"`
  - `LOG: "EARLY_EXIT: Session not found..."` ← If session lookup fails
  - `LOG: "Session found: sessionId={}, dbId={}"`
  - `LOG: "Generated session title..."` ← Title generation
  - `LOG: "Session saved with title..."` ← After session update
  - `LOG: "Message saved with title..."` ← After message insert

### 4. **Error Handling**
- ✅ All exceptions logged with stack trace: `log.error("Failed to persist chat interaction...")`

---

## 🔍 Step-by-Step Diagnostic Process

### **Step 1: Check Application Logs**

**What to look for:**
```
1. "OpenAIService: About to call persistChat()" - Confirms /api/chat calls persistence
2. "ComprehendChatService: About to call persistChat()" - Confirms /api/chat/comprehend calls persistence
3. "EARLY_EXIT: Tenant not found..." - Means tenant lookup failed
4. "EARLY_EXIT: User not found..." - Means user lookup failed  
5. "EARLY_EXIT: Session not found..." - Means session lookup failed
6. "Failed to persist chat interaction" - Means exception occurred
7. "persistChat() completed successfully" - Confirms persistence succeeded
```

**Where to find logs:**
- Console output (if running locally)
- Log file (usually in `target/logs/` or configurable in `application.properties`)
- Docker logs: `docker logs <container-id>`

---

### **Step 2: Run SQL Verification Queries**

Run these queries in your MySQL client to verify data:

#### **Check Session Table**
```sql
SELECT 
    id, 
    session_id, 
    tenant_id, 
    user_id, 
    title, 
    status, 
    tokens_used, 
    created_at, 
    updated_at 
FROM session 
ORDER BY created_at DESC 
LIMIT 10;
```

**Expected**: Should show recently created sessions with non-null `tenant_id` and `user_id`.

#### **Check Request_Logs Table**
```sql
SELECT 
    id, 
    session_ref_id, 
    original_text, 
    sanitized_text, 
    openai_response, 
    title, 
    tokens_used, 
    created_at 
FROM request_logs 
ORDER BY created_at DESC 
LIMIT 10;
```

**Expected**: Should show recently created messages with non-null `session_ref_id`.

#### **Check Foreign Key Relationship**
```sql
SELECT 
    rl.id, 
    rl.session_ref_id, 
    s.session_id, 
    s.id as session_db_id,
    rl.original_text,
    rl.created_at
FROM request_logs rl
LEFT JOIN session s ON rl.session_ref_id = s.id
ORDER BY rl.created_at DESC 
LIMIT 10;
```

**Expected**: `session_db_id` should NOT be NULL for any recent rows.

#### **Check Session-Tenant-User Relationship**
```sql
SELECT 
    s.id,
    s.session_id,
    s.status,
    t.tenant_code,
    u.username,
    t.id as tenant_db_id,
    u.id as user_db_id,
    COUNT(rl.id) as message_count
FROM session s
LEFT JOIN tenant t ON s.tenant_id = t.id
LEFT JOIN user u ON s.user_id = u.id
LEFT JOIN request_logs rl ON rl.session_ref_id = s.id
GROUP BY s.id
ORDER BY s.created_at DESC
LIMIT 10;
```

**Expected**: 
- `tenant_db_id` and `user_db_id` should NOT be NULL
- `message_count` should be > 0 for sessions that had chat interactions

---

### **Step 3: Trace Session ID Mismatch**

Check if the sessionId in POST API matches what's being queried in GET API:

#### **Find a Specific Session**
```sql
-- Replace 'session-10010' with your actual session ID
SELECT 
    s.id as session_db_id,
    s.session_id,
    t.tenant_code,
    u.username,
    (SELECT COUNT(*) FROM request_logs WHERE session_ref_id = s.id) as message_count
FROM session s
LEFT JOIN tenant t ON s.tenant_id = t.id
LEFT JOIN user u ON s.user_id = u.id
WHERE s.session_id = 'session-10010';
```

**Expected**: Should return exactly 1 row with the session details.

---

### **Step 4: Repository Query Verification**

The GET endpoint calls this method:
```java
findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtAsc(
    tenant, user, sessionId
)
```

**Manual verification** (simulating the query):
```sql
SELECT rl.*
FROM request_logs rl
JOIN session s ON rl.session_ref_id = s.id
WHERE s.tenant_id = ? 
  AND s.user_id = ? 
  AND s.session_id = ?
ORDER BY rl.created_at ASC;
```

---

## 🎯 Likely Root Causes (in order of probability)

### **ROOT CAUSE #1: Session Lookup Failure ⚠️ MOST LIKELY**
**Indicator**: Log shows `"EARLY_EXIT: Session not found..."`

**Why**: When `persistChat()` tries to find the session using:
```java
sessionRepository.findByTenantAndUserAndSessionId(tenant, user, sessionId)
```

It returns empty Optional, causing early exit.

**Possible reasons**:
- Session exists but with different tenant/user association
- Session not created before chat call
- Database transaction isolation issue

**Check**:
```sql
SELECT * FROM session WHERE session_id = 'YOUR_SESSION_ID';
```

---

### **ROOT CAUSE #2: User Lookup Failure**
**Indicator**: Log shows `"EARLY_EXIT: User not found..."`

**Why**: User doesn't exist for the given tenant.

**Check**:
```sql
SELECT * FROM user WHERE username = 'YOUR_USER_ID' AND tenant_id = ?;
```

---

### **ROOT CAUSE #3: Tenant Lookup Failure**
**Indicator**: Log shows `"EARLY_EXIT: Tenant not found..."`

**Why**: Tenant doesn't exist.

**Check**:
```sql
SELECT * FROM tenant WHERE tenant_code = 'YOUR_TENANT_CODE';
```

---

### **ROOT CAUSE #4: Exception During Persistence**
**Indicator**: Log shows `"Failed to persist chat interaction..."`

**Why**: Exception thrown during save operation.

**Action**: Check logs for full stack trace and specific exception message.

---

### **ROOT CAUSE #5: Transaction Not Committed**
**Indicator**: 
- Logs show "persistChat() completed successfully"
- But no data appears in database

**Why**: Potential transaction rollback or connection closed before commit.

**Check**: Verify `@Transactional` annotations are present on service methods.

---

## 📊 Complete Flow Diagram

```
USER SENDS CHAT REQUEST
         ↓
   ChatController.post() / ComprehendChatController.post()
         ↓
   tenantService.registerUserAndSession() ← Creates session if needed
         ↓
   ChatService.chat() / ComprehendChatService.chat()
         ↓
   OpenAIService.chat() / Direct response processing
         ↓
   LOG: "About to call persistChat()"
         ↓
   persistChat(tenantId, userId, sessionId, ...)
         ↓
   ├─ Lookup Tenant by tenantCode ← EARLY EXIT #1
   ├─ Lookup User by tenant + username ← EARLY EXIT #2
   ├─ Lookup Session by tenant + user + sessionId ← EARLY EXIT #3
   ├─ Generate/Update session title
   ├─ Save Session entity
   ├─ Create RequestLog entity
   ├─ Set RequestLog.session = savedSession
   ├─ Set RequestLog.title
   └─ Save RequestLog entity
         ↓
   LOG: "persistChat() completed successfully"
         ↓
   USER GETS CHAT RESPONSE
         ↓
   USER CALLS GET /api/chat/sessions/{sessionId}/messages
         ↓
   ChatPersistenceService.loadSessionMessages()
         ↓
   Query: findBySession_TenantAndSession_UserAndSession_SessionIdOrderByCreatedAtAsc()
         ↓
   Return Messages (or empty [] if query fails)
```

---

## ✅ Testing Checklist

After adding these diagnostics:

- [ ] Build the application with Maven: `mvn clean compile`
- [ ] Start the application with logs enabled
- [ ] Send a chat message via `/api/chat` or `/api/chat/comprehend`
- [ ] Check logs for the "About to call persistChat()" message
- [ ] Check logs for any "EARLY_EXIT" messages
- [ ] Run the SQL queries above to verify data in database
- [ ] Call the GET history API and check if messages appear
- [ ] If empty, compare the sessionId in the GET request with database records

---

## 🚀 Next Steps

1. **Review the logs** - Look for the specific diagnostic messages
2. **Run SQL queries** - Verify data is actually in the database
3. **Identify the bottleneck** - Which log message is missing?
4. **Report findings** - Share:
   - The log output (with timestamps)
   - The SQL query results
   - The sessionId used in both POST and GET requests

This will pinpoint the exact location where data flow is breaking.
