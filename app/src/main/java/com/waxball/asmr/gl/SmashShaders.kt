package com.waxball.asmr.gl

/**
 * 화면 뿌시기 모드 셰이더.
 *
 * 투영 행렬이 없다. 정점이 이미 클립 공간이라 그대로 내보내면 된다.
 * 조각별 변환은 텍스처로 넘긴다 — 유니폼 배열은 개수 제한이 빡빡하고,
 * 조각이 150개면 그 한계를 넘는다. BallRenderer가 같은 이유로 같은 방법을 쓴다.
 */
object SmashShaders {

    const val VERTEX = """#version 300 es
in vec3 aPos;
in vec2 aUv;
in float aShard;

uniform sampler2D uXform;
uniform float uShardCount;

out vec2 vUv;
out float vAlpha;

void main() {
    float u = (aShard + 0.5) / uShardCount;
    vec4 r0 = texture(uXform, vec2(u, 0.125));
    vec4 r1 = texture(uXform, vec2(u, 0.375));
    vec4 r2 = texture(uXform, vec2(u, 0.625));
    vec4 meta = texture(uXform, vec2(u, 0.875));

    // meta.x 는 조각이 제 중심으로 오므라든 정도. 금이 벌어져 보이게 한다.
    // meta.yz 는 그 중심, meta.w 는 불투명도.
    vec3 p = aPos;
    p.xy = mix(p.xy, meta.yz, meta.x);

    vec3 moved = vec3(
        dot(r0.xyz, p) + r0.w,
        dot(r1.xyz, p) + r1.w,
        dot(r2.xyz, p) + r2.w
    );

    vUv = aUv;
    vAlpha = meta.w;
    gl_Position = vec4(moved.xy, 0.0, 1.0);
}
"""

    const val FRAGMENT = """#version 300 es
precision mediump float;

in vec2 vUv;
in float vAlpha;

uniform sampler2D uPhoto;

out vec4 fragColor;

void main() {
    if (vAlpha <= 0.01) discard;
    vec4 c = texture(uPhoto, vUv);
    fragColor = vec4(c.rgb, c.a * vAlpha);
}
"""
}
