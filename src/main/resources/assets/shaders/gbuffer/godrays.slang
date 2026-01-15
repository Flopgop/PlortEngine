implementing gbuffer;

__include common;
__include shadows;

static const int NUM_SAMPLES = 25;

float get_noise(float2 uv) {
    float2 noise_uv = uv * (float2(meta_data.screen_size) / 64.0);
    return noise_texture.SampleLevel(noise_uv, 0).r;
}

float compute_phase(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * 3.14159 * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5));
}

float4 sun_light_shafts(ViewData view_data, SceneData* scene_data, Sampler2D scene_shadow_map, Sampler2D player_shadow_map, ShadowData* player_shadow_data, float2 uv, float3 pixel_world_pos) {
    float3 ray_start = view_data.camera_position.xyz;
    float3 ray_end = pixel_world_pos;

    float3 ray_vec = ray_end - ray_start;
    float ray_len = length(ray_vec);
    float3 ray_dir = ray_vec / ray_len;

    float3 sun_dir = normalize(-scene_data->sun_dir);
    float cosTheta = dot(ray_dir, sun_dir);
    float phase = compute_phase(cosTheta, 0.8);

    float max_dist = min(ray_len, 50.0);
    float step_size = max_dist / float(NUM_SAMPLES);

    float jitter = get_noise(uv);
    float3 current_pos = ray_start + ray_dir * (step_size * jitter);

    float accumulation = 0.0;

    [unroll]
    for (int i = 0; i < NUM_SAMPLES; i++) {
        float shadow_term = compute_shadow(scene_shadow_map, player_shadow_map, scene_data->shadow, player_shadow_data, current_pos);

        if (shadow_term > 0.0) {
            float distance_fade = saturate(length(current_pos - ray_start) / 2.0);
            accumulation += shadow_term * distance_fade;
        }

        current_pos += ray_dir * step_size;
    }

    accumulation /= float(NUM_SAMPLES);
    float3 final_color = accumulation * scene_data->sun_color * scene_data->sun_intensity * phase;

    return float4(final_color, accumulation);
}