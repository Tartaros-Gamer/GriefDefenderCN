/*
 * This file is part of GriefDefender, licensed under the MIT License (MIT).
 *
 * Copyright (c) bloodmc
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.griefdefender;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import com.griefdefender.api.Core;
import com.griefdefender.api.Group;
import com.griefdefender.api.Subject;
import com.griefdefender.api.User;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.ClaimBlockSystem;
import com.griefdefender.api.claim.ClaimManager;
import com.griefdefender.api.data.PlayerData;
import com.griefdefender.api.permission.flag.Flag;
import com.griefdefender.api.provider.WorldEditProvider;
import com.griefdefender.cache.PermissionHolderCache;
import com.griefdefender.storage.BaseStorage;

@Singleton
public class GDCore implements Core {

    @Override
    public boolean isEnabled(UUID worldUniqueId) {
        return GriefDefenderPlugin.getInstance().claimsEnabledForWorld(worldUniqueId);
    }

    @Override
    public ClaimBlockSystem getClaimBlockSystem() {
        return GriefDefenderPlugin.getGlobalConfig().getConfig().playerdata.claimBlockSystem;
    }

    @Override
    public boolean isEconomyModeEnabled() {
        return GriefDefenderPlugin.getInstance().isEconomyModeEnabled();
    }

    @Override
    public boolean isProtectionModuleEnabled(Flag flag) {
        return GriefDefenderPlugin.getFlagConfig().getConfig().isFlagEnabled(flag.toString());
    }

    @Override
    public ClaimManager getClaimManager(UUID worldUniqueId) {
        return GriefDefenderPlugin.getInstance().dataStore.getClaimWorldManager(worldUniqueId);
    }

    @Override
    public Optional<PlayerData> getPlayerData(UUID worldUniqueId, UUID playerUniqueId) {
        return Optional.ofNullable(GriefDefenderPlugin.getInstance().dataStore.getOrCreatePlayerData(worldUniqueId, playerUniqueId));
    }

    @Override
    public @Nullable Claim getClaim(UUID uuid) {
        for (World world : Bukkit.getServer().getWorlds()) {
            final ClaimManager claimManager = this.getClaimManager(world.getUID());
            if (claimManager == null) {
                continue;
            }
            if (claimManager.getWildernessClaim().getUniqueId().equals(uuid)) {
                return claimManager.getWildernessClaim();
            }
            for (Claim claim : claimManager.getWorldClaims()) {
                if (claim.getUniqueId().equals(uuid)) {
                    return claim;
                }
            }
        }

        return null;
    }

    @Override
    public List<Claim> getAllClaims() {
        List<Claim> claimList = new ArrayList<>();

        for (World world : Bukkit.getServer().getWorlds()) {
            claimList.addAll(this.getClaimManager(world.getUID()).getWorldClaims());
        }

        return ImmutableList.copyOf(claimList);
    }

    @Override
    public List<Claim> getAllPlayerClaims(UUID playerUniqueId) {
        List<Claim> claimList = new ArrayList<>();
        if (BaseStorage.USE_GLOBAL_PLAYER_STORAGE) {
            final World world = Bukkit.getWorlds().get(0);
            final ClaimManager claimManager = this.getClaimManager(world.getUID());
            claimList.addAll(claimManager.getPlayerClaims(playerUniqueId));
            return ImmutableList.copyOf(claimList);
        }

        for (World world : Bukkit.getServer().getWorlds()) {
            claimList.addAll(this.getClaimManager(world.getUID()).getPlayerClaims(playerUniqueId));
        }

        return ImmutableList.copyOf(claimList);
    }

    @Override
    public Subject getDefaultSubject() {
        return GriefDefenderPlugin.DEFAULT_HOLDER;
    }

    @Override
    public Subject getSubject(String identifier) {
        return PermissionHolderCache.getInstance().getOrCreateHolder(identifier);
    }

    @Override
    public User getUser(UUID uuid) {
        return PermissionHolderCache.getInstance().getOrCreateUser(uuid);
    }

    @Override
    public Group getGroup(String name) {
        return PermissionHolderCache.getInstance().getOrCreateGroup(name);
    }

    @Override
    public @Nullable WorldEditProvider getWorldEditProvider() {
        return GriefDefenderPlugin.getInstance().getWorldEditProvider();
    }
}
