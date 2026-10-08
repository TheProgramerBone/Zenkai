#version 150

// CUERPO de la familia DARK_DENSE — fragment. Mismo contrato de salida que aura_flame_shell.fsh.
//
// Lenguaje INVERTIDO respecto a la llama: en la llama el centro es casi transparente y se lee el
// contorno; aquí el interior es DENSO y PROFUNDO (el tinte oscurecido pero saturado, nunca hacia
// gris — un translúcido oscurecido hacia gris se lee sucio, lección de aura.md) y lo claro es un
// borde FINO. Por dentro, vetas del ruido que suben despacio rompen el interior (la masa hierve)
// y desordenan el límite entre bandas. Bandas duras, como la llama: sigue siendo el mismo mundo
// cel/pixel.

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
uniform vec2 ZenkaiShellBaseFade;
uniform vec4 ZenkaiShellCel;       // y, z, w = umbrales de borde, banda clara y cuerpo
uniform vec4 ZenkaiBodyLook;       // x = vetas (0..1), y = profundidad del interior (0..1)

out vec4 fragColor;

vec3 lift(vec3 c, float k) {
    float m = max(max(c.r, c.g), c.b);
    return c / max(m, 0.001) * min(1.0, m * k);
}

// Oscurece SIN desaturar: baja el canal máximo y empuja los otros hacia 0 (más puro), no hacia gris.
vec3 deepen(vec3 c, float d) {
    float m = max(max(c.r, c.g), c.b);
    vec3 pure = clamp((c - m * 0.35 * d) / max(1.0 - 0.35 * d, 0.001), 0.0, 1.0);
    return pure * (1.0 - 0.55 * d);
}

void main() {
    vec3 N = normalize(vNormal);
    // En la GUI (vista previa del aura) la proyección es ORTOGRÁFICA: la visual es el eje Z de la
    // pantalla para cualquier píxel, no la dirección al punto (que daría un borde torcido).
    vec3 V = (ZenkaiOrtho > 0.5 && ZenkaiOrtho < 1.5) ? vec3(0.0, 0.0, 1.0) : normalize(vViewDir);
    float facingRaw = dot(V, N);
    if (facingRaw < 0.0) N = -N;
    float facingAbs = abs(dot(V, N));
    float edge = clamp(pow(1.0 - facingAbs, ZenkaiShellFresnel.x) / max(ZenkaiShellFresnel.y, 0.001), 0.0, 1.0);

    float t = GameTime * 1200.0;
    float ang = atan(vUnit.z, vUnit.x);
    float swirl = texture(Sampler0, vec2(ang / 6.28318530718 * 3.0 + t * 0.02, vUnit.y * 0.8 - t * ZenkaiShellSpeed * 0.15)).g;
    float veins = clamp(ZenkaiBodyLook.x, 0.0, 1.0);

    // Las vetas desordenan el borde entre bandas: el límite hierve en vez de ser un anillo liso.
    float i = clamp(edge + (swirl - 0.5) * 0.3 * veins, 0.0, 1.0);
    i *= mix(0.5, 1.0, smoothstep(-0.7, -0.1, vLocalY));

    vec3 tint = ZenkaiShellOuterColor.rgb;
    vec3 deep = deepen(ZenkaiShellCoreColor.rgb, clamp(ZenkaiBodyLook.y, 0.0, 1.0));

    vec3 color;
    float alpha;
    float emitBlend;
    if (i >= ZenkaiShellCel.y) {
        color = mix(lift(tint, 1.6), vec3(1.0), 0.15);
        alpha = ZenkaiShellAlpha.y;
        emitBlend = 1.0;
    } else if (i >= ZenkaiShellCel.z) {
        color = lift(tint, 1.15);
        alpha = ZenkaiShellAlpha.y * 0.85;
        emitBlend = 0.45;
    } else if (i >= ZenkaiShellCel.w) {
        color = tint;
        alpha = mix(ZenkaiShellAlpha.x, ZenkaiShellAlpha.y, 0.6);
        emitBlend = 0.1;
    } else {
        // Interior profundo, con las vetas claras subiendo.
        float vein = step(0.62, swirl) * veins;
        color = mix(deep, tint, vein * 0.7);
        alpha = ZenkaiShellAlpha.x * (1.0 + 0.3 * vein);
        emitBlend = vein * 0.15;
    }
    alpha *= vColor.a;

    alpha *= smoothstep(ZenkaiShellBaseFade.x, ZenkaiShellBaseFade.y, vLocalY);
    // ZenkaiOrtho = 2: la cámara está DENTRO del aura (primera persona propia) y solo ve caras
    // traseras; sin esto el aura propia no se vería nunca en primera persona.
    if (facingRaw < 0.0 && ZenkaiOrtho < 1.5) alpha = min(alpha, 0.01);

    if (ZenkaiBloomMode > 0.5) {
        // Solo el borde brilla: el interior oscuro nunca entra al bloom.
        alpha *= ZenkaiShellBloom.x * mix(0.0, 1.0, emitBlend);
    }

    alpha = clamp(alpha, 0.0, 1.0) * ColorModulator.a;
    if (alpha < 0.004) discard;
    float emit = clamp(mix(ZenkaiShellEmit.x, ZenkaiShellEmit.y, emitBlend), 0.0, 1.0);
    fragColor = vec4(color * ColorModulator.rgb * alpha, alpha * (1.0 - emit));
}
