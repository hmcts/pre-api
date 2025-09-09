# Technology Stack

## Build System
- **Gradle** with wrapper (`./gradlew`) - no need to install Gradle separately
- **Java 21** with toolchain support
- **Spring Boot 3.4.2** application framework

## Core Technologies
- **Spring Boot** with Spring Data JPA, Spring Security, Spring Batch
- **PostgreSQL** database with Flyway migrations
- **Hibernate** ORM with JPA
- **Lombok** for reducing boilerplate code
- **Jackson** for JSON processing
- **OpenFeign** for HTTP client communication

## Testing Framework
- **JUnit 5** for unit testing
- **Testcontainers** for integration testing with PostgreSQL
- **REST Assured** for functional testing
- **Spring Boot Test** for integration testing

## Code Quality Tools
- **Checkstyle** - Google Java Style with 120 character line limit
- **PMD** - static code analysis
- **JaCoCo** - code coverage reporting
- **OWASP Dependency Check** - security vulnerability scanning
- **SonarQube** integration for code quality metrics

## Azure Integration
- **Azure Blob Storage** for file storage
- **Azure Active Directory** for authentication
- **Application Insights** for logging and monitoring

## Common Commands

### Building
```bash
./gradlew build          # Full build with tests
./gradlew bootJar        # Create executable JAR
```

### Testing
```bash
./gradlew test           # Unit tests
./gradlew integration    # Integration tests
./gradlew functional     # Functional tests
./gradlew smoke          # Smoke tests
./gradlew check          # All code quality checks + tests
```

### Running
```bash
./gradlew bootRun        # Run from source
./gradlew bootRun --debug-jvm  # Run with debugger
```

### Database
```bash
docker-compose up --detach     # Start local PostgreSQL
./gradlew flywayMigrate       # Run database migrations
```

### Code Quality
```bash
./gradlew checkstyleMain      # Checkstyle checks
./gradlew pmdMain            # PMD analysis
./gradlew jacocoTestReport   # Generate coverage report
./gradlew dependencyCheck   # Security vulnerability scan
```

## Environment Setup
- Requires `.env` file with environment variables (copy from `.env.local`)
- Load variables: `export $(grep -v '^#' .env | xargs -0)`
- Azure CLI login required for testcontainers: `az acr login --name hmctspublic`