package dev.foldedhorizons.core.machine;

import net.minecraft.world.level.block.Block;

/**
 * Root Binder — the ownership machine, designed as a GregTech CEu Modern
 * singleblock. Lives INSIDE the shared (team) space; operates on the
 * inserting player, no items involved.
 *
 * <p>Designed behavior:</p>
 * <ul>
 *   <li>Merge (personal → team): converts the player's personal space into
 *       the team space if the team has none. Registry entry and FTB Chunks
 *       claims transfer to the team UUID. One-way.</li>
 *   <li>Purge (personal → team's space): if the team already has a space,
 *       the player demolishes their personal cell and their destination
 *       re-addresses to the team space.</li>
 *   <li>Split (team → personal): a member leaving the team reclaims a fresh
 *       personal space; the team space stays with the team.</li>
 *   <li>30-second grace window: a confirmed conversion schedules the switch
 *       30 s out. During the window the breacher refuses entry to the
 *       affected space and the binder shows a countdown. At expiry the
 *       registry switch applies and claims are re-registered, so ownership
 *       and protection never diverge.</li>
 * </ul>
 *
 * <p>TODO: implement conversion scheduling + grace-window state in a block
 * entity, hook FTB Chunks claim transfer. Currently an inert block
 * placeholder.</p>
 */
public class RootBinderBlock extends Block {

    public RootBinderBlock(Properties properties) {
        super(properties);
        // TODO: conversion GUI (merge / purge / split) + grace countdown.
    }
}
