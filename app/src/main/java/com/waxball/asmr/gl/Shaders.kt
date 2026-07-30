package com.waxball.asmr.gl

object Shaders {

    /**
     * 조각마다 변환이 다르므로 정점이 자기 조각 번호를 들고 다닌다.
     * 그 번호로 텍스처에서 3x4 변환 행렬과 (수축량, 투명도)를 읽는다.
     *
     * 수축량은 금이 갈수록 커진다. 조각이 자기 중심 쪽으로 조금씩 줄어들면서
     * 조각 사이에 틈이 벌어지는데, 그게 눈에는 금으로 보인다.
     */
    const val SHELL_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aShrink;
layout(location = 3) in vec2 aShardFace;

uniform mat4 uViewProj;
uniform sampler2D uXform;
uniform float uScale;

out vec3 vNormal;
out vec3 vWorld;
out float vFace;
out float vAlpha;

void main() {
    int id = int(aShardFace.x + 0.5);
    vec4 r0 = texelFetch(uXform, ivec2(id, 0), 0);
    vec4 r1 = texelFetch(uXform, ivec2(id, 1), 0);
    vec4 r2 = texelFetch(uXform, ivec2(id, 2), 0);
    vec4 st = texelFetch(uXform, ivec2(id, 3), 0);

    vec4 p = vec4(aPos - aShrink * st.x, 1.0);
    vec3 world = vec3(dot(r0, p), dot(r1, p), dot(r2, p)) * uScale;
    vec3 n = normalize(vec3(dot(r0.xyz, aNormal), dot(r1.xyz, aNormal), dot(r2.xyz, aNormal)));

    vNormal = n;
    vWorld = world;
    vFace = aShardFace.y;
    vAlpha = st.y;
    gl_Position = uViewProj * vec4(world, 1.0);
}
"""

    const val SHELL_FRAGMENT = """#version 300 es
precision mediump float;

in vec3 vNormal;
in vec3 vWorld;
in float vFace;
in float vAlpha;

uniform vec3 uShellColor;
uniform vec3 uFleshColor;
uniform vec3 uLightDir;
uniform vec3 uCamPos;

/** 표면 무늬 종류. 0=민무늬 1=가로띠 2=소용돌이 3=분화구 4=얼음균열 5=용암 6=항성 7=점박이 8=바다·대륙 */
uniform int uSurface;
/** 무늬에 쓰는 두 번째 색. 띠 사이나 균열 안쪽에 들어간다. */
uniform vec3 uAccentColor;
/** 흐르는 시간(초). 용암 틈과 항성 표면이 아주 천천히 움직인다. */
uniform float uTime;

out vec4 fragColor;

/** 해시 기반 값잡음. 텍스처를 넣으면 용량이 커져서 셰이더로 만든다. */
float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i);
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    return 0.5 * noise(p) + 0.25 * noise(p * 2.03) + 0.125 * noise(p * 4.01)
         + 0.0625 * noise(p * 8.07);
}

/**
 * 행성 표면색. 단순히 두 색을 섞는 게 아니라 종류마다 실제 지형처럼 만든다.
 *
 * @param emissive 스스로 내는 빛. 용암 틈과 항성만 쓴다
 * @param specMask 반짝임 배율. 바다·얼음은 반짝이고 흙·구름은 안 반짝인다
 */
