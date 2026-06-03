package scanner

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

type ScanResult struct {
	Docker     DockerScanResult     `json:"docker"`
	Databases  []DatabaseInfo       `json:"databases"`
	WebServers []WebServerInfo      `json:"web_servers"`
	System     SystemInfo           `json:"system"`
}

type DockerScanResult struct {
	Available  bool        `json:"available"`
	Containers []Container `json:"containers"`
	Volumes    []Volume    `json:"volumes"`
}

type Container struct {
	Name   string `json:"name"`
	Image  string `json:"image"`
	State  string `json:"state"`
	Status string `json:"status"`
}

type Volume struct {
	Name       string `json:"name"`
	Mountpoint string `json:"mountpoint"`
}

type DatabaseInfo struct {
	Type    string `json:"type"`
	Version string `json:"version"`
	Port    int    `json:"port"`
	Status  string `json:"status"`
}

type WebServerInfo struct {
	Type    string `json:"type"`
	Version string `json:"version"`
	Status  string `json:"status"`
}

type SystemInfo struct {
	OS       string `json:"os"`
	Hostname string `json:"hostname"`
	CPUCores int    `json:"cpu_cores"`
}

// ===== State.json collector types =====

// StateSnapshot is the full state.json structure captured during snapshots.
type StateSnapshot struct {
	CollectedAt  string             `json:"collected_at"`
	AgentVersion string             `json:"agent_version"`
	OS           OSInfo             `json:"os"`
	Packages     []PackageInfo      `json:"packages"`
	Services     []ServiceInfo      `json:"services"`
	Ports        []PortInfo         `json:"ports"`
	Docker       DockerState        `json:"docker"`
	Configs      []ConfigHash       `json:"configs"`
	Crontab      []CrontabEntry     `json:"crontab"`
}

type OSInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Kernel  string `json:"kernel"`
	Arch    string `json:"arch"`
}

type PackageInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Manager string `json:"manager"`
}

type ServiceInfo struct {
	Name    string `json:"name"`
	Status  string `json:"status"`
	Enabled bool   `json:"enabled"`
	PID     int    `json:"pid,omitempty"`
}

type PortInfo struct {
	Port     int    `json:"port"`
	Protocol string `json:"protocol"`
	Process  string `json:"process"`
	State    string `json:"state"`
}

type DockerState struct {
	Available    bool              `json:"available"`
	Containers   []DockerContainer `json:"containers"`
	ComposeFiles []string          `json:"compose_files"`
}

type DockerContainer struct {
	ID     string   `json:"id"`
	Name   string   `json:"name"`
	Image  string   `json:"image"`
	Status string   `json:"status"`
	Ports  []string `json:"ports"`
}

type ConfigHash struct {
	Path   string `json:"path"`
	SHA256 string `json:"sha256"`
	Size   int64  `json:"size"`
}

type CrontabEntry struct {
	User     string `json:"user"`
	Schedule string `json:"schedule"`
	Command  string `json:"command"`
}

func ScanAll() ScanResult {
	result := ScanResult{}
	result.Docker = ScanDocker()
	result.Databases = ScanDatabases()
	result.WebServers = ScanWebServers()
	result.System = ScanSystem()
	return result
}

