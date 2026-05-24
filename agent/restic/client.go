package restic

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os/exec"
	"strings"
	"time"
)

const (
	resticVersion = "0.16.5"
	backupTimeout = 2 * time.Hour
	checkTimeout  = 10 * time.Minute
)

// Snapshot holds parsed backup result from restic JSON output.
type Snapshot struct {
	SnapshotID         string `json:"snapshot_id"`
	Tree               string `json:"tree"`
	TotalBytesProcessed int64  `json:"total_bytes_processed"`
	Time               string `json:"time"`
}

// SnapshotInfo holds a restic snapshot listing entry.
type SnapshotInfo struct {
	ID      string                 `json:"id"`
	Time    string                 `json:"time"`
	Tree    string                 `json:"tree"`
	Summary map[string]interface{} `json:"summary"`
}

// Client executes restic CLI commands locally.
type Client struct {
	resticPath string
}

// NewClient creates a restic client. Call EnsureInstalled before use.
func NewClient() *Client {
	return &Client{}
}

// EnsureInstalled checks if restic is available, attempts auto-install if not.
func (c *Client) EnsureInstalled() error {
	path, err := exec.LookPath("restic")
	if err == nil {
		c.resticPath = path
		// Verify it works
		if out, err := exec.Command(path, "version").CombinedOutput(); err == nil && strings.Contains(string(out), "restic") {
			log.Printf("Restic found at %s: %s", path, strings.TrimSpace(strings.Split(string(out), "\n")[0]))
			return nil
		}
	}

	log.Println("Restic not found, attempting auto-install...")

	// Method 1: Direct binary download
	if c.tryDownload() {
		return nil
	}

	// Method 2: apt-get (Debian/Ubuntu)
	if c.tryAptInstall() {
		return nil
	}

	// Method 3: yum (CentOS/RHEL)
	if c.tryYumInstall() {
		return nil
	}

	return fmt.Errorf("failed to install restic via all methods")
}

func (c *Client) tryDownload() bool {
	url := fmt.Sprintf("https://github.com/restic/restic/releases/download/v%s/restic_%s_linux_amd64.bz2", resticVersion, resticVersion)

	// Try curl first, then wget
	downloadCmd := fmt.Sprintf("curl -fsSL -o /tmp/restic.bz2 %s 2>&1 || wget -q -O /tmp/restic.bz2 %s 2>&1", url, url)
	if out, err := exec.Command("sh", "-c", downloadCmd).CombinedOutput(); err != nil {
		log.Printf("Download failed: %s", string(out))
		return false
	}

	// Try system-wide install
	installCmd := "bunzip2 -f /tmp/restic.bz2 && sudo mv /tmp/restic /usr/local/bin/restic && sudo chmod +x /usr/local/bin/restic"
	if out, err := exec.Command("sh", "-c", installCmd).CombinedOutput(); err == nil {
		if v, err := exec.Command("/usr/local/bin/restic", "version").CombinedOutput(); err == nil && strings.Contains(string(v), "restic") {
			c.resticPath = "/usr/local/bin/restic"
			log.Printf("Restic installed to /usr/local/bin")
			return true
		}
	} else {
		log.Printf("System install failed: %s", string(out))
	}

	// Try user-local install
	installCmd2 := "bunzip2 -f /tmp/restic.bz2 2>/dev/null; mkdir -p ~/.local/bin && mv /tmp/restic ~/.local/bin/restic && chmod +x ~/.local/bin/restic"
	if out, err := exec.Command("sh", "-c", installCmd2).CombinedOutput(); err == nil {
		if v, err := exec.Command("sh", "-c", "export PATH=$HOME/.local/bin:$PATH && restic version").CombinedOutput(); err == nil && strings.Contains(string(v), "restic") {
			c.resticPath = "$HOME/.local/bin/restic"
			log.Printf("Restic installed to ~/.local/bin")
			return true
		}
	} else {
		log.Printf("User-local install failed: %s", string(out))
	}

	return false
}

func (c *Client) tryAptInstall() bool {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()
	cmd := exec.CommandContext(ctx, "sh", "-c", "sudo apt-get update -qq 2>/dev/null && sudo apt-get install -y -qq restic 2>&1")
	if out, err := cmd.CombinedOutput(); err != nil {
		log.Printf("apt-get install failed: %s", string(out))
		return false
	}
	if v, err := exec.Command("restic", "version").CombinedOutput(); err == nil && strings.Contains(string(v), "restic") {
		c.resticPath = "restic"
		log.Printf("Restic installed via apt-get")
		return true
	}
	return false
}

func (c *Client) tryYumInstall() bool {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()
	cmd := exec.CommandContext(ctx, "sh", "-c", "sudo yum install -y restic 2>&1")
	if out, err := cmd.CombinedOutput(); err != nil {
		log.Printf("yum install failed: %s", string(out))
		return false
	}
	if v, err := exec.Command("restic", "version").CombinedOutput(); err == nil && strings.Contains(string(v), "restic") {
		c.resticPath = "restic"
		log.Printf("Restic installed via yum")
		return true
	}
	return false
}

