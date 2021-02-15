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

import com.griefdefender.GDPlayerData;
import com.griefdefender.GriefDefenderPlugin;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.claim.GDClaim;

import java.util.UUID;

import org.bukkit.entity.Player;

public class ClaimVisualRevertTask implements Runnable {

    private Player player;
    private GDPlayerData playerData;
    private UUID visualUniqueId;
    private boolean shovelStartVisual = false;

    public ClaimVisualRevertTask(UUID visualUniqueId, Player player, GDPlayerData playerData) {
        this.visualUniqueId = visualUniqueId;
        this.playerData = playerData;
        this.player = player;
        this.shovelStartVisual = playerData.lastShovelLocation != null;
    }

    public boolean isShovelStartVisual() {
        return this.shovelStartVisual;
    }

    public UUID getVisualUniqueId() {
        return this.visualUniqueId;
    }

    @Override
    public void run() {
        final Claim claim = GriefDefenderPlugin.getInstance().dataStore.getClaim(player.getWorld().getUID(), this.visualUniqueId);
        this.playerData.revertClaimVisual((GDClaim) claim, this.visualUniqueId);
    }
}
