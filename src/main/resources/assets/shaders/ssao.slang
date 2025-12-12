
struct ViewData {
    float4x4 view_projection;
    float4x4 view;
    float4x4 projection;
    float4x4 inverse_view;
    float4x4 inverse_projection;
    float4 camera_position;
}

[[vk::binding(0, 0)]]
Sampler2D g_position; // view space

[[vk::binding(1, 0)]]
Sampler2D g_normal; // vertex normal * normal matrix

[[vk::binding(2, 0)]]
Sampler2D noise_texture;

[[vk::binding(3, 0)]]
ConstantBuffer<ViewData> view_data;

[[vk::binding(4, 0)]]
[[vk::image_format("r8")]]
RWTexture2D<float> output_texture;

#define KERNEL_SIZE 64
static const float3 ssao_kernel[KERNEL_SIZE] = {
    float3(-0.5620, -0.5087, 0.6522), float3(-0.7527, -0.1534, 0.6403), float3(0.5627, -0.6329, 0.5318), float3(-0.0950, 0.4484, 0.8888), float3(0.3312, 0.8396, 0.4306), float3(-0.5298, -0.4162, 0.7390), float3(-0.6275, -0.7716, 0.1041), float3(0.4715, -0.5348, 0.7012), float3(-0.8894, -0.2039, 0.4092), float3(-0.7257, 0.6030, 0.3312), float3(-0.4399, -0.4757, 0.7617), float3(0.4293, -0.2199, 0.8760), float3(0.5508, -0.7752, 0.3095), float3(-0.6485, -0.7554, 0.0938), float3(0.1850, 0.7312, 0.6566), float3(0.5553, -0.6739, 0.4873), float3(-0.5728, -0.7948, 0.2005), float3(0.5331, 0.4766, 0.6990), float3(-0.0412, 0.3067, 0.9509), float3(0.3141, 0.9372, 0.1513), float3(0.8865, -0.4231, 0.1874), float3(-0.8510, -0.4474, 0.2750), float3(0.4745, 0.7629, 0.4391), float3(-0.6681, 0.4060, 0.6236), float3(0.8313, -0.2323, 0.5049), float3(-0.5222, 0.2524, 0.8146), float3(-0.5679, -0.8134, 0.1259), float3(-0.8194, -0.2991, 0.4890), float3(-0.6084, 0.1150, 0.7853), float3(0.4235, -0.5029, 0.7535), float3(0.0015, -0.2299, 0.9732), float3(-0.1875, 0.1472, 0.9712), float3(0.3713, 0.1794, 0.9110), float3(-0.8889, -0.3044, 0.3425), float3(0.8241, 0.5620, 0.0707), float3(0.7980, -0.5707, 0.1939), float3(0.5539, 0.5105, 0.6577), float3(0.6248, -0.7741, 0.1019), float3(0.8388, 0.4719, 0.2714), float3(-0.5007, 0.5253, 0.6880), float3(0.7686, -0.6309, 0.1059), float3(0.1133, -0.2531, 0.9608), float3(-0.7215, -0.2584, 0.6424), float3(0.0561, -0.7778, 0.6260), float3(0.9838, 0.1714, 0.0533), float3(-0.5391, 0.6050, 0.5859), float3(0.2480, 0.9524, 0.1772), float3(-0.7772, -0.2758, 0.5656), float3(-0.7376, 0.4081, 0.5380), float3(0.4149, 0.8939, 0.1699), float3(-0.8030, 0.4706, 0.3658), float3(-0.7146, -0.5170, 0.4712), float3(-0.8383, -0.5423, 0.0563), float3(0.3182, 0.9282, 0.1928), float3(0.7106, -0.6336, 0.3061), float3(-0.6484, -0.4776, 0.5928), float3(-0.1521, 0.7559, 0.6367), float3(-0.0306, 0.5094, 0.8600), float3(0.2093, 0.4199, 0.8831), float3(-0.8823, -0.4400, 0.1673), float3(0.5282, -0.4788, 0.7012), float3(0.2557, 0.9577, 0.1320), float3(0.3926, 0.7336, 0.5547), float3(-0.7107, -0.6175, 0.3371)
};

