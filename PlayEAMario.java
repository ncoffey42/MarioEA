import agents.ea.Agent;
import engine.core.MarioGame;
import engine.core.MarioResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PlayEAMario {
    public static void printResults(MarioResult result) {
        System.out.println("****************************************************************");
        System.out.println("EA MARIO");
        System.out.println("Game Status: " + result.getGameStatus().toString() +
                " Percentage Completion: " + result.getCompletionPercentage());
        System.out.println("Lives: " + result.getCurrentLives() + " Coins: " + result.getCurrentCoins() +
                " Remaining Time: " + (int) Math.ceil(result.getRemainingTime() / 1000f));
        System.out.println("Mario State: " + result.getMarioMode() +
                " (Mushrooms: " + result.getNumCollectedMushrooms() + " Fire Flowers: " + result.getNumCollectedFireflower() + ")");
        System.out.println("Total Kills: " + result.getKillsTotal() + " (Stomps: " + result.getKillsByStomp() +
                " Fireballs: " + result.getKillsByFire() + " Shells: " + result.getKillsByShell() +
                " Falls: " + result.getKillsByFall() + ")");
        System.out.println("Bricks: " + result.getNumDestroyedBricks() + " Jumps: " + result.getNumJumps() +
                " Max X Jump: " + result.getMaxXJump() + " Max Air Time: " + result.getMaxJumpAirTime());
        System.out.println("****************************************************************");
    }

    public static String getLevel(String filepath) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(filepath)));
        } catch (IOException e) {
            System.err.println("Error reading level file: " + e.getMessage());
        }
        return content;
    }

    public static void main(String[] args) {
        String levelPath = "./levels/original/lvl-1.txt";
        String levelContent = getLevel(levelPath);
        
        // Create the evolutionary agent
        Agent evolutionaryAgent = new Agent();
        
        // First, evolve the agent using the actual level content
        System.out.println("Beginning evolution process...");
        evolutionaryAgent.evolve(levelContent);
        System.out.println("Evolution complete!");
        
        // Then run the game with the evolved agent
        MarioGame game = new MarioGame();
        System.out.println("Running the game with the evolved agent...");
        MarioResult result = game.runGame(evolutionaryAgent, levelContent, 30, 0, true);
        
        // Print the results
        System.out.println("\nFinal run results:");
        printResults(result);
    }
}