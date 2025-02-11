# These scripts are equivalent to the ones in uploadContest.http and are generated
# by IntelliJ IDEA.  They are untested.

### Upload Minneapolis mayor 2013 config
curl -X POST --location "http://localhost:8080/{{api}}/newContest?contestName=2013_minneapolis_mayor" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d @../src/test/resources/network/brightspots/rcv/test_data/2013_minneapolis_mayor/2013_minneapolis_mayor_config.json

### Upload FAKE chunk 0
curl -X POST --location "http://localhost:8080/{{api}}/castVotes?contestId=%7B%7BcontestId%7D%7D&chunk=0" \
    -H "Content-Type: application/octet-stream" \
    -H "Accept: application/json" \
    -d 'contents 0 line 0
contents 0 line 1'

### Upload FAKE chunk 1
curl -X POST --location "http://localhost:8080/{{api}}/castVotes?contestId=%7B%7BcontestId%7D%7D&chunk=1" \
    -H "Content-Type: application/octet-stream" \
    -H "Accept: application/json" \
    -d 'contents 1 line 0
contents 1 line 1'

### Upload REAL data - chunk 0
curl -X POST --location "http://localhost:8080/{{api}}/castVotes?contestId=%7B%7BcontestId%7D%7D&chunk=0" \
    -H "Content-Type: application/octet-stream" \
    -H "Accept: application/json" \
    -d @../src/test/resources/network/brightspots/rcv/test_data/2013_minneapolis_mayor/2013_minneapolis_mayor_cvr.xlsx

### Tabulate
curl -X GET --location "http://localhost:8080/{{api}}/tabulate?contestId=%7B%7BcontestId%7D%7D&name=Herb+Jellinek" \
    -H "Accept: application/json"

### Get the app version
curl -X GET --location "http://localhost:8080/{{api}}/appVersion" \
    -H "Accept: text/plain"
