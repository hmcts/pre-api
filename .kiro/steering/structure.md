# Project Structure

## Root Level Organization
```
├── src/                    # Source code
├── config/                 # Configuration files (checkstyle, PMD, OWASP)
├── docker/                 # Docker-related files
├── infrastructure/         # Terraform infrastructure code
├── charts/                 # Helm charts for deployment
├── scripts/               # Utility scripts
├── lib/                   # External libraries and binaries
├── build.gradle           # Gradle build configuration
├── docker-compose.yml     # Local development database
└── lombok.config          # Lombok configuration
```

## Source Code Structure

### Main Application (`src/main/java/uk/gov/hmcts/reform/preapi/`)
- **Application.java** - Main Spring Boot application class
- **controllers/** - REST API endpoints (one controller per domain entity)
- **services/** - Business logic layer
- **repositories/** - Data access layer (Spring Data JPA)
- **entities/** - JPA entity classes
- **dto/** - Data Transfer Objects for API requests/responses
- **config/** - Spring configuration classes
- **security/** - Authentication and authorization
- **media/** - Media processing and storage integration
- **batch/** - Spring Batch job configurations
- **tasks/** - Scheduled tasks and cron jobs
- **email/** - Email service integrations
- **alerts/** - Slack notification services
- **enums/** - Application enumerations
- **exception/** - Custom exception classes and global handler
- **util/**, **utils/** - Utility classes

### Test Structure
- **src/test/** - Unit tests (mirrors main package structure)
- **src/integrationTest/** - Integration tests with database
- **src/functionalTest/** - End-to-end functional tests
- **src/smokeTest/** - Basic smoke tests

### Resources
- **src/main/resources/application.yaml** - Main configuration
- **src/main/resources/db/migration/** - Flyway database migrations
- **src/main/resources/batch/** - Batch processing reference data

## Architectural Patterns

### Layered Architecture
1. **Controllers** - Handle HTTP requests, validation, response formatting
2. **Services** - Business logic, orchestration between repositories
3. **Repositories** - Data access using Spring Data JPA
4. **Entities** - JPA entities with proper relationships

### Naming Conventions
- **Controllers**: `{Entity}Controller.java` (e.g., `BookingController.java`)
- **Services**: `{Entity}Service.java` (e.g., `BookingService.java`)
- **Repositories**: `{Entity}Repository.java` (e.g., `BookingRepository.java`)
- **DTOs**: `{Entity}DTO.java` for responses, `Create{Entity}DTO.java` for requests
- **Entities**: `{Entity}.java` (e.g., `Booking.java`)

### Package Organization
- Group by feature/domain (booking, case, user, etc.)
- Separate DTOs into subpackages by purpose (base, flow, reports, etc.)
- Keep configuration classes in dedicated config package
- Isolate external integrations (media, email, alerts)

## Configuration Files
- **checkstyle.xml** - Google Java Style with 120 char line limit
- **ruleset.xml** - PMD rules configuration
- **suppressions.xml** - OWASP dependency check suppressions
- **.editorconfig** - IDE formatting rules (4 spaces for Java, 2 for others)

## Database
- **Flyway migrations** in `src/main/resources/db/migration/`
- **Naming**: `V{number}__{Description}.sql`
- **PostgreSQL** with proper indexing and constraints
- **JPA entities** with Hibernate annotations