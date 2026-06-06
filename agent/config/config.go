package config

import (
	"fmt"
	"os"
	"strings"

	"gopkg.in/yaml.v3"
)

type Config struct {
	ServerURL         string   `yaml:"server_url"`
	APIKey            string   `yaml:"api_key"`
	AgentID           string   `yaml:"agent_id"`
	ServerID          int64    `yaml:"server_id"`
	ListenPort        int      `yaml:"listen_port"`
	ScanInterval      int      `yaml:"scan_interval"`
	HeartbeatInterval int      `yaml:"heartbeat_interval"`
	TLSEnabled        bool     `yaml:"tls_enabled"`
	TLSCert           string   `yaml:"tls_cert"`
	TLSKey            string   `yaml:"tls_key"`
	AuthToken         string   `yaml:"auth_token"`

	// Custom state collectors: external commands that produce JSON on stdout.
	// Each entry is a path to an executable that outputs JSON to stdout.
	// The JSON will be merged into the state.json under "custom.<name>".
	CustomCollectors []CustomCollector `yaml:"custom_collectors"`

	// CustomConfigPaths: additional file paths to track (hash + size) in state.json configs.
	// These are added to the built-in list of /etc config files.
	CustomConfigPaths []string `yaml:"custom_config_paths"`

	// FileWatcher: passive state collection via inotify/fswatch.
	// When enabled, watches specified directories for file changes and triggers
	// incremental state.json config hash updates (without full re-collection).
	FileWatcher WatcherConfig `yaml:"file_watcher"`
}

// WatcherConfig holds configuration for the passive file watcher.
type WatcherConfig struct {
	Enabled    bool     `yaml:"enabled"`
	WatchPaths []string `yaml:"watch_paths"`
	DebounceMs int      `yaml:"debounce_ms"`
}

// CustomCollector defines an external command that collects custom state data.
type CustomCollector struct {
	Name    string `yaml:"name"`    // Unique name for this collector (used as key in state.json)
	Command string `yaml:"command"` // Command to execute (shell-compatible)
	Timeout int    `yaml:"timeout"` // Timeout in seconds (default: 10)
}

func DefaultConfig() *Config {
	return &Config{
		ListenPort:        9270,
		ScanInterval:      300,
		HeartbeatInterval: 30,
	}
}

// Validate checks the configuration for required fields and returns clear error messages.
func (c *Config) Validate() error {
	var errs []string

	if c.ListenPort <= 0 || c.ListenPort > 65535 {
		errs = append(errs, fmt.Sprintf("listen_port must be between 1 and 65535, got %d", c.ListenPort))
	}

	if c.ScanInterval < 60 {
		errs = append(errs, fmt.Sprintf("scan_interval must be at least 60 seconds, got %d", c.ScanInterval))
	}

	if c.HeartbeatInterval < 5 {
		errs = append(errs, fmt.Sprintf("heartbeat_interval must be at least 5 seconds, got %d", c.HeartbeatInterval))
	}

	if c.TLSEnabled {
		if c.TLSCert == "" {
			errs = append(errs, "tls_cert is required when tls_enabled is true")
		}
		if c.TLSKey == "" {
			errs = append(errs, "tls_key is required when tls_enabled is true")
		}
		if c.TLSCert != "" {
			if _, err := os.Stat(c.TLSCert); os.IsNotExist(err) {
				errs = append(errs, fmt.Sprintf("tls_cert file not found: %s", c.TLSCert))
			}
		}
		if c.TLSKey != "" {
			if _, err := os.Stat(c.TLSKey); os.IsNotExist(err) {
				errs = append(errs, fmt.Sprintf("tls_key file not found: %s", c.TLSKey))
			}
		}
	}

	if len(errs) > 0 {
		return fmt.Errorf("configuration errors:\n  - %s", strings.Join(errs, "\n  - "))
	}
	return nil
}

func LoadConfig(path string) (*Config, error) {
	cfg := DefaultConfig()
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return cfg, nil
		}
		return nil, err
	}
	err = yaml.Unmarshal(data, cfg)
	if err != nil {
		return nil, fmt.Errorf("failed to parse config file %s: %w", path, err)
	}
	return cfg, nil
}

func SaveConfig(path string, cfg *Config) error {
	data, err := yaml.Marshal(cfg)
	if err != nil {
		return err
	}
	// Use restrictive permissions (owner-only read/write) to protect sensitive config data
	if err := os.WriteFile(path, data, 0600); err != nil {
		return err
	}
	// On Unix, enforce 0600 even if umask would allow broader access
	if err := os.Chmod(path, 0600); err != nil {
		// Non-fatal on non-Unix systems
		_ = err
	}
	return nil
}
