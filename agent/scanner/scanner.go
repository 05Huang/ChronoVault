package scanner

import (
	"os/exec"
	"strings"
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

func ScanAll() ScanResult {
	result := ScanResult{}
	result.Docker = ScanDocker()
	result.Databases = ScanDatabases()
	result.WebServers = ScanWebServers()
	result.System = ScanSystem()
	return result
}

func commandExists(cmd string) bool {
	_, err := exec.LookPath(cmd)
	return err == nil
}

func runCommand(name string, args ...string) string {
	cmd := exec.Command(name, args...)
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(out))
}
