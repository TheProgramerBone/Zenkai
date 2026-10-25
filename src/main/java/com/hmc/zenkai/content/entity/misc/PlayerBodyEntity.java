package com.hmc.zenkai.content.entity.misc;

import com.hmc.zenkai.feature.player.death.PlayerBodyAccess;
import com.hmc.zenkai.feature.player.death.PlayerBodyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.FluidTags;

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

    /** Filas del cofre que se abre: 6x9 = 54 huecos (inventario 36 + armadura 4 + mano 1 caben de sobra). */
    public static final int SLOTS = 54;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_RACE =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_FORM =
            SynchedEntityData.defineId(PlayerBodyEntity.class, EntityDataSerializers.STRING);

    private final SimpleContainer inventory = new SimpleContainer(SLOTS);
    /** Reloj del mundo en que nació (para ordenar por antigüedad y aplicar el tope). */
    private long createdAt;

    public PlayerBodyEntity(EntityType<? extends PlayerBodyEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_OWNER_NAME, "");
        builder.define(DATA_RACE, "");
        builder.define(DATA_FORM, "");
    }

    /** Configuración al nacer (solo servidor). */
    public void configure(UUID owner, String ownerName, String race, String form, long createdAt) {
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
    public long createdAt() { return createdAt; }
    public Container inventory() { return inventory; }

    /** Mete un objeto; devuelve lo que no cupo (para que el llamador lo suelte al suelo). */
    public ItemStack insert(ItemStack stack) {
        return inventory.addItem(stack);
    }

    public boolean hasItems() { return !inventory.isEmpty(); }

    /** Suelta todo el contenido al suelo (desintegrar, enterrar, tope de cuerpos...). Solo servidor. */
    public void spillContents() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
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
        if (tickCount % 20 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            PlayerBodyRegistry.get(sl.getServer()).move(getUUID(), sl.dimension(), blockPosition());
        }
    }

    private void rescueFromVoid() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        BlockPos safe = owner().map(o -> com.hmc.zenkai.feature.player.death.PlayerBodyManager
                .lastSafe(o, sl.dimension())).orElse(null);
        if (safe == null) {
            int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    getBlockX(), getBlockZ());
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

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    /** Clicable (para abrirla) pero no empujable ni atacable. */
    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean isAttackable() { return false; }

    // ── Interacción: servidor autoritativo ───────────────────────────────────

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!PlayerBodyAccess.canOpen(sp, this)) {
            sp.displayClientMessage(Component.translatable("zenkai.body.not_yours"), true);
            return InteractionResult.CONSUME;
        }
        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> ChestMenu.sixRows(id, inv, new BodyView(this)),
                Component.translatable("zenkai.body.title", ownerName())));
        return InteractionResult.CONSUME;
    }

    /** El inventario del cuerpo visto desde el menú: cierra el menú si se aleja o si alguien deja de poder abrirlo. */
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

    @Override
    public boolean shouldBeSaved() { return true; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        owner().ifPresent(o -> tag.putUUID("Owner", o));
        tag.putString("OwnerName", ownerName());
        tag.putString("Race", raceId());
        tag.putString("Form", formId());
        tag.putLong("CreatedAt", createdAt);
        tag.put("Items", inventory.createTag(registryAccess()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(DATA_OWNER_NAME, tag.getString("OwnerName"));
        entityData.set(DATA_RACE, tag.getString("Race"));
        entityData.set(DATA_FORM, tag.getString("Form"));
        createdAt = tag.getLong("CreatedAt");
        inventory.fromTag(tag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND), registryAccess());
    }

    /** Al retirarse de verdad (no al descargar el chunk) sale del índice. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()
                && level() instanceof net.minecraft.server.level.ServerLevel sl) {
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
