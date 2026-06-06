package chronovault

import (
	"context"
	"fmt"
	"strconv"

	"github.com/hashicorp/terraform-plugin-framework/path"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

// Ensure the implementation satisfies the expected interfaces.
var (
	_ resource.Resource                = &ServerResource{}
	_ resource.ResourceWithImportState = &ServerResource{}
)

// ServerResource manages a ChronoVault server registration.
type ServerResource struct {
	client *ApiClient
}

// ServerResourceModel describes the resource data model.
type ServerResourceModel struct {
	ID     types.String `tfsdk:"id"`
	Name   types.String `tfsdk:"name"`
	IP     types.String `tfsdk:"ip"`
	OS     types.String `tfsdk:"os"`
	Status types.String `tfsdk:"status"`
}

func NewServerResource() resource.Resource {
	return &ServerResource{}
}

func (r *ServerResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_server"
}

func (r *ServerResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Manages a ChronoVault server registration.",

		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "Server ID (assigned by ChronoVault).",
			},
			"name": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "Display name for the server.",
			},
			"ip": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "Server IP address or hostname.",
			},
			"os": schema.StringAttribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "Operating system (e.g. Ubuntu, CentOS).",
			},
			"status": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "Current server status (RUNNING, STOPPED, ERROR).",
			},
		},
	}
}

func (r *ServerResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
	if req.ProviderData == nil {
		return
	}
	apiClient, ok := req.ProviderData.(*ApiClient)
	if !ok {
		resp.Diagnostics.AddError("Unexpected ProviderData", "Expected *ApiClient")
		return
	}
	r.client = apiClient
}

func (r *ServerResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var data ServerResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	result, err := r.client.CreateServer(
		data.Name.ValueString(),
		data.IP.ValueString(),
		data.OS.ValueString(),
	)
	if err != nil {
		resp.Diagnostics.AddError("Create server failed", err.Error())
		return
	}

	data.ID = types.StringValue(fmt.Sprintf("%v", result["id"]))
	data.Status = types.StringValue("RUNNING")
	if os, ok := result["os"].(string); ok && os != "" {
		data.OS = types.StringValue(os)
	}
	if status, ok := result["status"].(string); ok && status != "" {
		data.Status = types.StringValue(status)
	}

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

func (r *ServerResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var data ServerResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	result, err := r.client.GetServer(data.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Read server failed", err.Error())
		return
	}
	if result == nil {
		resp.State.RemoveResource(ctx)
		return
	}

	data.Name = types.StringValue(fmt.Sprintf("%v", result["name"]))
	data.IP = types.StringValue(fmt.Sprintf("%v", result["ip"]))
	if os, ok := result["os"].(string); ok {
		data.OS = types.StringValue(os)
	}
	if status, ok := result["status"].(string); ok {
		data.Status = types.StringValue(status)
	}

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

func (r *ServerResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var data ServerResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	_, err := r.client.UpdateServer(data.ID.ValueString(), data.Name.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Update server failed", err.Error())
		return
	}

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

func (r *ServerResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var data ServerResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	err := r.client.DeleteServer(data.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Delete server failed", err.Error())
	}
}

func (r *ServerResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	id := req.ID
	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("id"), id)...)
	// Trigger a Read to populate other attributes
	var data ServerResourceModel
	data.ID = types.StringValue(id)
	result, err := r.client.GetServer(id)
	if err != nil {
		resp.Diagnostics.AddError("Import failed", err.Error())
		return
	}
	if result == nil {
		resp.Diagnostics.AddError("Import failed", fmt.Sprintf("Server %s not found", id))
		return
	}
	data.Name = types.StringValue(fmt.Sprintf("%v", result["name"]))
	data.IP = types.StringValue(fmt.Sprintf("%v", result["ip"]))
	if os, ok := result["os"].(string); ok {
		data.OS = types.StringValue(os)
	}
	if status, ok := result["status"].(string); ok {
		data.Status = types.StringValue(status)
	}
	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

// Helper to convert interface{} to int
func toInt(v interface{}) int {
	switch val := v.(type) {
	case float64:
		return int(val)
	case int:
		return val
	case string:
		i, _ := strconv.Atoi(val)
		return i
	default:
		return 0
	}
}
