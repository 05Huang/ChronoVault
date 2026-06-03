package transport

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/chronovault/agent/scanner"
)

func TestNewClient(t *testing.T) {
	c := NewClient("http://localhost:8080", "test-key", "agent-1")
	if c == nil {
		t.Fatal("NewClient() returned nil")
	}
	if c.baseURL != "http://localhost:8080" {
		t.Errorf("baseURL = %q, want %q", c.baseURL, "http://localhost:8080")
	}
	if c.apiKey != "test-key" {
		t.Errorf("apiKey = %q, want %q", c.apiKey, "test-key")
	}
	if c.agentID != "agent-1" {
		t.Errorf("agentID = %q, want %q", c.agentID, "agent-1")
	}
	if c.httpClient == nil {
		t.Error("httpClient is nil")
	}
}

func TestHeartbeat(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/agent/heartbeat" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != "POST" {
			t.Errorf("unexpected method: %s", r.Method)
		}
		if r.Header.Get("Authorization") != "Bearer test-key" {
			t.Errorf("unexpected auth header: %s", r.Header.Get("Authorization"))
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "ok",
			"data":    nil,
		})
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	err := c.Heartbeat()
	if err != nil {
		t.Errorf("Heartbeat() error: %v", err)
	}
}

func TestGetPendingTasks(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/agent/tasks/pending" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}

		tasks := []TaskInfo{
			{ID: 1, Type: "SNAPSHOT", Status: "PENDING"},
			{ID: 2, Type: "RECOVER", Status: "PENDING"},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "ok",
			"data":    tasks,
		})
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	tasks, err := c.GetPendingTasks()
	if err != nil {
		t.Fatalf("GetPendingTasks() error: %v", err)
	}
	if len(tasks) != 2 {
		t.Errorf("GetPendingTasks() returned %d tasks, want 2", len(tasks))
	}
	if tasks[0].Type != "SNAPSHOT" {
		t.Errorf("tasks[0].Type = %q, want %q", tasks[0].Type, "SNAPSHOT")
	}
}

func TestRegister(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/agent/register" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}

		// Verify request body contains expected fields
		var body map[string]string
		json.NewDecoder(r.Body).Decode(&body)
		if body["agentId"] != "agent-1" {
			t.Errorf("request agentId = %q, want %q", body["agentId"], "agent-1")
		}
		if body["name"] != "test-host" {
			t.Errorf("request name = %q, want %q", body["name"], "test-host")
		}

		resp := RegisterResponse{
			ServerID: 42,
			AgentID:  "agent-1",
			Status:   "registered",
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "ok",
			"data":    resp,
		})
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	scanResult := scanner.ScanResult{
		System: scanner.SystemInfo{
			Hostname: "test-host",
			OS:       "linux",
		},
	}

	resp, err := c.Register(scanResult)
	if err != nil {
		t.Fatalf("Register() error: %v", err)
	}
	if resp.ServerID != 42 {
		t.Errorf("Register().ServerID = %d, want 42", resp.ServerID)
	}
	if resp.Status != "registered" {
		t.Errorf("Register().Status = %q, want %q", resp.Status, "registered")
	}
}

func TestUpdateTaskProgress(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/agent/tasks/5/progress" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}

		var body map[string]interface{}
		json.NewDecoder(r.Body).Decode(&body)
		if body["progress"].(float64) != 50 {
			t.Errorf("progress = %v, want 50", body["progress"])
		}
		if body["message"] != "halfway done" {
			t.Errorf("message = %v, want 'halfway done'", body["message"])
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "ok",
			"data":    nil,
		})
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	err := c.UpdateTaskProgress(5, 50, "halfway done")
	if err != nil {
		t.Errorf("UpdateTaskProgress() error: %v", err)
	}
}

func TestCompleteTask(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/agent/tasks/3/complete" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "ok",
			"data":    nil,
		})
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	err := c.CompleteTask(3, "snapshot-abc123")
	if err != nil {
		t.Errorf("CompleteTask() error: %v", err)
	}
}

func TestFailTask(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/agent/tasks/3/fail" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "ok",
			"data":    nil,
		})
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	err := c.FailTask(3, "disk full")
	if err != nil {
		t.Errorf("FailTask() error: %v", err)
	}
}

func TestAPIError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	err := c.Heartbeat()
	if err == nil {
		t.Error("Heartbeat() should return error on 500")
	}
}

func TestTaskInfoFields(t *testing.T) {
	task := TaskInfo{
		ID:         1,
		Type:       "SNAPSHOT",
		Status:     "PENDING",
		Progress:   0,
		RepoURL:    "/data/repo",
		Password:   "secret",
		StorageType: "LOCAL",
		Paths:      []string{"/home", "/etc"},
		Excludes:   []string{"*.log"},
		ParentID:   "abc123",
	}

	if task.RepoURL != "/data/repo" {
		t.Errorf("RepoURL = %q, want %q", task.RepoURL, "/data/repo")
	}
	if len(task.Paths) != 2 {
		t.Errorf("Paths length = %d, want 2", len(task.Paths))
	}
	if len(task.Excludes) != 1 {
		t.Errorf("Excludes length = %d, want 1", len(task.Excludes))
	}
}

func TestReportStateSnapshot(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/agent/state/report" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != "POST" {
			t.Errorf("unexpected method: %s", r.Method)
		}

		var body map[string]interface{}
		json.NewDecoder(r.Body).Decode(&body)
		if body["agentId"] != "agent-1" {
			t.Errorf("agentId = %v, want agent-1", body["agentId"])
		}
		if body["snapshotId"] != "snap-123" {
			t.Errorf("snapshotId = %v, want snap-123", body["snapshotId"])
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "ok",
			"data":    nil,
		})
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	stateData := json.RawMessage(`{"collected_at":"2026-06-03T10:00:00Z","packages":[]}`)
	err := c.ReportStateSnapshot("snap-123", stateData)
	if err != nil {
		t.Errorf("ReportStateSnapshot() error: %v", err)
	}
}

func TestAPIAuthError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte("unauthorized"))
	}))
	defer server.Close()

	c := NewClient(server.URL, "wrong-key", "agent-1")
	err := c.Heartbeat()
	if err == nil {
		t.Error("Heartbeat() should return error on 401")
	}
}

func TestAPIRetryOn5xx(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts < 3 {
			w.WriteHeader(http.StatusInternalServerError)
			w.Write([]byte("server error"))
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "ok",
			"data":    nil,
		})
	}))
	defer server.Close()

	c := NewClient(server.URL, "test-key", "agent-1")
	err := c.postWithRetry("/api/agent/heartbeat", map[string]string{"agentId": "agent-1"}, nil)
	if err != nil {
		t.Errorf("postWithRetry() should succeed after retries, got error: %v", err)
	}
	if attempts != 3 {
		t.Errorf("Expected 3 attempts, got %d", attempts)
	}
}
