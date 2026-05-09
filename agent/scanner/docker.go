package scanner

import (
	"encoding/json"
	"strings"
)

func ScanDocker() DockerScanResult {
	result := DockerScanResult{}

	if !commandExists("docker") {
		return result
	}

	// Check if daemon is running
	info := runCommand("docker", "info")
	if info == "" {
		return result
	}
	result.Available = true

	// List containers
	psOutput := runCommand("docker", "ps", "-a", "--format", "{{json .}}")
	for _, line := range strings.Split(psOutput, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		var data map[string]interface{}
		if err := json.Unmarshal([]byte(line), &data); err != nil {
			continue
		}
		c := Container{
			Name:  getString(data, "Names"),
			Image: getString(data, "Image"),
			State: getString(data, "State"),
		}
		result.Containers = append(result.Containers, c)
	}

	// List volumes
	volOutput := runCommand("docker", "volume", "ls", "--format", "{{json .}}")
	for _, line := range strings.Split(volOutput, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		var data map[string]interface{}
		if err := json.Unmarshal([]byte(line), &data); err != nil {
			continue
		}
		v := Volume{
			Name: getString(data, "Name"),
		}
		result.Volumes = append(result.Volumes, v)
	}

	return result
}

func getString(data map[string]interface{}, key string) string {
	if val, ok := data[key]; ok {
		if s, ok := val.(string); ok {
			return s
		}
	}
	return ""
}
