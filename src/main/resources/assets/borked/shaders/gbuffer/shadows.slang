implementing gbuffer;

__include common;

static const int PCF_SAMPLES = 64;
static const int SIMPLE_PCF_SAMPLES = 6;
static const float FILTER_RADIUS = 0.002;

static float2 compute_vogel_sample(int i, int count, float phi) {
    float r = sqrt(i + 0.5f) / sqrt(count);
    float theta = i * 2.4f + phi;

    return float2(r * cos(theta), r * sin(theta));
}

static const int VOGEL_COUNT = 128;
static const float2 VOGEL_DISK[VOGEL_COUNT] = { float2(0.06250000, 0.00000000), float2(-0.07982252, 0.07312397), float2(0.01221812, -0.13921914), float2(0.10061112, 0.13122939), float2(-0.18463378, -0.03265912), float2(0.17490123, -0.11125785), float2(-0.05850104, 0.21762095), float2(-0.11156783, -0.21481706), float2(0.24205756, 0.08839903), float2(-0.25182055, 0.10394786), float2(0.12139415, -0.25941224), float2(0.08970719, 0.28600065), float2(-0.27037850, -0.15668987), float2(0.31718476, -0.06973218), float2(-0.19357292, 0.27533756), float2(-0.04471983, -0.34509982), float2(0.27453588, 0.23137913), float2(-0.36943924, 0.01527747), float2(0.26947756, -0.26816617), float2(-0.01802909, 0.38989576), float2(-0.25640877, -0.30726340), float2(0.40617980, 0.05465092), float2(-0.34415532, 0.23945431), float2(0.09404303, -0.41803069), float2(0.21751663, 0.37959553), float2(-0.42522421, -0.13565812), float2(0.41305120, -0.19084013), float2(-0.17894410, 0.42757778), float2(-0.15970367, -0.44401688), float2(0.42495432, 0.22334408), float2(-0.47201732, 0.12442225), float2(0.26829790, -0.41726489), float2(0.08534715, 0.49661063), float2(-0.40447060, -0.31324477), float2(0.51739140, -0.04286482), float2(-0.35762652, 0.38658379), float2(0.00260500, -0.53399388), float2(0.36366851, 0.40089146), float2(-0.54609494, -0.05061196), float2(0.44249845, -0.33584055), float2(-0.10067827, 0.55341678), float2(-0.30326674, -0.48192119), float2(0.55572946, 0.15230240), float2(-0.51865259, 0.26616394), float2(0.20496316, -0.55285292), float2(0.22479736, 0.55220911), float2(-0.54465884, -0.25812399), float2(0.58213506, -0.17947846), float2(-0.31123767, 0.53107190), float2(-0.13056249, -0.60800673), float2(0.51207072, 0.36375105), float2(-0.62943631, 0.07844543), float2(0.41511005, -0.48768832), float2(0.02356073, 0.64607557), float2(-0.45801212, -0.46476461), float2(0.65761930, 0.03362452), float2(-0.51217379, 0.42318349), float2(0.09261354, -0.66380832), float2(0.38339675, 0.55681072), float2(-0.66443220, -0.15288430), float2(0.59816761, -0.33889786), float2(-0.21389407, 0.65933154), float2(-0.28998244, -0.63576052), float2(0.64839985, 0.27508431), float2(-0.66913400, 0.23699353), float2(0.33588563, -0.63158498), float2(0.18031883, 0.69786558), float2(-0.60889005, -0.39572296), float2(0.72156989, -0.12038749), float2(-0.45402077, 0.58037393), float2(-0.05766662, -0.73990257), float2(0.54615126, 0.51020834), float2(-0.75256384, -0.00734269), float2(0.56372507, -0.50639194), float2(-0.07410954, 0.75930167), float2(-0.46132012, -0.61402565), float2(0.75991457, 0.14207781), float2(-0.66058521, 0.41121276), float2(0.21067090, -0.75425395), float2(0.35639765, 0.70290431), float2(-0.74222608, -0.27929679), float2(0.74051373, -0.29725100), float2(-0.34735320, 0.72379348), float2(-0.23419462, -0.77297906), float2(0.69897594, 0.41423289), float2(-0.79990497, 0.16769256), float2(0.47932907, -0.66785095), float2(0.09824752, 0.82093920), float2(-0.63055370, -0.54204085), float2(0.83577626, -0.02639673), float2(-0.60177865, 0.58727652), float2(0.04729240, -0.84416064), float2(0.53826784, 0.65796959), float2(-0.84588966, -0.12222698), float2(0.71006278, -0.48383064), float2(-0.19779359, 0.84081594), float2(-0.42432045, -0.75753443), float2(0.82884931, 0.27336344), float2(-0.79989277, 0.36014277), float2(0.34829775, -0.80995829), float2(0.29175017, 0.83668279), float2(-0.78417107, -0.42195317), float2(0.86749065, -0.21963884), float2(-0.49368745, 0.75157598), float2(-0.14434482, -0.89194777), float2(0.71232141, 0.56286496), float2(-0.90973458, 0.06643975), float2(0.62886233, -0.66661527), float2(-0.01347361, 0.92058390), float2(-0.61472387, -0.69107403), float2(0.92428383, 0.09476626), float2(-0.74891776, 0.55697032), float2(0.17678808, -0.92068030), float2(0.49373242, 0.80183979), float2(-0.90967903, -0.25887317), float2(0.84932002, -0.42544007), float2(-0.34034537, 0.89124703), float2(-0.35257215, -0.89087689), float2(0.86541369, 0.42052395), float2(-0.92607189, 0.27565306), float2(0.49872935, -0.83227116), float2(0.19524874, 0.95451384), float2(-0.79197436, -0.57428901), float2(0.97586279, -0.11196236), float2(-0.64654316, 0.74474035), float2(-0.02642959, -0.98983343), float2(0.69084727, 0.71485054), float2(-0.99619823, -0.06068637) };

