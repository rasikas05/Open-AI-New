#!/bin/bash
# Function Validation API - Testing Scripts
# These scripts can be used to test the API endpoint

API_BASE_URL="http://localhost:8080/api/functions"
ENDPOINT="$API_BASE_URL/validate"

echo "================================================"
echo "Function Validation API - Test Scripts"
echo "================================================"
echo ""

# Test 1: Mixed Valid and Invalid
echo "Test 1: Mixed Valid and Invalid FNIDs"
echo "---"
echo "Request:"
curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d '{
    "functionIds": ["MMS001", "MMS200", "OIS100", "INVALID001"]
  }' | jq '.'
echo ""
echo ""

# Test 2: All Valid
echo "Test 2: All Valid FNIDs"
echo "---"
echo "Request:"
curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d '{
    "functionIds": ["MMS001", "MMS200"]
  }' | jq '.'
echo ""
echo ""

# Test 3: All Invalid
echo "Test 3: All Invalid FNIDs"
echo "---"
echo "Request:"
curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d '{
    "functionIds": ["INVALID001", "INVALID002", "INVALID003"]
  }' | jq '.'
echo ""
echo ""

# Test 4: Empty List
echo "Test 4: Empty List"
echo "---"
echo "Request:"
curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d '{
    "functionIds": []
  }' | jq '.'
echo ""
echo ""

# Test 5: With Duplicates
echo "Test 5: With Duplicates"
echo "---"
echo "Request:"
curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d '{
    "functionIds": ["MMS001", "MMS001", "MMS200", "MMS200"]
  }' | jq '.'
echo ""
echo ""

# Test 6: Single Valid
echo "Test 6: Single Valid FNID"
echo "---"
echo "Request:"
curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d '{
    "functionIds": ["MMS001"]
  }' | jq '.'
echo ""
echo ""

# Test 7: Single Invalid
echo "Test 7: Single Invalid FNID"
echo "---"
echo "Request:"
curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d '{
    "functionIds": ["INVALID001"]
  }' | jq '.'
echo ""
echo ""

echo "================================================"
echo "All tests completed!"
echo "================================================"
