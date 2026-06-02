package transport

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/chronovault/agent/scanner"
)

type Client struct {
	baseURL    string
	apiKey     string
	agentID    string
	serverID   string
	httpClient *http.Client
}

type RegisterResponse struct {
	ServerID int64  `json:"serverId"`
	AgentID  string `json:"agentId"`
	Status   string `json:"status"`
}

type TaskInfo struct {
	ID       int64  `json:"id"`
	Type     string `json:"type"`
	Status   string `json:"status"`
	Progress int    `json:"progress"`
	Message  string `json:"message"`

	// Storage config for SNAPSHOT/RECOVER tasks
	RepoURL    string   `json:"repoUrl"`
	Password   string   `json:"password"`
	StorageType string  `json:"storageType"`
	Endpoint   string   `json:"endpoint"`
	Paths      []string `json:"paths"`
	Excludes   []string `json:"excludes"`
	ParentID   string   `json:"parentId"`
	SnapshotID string   `json:"snapshotId"`
	TargetPath string   `json:"targetPath"`
}

func NewClient(baseURL, apiKey, agentID string) *Client {
	return &Client{
		baseURL:  baseURL,
		apiKey:   apiKey,
		agentID:  agentID,
		serverID: "",
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

func (c *Client) SetServerID(serverID string) {
	c.serverID = serverID
}

func (c *Client) Register(scanResult scanner.ScanResult) (*RegisterResponse, error) {
	body := map[string]string{
		"agentId":      c.agentID,
		"name":         scanResult.System.Hostname,
		"os":           scanResult.System.OS,
		"agentVersion": "0.1.0",
	}

	if c.serverID != "" {
		body["serverId"] = c.serverID
	}

	caps, _ := json.Marshal(scanResult)
	body["capabilities"] = string(caps)

	var resp RegisterResponse
	if err := c.post("/api/agent/register", body, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) Heartbeat() error {
	body := map[string]interface{}{
		"agentId": c.agentID,
		"metrics": map[string]interface{}{
			"timestamp": time.Now().Unix(),
		},
	}
	return c.post("/api/agent/heartbeat", body, nil)
}

func (c *Client) GetPendingTasks() ([]TaskInfo, error) {
	body := map[string]string{
		"agentId": c.agentID,
	}
	var tasks []TaskInfo
	if err := c.post("/api/agent/tasks/pending", body, &tasks); err != nil {
		return nil, err
	}
	return tasks, nil
}

func (c *Client) UpdateTaskProgress(taskID int64, progress int, message string) error {
	body := map[string]interface{}{
		"progress": progress,
		"message":  message,
	}
	return c.post(fmt.Sprintf("/api/agent/tasks/%d/progress", taskID), body, nil)
}

func (c *Client) CompleteTask(taskID int64, result string) error {
	body := map[string]string{
		"result": result,
	}
	return c.post(fmt.Sprintf("/api/agent/tasks/%d/complete", taskID), body, nil)
}

func (c *Client) FailTask(taskID int64, errMsg string) error {
	body := map[string]string{
		"error": errMsg,
	}
	return c.post(fmt.Sprintf("/api/agent/tasks/%d/fail", taskID), body, nil)
}

func (c *Client) post(path string, body interface{}, result interface{}) error {
	jsonBody, err := json.Marshal(body)
	if err != nil {
		return err
	}

	req, err := http.NewRequest("POST", c.baseURL+path, bytes.NewBuffer(jsonBody))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	if c.apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+c.apiKey)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		errStr := err.Error()
		if strings.Contains(errStr, "timeout") || strings.Contains(errStr, "deadline exceeded") {
			return fmt.Errorf("网络请求超时，请检查服务器是否可达: %w", err)
		}
		if strings.Contains(errStr, "connection refused") {
			return fmt.Errorf("无法连接到服务器 %s，请检查服务器是否运行: %w", c.baseURL, err)
		}
		return fmt.Errorf("网络请求失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		switch resp.StatusCode {
		case 401:
			return fmt.Errorf("API 认证失败 (401)，请检查 API 密钥是否正确")
		case 403:
			return fmt.Errorf("API 访问被拒绝 (403)，权限不足")
		case 404:
			return fmt.Errorf("API 端点不存在 (404)，请检查服务器版本")
		case 429:
			return fmt.Errorf("API 请求过于频繁 (429)，请稍后重试")
		case 500:
			return fmt.Errorf("服务器内部错误 (500)，请检查服务器日志")
		default:
			return fmt.Errorf("API 错误 %d: %s", resp.StatusCode, string(respBody))
		}
	}

	if result != nil {
		respBody, err := io.ReadAll(resp.Body)
		if err != nil {
			return err
		}
		// Unwrap ApiResponse wrapper
		var wrapper struct {
			Data json.RawMessage `json:"data"`
		}
		if err := json.Unmarshal(respBody, &wrapper); err == nil && wrapper.Data != nil {
			respBody = wrapper.Data
		}
		return json.Unmarshal(respBody, result)
	}
	return nil
}

// postWithRetry wraps post with exponential backoff retry logic.
// Retries up to maxRetries times on network errors and 5xx responses.
func (c *Client) postWithRetry(path string, body interface{}, result interface{}) error {
	maxRetries := 3
	var lastErr error

	for attempt := 0; attempt <= maxRetries; attempt++ {
		if attempt > 0 {
			delay := time.Duration(1<<uint(attempt-1)) * time.Second // 1s, 2s, 4s
			log.Printf("Retrying request to %s (attempt %d/%d) after %v...", path, attempt+1, maxRetries+1, delay)
			time.Sleep(delay)
		}

		lastErr = c.post(path, body, result)
		if lastErr == nil {
			return nil
		}

		// Don't retry on client errors (4xx except 429)
		errStr := lastErr.Error()
		if strings.Contains(errStr, "认证失败") || strings.Contains(errStr, "访问被拒绝") ||
			strings.Contains(errStr, "端点不存在") {
			return lastErr
		}
	}

	return fmt.Errorf("请求失败（已重试 %d 次）: %w", maxRetries, lastErr)
}
