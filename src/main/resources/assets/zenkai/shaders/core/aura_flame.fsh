#version 150

// FASE 1 del sistema de aura NUEVO (estilo dbrebirth-0.3, ver
// .claude/pendiente/aura-dbrebirth-sistema-propuesta.md). A diferencia de aura_rim.fsh (fragment
// mínimo — el aro lo produce el cull FRONT + depth-test del RenderType, ver ModAuraRenderType),
// esta malla ENVUELVE al jugador varias veces su tamaño: el mismo truco de cull FRONT taparía al
// jugador entero desde dentro (mismo problema ya documentado para BARRIER/EXPLOSION en
// CLAUDE.md). En vez de eso, AuraFlameRenderType usa NO_CULL y este fragment shader oculta la
// cara trasera POR ALFA — mecanismo real de dbrebirth (ver el análisis en la propuesta): la
// normal en espacio de vista (vNormal) señala hacia afuera de la malla; si mira en la misma
// dirección que la cámara (facingRaw < 0, la cara "de espaldas" a quien mira) se atenúa casi del
// todo en vez de dibujarse — así, cuando la cámara queda DENTRO de la malla (primera persona con
// una llama grande), casi toda la superficie visible es "cara trasera" desde su propio punto de
// vista y desaparece sola, sin necesitar un modo de cull distinto para ese caso.

in vec4 vColor;
in vec3 vNormal;
in vec3 vViewDir;
in vec2 vUv;
in float vBlend;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 ZenkaiFlameCoreColor;  // color del núcleo (AuraColors.Layers.inner)
uniform vec4 ZenkaiFlameOuterColor; // color de la envolvente (AuraColors.Layers.outer, o = inner si no hay capa exterior)

out vec4 fragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(vViewDir);
    float facingRaw = dot(N, V);

    vec4 tex = texture(Sampler0, vUv);
    vec3 tint = mix(ZenkaiFlameCoreColor.rgb, ZenkaiFlameOuterColor.rgb, clamp(vBlend, 0.0, 1.0));

    float alpha = tex.a * vColor.a;
    if (facingRaw < 0.0) alpha *= 0.01; // cara trasera casi invisible, ver comentario de cabecera

    fragColor = vec4(tint, alpha) * ColorModulator;
    if (fragColor.a < 0.004) discard;
}
