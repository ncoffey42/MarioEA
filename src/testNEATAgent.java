import agents.ea.NeuroEAgent;
import engine.core.MarioGame;
import engine.core.MarioResult;
import java.io.File;

public class testNEATAgent {

    public static void main(String[] args) {

        // Load Levels to test the Agent on in order to determine generalizability
        String[] levelpaths = {
            "./levels/original/lvl-1.txt",
            "./levels/original/lvl-2.txt",
            "./levels/original/lvl-3.txt",
            "./levels/original/lvl-4.txt",
            "./levels/original/lvl-5.txt"
        };

        MarioGame game = new MarioGame();
        NeuroEAgent agent = new NeuroEAgent();
        agent.loadTrainedAgent("./saved_agents/best-neat-marioPopulation.eg");
        
        // Run all of the levels with the trained agent
        for (String path: levelpaths) {

            String level = PlayNEATMario.getLevel(path);
            MarioResult result = game.runGame(agent, level, 30, 0, true);
            PlayNEATMario.printResults(result);
        }
        
    }
}