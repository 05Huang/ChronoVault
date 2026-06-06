package scanner

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
)

// FileWatcher monitors specified directories for file changes and triggers
// incremental state.json updates (config hash recalculation only).
// This provides passive state collection: instead of periodic full scans,
// it only recalculates the configs section when files change.
type FileWatcher struct {
	watcher       *fsnotify.Watcher
	watchPaths    []string   // Directories to monitor (e.g., /etc, /opt)
	debounceTimer *time.Timer
	mu            sync.Mutex
	onChange      func(changedFiles []string)
	ctx           context.Context
	cancel        context.CancelFunc
	debounceWait  time.Duration // How long to wait after last change before triggering
}

// WatcherConfig holds configuration for the file watcher.
type WatcherConfig struct {
	Enabled      bool     `yaml:"enabled"`
	WatchPaths   []string `yaml:"watch_paths"`    // Directories to monitor
	DebounceMs   int      `yaml:"debounce_ms"`    // Debounce interval in ms (default: 3000)
	ExcludeGlobs []string `yaml:"exclude_globs"`  // Glob patterns to exclude (e.g., "*.log")
}

// DefaultWatcherConfig returns sensible defaults for the file watcher.
func DefaultWatcherConfig() WatcherConfig {
	return WatcherConfig{
		Enabled:    false,
		WatchPaths: []string{"/etc"},
		DebounceMs: 3000,
	}
}

// NewFileWatcher creates a new file watcher with the given configuration.
func NewFileWatcher(cfg WatcherConfig, onChange func(changedFiles []string)) (*FileWatcher, error) {
	if !cfg.Enabled {
		return nil, fmt.Errorf("file watcher is disabled")
	}

	fsw, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, fmt.Errorf("failed to create fsnotify watcher: %w", err)
	}

	debounceWait := time.Duration(cfg.DebounceMs) * time.Millisecond
	if debounceWait <= 0 {
		debounceWait = 3 * time.Second
	}

	ctx, cancel := context.WithCancel(context.Background())

	fw := &FileWatcher{
		watcher:      fsw,
		watchPaths:   cfg.WatchPaths,
		debounceWait: debounceWait,
		onChange:     onChange,
		ctx:          ctx,
		cancel:       cancel,
	}

	// Add watch paths
	for _, path := range cfg.WatchPaths {
		if err := fw.addWatchRecursive(path); err != nil {
			log.Printf("Warning: failed to watch path %s: %v", path, err)
		} else {
			log.Printf("Watching directory: %s", path)
		}
	}

	return fw, nil
}

// addWatchRecursive adds a directory and its subdirectories to the watch list.
func (fw *FileWatcher) addWatchRecursive(path string) error {
	info, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("cannot stat %s: %w", path, err)
	}
	if !info.IsDir() {
		return fmt.Errorf("%s is not a directory", path)
	}

	if err := fw.watcher.Add(path); err != nil {
		return fmt.Errorf("cannot watch %s: %w", path, err)
	}

	// Recursively add subdirectories (limited depth to avoid watching too many dirs)
	err = filepath.Walk(path, func(p string, info os.FileInfo, err error) error {
		if err != nil {
			return nil // Skip inaccessible dirs
		}
		if info.IsDir() {
			// Skip hidden dirs, proc, sys, and very deep paths
			base := filepath.Base(p)
			if base[0] == '.' || base == "proc" || base == "sys" || base == "dev" {
				return filepath.SkipDir
			}
			// Limit depth: count path separators
			rel, _ := filepath.Rel(path, p)
			depth := len(filepath.SplitList(rel))
			_ = depth
			if err := fw.watcher.Add(p); err != nil {
				// Non-fatal: some dirs may not be watchable
				return nil
			}
		}
		return nil
	})

	return err
}

