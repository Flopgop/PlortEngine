
struct AssembledVertex {
    float4 position : SV_Position;
    float2 uv;
}

[shader("mesh")]
[numthreads(1,1,1)]
[outputtopology("triangle")]
void meshMain(
    uint3 dispatch_id: SV_DispatchThreadID,
    OutputVertices<AssembledVertex, 4> vertices,
    OutputIndices<uint3, 2> primitives
) {
    SetMeshOutputCounts(4, 2);

    float2 positions[4] = {
        float2(-1.0, -1.0),
        float2(-1.0, 1.0), 
        float2(1.0, 1.0),  
        float2(1.0, -1.0)  
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

[[vk::binding(0, 0)]]
Sampler2D g_position;

[[vk::binding(1, 0)]]
Sampler2D g_normal;

[[vk::binding(2, 0)]]
Sampler2D g_albedo;

[[vk::binding(3, 0)]]
Sampler2D g_depth;

[[vk::binding(4, 0)]]
Sampler2D noise_texture;

[[vk::binding(5, 0)]]
Sampler2D ssao;

[[vk::binding(6, 0)]]
ConstantBuffer<ViewData> view_data;

[[vk::binding(7, 0)]]
ConstantBuffer<MetaData> meta_data;

float3 computeLighting(float3 position, float3 normal, float3 albedo) {
    float3 light_dir = normalize(float3(1.0));
    float3 view_dir = normalize(-position); // position is in view space

    // Ambient
    float3 ambient = 0.05 * albedo;

    // Diffuse
    float diff = max(dot(normal, light_dir), 0.0);
    float3 diffuse = diff * albedo;

    // Specular (classic Phong reflection model)
    float3 reflect_dir = reflect(-light_dir, normal);
    float spec = pow(max(dot(view_dir, reflect_dir), 0.0), 32.0); // shininess 32
    float3 specular = spec * float3(1.0, 1.0, 1.0);

    return ambient + diffuse + specular;
}

[shader("fragment")]
float4 fragmentMain(AssembledVertex vertex) : SV_Target0 {
    float2 uv = vertex.uv.xy;

    float4 position = g_position.Sample(uv);
    if (position.a <= 0.1) discard;
    float3 normal = normalize(g_normal.Sample(uv).xyz * 0.5 + 0.5);
    float3 albedo = g_albedo.Sample(uv).rgb;

    return float4(float3(albedo * ssao.Sample(uv).r), 1.0);
}