// CollectStateSnapshot gathers comprehensive system state information.
// This is the core differentiator: state.json captures what the system looks like,
// complementing the file-level backup from restic.
func CollectStateSnapshot() (*StateSnapshot, error) {
	state := &StateSnapshot{
		CollectedAt:  time.Now().UTC().Format(time.RFC3339),
		AgentVersion: "0.1.0",
	}

	// Use concurrent collection for better performance
	// Target: all modules complete within 10 seconds total
	var wg sync.WaitGroup
	var mu sync.Mutex
	collectionErrors := []string{}

	// Collect OS info (fast, do synchronously)
	state.OS = collectOSInfo()

	// Collect other modules concurrently using safe goroutines
	// Each goroutine writes to its own local variable, then we merge after completion
	type collectResult struct {
		packages   []PackageInfo
		services   []ServiceInfo
		ports      []PortInfo
		docker     DockerState
		configs    []ConfigHash
		crontab    []CrontabEntry
		moduleName string
	}

	results := make([]collectResult, 6)

	type moduleDef struct {
		name string
		fn   func() interface{}
	}

	modules := []moduleDef{
		{"packages", func() interface{} { return collectPackages() }},
		{"services", func() interface{} { return collectServices() }},
		{"ports", func() interface{} { return collectPorts() }},
		{"docker", func() interface{} { return collectDockerState() }},
		{"configs", func() interface{} { return collectConfigHashes() }},
		{"crontab", func() interface{} { return collectCrontab() }},
	}

	for i, module := range modules {
		wg.Add(1)
		go func(idx int, mod moduleDef) {
			defer wg.Done()
			start := time.Now()
			result := mod.fn()
			elapsed := time.Since(start)

			mu.Lock()
			results[idx].moduleName = mod.name
			switch v := result.(type) {
			case []PackageInfo:
				results[idx].packages = v
			case []ServiceInfo:
				results[idx].services = v
			case []PortInfo:
				results[idx].ports = v
			case DockerState:
				results[idx].docker = v
			case []ConfigHash:
				results[idx].configs = v
			case []CrontabEntry:
				results[idx].crontab = v
			}
			if elapsed > 2*time.Second {
				collectionErrors = append(collectionErrors, fmt.Sprintf("%s: %v", mod.name, elapsed))
			}
			mu.Unlock()
		}(i, module)
	}

	// Wait for all modules with timeout
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// All modules completed successfully
	case <-time.After(10 * time.Second):
		log.Printf("Warning: State collection timed out after 10s")
	}

	// Merge results safely under lock
	mu.Lock()
	for _, r := range results {
		if r.packages != nil {
			state.Packages = r.packages
		}
		if r.services != nil {
			state.Services = r.services
		}
		if r.ports != nil {
			state.Ports = r.ports
		}
		if r.docker.Containers != nil || r.docker.ComposeFiles != nil {
			state.Docker = r.docker
		}
		if r.configs != nil {
			state.Configs = r.configs
		}
		if r.crontab != nil {
			state.Crontab = r.crontab
		}
	}
	mu.Unlock()

	if len(collectionErrors) > 0 {
		log.Printf("Slow collection modules: %s", strings.Join(collectionErrors, ", "))
	}

	return state, nil
}

// MarshalJSON returns JSON encoding of the state snapshot.
func (s *StateSnapshot) MarshalJSON() ([]byte, error) {
	type Alias StateSnapshot
	return json.Marshal(struct {
		*Alias
	}{(*Alias)(s)})
}

func collectOSInfo() OSInfo {
	info := OSInfo{
		Name:   runCommandSafe("cat /etc/os-release 2>/dev/null | grep ^NAME= | cut -d'\"' -f2"),
		Version: runCommandSafe("cat /etc/os-release 2>/dev/null | grep ^VERSION_ID= | cut -d'\"' -f2"),
		Kernel:  runCommandSafe("uname -r"),
		Arch:    runtime.GOARCH,
	}
	if info.Name == "" {
		info.Name = runtime.GOOS
	}
	return info
}

func collectPackages() []PackageInfo {
	var packages []PackageInfo

	// Try dpkg (Debian/Ubuntu)
	if out := runCommandSafe("dpkg-query -W -f='${Package}\t${Version}\n' 2>/dev/null"); out != "" {
		for _, line := range strings.Split(out, "\n") {
			line = strings.TrimSpace(line)
			if line == "" {
				continue
			}
			parts := strings.SplitN(line, "\t", 2)
			if len(parts) == 2 {
				packages = append(packages, PackageInfo{
					Name:    parts[0],
					Version: parts[1],
					Manager: "apt",
				})
			}
		}
		return packages
	}

	// Try rpm (RHEL/CentOS)
	if out := runCommandSafe("rpm -qa --queryformat '%{NAME}\t%{VERSION}-%{RELEASE}\n' 2>/dev/null"); out != "" {
		for _, line := range strings.Split(out, "\n") {
			line = strings.TrimSpace(line)
			if line == "" {
				continue
			}
			parts := strings.SplitN(line, "\t", 2)
			if len(parts) == 2 {
				packages = append(packages, PackageInfo{
					Name:    parts[0],
					Version: parts[1],
					Manager: "yum",
				})
			}
		}
		return packages
	}

	// Try apk (Alpine)
	if out := runCommandSafe("apk list --installed 2>/dev/null"); out != "" {
		for _, line := range strings.Split(out, "\n") {
			line = strings.TrimSpace(line)
			if line == "" {
				continue
			}
			// Format: "package-version"
			parts := strings.SplitN(line, "-", 2)
			if len(parts) >= 2 {
				packages = append(packages, PackageInfo{
					Name:    parts[0],
					Version: strings.Join(parts[1:], "-"),
					Manager: "apk",
				})
			}
		}
	}

	return packages
}

