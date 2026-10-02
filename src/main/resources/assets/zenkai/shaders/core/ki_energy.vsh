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
// EJE DE VUELO (vAxis). BUG CORREGIDO (2026-10-01): antes era `ModelViewMat * (0,0,1)`, suponiendo
// que esa matriz llevaba la orientación de la técnica. No la lleva: los vértices llegan YA con la
// pose del PoseStack horneada en CPU (KiVfxMesh/KiVfxGeometry usan addVertex(pose, ...)), y
// ModelViewMat es solo la matriz de VISTA. Así que vAxis valía siempre el +Z del MUNDO y el
// fresnel axial quitaba de la vista un componente equivocado en cualquier haz que no apuntara a
// +Z — el sombreado del haz cambiaba según hacia dónde se disparaba. Ahora el eje viaja como
// uniform (ZenkaiAxisWorld, ejes de mundo) y aquí se pasa a espacio de vista. Un uniform vale
// porque KiVfxFrameQueue vuelca UN lote por técnica. Lo consume el fragment solo para formas
// alargadas (ZenkaiAxial).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ZenkaiAxisWorld; // eje del haz/hélice en ejes de MUNDO (ver "EJE DE VUELO")
uniform float ZenkaiAxial;    // 1 = forma con eje: campo de ruido continuo, ver vField
uniform float ZenkaiShape;    // 0 SURFACE, 1 RADIAL, 2 RIM: decide el dominio del ruido, ver vField

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
    vAxis    = normalize(mat3(ModelViewMat) * ZenkaiAxisWorld);
    vColor   = Color;
    vUv      = UV0;

    // DOMINIO DEL HERVOR: normal de malla + UV, no la posición de mundo. Con la posición, el
    // patrón se queda anclado al mundo y el proyectil lo atraviesa (se ve como volar a través de
    // una nube fija). Con la normal, el patrón viaja pegado a la superficie. La UV entra porque
    // el disco tiene la normal constante y sin ella no herviría en absoluto.
    if (ZenkaiAxial > 0.5) {
        // FORMAS CON EJE (haz, hélice). Antes: Normal + (UV*2-1), que da tres problemas a la vez:
        // (1) COSTURA — UV.x pasa de 0 a 1 en la MISMA arista del tubo y vField salta 2 unidades;
        // (2) ESCALA — UV.y del tubo va en bloques (decenas) y el de la esfera de la cabeza en 0..1,
        // así que el dentado del haz y el de su cabeza tenían tamaños de diente distintos;
        // (3) dos senos casi paralelos (x,y) daban el rayado en diagonal del zoom.
        // Ahora el campo es una coordenada 3D CONTINUA: la normal (periódica alrededor del tubo,
        // sin costura) más el avance a lo largo del eje, UV.y en RADIOS (KiVfxGeometry.tubeVertex),
        // llevado por el eje real. Alargado a lo largo del flujo (0.2): astillas de unos dos
        // radios de largo, no grano.
        // En la CABEZA esférica el avance por UV.y no basta (va de 0 a 1): sin más, el campo es
        // isótropo y da ~4 romboides por diámetro, los "facetas" de la cabeza. Se comprime el
        // componente de la normal a lo largo del eje (0.35): las mismas astillas se alargan a lo
        // largo del flujo también ahí. En el tubo la normal es perpendicular al eje, así que no
        // le cambia nada.
        vec3 ax = normalize(ZenkaiAxisWorld);
        float along = dot(Normal, ax);
        vField = Normal - ax * (along * 0.65) + ax * (UV0.y * 0.2);
    } else if (ZenkaiShape > 0.5 && ZenkaiShape < 1.5) {
        // RADIAL (disco): la normal es constante en toda la cara, así que sin la UV no herviría.
        vField = Normal + vec3(UV0 * 2.0 - 1.0, 0.0);
    } else {
        // SURFACE/RIM (esferas): la normal ya identifica cada punto de la superficie y es
        // periódica alrededor de la esfera. Sumar la UV solo añadía la costura de la longitud
        // (UV.x salta de 1 a 0 en la misma arista y el campo saltaba 2 unidades).
        vField = Normal;
    }
}
