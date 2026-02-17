# Airline Staff Authentication System - Memory Bank

## Project Overview
**Project Name:** airline-staff-auth  
**Type:** Full-stack web application for airline staff authentication and management  
**Repository:** https://github.com/vittal94/airline-staff-auth.git  
**Last Commit:** f8b26e9ed562e219f22bdddd85730b9892fc3d36

## Architecture

### Technology Stack

#### Backend
- **Framework:** Jakarta EE (Servlet API 6.1.0)
- **Language:** Java 21
- **Build Tool:** Maven
- **Server:** Apache Tomcat (embedded in Docker)
- **Packaging:** WAR file

#### Frontend
- **Framework:** Vue.js 3.5.26
- **Build Tool:** Vite 7.3.0
- **Language:** JavaScript (ES Modules)
- **Node Version:** ^20.19.0 || >=22.12.0

#### Database
- **RDBMS:** PostgreSQL 16 (Alpine)
- **Migration Tool:** Flyway 10.4.1
- **Connection Pool:** HikariCP 5.1.0

#### Infrastructure
- **Containerization:** Docker & Docker Compose
- **Email Testing:** Mailpit (SMTP server with web UI)

## Project Structure

```
airline-staff-auth/
├── airline-backend/          # Java backend application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/airline/airlinebackend/
│   │   │   │   ├── config/           # Application configuration
│   │   │   │   ├── dto/              # Data Transfer Objects
│   │   │   │   │   ├── request/      # Request DTOs
│   │   │   │   │   └── response/     # Response DTOs
│   │   │   │   ├── exception/        # Custom exceptions
│   │   │   │   ├── model/            # Domain models
│   │   │   │   │   └── emums/        # Enumerations
│   │   │   │   └── HelloServlet.java # Sample servlet
│   │   │   ├── resources/
│   │   │   │   ├── db/migration/     # Flyway SQL migrations
│   │   │   │   └── application.properties
│   │   │   └── webapp/               # Web resources
│   │   └── test/                     # Unit tests
│   ├── pom.xml                       # Maven configuration
│   ├── Dockerfile                    # Production Docker image
│   └── Dockerfile.dev                # Development Docker image
│
├── airline-frontend/         # Vue.js frontend application
│   ├── src/
│   │   ├── assets/          # Static assets (CSS, images)
│   │   ├── components/      # Vue components
│   │   ├── App.vue          # Root component
│   │   └── main.js          # Application entry point
│   ├── public/              # Public static files
│   ├── package.json         # NPM dependencies
│   ├── vite.config.js       # Vite configuration
│   └── Dockerfile           # Frontend Docker image
│
├── docker-compose.yml       # Docker orchestration
├── DOCKER-README.md         # Docker setup guide
├── .env                     # Environment variables
└── .gitignore              # Git ignore rules
```

## Key Dependencies

### Backend Dependencies
| Dependency | Version | Purpose |
|------------|---------|---------|
| Jakarta Servlet API | 6.1.0 | Web servlet framework |
| PAC4J Core | 6.0.3 | Security framework |
| PAC4J Jakarta EE | 6.0.3 | Jakarta EE integration |
| PostgreSQL Driver | 42.7.1 | Database connectivity |
| HikariCP | 5.1.0 | Connection pooling |
| Flyway Core | 10.4.1 | Database migrations |
| Jackson Databind | 2.16.1 | JSON processing |
| Bouncy Castle | 1.77 | Argon2 password hashing |
| Jakarta Mail | 2.1.2 | Email functionality |
| OWASP Encoder | 1.2.3 | XSS prevention |
| Jsoup | 1.17.2 | HTML sanitization |
| SLF4J | 2.0.11 | Logging API |
| Logback | 1.4.14 | Logging implementation |
| JUnit Jupiter | 5.11.0 | Testing framework |
| Mockito | 5.8.0 | Mocking framework |

### Frontend Dependencies
| Dependency | Version | Purpose |
|------------|---------|---------|
| Vue | 3.5.26 | Frontend framework |
| Vite | 7.3.0 | Build tool & dev server |
| @vitejs/plugin-vue | 6.0.3 | Vue plugin for Vite |
| vite-plugin-vue-devtools | 8.0.5 | Vue DevTools integration |

## Database Schema

### Tables

#### 1. users
**Purpose:** Store airline staff user accounts

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() | Unique user identifier |
| email | VARCHAR(255) | NOT NULL, UNIQUE | User email address |
| password_hash | VARCHAR(255) | NOT NULL | Argon2 hashed password |
| name | VARCHAR(100) | | User full name |
| role | VARCHAR(50) | NOT NULL, CHECK | User role (ADMIN, FLIGHT_MANAGER, CUSTOMER_MANAGER) |
| status | VARCHAR(50) | NOT NULL, DEFAULT 'PENDING_EMAIL_CONFIRMATION' | Account status |
| password_change_required | BOOLEAN | NOT NULL, DEFAULT false | Force password change flag |
| first_login_completed | BOOLEAN | NOT NULL, DEFAULT false | First login tracking |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Account creation time |
| updated_at | TIMESTAMP WITH TIME ZONE | | Last update time |
| last_login_at | TIMESTAMP WITH TIME ZONE | | Last successful login |