vec3 planet(vec3 n, vec3 l, vec3 v, out vec3 emissive, out float specMask) {
    emissive = vec3(0.0);
    specMask = 1.0;
    vec3 dark = uShellColor * 0.55;

    if (uSurface == 1) {
        // 가스 행성의 가로 띠. 위도 줄무늬를 난류로 흐트러뜨려야 목성처럼 보인다.
        // 곧은 줄무늬는 비치볼이 된다.
        vec3 q = n * 3.0;
        float warp = fbm(q + vec3(0.0, fbm(q * 1.6) * 1.4, 0.0)) - 0.5;
        float t = sin(n.y * 9.5 + warp * 2.8) * 0.5 + 0.5;
        vec3 col = mix(dark, uShellColor, smoothstep(0.12, 0.5, t));
        col = mix(col, uAccentColor, smoothstep(0.58, 0.94, t));
        // 대적점 같은 폭풍 하나. 이게 있어야 "돌고 있는 행성"으로 읽힌다.
        float storm = smoothstep(0.34, 0.08,
            length(vec2(atan(n.z, n.x) - 1.2, (n.y + 0.3) * 2.6)));
        col = mix(col, mix(uAccentColor, vec3(0.72, 0.28, 0.16), 0.5), storm * 0.75);
        specMask = 0.25;
        return col;
    }
    if (uSurface == 2) {
        // 두꺼운 구름의 소용돌이. 잡음으로 잡음을 비틀면(도메인 워핑) 구름이 흐르는
        // 결이 나온다. 금성·해왕성이 이 얼굴이다.
        vec3 q = n * 2.5;
        float f1 = fbm(q + vec3(1.7, 9.2, 4.1));
        float f2 = fbm(q + vec3(8.3, 2.8, 5.9));
        float m = fbm(q + 2.1 * vec3(f1, f2, f1));
        vec3 col = mix(uShellColor, uAccentColor, smoothstep(0.3, 0.72, m));
        specMask = 0.3;
        return col * (0.82 + 0.36 * f2);
    }
    if (uSurface == 3) {
        // 분화구. 빛 쪽으로 살짝 옮겨 다시 재서 그 차이로 눌린 자국의 명암을 낸다.
        // 색만 바꾸면 평평한 얼룩이고, 명암이 있어야 파인 것으로 보인다.
        float h = fbm(n * 5.5);
        float toLight = fbm(n * 5.5 + l * 0.07);
        float emboss = clamp((toLight - h) * 5.0, -0.45, 0.45);
        vec3 col = mix(uShellColor, uAccentColor, smoothstep(0.54, 0.38, h));
        col *= (1.0 + emboss) * (0.9 + 0.2 * noise(n * 17.0));
        specMask = 0.1;
        return col;
    }
    if (uSurface == 4) {
        // 얼음 껍질의 균열. 유로파처럼 매끈한 얼음판에 실금이 가로지른다.
        float c = abs(fbm(n * 4.5) - 0.5);
        float crack = smoothstep(0.06, 0.0, c);
        vec3 col = uShellColor * (0.9 + 0.25 * fbm(n * 2.2));
        specMask = 1.8;   // 얼음은 젖은 것처럼 반짝인다
        return mix(col, uAccentColor, crack * 0.85);
    }
    if (uSurface == 5) {
        // 식은 껍질 사이로 용암이 비친다. 틈은 조명과 무관하게 스스로 빛나야
        // 뜨거워 보인다. 아주 천천히 흐른다.
        float crust = fbm(n * 3.2 + vec3(uTime * 0.015));
        float glow = smoothstep(0.56, 0.40, crust);
        emissive = uAccentColor * glow * 1.5;
        specMask = 0.0;
        return mix(dark, uShellColor, smoothstep(0.35, 0.75, crust));
    }
    if (uSurface == 6) {
        // 항성. 표면이 끓고(쌀알무늬) 스스로 빛난다. 실제 항성은 가장자리가
        // 어두워지므로(주연감광) 테두리를 밝히면 전구처럼 보인다.
        float g = fbm(n * 6.0 + vec3(0.0, uTime * 0.05, 0.0))
                + 0.5 * fbm(n * 13.0 - vec3(uTime * 0.03));
        vec3 col = mix(uShellColor, uAccentColor, smoothstep(0.4, 1.1, g));
        float limb = 0.45 + 0.55 * max(dot(n, v), 0.0);
        emissive = col * (0.6 + 0.55 * g) * limb;
        specMask = 0.0;
        return col * 0.2;
    }
    if (uSurface == 7) {
        // 점박이. 굵은 얼룩 위에 자잘한 알갱이가 박힌 암석 표면.
        float g = fbm(n * 8.0);
        vec3 col = mix(uShellColor, uAccentColor, smoothstep(0.52, 0.75, g));
        col = mix(col, uAccentColor, smoothstep(0.74, 0.9, noise(n * 21.0)) * 0.5);
        return col * (0.88 + 0.24 * g);
    }
    // uSurface == 8: 바다·대륙·구름·빙관. 지구다.
    float cont = fbm(n * 2.6 + vec3(4.2, 1.3, 7.8));
    float land = smoothstep(0.5, 0.535, cont);
    vec3 landCol = mix(uAccentColor, vec3(0.72, 0.62, 0.42), smoothstep(0.6, 0.78, cont));
    vec3 col = mix(uShellColor, landCol, land);
    float cap = smoothstep(0.7, 0.84, abs(n.y) + (fbm(n * 5.0) - 0.5) * 0.14);
    col = mix(col, vec3(0.92, 0.95, 1.0), cap);
    float clouds = smoothstep(0.55, 0.78, fbm(n * 3.8 + vec3(uTime * 0.008, 0.0, 0.0)));
    // 바다에만 해가 비쳐 반짝인다. 육지와 구름은 반짝이지 않는다.
    specMask = (1.0 - land) * (1.0 - clouds) * 1.6;
    return mix(col, vec3(0.98), clouds * 0.8);
}

