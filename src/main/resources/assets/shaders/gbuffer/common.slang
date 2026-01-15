implementing gbuffer;

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

struct SceneData {
    float3 sun_dir;
    float3 sun_color;
    float sun_intensity;

    float3 ambient_color;
    
    float3 fog_color;
    float fog_density;

    ShadowData* shadow;
}

struct ShadowData {
    float4x4 view_projection;
    float4x4 view;
    float4x4 projection;
    float4x4 inverse_view;
    float4x4 inverse_projection;
    float4 camera_position;
}