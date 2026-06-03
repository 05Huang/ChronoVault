#!/bin/bash
# ChronoVault - Quick Start Script
# Usage: curl -sSL https://raw.githubusercontent.com/your-org/chronovault/main/quick-start.sh | bash

set -e

echo "============================================"
echo "  ChronoVault - Git for Server State"
echo "  Quick Start Installer"
echo "============================================"
echo ""

# Check prerequisites
check_command() {
    if ! command -v "$1" &> /dev/null; then
        echo "❌ $1 is required but not installed."
        echo "   $2"
        return 1
    fi
    echo "✅ $1 found: $(command -v $1)"
    return 0
}

echo "Checking prerequisites..."
MISSING=0
check_command docker "Install from https://docs.docker.com/get-docker/" || MISSING=1
check_command docker-compose "Install from https://docs.docker.com/compose/install/" || MISSING=1
check_command git "Install from https://git-scm.com/" || MISSING=1

if [ $MISSING -eq 1 ]; then
    echo ""
    echo "Please install missing prerequisites and try again."
    exit 1
fi

echo ""
echo "All prerequisites found!"
echo ""

# Clone or use existing directory
REPO_DIR="${1:-chronovault}"
if [ -d "$REPO_DIR" ]; then
    echo "📁 Directory $REPO_DIR already exists, using it..."
    cd "$REPO_DIR"
else
    echo "📥 Cloning ChronoVault..."
    git clone https://github.com/your-org/chronovault.git "$REPO_DIR"
    cd "$REPO_DIR"
fi

# Generate secrets if .env doesn't exist
if [ ! -f .env ]; then
    echo "🔐 Generating secure configuration..."
    JWT_SECRET=$(openssl rand -hex 32)
    MASTER_KEY=$(openssl rand -hex 32)
    RESTIC_PASSWORD=$(openssl rand -hex 32)

    cat > .env << EOF
# ChronoVault Configuration (auto-generated)
POSTGRES_DB=chronovault
POSTGRES_USER=chronovault
POSTGRES_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$JWT_SECRET
CHRONOVAULT_MASTER_KEY=$MASTER_KEY
CHRONOVAULT_RESTIC_PASSWORD=$RESTIC_PASSWORD
SPRING_PROFILES_ACTIVE=prod
MIMO_ENABLED=false
EOF
    echo "✅ Configuration saved to .env"
else
    echo "✅ Using existing .env configuration"
fi

# Start services
echo ""
echo "🚀 Starting ChronoVault services..."
docker-compose up -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 10

# Health check
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
        echo "✅ Backend is healthy!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "⚠️  Backend may still be starting. Check with: docker-compose logs backend"
    fi
    sleep 2
done

echo ""
echo "============================================"
echo "  🎉 ChronoVault is running!"
echo "============================================"
echo ""
echo "  Frontend:  http://localhost"
echo "  Backend:   http://localhost:8080"
echo "  Swagger:   http://localhost:8080/swagger-ui.html"
echo ""
echo "  Default credentials:"
echo "    Email:    admin@chronovault.com"
echo "    Password: admin123"
echo ""
echo "  Next steps:"
echo "    1. Open http://localhost in your browser"
echo "    2. Register an account or use default credentials"
echo "    3. Add your first server (SSH connection required)"
echo "    4. Create your first snapshot"
echo ""
echo "  Management commands:"
echo "    docker-compose logs -f     # View logs"
echo "    docker-compose down        # Stop services"
echo "    docker-compose up -d       # Start services"
echo "============================================"