func collectServices() []ServiceInfo {
	var services []ServiceInfo

	if !commandExists("systemctl") {
		return services
	}

	// Get all loaded service units
	out := runCommandSafe("systemctl list-units --type=service --all --no-pager --no-legend 2>/dev/null")
	if out == "" {
		return services
	}

	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 4 {
			continue
		}

		name := strings.TrimSuffix(fields[0], ".service")
		status := fields[2] // active, inactive, failed, etc.
		enabled := fields[3] == "loaded"

		// Get PID if active
		pid := 0
		if status == "active" {
			pidOut := runCommandSafe(fmt.Sprintf("systemctl show %s --property=MainPID --value 2>/dev/null", name))
			if pidOut != "" && pidOut != "0" {
				fmt.Sscanf(pidOut, "%d", &pid)
			}
		}

		services = append(services, ServiceInfo{
			Name:    name,
			Status:  status,
			Enabled: enabled,
			PID:     pid,
		})
	}

	return services
}

func collectPorts() []PortInfo {
	var ports []PortInfo

	// Try ss first (modern replacement for netstat)
	out := runCommandSafe("ss -tulnp 2>/dev/null")
	if out == "" {
		out = runCommandSafe("netstat -tulnp 2>/dev/null")
	}
	if out == "" {
		return ports
	}

	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "State") || strings.HasPrefix(line, "Netid") {
			continue
		}

		fields := strings.Fields(line)
		if len(fields) < 5 {
			continue
		}

		// Parse the local address which contains the port
		var localAddr string
		var protocol string
		var state string

		// ss format: Netid State Recv-Q Send-Q Local-Address:Port Peer-Address:Port Process
		if strings.Contains(fields[0], "tcp") || strings.Contains(fields[0], "udp") {
			protocol = "tcp"
			if strings.Contains(fields[0], "udp") {
				protocol = "udp"
			}
			state = fields[1]
			localAddr = fields[4]
		} else {
			// netstat format: Proto Recv-Q Send-Q Local-Address Foreign-Address State PID/Program
			protocol = fields[0]
			localAddr = fields[3]
			if len(fields) > 5 {
				state = fields[5]
			}
		}

		// Extract port from address (last part after colon)
		parts := strings.Split(localAddr, ":")
		if len(parts) < 2 {
			continue
		}
		portStr := parts[len(parts)-1]

		var port int
		if _, err := fmt.Sscanf(portStr, "%d", &port); err != nil || port == 0 {
			continue
		}

		// Try to find process name
		process := ""
		processIdx := -1
		for i, f := range fields {
			if strings.Contains(f, "users:") || strings.Contains(f, "/") && strings.Contains(f, "pid=") {
				processIdx = i
				break
			}
		}
		if processIdx > 0 && processIdx < len(fields) {
			process = fields[processIdx]
			// Clean up: extract just the program name
			if idx := strings.Index(process, "\""); idx >= 0 {
				end := strings.Index(process[idx+1:], "\"")
				if end >= 0 {
					process = process[idx+1 : idx+1+end]
				}
			}
		}

		ports = append(ports, PortInfo{
			Port:     port,
			Protocol: protocol,
			Process:  process,
			State:    state,
		})
	}

	return ports
}

func collectDockerState() DockerState {
	state := DockerState{
		Available:    commandExists("docker"),
		Containers:   []DockerContainer{},
		ComposeFiles: []string{},
	}

	if !state.Available {
		return state
	}

	// Check if docker daemon is running
	if out := runCommandSafe("docker info 2>/dev/null | head -1"); !strings.Contains(out, "Containers") {
		state.Available = false
		return state
	}

	// List containers with details
	out := runCommandSafe("docker ps -a --format '{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}' 2>/dev/null")
	if out != "" {
		for _, line := range strings.Split(out, "\n") {
			line = strings.TrimSpace(line)
			if line == "" {
				continue
			}
			parts := strings.SplitN(line, "|", 4)
			if len(parts) < 4 {
				continue
			}

			// Get port bindings
			portsOut := runCommandSafe(fmt.Sprintf("docker inspect %s --format '{{range .NetworkSettings.Ports}}{{.}}{{end}}' 2>/dev/null", parts[0]))
			var ports []string
			if portsOut != "" {
				ports = strings.Split(portsOut, " ")
			}

			state.Containers = append(state.Containers, DockerContainer{
				ID:     parts[0],
				Name:   parts[1],
				Image:  parts[2],
				Status: parts[3],
				Ports:  ports,
			})
		}
	}

	// Find docker-compose files
	out = runCommandSafe("find / -maxdepth 5 -name 'docker-compose.yml' -o -name 'docker-compose.yaml' -o -name 'compose.yml' -o -name 'compose.yaml' 2>/dev/null | head -20")
	if out != "" {
		for _, line := range strings.Split(out, "\n") {
			line = strings.TrimSpace(line)
			if line != "" && !strings.Contains(line, "proc") && !strings.Contains(line, "sys") {
				state.ComposeFiles = append(state.ComposeFiles, line)
			}
		}
	}

	return state
}

