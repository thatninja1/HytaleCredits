package dev.hytalemodding.creditsystem.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.creditsystem.config.CreditConfig;
import dev.hytalemodding.creditsystem.service.CreditsService;
import dev.hytalemodding.creditsystem.ui.HytaleCreditShopPage;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class CreditShopOpenCommand extends AbstractPlayerCommand {
    private final CreditsService creditsService;
    private final CreditConfig config;
    private final Logger logger;

    public CreditShopOpenCommand(CreditsService creditsService, CreditConfig config, Logger logger) {
        super("creditshop", "Open the credit shop");
        this.creditsService = creditsService;
        this.config = config;
        this.logger = logger;
        this.addAliases("cshop");
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> senderRef, PlayerRef senderPlayerRef, World world) {
        if (!creditsService.isOnline()) {
            context.sendMessage(Message.raw("Credits system unavailable."));
            return;
        }

        try {
            Player player = store.getComponent(senderRef, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Failed to open Credit Shop UI (player component missing)."));
                return;
            }

            player.getPageManager().openCustomPage(
                    senderRef,
                    store,
                    new HytaleCreditShopPage(senderPlayerRef, creditsService, config, logger)
            );
            context.sendMessage(Message.raw("Credit shop opened."));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[CreditSystem] Failed to open credit shop UI", e);
            context.sendMessage(Message.raw("Failed to open Credit Shop UI (check server logs)."));
        }
    }
}
