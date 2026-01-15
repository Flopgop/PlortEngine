
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
    uint width, height;
    g_position.GetDimensions(width, height);
    float2 screen_size = float2(width, height);
    float2 uv = (pixel_coord + 0.5) / screen_size;

    uint2 noise_res;
    noise_texture.GetDimensions(noise_res.x, noise_res.y);
    float3 noise = noise_texture.Load(uint3(pixel_coord % noise_res, 0)).xyz;

    float rotation_angle = noise.x * PI;
    float step_jitter = noise.y;

    float3 V = normalize(-position);

    float total_occlusion = 0;

    for (int d = 0; d < NUM_DIRECTIONS; d++) {
        float angle = (float(d) + rotation_angle) * (PI / NUM_DIRECTIONS);
        float2 dir_2d = float2(cos(angle), sin(angle));

        float3 slice_dir = normalize(float3(dir_2d, 0));
        float3 plane_normal = normalize(cross(slice_dir, V));
        if (length(cross(slice_dir, V)) < 0.0001) continue;
        float3 proj_normal = normal - plane_normal * dot(normal, plane_normal);
        float proj_len = length(proj_normal);

        if (proj_len < 0.0001) {
            total_occlusion += 1.0;
            continue;
        }

        float3 projected_n = normalize(proj_normal);
        float n_dot_v = clamp(dot(projected_n, V), -0.99999, 0.99999);
        float n = acos(n_dot_v) * sign(dot(slice_dir, projected_n));

        float h1 = n - PI / 2.0;
        float h2 = n + PI / 2.0;

        float screen_radius = (GTAO_RADIUS_WORLD * view_data.projection[1][1]) / -position.z;
        screen_radius *= (height / 2.0);

        [unroll]
        for (int side = 0; side < 2; side++) {
            float2 search_dir = (side == 0) ? dir_2d : -dir_2d;
            float current_h = (side == 0) ? h1 : h2;

            for (int s = 1; s <= NUM_STEPS; s++) {
                float step_dist = (float(s) + step_jitter) / (float(NUM_STEPS) + 1.0);
                float2 offset_uv = uv + (search_dir * step_dist * (screen_radius / screen_size));

                if (any(offset_uv < 0) || any(offset_uv > 1)) break;

                float3 sample_pos = g_position.SampleLevel(offset_uv, 0).xyz;
                float3 horizon_vec = sample_pos - position;
                float dist_sq = dot(horizon_vec, horizon_vec);

                if (dist_sq > 0.0001 && dist_sq < (GTAO_RADIUS_WORLD * GTAO_RADIUS_WORLD)) {
                    float3 v_sample = normalize(horizon_vec);
                    float h_dot_v = clamp(dot(v_sample, V), -0.99999, 0.99999);
                    float angle_h = acos(h_dot_v);

                    if (side == 0) h1 = max(h1, n - angle_h);
                    else h2 = min(h2, n + angle_h);
                }
            }
        }

        h1 = n + clamp(h1 - n, -PI / 2.0, PI / 2.0);
        h2 = n + clamp(h2 - n, -PI / 2.0, PI / 2.0);

        float intensity = (cos(n) + 2.0 * h1 * sin(n) - cos(2.0 * h1 - n) +
                           cos(n) + 2.0 * h2 * sin(n) - cos(2.0 * h2 - n)) / 4.0;

        if (!isnan(intensity)) total_occlusion += saturate(intensity);
    }

    return saturate(total_occlusion / NUM_DIRECTIONS);
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