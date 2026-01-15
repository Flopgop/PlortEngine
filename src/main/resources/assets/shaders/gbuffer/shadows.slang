implementing gbuffer;

__include common;

float2 sample_shadow_map(float3 world_pos, Sampler2D map, float4x4 view_proj) {
    float4 clip = mul(view_proj, float4(world_pos, 1.0));
    float2 uv = clip.xy * 0.5 + 0.5;
    float depth = clip.z / clip.w;

    if (uv.x < 0 || uv.x > 1 || uv.y < 0 || uv.y > 1)
        return float2(depth, 1.0);

    return float2(depth, map.Sample(uv).r);
}

float compute_shadow(Sampler2D scene_shadows, Sampler2D player_shadows, ShadowData *scene_shadow_data, ShadowData *player_shadow_data, float3 world_pos) {
    float2 scene_depth = sample_shadow_map(world_pos, scene_shadows, scene_shadow_data->view_projection);
    float2 player_depth = sample_shadow_map(world_pos, player_shadows, player_shadow_data->view_projection);

    float scene_shadow_factor = scene_depth.x > scene_depth.y + 0.001 ? 0.0 : 1.0;
    float player_shadow_factor = player_depth.x > player_depth.y + 0.001 ? 0.0 : 1.0;

    return min(player_shadow_factor, scene_shadow_factor);
}