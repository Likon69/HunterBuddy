package com.hunterbuddy.behavior.command;

import com.hunterbuddy.behavior.lab.SelfCheck;
import com.hunterbuddy.behavior.lab.SelfCheckReport;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public final class BehaviorCommand extends Command {
    public BehaviorCommand() {
        super("bt", "Run behavior tree foundation checks.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("selfcheck").executes(context -> selfcheck()));
    }

    private int selfcheck() {
        try {
            SelfCheckReport report = SelfCheck.run();
            info("Foundation OK: %d checks, %d simulated ticks. Phase 1 acceptance remains incomplete.", report.checks().size(), report.ticks());
            return SINGLE_SUCCESS;
        } catch (RuntimeException ex) {
            error("SelfCheck failed: %s", ex.getMessage());
            return 0;
        }
    }
}
