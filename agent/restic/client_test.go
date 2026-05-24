package restic

import (
	"os/exec"
	"testing"
)

func TestShellEscape(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"hello", "'hello'"},
		{"hello world", "'hello world'"},
		{"it's", `'it'\''s'`},
		{"/path/to/dir", "'/path/to/dir'"},
		{"", "''"},
		{"no-quotes", "'no-quotes'"},
		{"multiple'quotes'here", `'multiple'\''quotes'\''here'`},
	}

	for _, tt := range tests {
		result := shellEscape(tt.input)
		if result != tt.expected {
			t.Errorf("shellEscape(%q) = %q, want %q", tt.input, result, tt.expected)
		}
	}
}

func TestTruncate(t *testing.T) {
	tests := []struct {
		input    string
		maxLen   int
		expected string
	}{
		{"short", 10, "short"},
		{"exactlyten", 10, "exactlyten"},
		{"this is longer than ten", 10, "this is lo..."},
		{"", 5, ""},
		{"abc", 0, "..."},
	}

	for _, tt := range tests {
		result := truncate(tt.input, tt.maxLen)
		if result != tt.expected {
			t.Errorf("truncate(%q, %d) = %q, want %q", tt.input, tt.maxLen, result, tt.expected)
		}
	}
}

func TestBuildRepoUrl(t *testing.T) {
	tests := []struct {
		storageType string
		endpoint    string
		expected    string
	}{
		{"LOCAL", "/data/backups", "/data/backups"},
		{"local", "/data/backups", "/data/backups"},
		{"BLOCK", "/dev/sda1", "/dev/sda1"},
		{"S3", "s3.amazonaws.com/my-bucket", "s3:s3.amazonaws.com/my-bucket"},
		{"s3", "s3.amazonaws.com/my-bucket", "s3:s3.amazonaws.com/my-bucket"},
		{"OSS", "oss-cn-hangzhou.aliyuncs.com/my-bucket", "s3:oss-oss-cn-hangzhou.aliyuncs.com/my-bucket"},
		{"WEBDAV", "https://dav.example.com/backups", "rest:https://dav.example.com/backups"},
		{"UNKNOWN", "/some/path", "/some/path"},
		{"", "/default", "/default"},
	}

	for _, tt := range tests {
		result := BuildRepoUrl(tt.storageType, tt.endpoint)
		if result != tt.expected {
			t.Errorf("BuildRepoUrl(%q, %q) = %q, want %q", tt.storageType, tt.endpoint, result, tt.expected)
		}
	}
}

func TestNewClient(t *testing.T) {
	c := NewClient()
	if c == nil {
		t.Fatal("NewClient() returned nil")
	}
	if c.resticPath != "" {
		t.Errorf("NewClient() resticPath = %q, want empty", c.resticPath)
	}
}

func TestBuildCmd(t *testing.T) {
	c := &Client{resticPath: "/usr/bin/restic"}
	cmd := c.buildCmd("mypassword", "snapshots", "--repo", "/data")

	if cmd.Path == "" {
		t.Error("buildCmd() produced command with empty path")
	}

	// Check RESTIC_PASSWORD is set in environment
	found := false
	for _, env := range cmd.Env {
		if env == "RESTIC_PASSWORD=mypassword" {
			found = true
			break
		}
	}
	if !found {
		t.Error("buildCmd() did not set RESTIC_PASSWORD in environment")
	}

	// Check args
	expectedArgs := []string{"snapshots", "--repo", "/data"}
	if len(cmd.Args) < len(expectedArgs)+1 {
		t.Errorf("buildCmd() args = %v, want at least %d args", cmd.Args, len(expectedArgs)+1)
	}
}

func TestEnsureInstalled_NotFound(t *testing.T) {
	// This test verifies EnsureInstalled returns an error when restic is not available.
	// It will attempt auto-install which should fail in CI/test environments.
	c := &Client{resticPath: ""}

	// Skip if restic happens to be installed
	if _, err := exec.LookPath("restic"); err == nil {
		t.Skip("restic is installed, skipping not-found test")
	}

	err := c.EnsureInstalled()
	if err == nil {
		t.Error("EnsureInstalled() should return error when restic is not available")
	}
}
