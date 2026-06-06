package chronovault

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// ApiClient is a minimal HTTP client for the ChronoVault REST API.
type ApiClient struct {
	Host  string
	Token string
	HTTP  *http.Client
}

func (c *ApiClient) baseURL() string {
	return strings.TrimRight(c.Host, "/") + "/api/v1"
}

func (c *ApiClient) doRequest(method, path string, body interface{}) (map[string]interface{}, int, error) {
	url := c.baseURL() + path

	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, 0, fmt.Errorf("marshal request body: %w", err)
		}
		bodyReader = bytes.NewReader(data)
	}

	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return nil, 0, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.Token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.HTTP.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("execute request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("read response: %w", err)
	}

	var result map[string]interface{}
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &result); err != nil {
			return nil, resp.StatusCode, fmt.Errorf("parse response: %w", err)
		}
	}

	return result, resp.StatusCode, nil
}

// GetServer fetches a server by ID.
func (c *ApiClient) GetServer(id string) (map[string]interface{}, error) {
	result, status, err := c.doRequest("GET", "/servers/"+id, nil)
	if err != nil {
		return nil, err
	}
	if status == 404 {
		return nil, nil
	}
	if status >= 400 {
		return nil, fmt.Errorf("API error %d: %v", status, result)
	}
	if data, ok := result["data"].(map[string]interface{}); ok {
		return data, nil
	}
	return result, nil
}

// CreateServer registers a new server.
func (c *ApiClient) CreateServer(name, ip, osName string) (map[string]interface{}, error) {
	body := map[string]interface{}{
		"name": name,
		"ip":   ip,
		"os":   osName,
	}
	result, status, err := c.doRequest("POST", "/servers", body)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, fmt.Errorf("API error %d: %v", status, result)
	}
	if data, ok := result["data"].(map[string]interface{}); ok {
		return data, nil
	}
	return result, nil
}

// UpdateServer updates a server by ID.
func (c *ApiClient) UpdateServer(id, name string) (map[string]interface{}, error) {
	body := map[string]interface{}{
		"name": name,
	}
	result, status, err := c.doRequest("PUT", "/servers/"+id, body)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, fmt.Errorf("API error %d: %v", status, result)
	}
	if data, ok := result["data"].(map[string]interface{}); ok {
		return data, nil
	}
	return result, nil
}

// DeleteServer deletes a server by ID.
func (c *ApiClient) DeleteServer(id string) error {
	_, status, err := c.doRequest("DELETE", "/servers/"+id, nil)
	if err != nil {
		return err
	}
	if status >= 400 && status != 404 {
		return fmt.Errorf("API error %d", status)
	}
	return nil
}

// GetSnapshotPolicy fetches a snapshot retention policy by ID.
func (c *ApiClient) GetSnapshotPolicy(id string) (map[string]interface{}, error) {
	result, status, err := c.doRequest("GET", "/snapshots/retention/"+id, nil)
	if err != nil {
		return nil, err
	}
	if status == 404 {
		return nil, nil
	}
	if status >= 400 {
		return nil, fmt.Errorf("API error %d: %v", status, result)
	}
	if data, ok := result["data"].(map[string]interface{}); ok {
		return data, nil
	}
	return result, nil
}

// CreateSnapshotPolicy creates a new snapshot retention policy.
func (c *ApiClient) CreateSnapshotPolicy(serverID int, name string, maxCount, maxAgeDays, minKeepDays *int) (map[string]interface{}, error) {
	body := map[string]interface{}{
		"serverId": serverID,
		"name":     name,
		"enabled":  true,
	}
	if maxCount != nil {
		body["maxCount"] = *maxCount
	}
	if maxAgeDays != nil {
		body["maxAgeDays"] = *maxAgeDays
	}
	if minKeepDays != nil {
		body["minKeepDays"] = *minKeepDays
	} else {
		defaultMinKeep := 7
		body["minKeepDays"] = defaultMinKeep
	}
	result, status, err := c.doRequest("POST", "/snapshots/retention", body)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, fmt.Errorf("API error %d: %v", status, result)
	}
	if data, ok := result["data"].(map[string]interface{}); ok {
		return data, nil
	}
	return result, nil
}

// UpdateSnapshotPolicy updates an existing snapshot retention policy.
func (c *ApiClient) UpdateSnapshotPolicy(id string, name string, maxCount, maxAgeDays, minKeepDays *int, enabled bool) (map[string]interface{}, error) {
	body := map[string]interface{}{
		"name":    name,
		"enabled": enabled,
	}
	if maxCount != nil {
		body["maxCount"] = *maxCount
	}
	if maxAgeDays != nil {
		body["maxAgeDays"] = *maxAgeDays
	}
	if minKeepDays != nil {
		body["minKeepDays"] = *minKeepDays
	}
	result, status, err := c.doRequest("PUT", "/snapshots/retention/"+id, body)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, fmt.Errorf("API error %d: %v", status, result)
	}
	if data, ok := result["data"].(map[string]interface{}); ok {
		return data, nil
	}
	return result, nil
}

// DeleteSnapshotPolicy deletes a snapshot retention policy by ID.
func (c *ApiClient) DeleteSnapshotPolicy(id string) error {
	_, status, err := c.doRequest("DELETE", "/snapshots/retention/"+id, nil)
	if err != nil {
		return err
	}
	if status >= 400 && status != 404 {
		return fmt.Errorf("API error %d", status)
	}
	return nil
}
