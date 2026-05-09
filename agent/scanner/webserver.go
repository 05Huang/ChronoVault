package scanner

import "strings"

func ScanWebServers() []WebServerInfo {
	var servers []WebServerInfo

	// Nginx
	if commandExists("nginx") {
		version := runCommand("nginx", "-v")
		version = strings.TrimPrefix(version, "nginx version: ")
		servers = append(servers, WebServerInfo{
			Type:    "Nginx",
			Version: version,
			Status:  "running",
		})
	}

	// Apache
	if commandExists("httpd") || commandExists("apache2") {
		version := runCommand("httpd", "-v")
		if version == "" {
			version = runCommand("apache2", "-v")
		}
		servers = append(servers, WebServerInfo{
			Type:    "Apache",
			Version: extractVersion(version),
			Status:  "running",
		})
	}

	// Caddy
	if commandExists("caddy") {
		version := runCommand("caddy", "version")
		servers = append(servers, WebServerInfo{
			Type:    "Caddy",
			Version: strings.TrimSpace(version),
			Status:  "running",
		})
	}

	return servers
}
