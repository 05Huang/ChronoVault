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
	_ resource.Resource                = &SnapshotPolicyResource{}
	_ resource.ResourceWithImportState = &SnapshotPolicyResource{}
)

// SnapshotPolicyResource manages a ChronoVault snapshot retention policy.
type SnapshotPolicyResource struct {
	client *ApiClient
}

// SnapshotPolicyResourceModel describes the resource data model.
type SnapshotPolicyResourceModel struct {
	ID           types.String `tfsdk:"id"`
	ServerID     types.Int64  `tfsdk:"server_id"`
	Name         types.String `tfsdk:"name"`
	MaxCount     types.Int64  `tfsdk:"max_count"`
	MaxAgeDays   types.Int64  `tfsdk:"max_age_days"`
	MinKeepDays  types.Int64  `tfsdk:"min_keep_days"`
	Enabled      types.Bool   `tfsdk:"enabled"`
	DeletedCount types.Int64  `tfsdk:"deleted_count"`
}

func NewSnapshotPolicyResource() resource.Resource {
	return &SnapshotPolicyResource{}
}

func (r *SnapshotPolicyResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_snapshot_policy"
}

func (r *SnapshotPolicyResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Manages a ChronoVault snapshot retention policy — controls how many snapshots to keep and for how long.",

		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "Policy ID (assigned by ChronoVault).",
			},
			"server_id": schema.Int64Attribute{
				Required:            true,
				MarkdownDescription: "ID of the server this policy applies to.",
			},
			"name": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "Policy name (e.g. 'production-daily', 'staging-weekly').",
			},
			"max_count": schema.Int64Attribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "Maximum number of snapshots to retain. Null means no count limit.",
			},
			"max_age_days": schema.Int64Attribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "Maximum age of snapshots in days. Null means no age limit.",
			},
			"min_keep_days": schema.Int64Attribute{
				Required:            true,
				MarkdownDescription: "Minimum number of days to keep snapshots (protection period). Snapshots within this window are never deleted. Default: 7.",
			},
			"enabled": schema.BoolAttribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "Whether this policy is active. Default: true.",
			},
			"deleted_count": schema.Int64Attribute{
				Computed:            true,
				MarkdownDescription: "Total number of snapshots deleted by this policy.",
			},
		},
	}
}

func (r *SnapshotPolicyResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *SnapshotPolicyResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var data SnapshotPolicyResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	var maxCount, maxAgeDays, minKeepDays *int
	if !data.MaxCount.IsNull() {
		v := int(data.MaxCount.ValueInt64())
		maxCount = &v
	}
	if !data.MaxAgeDays.IsNull() {
		v := int(data.MaxAgeDays.ValueInt64())
		maxAgeDays = &v
	}
	v := int(data.MinKeepDays.ValueInt64())
	minKeepDays = &v

	result, err := r.client.CreateSnapshotPolicy(
		int(data.ServerID.ValueInt64()),
		data.Name.ValueString(),
		maxCount, maxAgeDays, minKeepDays,
	)
	if err != nil {
		resp.Diagnostics.AddError("Create snapshot policy failed", err.Error())
		return
	}

	data.ID = types.StringValue(fmt.Sprintf("%v", result["id"]))
	data.DeletedCount = types.Int64(0)
	if dc, ok := result["deletedCount"]; ok {
		data.DeletedCount = types.Int64(int64(toInt(dc)))
	}
	data.Enabled = types.BoolValue(true)

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

func (r *SnapshotPolicyResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var data SnapshotPolicyResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	result, err := r.client.GetSnapshotPolicy(data.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Read snapshot policy failed", err.Error())
		return
	}
	if result == nil {
		resp.State.RemoveResource(ctx)
		return
	}

	data.Name = types.StringValue(fmt.Sprintf("%v", result["name"]))
	if sid, ok := result["serverId"]; ok {
		data.ServerID = types.Int64(int64(toInt(sid)))
	}
	if mc, ok := result["maxCount"]; ok && mc != nil {
		data.MaxCount = types.Int64(int64(toInt(mc)))
	} else {
		data.MaxCount = types.Int64Null()
	}
	if mad, ok := result["maxAgeDays"]; ok && mad != nil {
		data.MaxAgeDays = types.Int64(int64(toInt(mad)))
	} else {
		data.MaxAgeDays = types.Int64Null()
	}
	if mkd, ok := result["minKeepDays"]; ok {
		data.MinKeepDays = types.Int64(int64(toInt(mkd)))
	}
	if en, ok := result["enabled"].(bool); ok {
		data.Enabled = types.BoolValue(en)
	}
	if dc, ok := result["deletedCount"]; ok {
		data.DeletedCount = types.Int64(int64(toInt(dc)))
	}

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

func (r *SnapshotPolicyResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var data SnapshotPolicyResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	var maxCount, maxAgeDays, minKeepDays *int
	if !data.MaxCount.IsNull() {
		v := int(data.MaxCount.ValueInt64())
		maxCount = &v
	}
	if !data.MaxAgeDays.IsNull() {
		v := int(data.MaxAgeDays.ValueInt64())
		maxAgeDays = &v
	}
	v := int(data.MinKeepDays.ValueInt64())
	minKeepDays = &v

	_, err := r.client.UpdateSnapshotPolicy(
		data.ID.ValueString(),
		data.Name.ValueString(),
		maxCount, maxAgeDays, minKeepDays,
		data.Enabled.ValueBool(),
	)
	if err != nil {
		resp.Diagnostics.AddError("Update snapshot policy failed", err.Error())
		return
	}

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

func (r *SnapshotPolicyResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var data SnapshotPolicyResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	err := r.client.DeleteSnapshotPolicy(data.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Delete snapshot policy failed", err.Error())
	}
}

func (r *SnapshotPolicyResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	id := req.ID
	var data SnapshotPolicyResourceModel
	data.ID = types.StringValue(id)

	result, err := r.client.GetSnapshotPolicy(id)
	if err != nil {
		resp.Diagnostics.AddError("Import failed", err.Error())
		return
	}
	if result == nil {
		resp.Diagnostics.AddError("Import failed", fmt.Sprintf("Policy %s not found", id))
		return
	}

	data.Name = types.StringValue(fmt.Sprintf("%v", result["name"]))
	if sid, ok := result["serverId"]; ok {
		data.ServerID = types.Int64(int64(toInt(sid)))
	}
	if mc, ok := result["maxCount"]; ok && mc != nil {
		data.MaxCount = types.Int64(int64(toInt(mc)))
	} else {
		data.MaxCount = types.Int64Null()
	}
	if mad, ok := result["maxAgeDays"]; ok && mad != nil {
		data.MaxAgeDays = types.Int64(int64(toInt(mad)))
	} else {
		data.MaxAgeDays = types.Int64Null()
	}
	if mkd, ok := result["minKeepDays"]; ok {
		data.MinKeepDays = types.Int64(int64(toInt(mkd)))
	}
	if en, ok := result["enabled"].(bool); ok {
		data.Enabled = types.BoolValue(en)
	}
	if dc, ok := result["deletedCount"]; ok {
		data.DeletedCount = types.Int64(int64(toInt(dc)))
	}

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

// Helper to convert interface{} to int (duplicated from resource_server.go for isolation)
func toIntPolicy(v interface{}) int {
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
