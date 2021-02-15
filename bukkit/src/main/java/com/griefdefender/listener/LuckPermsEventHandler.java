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

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.griefdefender.cache.PermissionHolderCache;
import com.griefdefender.permission.GDPermissionUser;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

public class LuckPermsEventHandler {

    private final LuckPerms luckPermsApi;

    public LuckPermsEventHandler(LuckPerms luckPermsApi) {
        this.luckPermsApi = luckPermsApi;
        this.luckPermsApi.getEventBus().subscribe(GroupDataRecalculateEvent.class, this::onGroupDataRecalculate);
        this.luckPermsApi.getEventBus().subscribe(UserDataRecalculateEvent.class, this::onUserDataRecalculate);
    }

    public void onGroupDataRecalculate(GroupDataRecalculateEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            final GDPermissionUser user = PermissionHolderCache.getInstance().getOrCreateUser(player);
            user.getInternalPlayerData().resetOptionCache();
            final User lpUser = this.luckPermsApi.getUserManager().getUser(player.getUniqueId());
            if (lpUser != null) {
                final Collection<Group> groups = lpUser.getInheritedGroups(QueryOptions.defaultContextualOptions());
                for (Group group : groups) {
                    if (group.equals(event.getGroup())) {
                        user.getInternalPlayerData().refreshPlayerOptions();
                    }
                }
            }
        }
    }

    public void onUserDataRecalculate(UserDataRecalculateEvent event) {
        final GDPermissionUser user = PermissionHolderCache.getInstance().getOrCreateUser(event.getUser().getUniqueId());
        if (user.getOnlinePlayer() != null) {
            user.getInternalPlayerData().resetOptionCache();
            user.getInternalPlayerData().refreshPlayerOptions();
        }
    }
}