static const int CONCENTRIC_COUNT = 64; 
static const float2 CONCENTRIC_RINGS[CONCENTRIC_COUNT] = { float2(0.12500000, 0.00000000), float2(0.08838835, 0.08838835), float2(0.00000000, 0.12500000), float2(-0.08838835, 0.08838835), float2(-0.12500000, 0.00000000), float2(-0.08838835, -0.08838835), float2(-0.00000000, -0.12500000), float2(0.08838835, -0.08838835), float2(0.23096988, 0.09567086), float2(0.09567086, 0.23096988), float2(-0.09567086, 0.23096988), float2(-0.23096988, 0.09567086), float2(-0.23096988, -0.09567086), float2(-0.09567086, -0.23096988), float2(0.09567086, -0.23096988), float2(0.23096988, -0.09567086), float2(0.37500000, 0.00000000), float2(0.26516504, 0.26516504), float2(0.00000000, 0.37500000), float2(-0.26516504, 0.26516504), float2(-0.37500000, 0.00000000), float2(-0.26516504, -0.26516504), float2(-0.00000000, -0.37500000), float2(0.26516504, -0.26516504), float2(0.46193977, 0.19134172), float2(0.19134172, 0.46193977), float2(-0.19134172, 0.46193977), float2(-0.46193977, 0.19134172), float2(-0.46193977, -0.19134172), float2(-0.19134172, -0.46193977), float2(0.19134172, -0.46193977), float2(0.46193977, -0.19134172), float2(0.62500000, 0.00000000), float2(0.44194174, 0.44194174), float2(0.00000000, 0.62500000), float2(-0.44194174, 0.44194174), float2(-0.62500000, 0.00000000), float2(-0.44194174, -0.44194174), float2(-0.00000000, -0.62500000), float2(0.44194174, -0.44194174), float2(0.69290965, 0.28701257), float2(0.28701257, 0.69290965), float2(-0.28701257, 0.69290965), float2(-0.69290965, 0.28701257), float2(-0.69290965, -0.28701257), float2(-0.28701257, -0.69290965), float2(0.28701257, -0.69290965), float2(0.69290965, -0.28701257), float2(0.87500000, 0.00000000), float2(0.61871843, 0.61871843), float2(0.00000000, 0.87500000), float2(-0.61871843, 0.61871843), float2(-0.87500000, 0.00000000), float2(-0.61871843, -0.61871843), float2(-0.00000000, -0.87500000), float2(0.61871843, -0.61871843), float2(0.92387953, 0.38268343), float2(0.38268343, 0.92387953), float2(-0.38268343, 0.92387953), float2(-0.92387953, 0.38268343), float2(-0.92387953, -0.38268343), float2(-0.38268343, -0.92387953), float2(0.38268343, -0.92387953), float2(0.92387953, -0.38268343) };