void main() {
    if (vAlpha < 0.02) discard;

    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uCamPos - vWorld);

    // 무늬는 껍질 바깥면에만 그린다. 깨진 단면과 안쪽면은 왁스 속살이라 민무늬다.
    vec3 emissive = vec3(0.0);
    float specMask = 1.0;
    vec3 shell = uShellColor;
    if (uSurface > 0 && vFace > 0.5) {
        shell = planet(n, l, v, emissive, specMask);
    }

    vec3 base;
    if (vFace > 0.5) {
        base = shell;
    } else if (vFace < -0.5) {
        base = uFleshColor * 0.62;          // 껍질 안쪽면은 그늘져 있다
    } else {
        base = mix(uShellColor, uFleshColor, 0.35) * 0.72;  // 깨진 단면
    }

    vec3 h = normalize(l + v);

    float wrap = dot(n, l) * 0.5 + 0.5;     // 왁스는 빛을 머금어 경계가 부드럽다
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
    float spec = pow(max(dot(n, h), 0.0), 48.0);

    // 대기 테두리는 그 행성의 색을 띤다. 지구는 푸르게, 화성은 불그스름하게.
    vec3 atmo = mix(uShellColor, vec3(1.0), 0.35);
    vec3 col = base * (0.24 + 0.88 * wrap * wrap)
             + atmo * rim * 0.3
             + vec3(spec * 0.3) * specMask
             + emissive;
    fragColor = vec4(col, vAlpha);
}
"""

    /** 말랑이 코어. 누르면 그 지점이 실제로 눌린다. */
    const val CORE_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;

uniform mat4 uViewProj;
uniform mat3 uRot;
uniform float uRadius;
uniform vec3 uPressPoint;
uniform float uPressAmount;
uniform vec3 uOffset;

out vec3 vNormal;
out vec3 vWorld;

void main() {
    vec3 p = aPos;
    float d = max(0.0, 1.0 - length(p - uPressPoint) / 0.85);
    p -= normalize(uPressPoint + vec3(0.0001)) * (d * d) * uPressAmount;

    // 여러 개를 흩어 놓을 때 껍질과 같은 자리로 옮겨야 한다.
    vec3 world = (uRot * p) * uRadius + uOffset;
    vNormal = normalize(uRot * aPos);
    vWorld = world;
    gl_Position = uViewProj * vec4(world, 1.0);
}
"""

    const val CORE_FRAGMENT = """#version 300 es
precision mediump float;

in vec3 vNormal;
in vec3 vWorld;

uniform vec3 uColor;
uniform vec3 uLightDir;
uniform vec3 uCamPos;

/**
 * 1이면 속살(불투명), 1보다 작으면 고무풍선 껍질이다.
 * 같은 구 메시를 반지름과 이 값만 바꿔 두 번 그린다.
 */
uniform float uAlpha;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uCamPos - vWorld);
    float wrap = dot(n, l) * 0.5 + 0.5;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 2.0);
    // 말랑한 것은 빛이 속으로 스며 가장자리가 밝다
    vec3 col = uColor * (0.3 + 0.8 * wrap) + uColor * rim * 0.5;

    if (uAlpha < 0.999) {
        // 고무풍선은 가운데가 비치고 가장자리에서 두꺼워 보인다. 알파를 프레넬로 준다.
        // 균일하게 주면 비닐랩을 씌운 것처럼 보이고 안이 훤히 들여다보인다.
        float edge = pow(1.0 - max(dot(n, v), 0.0), 1.6);
        float a = uAlpha * (0.35 + 1.9 * edge);
        // 젖은 고무의 반짝임
        vec3 h = normalize(l + v);
        float spec = pow(max(dot(n, h), 0.0), 48.0);
        fragColor = vec4(col * 0.7 + vec3(spec) * 0.6, clamp(a, 0.0, 0.9));
        return;
    }
    fragColor = vec4(col, 1.0);
}
"""
}
