#version 150

// CUERPO de la familia DIVINE — fragment. Mismo contrato de salida que aura_flame_shell.fsh
// (premultiplicado vec4(c*a, a*(1-emit)), cara trasera a alfa 0.01, pasada de bloom con
// ZenkaiBloomMode), con otro LENGUAJE:
//
//  - la llama corta la intensidad en 4 bandas de step duro (cel de DMZ);
//  - aquí las bandas son 3 y con el borde SUAVIZADO (smoothstep estrecho): contorno limpio y
//    controlado, sin el dentado de la llama;
//  - el interior es una NUBE (aura_2): dos octavas del ruido subiendo despacio modulan el color y la
//    opacidad del centro, así la capa interior se lee como energía densa y no como un vidrio vacío.

in vec4 vColor;
in vec3 vNormal;
in vec3 vViewDir;
in float vPeak;
in float vLocalY;
in vec3 vUnit;

uniform vec4 ColorModulator;
uniform float GameTime;
uniform sampler2D Sampler0;
uniform float ZenkaiShellSpeed;
uniform float ZenkaiBloomMode;
uniform float ZenkaiOrtho;        // 1 = vista ortográfica (GUI)
uniform vec2 ZenkaiShellEmit;
uniform vec2 ZenkaiShellBloom;
uniform vec2 ZenkaiShellAlpha;
uniform vec2 ZenkaiShellFresnel;
uniform vec4 ZenkaiShellCoreColor;
uniform vec4 ZenkaiShellOuterColor;
uniform vec2 ZenkaiShellCelShape;
uniform vec2 ZenkaiShellBaseFade;
uniform vec4 ZenkaiShellCel;       // y, z = umbrales de borde y banda clara
uniform vec4 ZenkaiBodyLook;       // x = nube del interior (0..1), z = ancho del suavizado de bandas

out vec4 fragColor;

vec3 lift(vec3 c, float k) {
    float m = max(max(c.r, c.g), c.b);
    return c / max(m, 0.001) * min(1.0, m * k);
}

vec3 rimColor(vec3 c) {
    return mix(lift(c, 1.6), vec3(1.0), 0.25);
}

void main() {
    vec3 N = normalize(vNormal);
    // En la GUI (vista previa del aura) la proyección es ORTOGRÁFICA: la visual es el eje Z de la
    // pantalla para cualquier píxel, no la dirección al punto (que daría un borde torcido).
    vec3 V = ZenkaiOrtho > 0.5 ? vec3(0.0, 0.0, 1.0) : normalize(vViewDir);
    float facingRaw = dot(V, N);
    if (facingRaw < 0.0) N = -N;
    float facingAbs = abs(dot(V, N));
    float edge = clamp(pow(1.0 - facingAbs, ZenkaiShellFresnel.x) / max(ZenkaiShellFresnel.y, 0.001), 0.0, 1.0);

    float t = GameTime * 1200.0;
    float ang = atan(vUnit.z, vUnit.x);
    // Nube: U entera (x2, x3) para no dejar costura en atan; suben a distinta velocidad.
    float c1 = texture(Sampler0, vec2(ang / 6.28318530718 * 2.0, vUnit.y * 0.5 - t * ZenkaiShellSpeed * 0.25)).r;
    float c2 = texture(Sampler0, vec2(ang / 6.28318530718 * 3.0, vUnit.y * 0.9 - t * ZenkaiShellSpeed * 0.4)).g;
    float cloud = clamp(c1 * 0.6 + c2 * 0.4, 0.0, 1.0);
    float cloudAmt = clamp(ZenkaiBodyLook.x, 0.0, 1.0);

    float i = clamp(max(edge, vPeak * ZenkaiShellCelShape.x), 0.0, 1.0);
    i *= mix(ZenkaiShellCelShape.y, 1.0, smoothstep(-0.7, -0.1, vLocalY));
    float w = max(ZenkaiBodyLook.z, 0.005);
    float rimB = smoothstep(ZenkaiShellCel.y - w, ZenkaiShellCel.y + w, i);
    float lightB = smoothstep(ZenkaiShellCel.z - w * 1.2, ZenkaiShellCel.z + w * 1.2, i);

    vec3 tint = ZenkaiShellOuterColor.rgb;
    // Interior: el color del centro de la capa, con la nube aclarándolo a parches.
    vec3 inner = mix(ZenkaiShellCoreColor.rgb, lift(ZenkaiShellCoreColor.rgb, 1.3), cloud * cloudAmt);
    vec3 color = mix(inner, lift(tint, 1.2), lightB);
    color = mix(color, rimColor(tint), rimB);

    float aIn = ZenkaiShellAlpha.x * mix(1.0, 0.4 + 1.2 * cloud, cloudAmt);
    float alpha = mix(aIn, ZenkaiShellAlpha.y * 0.75, lightB);
    alpha = mix(alpha, ZenkaiShellAlpha.y, rimB) * vColor.a;
    float emitBlend = max(lightB * 0.4, rimB);

    alpha *= smoothstep(ZenkaiShellBaseFade.x, ZenkaiShellBaseFade.y, vLocalY);
    if (facingRaw < 0.0) alpha = min(alpha, 0.01);

    if (ZenkaiBloomMode > 0.5) {
        alpha *= ZenkaiShellBloom.x * mix(ZenkaiShellBloom.y, 1.0, emitBlend);
    }

    alpha = clamp(alpha, 0.0, 1.0) * ColorModulator.a;
    if (alpha < 0.004) discard;
    float emit = clamp(mix(ZenkaiShellEmit.x, ZenkaiShellEmit.y, emitBlend), 0.0, 1.0);
    fragColor = vec4(color * ColorModulator.rgb * alpha, alpha * (1.0 - emit));
}
