package com.hmc.zenkai.client.gui.wheel;

import com.hmc.zenkai.client.overlay.RadialGauge;
import com.hmc.zenkai.feature.wheel.WheelSelectPacket;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Menú radial (mantener X). Screen y no overlay: se queda abierto al soltar la tecla y se
 * elige con clic, así que hacen falta cursor real, clics y Esc, que un Screen da gratis.
 * isPauseScreen() -> false: el mundo sigue corriendo, esto se usa en combate.
 * La rueda NO ejecuta transformaciones: SELECCIONA. Ver WheelMenu.
 * Los hijos de un nodo pueden ser categorías (se despliegan en el anillo siguiente, dentro de
 * su propio sector) u hojas sueltas (interruptores como el Kaioken, que actúan al clic).
 * Cada sector se pinta en tres pasadas: contorno negro, relleno con degradado radial
 * (oscuro dentro, claro fuera) y brillo superior al pasar el ratón. Sin eso se ve plano.
 *
 * PROFUNDIDAD (0.3.0): antes esto solo soportaba EXACTAMENTE 2 anillos fijos (hoverRoot/
 * hoverChild como dos int sueltos) pese a que {@link WheelNode} se documenta como un árbol
 * recursivo — una categoría dentro de otra categoría se dibujaba pero no se podía pulsar
 * (bug real, confirmado en juego 2026-09-28: la sub-rama "Arcosian" dentro de "Forms" no
 * respondía al clic). {@code hoverPath} generaliza esto a profundidad ARBITRARIA (acotada solo
 * por {@link #MAX_DEPTH}, un tope defensivo contra un árbol cíclico de un datapack roto, no un
 * límite de diseño): cada anillo subdivide el sector del nodo elegido en el anillo anterior, y
 * tanto {@link #pick}, {@link #render} como {@link #mouseClicked} recorren el mismo camino de
 * índices en vez de tener un caso especial por nivel.
 */
public class WheelScreen extends Screen {

    // Rueda compacta y de bandas iguales: cuanto más parecidos son el hueco y el grosor de
    // cada anillo, más "redonda" se lee y menos ocupa la pantalla. BAND_WIDTH/RING_GAP
    // reproducen EXACTAMENTE los radios que antes eran R0_IN=36/R0_OUT=72/R1_IN=74/R1_OUT=110
    // (ringIn(0)=36, ringOut(0)=72, ringIn(1)=74, ringOut(1)=110) — ver ringIn()/ringOut().
    private static final float R_HOLE     = 34f;   // zona muerta central = cancelar
    private static final float BAND_WIDTH = 36f;
    private static final float RING_GAP   = 2f;

    /** Tope defensivo de anillos a dibujar/gestionar — NO es un límite de diseño de cuántos
     *  niveles puede tener el menú, solo evita un bucle sin fin si algún día un árbol de
     *  WheelNode construido a mano tuviera un ciclo. La profundidad real de hoy (Forms ->
     *  categoría de raza -> forma) usa 3; esto deja margen de sobra para crecer sin tocar
     *  esta clase otra vez. */
    private static final int MAX_DEPTH = 6;

    private static final float HOVER_POP = 3f;   // el sector apuntado sobresale
    private static final float OUTLINE   = 1.5f; // grosor del contorno negro
    private static final float GAP_DEG   = 2.2f; // aire entre sectores: el look segmentado

    /** Contorno de sector: casi negro para que los sectores no se fundan entre sí sobre el
     *  mundo. Es estructura de la rueda, no un color de estado, pero se nombra desde la paleta
     *  para que no quede como un literal huérfano. */
    private static final int COL_OUTLINE  = ZenkaiPalette.withAlpha(0xFF000000, 0xE6);
    private static final int COL_DISABLED = 0x606068;
    /** Base oscura sobre la que se tiñe cada sector. */
    private static final int COL_BACKDROP = 0x101014;

    // En reposo casi no hay color: el sector es gris oscuro y el nombre es lo que se lee.
    // Al apuntarlo salta al color propio a tope, que es lo que da el golpe de brillo.
    private static final float TINT_IDLE  = 0.18f;
    private static final float TINT_HOVER = 0.85f;

    private static final float LABEL_SCALE = 0.72f;  // letra más pequeña
    private static final int   LABEL_WRAP  = 84;     // ancho de corte, en px ya escalados

    // Solución de solapamiento (arfc29.mp4, referencia del usuario 2026-09-28), en dos escalones:
    //   1) Gajo estrecho (< ROTATE_BELOW_DEG) -> la etiqueta se gira para salir "como una aguja"
    //      desde el borde interior del gajo hacia fuera en vez de quedarse siempre horizontal
    //      (ver labelAt). El hueco angular que necesita pasa de ser su ANCHO (grande) a ser su
    //      GROSOR de línea (pequeño), así que caben muchos más hermanos antes de solaparse.
    //   2) Ni así cabe (Arcosian con 7 formas en el mismo gajo de "Forms", ~2-3° cada una) -> el
    //      anillo entero se reemplaza por una lista con el estilo de tooltip real de Minecraft
    //      (ver drawListBox) en vez de seguir intentando meter más gajos.
    // listBoxFor() decide esto por adelantado (usando el mismo umbral de rotación que render())
    // para el paso 1, y el ancho/alto real de la etiqueta más larga para el paso 2.
    private static final float ROTATE_BELOW_DEG = 30f;
    // El texto radial ("aguja") solo se usa a partir del TERCER nivel (depth >= 2: las
    // transformaciones). Los dos primeros anillos (categorías raíz y su nivel "Race/Super Forms/
    // Divine...") siempre van horizontales, con la lista lateral como red si no caben (pedido
    // del usuario, 2026-09-29).
    private static final int ROTATE_FROM_DEPTH = 2;
    private static final float ROTATE_STAGGER   = 11f; // radio extra de los gajos impares al rotar
    // LIST_FIT_MARGIN > 1 = cuánto puede la etiqueta más exigente "invadir" el hueco de su
    // vecina (un roce leve se lee bien) antes de rendirse a la lista — 1.0 sería el punto
    // exacto en que dos etiquetas del tamaño máximo llegarían a tocarse justo por el borde.
    private static final float LIST_FIT_MARGIN = 1.4f;
    private static final int   LIST_PAD        = 4;

    // Mismos colores EXACTOS que usa Minecraft para el fondo/borde de sus propios tooltips
    // (Screen#renderTooltipInternal) — para que la lista de emergencia se lea como parte del
    // juego en vez de un rectángulo negro pegado encima.
    private static final int TOOLTIP_BG           = 0xF0100010;
    private static final int TOOLTIP_BORDER_START = 0x505000FF;
    private static final int TOOLTIP_BORDER_END   =
            (TOOLTIP_BORDER_START & 0xFEFEFE) >> 1 | (TOOLTIP_BORDER_START & 0xFF000000);

    private final WheelNode root;

    /** Camino de índices hasta el nodo más profundo bajo el ratón AHORA MISMO. Vacío = fuera
     *  de la rueda (centro o más allá del último anillo con contenido). El índice en la
     *  posición {@code d} es el hijo elegido dentro del sector del nodo de la posición
     *  {@code d-1} (o de la raíz si d=0) — NUNCA un índice absoluto sobre el círculo completo. */
    private List<Integer> hoverPath = new ArrayList<>();

    // Estado "pegajoso" de la lista lateral actualmente abierta (si la hay). La caja vive en
    // coordenadas de PANTALLA, no polares — si se recalculara el gajo elegido por ángulo cada
    // fotograma, mover el ratón HACIA la caja (que sobresale del círculo) cambiaría el ángulo
    // respecto al centro lo bastante como para que pick() eligiera OTRO hermano a mitad de
    // camino, y la lista se cerraría antes de poder alcanzarla. Mientras el ratón siga dentro
    // del gajo original (por ángulo, a cualquier radio) o dentro de la caja, se conserva el
    // mismo camino en vez de recalcularlo desde cero.
    private ListBox stickyBox;
    private List<Integer> stickyPrefix = List.of();
    private float stickyAngStart, stickyAngSize;

    public WheelScreen(WheelNode root) {
        super(Component.translatable("wheel.zenkai.title"));
        this.root = root;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Geometría ────────────────────────────────────────────────────────────

    private float cx() { return this.width / 2f; }
    private float cy() { return this.height / 2f; }

    private List<WheelNode> roots() { return root.children(); }

    private static float ringIn(int depth) { return R_HOLE + RING_GAP + depth * (BAND_WIDTH + RING_GAP); }
    private static float ringOut(int depth) { return ringIn(depth) + BAND_WIDTH; }

    /**
     * Recorre el árbol por ÁNGULO desde la raíz, profundidad a profundidad, subdividiendo en
     * cada nivel el sector del nodo ya elegido (nunca el círculo completo salvo en depth 0).
     * Se PARA en la profundidad donde cae {@code r} (el ratón está dentro de ese anillo, {@code
     * r < ringIn(depth+1)} — el hueco entre dos anillos cuenta como parte del de DENTRO, igual
     * que antes) o donde el nodo elegido ya no tiene hijos que ofrecer un anillo más.
     * false = centro o fuera de la rueda: el gesto de cancelar.
     */
    private boolean pick(double mouseX, double mouseY) {
        float dx = (float) mouseX - cx();
        float dy = (float) mouseY - cy();
        float r = Mth.sqrt(dx * dx + dy * dy);
        float ang = (float) Math.toDegrees(Math.atan2(dy, dx));

        // Atajo pegajoso: si ya había una lista abierta y el ratón sigue dentro de su gajo
        // original o de la caja, se conserva el mismo camino sin volver a calcular por ángulo.
        if (stickyBox != null) {
            boolean inWedgeAngle = r >= R_HOLE && norm(ang - stickyAngStart) < stickyAngSize;
            int row = stickyBox.rowAt(mouseX, mouseY);
            if (inWedgeAngle || row >= 0) {
                hoverPath = new ArrayList<>(stickyPrefix);
                if (row >= 0) hoverPath.add(row);
                return true;
            }
            stickyBox = null;
        }

        hoverPath = new ArrayList<>();
        if (r < R_HOLE || roots().isEmpty()) return false;

        List<WheelNode> level = roots();
        float spanStart = -90f, spanSize = 360f;

        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            int n = level.size();
            if (n == 0) break;

            float size = spanSize / n;
            int idx = Mth.clamp((int) (norm(ang - spanStart) / size), 0, n - 1);
            hoverPath.add(idx);

            if (r < ringIn(depth + 1)) break; // el ratón cae DENTRO de este anillo: para aquí

            WheelNode chosen = level.get(idx);
            List<WheelNode> kids = chosen.children();
            if (kids.isEmpty()) break; // nada más que ofrecer un anillo más adentro... afuera

            float chosenStart = spanStart + idx * size;
            ListBox lb = listBoxFor(kids, depth + 1, size / kids.size(),
                    (ringIn(depth + 1) + ringOut(depth + 1)) / 2f, chosenStart + size / 2f);
            if (lb != null) {
                stickyBox = lb;
                stickyPrefix = new ArrayList<>(hoverPath);
                stickyAngStart = chosenStart;
                stickyAngSize = size;
                int row = lb.rowAt(mouseX, mouseY);
                if (row >= 0) hoverPath.add(row);
                break; // los hijos se ofrecen como lista, no como un anillo más: no seguir subdividiendo
            }

            spanStart += idx * size;
            spanSize = size;
            level = kids;
        }
        return true;
    }

    /**
     * Decide si los hijos de una categoría caben como gajos de un anillo más o si hace falta
     * una lista lateral en su lugar (ver el bloque de comentarios sobre {@link #stickyBox}).
     * null = caben de sobra, dibújalos como siempre. pick() es la ÚNICA que llama a esto —
     * render() reutiliza el resultado guardado en stickyBox para no poder discrepar con pick()
     * sobre qué gajo es cuál (ese fue el bug real de hoverPath/clic documentado en la clase).
     */
    private ListBox listBoxFor(List<WheelNode> kids, int ringDepth, float slotDeg, float midRadius,
                               float midAngleDeg) {
        int n = kids.size();
        if (n == 0) return null;

        // MISMO umbral que usará render() para este anillo: si va a salir rotada, lo que hay
        // que comprobar que quepa no es el ANCHO del texto (que ahora corre a lo largo del
        // radio, sin chocar con el vecino) sino su GROSOR de línea (perpendicular al radio,
        // eso sí compite por el mismo hueco angular que el vecino).
        boolean rotated = ringDepth >= ROTATE_FROM_DEPTH && slotDeg < ROTATE_BELOW_DEG;
        float slotArc = (float) Math.toRadians(slotDeg) * midRadius;
        float maxNeed = 0f;
        for (WheelNode k : kids) {
            float need = rotated ? this.font.lineHeight * LABEL_SCALE : this.font.width(k.label()) * LABEL_SCALE;
            maxNeed = Math.max(maxNeed, need);
        }
        if (maxNeed <= slotArc * LIST_FIT_MARGIN) return null; // cabe de sobra como gajos

        float rowH = this.font.lineHeight + 2;
        float boxW = 0f;
        for (WheelNode k : kids) boxW = Math.max(boxW, this.font.width(k.label()));
        boxW += LIST_PAD * 2;
        float boxH = n * rowH + LIST_PAD * 2;

        float rad = (float) Math.toRadians(midAngleDeg);
        boolean rightSide = Mth.cos(rad) >= 0f;
        float anchorX = cx() + Mth.cos(rad) * midRadius;
        float anchorY = cy() + Mth.sin(rad) * midRadius;
        float x = rightSide ? anchorX + 6f : anchorX - 6f - boxW;
        float y = anchorY - boxH / 2f;
        x = Mth.clamp(x, 4f, this.width - boxW - 4f);
        y = Mth.clamp(y, 4f, this.height - boxH - 4f);

        return new ListBox(x, y, boxW, boxH, rowH, kids);
    }

    private record ListBox(float x, float y, float w, float h, float rowH, List<WheelNode> items) {
        /** -1 si el ratón no cae en ninguna fila. */
        int rowAt(double mouseX, double mouseY) {
            if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) return -1;
            int idx = (int) ((mouseY - y - LIST_PAD) / rowH);
            return idx >= 0 && idx < items.size() ? idx : -1;
        }
    }

    private static float norm(float deg) {
        float d = deg % 360f;
        return d < 0 ? d + 360f : d;
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(double mouseX, double mouseY) { pick(mouseX, mouseY); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!pick(mouseX, mouseY)) { onClose(); return true; }

        WheelNode target = null;
        List<WheelNode> level = roots();
        for (int idx : hoverPath) {
            if (idx < 0 || idx >= level.size()) { target = null; break; }
            target = level.get(idx);
            level = target.children();
        }

        if (target != null && target.enabled() && target.isLeaf()) {
            PacketDistributor.sendToServer(
                    new WheelSelectPacket(target.kind().name(), target.value()));
            onClose();
        }
        return true;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        pick(mouseX, mouseY);

        List<WheelNode> level = roots();
        if (level.isEmpty()) {
            PanelText.centeredOnDark(g, this.font, Component.translatable("wheel.zenkai.empty"),
                    (int) cx(), (int) cy() - 4, ZenkaiPalette.TEXT_OFF);
            return;
        }

        float spanStart = -90f, spanSize = 360f;

        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            int n = level.size();
            if (n == 0) break;

            float size = spanSize / n;
            float rIn = ringIn(depth), rOut = ringOut(depth);
            // Mismo par de constantes que antes tenían ring0/ring1 por separado: el anillo 0
            // (círculo completo dividido entre sus hermanos) lleva un hueco más ancho a cada
            // lado que cualquier anillo hijo (subdivisión DENTRO del sector del padre).
            float gapStart = depth == 0 ? GAP_DEG : GAP_DEG * 0.5f;
            float gapTotal = depth == 0 ? GAP_DEG * 2f : GAP_DEG;

            Integer hoveredIdx = hoverPath.size() > depth ? hoverPath.get(depth) : null;
            // "Caliente" (bulto + brillo) SOLO el anillo donde el ratón está de verdad ahora
            // mismo — los niveles más superficiales del camino se dibujan como "seleccionados"
            // (sus hijos se muestran) pero sin el golpe de brillo, igual que ya hacía el
            // ring0 antes de esta generalización.
            boolean deepest = hoverPath.size() == depth + 1;
            boolean rotated = depth >= ROTATE_FROM_DEPTH && size < ROTATE_BELOW_DEG;

            for (int k = 0; k < n; k++) {
                WheelNode node = level.get(k);
                float a0 = spanStart + k * size;
                boolean hot = deepest && hoveredIdx != null && hoveredIdx == k;
                float pop = hot ? HOVER_POP : 0f;
                // Escalonado par/impar SOLO cuando la etiqueta va girada (ver labelAt): todas
                // arrancan del mismo radio interior, así que con el gajo muy estrecho los
                // ARRANQUES de agujas vecinas quedaban pegados entre sí incluso divergiendo
                // hacia fuera (captura del usuario, 2026-09-28: "Golden Form"/"Black Form" aún
                // se rozaban). Alternar el punto de partida separa justo esa zona de arranque.
                float stagger = (rotated && k % 2 == 1) ? ROTATE_STAGGER : 0f;

                sector(g, a0 + gapStart, size - gapTotal, rIn, rOut,
                        node.color(), node.enabled(), hot, node.active());
                labelAt(g, node.label(), a0 + size / 2f, rIn + pop + stagger, rOut + pop, rotated, labelColor(node));
            }

            if (hoveredIdx == null) break;
            WheelNode chosen = level.get(hoveredIdx);
            List<WheelNode> kids = chosen.children();
            if (kids.isEmpty()) break;

            // pick() ya decidió (mismo cálculo, ver stickyBox) si esta rama se ofrece como
            // lista en vez de como anillo. depth == stickyPrefix.size() - 1 identifica que la
            // lista pegajosa activa AHORA MISMO cuelga justo del gajo que se acaba de dibujar.
            if (stickyBox != null && depth == stickyPrefix.size() - 1) {
                Integer hoveredRow = hoverPath.size() > depth + 1 ? hoverPath.get(depth + 1) : null;
                drawListBox(g, stickyBox, hoveredRow);
                break;
            }

            spanStart += hoveredIdx * size;
            spanSize = size;
            level = kids;
        }
    }

    /**
     * Rama demasiado poblada para caber como gajos ni girando el texto (ver {@link #stickyBox}):
     * en vez de un rectángulo propio, el fondo y el borde con degradado violeta son EXACTAMENTE
     * los que Minecraft dibuja para sus propios tooltips (mismo algoritmo que
     * {@code Screen#renderTooltipInternal}, con los mismos {@link #TOOLTIP_BG}/
     * {@code TOOLTIP_BORDER_*}), para que la lista se lea como parte del juego. Sin franja de
     * fondo por fila — un tooltip vainilla nunca resalta una línea suelta — la fila bajo el
     * ratón se distingue con el mismo color de "activo/hover" que ya usan las etiquetas de los
     * gajos (labelColor/TEXT_HOVER), no con un elemento nuevo.
     */
    private void drawListBox(GuiGraphics g, ListBox lb, Integer hoveredRow) {
        int x0 = (int) lb.x(), y0 = (int) lb.y();
        int x1 = (int) (lb.x() + lb.w()), y1 = (int) (lb.y() + lb.h());

        g.fillGradient(x0 - 3, y0 - 4, x1 + 3, y0 - 3, TOOLTIP_BG, TOOLTIP_BG);
        g.fillGradient(x0 - 3, y1 + 3, x1 + 3, y1 + 4, TOOLTIP_BG, TOOLTIP_BG);
        g.fillGradient(x0 - 3, y0 - 3, x1 + 3, y1 + 3, TOOLTIP_BG, TOOLTIP_BG);
        g.fillGradient(x0 - 4, y0 - 3, x0 - 3, y1 + 3, TOOLTIP_BG, TOOLTIP_BG);
        g.fillGradient(x1 + 3, y0 - 3, x1 + 4, y1 + 3, TOOLTIP_BG, TOOLTIP_BG);
        g.fillGradient(x0 - 3, y0 - 3 + 1, x0 - 3 + 1, y1 + 3 - 1, TOOLTIP_BORDER_START, TOOLTIP_BORDER_END);
        g.fillGradient(x1 + 2, y0 - 3 + 1, x1 + 3, y1 + 3 - 1, TOOLTIP_BORDER_START, TOOLTIP_BORDER_END);
        g.fillGradient(x0 - 3, y0 - 3, x1 + 3, y0 - 3 + 1, TOOLTIP_BORDER_START, TOOLTIP_BORDER_START);
        g.fillGradient(x0 - 3, y1 + 2, x1 + 3, y1 + 3, TOOLTIP_BORDER_END, TOOLTIP_BORDER_END);

        for (int i = 0; i < lb.items().size(); i++) {
            WheelNode node = lb.items().get(i);
            int rowY = (int) (lb.y() + LIST_PAD + i * lb.rowH());
            int color = (hoveredRow != null && hoveredRow == i) ? ZenkaiPalette.TEXT_HOVER : labelColor(node);
            PanelText.onDark(g, this.font, node.label(), x0 + LIST_PAD, rowY, color);
        }
    }

    /**
     * Un sector completo: contorno negro por detrás, relleno con degradado radial y un filo
     * en el borde exterior — TENUE siempre que esté habilitado, a tope si está seleccionado.
     * El filo permanente es la diferencia con la versión vieja: un sector sin ÉL en reposo se
     * leía como un gajo de color plano pegado sobre el mundo; con él, hasta el que no tiene el
     * ratón encima insinúa el mismo bisel que ahora comparten los anillos de carga del HUD
     * (ver RadialGauge, de donde sale el primitivo de arco con degradado).
     */
    private void sector(GuiGraphics g, float start, float size, float rIn, float rOut,
                        int color, boolean enabled, boolean hovered, boolean active) {
        float pop = hovered ? HOVER_POP : 0f;
        float in = rIn, out = rOut + pop;

        // 1) Contorno: el mismo arco un poco más grande, en negro, por debajo.
        arc(g, start - 0.6f, size + 1.2f, in - OUTLINE, out + OUTLINE, COL_OUTLINE, COL_OUTLINE);

        // 2) Relleno: el color NO se pinta puro. Se mezcla sobre un fondo oscuro, poco por
        //    dentro y más por fuera. Así se lee como un menú de Minecraft (translúcido y
        //    apagado) en vez de como un tarta de colores planos, y el degradado le da bulto.
        int tone = enabled ? color : COL_DISABLED;
        int alpha = enabled ? (hovered ? 0xF2 : 0xB8) : 0x70;
        float t = hovered ? TINT_HOVER : TINT_IDLE;
        arc(g, start, size, in, out,
                RadialGauge.withAlpha(RadialGauge.mix(COL_BACKDROP, tone, t * 0.55f), alpha),
                RadialGauge.withAlpha(RadialGauge.mix(COL_BACKDROP, tone, t), alpha));

        // 3) Filo exterior: siempre presente si el sector está habilitado (apagado y estrecho
        //    en reposo, más ancho y claro al pasar el ratón, a tope de brillo si es el activo).
        if (!enabled) return;
        int rimAlpha = active ? 0xFF : (hovered ? 0xC0 : 0x70);
        float rimWidth = active ? 2f : 1.2f;
        int rimTone = active ? color : RadialGauge.mix(COL_BACKDROP, color, hovered ? 0.9f : 0.65f);
        arc(g, start, size, out - rimWidth, out,
                RadialGauge.withAlpha(rimTone, rimAlpha),
                RadialGauge.withAlpha(active ? ZenkaiPalette.TEXT : 0xFFFFFF, rimAlpha));
    }

    /** El seleccionado escribe en su propio color; el resto en blanco. Deshabilitado, apagado. */
    private static int labelColor(WheelNode n) {
        if (!n.enabled()) return ZenkaiPalette.TEXT_OFF;
        return n.active() ? RadialGauge.withAlpha(n.color(), 0xFF) : ZenkaiPalette.TEXT;
    }

    /**
     * Gajo ancho ({@code rotated=false}): igual que siempre, horizontal y centrado a mitad de
     * anillo. Gajo estrecho ({@code rotated=true}, ver {@link #ROTATE_BELOW_DEG}): la etiqueta
     * se gira para salir "como una aguja" desde el borde interior del gajo hacia fuera —
     * pasado el ecuador izquierdo del círculo se gira 180° más y se dibuja desde el otro
     * extremo, para que el texto siga leyéndose de izquierda a derecha en vez de cabeza abajo
     * (mismo truco que las etiquetas de cualquier gráfico circular con texto radial).
     */
    private void labelAt(GuiGraphics g, Component text, float angDeg, float rIn, float rOut,
                         boolean rotated, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, LABEL_WRAP);
        int lineH = this.font.lineHeight;
        float totalH = lines.size() * lineH;
        float rad = (float) Math.toRadians(angDeg);

        g.pose().pushPose();
        if (!rotated) {
            float radius = (rIn + rOut) / 2f;
            g.pose().translate(cx() + Mth.cos(rad) * radius, cy() + Mth.sin(rad) * radius, 0f);
            g.pose().scale(LABEL_SCALE, LABEL_SCALE, 1f);
            for (int i = 0; i < lines.size(); i++) {
                FormattedCharSequence line = lines.get(i);
                int w = this.font.width(line);
                g.drawString(this.font, line, -w / 2, (int) (-totalH / 2f + i * lineH), color, true);
            }
        } else {
            boolean leftHalf = Mth.cos(rad) < 0f;
            float anchorR = rIn + 3f;
            g.pose().translate(cx() + Mth.cos(rad) * anchorR, cy() + Mth.sin(rad) * anchorR, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(leftHalf ? angDeg + 180f : angDeg));
            g.pose().scale(LABEL_SCALE, LABEL_SCALE, 1f);
            for (int i = 0; i < lines.size(); i++) {
                FormattedCharSequence line = lines.get(i);
                int w = this.font.width(line);
                g.drawString(this.font, line, leftHalf ? -w : 0, (int) (i * lineH - totalH / 2f), color, true);
            }
        }
        g.pose().popPose();
    }

    /**
     * Sector anular relleno, con color propio para el borde interior y el exterior (de ahí
     * el degradado). Delega en RadialGauge.arc: mismo primitivo de Tesselator que usan los
     * anillos de carga del HUD, para que el Wheel y esos anillos compartan exactamente el
     * mismo trazo en vez de dos copias que puedan divergir.
     */
    private void arc(GuiGraphics g, float startDeg, float sizeDeg,
                     float rIn, float rOut, int argbIn, int argbOut) {
        RadialGauge.arc(g, cx(), cy(), rIn, rOut, startDeg, sizeDeg, argbIn, argbOut);
    }
}
