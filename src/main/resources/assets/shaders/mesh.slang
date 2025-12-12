
#define MAX_VERTICES_PER_MESHLET 64
#define MAX_TRIANGLES_PER_MESHLET 96

struct Vertex {
    float3 position;      // 12 bytes
    float3 normal;        // 12 bytes
    float4 tangent;       // 16 bytes  
    float2 uv;            // 8 bytes
    // size = 48    bytes % 16 == 0
}

struct Meshlet {
    uint vertices[MAX_VERTICES_PER_MESHLET];        // 4 bytes * MAX_VERTICES_PER_MESHLET
    uint indices[3 * MAX_TRIANGLES_PER_MESHLET];    // 4 bytes * 3 * MAX_TRIANGLES_PER_MESHLET
    uint vertex_count;                              // 4 bytes
    uint index_count;                               // 4 bytes
    uint _pad0[2];                                  // 8 bytes padding
    // size = ?                               bytes % 16 == 0
}

struct MeshletBounds {
    float3 center;
    float radius;
    float3 cone_apex;
    float cone_cutoff;
    float3 cone_axis;
    float _pad0[1];
}

struct ViewData {
    float4x4 view_projection;
    float4x4 view;
    float4x4 projection;
    float4x4 inverse_view;
    float4x4 inverse_projection;
    float4 camera_position;
}

struct Instance {
    float4x4 model;
    float4x4 inverse_model;
}

struct AssembledVertex {
    float4 clip_position : SV_Position;
    float3 view_position;
    float3 normal, tangent, bitangent;
    float2 uv;
    float mesh_index;
}

[[vk::binding(0, 0)]]
ConstantBuffer<ViewData> view_data; // per frame

[[vk::binding(1, 0)]]
Sampler2D albedo; // per material

[[vk::binding(2, 0)]]
Sampler2D normal_map;

struct DrawData {
    Vertex* vertices;
    Meshlet* meshlets;
    MeshletBounds* meshlet_bounds;
    Instance *instances;
}

[shader("mesh")]
[numthreads(1, 1, 1)]
[outputtopology("triangle")]
[require(spirv_1_6, spvMeshShadingEXT)]
void meshMain(
    uniform DrawData draw_data, // push constant, offset = 0

    uint3 dispatch_id: SV_DispatchThreadID,
    OutputVertices<AssembledVertex, MAX_VERTICES_PER_MESHLET> verts,
    OutputIndices<uint3, MAX_TRIANGLES_PER_MESHLET> prims
) {
    uint mesh_index = dispatch_id.x;
    uint instance_index = dispatch_id.y;

    let meshlets = &draw_data.meshlets[0];
    let instances = &draw_data.instances[0];
    let vertices = &draw_data.vertices[0];

    let me = meshlets + mesh_index;
    let transform = instances + instance_index;

    SetMeshOutputCounts(me.vertex_count, me.index_count / 3);

    for (uint i = 0; i < me.vertex_count; ++i) {
        uint vi = me.vertices[i];
        let vertex = vertices + vi;

        float3 pos = vertex.position;

        float4 Pw = mul(transform.model, float4(pos, 1.0));
        float4 Pv = mul(view_data.view, Pw);
        float4 P = mul(view_data.projection, Pv);

        float3 normal = normalize(mul(transform.model, float4(vertex.normal, 0.0)).xyz);
        float3 tangent = normalize(mul(transform.model, float4(vertex.tangent.xyz, 0.0)).xyz);
        tangent = normalize(tangent - dot(tangent, normal) * normal); // reorthogonalize
        float3 bitangent = normalize(cross(normal, tangent) * vertex.tangent.w);

        verts[i] = {
            P, Pv.xyz, normal, tangent, bitangent, vertex.uv, float(mesh_index)
        };
    }

    for (uint i = 0; i < me.index_count / 3; ++i) {
        prims[i] = uint3(
            me.indices[3 * i + 0],
            me.indices[3 * i + 1],
            me.indices[3 * i + 2]
        );
    }
}

struct ComputedFragment {
    float4 g_position : SV_Target0;
    float4 g_normal : SV_Target1;
    float4 g_albedo : SV_Target2;
}

#define MAX_COLORS 10
static const float3 meshlet_colors[MAX_COLORS] = {
    float3(1, 0, 0),
    float3(0, 1, 0),
    float3(0, 0, 1),
    float3(1, 1, 0),
    float3(1, 0, 1),
    float3(0, 1, 1),
    float3(1, 0.5, 0),
    float3(0.5, 1, 0),
    float3(0, 0.5, 1),
    float3(1, 1, 1)
};

[shader("fragment")]
ComputedFragment fragmentMain(
    AssembledVertex vertex
) {
    float2 uv = vertex.uv.xy;

    float4 sampledColor = albedo.Sample(uv);
    float3 sampledNormal = normal_map.Sample(uv).xyz * 2 - 1;

    float3x3 tbn = float3x3(vertex.tangent, vertex.bitangent, vertex.normal);

    float3 normal = normalize(mul(sampledNormal, tbn));

    ComputedFragment frag = {
        float4(vertex.view_position, 1),
        float4(normalize(mul(float3x3(view_data.view), normal)), 1),
        sampledColor//float4(meshlet_colors[int(vertex.mesh_index) % MAX_COLORS], 1.0)
    };

    return frag;
}