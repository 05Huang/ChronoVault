package cmd

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/chronovault/agent/config"
	"github.com/chronovault/agent/restic"
	"github.com/chronovault/agent/scanner"
	"github.com/chronovault/agent/server"
	"github.com/chronovault/agent/transport"
)

// Snapshot handles snapshot management commands
func Snapshot(args []string) {
	if len(args) < 1 {
		fmt.Println("Usage: chronovault-agent snapshot <list|create|rollback|diff>")
		return
	}

	switch args[0] {
	case "list":
		snapshotList()
	case "create":
		snapshotCreate(args[1:])
	case "rollback":
		snapshotRollback(args[1:])
	case "diff":
		snapshotDiff(args[1:])
	default:
		fmt.Printf("Unknown snapshot command: %s\n", args[0])
		fmt.Println("Usage: chronovault-agent snapshot <list|create|rollback|diff>")
	}
}

func snapshotList() {
	if cfg.ServerURL == "" || cfg.APIKey == "" {
		fmt.Println("Error: Server URL and API key must be configured")
		fmt.Println("Please set server_url and api_key in /etc/chronovault/agent.yml")
		return
	}

	client := transport.NewClient(cfg.ServerURL, cfg.APIKey, cfg.AgentID)
	snapshots, err := client.ListSnapshots()
	if err != nil {
		fmt.Printf("Error listing snapshots: %v\n", err)
		return
	}

	if len(snapshots) == 0 {
		fmt.Println("No snapshots found")
		return
	}

	fmt.Printf("%-8s %-20s %-12s %-20s\n", "ID", "Title", "Status", "Created")
	fmt.Printf("%-8s %-20s %-12s %-20s\n", "--------", "--------------------", "------------", "--------------------")
	for _, s := range snapshots {
		title := s["title"].(string)
		if len(title) > 18 {
			title = title[:18] + ".."
		}
		createdAt := ""
		if t, ok := s["createdAt"].(string); ok && len(t) > 19 {
			createdAt = t[:19]
		}
		fmt.Printf("%-8v %-20s %-12v %-20s\n", s["id"], title, s["status"], createdAt)
	}
}

func snapshotCreate(args []string) {
	serverURL := ""
	for i := 0; i < len(args)-1; i++ {
		if args[i] == "--server-url" {
			serverURL = args[i+1]
		}
	}

	if serverURL == "" {
		serverURL = cfg.ServerURL
	}

	if serverURL == "" || cfg.APIKey == "" {
		fmt.Println("Error: Server URL and API key must be configured")
		return
	}

	fmt.Println("Creating snapshot...")

	// Collect state
	stateCollector := scanner.NewStateCollector()
	state, err := stateCollector.Collect()
	if err != nil {
		fmt.Printf("Warning: State collection failed: %v\n", err)
		state = "{}"
	}

	// Create restic backup
	rc := restic.NewClient()
	if err := rc.EnsureInstalled(); err != nil {
		fmt.Printf("Error: Restic not installed: %v\n", err)
		return
	}

	hash, err := rc.Backup("/", cfg.ResticPassword)
	if err != nil {
		fmt.Printf("Error creating backup: %v\n", err)
		return
	}

	// Report to server
	client := transport.NewClient(cfg.ServerURL, cfg.APIKey, cfg.AgentID)
	result, err := client.ReportSnapshot(hash, state)
	if err != nil {
		fmt.Printf("Error reporting snapshot: %v\n", err)
		return
	}

	fmt.Printf("Snapshot created successfully!\n")
	fmt.Printf("  Hash: %s\n", hash)
	fmt.Printf("  Server response: %v\n", result)
}

func snapshotRollback(args []string) {
	snapshotID := ""
	for i := 0; i < len(args)-1; i++ {
		if args[i] == "--id" {
			snapshotID = args[i+1]
		}
	}

	if snapshotID == "" {
		fmt.Println("Error: Snapshot ID is required")
		fmt.Println("Usage: chronovault-agent snapshot rollback --id <snapshot-id>")
		return
	}

	fmt.Printf("Rolling back to snapshot %s...\n", snapshotID)

	rc := restic.NewClient()
	if err := rc.EnsureInstalled(); err != nil {
		fmt.Printf("Error: Restic not installed: %v\n", err)
		return
	}

	err := rc.Restore(snapshotID, "/", cfg.ResticPassword)
	if err != nil {
		fmt.Printf("Error restoring snapshot: %v\n", err)
		return
	}

	fmt.Printf("Snapshot %s restored successfully!\n", snapshotID)
}

