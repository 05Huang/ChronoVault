package restic

import (
	"context"
	"os/exec"
	"strings"
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
		{"path with spaces and 'quotes'", "'path with spaces and '\\''quotes'\'''" },
		{"special!@#$%^&*()", "'special!@#$%^&*()'"},
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
		{"12345", 5, "12345"},
		{"123456", 5, "12345..."},
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
	ctx := context.Background()
	cmd := c.buildCmd(ctx, "mypassword", "snapshots", "--repo", "/data")

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

	// Check args: ["/usr/bin/restic", "snapshots", "--repo", "/data"]
	if len(cmd.Args) != 4 {
		t.Errorf("buildCmd() args count = %d, want 4", len(cmd.Args))
	}
	if cmd.Args[1] != "snapshots" || cmd.Args[2] != "--repo" || cmd.Args[3] != "/data" {
		t.Errorf("buildCmd() args = %v, want [snapshots --repo /data]", cmd.Args[1:])
	}
}

func TestBuildCmd_ContextCancellation(t *testing.T) {
	c := &Client{resticPath: "/usr/bin/restic"}
	ctx, cancel := context.WithCancel(context.Background())
	cancel() // Cancel immediately

	cmd := c.buildCmd(ctx, "pass", "snapshots")
	if cmd == nil {
		t.Fatal("buildCmd() returned nil")
	}
	// Context is cancelled, but the cmd object is still valid
	if cmd.Context() != ctx {
		t.Error("buildCmd() did not use the provided context")
	}
}

func TestClassifyError(t *testing.T) {
	tests := []struct {
		output   string
		exitCode int
		contains string
	}{
		{"no space left on device", 1, "磁盘空间不足"},
		{"permission denied", 1, "权限不足"},
		{"connection refused", 1, "无法连接"},
		{"timeout occurred", 1, "连接超时"},
		{"authentication failed", 1, "存储认证失败"},
		{"repository does not exist", 1, "备份仓库不存在"},
		{"wrong password", 1, "备份密码错误"},
		{"some error", 10, "仓库锁冲突"},
		{"partial read", 12, "部分文件读取失败"},
		{"unknown error", 1, "备份命令执行失败"},
		{"random text", 0, "备份操作失败"},
	}

	for _, tt := range tests {
		result := classifyError(tt.output, tt.exitCode)
		if !strings.Contains(result, tt.contains) {
			t.Errorf("classifyError(%q, %d) = %q, want to contain %q", tt.output, tt.exitCode, result, tt.contains)
		}
	}
}

func TestEnsureInstalled_NotFound(t *testing.T) {
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

func TestSnapshotInfoStruct(t *testing.T) {
	si := SnapshotInfo{
		ID:   "abc123def",
		Time: "2026-06-03T10:00:00Z",
		Tree: "deadbeef",
		Summary: map[string]interface{}{
			"total_bytes_processed": float64(1024),
		},
	}
	if si.ID != "abc123def" {
		t.Errorf("SnapshotInfo.ID = %q, want %q", si.ID, "abc123def")
	}
	if si.Summary["total_bytes_processed"] != float64(1024) {
		t.Errorf("SnapshotInfo.Summary total_bytes_processed = %v, want 1024", si.Summary["total_bytes_processed"])
	}
}
