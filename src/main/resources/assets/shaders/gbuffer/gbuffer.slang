
module gbuffer;

__include common;
__include lighting;
__include shadows;
__include godrays;
__include ssr;

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
Sampler2D shadow;

[[vk::binding(7, 0)]]
Sampler2D player_shadow;

[[vk::binding(8, 0)]]
ConstantBuffer<ViewData> view_data;

[[vk::binding(9, 0)]]
ConstantBuffer<MetaData> meta_data;

[[vk::binding(10, 0)]]
ConstantBuffer<SceneData*> scene_data;

[[vk::binding(11, 0)]]
ConstantBuffer<ShadowData*> player_shadow_data;

[shader("fragment")]
float4 fragmentMain(AssembledVertex vertex) : SV_Target0 {
    float2 uv = vertex.uv.xy;
    
    float4 position = g_position.Sample(uv);

    float3 world_pos;
    if (position.a <= 0.1) {
        float3 view_dir = normalize(mul(view_data.inverse_projection, float4(uv * 2.0 - 1.0, 1.0, 1.0)).xyz);
        view_dir = mul((float3x3)view_data.inverse_view, view_dir);
        world_pos = view_data.camera_position.xyz + view_dir * 100.0;
    } else {
        world_pos = mul(view_data.inverse_view, float4(position.xyz, 1.0)).xyz;
    }

    float4 godrays = sun_light_shafts(view_data, scene_data, shadow, player_shadow, player_shadow_data, uv, world_pos);

    if (position.a <= 0.1) {
        return float4(godrays.xyz, 1.0);
    }

    float3 normal = normalize(g_normal.Sample(uv).xyz);
    float3 albedo = g_albedo.Sample(uv).rgb;

    float shadow = compute_shadow(shadow, player_shadow, scene_data->shadow, player_shadow_data, world_pos);

    float3 lit = shadow * compute_lighting(scene_data, view_data, position.xyz, normal, albedo, ssao.Sample(uv).r);

    // float3 reflections = compute_ssr(position.xyz, normal, uv);
    // lit += reflections * pow(1.0 - max(dot(normal, normalize(position.xyz)), 0.0), 5.0);

    float fog_factor = saturate(1.0 - exp(-scene_data->fog_density * length(position.xyz)));
    float3 lit_fogged = lerp(lit, scene_data->fog_color, fog_factor);
    return float4(lit_fogged + godrays.xyz, 1.0);
}