package dev.foldedhorizons.core.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Core's default {@link TeamIdentityProvider}: the built-in party list.
 *
 * <p>A player not in a party is their own group ({@code id} = player UUID,
 * {@code kind} {@code PLAYER}) — personal pockets keep working with zero
 * setup. Players added via {@link #assign} share a party: every member
 * resolves to the party's stable id, so pockets and claims become shared
 * group assets. The FTB module replaces this provider entirely; this class
 * only serves core-alone packs.</p>
 *
 * <p>Membership persists in a small SavedData on the overworld's
 * {@code DimensionDataStorage} (id {@code "foldedhorizons_parties"}), shaped
 * like {@code PocketRegistry}.</p>
 */
public final class BuiltInPartyRegistry extends SavedData
        implements TeamIdentityProvider {

    /** SavedData id on the overworld's DimensionDataStorage. */
    public static final String DATA_ID = "foldedhorizons_parties";

    /** Shared stateless instance used until a server context exists. */
    public static final BuiltInPartyRegistry INSTANCE = new BuiltInPartyRegistry();

    /** player UUID -> party membership. */
    private final Map<UUID, Membership> members = new HashMap<>();

    /** One party membership: stable group id + display name. */
    record Membership(UUID partyId, String name) {}

    @Override
    public GroupIdentity identityFor(Player player) {
        Membership membership = members.get(player.getUUID());
        if (membership == null) {
            // Default: the player is their own party.
            return new GroupIdentity(player.getUUID(),
                    player.getGameProfile().getName(), GroupIdentity.Kind.PLAYER);
        }
        return new GroupIdentity(membership.partyId(), membership.name(),
                GroupIdentity.Kind.TEAM);
    }

    /**
     * Add {@code player} to (or move them between) a shared party. Server-side
     * only; no-op on client entities.
     */
    public void assign(ServerPlayer player, UUID partyId, String partyName) {
        members.put(player.getUUID(), new Membership(
                Objects.requireNonNull(partyId), Objects.requireNonNull(partyName)));
        setDirty();
    }

    /** Remove {@code player} from their party (falls back to solo identity). */
    public void leave(ServerPlayer player) {
        if (members.remove(player.getUUID()) != null) {
            setDirty();
        }
    }

    // ------------------------------------------------------------------
    // SavedData plumbing (same pattern as PocketRegistry)
    // ------------------------------------------------------------------

    /** Get-or-create on the overworld's data storage. */
    public static BuiltInPartyRegistry getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(BuiltInPartyRegistry::new, BuiltInPartyRegistry::load),
                DATA_ID);
    }

    /** Convenience overload for callers already holding a level. */
    public static BuiltInPartyRegistry getOrCreate(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(BuiltInPartyRegistry::new, BuiltInPartyRegistry::load),
                DATA_ID);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag membersTag = new CompoundTag();
        for (Map.Entry<UUID, Membership> e : members.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("party", e.getValue().partyId());
            entry.putString("name", e.getValue().name());
            membersTag.put(e.getKey().toString(), entry);
        }
        tag.put("members", membersTag);
        return tag;
    }

    /** Deserialize; malformed keys are dropped, never fatal. */
    public static BuiltInPartyRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        BuiltInPartyRegistry registry = new BuiltInPartyRegistry();
        CompoundTag membersTag = tag.getCompound("members");
        for (String playerStr : membersTag.getAllKeys()) {
            try {
                CompoundTag entry = membersTag.getCompound(playerStr);
                registry.members.put(UUID.fromString(playerStr), new Membership(
                        entry.getUUID("party"), entry.getString("name")));
            } catch (IllegalArgumentException e) {
                // skip malformed member key
            }
        }
        return registry;
    }
}
