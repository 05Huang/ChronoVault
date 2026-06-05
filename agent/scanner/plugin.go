package scanner

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os/exec"
	"time"
)

// CustomCollectorResult holds the output of an external collector.
type CustomCollectorResult struct {
	Name   string                 `json:"name"`
	Data   map[string]interface{} `json:"data"`
	Error  string                 `json:"error,omitempty"`
	TookMs int64                  `json:"took_ms"`
}

// PluginConfig matches config.CustomCollector for decoupling.
type PluginConfig struct {
	Name    string
	Command string
	Timeout int
}

// RunCustomCollectors executes all configured external collectors concurrently.
// Each collector is a separate process that outputs JSON to stdout.
// Results are merged into state.json under "custom.<name>".
func RunCustomCollectors(collectors []PluginConfig) map[string]interface{} {
	if len(collectors) == 0 {
		return nil
	}

	results := make(chan CustomCollectorResult, len(collectors))

	for _, c := range collectors {
		go func(collector PluginConfig) {
			results <- runSingleCollector(collector)
		}(c)
	}

	merged := make(map[string]interface{})
	for i := 0; i < len(collectors); i++ {
		r := <-results
		if r.Error != "" {
			log.Printf("[PLUGIN] Collector '%s' failed: %s (took %dms)", r.Name, r.Error, r.TookMs)
			merged[r.Name] = map[string]interface{}{
				"error":  r.Error,
				"took_ms": r.TookMs,
			}
		} else {
			log.Printf("[PLUGIN] Collector '%s' completed (took %dms)", r.Name, r.TookMs)
			merged[r.Name] = r.Data
		}
	}

	return merged
}

// runSingleCollector executes one external collector command with timeout.
// The command must output valid JSON to stdout.
func runSingleCollector(c PluginConfig) CustomCollectorResult {
	start := time.Now()
	result := CustomCollectorResult{Name: c.Name}

	timeoutSecs := c.Timeout
	if timeoutSecs <= 0 {
		timeoutSecs = 10
	}
	timeout := time.Duration(timeoutSecs) * time.Second

	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	cmd := exec.CommandContext(ctx, "sh", "-c", c.Command)
	output, err := cmd.CombinedOutput()
	result.TookMs = time.Since(start).Milliseconds()

	if err != nil {
		if ctx.Err() == context.DeadlineExceeded {
			result.Error = fmt.Sprintf("command timed out after %ds", timeoutSecs)
		} else {
			result.Error = fmt.Sprintf("command failed: %v, output: %s", err, truncateStr(string(output), 200))
		}
		return result
	}

	// Parse JSON output
	var data map[string]interface{}
	if err := json.Unmarshal(output, &data); err != nil {
		// Try parsing as a JSON array and wrap it
		var arrData interface{}
		if err2 := json.Unmarshal(output, &arrData); err2 != nil {
			result.Error = fmt.Sprintf("invalid JSON output: %v", err)
			return result
		}
		data = map[string]interface{}{"items": arrData}
	}

	result.Data = data
	return result
}

func truncateStr(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max] + "..."
}
