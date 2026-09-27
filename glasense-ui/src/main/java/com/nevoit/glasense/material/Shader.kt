package com.nevoit.glasense.material

import org.intellij.lang.annotations.Language

@Language("AGSL")
internal const val AGSL_CODE = """
uniform shader image;
uniform float p0, p1, p2, p3;
uniform float mapIntensity;
uniform float saturation;
uniform float brightness;
uniform float ditherStrength;

const vec3 LUMA_WEIGHTS = vec3(0.2126, 0.7152, 0.0722);

float bezierMap(float x) {
    float invX = 1.0 - x;
    float invX2 = invX * invX;
    float invX3 = invX2 * invX;
    float x2 = x * x;
    float x3 = x2 * x;

    return invX3 * p0
        + 3.0 * invX2 * x * p1
        + 3.0 * invX * x2 * p2
        + x3 * p3;
}

float interleavedGradientNoise(vec2 position) {
    return fract(
        52.9829189 * fract(
            dot(position, vec2(0.06711056, 0.00583715))
        )
    );
}

vec4 main(vec2 fragCoord) {
    vec3 rgb = image.eval(fragCoord).rgb;

    float luma1 = dot(rgb, LUMA_WEIGHTS);
    float mappedLuma = bezierMap(luma1);
    vec3 colorMapped = mix(rgb, vec3(mappedLuma), mapIntensity);

    float luma2 = dot(colorMapped, LUMA_WEIGHTS);
    vec3 colorSaturated = mix(vec3(luma2), colorMapped, saturation);

    float dither = interleavedGradientNoise(fragCoord) - 0.5;
    dither *= ditherStrength / 255.0;

    vec3 finalColor = colorSaturated + vec3(brightness + dither);

    return vec4(clamp(finalColor, 0.0, 1.0), 1.0);
}
"""

@Language("AGSL")
internal const val LENS_SHADER = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

uniform shader content;
uniform float2 size;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);

    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
            + depthEffect * normalize(centeredCoord)
    );
    return content.eval(coord + d * grad);
}
"""

@Language("AGSL")
internal const val DEFAULT_HIGHLIGHT_SHADER = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    return length(max(cornerCoord, 0.0)) - radius + min(max(cornerCoord.x, cornerCoord.y), 0.0);
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) return sign(coord) * normalize(max(cornerCoord, 0.0));
    float gradX = step(cornerCoord.y, cornerCoord.x);
    return sign(coord) * float2(gradX, 1.0 - gradX);
}
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float intensity = pow(abs(dot(grad, float2(cos(angle), sin(angle)))), falloff);
    return color * intensity;
}
"""