static const int BLOCKER_SAMPLES = 36;
static const float LIGHT_SIZE = 0.01;
static const float NEAR_PLANE = 0.01;
static const float SHADOW_BIAS = 0.0005;
static const float MAX_SHADOW_RADIUS = 0.01;

float find_blocker_depth(Sampler2D map, float2 uv, float z_receiver, float2x2 rotation) {
    float blocker_sum = 0.0;
    int num_blockers = 0;

    float search_radius = LIGHT_SIZE * (z_receiver - NEAR_PLANE) / z_receiver;
    float stride = float(VOGEL_COUNT) / float(BLOCKER_SAMPLES);

    [unroll]
    for (int i = 0; i < BLOCKER_SAMPLES; i++) {
        int vogel_index = min(VOGEL_COUNT - 1, int(float(i) * stride));
        float2 offset = mul(rotation, VOGEL_DISK[vogel_index]) * search_radius;
        float shadow_map_depth = map.Sample(uv + offset).r;

        if (shadow_map_depth < z_receiver - SHADOW_BIAS) {
            blocker_sum += shadow_map_depth;
            num_blockers++;
        }
    }

    if (num_blockers == 0) return -1.0;
    return blocker_sum / float(num_blockers);
}

float compute_pcf_variable(Sampler2D map, float2 uv, float z_receiver, float2x2 rotation, float radius) {
    float shadow_sum = 0.0;

    float stride = float(CONCENTRIC_COUNT) / float(PCF_SAMPLES);

    [unroll]
    for (int i = 0; i < PCF_SAMPLES; i++) {
        int ring_index = min(CONCENTRIC_COUNT - 1, int(float(i) * stride));
        float2 offset = mul(rotation, CONCENTRIC_RINGS[ring_index]) * radius;
        float shadow_map_depth = map.Sample(uv + offset).r;
        shadow_sum += (z_receiver - SHADOW_BIAS < shadow_map_depth) ? 1.0 : 0.0;
    }

    return shadow_sum / float(PCF_SAMPLES);
}

float compute_pcss(Sampler2D noise, Sampler2D map, float4x4 view_proj, float3 world_pos, float2 noise_uv) {
    float4 clip = mul(view_proj, float4(world_pos, 1.0));
    float2 uv = clip.xy / clip.w * 0.5 + 0.5;
    float z_receiver = clip.z / clip.w;

    if (uv.x < 0 || uv.x > 1 || uv.y < 0 || uv.y > 1) return 1.0;

    float2 noise_sample = noise.Sample(noise_uv).rg;
    float angle = noise_sample.r * TAU;
    float s = sin(angle);
    float c = cos(angle);
    float2x2 rotation_matrix = float2x2(c, -s, s, c);

    float avg_blocker_depth = find_blocker_depth(map, uv, z_receiver, rotation_matrix);
    if (avg_blocker_depth == -1.0) return 1.0;

    float penumbra_size = (z_receiver - avg_blocker_depth) * LIGHT_SIZE / avg_blocker_depth;
    float final_radius = saturate(penumbra_size / MAX_SHADOW_RADIUS) * MAX_SHADOW_RADIUS;

    return compute_pcf_variable(map, uv, z_receiver, rotation_matrix, final_radius);
}

