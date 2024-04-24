# How to run the Web application

## Build the JAR
````bash
$ cd <project root dir>
$ ./gradlew bootJar
````

## Run the JAR
````bash
$ java -jar build/libs/rcv-1.3.999.jar 
````

## Copy test data
````bash
$ cp -r <contest data dir> /tmp
````

## Connect to it
````bash
$ wget "http://localhost:8080/api/v1.0/appVersion" -O -
...
RCTab 1.3.999
...
$ wget "http://localhost:8080/api/v1.0/tabulate?path=/tmp/2013_minneapolis_mayor/2013_minneapolis_mayor_config.json&name=My%20Name" -O output.json
````
