struct AABB {
    float4 min;
    float4 max;
};

struct DrawAABBData {
    AABB *aabbs;
};

static const uint2 BOX_EDGES[12] = {
    uint2(0, 1), uint2(1, 3), uint2(3, 2), uint2(2, 0),
    uint2(4, 5), uint2(5, 7), uint2(7, 6), uint2(6, 4),
    uint2(0, 4), uint2(1, 5), uint2(2, 6), uint2(3, 7)
};

static const float3 BOX_CORNERS[8] = {
    float3(0, 0, 0),
    float3(1, 0, 0),
    float3(0, 1, 0),
    float3(1, 1, 0),
    float3(0, 0, 1),
    float3(1, 0, 1),
    float3(0, 1, 1),
    float3(1, 1, 1)
};

struct AABBVertex {
    float4 clip_position : SV_Position;
    float has_collision;
};

struct ViewData {
    float4x4 view_projection;
    float4x4 view;
    float4x4 projection;
    float4x4 inverse_view;
    float4x4 inverse_projection;
    float4 camera_position;
}

[[vk::push_constant]]
ConstantBuffer<DrawAABBData> draw_data;

[[vk::binding(0, 0)]]
ConstantBuffer<ViewData> view_data;

[shader("mesh")]
[numthreads(1, 1, 1)]
[outputtopology("line")]
[require(spirv_1_6, spvMeshShadingEXT)]
void meshMain(
    uint3 dispatch_id: SV_DispatchThreadID,
    OutputVertices<AABBVertex, 8> verts,
    OutputIndices<uint2, 12> prims
) {
    uint aabb_index = dispatch_id.x;
    AABB aabb = draw_data.aabbs[aabb_index];

    SetMeshOutputCounts(8, 12);

    float3 extent = aabb.max.xyz - aabb.min.xyz;

    for (uint i = 0; i < 8; ++i) {
        float3 local = BOX_CORNERS[i];
        float3 world_pos = aabb.min.xyz + local * extent;

        float4 clip = mul(
            view_data.view_projection,
            float4(world_pos, 1.0)
        );

        clip.y = -clip.y;

        verts[i].clip_position = clip;
        verts[i].has_collision = aabb.max.w;
    }

    for (uint i = 0; i < 12; ++i) {
        prims[i] = BOX_EDGES[i];
    }
}

struct AABBFragment {
    float4 color : SV_Target0;
};

[shader("fragment")]
AABBFragment fragmentMain(AABBVertex vert) {
    AABBFragment frag;
    frag.color = lerp(float4(1, 0, 0, 1), float4(0, 1, 0, 1), vert.has_collision);
    return frag;
}