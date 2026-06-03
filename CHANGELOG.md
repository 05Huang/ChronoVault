# Changelog

All notable changes to ChronoVault will be documented in this file.

## [Unreleased]

### Added
- **Input Validation**: 23 type-safe DTOs replacing all raw Map/List @RequestBody parameters across 11 controllers
- **XSS Prevention**: SanitizeUtil for HTML escaping, Jackson ObjectMapper for safe export serialization
- **Security Hardening**: Narrowed WebSocket auth paths, Swagger UI disabled in prod, master key length validation
- **Exception Handling**: 6 new exception handlers (ConstraintViolation, DataIntegrity, HttpMessageNotReadable, etc.)
- **ErrorCode Enum**: Unified error codes (40001-50302) for all API responses
- **Database Optimization**: JPQL JOIN queries replacing N+1, paginated diff queries, transaction-split rollback
- **Structured Logging**: LogContextFilter with MDC (requestId, userId, clientIp), logback-spring.xml config
- **API Design**: Unified pagination, SnapshotController endpoint split (/snapshots paged + /snapshots/all)
- **Audit Enhancement**: @Auditable with resourceType/resourceId, V40 migration, userAgent tracking
- **Dashboard Optimization**: findLatestPerServer() single query replacing N+1, paginated change summaries
- **State Collection**: System info (hostname, IP, memory, disk, CPU, uptime), per-module timing
- **Distributed Locking**: Redis SETNX for AutoSnapshotService and ServerHealthMonitor
- **SSH Metrics**: Connection pool logging (active connections, pool size) every 60s
- **Frontend Error Handling**: API client toast notifications for 400/404/409/429/500 errors
- **Frontend Type Safety**: SockJS type declarations, eliminated all 'any' types in API modules
- **Frontend UX**: SkeletonLoader, EmptyState, LoadingSpinner components, page transitions, breadcrumbs
- **Agent Reliability**: Panic recovery in executeTask(), shutdown-aware heartbeat loop
- **Agent Health Reporting**: Enhanced heartbeat with disk/memory/uptime/restic metrics
- **Docker Hardening**: restart:unless-stopped, healthchecks, memory limits, .dockerignore files
- **CI/CD Pipeline**: GitHub Actions for backend test, frontend type check, agent build
- **Monitoring**: Prometheus + Grafana stack (docker-compose.monitoring.yml)
- **Controller Tests**: 27 new tests (SnapshotController, ServerController, AuthController)
- **Swagger Documentation**: @Tag annotations on all 9 core controllers
- **User Documentation**: QUICKSTART.md, SECURITY.md, updated CLAUDE.md

### Changed
- SnapshotController.getSnapshots(): Now defaults to paged (page=0, size=20)
- SnapshotService.rollback(): Split into non-transactional SSH + transactional DB updates
- AutoSnapshotService: Uses findByAutoSnapshotEnabledTrueAndStatus() query
- DashboardService.getOverview(): Uses findLatestPerServer() and findRecentWithChangeSummary()
- StateCollectionService: Each module timed independently, system info collected
- Application-prod.yml: Prometheus endpoint exposed

### Fixed
- CredentialEncryptor: Added 32-char minimum key length validation
- SecurityConfig: WebSocket auth narrowed from /ws/** to /ws/events + /ws/topics/**
- Agent heartbeatLoop: Now respects shutdown context
- SnapshotServiceTest: Updated to match new repository method signatures

## [0.1.0] - 2026-06-01

### Added
- Initial release with Spring Boot backend, Vue 3 frontend, Go agent
- Server management with SSH connection pooling
- Snapshot creation with Restic backup engine
- state.json collection (packages, services, ports, Docker, configs, crontab)
- Snapshot diff engine (state.json comparison)
- Git-style timeline view
- Diff visualization with syntax highlighting
- Selective rollback capability
- Multi-storage support (Local, S3, OSS, WebDAV)
- Team management with RBAC (OWNER/ADMIN/MEMBER/VIEWER)
- Alert system with configurable rules
- AI-powered analysis (MiMo integration)
- WebSocket real-time updates
- Docker Compose deployment