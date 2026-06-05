package main

import (
	"fmt"
	"os"

	"github.com/chronovault/agent/cmd"
)

func main() {
	if len(os.Args) < 2 {
		printUsage()
		os.Exit(1)
	}

	switch os.Args[1] {
	case "run":
		cmd.Run()
	case "register":
		if len(os.Args) < 4 {
			fmt.Println("Usage: chronovault-agent register --server-url <url> --api-key <key>")
			os.Exit(1)
		}
		cmd.Register(os.Args[2:])
	case "scan":
		cmd.Scan()
	case "snapshot":
		if len(os.Args) < 3 {
			printSnapshotUsage()
			os.Exit(1)
		}
		cmd.Snapshot(os.Args[2:])
	case "version":
		fmt.Println("ChronoVault Agent v0.1.0")
	default:
		printUsage()
		os.Exit(1)
	}
}

func printUsage() {
	fmt.Println("ChronoVault Agent - Smart Server Time Machine")
	fmt.Println("")
	fmt.Println("Usage:")
	fmt.Println("  chronovault-agent run                    Start the agent daemon")
	fmt.Println("  chronovault-agent register               Register with ChronoVault server")
	fmt.Println("  chronovault-agent scan                   Scan local environment")
	fmt.Println("  chronovault-agent snapshot               Manage snapshots (list/create/rollback/diff)")
	fmt.Println("  chronovault-agent version                Show version")
}

func printSnapshotUsage() {
	fmt.Println("ChronoVault Snapshot Management")
	fmt.Println("")
	fmt.Println("Usage:")
	fmt.Println("  chronovault-agent snapshot list                           List all snapshots")
	fmt.Println("  chronovault-agent snapshot create --server-url <url>      Create a new snapshot")
	fmt.Println("  chronovault-agent snapshot rollback --id <id>             Rollback to a snapshot")
	fmt.Println("  chronovault-agent snapshot diff --id1 <id> --id2 <id>    Compare two snapshots")
}

