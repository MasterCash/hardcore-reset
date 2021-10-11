package dev.cashire.hardcorereset.commands;

import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.cashire.hardcorereset.Main;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class CommandBuilder {
  public static String cmd = "hardcoreReset";

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated) {
    if(dedicated) {
      dispatcher.register(
        literal(cmd).then(literal("force").requires(source -> source.hasPermissionLevel(4)).executes(CommandBuilder::forceRestart))
        .executes(CommandBuilder::restart)
      );
    }
  }

  public static int restart(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
    var server = context.getSource().getServer();
    var numPlayers = Main.playersAlive(server);
    if (numPlayers <= 0) {
      server.stop(false);
    } else {
      var player = context.getSource().getPlayer();
      Text text = Text.of("Cannot restart server due to: "  + (server.isHardcore() ? numPlayers + " players haven't died" : "server is not hardcore"));
      player.sendMessage(text, true);
      return 0;
    }
    return 1;
  }
  public static int forceRestart(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
    var server = context.getSource().getServer();
    if(server.isHardcore()) {
      Main.setForceDelete();
      server.stop(false);
    } else {
      var player = context.getSource().getPlayer();
      Text text = Text.of("Cannot force restart server due to: server is not hardcore");
      player.sendMessage(text, true);
      return 0;
    }
    return 1;
  }
  
}
