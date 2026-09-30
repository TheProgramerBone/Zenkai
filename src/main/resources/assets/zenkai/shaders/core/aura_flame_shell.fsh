#version 150

// LLAMA EXTERIOR del sistema de aura nuevo — fragment. Port del fresnel de dbrebirth-0.3
// (aura.fsh): el color y el alfa van del centro (de frente a la cámara) al borde (de canto) por
// fresnel, y la cara trasera se oculta POR ALFA en vez de por culling (el RenderType es NO_CULL):
// una malla que envuelve al jugador no puede usar cull FRONT como el aro legacy sin taparlo.
//
// Centro casi transparente A PROPÓSITO: en las referencias (anime SSJ) el cuerpo se lee dentro
// del aura; lo que se ve es el contorno.
//
// SALIDA PREMULTIPLICADA (mismo contrato que ki_energy.fsh / KiVfxRenderTypes):
// vec4(color*alfa, alfa*(1-emit)). Emisión (Fase 4) del cuerpo al borde: el contorno suma luz y
// mantiene el color saturado sobre un cielo de día; el cuerpo sigue tapando lo bastante para no
// lavarse a blanco al superponer capas.
//
// NÚCLEO CALIENTE: ZenkaiShellCoreColor llega ya como la capa caliente del tinte
// (KiVfxColors.derivedHot, azul -> cian, amarillo -> blanco cálido), nunca como blanco puro.
//
// PASADA DE BLOOM (ZenkaiBloomMode, KiVfxFrameQueue.bloomPass): la MISMA malla redibujada sobre
// negro en el target de bloom de KiVfxBloomPipeline. Solo cambia cuánto aporta cada píxel: el
// borde pesa entero y el cuerpo poco — el sangrado nace del contorno (aura_8), y una llama que
// llena media pantalla no la blanquea.

in vec4 vColor;
in vec3 vNormal;
in vec3 vViewDir;
in float vPeak;
in float vLocalY;
in vec3 vUnit;

uniform vec4 ColorModulator;
uniform float GameTime;
uniform sampler2D Sampler0;         // aura_noise.png (canal G: rayas verticales)
uniform float ZenkaiShellSpeed;
uniform float ZenkaiShellStreak;    // 0 = superficie lisa, 1 = rayas muy marcadas
uniform float ZenkaiBloomMode;      // 1 = pasada de bloom
uniform vec2 ZenkaiShellEmit;       // x = emisión del cuerpo, y = del borde
uniform vec2 ZenkaiShellBloom;      // x = peso general en bloom, y = peso del cuerpo frente al borde
uniform vec2 ZenkaiShellAlpha;      // x = alfa del centro, y = alfa del borde
uniform vec2 ZenkaiShellFresnel;    // x = exponente, y = divisor
uniform vec4 ZenkaiShellCoreColor;  // color del centro de ESTA capa (su tinte, algo calentado)
uniform vec4 ZenkaiShellOuterColor; // color del borde de ESTA capa (su tinte saturado)

out vec4 fragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(vViewDir);
    float facingRaw = dot(V, N);
    if (facingRaw < 0.0) N = -N;

    float facingAbs = abs(dot(V, N));
    float edge = clamp(pow(1.0 - facingAbs, ZenkaiShellFresnel.x) / max(ZenkaiShellFresnel.y, 0.001), 0.0, 1.0);
    // Las puntas de los picos tiran hacia el color del borde (solo COLOR).
    float blend = clamp(max(edge, vPeak * 0.6), 0.0, 1.0);
    // OPACIDAD y EMISIÓN con mucho menos peso de las puntas (2026-09-29, capturas de Blue+Kaioken y
    // Beast): con el mismo 0.6 que el color, las lenguas vistas de frente en la columna central se
    // volvían casi opacas y emisivas, y al solaparse varias sumaban luz hasta el blanco puro — el
    // "lavado a blanco" que el aura no debe tener. De frente la llama tiene que seguir siendo casi
    // transparente; el contorno es lo que se lee.
    float alphaBlend = clamp(max(edge, vPeak * 0.25), 0.0, 1.0);

    // RAYAS DE ENERGÍA (Fase 3): canal G de aura_noise.png, estirado en vertical, subiendo más
    // rápido que las lenguas — las estrías que recorren la llama en aura_3. UV calculada aquí por
    // píxel desde el punto de la esfera (ver vUnit en el vsh); U x3, entero, sin costura en atan.
    float t = GameTime * 1200.0;
    float ang = atan(vUnit.z, vUnit.x);
    vec2 streakUV = vec2(ang / 6.28318530718 * 3.0, vUnit.y * 0.6 - t * ZenkaiShellSpeed * 1.6);
    float streak = texture(Sampler0, streakUV).g;
    float streakMix = clamp(ZenkaiShellStreak, 0.0, 1.0);

    vec3 color = mix(ZenkaiShellCoreColor.rgb, ZenkaiShellOuterColor.rgb, blend);
    // Brillo y opacidad modulados por la raya: nunca oscurece por debajo del 80% (un aura
    // oscurecida se lee sucia, lección de aura.md §2).
    color *= mix(1.0, mix(0.8, 1.15, streak), streakMix);
    float alpha = mix(ZenkaiShellAlpha.x, ZenkaiShellAlpha.y, alphaBlend) * vColor.a;
    alpha *= mix(1.0, mix(0.55, 1.2, streak), streakMix);
    // La base se funde en vez de cortarse contra el suelo. Banda subida (antes -1.0..-0.55): el
    // casquete inferior de la elipsoide asomaba sobre el suelo como un "cuenco" con el borde
    // encendido por el fresnel (visto en juego); en las referencias las lenguas NACEN del suelo.
    alpha *= smoothstep(-0.8, -0.3, vLocalY);
    // Cara trasera casi invisible (mecanismo real de dbrebirth: finalAlpha = 0.01).
    if (facingRaw < 0.0) alpha = min(alpha, 0.01);

    if (ZenkaiBloomMode > 0.5) {
        alpha *= ZenkaiShellBloom.x * mix(ZenkaiShellBloom.y, 1.0, alphaBlend);
    }

    alpha = clamp(alpha, 0.0, 1.0) * ColorModulator.a;
    if (alpha < 0.004) discard;
    float emit = clamp(mix(ZenkaiShellEmit.x, ZenkaiShellEmit.y, alphaBlend), 0.0, 1.0);
    fragColor = vec4(color * ColorModulator.rgb * alpha, alpha * (1.0 - emit));
}
