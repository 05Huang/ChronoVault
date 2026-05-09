package scanner

import (
	"net"
	"strings"
	"time"
)

func ScanDatabases() []DatabaseInfo {
	var databases []DatabaseInfo

	// Check common database ports
	dbChecks := []struct {
		name string
		port int
	}{
		{"MySQL", 3306},
		{"PostgreSQL", 5432},
		{"Redis", 6379},
		{"MongoDB", 27017},
	}

	for _, check := range dbChecks {
		conn, err := net.DialTimeout("tcp", "localhost:"+itoa(check.port), 2*time.Second)
		if err != nil {
			continue
		}
		conn.Close()

		db := DatabaseInfo{
			Type:   check.name,
			Port:   check.port,
			Status: "running",
		}

		// Try to get version
		switch check.name {
		case "MySQL":
			version := runCommand("mysql", "--version")
			if version != "" {
				db.Version = extractVersion(version)
			}
		case "PostgreSQL":
			version := runCommand("psql", "--version")
			if version != "" {
				db.Version = extractVersion(version)
			}
		case "Redis":
			version := runCommand("redis-server", "--version")
			if version != "" {
				db.Version = extractVersion(version)
			}
		}

		databases = append(databases, db)
	}

	return databases
}

func itoa(i int) string {
	return strings.TrimSpace(strings.Replace(
		strings.Replace(
			strings.Replace(
				strings.Replace(
					strings.Replace(
						strings.Replace(
							strings.Replace(
								strings.Replace(
									strings.Replace(
										strings.Replace("0", "0", "0", -1),
										"0", "1", -1),
									"0", "2", -1),
								"0", "3", -1),
							"0", "4", -1),
						"0", "5", -1),
					"0", "6", -1),
				"0", "7", -1),
			"0", "8", -1),
		"0", "9", -1))
}

func extractVersion(s string) string {
	parts := strings.Fields(s)
	for _, p := range parts {
		if strings.Contains(p, ".") && len(p) > 2 {
			return p
		}
	}
	return s
}
