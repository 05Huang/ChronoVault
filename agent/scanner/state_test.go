package scanner

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestCollectStateSnapshot_NotNil(t *testing.T) {
	// CollectStateSnapshot should always return a non-nil result,
	// even if some collectors fail (e.g., no Docker, no systemctl).
	state, err := CollectStateSnapshot()
	if err != nil {
		t.Fatalf("CollectStateSnapshot() returned error: %v", err)
	}
	if state == nil {
		t.Fatal("CollectStateSnapshot() returned nil")
	}
	if state.CollectedAt == "" {
		t.Error("CollectedAt is empty")
	}
	if state.AgentVersion == "" {
		t.Error("AgentVersion is empty")
	}
}

func TestCollectStateSnapshot_JSONMarshal(t *testing.T) {
	state, err := CollectStateSnapshot()
	if err != nil {
		t.Fatalf("CollectStateSnapshot() error: %v", err)
	}

	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		t.Fatalf("json.MarshalIndent() error: %v", err)
	}

	// Verify it's valid JSON
	var parsed StateSnapshot
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("json.Unmarshal() error: %v", err)
	}

	if parsed.CollectedAt != state.CollectedAt {
		t.Errorf("Roundtrip CollectedAt mismatch: got %q, want %q", parsed.CollectedAt, state.CollectedAt)
	}
}

func TestCollectOSInfo(t *testing.T) {
	info := collectOSInfo()

	// On any Linux system, kernel should be set
	if info.Kernel == "" {
		t.Error("collectOSInfo() kernel is empty")
	}
	// Arch should be set (from runtime.GOARCH)
	if info.Arch == "" {
		t.Error("collectOSInfo() arch is empty")
	}
	// On Linux, Name should be set from /etc/os-release or runtime.GOOS
	if info.Name == "" {
		t.Error("collectOSInfo() name is empty")
	}
}

func TestCollectPackages_DetectsPackageManager(t *testing.T) {
	pkgs := collectPackages()

	// Should return a slice (possibly empty if no package manager found)
	if pkgs == nil {
		t.Error("collectPackages() returned nil, want empty slice")
	}

	// If packages exist, they should have valid fields
	for _, pkg := range pkgs {
		if pkg.Name == "" {
			t.Error("Package with empty name")
		}
		if pkg.Version == "" {
			t.Errorf("Package %q has empty version", pkg.Name)
		}
		if pkg.Manager == "" {
			t.Errorf("Package %q has empty manager", pkg.Name)
		}
	}

	t.Logf("Found %d packages (manager=%s)", len(pkgs), firstPackageManager(pkgs))
}

func TestCollectServices(t *testing.T) {
	services := collectServices()

	if services == nil {
		t.Error("collectServices() returned nil, want empty slice")
	}

	// Validate structure of returned services
	for _, svc := range services {
		if svc.Name == "" {
			t.Error("Service with empty name")
		}
		if svc.Status == "" {
			t.Errorf("Service %q has empty status", svc.Name)
		}
		// Active services should have PID > 0
		if svc.Status == "active" && svc.PID == 0 {
			// Some services may legitimately have PID 0 (e.g., socket-activated)
			// Just log a warning, don't fail
			t.Logf("Warning: active service %q has PID 0", svc.Name)
		}
	}

	t.Logf("Found %d services", len(services))
}

func TestCollectPorts(t *testing.T) {
	ports := collectPorts()

	if ports == nil {
		t.Error("collectPorts() returned nil, want empty slice")
	}

	for _, port := range ports {
		if port.Port <= 0 || port.Port > 65535 {
			t.Errorf("Invalid port number: %d", port.Port)
		}
		if port.Protocol != "tcp" && port.Protocol != "udp" {
			t.Errorf("Invalid protocol: %q", port.Protocol)
		}
	}

	t.Logf("Found %d listening ports", len(ports))
}

func TestCollectDockerState(t *testing.T) {
	state := collectDockerState()

	// Docker state should always return a valid struct
	if state.Containers == nil {
		t.Error("Docker containers is nil")
	}
	if state.ComposeFiles == nil {
		t.Error("Docker compose_files is nil")
	}

	// Validate container structure
	for _, c := range state.Containers {
		if c.Name == "" {
			t.Error("Container with empty name")
		}
		if c.Image == "" {
			t.Errorf("Container %q has empty image", c.Name)
		}
	}

	t.Logf("Docker available=%v, containers=%d, compose_files=%d",
		state.Available, len(state.Containers), len(state.ComposeFiles))
}