#define PI 3.141592653
#define NUM_DIRECTIONS 16
#define NUM_STEPS 4

static const float GTAO_RADIUS_WORLD = 0.25;
static const float GTAO_SAMPLES_PER_DIRECTION = 16;
static const float GTAO_THICKNESS_FALLOFF = 2.0;
static const float GTAO_INTENSITY = 25.0;

float computeGTAO(float3 position, float3 normal, uint2 pixel_coord) {
    uint width, height, levels;
    g_position.GetDimensions(0, width, height, levels);
    float2 screen_size = float2(width, height);
    float2 uv = (pixel_coord + 0.5) / screen_size;

    uint noise_width, noise_height, noise_levels;
    noise_texture.GetDimensions(0, noise_width, noise_height, noise_levels);

    float2 noise_uv = (uv / float2(noise_width, noise_height)) * 4.0;
    float3 random_vec = normalize(noise_texture.Sample(noise_uv).xyz);

    float3 tangent = normalize(random_vec - normal * dot(random_vec, normal));
    float3 bitangent = cross(normal, tangent);
    float3x3 tbn = float3x3(tangent, bitangent, normal);

    float occlusion = 0.0;

    const float max_r_sq = GTAO_RADIUS_WORLD * GTAO_RADIUS_WORLD;

    for (int d = 0; d < NUM_DIRECTIONS; d++) {
        float angle = 2.0 * PI * d / NUM_DIRECTIONS;
        float2 dir_2d = float2(cos(angle), sin(angle));

        float random_angle = random_vec.x * 2.0 * PI;
        float cos_r = cos(random_angle);
        float sin_r = sin(random_angle);
        float2 rotated_dir_2d = float2(
            dir_2d.x * cos_r - dir_2d.y * sin_r,
            dir_2d.x * sin_r + dir_2d.y * cos_r
        );

        float3 ray_tbn = normalize(float3(rotated_dir_2d, 0.0));

        float3 ray_view = mul(tbn, ray_tbn);

        float depth_sum = 0.0;
        float step_size_view = GTAO_RADIUS_WORLD / (GTAO_SAMPLES_PER_DIRECTION + 1.0);

        float min_depth_along_ray = 1e38;

        for (int s = 1; s <= GTAO_SAMPLES_PER_DIRECTION; s++) {
            float3 sample_pos_view = position + ray_view * (s * step_size_view);
            float4 offset_clip = mul(view_data.projection, float4(sample_pos_view, 1.0));
            float2 offset_ndc = offset_clip.xy / offset_clip.w;
            float2 sample_uv = offset_ndc * 0.5f + 0.5f;

            if (any(sample_uv < 0.0) || any(sample_uv > 1.0)) {
                min_depth_along_ray = min(min_depth_along_ray, 1e38);
            } else {
                float3 sampled_pos = g_position.Sample(sample_uv).xyz;
                min_depth_along_ray = min(min_depth_along_ray, sampled_pos.z);
            }
        }

        float3 V = float3(0.0, 0.0, min_depth_along_ray) - position; 
        V = float3(position.xy, min_depth_along_ray) - position;

        float closest_depth = min_depth_along_ray;
        float depth_diff = closest_depth - position.z;
        float length_along_ray = max(0.0, depth_diff);

        float attenuation = max(0.0, 1.0 - pow(length_along_ray / GTAO_RADIUS_WORLD, GTAO_THICKNESS_FALLOFF));

        float nDotV = dot(normal, ray_view);
        float angle_factor = saturate(nDotV); 
        float dir_occlusion = length_along_ray * angle_factor * attenuation;

        occlusion += dir_occlusion;
    }

    occlusion /= NUM_DIRECTIONS;
    occlusion *= GTAO_INTENSITY; 

    return saturate(1.0 - occlusion);
}

