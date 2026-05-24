package scanner

import "testing"

func TestExtractVersion(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"mysql  Ver 8.0.36 for Linux on x86_64 (MySQL Community Server - GPL)", "8.0.36"},
		{"psql (PostgreSQL) 16.2 (Ubuntu 16.2-1.pgdg22.04+1)", "16.2"},
		{"Redis server v=7.2.4 sha=00000000:0 malloc=jemalloc-5.3.0", "7.2.4"},
		{"short", "short"},
		{"", ""},
		{"no version here", "here"},
		{"version 1.2.3.4 extra", "1.2.3.4"},
	}

	for _, tt := range tests {
		result := extractVersion(tt.input)
		if result != tt.expected {
			t.Errorf("extractVersion(%q) = %q, want %q", tt.input, result, tt.expected)
		}
	}
}

func TestDatabaseInfoStruct(t *testing.T) {
	db := DatabaseInfo{
		Type:    "MySQL",
		Version: "8.0.36",
		Port:    3306,
		Status:  "running",
	}

	if db.Type != "MySQL" {
		t.Errorf("DatabaseInfo.Type = %q, want %q", db.Type, "MySQL")
	}
	if db.Port != 3306 {
		t.Errorf("DatabaseInfo.Port = %d, want %d", db.Port, 3306)
	}
}

func TestScanDatabases_NoDatabases(t *testing.T) {
	// In a test environment, no databases should be running on standard ports
	// unless the test machine has them. This just verifies the function doesn't panic.
	dbs := ScanDatabases()
	if dbs == nil {
		t.Error("ScanDatabases() returned nil, expected empty slice")
	}
	// We don't assert length because the test machine might have databases running
}