// Start begins watching for file changes. Runs until Stop() is called.
func (fw *FileWatcher) Start() {
	log.Println("File watcher started")

	// Track changed files for debouncing
	var changedFiles []string
	var changeMu sync.Mutex

	for {
		select {
		case <-fw.ctx.Done():
			log.Println("File watcher stopped")
			return
		case event, ok := <-fw.watcher.Events:
			if !ok {
				return
			}
			// Only care about Write, Create, Remove, Rename
			if event.Op&(fsnotify.Write|fsnotify.Create|fsnotify.Remove|fsnotify.Rename) == 0 {
				continue
			}

			// Skip non-config files (e.g., .log, .tmp, .swp)
			if isExcludedFile(event.Name) {
				continue
			}

			changeMu.Lock()
			changedFiles = append(changedFiles, event.Name)
			changeMu.Unlock()

			// Debounce: reset timer on each change
			fw.mu.Lock()
			if fw.debounceTimer != nil {
				fw.debounceTimer.Stop()
			}
			fw.debounceTimer = time.AfterFunc(fw.debounceWait, func() {
				changeMu.Lock()
				files := make([]string, len(changedFiles))
				copy(files, changedFiles)
				changedFiles = nil
				changeMu.Unlock()

				if len(files) > 0 {
					log.Printf("File watcher: %d files changed, triggering state update", len(files))
					fw.onChange(files)
				}
			})
			fw.mu.Unlock()

		case err, ok := <-fw.watcher.Errors:
			if !ok {
				return
			}
			log.Printf("File watcher error: %v", err)
		}
	}
}

// Stop stops the file watcher and cleans up resources.
func (fw *FileWatcher) Stop() {
	fw.cancel()
	fw.watcher.Close()
	log.Println("File watcher stopped")
}

// isExcludedFile checks if a file should be excluded from monitoring.
func isExcludedFile(path string) bool {
	base := filepath.Base(path)
	// Skip common non-config files
	excludedExts := []string{".log", ".tmp", ".swp", ".swx", "~", ".bak", ".pid", ".lock"}
	for _, ext := range excludedExts {
		if len(base) > len(ext) && base[len(base)-len(ext):] == ext {
			return true
		}
	}
	// Skip hidden files
	if len(base) > 0 && base[0] == '.' {
		return true
	}
	return false
}

// CollectIncrementalState collects only the config hashes section of state.json.
// This is a lightweight operation compared to CollectStateSnapshot.
// It reads current config file hashes and returns the updated configs list.
func CollectIncrementalState() []ConfigHash {
	return collectConfigHashes()
}

// UpdateStateJsonConfigs reads the current state.json from disk, updates only
// the configs section, and writes it back. Returns the list of changed config paths.
func UpdateStateJsonConfigs(changedFiles []string) ([]string, error) {
	statePath := GetStateFilePath()

	// Read existing state.json
	data, err := os.ReadFile(statePath)
	if err != nil {
		if os.IsNotExist(err) {
			// No state.json yet — do a full collection instead
			return nil, fmt.Errorf("state.json not found, full collection needed")
		}
		return nil, fmt.Errorf("failed to read state.json: %w", err)
	}

	// Parse existing state
	var state map[string]interface{}
	if err := json.Unmarshal(data, &state); err != nil {
		return nil, fmt.Errorf("failed to parse state.json: %w", err)
	}

	// Check if any changed files match tracked config paths
	currentConfigs := CollectIncrementalState()
	changedConfigs := filterChangedConfigs(currentConfigs, changedFiles)

	if len(changedConfigs) == 0 {
		// No tracked configs were affected
		return nil, nil
	}

	// Update configs section
	state["configs"] = currentConfigs
	state["collected_at"] = time.Now().UTC().Format(time.RFC3339)

	// Write back
	updatedData, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("failed to marshal updated state: %w", err)
	}

	if err := os.WriteFile(statePath, updatedData, 0644); err != nil {
		return nil, fmt.Errorf("failed to write state.json: %w", err)
	}

	var changedPaths []string
	for _, c := range changedConfigs {
		changedPaths = append(changedPaths, c.Path)
	}

	log.Printf("State.json configs updated: %d config files changed", len(changedPaths))
	return changedPaths, nil
}

// filterChangedConfigs returns configs whose paths match the changed files.
func filterChangedConfigs(configs []ConfigHash, changedFiles []string) []ConfigHash {
	changedSet := make(map[string]bool, len(changedFiles))
	for _, f := range changedFiles {
		changedSet[f] = true
		// Also check resolved path
		if resolved, err := filepath.EvalSymlinks(f); err == nil {
			changedSet[resolved] = true
		}
	}

	var result []ConfigHash
	for _, cfg := range configs {
		if changedSet[cfg.Path] {
			result = append(result, cfg)
		}
	}

	// If no specific configs matched but files were changed in tracked directories,
	// do a full config refresh
	if len(result) == 0 && len(changedFiles) > 0 {
		return configs
	}

	return result
}

// HashFileForWatcher computes SHA256 hash and size of a file.
func HashFileForWatcher(path string) (string, int64) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", 0
	}
	hash := sha256.Sum256(data)
	return fmt.Sprintf("%x", hash), int64(len(data))
}
