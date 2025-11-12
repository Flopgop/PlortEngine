
#define MAX_VERTICES_PER_MESHLET 64
#define MAX_TRIANGLES_PER_MESHLET 96

struct Vertex {
    float3 position;      // 12 bytes
    float _pad0[1];       // 4 bytes
    float3 normal;        // 12 bytes
    float _pad1[1];       // 4 bytes
    float2 uv;            // 8 bytes
    float _pad2[2];       // 8 bytes
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
    float4 camera_position;
}

struct Transform {
    float4x4 model;
    float4x4 inverse_model;
}

struct TaskData {
    uint mesh_index;
    uint instance_index;
}

struct AssembledVertex {
    float4 position : SV_Position;
    float3 normal : Normal;
    float2 uv : TexCoord;
    float mesh_index : Index;
};

#define MAX_COLORS 10
static float3 meshletcolors[MAX_COLORS] = {
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

[[vk::binding(0, 0)]]
StructuredBuffer<Vertex> vertex_data;

[[vk::binding(1, 0)]]
StructuredBuffer<Meshlet> meshlet_data;

[[vk::binding(2, 0)]]
StructuredBuffer<MeshletBounds> bounds_data;

[[vk::binding(3, 0)]]
StructuredBuffer<Transform> instance_data;

[[vk::binding(4, 0)]]
ConstantBuffer<ViewData> view_data;

[[vk::binding(5, 0)]]
Sampler2D albedo;

bool SphereInFrustum(float4 clip_pos, float radius) {
    float w = clip_pos.w;

    if (clip_pos.x + radius < -w || clip_pos.x - radius > w) return false;
    if (clip_pos.y + radius < -w || clip_pos.y - radius > w) return false;
    if (clip_pos.z + radius < 0. || clip_pos.z - radius > w) return false;

    return true;
}

bool ConeVisible(float3 cone_apex, float3 cone_axis, float cone_cutoff, float3 camera_position) {
    return dot(normalize(cone_apex - camera_position), cone_axis) < cone_cutoff;
}

[shader("amplification")]
[numthreads(1, 1, 1)]
void taskMain(
    uint3 dispatchID: SV_DispatchThreadID,
) {
    bool draw = true;
    uint mesh_index = dispatchID.x;
    uint instance_index = dispatchID.y;

    // float4x4 view_proj = view_data.view_projection;
    // float3 cam_pos = view_data.camera_position.xyz;

    // MeshletBounds bounds = bounds_data[mesh_index];
    // Transform transform = instance_data[instance_index];

    // float4 world_center = mul(transform.model, float4(bounds.center, 1.0));
    // float max_scale = max(max(length(transform.model[0].xyz), length(transform.model[1].xyz)), length(transform.model[2].xyz));
    // float radius_world = bounds.radius * max_scale;

    // float4 clip_center = mul(view_proj, world_center);

    // float3 os_cam_pos = mul(transform.inverse_model, float4(cam_pos, 1.0)).xyz;

    // draw = SphereInFrustum(clip_center, radius_world) && ConeVisible(bounds.cone_apex, normalize(bounds.cone_axis), bounds.cone_cutoff, os_cam_pos);
    
    TaskData payload = { mesh_index, instance_index };
    DispatchMesh(WaveActiveCountBits(draw), 1, 1, payload);
}

[shader("mesh")]
[numthreads(1, 1, 1)]
[outputtopology("triangle")]
void meshMain(
    in payload TaskData payload,
    OutputVertices<AssembledVertex, MAX_VERTICES_PER_MESHLET> verts,
    OutputIndices<uint3, MAX_TRIANGLES_PER_MESHLET> prims
) {
    uint mesh_index = payload.mesh_index;
    uint instance_index = payload.instance_index;

    Meshlet me = meshlet_data[mesh_index];
    Transform transform = instance_data[instance_index];

    SetMeshOutputCounts(me.vertex_count, me.index_count / 3);

    for (uint i = 0; i < me.vertex_count; ++i) {
        uint vi = me.vertices[i];
        Vertex vertex = vertex_data[vi];

        float3 pos = vertex.position;

        float4 Pw = mul(transform.model, float4(pos, 1.0));
        float4 P = mul(view_data.view_projection, Pw);
        
        P.y = -P.y; P.z = (P.z + P.w) / 2.0;
        AssembledVertex assembled;
        assembled.position = P;
        assembled.normal = vertex.normal;
        assembled.uv = vertex.uv;
        assembled.mesh_index = float(mesh_index);
        verts[i] = assembled;
    }

    for (uint i = 0; i < me.index_count / 3; ++i) {
        prims[i] = uint3(
            me.indices[3 * i + 0],
            me.indices[3 * i + 1],
            me.indices[3 * i + 2]
        );
    }
}

[earlydepthstencil]
[shader("fragment")]
float4 fragmentMain(
    AssembledVertex vertex
) : SV_Target0 {
    float3 meshletColor = meshletcolors[int(vertex.mesh_index) % MAX_COLORS];
    float2 uv = vertex.uv.yx;

    float4 sampledColor = albedo.Sample(uv);

    if (sampledColor.a < 0.1)
        discard;

    return float4(lerp(sampledColor.xyz, vertex.normal.xyz, 0.5), 1.0);
}