package config

import (
	"os"

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
	return cfg, err
}

func SaveConfig(path string, cfg *Config) error {
	data, err := yaml.Marshal(cfg)
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0644)
}
