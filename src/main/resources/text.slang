struct GlyphMeshlet {
    float4 bounds;
    float3 color;
    uint glyph_index;
    float depth;
    float _pad0[3];
}

struct GlyphData {
    float4 atlas_uv;
    float distance_field_size;
    float _pad0[3];
}

struct DrawData {
    float4x4 projection;
    uint glyph_count;
    uint _pad0[2];
}

struct AssembledVertex {
    float4 position : SV_Position;
    float3 color : LOCATION0;
    float2 uv : LOCATION1;
    uint glyph_index : LOCATION2;
}

[[vk::push_constant]]
ConstantBuffer<DrawData> string_data;

[[vk::binding(0, 0)]]
StructuredBuffer<GlyphMeshlet, Std430DataLayout> meshlets;

[[vk::binding(1, 0)]]
StructuredBuffer<GlyphData> glyph_data;

[[vk::binding(2, 0)]]
Sampler2D text_atlas;

#define MS_GROUP_SIZE 64

[shader("mesh")]
[outputtopology("triangle")]
[numthreads(MS_GROUP_SIZE, 1, 1)]
void meshMain(
    uint3 id: SV_DispatchThreadID,
    uint3 lid: SV_GroupThreadID,
    OutputVertices<AssembledVertex, 4 * MS_GROUP_SIZE> verts,
    OutputIndices<uint3, 2 * MS_GROUP_SIZE> prims
) {
    if (id.x >= string_data.glyph_count) return;

    SetMeshOutputCounts(4 * MS_GROUP_SIZE, 2 * MS_GROUP_SIZE);

    GlyphMeshlet g = meshlets[id.x];
    int vertex_offset = lid.x * 4;
    int prim_offset = lid.x * 2;

    verts[vertex_offset + 0] = { mul(string_data.projection, float4(g.bounds.x, g.bounds.y + g.bounds.w, g.depth, 1.0)), g.color, float2(0.0, 0.0), g.glyph_index };
    verts[vertex_offset + 1] = { mul(string_data.projection, float4(g.bounds.x, g.bounds.y, g.depth, 1.0)), g.color, float2(0.0, 1.0), g.glyph_index };
    verts[vertex_offset + 2] = { mul(string_data.projection, float4(g.bounds.x + g.bounds.z, g.bounds.y, g.depth, 1.0)), g.color, float2(1.0, 1.0), g.glyph_index };
    verts[vertex_offset + 3] = { mul(string_data.projection, float4(g.bounds.x + g.bounds.z, g.bounds.y + g.bounds.w, g.depth, 1.0)), g.color, float2(1.0, 0.0), g.glyph_index };

    prims[prim_offset + 0] = uint3(vertex_offset + 0, vertex_offset + 2, vertex_offset + 1);
    prims[prim_offset + 1] = uint3(vertex_offset + 0, vertex_offset + 3, vertex_offset + 2);
}

#define PIXEL_RANGE 2

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float screenPxRange(GlyphData glyph, float2 uv) {
    float w, h;
    text_atlas.GetDimensions(w, h);
    float2 unitRange = float2(glyph.distance_field_size) / float2(w, h);
    float2 screenTexSize = float2(1.0) / fwidth(uv);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

[shader("fragment")]
float4 fragmentMain(AssembledVertex vertex) : SV_Target0 {
    uint glyph_index = vertex.glyph_index;
    GlyphData glyph = glyph_data[glyph_index];
    float2 uv = glyph.atlas_uv.xy + vertex.uv * glyph.atlas_uv.zw;

    float3 msd = text_atlas.Sample(uv).rgb;
    float sd = median(msd.r, msd.g, msd.b);

    float screenPxDistance = screenPxRange(glyph, uv) * (sd - 0.5);

    float opacity = clamp(screenPxDistance + 0.5, 0.0, 1.0);

    if (opacity < 0.1) discard;

    return float4(vertex.color, opacity);
}