#version 150

// LLAMA del contorno de silueta (SilhouetteFlameRenderer, 2026-10-26, referencia smoke.png del
// usuario): lenguas finas pegadas al borde REAL del jugador que lamen hacia arriba, en dos bandas
// duras (interior clara pegada al cuerpo, exterior del color de fuera). Sin degradado continuo, como
// el resto del aura.
//
// El "arriba" es el del jugador EN PANTALLA (Up), no el de la pantalla: con la cámara inclinada las
// lenguas siguen saliendo hacia su cabeza. Las coordenadas del ruido van en unidades de grosor, así
// el dibujo no cambia con la distancia.

uniform sampler2D Sampler0; // máscara: R = ranura, A = cubierto
uniform sampler2D Sampler1; // campo de esta ranura (media resolución)
uniform sampler2D Sampler2; // aura_noise.png
uniform vec2 ScreenSize;
uniform float Id;
uniform float Thickness;    // píxeles
uniform vec2 Up;
uniform float Time;
uniform vec4 Look;          // x = largo de lenguas, y = velocidad, z = opacidad
uniform vec3 Inner;
uniform vec3 Outer;

in vec2 texCoord;
out vec4 fragColor;

float noiseR(vec2 uv) { return texture(Sampler2, fract(uv)).r; }
float noiseG(vec2 uv) { return texture(Sampler2, fract(uv)).g; }

void main() {
    vec2 frag = texCoord * ScreenSize;
    vec4 m = texture(Sampler0, texCoord);
    bool self = m.a > 0.5 && abs(m.r - Id) < 0.03;
    float near = texture(Sampler1, texCoord).r;

    vec2 side = vec2(Up.y, -Up.x);
    vec2 p = vec2(dot(frag, side), dot(frag, Up)) / Thickness;
    float rise = Time * 2.6 * Look.y;

    if (self) {
        // Sobre el cuerpo: solo una línea clara justo en el borde (la de la referencia), que tiembla.
        float flick = noiseG(vec2(p.x * 0.21, (p.y - rise * 1.3) * 0.09));
        float rim = 1.0 - step(0.62 + 0.18 * flick, near);
        if (rim < 0.5) discard;
        fragColor = vec4(Inner, Look.z * 0.9);
        return;
    }

    // Fuera: el campo leído DESPLAZADO hacia abajo saca lenguas hacia arriba. Un ruido grande decide
    // dónde hay lengua larga (pocas y altas, como smoke.png) y uno fino rompe el borde para que no sea un
    // halo liso; las puntas se adelgazan al subir.
    float big = noiseR(vec2(p.x * 0.13, (p.y - rise) * 0.035));
    float fine = noiseG(vec2(p.x * 0.33 + 0.37, (p.y - rise * 1.6) * 0.15));
    float tongue = smoothstep(0.42, 0.92, big);
    float lick = (0.35 + 4.2 * tongue * tongue) * Look.x;
    float sway = noiseG(vec2(p.x * 0.05 + 0.5, (p.y - rise * 0.6) * 0.04)) - 0.5;
    vec2 shifted = frag - (Up * lick + side * sway * 0.8 * lick) * Thickness;
    float lifted = texture(Sampler1, shifted / ScreenSize).r;
    float density = max(lifted, near) - fine * 0.30 * (1.0 - near);

    // Motas de vapor (clip del Legendario): puntos sueltos que suben junto al contorno.
    vec2 cell = floor(vec2(p.x * 0.9, (p.y - rise * 1.1) * 0.9));
    float h = fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
    vec2 inCell = fract(vec2(p.x * 0.9, (p.y - rise * 1.1) * 0.9)) - 0.5;
    float halo = texture(Sampler1, (frag - Up * 1.5 * Thickness) / ScreenSize).r;
    bool mote = h > 0.93 && dot(inCell, inCell) < 0.06 && halo > 0.02 && density < 0.10;
    if (mote) {
        fragColor = vec4(mix(Outer, Inner, step(0.97, h)), Look.z * 0.9);
        return;
    }

    if (density < 0.10) discard;
    // Banda interior FINA (smoke.png: una línea amarilla pegada al cuerpo; el resto, lenguas verdes).
    vec3 color = density > 0.48 ? Inner : Outer;
    float alpha = density > 0.48 ? Look.z : Look.z * 0.85;
    fragColor = vec4(color, alpha);
}
