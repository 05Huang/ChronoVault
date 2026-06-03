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

	client := transport.NewClient(cfg.ServerURL, cfg.APIKey, cfg.AgentID)

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

	// Start task polling loop
	go taskPollingLoop(client)

	// Wait for signal
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	sig := <-sigCh
	log.Printf("Received signal %v, shutting down gracefully...", sig)

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

func taskPollingLoop(client *transport.Client) {
	interval := 10 * time.Second
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-shutdownCtx.Done():
			log.Println("Task polling stopped (shutdown)")
			return
		case <-ticker.C:
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
				log.Printf("Executing task %d: %s", task.ID, task.Type)
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
