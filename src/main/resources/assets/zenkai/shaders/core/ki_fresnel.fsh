#version 150

// Fresnel de TRES BANDAS. Es lo que convierte una malla teñida en energía de Dragon Ball:
// núcleo blanco, cuerpo del color de la técnica y contorno más profundo, con los límites entre
// bandas temblando y el borde exterior disolviéndose en el aire.
//
// LOS TRES COLORES SALEN DEL TINTE DEL VÉRTICE, no de uniforms. El color de una técnica lo
// elige el jugador y ya viaja por vértice; derivar aquí el núcleo (tinte lavado a blanco) y el
// contorno (tinte oscurecido) evita tener el mismo color en tres sitios y hace imposible que se
// desincronicen. Los uniforms solo llevan la FORMA de la rampa, que es dirección de arte por
// tipo de técnica y vive en KiVisual.
//
// La banda se decide con `g`, un escalar 0..1 que significa "cuánto núcleo hay aquí". Cómo se
// calcula depende de ZenkaiShape, porque una esfera, un disco plano y una burbuja no tienen el
// mismo centro:
//   0 SURFACE — superficies cerradas (esfera, tubo, hélice). Para una esfera vista desde fuera,
//               `g` resulta ser EXACTAMENTE 1 menos el radio en pantalla: por eso los niveles de
//               banda se pueden leer como porcentajes del radio.
//   1 RADIAL  — superficies planas (disco): núcleo en el centro geométrico, vía UV.
//   2 RIM     — burbuja (barrera): fresnel INVERTIDO. Brilla en el filo y desaparece de frente,
//               que es lo que la hace leer como cristal y no como una bola sólida.
//
// HUBO UN CUARTO MODO, un fresnel cilíndrico que quitaba a la vista su componente a lo largo
// del haz para que el núcleo no se estrechara en el extremo lejano. Se retiró: en la misma
// geometría, SURFACE daba el degradado correcto y él salía plano y con la silueta dura, y
// dependía de un eje subido por uniform que era el único estado por proyectil imposible de
// verificar. El defecto que corregía además desapareció al anclar los haces por la punta.
//
// EL BORDE NO SE CORTA. `edgeFade` (ZenkaiTone.z) es la anchura del desvanecido final, y es el
// parámetro que separa "energía" de "objeto": con un valor pequeño la silueta queda nítida y el
// proyectil se ve como una canica sobrepuesta en la escena por muy bien que estén las bandas de
// dentro.

in vec4 vColor;
in vec3 vNormal;
in vec3 vViewDir;
in vec2 vUv;
in vec3 vField;

uniform vec4 ColorModulator;
uniform float GameTime;
uniform float ZenkaiShape;
uniform vec3 ZenkaiBands;   // x = nivel del núcleo, y = del cuerpo, z = del contorno
uniform vec3 ZenkaiTone;    // x = blancura del núcleo, y = oscurecido del contorno, z = edgeFade
uniform float ZenkaiWobble; // amplitud del hervor (0 = bandas perfectamente quietas)

out vec4 fragColor;

// Amplitud del temblor de cada límite. El de la silueta es el más visible: es el que impide que
// el contorno sea una circunferencia perfecta, que es lo que delata a una esfera de malla.
const float WOBBLE_CORE    = 0.15;
const float WOBBLE_OUTLINE = 0.13;

/** Campo de llama: cuatro senos con frecuencias primas entre sí. No es ruido real y no hace
 *  falta que lo sea — solo tiene que no repetirse a la vista en los pocos segundos que vive un
 *  proyectil, y esto es dos órdenes de magnitud más barato que un simplex. */
float flameField(vec3 p, float t, float freq, float speed) {
    float v  = sin(p.x *  9.0 * freq + t * 4.0 * speed)               * 0.50;
    v       += sin(p.y * 11.0 * freq - t * 5.5 * speed + p.x * 3.0)   * 0.32;
    v       += sin(p.z * 13.0 * freq + t * 6.5 * speed + p.y * 4.0)   * 0.24;
    v       += sin((p.x + p.y + p.z) * 19.0 * freq - t * 8.0 * speed) * 0.16;
    return v;
}

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(vViewDir);

    // GameTime es la única fuente de tiempo: la pone Minecraft cada frame y vale igual para
    // todos los proyectiles, así que no hay que subir un uniform propio por entidad.
    float t = GameTime * 24000.0 * 0.05;

    float g;
    if (ZenkaiShape > 1.5) {                     // RIM
        float f = abs(dot(V, N));
        g = 1.0 - f;
        g = g * g;                               // aprieta el filo: si no, la burbuja se llena
    } else if (ZenkaiShape > 0.5) {              // RADIAL
        g = 1.0 - clamp(vUv.x, 0.0, 1.0);
    } else {                                     // SURFACE
        float f = abs(dot(V, N));
        g = 1.0 - sqrt(max(0.0, 1.0 - f * f));
    }

    float flameCore    = flameField(vField, t, 1.35, 1.30);
    float flameOutline = flameField(vField, t, 0.65, 0.65);

    vec3 tint    = vColor.rgb;
    vec3 core    = mix(tint, vec3(1.0), ZenkaiTone.x);
    vec3 outline = tint * ZenkaiTone.y;

    vec3 col = outline;
    col = mix(col, tint, smoothstep(ZenkaiBands.z, ZenkaiBands.y,
                                    g + flameOutline * WOBBLE_OUTLINE * ZenkaiWobble));
    float toCore = smoothstep(ZenkaiBands.y, ZenkaiBands.x,
                              g + flameCore * WOBBLE_CORE * ZenkaiWobble);
    col = mix(col, core, toCore);

    // Parpadeo de intensidad, más fuerte en el núcleo. Es lo que impide que la técnica se vea
    // como plástico pintado cuando el proyectil está quieto respecto a la cámara.
    col *= 1.0 + flameCore * mix(0.08, 0.20, toCore) * ZenkaiWobble;

    float edge = g + flameOutline * WOBBLE_OUTLINE * ZenkaiWobble;
    float alpha = vColor.a * smoothstep(0.0, max(1.0e-3, ZenkaiTone.z), edge);

    fragColor = vec4(col, alpha) * ColorModulator;
    if (fragColor.a < 0.004) discard;
}