float computeHBAO(float3 position, float3 normal, uint2 pixel_coord) { // note this is NOT HBAO and it's implemented more like GTAO, but wrong.
    float radius = 0.02 / position.z; // perspective scale
    float occlusion = 0.0;

    uint width, height, levels;
    g_position.GetDimensions(0, width, height, levels);
    float2 screen_size = float2(width, height);
    float2 uv = (pixel_coord + 0.5) / screen_size;
    
    float3 rand = normalize(noise_texture.Sample(uv).xyz);
    float2 cos_sin = float2(cos(rand.x * 2 * PI), sin(rand.x * 2 * PI));

    for (int d = 0; d < NUM_DIRECTIONS; d++) {
        float angle = 2 * PI * d / NUM_DIRECTIONS;
        float2 dir = float2(cos(angle), sin(angle));

        dir = float2(dir.x * cos_sin.x - dir.y * cos_sin.y, dir.x * cos_sin.y + dir.y * cos_sin.x);

        dir.x *= (screen_size.y / screen_size.x);

        float step_size_uv = radius / (NUM_STEPS + 1);

        float rayLength = radius / (NUM_STEPS + 1);
        for (int s = 1; s <= NUM_STEPS; s++) {
            float2 offset = dir * step_size_uv * s;
            float2 sample_uv = uv + offset;

            if (any(sample_uv < 0) || any(sample_uv > 1)) continue;

            float3 sample_pos = g_position.Sample(sample_uv).xyz;

            if (sample_pos.z > 1000.0) continue;

            float3 V = sample_pos - position;
            float dist2 = dot(V, V);
            float nDotV = dot(normal, normalize(V));

            float attenuation = saturate(1.0 - dist2 / (0.02 * 0.02 * 2.0));
            occlusion += saturate(nDotV - 0.05) * attenuation;
        }
    }

    occlusion /= NUM_DIRECTIONS * NUM_STEPS;
    return saturate(1.0 - occlusion * 5.0);
}

float computeSSAO(float3 position, float3 normal, uint2 uv) {
    uint noise_width, noise_height, noise_levels;
    noise_texture.GetDimensions(0, noise_width, noise_height, noise_levels);
    float3 random_vec = normalize(noise_texture.Sample((uv / float2(noise_width, noise_height)) * 3).xyz);

    float3 tangent = normalize(random_vec - normal * dot(random_vec, normal));
    float3 bitangent = cross(normal, tangent);
    float3x3 tbn = float3x3(tangent, bitangent, normal);

    const float radius = 0.02f;
    const float bias = 0.02f;
    float occlusion = 0.0f;

    [unroll]
    for (int i = 0; i < KERNEL_SIZE; i++) {
        float3 sample_pos = position + mul(tbn, ssao_kernel[i]) * radius;

        float4 offset = mul(view_data.projection, float4(sample_pos, 1.0));
        offset.xyz /= offset.w;
        float2 offset_uv = offset.xy * 0.5f + 0.5f;

        float4 sample_view_pos = g_position.Sample(offset_uv);
        if (sample_view_pos.a <= 0.1) continue;

        float range_check = smoothstep(0.0, 1.0, radius / abs(position.z - sample_view_pos.z));
        occlusion += (sample_view_pos.z > sample_pos.z + bias ? 1.0 : 0.0) * range_check;
    }

    occlusion = 1.0 - (occlusion / KERNEL_SIZE);
    return occlusion;
}

[shader("compute")]
[numthreads(8,8,1)]
void main(uint3 thread_id: SV_DispatchThreadID) {
    uint2 coord_xy = thread_id.xy;

    uint3 coord = uint3(coord_xy, 0);

    float4 position = g_position.Load(coord);
    if (position.a <= 0.1) {
        output_texture[coord_xy] = 1.0;
        return;
    }
    float3 normal = normalize(g_normal.Load(coord).xyz);

    float ssao = computeGTAO(position.xyz, normal, coord_xy);

    output_texture[coord_xy] = saturate(ssao);
}