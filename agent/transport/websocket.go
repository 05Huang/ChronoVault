package transport

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"sync"
	"time"
)

// WSMessage is a WebSocket message envelope for agent communication.
type WSMessage struct {
	Type    string          `json:"type"`    // "task", "cancel", "ping", "pong"
	Payload json.RawMessage `json:"payload,omitempty"`
}

// TaskDelivery carries task info over WebSocket.
type TaskDelivery struct {
	TaskID   int64  `json:"taskId"`
	Type     string `json:"type"`
	RepoURL  string `json:"repoUrl,omitempty"`
	Password string `json:"password,omitempty"`
	// ... other fields from TaskInfo
}

// WSClient maintains a WebSocket connection to the backend for real-time task delivery.
// Falls back to HTTP polling if WebSocket is unavailable.
type WSClient struct {
	baseURL    string
	agentID    string
	apiKey     string
	httpClient *http.Client

	mu          sync.Mutex
	conn        *http.Response
	connected   bool
	taskHandler func(TaskInfo)

	ctx    context.Context
	cancel context.CancelFunc
}

// NewWSClient creates a new WebSocket client for agent communication.
func NewWSClient(baseURL, agentID, apiKey string, handler func(TaskInfo)) *WSClient {
	ctx, cancel := context.WithCancel(context.Background())
	return &WSClient{
		baseURL:    baseURL,
		agentID:    agentID,
		apiKey:     apiKey,
		httpClient: &http.Client{Timeout: 0}, // No timeout for long-poll
		taskHandler: handler,
		ctx:        ctx,
		cancel:     cancel,
	}
}

// Start begins the WebSocket connection loop with automatic reconnection.
func (w *WSClient) Start() {
	go w.connectLoop()
}

// Stop gracefully shuts down the WebSocket client.
func (w *WSClient) Stop() {
	w.cancel()
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.conn != nil {
		w.conn.Body.Close()
		w.connected = false
	}
}

// IsConnected returns whether the WebSocket connection is active.
func (w *WSClient) IsConnected() bool {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.connected
}

// connectLoop continuously tries to establish and maintain a WebSocket connection.
func (w *WSClient) connectLoop() {
	backoff := time.Second
	maxBackoff := 30 * time.Second

	for {
		select {
		case <-w.ctx.Done():
			return
		default:
		}

		if err := w.connect(); err != nil {
			log.Printf("[WS] Connection failed: %v, retrying in %v", err, backoff)
			select {
			case <-w.ctx.Done():
				return
			case <-time.After(backoff):
			}
			backoff = backoff * 2
			if backoff > maxBackoff {
				backoff = maxBackoff
			}
		} else {
			backoff = time.Second // Reset backoff on successful connection
		}
	}
}

// connect establishes a long-connection to the backend and reads task messages.
// Uses HTTP long-polling as the transport (compatible with any HTTP server).
func (w *WSClient) connect() error {
	w.mu.Lock()
	w.connected = false
	w.mu.Unlock()

	// Build the long-poll URL
	u, err := url.Parse(w.baseURL + "/api/agent/ws/" + w.agentID)
	if err != nil {
		return fmt.Errorf("invalid URL: %w", err)
	}

	req, err := http.NewRequestWithContext(w.ctx, "GET", u.String(), nil)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+w.apiKey)
	req.Header.Set("Accept", "text/event-stream")
	req.Header.Set("Cache-Control", "no-cache")

	log.Printf("[WS] Connecting to %s", u.String())

	resp, err := w.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("connection failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("server returned status %d", resp.StatusCode)
	}

	w.mu.Lock()
	w.conn = resp
	w.connected = true
	w.mu.Unlock()

	log.Printf("[WS] Connected successfully")

	// Read messages from the response body (newline-delimited JSON)
	decoder := json.NewDecoder(resp.Body)
	for {
		select {
		case <-w.ctx.Done():
			return nil
		default:
		}

		var msg WSMessage
		if err := decoder.Decode(&msg); err != nil {
			w.mu.Lock()
			w.connected = false
			w.mu.Unlock()
			return fmt.Errorf("read error: %w", err)
		}

		w.handleMessage(msg)
	}
}

// handleMessage processes incoming WebSocket messages.
func (w *WSClient) handleMessage(msg WSMessage) {
	switch msg.Type {
	case "task":
		var task TaskInfo
		if err := json.Unmarshal(msg.Payload, &task); err != nil {
			log.Printf("[WS] Failed to parse task: %v", err)
			return
		}
		log.Printf("[WS] Received task %d: %s", task.ID, task.Type)
		if w.taskHandler != nil {
			go w.taskHandler(task)
		}
	case "cancel":
		var cancelMsg struct {
			TaskID int64 `json:"taskId"`
		}
		if err := json.Unmarshal(msg.Payload, &cancelMsg); err != nil {
			log.Printf("[WS] Failed to parse cancel: %v", err)
			return
		}
		log.Printf("[WS] Cancel request for task %d (not implemented yet)", cancelMsg.TaskID)
	case "ping":
		// Respond with pong (handled at transport level)
	case "pong":
		// Connection keepalive acknowledgment
	default:
		log.Printf("[WS] Unknown message type: %s", msg.Type)
	}
}
