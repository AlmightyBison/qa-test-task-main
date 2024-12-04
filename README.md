# Test Task for QA Automation Engineer

## Requirements
- Java 21
- GitHub account

## Description

This is a command-line utility that allows you to manage the status of a server.

All changes (events) to the server status are stored in the database (JSON file).

Application supports the following commands:

| Command | Description                                               | Parameters                                                                                                                              | Output                                                                                                                                                                                                                                                                                                     |
|---------|-----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| status  | Shows the server status (up, down, stopping, or starting) |                                                                                                                                         | _Status: DOWN_<br>or<br>_Status: UP<br>Uptime: 60 seconds_                                                                                                                                                                                                                                                 |
| up      | Runs the server                                           |                                                                                                                                         | _Starting...<br>Status: UP_<br>or<br>_Starting...<br>Status: FAILED_                                                                                                                                                                                                                                       |
| down    | Stops the server                                          |                                                                                                                                         | _Stopping...<br>Status: DOWN_<br>or<br>_Stopping...<br>Status: FAILED_                                                                                                                                                                                                                                     |
| history | Shows the history of events                               | --from yyyy-mm-dd<br>--to yyyy-mm-dd<br>--sort asc &#124; desc<br>--status up &#124; down &#124; starting &#124; stopping &#124; failed | _Status: STARTING, Timestamp: 2024-11-07T07:02:33<br>Status: FAILED, Timestamp: 2024-11-07T07:02:33<br>Status: STARTING, Timestamp: 2024-11-07T07:02:39<br>Status: UP, Timestamp: 2024-11-07T07:02:39<br>Status: STOPPING, Timestamp: 2024-11-07T07:02:46<br>Status: DOWN, Timestamp: 2024-11-07T07:02:46_ |

Try some commands and notice the output:

```bash
mvn clean spring-boot:run
```

```bash
mvn clean spring-boot:run "-Dspring-boot.run.arguments=up"
```

```bash
mvn clean spring-boot:run "-Dspring-boot.run.arguments=status"
```

```bash
mvn clean spring-boot:run "-Dspring-boot.run.arguments=down"
```

```bash
mvn clean spring-boot:run "-Dspring-boot.run.arguments=history --sort asc --from 2024-11-01 --to 2024-11-30"
```

```bash
mvn clean spring-boot:run "-Dspring-boot.run.arguments=unknown"
```

Try to run integration tests from command line:

```bash
mvn clean test
```

# Your Task
- Clone or fork this repository to your GitHub account 
- Complete integration tests for the application
- Identify edge cases and write tests for them
- Follow Java coding best practices and code quality standards
- Share the link to your repository with us
