## Create Impression Event
```bash
curl -X PUT \
  http://localhost:18181/events/impression \
  -H 'Content-Type: application/json' \
  -H 'cache-control: no-cache' \
  -d '{
	"requestId": "1",
	"adId": "1",
	"adTitle": "real madrid",
	"advertiserCost": 0.99,
	"appId": "1",
	"appTitle": "one football",
	"impressionTime": 1549634688459
}'
```

## Create Click Event
```bash
curl -X PUT \
  http://localhost:18181/events/click \
  -H 'Content-Type: application/json' \
  -H 'cache-control: no-cache' \
  -d '{
	"requestId": "1",
	"clickTime": 1549634734234
}'
```
