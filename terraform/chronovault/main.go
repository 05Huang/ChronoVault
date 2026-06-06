package main

import (
	"context"
	"log"

	"github.com/chronovault/terraform-provider-chronovault/chronovault"
	"github.com/hashicorp/terraform-plugin-framework/providerserver"
)

var version = "0.1.0"

func main() {
	opts := providerserver.ServeOpts{
		Address: "registry.terraform.io/chronovault/chronovault",
	}
	if err := providerserver.Serve(context.Background(), chronovault.New(version), opts); err != nil {
		log.Fatal("Error starting provider: ", err)
	}
}
