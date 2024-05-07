# How to run the Web application

First open two terminals.

## Build the JAR from source

This step is optional.

````bash
$ cd <project root dir>
$ ./gradlew bootJar
````

## Run the server from the JAR file

Do this in terminal 1.

````bash
$ java -jar rcv-1.3.999.jar 
````

## Test that you can connect to it

Do this and all following steps in terminal 2.

````bash
$ curl -X GET --location "http://localhost:8080/api/v1.0/appVersion" \
    -H "Accept: text/plain"
````
Response:
````
RCTab 1.3.999
````

## Use it for real

### Create a new contest

Assuming you have test data in the directory `$my_test_data`, upload an election configuration file to start
a new contest.

````bash
$ curl -X POST --location "http://localhost:8080/api/v1.0/newContest?contestName=2013_minneapolis_mayor" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d @$my_test_data/2013_minneapolis_mayor/2013_minneapolis_mayor_config.json
````
Response:
````json
{
  "contestId": "77d07259-9132-4433-af58-ed97b9090a46",
  "nextUpload": 0
}
````

### Upload this first and only chunk of cast vote records

In the next request, the `contestId` `77d07259-9132-4433-af58-ed97b9090a46` comes from the `contestId` field of the
JSON response above, and the `chunk` value comes from its `nextUpload` field.

````bash
$ curl -X POST --location "http://localhost:8080/api/v1.0/castVotes?contestId=77d07259-9132-4433-af58-ed97b9090a46&chunk=0" \
    -H "Content-Type: application/octet-stream" \
    -H "Accept: application/json" \
    -d @$my_test_data/2013_minneapolis_mayor/2013_minneapolis_mayor_cvr.xlsx
````
Response:
````json
{
  "contestId": "77d07259-9132-4433-af58-ed97b9090a46",
  "nextUpload": 1
}
````

### Tabulate the results

Assuming, as in this case, that all the cast vote data is contained in a single file, we can move on to tabulation. 

````bash
$ curl -X GET --location "http://localhost:8080/api/v1.0/tabulate?contestId=77d07259-9132-4433-af58-ed97b9090a46&name=John%20Eastman" \
    -H "Accept: application/json"
````
Response:
````
{
  "config": {
    "contest": "2013 Minneapolis Mayor",
    "date": "",
    "generatedBy": "RCTab 1.3.999",
    "jurisdiction": "Minneapolis",
    "office": "Mayor"
  },
  "jsonFormatVersion": "1",
  "results": [
    {
...
````
There will be copious log output in terminal 1 as well.

### Clean up

Once you've received the results, you can delete the result files.

````bash
$ curl -X GET --location "http://localhost:8080/api/v1.0/clear?contestId=77d07259-9132-4433-af58-ed97b9090a46" \
    -H "Accept: application/json"
````
Response:
````
HTTP/1.1 202 
Content-Length: 0
````