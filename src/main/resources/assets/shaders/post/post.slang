module post;

struct AssembledVertex {
    float4 position : SV_Position;
    float2 uv;
}

[shader("mesh")]
[numthreads(1, 1, 1)]
[outputtopology("triangle")]
void meshMain(
    uint3 dispatch_id: SV_DispatchThreadID,
    OutputVertices<AssembledVertex, 4> vertices,
    OutputIndices<uint3, 2> primitives
) {
    SetMeshOutputCounts(4, 2);

    float2 positions[4] = {
        float2(-1.0, 1.0),
        float2(-1.0, -1.0),
        float2(1.0, -1.0),
        float2(1.0, 1.0)
    };

    float2 uvs[4] = {
        float2(0.0, 1.0),
        float2(0.0, 0.0),
        float2(1.0, 0.0),
        float2(1.0, 1.0)
    };

    [unroll]
    for (uint i = 0; i < 4; i++) {
        vertices[i].position = float4(positions[i], 0.0, 1.0);
        vertices[i].uv = uvs[i];
    }

    primitives[0] = uint3(0, 1, 2);
    primitives[1] = uint3(0, 2, 3);
}

struct DrawData {

}

[[vk::binding(0, 0)]]
ConstantBuffer<DrawData> post_data;

[[vk::binding(1, 0)]]
Sampler2D gbuffer_processed_color;

[[vk::binding(2, 0)]]
Sampler2D gbuffer_processed_depth;

[shader("fragment")]
float4 fragmentMain(AssembledVertex vertex) : SV_Target0 {
    // TODO: I need to implement CMAA2, and maybe other antialiasing idk
    return gbuffer_processed_color.Sample(vertex.uv);
}