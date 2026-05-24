# ChronoVault — Build and Run Instructions

## Project Setup

### Infrastructure (Docker)
```bash
# Start PostgreSQL and Redis
docker-compose up -d postgres redis
```

### Backend (Spring Boot)
```bash
cd backend

# Maven is located at (use full path if mvn is not in PATH):
MVN="/c/Users/34415/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn"

# Compile only
$MVN compile

# Run tests (uses H2 in-memory DB, profile: test)
$MVN test

# Run a single test class
$MVN test -Dtest=SshConnectionManagerTest

# Start in dev mode
$MVN spring-boot:run -Dspring-boot.run.profiles=dev
```

### Frontend (Vue 3)
```bash
cd frontend

# Install dependencies
npm install

# Type check
npx vue-tsc --noEmit

# Dev server (port 5173)
npm run dev

# Production build
npm run build
```

### Agent (Go)
```bash
cd agent

# Build
go build -o chronovault-agent .

# Run tests
go test ./...

# Scan server environment
./chronovault-agent scan

# Start daemon
./chronovault-agent run
```

## Database Migrations

Migrations are in `backend/src/main/resources/db/migration/`.
Check latest version before creating new migration:
```bash
ls backend/src/main/resources/db/migration/ | sort -V | tail -1
```

Naming convention: `V{next_number}__description.sql`

JPA uses `ddl-auto: validate` — schema must match migrations exactly.
If you modify entity fields, you MUST create a new Flyway migration.

## Key Code Patterns

### Backend
- Use Lombok: `@Slf4j`, `@Service`, `@RequiredArgsConstructor`
- Constructor injection (no `@Autowired`)
- `@Transactional` on service methods
- DTOs use Java records: `public record FooDTO(...) {}`
- Entities use `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
- Custom exceptions: `BadRequestException`, `ResourceNotFoundException`
- Global error handling: `GlobalExceptionHandler`

### Frontend
- Tailwind CSS 4 with Material Design 3 tokens
- Icons: `material-symbols-outlined`
- Glass panels: `glass-panel` class
- State management: Pinia stores in `src/stores/`
- API modules: one per domain in `src/api/`
- Types: one per domain in `src/types/`
- Modals: opened via `useModalStore().open({ component, title, props })`

## Verification Checklist (After Each Task)

1. Backend compiles: `$MVN compile` (from backend/ directory)
2. Frontend type checks: `cd frontend && npx vue-tsc --noEmit`
3. Existing tests pass: `$MVN test` (from backend/ directory)
4. Git commit (MANDATORY): `git add -A && git commit -m "feat(scope): description"`

## IMPORTANT: Git Commit
You MUST execute git commit yourself after every task. Do NOT skip this.
Do NOT ask the user to commit. Do NOT suggest manual commit.
Run: `git add -A && git commit -m "..."`

## Key Learnings
- SSH commands use `SshConnection.executeCommand(cmd, timeout)`
- Restic commands are built with `shellEscape()` for security
- Flyway migrations run automatically on app startup
- WebSocket topics: `/topic/events`, `/topic/tasks`, `/topic/tasks/{id}`
