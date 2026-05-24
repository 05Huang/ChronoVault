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
	fmt.Println("  chronovault-agent version                Show version")
}

