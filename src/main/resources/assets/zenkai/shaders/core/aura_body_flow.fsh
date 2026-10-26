#version 150

// CUERPO FLOWING / contorno RIM — fragment. Mismo contrato de salida que el resto de cuerpos
// (premultiplicado, cara trasera casi invisible, pasada de bloom).
//
// Lenguaje (aura_6/aura_12/aura_14): interior casi vacío (el cuerpo se lee entero), contorno
// PLATEADO que pasa al color exterior hacia fuera, estrías que suben por la superficie siguiendo el
// flujo del vsh, y MOTAS de luz sueltas (ZenkaiFlowLook.x) que titilan. Con ZenkaiFlowLook.y = 1
// solo se dibuja la banda del borde: es el contorno fino del componente rim.

in vec4 vColor;
in vec3 vNormal;
in vec3 vViewDir;
in float vPeak;
in float vLocalY;
in vec3 vUnit;
in vec2 vFlowUV;

uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec4 ZenkaiAuraLook;     // PRUEBA de dirección de arte (2026-10-26, AuraDebug.look*): x = fps a saltos (0 = suave), y = 1 tres bandas duras, z = tope de blanco del borde (0 = el de siempre), w = relleno de la parte alta (solo la llama)
uniform sampler2D Sampler0;
uniform float ZenkaiBloomMode;
uniform float ZenkaiOrtho;        // 1 = vista ortográfica (GUI)
uniform vec2 ZenkaiShellEmit;
uniform vec2 ZenkaiShellBloom;
uniform vec2 ZenkaiShellAlpha;
uniform vec2 ZenkaiShellFresnel;
uniform vec4 ZenkaiShellCoreColor;
uniform vec4 ZenkaiShellOuterColor;
uniform vec4 ZenkaiShellCel;
uniform vec4 ZenkaiFlowLook;        // x = motas, y = solo borde, z = suavizado de bandas, w = 1 en la pre-pasada de profundidad

out vec4 fragColor;

vec3 lift(vec3 c, float k) {
    float m = max(max(c.r, c.g), c.b);
    return c / max(m, 0.001) * min(1.0, m * k);
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

    float streak = texture(Sampler0, vFlowUV * vec2(1.0, 2.5)).g;
    // ZenkaiFlowLook.y >= 2 = contorno ESTABLE (Beast forzando): sin estrías. Su textura se muestrea con
    // la altura de MUNDO, así que al volar pasa a toda velocidad por la banda y el borde titila.
    bool steady = ZenkaiFlowLook.y > 1.5;
    float i = clamp(edge + (streak - 0.5) * (steady ? 0.0 : 0.25), 0.0, 1.0);
    float w = max(ZenkaiFlowLook.z, 0.005);
    float rimB = smoothstep(ZenkaiShellCel.y - w, ZenkaiShellCel.y + w, i);
    float lightB = smoothstep(ZenkaiShellCel.z - w, ZenkaiShellCel.z + w, i);

    // Del contorno plateado (el color interior, casi blanco en UI) al exterior azul/violeta.
    vec3 silver = mix(lift(ZenkaiShellCoreColor.rgb, 1.5), vec3(1.0), 0.35);
    vec3 outer = ZenkaiShellOuterColor.rgb;
    vec3 color = mix(outer, lift(outer, 1.3), lightB);
    color = mix(color, silver, rimB);
    float alpha = mix(ZenkaiShellAlpha.x, ZenkaiShellAlpha.y * 0.7, lightB);
    alpha = mix(alpha, ZenkaiShellAlpha.y, rimB);
    float emitBlend = max(lightB * 0.5, rimB);

    // Solo borde (rim): fuera de la banda del borde, nada.
    if (ZenkaiFlowLook.y > 0.5) {
        alpha = ZenkaiShellAlpha.y * rimB;
        color = steady ? lift(outer, 1.15) : mix(outer, silver, 0.6);
        emitBlend = 1.0;
    }

    // Motas de luz: puntos del ruido fino por encima de un umbral, titilando con el tiempo.
    float t = GameTime * 1200.0;
    // Movimiento A SALTOS (prueba de dirección de arte): el tiempo avanza a pocos fotogramas por
    // segundo, como animación dibujada; 0 = continuo.
    if (ZenkaiAuraLook.x > 0.0) t = floor(t * ZenkaiAuraLook.x) / ZenkaiAuraLook.x;
    float fleck = texture(Sampler0, vFlowUV * vec2(5.0, 4.0) + vec2(0.0, t * 0.05)).r;
    float fleckOn = step(0.9, fleck) * ZenkaiFlowLook.x * (0.6 + 0.4 * sin(t * 9.0 + fleck * 40.0));
    color = mix(color, vec3(1.0), clamp(fleckOn, 0.0, 1.0));
    alpha = max(alpha, clamp(fleckOn, 0.0, 1.0) * 0.9);
    emitBlend = max(emitBlend, fleckOn);

    if (ZenkaiFlowLook.y > 2.5) {
        // CONTORNO DE SILUETA (Beast forzando): solo las caras TRASERAS de las cajas infladas, lisas y
        // del color puro. El cuerpo real, más cerca de la cámara, las tapa por profundidad y queda
        // únicamente el borde que asoma alrededor de la silueta (casco invertido).
        if (facingRaw >= 0.0) discard;
        color = lift(outer, 1.15);
        alpha = ZenkaiShellAlpha.y;
        emitBlend = 1.0;
    } else if (facingRaw < 0.0) {
        alpha = min(alpha, 0.01);
    }

    if (ZenkaiBloomMode > 0.5) {
        alpha *= ZenkaiShellBloom.x * mix(ZenkaiShellBloom.y, 1.0, emitBlend);
    }

    alpha = clamp(alpha, 0.0, 1.0) * ColorModulator.a;
    // En la pre-pasada de profundidad (solo escribe depth, sin color) NO se descarta nada: tiene que
    // quedar la silueta entera de la unión de cápsulas, también donde el color sería transparente.
    if (alpha < 0.004 && ZenkaiFlowLook.w < 0.5) discard;
    float emit = clamp(mix(ZenkaiShellEmit.x, ZenkaiShellEmit.y, emitBlend), 0.0, 1.0);
    fragColor = vec4(color * ColorModulator.rgb * alpha, alpha * (1.0 - emit));
}
