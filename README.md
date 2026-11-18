# Read Me First

The following was discovered as part of building this project:

* The original package name 'com.djt.jukeanator-engine' is invalid and this project uses 'com.djt.jukeanator_engine' instead.

# Documentation

This file is for developers: building the application with Maven, running it from the source tree, and running the tests. Installing and configuring a jukebox PC is covered in separate guides:

- [WindowsConfig.md](WindowsConfig.md): Windows prerequisites (Java, media player), launch script, touchscreen, kiosk settings and auto-start.
- [LinuxConfig.md](LinuxConfig.md): the same for Linux Mint.
- [MasterConfig.md](MasterConfig.md): the MySQL database and master mode setup.

# Building the Application

Build the WAR from the project directory with the Maven wrapper (no separate Maven install is needed). On Linux:

```
./mvnw clean package -DskipTests
```

On Windows:

```
mvn clean package -DskipTests
```

In PowerShell, use `.\mvnw.cmd` instead of `mvnw.cmd`.

This creates `target/jukeanator-engine-0.0.1-SNAPSHOT.war`. `-DskipTests` skips the tests, some of which need a local MySQL server (see "Live Integration Tests"). To deploy the WAR on a jukebox PC, see [WindowsConfig.md](WindowsConfig.md) or [LinuxConfig.md](LinuxConfig.md).

# Running the Application

The application needs a Java 25 runtime and a media player (VLC, or Winamp on Windows). Installing both is described in [WindowsConfig.md](WindowsConfig.md) and [LinuxConfig.md](LinuxConfig.md). The environment variables it reads (`DISCOGS_CONSUMER_KEY`, `DISCOGS_CONSUMER_SECRET`, `JWT_SECRET`, and the database variables when MySQL is used) are described there and in [MasterConfig.md](MasterConfig.md).

## From the source tree

On Windows, in a Command Prompt in the project directory:

```
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.data-dir=C:\kiosk\data --app.config-dir=C:\kiosk\config"
```

On Linux:

```
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--app.data-dir=$HOME/kiosk/data --app.config-dir=$HOME/kiosk/config"
```

In PowerShell, use `.\mvnw.cmd` instead of `mvnw.cmd`. `spring-boot:run` compiles the code but does not run the tests, so `-DskipTests` is not needed.

## From the WAR

```
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir=C:\kiosk\config --app.data-dir=C:\kiosk\data
```

## Externalized config (e.g. kiosk deployments)

By default the app seeds a `config/application.yml` (and sibling `data/`) next to the WAR on first run and reads overrides from there. To point it at a config directory somewhere else instead, pass `--app.config-dir` as a program argument (after the jar filename):

```
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir=C:\kiosk\config
```

For an Eclipse launch configuration, put these in the **Program arguments** box instead:

```
--app.data-dir=C:\kiosk\data --app.config-dir=C:\kiosk\config
```

Note the argument order: JVM options (`-D...`, `--enable-native-access=...`, etc.) come *before* `-jar <file>`, and everything after the jar filename is passed to the application. Putting `--app.config-dir` before `-jar` makes the Java launcher reject it as an unrecognized option.

On first run this seeds `C:\kiosk\config\application.yml` (with `app.data-dir` pre-set to the sibling directory `C:\kiosk\data`) if it does not already exist, then loads overrides from it. This is the same seeding behavior as the WAR-relative default, just rooted at the given directory instead.

# Running the Tests

Run the unit and integration tests with the Maven wrapper. On Linux:

```
./mvnw test
```

On Windows:

```
mvnw.cmd test
```

`./mvnw clean package` (without `-DskipTests`) runs the same tests before building the WAR. Some of the tests need a local MySQL server; see "Live Integration Tests".

## Live Integration Tests

Some tests run against a real local MySQL server: `JukeanatorEngineApplicationTests`, the `MySql*ApplicationTests` classes, the `*RepositoryJpaImplTest` classes, `SongLibraryRepositoryEquivalenceTest`, the `MasterSlave*IntegrationTest` classes and the `WebUiMaster*LifecycleTest` classes. They run with the normal `mvnw test` and need:

- MySQL 8.0 or later on `localhost:3306` (installation is described under "MySQL database" in [MasterConfig.md](MasterConfig.md)).
- A `jukeanator_test` database that the `jukeanator` user can access. The tests reset its tables, so never point them at a database holding real data.
- The `jukeanator` user's password in `DB_PASSWORD`, or the password `password` if `DB_PASSWORD` is not set (see `src/test/resources/application-test.yml`).

If the database and user were already created under "Master mode MySQL setup" in [MasterConfig.md](MasterConfig.md), nothing more is needed. Otherwise, create them with one command.

Linux (Mint):

```
sudo mysql -e "CREATE DATABASE IF NOT EXISTS jukeanator_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'password'; GRANT ALL PRIVILEGES ON jukeanator_test.* TO 'jukeanator'@'localhost'; FLUSH PRIVILEGES;"
```

Windows (in a Command Prompt in `C:\Program Files\MySQL\MySQL Server 8.4\bin`; enter the `root` password when asked):

```
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS jukeanator_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'password'; GRANT ALL PRIVILEGES ON jukeanator_test.* TO 'jukeanator'@'localhost'; FLUSH PRIVILEGES;"
```

If the `jukeanator` user already exists with another password, `CREATE USER IF NOT EXISTS` leaves that password unchanged; set `DB_PASSWORD` to it before running the tests.

## JFC/Swing GUI Functional Tests

These tests drive the real full-screen Swing UI, so they are excluded from the normal test run. The `gui-tests` profile runs every test and then the GUI tests (in a separate JVM), so the local MySQL test database is needed as for the normal tests (see "Live Integration Tests"). Run them on a machine with a display, and do not use the mouse or keyboard while they run. On Linux:

```
./mvnw test -Pgui-tests
```

On Windows:

```
mvnw.cmd test -Pgui-tests
```

# Getting Started

## Reference Documentation

For further reference, see the following:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.4/maven-plugin)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.4/reference/web/servlet.html)
* [HTTP Client](https://docs.spring.io/spring-boot/4.0.4/reference/io/rest-client.html#io.rest-client.restclient)
* [JDBC API](https://docs.spring.io/spring-boot/4.0.4/reference/data/sql.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.4/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Flyway Migration](https://docs.spring.io/spring-boot/4.0.4/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
* [WebSocket](https://docs.spring.io/spring-boot/4.0.4/reference/messaging/websockets.html)
* [Quartz Scheduler](https://docs.spring.io/spring-boot/4.0.4/reference/io/quartz.html)
* [Java Mail Sender](https://docs.spring.io/spring-boot/4.0.4/reference/io/email.html)

## Guides

The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Relational Data using JDBC with Spring](https://spring.io/guides/gs/relational-data-access/)
* [Managing Transactions](https://spring.io/guides/gs/managing-transactions/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Using WebSocket to build an interactive web application](https://spring.io/guides/gs/messaging-stomp-websocket/)

## Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM. While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent. To prevent this, the project POM contains empty overrides for these elements. If you switch to a different parent and want the inheritance, remove those overrides.
