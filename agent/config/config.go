package config

import (
	"fmt"
	"os"
	"strings"

	"gopkg.in/yaml.v3"
)

type Config struct {
	ServerURL  string `yaml:"server_url"`
	APIKey     string `yaml:"api_key"`
	AgentID    string `yaml:"agent_id"`
	ServerID   int64  `yaml:"server_id"`
	ListenPort int    `yaml:"listen_port"`
	ScanInterval int  `yaml:"scan_interval"`
	HeartbeatInterval int `yaml:"heartbeat_interval"`
	TLSEnabled bool   `yaml:"tls_enabled"`
	TLSCert    string `yaml:"tls_cert"`
	TLSKey     string `yaml:"tls_key"`
	AuthToken  string `yaml:"auth_token"`
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
	return os.WriteFile(path, data, 0644)
}
