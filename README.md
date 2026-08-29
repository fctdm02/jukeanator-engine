spring-boot:build# Read Me First
The following was discovered as part of building this project:

* The original package name 'com.djt.jukeanator-engine' is invalid and this project uses 'com.djt.jukeanator_engine' instead.

# Building/Running the Application
```
mvn clean package -DskipTests
./mvnw clean package -DskipTests
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war
```

Note: JVM options (`-D...`, `--enable-native-access=...`, etc.) must come *before* `-jar <file>`; anything after the jar filename is passed to the application instead.

## Externalized config (e.g. kiosk deployments)

By default the app seeds a `config/application.yml` (and sibling `data/`) next to the WAR on first run and reads overrides from there. To point it at a config directory somewhere else instead, pass `--app.config-dir` as a program argument (after the jar filename):

```
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir=C:\kiosk\config
```

Note the argument order: JVM options (`-D...`, `--enable-native-access=...`, etc.) come *before* `-jar <file>`; everything after the jar filename is passed to the application instead -- putting `--app.config-dir` before `-jar` makes the JVM launcher itself reject it as an unrecognized option.

On first run this seeds `C:\kiosk\config\application.yml` (with `app.data-dir` pre-wired to a sibling `C:\kiosk\data`) if it doesn't already exist, then loads overrides from it -- same seeding behavior as the WAR-relative default, just rooted at the given directory instead.

# How to start Docker
```
sudo systemctl start docker
```

# Master Mode
Setting up MySQL database for first time use:

```
CREATE DATABASE IF NOT EXISTS jukeanator;

CREATE USER IF NOT EXISTS 'jukeanator'@'%' IDENTIFIED BY 'password';
CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'password';

GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'%';
GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'localhost';

FLUSH PRIVILEGES;
```

# Live Integration Tests
Use the following:

```
sudo mysql -e "CREATE DATABASE IF NOT EXISTS jukeanator_test; 
GRANT ALL PRIVILEGES ON jukeanator_test.* TO 'jukeanator'@'localhost'; 
FLUSH PRIVILEGES;"
```

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.4/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.4/maven-plugin/build-image.html)
* [GraalVM Native Image Support](https://docs.spring.io/spring-boot/4.0.4/reference/packaging/native-image/introducing-graalvm-native-images.html)
* [Spring Boot Testcontainers support](https://docs.spring.io/spring-boot/4.0.4/reference/testing/testcontainers.html#testing.testcontainers)
* [Testcontainers MySQL Module Reference Guide](https://java.testcontainers.org/modules/databases/mysql/)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.4/reference/web/servlet.html)
* [HTTP Client](https://docs.spring.io/spring-boot/4.0.4/reference/io/rest-client.html#io.rest-client.restclient)
* [JDBC API](https://docs.spring.io/spring-boot/4.0.4/reference/data/sql.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.4/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Flyway Migration](https://docs.spring.io/spring-boot/4.0.4/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
* [Elasticsearch](https://docs.spring.io/spring-boot/4.0.4/reference/data/nosql.html#data.nosql.elasticsearch)
* [WebSocket](https://docs.spring.io/spring-boot/4.0.4/reference/messaging/websockets.html)
* [Quartz Scheduler](https://docs.spring.io/spring-boot/4.0.4/reference/io/quartz.html)
* [Testcontainers](https://java.testcontainers.org/)
* [Java Mail Sender](https://docs.spring.io/spring-boot/4.0.4/reference/io/email.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Relational Data using JDBC with Spring](https://spring.io/guides/gs/relational-data-access/)
* [Managing Transactions](https://spring.io/guides/gs/managing-transactions/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Using WebSocket to build an interactive web application](https://spring.io/guides/gs/messaging-stomp-websocket/)

### Additional Links
These additional references should also help you:

* [Configure AOT settings in Build Plugin](https://docs.spring.io/spring-boot/4.0.4/how-to/aot.html)

## GraalVM Native Support

This project has been configured to let you generate either a lightweight container or a native executable.
It is also possible to run your tests in a native image.

### Lightweight Container with Cloud Native Buildpacks
If you're already familiar with Spring Boot container images support, this is the easiest way to get started.
Docker should be installed and configured on your machine prior to creating the image.

To create the image, run the following goal:

```
$ ./mvnw spring-boot:build-image -Pnative
```

Then, you can run the app like any other container:

```
$ docker run --rm -p 8080:8080 jukeanator-engine:0.0.1-SNAPSHOT
```

### Executable with Native Build Tools
Use this option if you want to explore more options such as running your tests in a native image.
The GraalVM `native-image` compiler should be installed and configured on your machine.

NOTE: GraalVM 25+ is required.

To create the executable, run the following goal:

```
$ ./mvnw native:compile -Pnative
```

Then, you can run the app as follows:
```
$ target/jukeanator-engine
```

You can also run your existing tests suite in a native image.
This is an efficient way to validate the compatibility of your application.

To run your existing tests in a native image, run the following goal:

```
$ ./mvnw test -PnativeTest
```


### Testcontainers support

This project uses [Testcontainers at development time](https://docs.spring.io/spring-boot/4.0.4/reference/features/dev-services.html#features.dev-services.testcontainers).

Testcontainers has been configured to use the following Docker images:

* [`mysql:latest`](https://hub.docker.com/_/mysql)

Please review the tags of the used images and set them to the same as you're running in production.

#### Docker setup for tests (Windows, Linux, macOS)

`mvn clean package` runs integration tests that use Testcontainers + MySQL -- the same DBMS the hosting provider runs in production. Docker must be installed and the daemon must be reachable.

- **Windows**: install [Docker Desktop](https://www.docker.com/products/docker-desktop/) and ensure it is running.
- **macOS**: install Docker Desktop, [Colima](https://github.com/abiosoft/colima), or [Rancher Desktop](https://rancherdesktop.io/).
- **Linux**: install Docker Engine and ensure your user can run Docker commands.

Quick verification:

```
docker version
docker run --rm mysql:latest --version
```

If you are using Colima on macOS, export Docker host before running Maven:

```
export DOCKER_HOST=unix://${HOME}/.colima/default/docker.sock
./mvnw clean package
```

Windows PowerShell equivalent:

```
docker version
docker run --rm mysql:latest --version
.\mvnw.cmd clean package
```

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
