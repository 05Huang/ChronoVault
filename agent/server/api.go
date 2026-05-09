package server

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	"github.com/chronovault/agent/config"
	"github.com/chronovault/agent/scanner"
	"github.com/chronovault/agent/transport"
)

type APIServer struct {
	cfg    *config.Config
	client *transport.Client
}

func NewAPIServer(cfg *config.Config, client *transport.Client) *APIServer {
	return &APIServer{
		cfg:    cfg,
		client: client,
	}
}

func (s *APIServer) Start() error {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/health", s.handleHealth)
	mux.HandleFunc("/api/v1/scan", s.handleScan)
	mux.HandleFunc("/api/v1/snapshot", s.handleSnapshot)
	mux.HandleFunc("/api/v1/restore", s.handleRestore)

	addr := fmt.Sprintf(":%d", s.cfg.ListenPort)
	log.Printf("Agent API listening on %s", addr)
	return http.ListenAndServe(addr, mux)
}

func (s *APIServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, map[string]interface{}{
		"status":    "healthy",
		"agentId":   s.cfg.AgentID,
		"serverId":  s.cfg.ServerID,
	})
}

func (s *APIServer) handleScan(w http.ResponseWriter, r *http.Request) {
	result := scanner.ScanAll()
	writeJSON(w, result)
}

func (s *APIServer) handleSnapshot(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		TaskID int64 `json:"taskId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	go func() {
		s.client.UpdateTaskProgress(req.TaskID, 10, "开始快照...")
		// Snapshot logic would go here
		s.client.UpdateTaskProgress(req.TaskID, 100, "快照完成")
		s.client.CompleteTask(req.TaskID, `{"status": "completed"}`)
	}()

	writeJSON(w, map[string]string{"status": "started"})
}

func (s *APIServer) handleRestore(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		TaskID     int64  `json:"taskId"`
		SnapshotID string `json:"snapshotId"`
		TargetPath string `json:"targetPath"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	go func() {
		s.client.UpdateTaskProgress(req.TaskID, 10, "开始恢复...")
		// Restore logic would go here
		s.client.UpdateTaskProgress(req.TaskID, 100, "恢复完成")
		s.client.CompleteTask(req.TaskID, `{"status": "completed"}`)
	}()

	writeJSON(w, map[string]string{"status": "started"})
}

func writeJSON(w http.ResponseWriter, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(data)
}
