package com.mitesh.glasslock.ui

/**
 * AGSL shaders behind the liquid-glass look. Each samples a pre-blurred copy of the
 * wallpaper at the element's real screen position, bends it near the edges like a lens,
 * splits colour slightly (chromatic aberration) and adds a specular rim that follows the light.
 */
internal object GlassShaders {
    @Volatile
    var broken = false

    const val PANEL = """
uniform shader wall;
uniform float2 wallScale;
uniform float2 origin;
uniform float2 size;
uniform float radius;
uniform float2 light;
uniform float4 tint;
uniform float refraction;
uniform float bevel;
uniform float rimAlpha;
uniform float opacity;

float sdRound(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 p) {
    float2 hs = size * 0.5;
    float2 c = p - hs;
    float r = min(radius, min(hs.x, hs.y));
    float d = sdRound(c, hs, r);
    float aa = 1.0 - smoothstep(-0.75, 0.75, d);
    if (aa <= 0.0) {
        return half4(0.0);
    }
    float e = 0.75;
    float2 g = float2(sdRound(c + float2(e, 0.0), hs, r) - sdRound(c - float2(e, 0.0), hs, r),
                      sdRound(c + float2(0.0, e), hs, r) - sdRound(c - float2(0.0, e), hs, r));
    float gl = length(g);
    float2 n = gl > 0.0001 ? g / gl : float2(0.0, -1.0);
    float depth = clamp(-d / max(bevel, 1.0), 0.0, 1.0);
    float k = 1.0 - depth;
    float edge = 1.0 - sqrt(max(1.0 - k * k, 0.0));
    float bend = edge * refraction;
    float2 s = origin + p - n * bend;
    float2 ca = n * bend * 0.14;
    half rr = wall.eval((s - ca) * wallScale).r;
    half gg = wall.eval(s * wallScale).g;
    half bb = wall.eval((s + ca) * wallScale).b;
    half3 col = half3(rr, gg, bb);
    col = mix(col, half3(tint.rgb), half(tint.a));
    col = col * 1.04 + 0.015;
    float2 ld = normalize(light);
    float facing = dot(n, ld);
    float rim = 1.0 - smoothstep(0.0, 1.8, abs(d + 1.0));
    float spec = rim * (0.22 + 0.78 * max(facing, 0.0) + 0.40 * max(-facing, 0.0));
    float glow = pow(k, 5.0) * (0.06 + 0.16 * max(facing, 0.0));
    col += half3(spec * rimAlpha + glow);
    float a = aa * opacity;
    return half4(col * half(a), half(a));
}
"""

    const val TEXT = """
uniform shader wall;
uniform float2 wallScale;
uniform float2 origin;
uniform float2 stretch;
uniform float2 center;
uniform float2 light;
uniform float4 tint;
uniform float magnify;
uniform float2 bounds;

half4 main(float2 p) {
    float2 s = origin + p * stretch;
    float2 m = center + (s - center) * magnify;
    float2 ca = (s - center) * 0.008;
    half r = wall.eval((m - ca) * wallScale).r;
    half g = wall.eval(m * wallScale).g;
    half b = wall.eval((m + ca) * wallScale).b;
    half3 col = half3(r, g, b);
    col = mix(col, half3(tint.rgb), half(tint.a));
    float t = clamp((p.y - bounds.x) / max(bounds.y - bounds.x, 1.0), 0.0, 1.0);
    float2 ld = normalize(light);
    float sheen = 0.22 * (1.0 - t) * (0.75 + 0.5 * max(-ld.y, 0.0)) + 0.08 * smoothstep(0.7, 1.0, t);
    col += half3(sheen);
    return half4(col, 1.0);
}
"""
}