func snapshotDiff(args []string) {
	id1 := ""
	id2 := ""
	for i := 0; i < len(args)-1; i++ {
		if args[i] == "--id1" {
			id1 = args[i+1]
		}
		if args[i] == "--id2" {
			id2 = args[i+1]
		}
	}

	if id1 == "" || id2 == "" {
		fmt.Println("Error: Both --id1 and --id2 are required")
		fmt.Println("Usage: chronovault-agent snapshot diff --id1 <id1> --id2 <id2>")
		return
	}

	fmt.Printf("Comparing snapshots %s and %s...\n", id1, id2)

	// For now, just show basic diff info
	// In a full implementation, this would fetch state.json from both snapshots
	// and compare them using the diff engine
	fmt.Printf("Snapshot A: %s\n", id1)
	fmt.Printf("Snapshot B: %s\n", id2)
	fmt.Println("Note: Full diff comparison requires backend API access")
}

var (
	cfg          *config.Config
	taskWg       sync.WaitGroup
	shutdownCtx  context.Context
	shutdownFunc context.CancelFunc
)

func init() {
	var err error
	cfg, err = config.LoadConfig("/etc/chronovault/agent.yml")
	if err != nil {
		log.Printf("Warning: failed to load config: %v", err)
		cfg = config.DefaultConfig()
	}
}

func Run() {
	log.Println("Starting ChronoVault Agent...")

	// Validate configuration
	if err := cfg.Validate(); err != nil {
		log.Fatalf("Configuration error: %v\nPlease check your config file at /etc/chronovault/agent.yml", err)
	}

	// Initialize shutdown context
	shutdownCtx, shutdownFunc = context.WithCancel(context.Background())

	// Startup validation: check restic availability
	log.Println("Checking restic installation...")
	rc := restic.NewClient()
	if err := rc.EnsureInstalled(); err != nil {
		log.Fatalf("Restic check failed: %v\nPlease install restic manually: https://restic.net/", err)
	}
	log.Println("Restic is available")

	// Check for agent updates (non-blocking, best-effort)
	if cfg.ServerURL != "" && cfg.APIKey != "" {
		tempClient := transport.NewClient(cfg.ServerURL, cfg.APIKey, cfg.AgentID)
		if latest, url, needsUpdate, err := tempClient.CheckForUpdate(); err == nil && needsUpdate {
			log.Printf("=========================================")
			log.Printf("UPDATE AVAILABLE: %s (current: 0.1.0)", latest)
			log.Printf("Download: %s", url)
			log.Printf("=========================================")
		}
	}

	// Startup validation: check server reachability (if configured)
	client := transport.NewClient(cfg.ServerURL, cfg.APIKey, cfg.AgentID)
	if cfg.ServerURL != "" && cfg.APIKey != "" {
		log.Printf("Checking server reachability at %s...", cfg.ServerURL)
		if err := client.CheckServerReachable(); err != nil {
			log.Printf("Warning: server is not reachable: %v", err)
			log.Printf("Agent will continue running but cannot communicate with the server.")
			log.Printf("Please verify: 1) Server is running 2) API key is correct 3) Network connectivity")
		} else {
			log.Println("Server is reachable")
		}
	}

	// Initialize custom collectors from config
	if len(cfg.CustomCollectors) > 0 {
		var collectors []scanner.PluginConfig
		for _, c := range cfg.CustomCollectors {
			collectors = append(collectors, scanner.PluginConfig{
				Name:    c.Name,
				Command: c.Command,
				Timeout: c.Timeout,
			})
			log.Printf("Registered custom collector: %s (command: %s)", c.Name, c.Command)
		}
		scanner.SetCustomCollectors(collectors)
		log.Printf("Loaded %d custom collectors", len(collectors))
	}

	// Initialize custom config paths from config
	if len(cfg.CustomConfigPaths) > 0 {
		scanner.SetCustomConfigPaths(cfg.CustomConfigPaths)
		log.Printf("Tracking %d custom config paths", len(cfg.CustomConfigPaths))
	}

	// Initial scan
	log.Println("Performing initial environment scan...")
	result := scanner.ScanAll()
	log.Printf("Scan complete: %d containers, %d databases found",
		len(result.Docker.Containers), len(result.Databases))

	// Register with server
	if cfg.ServerURL != "" && cfg.APIKey != "" {
		log.Println("Registering with ChronoVault server...")
		resp, err := client.Register(result)
		if err != nil {
			log.Printf("Registration failed: %v", err)
		} else {
			cfg.ServerID = resp.ServerID
			log.Printf("Registered with server ID: %d", resp.ServerID)
		}
	}

	// Start local API server
	apiServer := server.NewAPIServer(cfg, client)
	go func() {
		log.Printf("Starting local API server on port %d", cfg.ListenPort)
		if err := apiServer.Start(); err != nil {
			log.Fatalf("Failed to start API server: %v", err)
		}
	}()

	// Start heartbeat loop
	go heartbeatLoop(client)

	// Start WebSocket connection for real-time task delivery (with HTTP polling fallback)
	wsClient := transport.NewWSClient(cfg.ServerURL, cfg.AgentID, cfg.APIKey, func(task transport.TaskInfo) {
		log.Printf("Executing task %d: %s (via WebSocket)", task.ID, task.Type)
		taskWg.Add(1)
		go func() {
			defer taskWg.Done()
			executeTask(client, task)
		}()
	})
	if cfg.ServerURL != "" && cfg.APIKey != "" {
		wsClient.Start()
		log.Println("WebSocket connection started for real-time task delivery")
	}

	// Start task polling loop (fallback when WebSocket is unavailable)
	go taskPollingLoop(client, wsClient)

	// Wait for signal
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	sig := <-sigCh
	log.Printf("Received signal %v, shutting down gracefully...", sig)

	// Stop WebSocket connection
	if wsClient != nil {
		wsClient.Stop()
	}

	// Cancel context to stop new tasks
	shutdownFunc()

	// Wait for in-progress tasks with timeout
	done := make(chan struct{})
	go func() {
		taskWg.Wait()
		close(done)
	}()

	select {
	case <-done:
		log.Println("All in-progress tasks completed. Agent stopped.")
	case <-time.After(30 * time.Second):
		log.Println("Shutdown timeout reached. Forcing exit with tasks still running.")
	}
}

