module post;

struct AssembledVertex {
    float4 position : SV_Position;
    float2 uv;
}

[shader("mesh")]
[numthreads(1, 1, 1)]
[outputtopology("triangle")]
void mesh_main(
    uint3 dispatch_id: SV_DispatchThreadID,
    OutputVertices<AssembledVertex, 4> vertices,
    OutputIndices<uint3, 2> primitives
) {
    SetMeshOutputCounts(4, 2);

    float2 positions[4] = {
        float2(-1.0, 1.0),
        float2(-1.0, -1.0),
        float2(1.0, -1.0),
        float2(1.0, 1.0)
    };

    float2 uvs[4] = {
        float2(0.0, 1.0),
        float2(0.0, 0.0),
        float2(1.0, 0.0),
        float2(1.0, 1.0)
    };

    [unroll]
    for (uint i = 0; i < 4; i++) {
        vertices[i].position = float4(positions[i], 0.0, 1.0);
        vertices[i].uv = uvs[i];
    }

    primitives[0] = uint3(0, 1, 2);
    primitives[1] = uint3(0, 2, 3);
}

struct ViewData {
    float4x4 view_projection;
    float4x4 view;
    float4x4 projection;
    float4x4 inverse_view;
    float4x4 inverse_projection;
    float4 camera_position;
}

struct DrawData {
    ViewData *view_data;
}

[[vk::binding(0, 0)]]
ConstantBuffer<DrawData> post_data;

[[vk::binding(1, 0)]]
Sampler2D gbuffer_processed_color;

[[vk::binding(2, 0)]]
Sampler2D gbuffer_processed_depth;

float get_linear_depth(float2 uv) {
    float2 nuv = uv;
    nuv.y = 1 - nuv.y;
    float depth = gbuffer_processed_depth.Sample(nuv).r;
    float4 view_space_pos = mul(post_data.view_data->inverse_projection, float4(nuv * 2.0 - 1.0, depth, 1.0));
    return view_space_pos.z / view_space_pos.w;
}

[shader("fragment")]
float4 fragment_main(AssembledVertex vertex) : SV_Target0 {
    return float4(gbuffer_processed_color.Sample(vertex.uv).rgb, 1.0);
}