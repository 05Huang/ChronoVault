.PHONY: help dev test build clean docker-up docker-down

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ============ Development ============

dev: ## Start all services for development
	@echo "Starting PostgreSQL and Redis..."
	docker-compose up -d postgres redis
	@echo "Starting backend..."
	cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &
	@echo "Starting frontend..."
	cd frontend && npm run dev &
	@echo "Services starting... Backend: http://localhost:8080, Frontend: http://localhost:5173"

dev-infra: ## Start only infrastructure (postgres + redis)
	docker-compose up -d postgres redis

dev-backend: ## Start only backend
	cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

dev-frontend: ## Start only frontend
	cd frontend && npm run dev

# ============ Build ============

build: build-backend build-frontend ## Build all components

build-backend: ## Build backend JAR
	cd backend && ./mvnw package -DskipTests -q

build-frontend: ## Build frontend for production
	cd frontend && npm run build

build-agent: ## Build agent binaries (requires Go)
	cd agent && GOOS=linux GOARCH=amd64 go build -o dist/chronovault-agent-linux-amd64 .
	cd agent && GOOS=linux GOARCH=arm64 go build -o dist/chronovault-agent-linux-arm64 .
	cd agent && GOOS=darwin GOARCH=amd64 go build -o dist/chronovault-agent-darwin-amd64 .

# ============ Test ============

test: test-backend test-frontend test-agent ## Run all tests

test-backend: ## Run backend tests
	cd backend && ./mvnw test

test-frontend: ## Check frontend TypeScript
	cd frontend && npx vue-tsc --noEmit

test-agent: ## Run agent tests (requires Go)
	cd agent && go test -race ./...

# ============ Docker ============

docker-up: ## Start all services with Docker
	docker-compose up -d

docker-down: ## Stop all services
	docker-compose down

docker-logs: ## View logs
	docker-compose logs -f

docker-ps: ## Show running containers
	docker-compose ps

# ============ Database ============

db-connect: ## Connect to PostgreSQL
	docker-compose exec postgres psql -U chronovault -d chronovault

db-migrate: ## Run Flyway migrations
	cd backend && ./mvnw flyway:migrate

db-reset: ## Reset database (DESTRUCTIVE!)
	cd backend && ./mvnw flyway:clean flyway:migrate

# ============ Clean ============

clean: ## Clean build artifacts
	cd backend && ./mvnw clean -q
	cd frontend && rm -rf dist node_modules/.vite

clean-docker: ## Remove all containers and volumes
	docker-compose down -v --remove-orphans

# ============ Lint ============

lint: ## Run linting
	cd frontend && npm run lint