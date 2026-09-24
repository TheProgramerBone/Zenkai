#version 150

// Vértice del pipeline de VFX de ki. No hace nada de arte: prepara los datos que el fragment
// necesita para decidir la banda — normal, dirección de vista, EJE de vuelo y un dominio estable
// para el hervor. TODOS en el MISMO espacio (STEP 5 de la auditoría de coordinate spaces).
//
// ESPACIO: los vértices llegan YA transformados por el PoseStack (Minecraft hornea la matriz de
// modelo en CPU), así que `ModelViewMat` aquí es prácticamente la matriz de VISTA y `Position`
// llega en espacio de OBJETO/LOCAL de la técnica (eje de vuelo = +Z local, ver KiVfxGeometry).
// `ModelViewMat * Position` da espacio de VISTA — el mismo espacio en el que se calculan
// `vNormal`, `vViewDir` y `vAxis` a continuación, así que el fragment nunca compara vectores de
// espacios distintos (la comprobación explícita que pedía el STEP 5/6 de la auditoría: NO hay
// mezcla world-space + view-space en ningún punto de este pipeline).
//
// EJE DE VUELO (vAxis). KiVfxGeometry genera SIEMPRE con +Z local como eje de vuelo (haz,
// hélice). Transformar ese vector CONSTANTE por la MISMA matriz que ya usa la normal da el eje EN
// ESPACIO DE VISTA sin ningún uniform nuevo por proyectil — nada que un lote de varios
// proyectiles distintos pueda desincronizar entre sí. Lo consume el fragment solo para formas
// alargadas (ZenkaiAxial), pero calcularlo siempre es barato (un vértice más transformado) y
// evita una segunda variante de shader.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vColor;
out vec3 vNormal;
out vec3 vViewDir;
out vec3 vAxis;
out vec2 vUv;
out vec3 vField;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    vNormal  = normalize(mat3(ModelViewMat) * Normal);
    vViewDir = normalize(-viewPos.xyz);
    vAxis    = normalize(mat3(ModelViewMat) * vec3(0.0, 0.0, 1.0));
    vColor   = Color;
    vUv      = UV0;

    // DOMINIO DEL HERVOR: normal de malla + UV, no la posición de mundo. Con la posición, el
    // patrón se queda anclado al mundo y el proyectil lo atraviesa (se ve como volar a través de
    // una nube fija). Con la normal, el patrón viaja pegado a la superficie. La UV entra porque
    // el disco tiene la normal constante y sin ella no herviría en absoluto.
    vField = Normal + vec3(UV0 * 2.0 - 1.0, 0.0);
}
