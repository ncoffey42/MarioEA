
import agents.ea.NeuroEAgent;
import engine.core.MarioGame;
import engine.core.MarioResult;
import agents.ea.NeuroEAgent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PlayNEATMario {

    public static void printResults(MarioResult result) {
        System.out.println("****************************************************************");
        System.out.println("NEUROEVOLUTIONARY MARIO");
        System.out.println("Game Status: " + result.getGameStatus() +
                " Completion: " + result.getCompletionPercentage());
        System.out.println("Lives: " + result.getCurrentLives() + " Coins: " + result.getCurrentCoins() +
                " Time Left: " + (int)Math.ceil(result.getRemainingTime() / 1000f));
        System.out.println("Kills: " + result.getKillsTotal() + " Bricks: " + result.getNumDestroyedBricks());
        System.out.println("Max X Jump: " + result.getMaxXJump() + " Air Time: " + result.getMaxJumpAirTime());
        System.out.println("****************************************************************");
    }

    public static String getLevel(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            System.err.println("Error loading level file: " + e.getMessage());
            return "";
        }
    }

    public static void main(String[] args) {
        String levelPath = "./levels/original/lvl-1.txt";
        String levelContent = getLevel(levelPath);

        NeuroEAgent agent = new NeuroEAgent();

        System.out.println("Training NeuroEvolutionary Agent...");
        MarioGame game = new MarioGame();
        game.runGame(agent, levelContent, 30, 0, false); // Training in initialize()

        System.out.println("Finished training. Running final simulation...");
        MarioResult result = game.runGame(agent, levelContent, 30, 0, true);

        printResults(result);
    }
}
