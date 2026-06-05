#!/bin/bash
# Script to add GitHub repository topics
# Usage: ./scripts/github-topics.sh

set -e

echo "Adding GitHub repository topics to ChronoVault..."

# Check if GitHub CLI is installed
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is not installed."
    echo "Install it from: https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "Error: Not authenticated with GitHub CLI."
    echo "Run: gh auth login"
    exit 1
fi

# Add topics to the repository
gh repo edit chronovault/chronovault --add-topic backup,server-management,devops,self-hosted,restic,go-agent,vue3,spring-boot,state-management,git,server-state,configuration-management,infrastructure-as-code,container-management,ssh

echo "Topics added successfully!"
echo ""
echo "Repository topics:"
gh repo view chronovault/chronovault --json repositoryTopics --jq ".repositoryTopics[].name"
