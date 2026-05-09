package scanner

import (
	"runtime"
	"strings"
)

func ScanSystem() SystemInfo {
	info := SystemInfo{
		OS:       runtime.GOOS,
		CPUCores: runtime.NumCPU(),
	}

	hostname, err := getHostname()
	if err == nil {
		info.Hostname = hostname
	}

	return info
}

func getHostname() (string, error) {
	hostname := runCommand("hostname")
	if hostname != "" {
		return hostname, nil
	}
	return "unknown", nil
}
