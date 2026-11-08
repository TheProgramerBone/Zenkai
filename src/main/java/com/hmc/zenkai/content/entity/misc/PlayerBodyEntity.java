package com.hmc.zenkai.content.entity.misc;

import com.hmc.zenkai.client.gui.menu.PlayerBodyMenu;
import com.hmc.zenkai.feature.player.death.PlayerBodyAccess;
import com.hmc.zenkai.feature.player.death.PlayerBodyCarry;
import com.hmc.zenkai.feature.player.death.PlayerBodyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * El CUERPO que deja un jugador al morir: una entidad (no un bloque, no un mob) con su inventario.
 * Existe SIEMPRE, también con la muerte permanente apagada: las rage forms (#6) y la visita de Uranai
 * Baba se apoyan en ella. Plan en {@code .claude/pendiente/plan-muerte-permanente.md}.
 *
 * Extiende {@link Entity} y no LivingEntity/Mob a propósito: ningún mob la apunta ni la ataca y no hay
 * IA que mantener. Es inmune a daño (salvo lo que salta la invulnerabilidad: vacío y /kill, y el vacío
 * se corrige en {@link #tick()}), a explosiones y al fuego.
 *
 * El inventario vive SOLO en el servidor y se guarda en el NBT de la propia entidad: así sobrevive a
 * descargar el chunk y a reiniciar. El cliente solo recibe lo que necesita para pintarla (dueño, nombre,
 * raza, forma). Quién puede abrirla lo decide {@link PlayerBodyAccess}, siempre en el servidor.
 */
public class PlayerBodyEntity extends Entity {

    /**
     * DISTRIBUCIÓN del contenedor (la del inventario del jugador, para que el menú sea el de vanilla):
     * 0-8 barra, 9-35 mochila, 36-39 armadura (36 botas ... 39 casco, igual que {@code Inventory}), 40 mano
     * secundaria y 41.. ranuras de Curios (hasta {@link #CURIO_MAX}), cada una con su tipo en {@link #curioId}.
     */
    public static final int MAIN = 36, ARMOR = 36, OFFHAND = 40, CURIO_BASE = 41, CURIO_MAX = 48;
    public static final int SLOTS = CURIO_BASE + CURIO_MAX;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_RACE =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_FORM =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.STRING);

    /** Propiedad "textures" del perfil del dueño al morir (base64, sin firma): sin ella un dueño desconectado se
     *  pintaría siempre con la skin por defecto, porque un GameProfile sin propiedades no tiene de dónde sacar la skin. */
    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.STRING);

    /** Escala del dueño al morir (atributo SCALE: las razas miden distinto). Sin ella el cuerpo salía del tamaño de un Steve
     *  junto a un jugador mucho mayor. Escala el modelo Y la caja de colisión. */
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.FLOAT);

    /** Equipo VISIBLE (armadura y manos), sincronizado a los clientes para dibujarlo. Lo refresca {@link #refreshGear()} desde el contenedor. */
    private static final EntityDataAccessor<ItemStack> DATA_HEAD = SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_CHEST = SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_LEGS = SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_FEET = SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_HAND = SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_OFF = SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.ITEM_STACK);

    private final SimpleContainer inventory = new SimpleContainer(SLOTS);
    /** Casilla de la barra (0-8) que tenía seleccionada el jugador al morir: lo que sostenía en la mano principal. */
    private int handSlot;
    /** Tipo de Curios de cada ranura 41.. ("" = sin usar). Ocupan siempre un prefijo. */
    private final String[] curioIds = new String[CURIO_MAX];
    /** Puntos de experiencia que guarda el cuerpo (los reclama el dueño con «Llevarse todo»). */
    private int xp;
    /** Reloj del mundo en que nació (para ordenar por antigüedad y aplicar el tope). */
    private long createdAt;

    public PlayerBodyEntity(EntityType<? extends PlayerBodyEntity> type, Level level) {
        super(type, level);
        Arrays.fill(curioIds, "");
        if (!level.isClientSide) inventory.addListener(c -> refreshGear());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_OWNER_NAME, "");
        builder.define(DATA_RACE, "");
        builder.define(DATA_FORM, "");
        builder.define(DATA_SKIN, "");
        builder.define(DATA_SCALE, 1.0f);
        builder.define(DATA_HEAD, ItemStack.EMPTY);
        builder.define(DATA_CHEST, ItemStack.EMPTY);
        builder.define(DATA_LEGS, ItemStack.EMPTY);
        builder.define(DATA_FEET, ItemStack.EMPTY);
        builder.define(DATA_HAND, ItemStack.EMPTY);
        builder.define(DATA_OFF, ItemStack.EMPTY);
    }

    /** Configuración al nacer (solo servidor). */
    public void configure(UUID owner, String ownerName, String race, String form, String skinTextures, float scale, long createdAt) {
        entityData.set(DATA_SCALE, Math.max(0.1f, scale));
        entityData.set(DATA_SKIN, skinTextures);
        entityData.set(DATA_OWNER, Optional.of(owner));
        entityData.set(DATA_OWNER_NAME, ownerName);
        entityData.set(DATA_RACE, race);
        entityData.set(DATA_FORM, form);
        this.createdAt = createdAt;
    }

    public Optional<UUID> owner() { return entityData.get(DATA_OWNER); }
    public String ownerName() { return entityData.get(DATA_OWNER_NAME); }
    public String raceId() { return entityData.get(DATA_RACE); }
    public String formId() { return entityData.get(DATA_FORM); }
    public String skinTextures() { return entityData.get(DATA_SKIN); }
    public float scale() { return entityData.get(DATA_SCALE); }

    /** La caja sigue a la escala del dueño: se recalcula al llegar el dato (en el servidor al fijarlo y en el cliente al sincronizar). */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_SCALE.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return getType().getDimensions().scale(scale());
    }
    public long createdAt() { return createdAt; }
    public int xp() { return xp; }
    public int handSlot() { return handSlot; }
    public void setHandSlot(int slot) { this.handSlot = Math.max(0, Math.min(8, slot)); refreshGear(); }

    /** El equipo que se ve (cliente y servidor leen los datos sincronizados). */
    public com.hmc.zenkai.feature.player.death.BodyGear gear() {
        return new com.hmc.zenkai.feature.player.death.BodyGear(entityData.get(DATA_HEAD), entityData.get(DATA_CHEST),
                entityData.get(DATA_LEGS), entityData.get(DATA_FEET), entityData.get(DATA_HAND), entityData.get(DATA_OFF));
    }

    /**
     * Copia al dato sincronizado lo que hay AHORA en las ranuras de armadura, la mano seleccionada y la mano secundaria. Solo
     * escribe si cambió: un ItemStack no tiene equals por valor y cada set mandaría un paquete.
     */
    private void refreshGear() {
        if (level().isClientSide) return;
        setGear(DATA_HEAD, inventory.getItem(ARMOR + 3));    // 39 casco
        setGear(DATA_CHEST, inventory.getItem(ARMOR + 2));   // 38 peto
        setGear(DATA_LEGS, inventory.getItem(ARMOR + 1));    // 37 grebas
        setGear(DATA_FEET, inventory.getItem(ARMOR));        // 36 botas
        setGear(DATA_HAND, inventory.getItem(handSlot));
        setGear(DATA_OFF, inventory.getItem(OFFHAND));
    }

    private void setGear(EntityDataAccessor<ItemStack> key, ItemStack now) {
        if (!ItemStack.matches(entityData.get(key), now)) entityData.set(key, now.copy());
    }
    public void setXp(int xp) { this.xp = Math.max(0, xp); }
    public Container inventory() { return inventory; }

    // ── Ranuras de Curios ────────────────────────────────────────────────────

    public String curioId(int curioIndex) { return curioIds[curioIndex]; }

    /** Cuántas ranuras de Curios están en uso (siempre un prefijo: 0..n-1). */
    public int curioCount() {
        int n = 0;
        while (n < CURIO_MAX && !curioIds[n].isEmpty()) n++;
        return n;
    }

    /** Reserva una ranura de Curios del tipo dado y devuelve su índice de contenedor, o -1 si no caben más. */
    public int claimCurioSlot(String id) {
        int n = curioCount();
        if (n >= CURIO_MAX) return -1;
        curioIds[n] = id;
        return CURIO_BASE + n;
    }

    // ── Contenido ────────────────────────────────────────────────────────────

    /** Mete un objeto en la MOCHILA (0-35); devuelve lo que no cupo (el llamador lo suelta al suelo). */
    public ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < MAIN && !rest.isEmpty(); i++) {          // primero completar montones
            ItemStack cur = inventory.getItem(i);
            if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, rest) && cur.getCount() < cur.getMaxStackSize()) {
                int move = Math.min(rest.getCount(), cur.getMaxStackSize() - cur.getCount());
                cur.grow(move);
                rest.shrink(move);
            }
        }
        for (int i = 0; i < MAIN && !rest.isEmpty(); i++) {          // después huecos libres
            if (inventory.getItem(i).isEmpty()) {
                int move = Math.min(rest.getCount(), rest.getMaxStackSize());
                inventory.setItem(i, rest.copyWithCount(move));
                rest.shrink(move);
            }
        }
        return rest;
    }

    public boolean hasItems() { return !inventory.isEmpty(); }

    /** Suelta todo el contenido al suelo (desintegrar, enterrar, tope de cuerpos...). Solo servidor. */
    public void spillContents() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
    }

    /** Quita el cuerpo del mundo. Con {@code spill} suelta antes los objetos y la XP al suelo; sin él, desaparecen con el cuerpo. */
    public void retire(boolean spill) {
        if (spill) {
            spillContents();
            if (xp > 0 && level() instanceof ServerLevel sl) net.minecraft.world.entity.ExperienceOrb.award(sl, position(), xp);
        }
        xp = 0;
        discard();
    }

    // ── Vida ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // Caída y rozamiento simples: no es un ser vivo, pero tampoco debe flotar en el aire.
        Vec3 v = getDeltaMovement();
        if (isInLava() || isEyeInFluid(FluidTags.WATER) || isInWater()) {
            // Los cuerpos flotan como los objetos: nada de hundirse en lava y perderse.
            setDeltaMovement(v.x * 0.9, Math.min(v.y + 0.04, 0.12), v.z * 0.9);
        } else if (!onGround()) {
            setDeltaMovement(v.x * 0.98, v.y - 0.04, v.z * 0.98);
        } else {
            setDeltaMovement(v.x * 0.5, 0, v.z * 0.5);
        }
        move(MoverType.SELF, getDeltaMovement());

        // Vacío: lo traemos al último punto seguro de su dueño, o a la superficie si no hay.
        if (getY() < level().getMinBuildHeight() - 4) {
            rescueFromVoid();
        }

        // El índice sigue la posición real (cae, lo empujan, lo recolocan...), una vez por segundo.
        if (tickCount % 20 == 0 && level() instanceof ServerLevel sl) {
            PlayerBodyRegistry reg = PlayerBodyRegistry.get(sl.getServer());
            // El tope pudo condenar este cuerpo con su chunk descargado (las entidades cargan DESPUÉS que el
            // chunk, así que buscarlo entonces falla): se ejecuta aquí, al volver a existir.
            Boolean spill = reg.takeCondemned(getUUID());
            if (spill != null) {
                retire(spill);
                return;
            }
            int days = com.hmc.zenkai.config.ServerConfig.PERMADEATH_BODY_LIFETIME_DAYS.get();
            if (days > 0 && sl.getGameTime() - createdAt > days * 24000L) {   // caducado: se vacía en el suelo y desaparece
                retire(true);
                return;
            }
            reg.move(getUUID(), sl.dimension(), blockPosition());
        }
    }

    private void rescueFromVoid() {
        if (!(level() instanceof ServerLevel sl)) return;
        BlockPos safe = owner().map(o -> com.hmc.zenkai.feature.player.death.PlayerBodyManager
                .lastSafe(o, sl.dimension())).orElse(null);
        if (safe == null) {
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
            safe = new BlockPos(getBlockX(), Math.max(y, sl.getMinBuildHeight() + 1), getBlockZ());
        }
        setDeltaMovement(Vec3.ZERO);
        teleportTo(safe.getX() + 0.5, safe.getY() + 0.1, safe.getZ() + 0.5);
    }

    // ── Inmunidades ──────────────────────────────────────────────────────────

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // Solo lo que salta la invulnerabilidad (vacío, /kill) la alcanza; el vacío lo corrige tick().
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) { return true; }

    @Override
    public boolean fireImmune() { return true; }

    /**
     * Solo una técnica de ki puede destruir un cuerpo, y solo con {@code permadeath.body_griefing}: es la contrapartida de
     * que cualquiera pueda saquearlo. Se desintegra y suelta su contenido al suelo. La ventana de protección del dueño
     * ({@code owner_grace_seconds}) también lo cubre, para que el ki que mató al jugador no borre su cuerpo en el acto.
     * Cualquier otro daño sigue sin hacerle nada.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !source.is(com.hmc.zenkai.registry.ModDamageTypes.KI_DAMAGE_TAG)) return false;
        if (!com.hmc.zenkai.config.ServerConfig.PERMADEATH_BODY_GRIEFING.get()) return false;
        int grace = com.hmc.zenkai.config.ServerConfig.PERMADEATH_OWNER_GRACE_SECONDS.get();
        if (grace > 0 && level().getGameTime() - createdAt < grace * 20L) return false;
        disintegrate(source);
        return true;
    }

    /** Efecto y retirada del cuerpo por un impacto de ki. Los objetos y la XP caen donde estaba. */
    private void disintegrate(DamageSource source) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, getX(), getY() + 0.5, getZ(), 40, 0.4, 0.5, 0.4, 0.05);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            sl.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_BURN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.7f);
            com.hmc.zenkai.feature.player.death.PlayerBodyInteractEvent.fire(
                    source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
                    this, com.hmc.zenkai.feature.player.death.PlayerBodyInteractEvent.Kind.DISINTEGRATE);
        }
        retire(true);
    }

    /**
     * {@code /kill} llama a {@code kill()}, que retira la entidad SIN pasar por {@code hurt}: se llevaría el
     * cuerpo y todos sus objetos. Se ignora; para borrar cuerpos a propósito está {@code /zenkai body killall}.
     */
    @Override
    public void kill() { }

    /** Clicable (para abrirla) pero no empujable ni atacable. */
    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean isAttackable() { return false; }

    // ── Interacción: servidor autoritativo ───────────────────────────────────

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!PlayerBodyAccess.checkOrWarn(sp, this)) return InteractionResult.CONSUME;
        if (sp.isShiftKeyDown()) {
            // Shift + clic derecho: cargarlo en brazos (como CarryOn).
            PlayerBodyCarry.pickUp(sp, this);
            if (isRemoved())   // si no lo cargó (desactivado, ya lleva uno) no hay interacción que avisar
                com.hmc.zenkai.feature.player.death.PlayerBodyInteractEvent.fire(sp, this,
                        com.hmc.zenkai.feature.player.death.PlayerBodyInteractEvent.Kind.CARRY);
            return InteractionResult.CONSUME;
        }
        com.hmc.zenkai.feature.player.death.PlayerBodyInteractEvent.fire(sp, this,
                com.hmc.zenkai.feature.player.death.PlayerBodyInteractEvent.Kind.OPEN);
        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new PlayerBodyMenu(id, inv, this),
                Component.translatable("zenkai.body.title", ownerName())),
                buf -> {
                    buf.writeVarInt(getId());
                    int n = curioCount();
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) buf.writeUtf(curioIds[i]);
                });
        return InteractionResult.CONSUME;
    }

    /** El inventario del cuerpo visto desde el menú: cierra el menú si se aleja o si alguien deja de poder abrirlo. */
    public Container viewFor() { return new BodyView(this); }

    private static final class BodyView implements Container {
        private final PlayerBodyEntity body;
        BodyView(PlayerBodyEntity body) { this.body = body; }

        @Override public int getContainerSize() { return body.inventory.getContainerSize(); }
        @Override public boolean isEmpty() { return body.inventory.isEmpty(); }
        @Override public ItemStack getItem(int s) { return body.inventory.getItem(s); }
        @Override public ItemStack removeItem(int s, int n) { return body.inventory.removeItem(s, n); }
        @Override public ItemStack removeItemNoUpdate(int s) { return body.inventory.removeItemNoUpdate(s); }
        @Override public void setItem(int s, ItemStack st) { body.inventory.setItem(s, st); }
        @Override public void setChanged() { body.inventory.setChanged(); }
        @Override public void clearContent() { body.inventory.clearContent(); }
        @Override public boolean stillValid(Player p) {
            return !body.isRemoved() && p instanceof ServerPlayer sp
                    && body.distanceToSqr(p) <= 64.0 && PlayerBodyAccess.canOpen(sp, body);
        }
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    /** Estado completo para llevarlo en brazos y volver a colocarlo (mismo formato que el guardado del chunk). */
    public CompoundTag saveForCarry() {
        CompoundTag t = new CompoundTag();
        addAdditionalSaveData(t);
        return t;
    }

    public void loadFromCarry(CompoundTag t) {
        readAdditionalSaveData(t);
    }

    @Override
    public boolean shouldBeSaved() { return true; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        owner().ifPresent(o -> tag.putUUID("Owner", o));
        tag.putString("OwnerName", ownerName());
        tag.putString("Race", raceId());
        tag.putString("Form", formId());
        tag.putString("Skin", skinTextures());
        tag.putFloat("Scale", scale());
        tag.putLong("CreatedAt", createdAt);
        tag.putInt("Xp", xp);
        // CON el número de ranura: SimpleContainer.createTag/fromTag no lo guardan y fromTag recoloca con addItem, así que tras
        // recargar el chunk o soltar el cuerpo la armadura, la mano y los Curios acababan revueltos en la mochila.
        ListTag slots = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putInt("Slot", i);
            t.put("Item", s.save(registryAccess()));
            slots.add(t);
        }
        tag.put("Slots", slots);
        tag.putInt("HandSlot", handSlot);
        ListTag ids = new ListTag();
        for (int i = 0; i < curioCount(); i++) ids.add(StringTag.valueOf(curioIds[i]));
        tag.put("CurioIds", ids);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(DATA_OWNER_NAME, tag.getString("OwnerName"));
        entityData.set(DATA_RACE, tag.getString("Race"));
        entityData.set(DATA_FORM, tag.getString("Form"));
        entityData.set(DATA_SKIN, tag.getString("Skin"));
        entityData.set(DATA_SCALE, tag.contains("Scale") ? Math.max(0.1f, tag.getFloat("Scale")) : 1.0f);
        createdAt = tag.getLong("CreatedAt");
        xp = tag.getInt("Xp");
        inventory.clearContent();
        if (tag.contains("Slots", Tag.TAG_LIST)) {
            for (Tag raw : tag.getList("Slots", Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) raw;
                int slot = t.getInt("Slot");
                if (slot < 0 || slot >= SLOTS) continue;
                ItemStack.parse(registryAccess(), t.get("Item")).ifPresent(s -> inventory.setItem(slot, s));
            }
        } else {
            inventory.fromTag(tag.getList("Items", Tag.TAG_COMPOUND), registryAccess());   // cuerpos guardados ANTES del arreglo (sin ranura)
        }
        handSlot = Math.max(0, Math.min(8, tag.getInt("HandSlot")));
        Arrays.fill(curioIds, "");
        ListTag ids = tag.getList("CurioIds", Tag.TAG_STRING);
        for (int i = 0; i < ids.size() && i < CURIO_MAX; i++) curioIds[i] = ids.getString(i);
        refreshGear();   // con la mano ya leída (los setItem de arriba la refrescaron con la casilla por defecto)
    }

    /** Al retirarse de verdad (no al descargar el chunk) sale del índice. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && level() instanceof ServerLevel sl) {
            PlayerBodyRegistry.get(sl.getServer()).remove(getUUID());
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        double d = 64.0;
        return distSq < d * d;
    }
}