func Register(args []string) {
	var serverURL, apiKey, serverID string
	for i := 0; i < len(args)-1; i++ {
		switch args[i] {
		case "--server-url":
			serverURL = args[i+1]
		case "--api-key":
			apiKey = args[i+1]
		case "--server-id":
			serverID = args[i+1]
		}
	}

	if serverURL == "" || apiKey == "" {
		fmt.Println("Error: --server-url and --api-key are required")
		os.Exit(1)
	}

	cfg.ServerURL = serverURL
	cfg.APIKey = apiKey

	client := transport.NewClient(serverURL, apiKey, "")
	if serverID != "" {
		client.SetServerID(serverID)
	}
	result := scanner.ScanAll()

	resp, err := client.Register(result)
	if err != nil {
		log.Fatalf("Registration failed: %v", err)
	}

	cfg.AgentID = resp.AgentID
	cfg.ServerID = resp.ServerID

	if err := config.SaveConfig("/etc/chronovault/agent.yml", cfg); err != nil {
		log.Printf("Warning: failed to save config: %v", err)
	}

	fmt.Printf("Successfully registered! Server ID: %d\n", resp.ServerID)
}

func Scan() {
	result := scanner.ScanAll()
	fmt.Printf("Docker Containers: %d\n", len(result.Docker.Containers))
	for _, c := range result.Docker.Containers {
		fmt.Printf("  - %s (%s) [%s]\n", c.Name, c.Image, c.State)
	}
	fmt.Printf("Databases: %d\n", len(result.Databases))
	for _, db := range result.Databases {
		fmt.Printf("  - %s %s on port %d\n", db.Type, db.Version, db.Port)
	}
	fmt.Printf("Web Servers: %d\n", len(result.WebServers))
	for _, ws := range result.WebServers {
		fmt.Printf("  - %s %s\n", ws.Type, ws.Version)
	}
}

