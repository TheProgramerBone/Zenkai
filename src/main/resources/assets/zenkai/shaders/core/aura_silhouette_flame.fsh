#version 150

// LLAMA del contorno de silueta (SilhouetteFlameRenderer, 2026-10-26, referencia smoke.png del
// usuario): lenguas finas pegadas al borde REAL del jugador que lamen hacia arriba, en dos bandas
// duras (interior clara pegada al cuerpo, exterior del color de fuera). Sin degradado continuo, como
// el resto del aura.
//
// El "arriba" es el del jugador EN PANTALLA (Up), no el de la pantalla: con la cámara inclinada las
// lenguas siguen saliendo hacia su cabeza. Las coordenadas del ruido van en unidades de grosor, así
// el dibujo no cambia con la distancia.
//
// OCLUSIÓN (2026-10-26): la pasada va sin test de depth, así que fuera del cuerpo se compara a mano la
// escena (Sampler3, depth copiado a R32F) con la distancia del cuerpo cercano que propaga el campo (G/R):
// si lo de la escena está claramente MÁS CERCA, algo tapa ese trozo del contorno y no se pinta. Antes las
// lenguas salían por encima de los bloques que tapaban al jugador. La línea clara de DENTRO tampoco se
// pinta en un corte con algo que tapa (el borde de arriba de un bloque delante de las piernas): solo
// donde al lado hay borde de verdad (lo de detrás del jugador).
//
// DEPTH (2026-10-26): la llama ESCRIBE depth (el del cuerpo cercano, nunca más lejos que la escena).
// Lo translúcido, las partículas y las entidades de bloque se dibujan DESPUÉS de esta pasada: sin
// depth, lo que estaba DETRÁS del jugador (agua, cristal, partículas) se pintaba encima de las lenguas.
// Lo que está delante sigue pasando el test y la tiñe, como al cuerpo.

uniform sampler2D Sampler0; // máscara: R = ranura, A = cubierto
uniform sampler2D Sampler1; // campo de esta ranura (media resolución)
uniform sampler2D Sampler2; // aura_noise.png
uniform sampler2D Sampler3; // depth de la escena (R32F)
uniform vec2 ProjParams;    // m22 y m32 de la proyección
uniform float Occlusion;    // 1 = descartar lo tapado (0 solo para comparar en el arnés)
uniform float WriteDepth;   // 1 = escribir el depth del cuerpo (0 solo para comparar en el arnés)
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

// Distancia del cuerpo cercano según el campo (muestra con más cobertura de las dos); 1e4 si no hay.
float bodyDist(vec4 a, vec4 b) {
    vec4 f = a.r >= b.r ? a : b;
    return f.r > 0.001 ? f.g / f.r : 1.0e4;
}

// Lo de la escena está delante del contorno: margen de medio bloque, las lenguas tienen volumen y el
// suelo justo delante de los pies no debe comérselas.
float linearAt(vec2 uv) {
    return ProjParams.y / ((texture(Sampler3, uv).r * 2.0 - 1.0) + ProjParams.x);
}

bool hidden(vec4 a, vec4 b) {
    if (Occlusion < 0.5) return false;
    return linearAt(texCoord) < bodyDist(a, b) - 0.5;
}

// Depth del fragmento: el del cuerpo a esa distancia, sin alejar nunca lo que ya hay en la escena (el
// suelo justo delante de los pies, dentro del margen de hidden()).
void setDepth(float dist) {
    float scene = texture(Sampler3, texCoord).r;
    if (WriteDepth < 0.5 || dist > 1.0e3) {
        gl_FragDepth = scene;
        return;
    }
    float ndc = ProjParams.y / dist - ProjParams.x;
    gl_FragDepth = min(clamp(ndc * 0.5 + 0.5, 0.0, 1.0), scene);
}

// ¿Es este píxel del borde interior un borde DE VERDAD? Busca alrededor (hasta el grosor) un píxel que
// no sea del jugador y cuya escena NO esté delante de él: el fondo. Si solo hay cosas que lo tapan, es
// un corte (bloque, otra entidad) y no lleva línea.
bool realEdge(float bodyD) {
    if (Occlusion < 0.5) return true;
    for (int r = 1; r <= 3; r++) {
        float rad = Thickness * 0.35 * float(r);
        for (int k = 0; k < 8; k++) {
            float a = float(k) * 0.785398;
            vec2 uv = texCoord + vec2(cos(a), sin(a)) * rad / ScreenSize;
            vec4 n = texture(Sampler0, uv);
            if (n.a > 0.5 && abs(n.r - Id) < 0.03) continue;
            if (linearAt(uv) >= bodyD - 0.5) return true;
        }
    }
    return false;
}

void main() {
    vec2 frag = texCoord * ScreenSize;
    vec4 m = texture(Sampler0, texCoord);
    bool self = m.a > 0.5 && abs(m.r - Id) < 0.03;
    vec4 nearS = texture(Sampler1, texCoord);
    float near = nearS.r;

    vec2 side = vec2(Up.y, -Up.x);
    vec2 p = vec2(dot(frag, side), dot(frag, Up)) / Thickness;
    float rise = Time * 2.6 * Look.y;

    if (self) {
        // Sobre el cuerpo: solo una línea clara justo en el borde (la de la referencia), que tiembla.
        float flick = noiseG(vec2(p.x * 0.21, (p.y - rise * 1.3) * 0.09));
        float rim = 1.0 - step(0.62 + 0.18 * flick, near);
        if (rim < 0.5 || !realEdge(m.g)) discard;
        setDepth(m.g);
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
    vec4 liftedS = texture(Sampler1, shifted / ScreenSize);
    float lifted = liftedS.r;
    float density = max(lifted, near) - fine * 0.30 * (1.0 - near);

    // Motas de vapor (clip del Legendario): puntos sueltos que suben junto al contorno.
    vec2 cell = floor(vec2(p.x * 0.9, (p.y - rise * 1.1) * 0.9));
    float h = fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
    vec2 inCell = fract(vec2(p.x * 0.9, (p.y - rise * 1.1) * 0.9)) - 0.5;
    vec4 haloS = texture(Sampler1, (frag - Up * 1.5 * Thickness) / ScreenSize);
    float halo = haloS.r;
    bool mote = h > 0.93 && dot(inCell, inCell) < 0.06 && halo > 0.02 && density < 0.10;
    if (mote) {
        if (hidden(nearS, haloS)) discard;
        setDepth(bodyDist(nearS, haloS));
        fragColor = vec4(mix(Outer, Inner, step(0.97, h)), Look.z * 0.9);
        return;
    }

    if (density < 0.10 || hidden(nearS, liftedS)) discard;
    setDepth(bodyDist(nearS, liftedS));
    // Banda interior FINA (smoke.png: una línea amarilla pegada al cuerpo; el resto, lenguas verdes).
    vec3 color = density > 0.48 ? Inner : Outer;
    float alpha = density > 0.48 ? Look.z : Look.z * 0.85;
    fragColor = vec4(color, alpha);
}
