package org.mirage.gfbs_auralis.server;

/**
 * G.F.B.S. Mirage (mirage_gfbs) - A Minecraft Mod
 * Copyright (C) 2025-2029 Mirage-MC
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mirage.gfbs_auralis.network.LoopSoundPacket;
import org.mirage.gfbs_auralis.network.NetworkHandler;
import org.mirage.gfbs_auralis.network.PlaySoundPacket;
import org.mirage.gfbs_auralis.network.StopSoundPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuralisServerManager {
    private static final Map<UUID, Map<ResourceLocation, SoundState>> playerSounds = new HashMap<>();

    public static void playSound(ServerPlayer player, ResourceLocation soundId, float volume, float pitch, boolean looping) {
        UUID playerId = player.getUUID();
        playerSounds.computeIfAbsent(playerId, k -> new HashMap<>());
        playerSounds.get(playerId).put(soundId, new SoundState(volume, pitch, looping));

        NetworkHandler.CHANNEL.sendTo(new PlaySoundPacket(soundId, volume, pitch, looping),
                player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void stopSound(ServerPlayer player, ResourceLocation soundId) {
        UUID playerId = player.getUUID();
        if (playerSounds.containsKey(playerId)) {
            playerSounds.get(playerId).remove(soundId);

            NetworkHandler.CHANNEL.sendTo(new StopSoundPacket(soundId),
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    public static void setLooping(ServerPlayer player, ResourceLocation soundId, boolean looping) {
        UUID playerId = player.getUUID();
        if (playerSounds.containsKey(playerId) && playerSounds.get(playerId).containsKey(soundId)) {
            SoundState state = playerSounds.get(playerId).get(soundId);
            state.setLooping(looping);

            NetworkHandler.CHANNEL.sendTo(new LoopSoundPacket(soundId, looping),
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    public static void syncSound(ServerPlayer player, ResourceLocation soundId, float volume, float pitch, boolean looping) {
        UUID playerId = player.getUUID();
        playerSounds.computeIfAbsent(playerId, k -> new HashMap<>());
        playerSounds.get(playerId).put(soundId, new SoundState(volume, pitch, looping));
    }

    public static void syncAllSoundsToPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (playerSounds.containsKey(playerId)) {
            Map<ResourceLocation, SoundState> sounds = playerSounds.get(playerId);
            for (Map.Entry<ResourceLocation, SoundState> entry : sounds.entrySet()) {
                ResourceLocation soundId = entry.getKey();
                SoundState state = entry.getValue();

                NetworkHandler.CHANNEL.sendTo(new PlaySoundPacket(soundId, state.getVolume(), state.getPitch(), state.isLooping()),
                        player.connection.connection,
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
            }
        }
    }

    public static void onPlayerLogin(ServerPlayer player) {
        syncAllSoundsToPlayer(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        playerSounds.remove(player.getUUID());
    }

    public static void onServerStop() {
        playerSounds.clear();
    }

    private static class SoundState {
        private float volume;
        private float pitch;
        private boolean looping;

        public SoundState(float volume, float pitch, boolean looping) {
            this.volume = volume;
            this.pitch = pitch;
            this.looping = looping;
        }

        public float getVolume() {
            return volume;
        }

        public float getPitch() {
            return pitch;
        }

        public boolean isLooping() {
            return looping;
        }

        public void setLooping(boolean looping) {
            this.looping = looping;
        }
    }
}