float compute_pcf(Sampler2D noise, Sampler2D map, float4x4 view_proj, float3 world_pos, float2 noise_uv) {
    float4 clip = mul(view_proj, float4(world_pos, 1.0));
    float2 uv = clip.xy / clip.w * 0.5 + 0.5;
    float z_receiver = clip.z / clip.w;

    if (uv.x < 0 || uv.x > 1 || uv.y < 0 || uv.y > 1) return 1.0;

    float2 noise_sample = noise.Sample(noise_uv).rg;
    float angle = noise_sample.r * TAU;
    float s = sin(angle);
    float c = cos(angle);
    float2x2 rotation_matrix = float2x2(c, -s, s, c);

    float shadow_sum = 0.0;

    float stride = float(SIMPLE_PCF_SAMPLES) / float(CONCENTRIC_COUNT);

    [unroll]
    for (int i = 0; i < SIMPLE_PCF_SAMPLES; i++) {
        int ring_index = min(CONCENTRIC_COUNT - 1, int(float(i) * stride));
        float2 offset = mul(rotation_matrix, CONCENTRIC_RINGS[ring_index]) * FILTER_RADIUS;

        float shadow_map_depth = map.Sample(uv + offset).r;

        shadow_sum += (z_receiver - 0.0015 < shadow_map_depth) ? 1.0 : 0.0;
    }

    return shadow_sum / float(SIMPLE_PCF_SAMPLES);
}

float2 sample_shadow_map(float3 world_pos, Sampler2D map, float4x4 view_proj) {
    float4 clip = mul(view_proj, float4(world_pos, 1.0));
    float2 uv = clip.xy * 0.5 + 0.5;
    float depth = clip.z / clip.w;

    if (uv.x < 0 || uv.x > 1 || uv.y < 0 || uv.y > 1)
        return float2(depth, 1.0);

    return float2(depth, map.Sample(uv).r);
}

float compute_shadow(Sampler2D scene_shadows, Sampler2D player_shadows, ViewData *scene_shadow_data, ViewData *player_shadow_data, float3 world_pos) {
    float2 scene_depth = sample_shadow_map(world_pos, scene_shadows, scene_shadow_data->view_projection);
    float2 player_depth = sample_shadow_map(world_pos, player_shadows, player_shadow_data->view_projection);

    float scene_shadow_factor = scene_depth.x > scene_depth.y + 0.001 ? 0.0 : 1.0;
    float player_shadow_factor = player_depth.x > player_depth.y + 0.001 ? 0.0 : 1.0;

    return min(player_shadow_factor, scene_shadow_factor);
}

float compute_shadow_soft_simple(Sampler2D noise, Sampler2D scene_shadows, Sampler2D player_shadows, ViewData *scene_shadow_data, ViewData *player_shadow_data, float3 world_pos, float2 noise_uv) {
    float scene_vis = compute_pcf(noise, scene_shadows, scene_shadow_data->view_projection, world_pos, noise_uv);
    float player_vis = compute_pcf(noise, player_shadows, player_shadow_data->view_projection, world_pos, noise_uv);

    return min(scene_vis, player_vis);
}

float compute_shadow_soft(Sampler2D noise, Sampler2D scene_shadows, Sampler2D player_shadows, ViewData *scene_shadow_data, ViewData *player_shadow_data, float3 world_pos, float2 noise_uv) {
    float scene_vis = compute_pcss(noise, scene_shadows, scene_shadow_data->view_projection, world_pos, noise_uv);
    float player_vis = compute_pcss(noise, player_shadows, player_shadow_data->view_projection, world_pos, noise_uv);

    return min(scene_vis, player_vis);
}