func TestCollectConfigHashes(t *testing.T) {
	configs := collectConfigHashes()

	if configs == nil {
		t.Error("collectConfigHashes() returned nil, want empty slice")
	}

	for _, cfg := range configs {
		if cfg.Path == "" {
			t.Error("Config with empty path")
		}
		if cfg.SHA256 == "" {
			t.Errorf("Config %q has empty SHA256", cfg.Path)
		}
		if len(cfg.SHA256) != 64 {
			t.Errorf("Config %q SHA256 length = %d, want 64", cfg.Path, len(cfg.SHA256))
		}
		if cfg.Size < 0 {
			t.Errorf("Config %q has negative size: %d", cfg.Path, cfg.Size)
		}
	}

	t.Logf("Found %d tracked config files", len(configs))
}

func TestCollectCrontab(t *testing.T) {
	entries := collectCrontab()

	if entries == nil {
		t.Error("collectCrontab() returned nil, want empty slice")
	}

	for _, entry := range entries {
		if entry.User == "" {
			t.Error("Crontab entry with empty user")
		}
		if entry.Schedule == "" {
			t.Error("Crontab entry with empty schedule")
		}
		if entry.Command == "" {
			t.Error("Crontab entry with empty command")
		}
	}

	t.Logf("Found %d crontab entries", len(entries))
}

func TestHashFile(t *testing.T) {
	// Create a temp file with known content
	tmpDir := t.TempDir()
	tmpFile := filepath.Join(tmpDir, "test.txt")
	content := "hello world"
	if err := os.WriteFile(tmpFile, []byte(content), 0644); err != nil {
		t.Fatalf("Failed to create temp file: %v", err)
	}

	hash, size := hashFile(tmpFile)
	if hash == "" {
		t.Error("hashFile() returned empty hash")
	}
	if len(hash) != 64 {
		t.Errorf("hashFile() hash length = %d, want 64 (SHA-256)", len(hash))
	}
	if size != int64(len(content)) {
		t.Errorf("hashFile() size = %d, want %d", size, len(content))
	}

	// Same content should produce same hash
	hash2, _ := hashFile(tmpFile)
	if hash != hash2 {
		t.Errorf("hashFile() inconsistent: %q vs %q", hash, hash2)
	}
}

func TestHashFile_NonExistent(t *testing.T) {
	hash, size := hashFile("/nonexistent/path/file.txt")
	if hash != "" {
		t.Errorf("hashFile(nonexistent) hash = %q, want empty", hash)
	}
	if size != 0 {
		t.Errorf("hashFile(nonexistent) size = %d, want 0", size)
	}
}

func TestGetStateFilePath(t *testing.T) {
	path := GetStateFilePath()
	if path == "" {
		t.Error("GetStateFilePath() returned empty string")
	}
	if !strings.HasSuffix(path, "state.json") {
		t.Errorf("GetStateFilePath() = %q, should end with state.json", path)
	}
	if !strings.Contains(path, ".chronovault") {
		t.Errorf("GetStateFilePath() = %q, should contain .chronovault", path)
	}
}

func TestSaveStateSnapshot(t *testing.T) {
	// Override home dir for test
	home, err := os.UserHomeDir()
	if err != nil {
		t.Skip("Cannot determine home dir")
	}
	testDir := filepath.Join(home, ".chronovault_test")
	os.MkdirAll(testDir, 0755)
	defer os.RemoveAll(testDir)

	// Temporarily override the path function
	origPath := GetStateFilePath
	_ = origPath // We can't easily override, so just test with default path

	state := &StateSnapshot{
		CollectedAt:  "2026-06-03T10:00:00Z",
		AgentVersion: "0.1.0-test",
		OS:           OSInfo{Name: "TestOS", Version: "1.0", Kernel: "5.0.0", Arch: "x86_64"},
		Packages:     []PackageInfo{{Name: "test-pkg", Version: "1.0", Manager: "apt"}},
	}

	err = SaveStateSnapshot(state)
	if err != nil {
		t.Fatalf("SaveStateSnapshot() error: %v", err)
	}

	// Verify file was written
	path := GetStateFilePath()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read saved state file: %v", err)
	}

	var parsed StateSnapshot
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("Failed to parse saved state file: %v", err)
	}

	if parsed.AgentVersion != "0.1.0-test" {
		t.Errorf("Saved state AgentVersion = %q, want %q", parsed.AgentVersion, "0.1.0-test")
	}

	// Cleanup
	os.Remove(path)
}

func TestCommandExists(t *testing.T) {
	// "ls" should exist on any Unix system
	if !commandExists("ls") {
		t.Error("commandExists('ls') = false, want true")
	}

	// A non-existent command
	if commandExists("definitely-not-a-real-command-12345") {
		t.Error("commandExists('definitely-not-a-real-command-12345') = true, want false")
	}
}