**Indexes:**
- `idx_users_email` on email
- `idx_users_status` on status
- `idx_users_role` on role
- `idx_users_created_at` on created_at

#### 2. sessions
**Purpose:** Manage user sessions (V2 migration)

#### 3. remember_me_tokens
**Purpose:** Store "Remember Me" tokens (V3 migration)

#### 4. email_confirmation_tokens
**Purpose:** Email verification tokens (V4 migration)

#### 5. login_attempts
**Purpose:** Track login attempts for rate limiting (V5 migration)

### Enumerations

#### Role
- `ADMIN` - System administrator
- `FLIGHT_MANAGER` - Flight operations manager
- `CUSTOMER_MANAGER` - Customer service manager

#### UserStatus
- `PENDING_EMAIL_CONFIRMATION` - Email not verified
- `PENDING_APPROVAL` - Awaiting admin approval
- `ACTIVE` - Active account
- `BLOCKED` - Blocked account

## Domain Models

### User Model
**Location:** `airline-backend/src/main/java/com/airline/airlinebackend/model/User.java`

**Key Features:**
- Builder pattern for object construction
- UUID-based identification
- Role-based access control
- Status-based account management
- Timestamp tracking (created, updated, last login)
- Password change enforcement
- First login tracking

**Key Methods:**
- `isLogin()` - Check if user status is ACTIVE
- `isAdmin()` - Check if user has ADMIN role
- `builder()` - Get Builder instance

### Other Models
- **Session** - User session management
- **RememberMeToken** - Persistent login tokens
- **EmailConfirmationToken** - Email verification
- **LoginAttempt** - Brute force protection

## DTOs (Data Transfer Objects)

### Request DTOs
- **LoginRequest** - User login credentials
- **RegisterRequest** - New user registration
- **ChangePasswordRequest** - Password change
- **ResendConfirmationRequest** - Resend email confirmation
- **UpdateCabinetRequest** - Update user profile

### Response DTOs
- **ApiResponse** - Generic API response wrapper
- **UserResponse** - User data response
- **CabinetResponse** - User cabinet/profile data
- **DashboardResponse** - Dashboard data
- **ErrorResponse** - Error information

## Exception Hierarchy

**Base Exception:** `BaseException`

**Custom Exceptions:**
- `AccountBlockedException` - Account is blocked
- `AccountNotApprovedException` - Account pending approval
- `AuthenticationException` - Authentication failure
- `AuthorizationException` - Authorization failure
- `DuplicateResourceException` - Resource already exists
- `EmailNotConfirmedException` - Email not verified
- `RateLimitExceededException` - Too many requests
- `ResourceNotFoundException` - Resource not found
- `ValidationException` - Input validation failure

## Configuration

### Application Configuration
**Location:** `airline-backend/src/main/java/com/airline/airlinebackend/config/`

- **AppConfig.java** - General application configuration
- **DatasourceConfig.java** - Database connection configuration

### Environment Variables (.env)
- `DB_NAME` - Database name
- `DB_USER` - Database username
- `DB_PASS` - Database password
- `DB_HOST` - Database host (default: localhost)
- `DB_PORT` - Database port (default: 5432)
- `SMTP_HOST` - SMTP server host
- `SMTP_PORT` - SMTP server port

## Docker Configuration

### Services

#### 1. postgres
- **Image:** postgres:16-alpine
- **Container:** airline-postgres
- **Port:** 5433:5432 (host:container)
- **Volume:** postgres_data
- **Health Check:** pg_isready

#### 2. mailpit
- **Image:** axllent/mailpit:latest
- **Container:** airline-mailpit
- **Ports:**
  - 8025 - Web UI
  - 1025 - SMTP server
- **Max Messages:** 5000

#### 3. backend (optional, profile: full-stack)
- **Build:** ./airline-backend/Dockerfile.dev
- **Container:** airline-backend
- **Port:** 8080:8080
- **Depends On:** postgres, mailpit

#### 4. frontend (optional, profile: full-stack)
- **Build:** ./airline-frontend/Dockerfile
- **Container:** airline-frontend
- **Port:** 5173:5173
- **Environment:** VITE_API_URL=http://localhost:8080

### Network
- **Name:** airline-network
- **Driver:** bridge

## Development Workflow

### Backend Development
1. Start infrastructure: `docker-compose up postgres mailpit`
2. Run Flyway migrations: `mvn flyway:migrate`
3. Build project: `mvn clean package`
4. Deploy WAR to Tomcat or run in IDE

### Frontend Development
1. Install dependencies: `npm install`
2. Start dev server: `npm run dev`
3. Access at: http://localhost:5173

### Full Stack (Docker)
```bash
docker-compose --profile full-stack up
```

