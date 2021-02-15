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
package com.griefdefender.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBurnEvent;
import com.griefdefender.GriefDefenderPlugin;
import com.griefdefender.api.Tristate;
import com.griefdefender.api.claim.TrustTypes;
import com.griefdefender.api.permission.flag.Flags;
import com.griefdefender.cache.PermissionHolderCache;
import com.griefdefender.claim.GDClaim;
import com.griefdefender.event.GDCauseStackManager;
import com.griefdefender.internal.tracking.PlayerTracker;
import com.griefdefender.internal.util.NMSUtil;
import com.griefdefender.permission.GDPermissionManager;
import com.griefdefender.permission.GDPermissionUser;
import com.griefdefender.permission.flag.GDFlags;
import com.griefdefender.storage.BaseStorage;
import com.griefdefender.util.CauseContextHelper;

public class CommonBlockEventHandler {

    private static CommonBlockEventHandler instance;

    public static CommonBlockEventHandler getInstance() {
        return instance;
    }

    static {
        instance = new CommonBlockEventHandler();
    }

    private final BaseStorage storage;

    public CommonBlockEventHandler() {
        this.storage = GriefDefenderPlugin.getInstance().dataStore;
    }

    public void handleBlockSpread(Event event, Block fromBlock, BlockState newState) {
        if (!GDFlags.BLOCK_SPREAD) {
            return;
        }

        final World world = fromBlock.getWorld();
        if (!GriefDefenderPlugin.getInstance().claimsEnabledForWorld(world.getUID())) {
            return;
        }

        final Location sourceLocation = fromBlock != null ? fromBlock.getLocation() : null;
        final GDPermissionUser user = CauseContextHelper.getEventUser(sourceLocation, PlayerTracker.Type.NOTIFIER);

        Location location = newState.getLocation();
        GDClaim targetClaim = this.storage.getClaimAt(location);

        final Tristate result = GDPermissionManager.getInstance().getFinalPermission(event, location, targetClaim, Flags.BLOCK_SPREAD, fromBlock, newState.getBlock().isEmpty() ? newState.getType() : newState, user, TrustTypes.BUILDER, true);
        if (result == Tristate.FALSE) {
            ((Cancellable) event).setCancelled(true);
        }
    }

    public void handleBlockModify(Event event, Object source, BlockState newState) {
        if (!GDFlags.BLOCK_MODIFY) {
            return;
        }

        Block fromBlock = null;
        if (source instanceof Block) {
            fromBlock = (Block) source;
            // Air -> block should always be recorded as place
            if (fromBlock.isEmpty() && !NMSUtil.getInstance().isMaterialAir(newState.getType())) {
                handleBlockPlace(event, source, newState);
                return;
            }
        } 
        if (!(event instanceof BlockBurnEvent) && fromBlock != null && newState != null && !fromBlock.getLocation().equals(newState.getLocation())) {
            handleBlockSpread(event, fromBlock, newState);
            return;
        }
        if (source instanceof Entity) {
            handleBlockPlace(event, source, newState);
            return;
        }

        if (newState.getType() == Material.AIR) {
            // Block -> Air should always be recorded as break
            // pass original state for target since AIR is the end result
            // In some cases both source and newState will be the same
            // ex. turtle egg hatching
            handleBlockBreak(event, source, newState);
            return;
        }

        final World world = newState.getWorld();
        if (!GriefDefenderPlugin.getInstance().claimsEnabledForWorld(world.getUID())) {
            return;
        }

        final GDPermissionUser user = GDCauseStackManager.getInstance().getCurrentCause().first(GDPermissionUser.class).orElse(null);
        Location location = newState.getLocation();
        GDClaim targetClaim = this.storage.getClaimAt(location);
        // Workaround for BlockBurnEvent being triggered across blocks
        if (event instanceof BlockBurnEvent) {
            if (source instanceof Block) {
                final Block sourceBlock = (Block) source;
                if (sourceBlock.getType() == Material.FIRE) {
                    if (!sourceBlock.getLocation().equals(newState.getLocation())) {
                        // use block-spread
                        final Tristate result = GDPermissionManager.getInstance().getFinalPermission(event, location, targetClaim, Flags.BLOCK_SPREAD, source, newState, user, TrustTypes.BUILDER, true);
                        if (result == Tristate.FALSE) {
                            ((Cancellable) event).setCancelled(true);
                        }
                        return;
                    }
                }
            }
        }
        if (!(source instanceof Player) && user == null) {
            if (source instanceof Block) {
                if (!NMSUtil.getInstance().isBlockIce(((Block) source).getType())) {
                    return;
                }
            } else {
                // always allow
                return;
            }
        }

        final Tristate result = GDPermissionManager.getInstance().getFinalPermission(event, location, targetClaim, Flags.BLOCK_MODIFY, source, newState, user, TrustTypes.BUILDER, true);
        if (result == Tristate.FALSE) {
            ((Cancellable) event).setCancelled(true);
        }
    }

    public void handleBlockPlace(Event event, Object source, BlockState newState) {
        if (!GDFlags.BLOCK_PLACE) {
            return;
        }

        Player player = source instanceof Player ? (Player) source : null;
        final Location location = newState.getLocation();
        if (location == null) {
            return;
        }

        final World world = newState.getWorld();
        if (!GriefDefenderPlugin.getInstance().claimsEnabledForWorld(world.getUID())) {
            return;
        }

        if (source instanceof Block && !NMSUtil.getInstance().isBlockSnow(newState.getType())) {
            final Block sourceBlock = (Block) source;
            if (sourceBlock.isEmpty()) {
                // allow air -> block placement
                return;
            }
        }

        GDPermissionUser user = player != null ? PermissionHolderCache.getInstance().getOrCreateUser(player.getUniqueId()) : GDCauseStackManager.getInstance().getCurrentCause().first(GDPermissionUser.class).orElse(null);
        if (user == null && source != null && source instanceof Block) {
            final Block sourceBlock = (Block) source;
            user = CauseContextHelper.getEventUser(sourceBlock.getLocation(), PlayerTracker.Type.OWNER);
        }

        GDClaim targetClaim = this.storage.getClaimAt(location);
        final Tristate result = GDPermissionManager.getInstance().getFinalPermission(event, location, targetClaim, Flags.BLOCK_PLACE, source, newState, user, TrustTypes.BUILDER, true);
        if (result == Tristate.FALSE) {
            ((Cancellable) event).setCancelled(true);
        }
    }

    public void handleBlockBreak(Event event, Object source, BlockState blockState) {
        if (!GDFlags.BLOCK_BREAK) {
            return;
        }

        // Special case
        if (source instanceof Block) {
            if (NMSUtil.getInstance().isBlockScaffolding(((Block) source))) {
                return;
            }
        }

        Player player = source instanceof Player ? (Player) source : null;
        final Location location = blockState.getLocation();
        if (location == null) {
            return;
        }

        final World world = blockState.getWorld();
        if (!GriefDefenderPlugin.getInstance().claimsEnabledForWorld(world.getUID())) {
            return;
        }

        GDClaim targetClaim = this.storage.getClaimAt(location);
        // Always pass the actual block being broken
        final Tristate result = GDPermissionManager.getInstance().getFinalPermission(event, location, targetClaim, Flags.BLOCK_BREAK, source, blockState.getBlock(), player, TrustTypes.BUILDER, true);
        if (result == Tristate.FALSE) {
            ((Cancellable) event).setCancelled(true);
        }
    }
}