func collectConfigHashes() []ConfigHash {
	var configs []ConfigHash

	// Key configuration files to track
	configPaths := []string{
		"/etc/nginx/nginx.conf",
		"/etc/nginx/sites-available/default",
		"/etc/apache2/apache2.conf",
		"/etc/mysql/my.cnf",
		"/etc/postgresql/*/main/postgresql.conf",
		"/etc/redis/redis.conf",
		"/etc/ssh/sshd_config",
		"/etc/hosts",
		"/etc/resolv.conf",
		"/etc/fstab",
		"/etc/crontab",
		"/etc/environment",
		"/etc/sysctl.conf",
	}

	for _, pattern := range configPaths {
		// Use shell glob expansion
		matches := runCommandSafe(fmt.Sprintf("ls %s 2>/dev/null", pattern))
		if matches == "" {
			continue
		}

		for _, path := range strings.Split(matches, "\n") {
			path = strings.TrimSpace(path)
			if path == "" {
				continue
			}

			hash, size := hashFile(path)
			if hash != "" {
				configs = append(configs, ConfigHash{
					Path:   path,
					SHA256: hash,
					Size:   size,
				})
			}
		}
	}

	return configs
}

func hashFile(path string) (string, int64) {
	data, err := os.ReadFile(path)
	if err != nil {
		// Try with sudo
		out := runCommandSafe(fmt.Sprintf("sudo cat %s 2>/dev/null", path))
		if out == "" {
			return "", 0
		}
		data = []byte(out)
	}

	hash := sha256.Sum256(data)
	return fmt.Sprintf("%x", hash), int64(len(data))
}

func collectCrontab() []CrontabEntry {
	var entries []CrontabEntry

	// System crontab
	if out := runCommandSafe("cat /etc/crontab 2>/dev/null"); out != "" {
		for _, line := range strings.Split(out, "\n") {
			line = strings.TrimSpace(line)
			if line == "" || strings.HasPrefix(line, "#") {
				continue
			}
			fields := strings.Fields(line)
			if len(fields) >= 6 {
				entries = append(entries, CrontabEntry{
					User:     fields[0],
					Schedule: strings.Join(fields[1:6], " "),
					Command:  strings.Join(fields[6:], " "),
				})
			}
		}
	}

	// User crontabs
	users := []string{"root"}
	userListOut := runCommandSafe("cut -d: -f1 /etc/passwd 2>/dev/null | head -20")
	if userListOut != "" {
		users = strings.Split(userListOut, "\n")
	}

	for _, user := range users {
		user = strings.TrimSpace(user)
		if user == "" {
			continue
		}
		out := runCommandSafe(fmt.Sprintf("crontab -u %s -l 2>/dev/null", user))
		if out == "" {
			continue
		}
		for _, line := range strings.Split(out, "\n") {
			line = strings.TrimSpace(line)
			if line == "" || strings.HasPrefix(line, "#") {
				continue
			}
			fields := strings.Fields(line)
			if len(fields) >= 6 {
				entries = append(entries, CrontabEntry{
					User:     user,
					Schedule: strings.Join(fields[1:6], " "),
					Command:  strings.Join(fields[6:], " "),
				})
			}
		}
	}

	return entries
}

func commandExists(cmd string) bool {
	_, err := exec.LookPath(cmd)
	return err == nil
}

func runCommandSafe(cmd string) string {
	out, err := exec.Command("sh", "-c", cmd).Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(out))
}

func runCommand(name string, args ...string) string {
	cmd := exec.Command(name, args...)
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(out))
}

// GetStateFilePath returns the path where state.json should be stored.
func GetStateFilePath() string {
	home, err := os.UserHomeDir()
	if err != nil {
		home = "/root"
	}
	return filepath.Join(home, ".chronovault", "state.json")
}

// SaveStateSnapshot writes the state snapshot to disk as a JSON file.
func SaveStateSnapshot(state *StateSnapshot) error {
	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal state snapshot: %w", err)
	}

	path := GetStateFilePath()
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return fmt.Errorf("failed to create directory %s: %w", dir, err)
	}

	if err := os.WriteFile(path, data, 0644); err != nil {
		return fmt.Errorf("failed to write state snapshot: %w", err)
	}

	return nil
}