## Database Migrations

### Flyway Configuration
**Location:** `airline-backend/pom.xml`

**Connection:**
- URL: jdbc:postgresql://localhost:5433/airline_db
- User: airline_user
- Password: securePassword123!
- Migrations: src/main/resources/db/migration

### Migration Files
1. **V1__create_users_table.sql** - Users table
2. **V2__create_session_table.sql** - Sessions table
3. **V3__remember_me_tokens_table.sql** - Remember me tokens
4. **V4__create_email_confirmation_tokens_table.sql** - Email tokens
5. **V5__create_login_attempts_table.sql** - Login attempts
6. **V6__insert_initial_admin.sql** - Initial admin user
7. **V7__create_session_cleanup_job.sql** - Session cleanup job

## Security Features

### Authentication
- Argon2 password hashing (Bouncy Castle)
- Session-based authentication
- Remember Me functionality
- Email confirmation required
- Admin approval workflow

### Authorization
- Role-based access control (RBAC)
- Three roles: ADMIN, FLIGHT_MANAGER, CUSTOMER_MANAGER
- PAC4J security framework integration

### Protection Mechanisms
- Rate limiting (login attempts tracking)
- Account blocking
- XSS prevention (OWASP Encoder)
- HTML sanitization (Jsoup)
- Password change enforcement
- First login tracking

## Testing

### Test Structure
**Location:** `airline-backend/src/test/java/`

**Test Categories:**
- **Unit Tests** (`unittests/`)
  - DTO tests
  - Exception tests
  - Model tests
- **Integration Tests** (to be implemented)
- **Password Tests** (`PasswordTest.java`)
- **Model Tests** (`modelTest/`)

### Testing Frameworks
- JUnit Jupiter 5.11.0
- Mockito 5.8.0

## API Endpoints (Planned)

### Authentication
- POST `/api/auth/login` - User login
- POST `/api/auth/register` - User registration
- POST `/api/auth/logout` - User logout
- POST `/api/auth/confirm-email` - Confirm email
- POST `/api/auth/resend-confirmation` - Resend confirmation

### User Management
- GET `/api/user/profile` - Get user profile
- PUT `/api/user/profile` - Update profile
- POST `/api/user/change-password` - Change password
- GET `/api/user/dashboard` - Get dashboard data

### Admin
- GET `/api/admin/users` - List all users
- PUT `/api/admin/users/:id/approve` - Approve user
- PUT `/api/admin/users/:id/block` - Block user

## Port Allocation

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL | 5433 | Database (mapped from 5432) |
| Mailpit Web UI | 8025 | Email testing interface |
| Mailpit SMTP | 1025 | SMTP server |
| Backend | 8080 | Java backend API |
| Frontend | 5173 | Vue.js dev server |

## Build Commands

### Backend
```bash
# Clean and build
mvn clean package

# Run tests
mvn test

# Run Flyway migrations
mvn flyway:migrate

# Run Flyway clean (WARNING: drops all objects)
mvn flyway:clean
```

### Frontend
```bash
# Install dependencies
npm install

# Development server
npm run dev

# Production build
npm run build

# Preview production build
npm run preview
```

## Important Notes

### Database Schema Issues
⚠️ **Known Issue in V1 Migration:**
- Line 19: `CREATE INDEX idx_users_email ON user(email);` - should be `users` not `user`
- Line 20: `CREATE INDEX idx_users_status ON user(status);` - should be `users` not `user`
- Line 24: `COMMENT ON TABLE user IS...` - should be `users` not `user`

### Initial Setup
1. Ensure Docker Desktop is running
2. Create `.env` file with required variables
3. Start PostgreSQL and Mailpit: `docker-compose up postgres mailpit`
4. Run Flyway migrations: `mvn flyway:migrate`
5. Verify database schema in PostgreSQL

### Email Testing
- Access Mailpit UI at http://localhost:8025
- All emails sent by the application will be captured
- No actual emails are sent externally

## Future Enhancements
- [ ] Implement service layer
- [ ] Implement repository/DAO layer
- [ ] Add REST controllers
- [ ] Implement frontend authentication flow
- [ ] Add API documentation (Swagger/OpenAPI)
- [ ] Implement integration tests
- [ ] Add logging configuration
- [ ] Implement session cleanup job
- [ ] Add password reset functionality
- [ ] Implement 2FA (Two-Factor Authentication)

## Development Team Notes

### Code Style
- Java 21 features enabled
- Builder pattern for complex objects
- Enum-based type safety
- UUID for primary keys
- Timestamp with timezone for temporal data

### Best Practices
- Use DTOs for API communication
- Custom exceptions for error handling
- Database migrations via Flyway
- Connection pooling with HikariCP
- Secure password hashing with Argon2
- Input validation and sanitization
- Comprehensive unit testing

---

**Last Updated:** 2026-02-17  
**Maintained By:** Development Team  
**Version:** 1.0-SNAPSHOT
