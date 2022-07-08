# foss-promoter-quarkus
FOSS Promoter demo based on Quarkus

# How to play with this demo. 

1. Build the code: 
```shell
mvn clean package
```


2. Build the containers using docker compose: 
```shell
docker-compose build 
```

3. Run everything using docker compose: 
```shell
docker-compose up -d
```

4. Send a Git repository for the system to generate the QR codes for each of the commits in the repository:

```shell
java -jar fp-cli/target/fp-cli-1.0.0-SNAPSHOT.jar  --api-server http://localhost:8080 --repository https://github.com/apache/maven.git
```

4.1. Repeat step 4 as you wish.

5. Shutdown everything
```shell
docker-compose down
```


# Tips: 

- To watch repositories that are being added to the system
```shell
docker exec -it foss-promoter-quarkus-kafka-1 ./bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic repositories
```


- To watch the commit events emitted in the system
```shell
 docker exec -it foss-promoter-quarkus-kafka-1 ./bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic commits
```

To test an invalid data input into the Web API service: 

```shell
curl --verbose -X POST http://localhost:8080/api/repository/ -H 'Accept: application/json' -H 'Content-Type: application/json' -d '{"nameeee": "https://github.com/apache/camel.git"}'
```