// Init initializes a new restic repository.
func (c *Client) Init(repoURL, password string) error {
	cmd := c.buildCmd(context.Background(), password, "init", "--repo", repoURL)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("restic init failed: %s", string(out))
	}
	return nil
}

// Backup runs a restic backup and returns the snapshot result.
func (c *Client) Backup(repoURL, password string, paths, excludes []string, parentID string) (*Snapshot, error) {
	args := []string{"backup"}
	for _, p := range paths {
		args = append(args, shellEscape(p))
	}
	for _, e := range excludes {
		args = append(args, "--exclude", shellEscape(e))
	}
	if parentID != "" {
		args = append(args, "--parent", parentID)
	}
	args = append(args, "--repo", repoURL, "--json")

	ctx, cancel := context.WithTimeout(context.Background(), backupTimeout)
	defer cancel()
	cmd := c.buildCmd(ctx, password, args...)

	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	exitCode := 0
	if err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			exitCode = exitErr.ExitCode()
		} else {
			return nil, fmt.Errorf("restic backup execution error: %w", err)
		}
	}

	// Exit code 0 = success, 3 = partial success (some files unreadable)
	if exitCode != 0 && exitCode != 3 {
		return nil, fmt.Errorf("restic backup failed (exit=%d): stdout=[%s] stderr=[%s]",
			exitCode, truncate(stdout.String(), 1000), truncate(stderr.String(), 1000))
	}

	if exitCode == 3 {
		log.Println("Restic backup completed with warnings (some files unreadable)")
	}

	// Parse JSON output - restic outputs one JSON object per line
	scanner := bufio.NewScanner(&stdout)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.Contains(line, `"snapshot_id"`) {
			var snap Snapshot
			if err := json.Unmarshal([]byte(line), &snap); err != nil {
				log.Printf("Failed to parse restic output line: %v", err)
				continue
			}
			return &snap, nil
		}
	}

	return nil, fmt.Errorf("no snapshot_id found in restic output")
}

// Snapshots lists all snapshots in the repository.
func (c *Client) Snapshots(repoURL, password string) ([]SnapshotInfo, error) {
	cmd := c.buildCmd(context.Background(), password, "snapshots", "--repo", repoURL, "--json")
	out, err := cmd.CombinedOutput()
	if err != nil {
		return nil, fmt.Errorf("restic snapshots failed: %s", string(out))
	}

	var snapshots []SnapshotInfo
	if err := json.Unmarshal(out, &snapshots); err != nil {
		return nil, fmt.Errorf("failed to parse snapshots: %w", err)
	}
	return snapshots, nil
}

// Restore restores a snapshot to the target path.
func (c *Client) Restore(repoURL, password, snapshotID, targetPath string) error {
	ctx, cancel := context.WithTimeout(context.Background(), backupTimeout)
	defer cancel()
	cmd := c.buildCmd(ctx, password, "restore", snapshotID, "--target", targetPath, "--repo", repoURL)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("restic restore failed: %s", string(out))
	}
	return nil
}

// Check verifies repository integrity.
func (c *Client) Check(repoURL, password string) error {
	ctx, cancel := context.WithTimeout(context.Background(), checkTimeout)
	defer cancel()
	cmd := c.buildCmd(ctx, password, "check", "--repo", repoURL)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("restic check failed: %s", string(out))
	}
	return nil
}

// Diff shows differences between two snapshots.
func (c *Client) Diff(repoURL, password, snap1, snap2 string) (string, error) {
	cmd := c.buildCmd(context.Background(), password, "diff", snap1, snap2, "--repo", repoURL)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("restic diff failed: %s", string(out))
	}
	return string(out), nil
}

// buildCmd constructs an exec.Cmd with RESTIC_PASSWORD set.
func (c *Client) buildCmd(ctx context.Context, password string, args ...string) *exec.Cmd {
	allArgs := args
	cmd := exec.CommandContext(ctx, c.resticPath, allArgs...)
	cmd.Env = append(cmd.Environ(), "RESTIC_PASSWORD="+password)
	return cmd
}

func shellEscape(s string) string {
	return "'" + strings.ReplaceAll(s, "'", "'\\''") + "'"
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}

// BuildRepoUrl constructs a restic repo URL from storage type and endpoint.
func BuildRepoUrl(storageType, endpoint string) string {
	switch strings.ToUpper(storageType) {
	case "LOCAL", "BLOCK":
		return endpoint
	case "S3":
		return "s3:" + endpoint
	case "OSS":
		return "s3:oss-" + endpoint
	case "WEBDAV":
		return "rest:" + endpoint
	default:
		return endpoint
	}
}
