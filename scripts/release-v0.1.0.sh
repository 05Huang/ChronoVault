#!/bin/bash
# ChronoVault v0.1.0 Release Script
# This script builds Agent binaries, creates a git tag, and prepares release notes
# Usage: ./scripts/release-v0.1.0.sh

set -e

VERSION="0.1.0"
RELEASE_DIR="releases/v${VERSION}"

echo "=== ChronoVault v${VERSION} Release ==="
echo ""

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v go &> /dev/null; then
    echo "Error: Go is not installed"
    exit 1
fi

if ! command -v git &> /dev/null; then
    echo "Error: Git is not installed"
    exit 1
fi

echo "Prerequisites OK"
echo ""

# Create release directory
echo "Creating release directory..."
mkdir -p "${RELEASE_DIR}"

# Build Agent binaries for multiple platforms
echo "Building Agent binaries..."

cd agent

# Linux amd64
echo "  Building linux/amd64..."
GOOS=linux GOARCH=amd64 go build -ldflags="-s -w -X main.version=${VERSION}" -o "../${RELEASE_DIR}/chronovault-agent-linux-amd64" .

# Linux arm64
echo "  Building linux/arm64..."
GOOS=linux GOARCH=arm64 go build -ldflags="-s -w -X main.version=${VERSION}" -o "../${RELEASE_DIR}/chronovault-agent-linux-arm64" .

# macOS amd64
echo "  Building darwin/amd64..."
GOOS=darwin GOARCH=amd64 go build -ldflags="-s -w -X main.version=${VERSION}" -o "../${RELEASE_DIR}/chronovault-agent-darwin-amd64" .

# macOS arm64 (Apple Silicon)
echo "  Building darwin/arm64..."
GOOS=darwin GOARCH=arm64 go build -ldflags="-s -w -X main.version=${VERSION}" -o "../${RELEASE_DIR}/chronovault-agent-darwin-arm64" .

# Windows amd64
echo "  Building windows/amd64..."
GOOS=windows GOARCH=amd64 go build -ldflags="-s -w -X main.version=${VERSION}" -o "../${RELEASE_DIR}/chronovault-agent-windows-amd64.exe" .

cd ..

# Generate checksums
echo "Generating checksums..."
cd "${RELEASE_DIR}"
sha256sum chronovault-agent-* > checksums.txt
cd ../..

# Create git tag
echo "Creating git tag v${VERSION}..."
git tag -a "v${VERSION}" -m "Release v${VERSION}: Initial release of ChronoVault

Features:
- Multi-server state management with Git-like operations
- State-aware snapshots (packages, services, ports, Docker, configs)
- Snapshot diff and comparison
- Selective rollback capability
- Branch, stash, bisect, cherry-pick operations
- Timeline view with change summaries
- Webhook notifications
- Disaster recovery plans
- AI-powered insights and recommendations

Security:
- AES-256-GCM encryption for credentials
- JWT authentication with role-based access control
- SSH key rotation support
- Audit logging for all operations

Infrastructure:
- Docker Compose deployment
- GitHub Actions CI/CD
- Prometheus metrics
- Grafana dashboards"

echo ""
echo "=== Release v${VERSION} Prepared ==="
echo ""
echo "Next steps:"
echo "1. Push the tag: git push origin v${VERSION}"
echo "2. Create GitHub Release:"
echo "   gh release create v${VERSION} \\"
echo "     --title 'ChronoVault v${VERSION}' \\"
echo "     --notes-file CHANGELOG.md \\"
echo "     ${RELEASE_DIR}/chronovault-agent-*"
echo "3. Verify release artifacts:"
echo "   - chronovault-agent-linux-amd64"
echo "   - chronovault-agent-linux-arm64"
echo "   - chronovault-agent-darwin-amd64"
echo "   - chronovault-agent-darwin-arm64"
echo "   - chronovault-agent-windows-amd64.exe"
echo "   - checksums.txt"
echo ""
echo "Release notes are in CHANGELOG.md"