func heartbeatLoop(client *transport.Client) {
	interval := time.Duration(cfg.HeartbeatInterval) * time.Second
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-shutdownCtx.Done():
			log.Println("Heartbeat loop stopped (shutdown)")
			return
		case <-ticker.C:
			if err := client.Heartbeat(); err != nil {
				log.Printf("Heartbeat failed: %v", err)
			}
		}
	}
}

func taskPollingLoop(client *transport.Client, wsClient *transport.WSClient) {
	interval := 10 * time.Second
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-shutdownCtx.Done():
			log.Println("Task polling stopped (shutdown)")
			return
		case <-ticker.C:
			// Skip HTTP polling when WebSocket is connected (real-time delivery)
			if wsClient != nil && wsClient.IsConnected() {
				continue
			}

			tasks, err := client.GetPendingTasks()
			if err != nil {
				continue
			}
			for _, task := range tasks {
				// Check shutdown before starting new tasks
				select {
				case <-shutdownCtx.Done():
					log.Println("Skipping new tasks during shutdown")
					return
				default:
				}
				log.Printf("Executing task %d: %s (via HTTP polling)", task.ID, task.Type)
				taskWg.Add(1)
				go func(t transport.TaskInfo) {
					defer taskWg.Done()
					executeTask(client, t)
				}(task)
			}
		}
	}
}

func executeTask(client *transport.Client, task transport.TaskInfo) {
	// Panic recovery to prevent agent crash from goroutine panics
	defer func() {
		if r := recover(); r != nil {
			log.Printf("PANIC in task %d: %v", task.ID, r)
			client.FailTask(task.ID, fmt.Sprintf("任务执行异常: %v", r))
		}
	}()

	client.UpdateTaskProgress(task.ID, 10, "开始执行...")

	switch task.Type {
	case "SNAPSHOT":
		executeSnapshot(client, task)
	case "RECOVER":
		executeRecover(client, task)
	default:
		client.FailTask(task.ID, "未知任务类型: "+task.Type)
	}
}

func executeSnapshot(client *transport.Client, task transport.TaskInfo) {
	rc := restic.NewClient()

	// Step 1: Ensure restic is installed
	client.UpdateTaskProgress(task.ID, 15, "检查 restic 安装...")
	if err := rc.EnsureInstalled(); err != nil {
		client.FailTask(task.ID, "restic 安装失败: "+err.Error())
		return
	}

	// Build repo URL
	repoURL := task.RepoURL
	if repoURL == "" {
		repoURL = restic.BuildRepoUrl(task.StorageType, task.Endpoint)
	}

	// Step 2: Init repo if needed
	client.UpdateTaskProgress(task.ID, 20, "初始化仓库...")
	if err := rc.Init(repoURL, task.Password); err != nil {
		// Init may fail if repo already exists, that's OK
		log.Printf("Restic init note: %v (may already exist)", err)
	}

	// Step 3: Run backup
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

	// Step 4: Report completion
	result := fmt.Sprintf(`{"snapshotId":"%s","totalBytesProcessed":%d}`, snap.SnapshotID, snap.TotalBytesProcessed)
	client.UpdateTaskProgress(task.ID, 100, "快照完成")
	client.CompleteTask(task.ID, result)
}

func executeRecover(client *transport.Client, task transport.TaskInfo) {
	rc := restic.NewClient()

	// Step 1: Ensure restic is installed
	client.UpdateTaskProgress(task.ID, 15, "检查 restic 安装...")
	if err := rc.EnsureInstalled(); err != nil {
		client.FailTask(task.ID, "restic 安装失败: "+err.Error())
		return
	}

	repoURL := task.RepoURL
	if repoURL == "" {
		repoURL = restic.BuildRepoUrl(task.StorageType, task.Endpoint)
	}

	// Step 2: Verify repo integrity
	client.UpdateTaskProgress(task.ID, 25, "验证仓库完整性...")
	if err := rc.Check(repoURL, task.Password); err != nil {
		log.Printf("Restic check warning: %v", err)
		// Continue anyway - check may fail on some storage backends
	}

	// Step 3: Restore
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
