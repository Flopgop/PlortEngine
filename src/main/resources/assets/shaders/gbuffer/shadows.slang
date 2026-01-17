implementing gbuffer;

__include common;

static const int PCF_SAMPLES = 32;
static const float FILTER_RADIUS = 0.002;

static const float2 VOGEL_DISK[32] = {
    float2(0.12500000, 0.00000000),
    float2(-0.15964505, 0.14624794),
    float2(0.02443623, -0.27843827),
    float2(0.20122224, 0.26245878),
    float2(-0.36926756, -0.06531823),
    float2(0.34980247, -0.22251570),
    float2(-0.11700208, 0.43524190),
    float2(-0.22313565, -0.42963412),
    float2(0.48411512, 0.17679806),
    float2(-0.50364111, 0.20789573),
    float2(0.24278829, -0.51882448),
    float2(0.17941437, 0.57200130),
    float2(-0.54075701, -0.31337974),
    float2(0.63436952, -0.13946436),
    float2(-0.38714585, 0.55067513),
    float2(-0.08943965, -0.69019964),
    float2(0.54907176, 0.46275826),
    float2(-0.73887847, 0.03055494),
    float2(0.53895513, -0.53633233),
    float2(-0.03605819, 0.77979152),
    float2(-0.51281753, -0.61452679),
    float2(0.81235959, 0.10930183),
    float2(-0.68831064, 0.47890862),
    float2(0.18808606, -0.83606138),
    float2(0.43503327, 0.75919105),
    float2(-0.85044841, -0.27131624),
    float2(0.82610240, -0.38168026),
    float2(-0.35788820, 0.85515556),
    float2(-0.31940733, -0.88803376),
    float2(0.84990864, 0.44668816),
    float2(-0.94403465, 0.24884450),
    float2(0.53659581, -0.83452977)
};

static const int BLOCKER_SAMPLES = 16;
static const float LIGHT_SIZE = 0.01;
static const float NEAR_PLANE = 0.1;

float find_blocker_depth(Sampler2D map, float2 uv, float z_receiver, float2x2 rotation) {
    float blocker_sum = 0.0;
    int num_blockers = 0;

    float search_radius = LIGHT_SIZE * (z_receiver - NEAR_PLANE) / z_receiver;

    [unroll]
    for (int i = 0; i < BLOCKER_SAMPLES; i++) {
        float2 offset = mul(rotation, VOGEL_DISK[i]) * search_radius;
        float shadow_map_depth = map.Sample(uv + offset).r;

        if (shadow_map_depth < z_receiver - 0.0015) {
            blocker_sum += shadow_map_depth;
            num_blockers++;
        }
    }

    if (num_blockers == 0) return -1.0;
    return blocker_sum / float(num_blockers);
}

float compute_pcf_variable(Sampler2D map, float2 uv, float z_receiver, float2x2 rotation, float radius) {
    float shadow_sum = 0.0;

    [unroll]
    for (int i = 0; i < PCF_SAMPLES; i++) {
        float2 offset = mul(rotation, VOGEL_DISK[i]) * radius;
        float shadow_map_depth = map.Sample(uv + offset).r;
        shadow_sum += (z_receiver - 0.0015 < shadow_map_depth) ? 1.0 : 0.0;
    }

    return shadow_sum / float(PCF_SAMPLES);
}

float compute_pcss(Sampler2D noise, Sampler2D map, float4x4 view_proj, float3 world_pos, float2 noise_uv) {
    float4 clip = mul(view_proj, float4(world_pos, 1.0));
    float2 uv = clip.xy / clip.w * 0.5 + 0.5;
    float z_receiver = clip.z / clip.w;

    if (uv.x < 0 || uv.x > 1 || uv.y < 0 || uv.y > 1) return 1.0;

    float2 noise_sample = noise.Sample(noise_uv).rg;
    float angle = noise_sample.r * 2.0 * 3.14159265;
    float s = sin(angle);
    float c = cos(angle);
    float2x2 rotation_matrix = float2x2(c, -s, s, c);

    float avg_blocker_depth = find_blocker_depth(map, uv, z_receiver, rotation_matrix);
    if (avg_blocker_depth == -1.0) return 1.0;

    float penumbra_size = (z_receiver - avg_blocker_depth) * LIGHT_SIZE / avg_blocker_depth;
    float final_radius = clamp(penumbra_size, 0.0, 0.01);

    return compute_pcf_variable(map, uv, z_receiver, rotation_matrix, final_radius);
}

float compute_pcf(Sampler2D noise, Sampler2D map, float4x4 view_proj, float3 world_pos, float2 noise_uv) {
    float4 clip = mul(view_proj, float4(world_pos, 1.0));
    float2 uv = clip.xy / clip.w * 0.5 + 0.5;
    float z_receiver = clip.z / clip.w;

    if (uv.x < 0 || uv.x > 1 || uv.y < 0 || uv.y > 1) return 1.0;

    float2 noise_sample = noise.Sample(noise_uv).rg;
    float angle = noise_sample.r * 2.0 * 3.14159265;
    float s = sin(angle);
    float c = cos(angle);
    float2x2 rotation_matrix = float2x2(c, -s, s, c);

    float shadow_sum = 0.0;

    [unroll]
    for (int i = 0; i < PCF_SAMPLES; i++) {
        float2 offset = mul(rotation_matrix, VOGEL_DISK[i]) * FILTER_RADIUS;

        float shadow_map_depth = map.Sample(uv + offset).r;

        shadow_sum += (z_receiver - 0.0015 < shadow_map_depth) ? 1.0 : 0.0;
    }

    return shadow_sum / float(PCF_SAMPLES);
}

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

float compute_shadow_soft(Sampler2D noise, Sampler2D scene_shadows, Sampler2D player_shadows, ShadowData *scene_shadow_data, ShadowData *player_shadow_data, float3 world_pos, float2 noise_uv) {
    float scene_vis = compute_pcss(noise, scene_shadows, scene_shadow_data->view_projection, world_pos, noise_uv);
    float player_vis = compute_pcss(noise, player_shadows, player_shadow_data->view_projection, world_pos, noise_uv);

    return min(scene_vis, player_vis);
}