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

struct MetaData {
    uint2 screen_size;
}

struct DrawData {
    ViewData *view_data;
    MetaData *meta_data;
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

static const int bayer[16] = {
    0, 8, 2, 10,
    12, 4, 14, 6,
    3, 11, 1, 9,
    15, 7, 13, 5
};

[shader("fragment")]
float4 fragment_main(AssembledVertex vertex) : SV_Target0 {
    float3 color = gbuffer_processed_color.Sample(vertex.uv).rgb;

    int2 p = int2(vertex.uv * post_data.meta_data->screen_size);
    int d = bayer[(p.x & 3) + ((p.y & 3) << 2)];

    float dither = (float(d) + 0.5) / 16.0 - 0.5;
    float levels = 255.0;

    color += dither / levels;
    color = floor(color * levels) / levels;

    return float4(color, 1.0);
}