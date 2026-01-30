implementing gbuffer;

__include common;

float3 compute_lighting(SceneData* scene_data, ViewData view_data, float3 position, float3 normal, float3 albedo, float ao, float shadow) {
    float3 light_dir = normalize(mul(float3x3(view_data.view), -scene_data->sun_dir));
    float3 light_color = scene_data->sun_color * scene_data->sun_intensity;

    float3 view_dir = normalize(-position); // position is in view space

    float3 ambient = scene_data->ambient_color * ao * albedo;

    float diff = max(dot(normal, light_dir), 0.0);
    float3 diffuse = diff * albedo * light_color;

    float3 half_dir = normalize(light_dir + view_dir + 1e-5);
    float spec = pow(max(dot(normal, half_dir), 0.0), 32.0);
    float3 specular = spec * light_color;

    return ambient + shadow * (diffuse + specular);
}