func TestRunCommandSafe(t *testing.T) {
	// Should return output
	out := runCommandSafe("echo hello")
	if out != "hello" {
		t.Errorf("runCommandSafe('echo hello') = %q, want %q", out, "hello")
	}

	// Should return empty string on failure (no panic)
	out = runCommandSafe("false")
	if out != "" {
		t.Errorf("runCommandSafe('false') = %q, want empty", out)
	}
}

// helper
func firstPackageManager(pkgs []PackageInfo) string {
	if len(pkgs) > 0 {
		return pkgs[0].Manager
	}
	return "none"
}

func TestRunCustomCollectors_ValidJSON(t *testing.T) {
	collectors := []PluginConfig{
		{Name: "test_collector", Command: `echo '{"key":"value","count":42}'`, Timeout: 5},
	}
	results := RunCustomCollectors(collectors)

	if results == nil {
		t.Fatal("RunCustomCollectors returned nil")
	}
	if len(results) != 1 {
		t.Fatalf("Expected 1 result, got %d", len(results))
	}

	data, ok := results["test_collector"].(map[string]interface{})
	if !ok {
		t.Fatalf("Expected map, got %T", results["test_collector"])
	}
	if data["key"] != "value" {
		t.Errorf("Expected key=value, got %v", data["key"])
	}
	if data["count"] != float64(42) {
		t.Errorf("Expected count=42, got %v", data["count"])
	}
}

func TestRunCustomCollectors_InvalidJSON(t *testing.T) {
	collectors := []PluginConfig{
		{Name: "bad_collector", Command: `echo 'not json'`, Timeout: 5},
	}
	results := RunCustomCollectors(collectors)

	if results == nil {
		t.Fatal("RunCustomCollectors returned nil")
	}

	data, ok := results["bad_collector"].(map[string]interface{})
	if !ok {
		t.Fatalf("Expected map, got %T", results["bad_collector"])
	}
	if _, hasErr := data["error"]; !hasErr {
		t.Error("Expected error field for invalid JSON output")
	}
}

func TestRunCustomCollectors_Timeout(t *testing.T) {
	collectors := []PluginConfig{
		{Name: "slow_collector", Command: `sleep 5`, Timeout: 1},
	}
	results := RunCustomCollectors(collectors)

	data, ok := results["slow_collector"].(map[string]interface{})
	if !ok {
		t.Fatalf("Expected map, got %T", results["slow_collector"])
	}
	if _, hasErr := data["error"]; !hasErr {
		t.Error("Expected error field for timed-out command")
	}
}

func TestRunCustomCollectors_EmptyList(t *testing.T) {
	results := RunCustomCollectors(nil)
	if results != nil {
		t.Errorf("Expected nil for empty collectors, got %v", results)
	}
}

func TestRunCustomCollectors_JSONArray(t *testing.T) {
	collectors := []PluginConfig{
		{Name: "array_collector", Command: `echo '[{"name":"a"},{"name":"b"}]'`, Timeout: 5},
	}
	results := RunCustomCollectors(collectors)

	data, ok := results["array_collector"].(map[string]interface{})
	if !ok {
		t.Fatalf("Expected map, got %T", results["array_collector"])
	}
	items, ok := data["items"]
	if !ok {
		t.Fatal("Expected 'items' key for array output")
	}
	if items == nil {
		t.Error("items should not be nil")
	}
}

func TestSetCustomCollectors(t *testing.T) {
	// Save original
	orig := customCollectors
	defer func() { customCollectors = orig }()

	SetCustomCollectors(nil)
	if len(customCollectors) != 0 {
		t.Error("Expected empty collectors after SetCustomCollectors(nil)")
	}

	SetCustomCollectors([]PluginConfig{{Name: "test", Command: "echo ok"}})
	if len(customCollectors) != 1 {
		t.Errorf("Expected 1 collector, got %d", len(customCollectors))
	}
}

func TestSetCustomConfigPaths(t *testing.T) {
	orig := customConfigPaths
	defer func() { customConfigPaths = orig }()

	SetCustomConfigPaths(nil)
	if len(customConfigPaths) != 0 {
		t.Error("Expected empty paths after SetCustomConfigPaths(nil)")
	}

	SetCustomConfigPaths([]string{"/tmp/test.conf", "/etc/custom/app.yml"})
	if len(customConfigPaths) != 2 {
		t.Errorf("Expected 2 paths, got %d", len(customConfigPaths))
	}
}