implementing gbuffer;

__include common;

static const int SSR_MAX_STEPS = 64;
static const float SSR_STEP_SIZE = 0.2;
static const float SSR_THICKNESS = 0.25;

float3 binary_search_ssr(Sampler2D g_position, float3 ray_dir, float3 hit_pos) {
    float3 current_pos = hit_pos;
    float3 last_pos = hit_pos;
    float3 step = ray_dir * SSR_STEP_SIZE;

    for (int i = 0; i < 5; i++) {
        step *= 0.5;

        float4 projected = mul(view_data.projection, float4(current_pos, 1.0));
        float2 sample_uv = (projected.xy / projected.w) * 0.5 + 0.5;

        float scene_z = g_position.SampleLevel(sample_uv, 0).z;

        if (current_pos.z < scene_z) {
            last_pos = current_pos;
            current_pos -= step;
        } else {
            current_pos += step;
        }
    }
    return last_pos;
}

float3 compute_ssr(Sampler2D g_albedo, Sampler2D g_position, ViewData view_data, float3 pos_vs, float3 normal_vs, float2 uv) {
    float3 view_dir = normalize(pos_vs);
    float3 reflect_dir = reflect(view_dir, normal_vs);

    float jitter = get_noise(uv);
    float3 ray_pos = pos_vs + reflect_dir * (SSR_STEP_SIZE * jitter);

    for (int i = 0; i < SSR_MAX_STEPS; i++) {
        ray_pos += reflect_dir * SSR_STEP_SIZE;

        float4 projected = mul(view_data.projection, float4(ray_pos, 1.0));
        float2 sample_uv = (projected.xy / projected.w) * 0.5 + 0.5;

        if (any(sample_uv < 0.0) || any(sample_uv > 1.0) || ray_pos.z > 0.0)
            break;

        float scene_z = g_position.SampleLevel(sample_uv, 0).z;

        if (ray_pos.z < scene_z && ray_pos.z > scene_z - SSR_THICKNESS) {
            float3 refined_pos = binary_search_ssr(g_position, reflect_dir, ray_pos);
            float4 final_projected = mul(view_data.projection, float4(refined_pos, 1.0));
            float2 final_uv = (final_projected.xy / final_projected.w) * 0.5 + 0.5;

            float3 reflected_color = g_albedo.SampleLevel(final_uv, 0).rgb;
            float2 edge_fade = smoothstep(0.0, 0.1, final_uv) * smoothstep(1.0, 0.9, final_uv);
            float dist_fade = saturate(1.0 - length(refined_pos - pos_vs) / 20.0);

            return reflected_color * edge_fade.x * edge_fade.y * dist_fade;
        }
    }

    return 0.0;
}