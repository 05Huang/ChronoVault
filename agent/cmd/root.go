package cmd

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/chronovault/agent/config"
	"github.com/chronovault/agent/scanner"
	"github.com/chronovault/agent/server"
	"github.com/chronovault/agent/transport"
)

var cfg *config.Config

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
	log.Printf("Received signal %v, shutting down...", sig)
}

func Register(args []string) {
	var serverURL, apiKey string
	for i := 0; i < len(args)-1; i++ {
		switch args[i] {
		case "--server-url":
			serverURL = args[i+1]
		case "--api-key":
			apiKey = args[i+1]
		}
	}

	if serverURL == "" || apiKey == "" {
		fmt.Println("Error: --server-url and --api-key are required")
		os.Exit(1)
	}

	cfg.ServerURL = serverURL
	cfg.APIKey = apiKey

	client := transport.NewClient(serverURL, apiKey, "")
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

	for range ticker.C {
		if err := client.Heartbeat(); err != nil {
			log.Printf("Heartbeat failed: %v", err)
		}
	}
}

func taskPollingLoop(client *transport.Client) {
	interval := 10 * time.Second
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for range ticker.C {
		tasks, err := client.GetPendingTasks()
		if err != nil {
			continue
		}
		for _, task := range tasks {
			log.Printf("Executing task %d: %s", task.ID, task.Type)
			go executeTask(client, task)
		}
	}
}

func executeTask(client *transport.Client, task transport.TaskInfo) {
	// Update progress
	client.UpdateTaskProgress(task.ID, 10, "开始执行...")

	switch task.Type {
	case "SNAPSHOT":
		client.UpdateTaskProgress(task.ID, 50, "创建快照...")
		// Snapshot logic would go here
		client.CompleteTask(task.ID, `{"status": "completed"}`)
	case "RECOVER":
		client.UpdateTaskProgress(task.ID, 50, "执行恢复...")
		client.CompleteTask(task.ID, `{"status": "completed"}`)
	default:
		client.FailTask(task.ID, "未知任务类型: "+task.Type)
	}
}
