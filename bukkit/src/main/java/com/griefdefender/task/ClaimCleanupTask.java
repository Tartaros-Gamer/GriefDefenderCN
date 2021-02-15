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
package com.griefdefender.task;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.griefdefender.GDBootstrap;
import com.griefdefender.GDPlayerData;
import com.griefdefender.GriefDefenderPlugin;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.permission.option.Options;
import com.griefdefender.claim.GDClaim;
import com.griefdefender.claim.GDClaimManager;
import com.griefdefender.configuration.GriefDefenderConfig;
import com.griefdefender.configuration.MessageStorage;
import com.griefdefender.internal.util.RestoreUtil;
import com.griefdefender.permission.GDPermissionManager;
import com.griefdefender.permission.GDPermissionUser;
import com.griefdefender.util.PermissionUtil;

import net.kyori.text.Component;
import net.kyori.text.serializer.plain.PlainComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ClaimCleanupTask extends BukkitRunnable {

    public ClaimCleanupTask(int interval) {
        this.runTaskTimer(GDBootstrap.getInstance(), 1L, interval * 20 * 60);
    }

    @Override
    public void run() {
        for (World world : Bukkit.getServer().getWorlds()) {
            GDClaimManager claimManager = GriefDefenderPlugin.getInstance().dataStore.getClaimWorldManager(world.getUID());
            Set<Claim> claimList = claimManager.getWorldClaims();
            if (claimList.size() == 0) {
                continue;
            }

            final GriefDefenderConfig<?> activeConfig = GriefDefenderPlugin.getActiveConfig(world);
            final boolean schematicRestore = activeConfig.getConfig().claim.claimAutoSchematicRestore;
            Iterator<Claim> iterator = new HashSet<>(claimList).iterator();
            while (iterator.hasNext()) {
                GDClaim claim = (GDClaim) iterator.next();
                final GDPlayerData playerData = claim.getOwnerPlayerData();
                if (claim.isAdminClaim() || !claim.getInternalClaimData().allowExpiration() || playerData == null) {
                    continue;
                }

                if (!playerData.dataInitialized) {
                    continue;
                }

                int areaOfDefaultClaim = 0;
                if (activeConfig.getConfig().claim.autoChestClaimBlockRadius >= 0) {
                    areaOfDefaultClaim = (int) Math.pow(activeConfig.getConfig().claim.autoChestClaimBlockRadius * 2 + 1, 2);
                }

                final GDPermissionUser subject = playerData.getSubject();
                Instant claimLastActive = claim.getInternalClaimData().getDateLastActive();

                final int claimExpirationChest = playerData.getChestClaimExpiration();
                if (claim.getClaimBlocks() <= areaOfDefaultClaim && claimExpirationChest > 0) {
                    if (claimLastActive.plus(Duration.ofDays(claimExpirationChest))
                        .isBefore(Instant.now())) {
                        playerData.useRestoreSchematic = schematicRestore;
                        claimManager.deleteClaim(claim);
                        playerData.useRestoreSchematic = false;
                        final Component message = GriefDefenderPlugin.getInstance().messageData.getMessage(MessageStorage.CLAIM_EXPIRED_INACTIVITY,
                                ImmutableMap.of(
                                "player", subject.getFriendlyName(),
                                "uuid", claim.getUniqueId().toString()));
                        GriefDefenderPlugin.getInstance().getLogger().info(PlainComponentSerializer.INSTANCE.serialize(message));
                        if (!schematicRestore && activeConfig.getConfig().claim.claimAutoNatureRestore) {
                            if (GriefDefenderPlugin.getMajorMinecraftVersion() <= 12 || GriefDefenderPlugin.getInstance().getWorldEditProvider() == null) {
                                RestoreUtil.getInstance().restoreClaim(claim);
                            } else {
                                GriefDefenderPlugin.getInstance().getWorldEditProvider().regenerateClaim(claim);
                            }
                        }
                        // remove all context permissions
                        PermissionUtil.getInstance().clearPermissions(claim);
                    }
                    continue;
                }

                if (!claim.isBasicClaim()) {
                    continue;
                }
                final int optionValue = GDPermissionManager.getInstance().getInternalOptionValue(TypeToken.of(Integer.class), subject, Options.EXPIRATION, claim);
                final int optionClaimExpirationBasic = optionValue;
                if (optionClaimExpirationBasic > 0) {
                    final Instant localNow = Instant.now();
                    final boolean claimNotActive = claimLastActive.plus(Duration.ofDays(optionClaimExpirationBasic)).isBefore(localNow);
                    if (!claimNotActive) {
                        final boolean taxEnabled = activeConfig.getConfig().economy.taxSystem;
                        if (!taxEnabled || !claim.getData().isExpired()) {
                            continue;
                        }
                        final Instant taxPastDueDate = claim.getEconomyData().getTaxPastDueDate();
                        if (taxPastDueDate == null) {
                            continue;
                        }

                        final int taxExpirationDays = GDPermissionManager.getInstance().getInternalOptionValue(TypeToken.of(Integer.class), subject, Options.TAX_EXPIRATION, claim).intValue();
                        final int expireDaysToKeep = GDPermissionManager.getInstance().getInternalOptionValue(TypeToken.of(Integer.class), subject, Options.TAX_EXPIRATION_DAYS_KEEP, claim).intValue();
                        if (!taxPastDueDate.plus(Duration.ofDays(taxExpirationDays + expireDaysToKeep)).isBefore(localNow)) {
                            continue;
                        }
                    }

                    playerData.useRestoreSchematic = schematicRestore;
                    claimManager.deleteClaim(claim);
                    playerData.useRestoreSchematic = false;
                    final Component message = GriefDefenderPlugin.getInstance().messageData.getMessage(MessageStorage.CLAIM_EXPIRED_INACTIVITY,
                            ImmutableMap.of(
                            "player", subject.getFriendlyName(),
                            "uuid", claim.getUniqueId().toString()));
                    GriefDefenderPlugin.getInstance().getLogger().info(PlainComponentSerializer.INSTANCE.serialize(message));
                    if (!schematicRestore && activeConfig.getConfig().claim.claimAutoNatureRestore) {
                        RestoreUtil.getInstance().restoreClaim(claim);
                    }
                }
            }
        }
    }
}
