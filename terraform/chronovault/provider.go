package chronovault

import (
	"context"
	"net/http"
	"time"

	"github.com/hashicorp/terraform-plugin-framework/datasource"
	"github.com/hashicorp/terraform-plugin-framework/provider"
	"github.com/hashicorp/terraform-plugin-framework/provider/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource"
)

// ChronoVaultProvider defines the provider implementation.
type ChronoVaultProvider struct {
	version string
}

// ChronoVaultProviderModel describes the provider data model.
type ChronoVaultProviderModel struct {
	Host  string `tfsdk:"host"`
	Token string `tfsdk:"token"`
}

// New returns a new provider instance.
func New(version string) func() provider.Provider {
	return func() provider.Provider {
		return &ChronoVaultProvider{
			version: version,
		}
	}
}

// Metadata returns the provider type name.
func (p *ChronoVaultProvider) Metadata(_ context.Context, _ provider.MetadataRequest, resp *provider.MetadataResponse) {
	resp.TypeName = "chronovault"
	resp.Version = p.version
}

// Schema defines the provider-level schema.
func (p *ChronoVaultProvider) Schema(_ context.Context, _ provider.SchemaRequest, resp *provider.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "ChronoVault Terraform provider — manage servers and snapshot policies via Infrastructure as Code.",
		Attributes: map[string]schema.Attribute{
			"host": schema.StringAttribute{
				MarkdownDescription: "ChronoVault server URL (e.g. https://chronovault.example.com). Also set via `CHRONOVAULT_HOST` env var.",
				Required:            true,
			},
			"token": schema.StringAttribute{
				MarkdownDescription: "ChronoVault API token for authentication. Also set via `CHRONOVAULT_TOKEN` env var.",
				Required:            true,
				Sensitive:           true,
			},
		},
	}
}

// Configure prepares a ChronoVault API client.
func (p *ChronoVaultProvider) Configure(ctx context.Context, req provider.ConfigureRequest, resp *provider.ConfigureResponse) {
	var data ChronoVaultProviderModel
	resp.Diagnostics.Append(req.Config.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	if data.Host.IsNull() || data.Host.IsUnknown() {
		resp.Diagnostics.AddError("Missing host", "Set the ChronoVault host URL.")
		return
	}
	if data.Token.IsNull() || data.Token.IsUnknown() {
		resp.Diagnostics.AddError("Missing token", "Set the ChronoVault API token.")
		return
	}

	client := &http.Client{Timeout: 30 * time.Second}
	apiClient := &ApiClient{
		Host:  data.Host.ValueString(),
		Token: data.Token.ValueString(),
	 HTTP:  client,
	}

	// Make the API client available during DataSource and Resource type Configure methods
	resp.DataSourceData = apiClient
	resp.ResourceData = apiClient
}

// Resources defines the resources available in this provider.
func (p *ChronoVaultProvider) Resources(_ context.Context) []func() resource.Resource {
	return []func() resource.Resource{
		NewServerResource,
		NewSnapshotPolicyResource,
	}
}

// DataSources defines the data sources available in this provider.
func (p *ChronoVaultProvider) DataSources(_ context.Context) []func() datasource.DataSource {
	return nil
}
