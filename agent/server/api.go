package server

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	"github.com/chronovault/agent/config"
	"github.com/chronovault/agent/restic"
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
	mux.HandleFunc("/health", s.handleHealth)
	mux.HandleFunc("/api/v1/scan", s.handleScan)
	mux.HandleFunc("/api/v1/snapshot", s.handleSnapshot)
	mux.HandleFunc("/api/v1/restore", s.handleRestore)

	var handler http.Handler = mux
	if s.cfg.AuthToken != "" {
		handler = s.authMiddleware(mux)
	}

	addr := fmt.Sprintf(":%d", s.cfg.ListenPort)
	log.Printf("Agent API listening on %s (tls=%v)", addr, s.cfg.TLSEnabled)

	if s.cfg.TLSEnabled && s.cfg.TLSCert != "" && s.cfg.TLSKey != "" {
		return http.ListenAndServeTLS(addr, s.cfg.TLSCert, s.cfg.TLSKey, handler)
	}
	return http.ListenAndServe(addr, handler)
}

func (s *APIServer) authMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Health endpoint is always public
		if r.URL.Path == "/health" {
			next.ServeHTTP(w, r)
			return
		}

		auth := r.Header.Get("Authorization")
		if auth != "Bearer "+s.cfg.AuthToken {
			http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
			return
		}
		next.ServeHTTP(w, r)
	})
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
		TaskID      int64    `json:"taskId"`
		RepoURL     string   `json:"repoUrl"`
		Password    string   `json:"password"`
		StorageType string   `json:"storageType"`
		Endpoint    string   `json:"endpoint"`
		Paths       []string `json:"paths"`
		Excludes    []string `json:"excludes"`
		ParentID    string   `json:"parentId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	go func() {
		task := transport.TaskInfo{
			ID:          req.TaskID,
			Type:        "SNAPSHOT",
			RepoURL:     req.RepoURL,
			Password:    req.Password,
			StorageType: req.StorageType,
			Endpoint:    req.Endpoint,
			Paths:       req.Paths,
			Excludes:    req.Excludes,
			ParentID:    req.ParentID,
		}
		executeSnapshotTask(s.client, task)
	}()

	writeJSON(w, map[string]string{"status": "started"})
}

func (s *APIServer) handleRestore(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		TaskID      int64  `json:"taskId"`
		SnapshotID  string `json:"snapshotId"`
		TargetPath  string `json:"targetPath"`
		RepoURL     string `json:"repoUrl"`
		Password    string `json:"password"`
		StorageType string `json:"storageType"`
		Endpoint    string `json:"endpoint"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	go func() {
		task := transport.TaskInfo{
			ID:          req.TaskID,
			Type:        "RECOVER",
			SnapshotID:  req.SnapshotID,
			TargetPath:  req.TargetPath,
			RepoURL:     req.RepoURL,
			Password:    req.Password,
			StorageType: req.StorageType,
			Endpoint:    req.Endpoint,
		}
		executeRecoverTask(s.client, task)
	}()

	writeJSON(w, map[string]string{"status": "started"})
}

func executeSnapshotTask(client *transport.Client, task transport.TaskInfo) {
	rc := restic.NewClient()

	client.UpdateTaskProgress(task.ID, 15, "检查 restic 安装...")
	if err := rc.EnsureInstalled(); err != nil {
		client.FailTask(task.ID, "restic 安装失败: "+err.Error())
		return
	}

	repoURL := task.RepoURL
	if repoURL == "" {
		repoURL = restic.BuildRepoUrl(task.StorageType, task.Endpoint)
	}

	client.UpdateTaskProgress(task.ID, 20, "初始化仓库...")
	if err := rc.Init(repoURL, task.Password); err != nil {
		log.Printf("Restic init note: %v (may already exist)", err)
	}

	client.UpdateTaskProgress(task.ID, 30, "执行备份...")
	paths := task.Paths
	if len(paths) == 0 {
		paths = []string{"/"}
	}
	excludes := task.Excludes
	if len(excludes) == 0 {
		excludes = []string{"/proc", "/sys", "/dev", "/tmp", "/var/cache", "node_modules", ".git"}
	}

	snap, err := rc.Backup(repoURL, task.Password, paths, excludes, task.ParentID)
	if err != nil {
		client.FailTask(task.ID, "备份失败: "+err.Error())
		return
	}

	result := fmt.Sprintf(`{"snapshotId":"%s","totalBytesProcessed":%d}`, snap.SnapshotID, snap.TotalBytesProcessed)
	client.UpdateTaskProgress(task.ID, 100, "快照完成")
	client.CompleteTask(task.ID, result)
}

func executeRecoverTask(client *transport.Client, task transport.TaskInfo) {
	rc := restic.NewClient()

	client.UpdateTaskProgress(task.ID, 15, "检查 restic 安装...")
	if err := rc.EnsureInstalled(); err != nil {
		client.FailTask(task.ID, "restic 安装失败: "+err.Error())
		return
	}

	repoURL := task.RepoURL
	if repoURL == "" {
		repoURL = restic.BuildRepoUrl(task.StorageType, task.Endpoint)
	}

	client.UpdateTaskProgress(task.ID, 25, "验证仓库完整性...")
	if err := rc.Check(repoURL, task.Password); err != nil {
		log.Printf("Restic check warning: %v", err)
	}

	client.UpdateTaskProgress(task.ID, 50, "执行恢复...")
	targetPath := task.TargetPath
	if targetPath == "" {
		targetPath = "/"
	}
	if err := rc.Restore(repoURL, task.Password, task.SnapshotID, targetPath); err != nil {
		client.FailTask(task.ID, "恢复失败: "+err.Error())
		return
	}

	client.UpdateTaskProgress(task.ID, 100, "恢复完成")
	client.CompleteTask(task.ID, `{"status":"completed"}`)
}

func writeJSON(w http.ResponseWriter, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(data)
}
