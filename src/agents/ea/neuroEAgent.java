import org.encog.Encog;
import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.ml.ea.train.EvolutionaryAlgorithm;
import org.encog.neural.neat.NEATNetwork;
import org.encog.neural.neat.NEATPopulation;
import org.encog.neural.neat.NEATUtil;
import org.encog.neural.networks.training.TrainingSetScore;
import org.encog.util.simple.EncogUtility;


public class NeuroEAgent implements MarioAgent {

    
    private NEATNetwork neatNetwork;
    private EvolutionaryAlgorithm trainer;
    private NEATPopulation population;
    private boolean isTrained = false; 

    // EA parameters
    private static final int POPULATION_SIZE = 300;
    private static final int MAX_GENERATIONS = 1;
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.3;
    private static final int TOURNAMENT_SIZE = 4;
    private static final int MAX_ACTIONS_SEQUENCE = 1000; // Maximum action sequence length
    
    // For storing results
    private static final String CSV_FILE_PATH = "neuroevolutionary_mario_results.csv";
    
    // Pass ForwardModel for Grid visual input, and timer for time remaining
    public void init(MarioForwardModel model, MarioTimer timer) {

        if (neatNetwork == null) {
            // Inputs

            // 2D Grid of Enemies (0 = Most detailed, enemy type etc...)
            // Network will need to learn associations for certain enemy types
            int[][] enemyGrid = model.getMarioEnemiesObservation(0);
            // 2D Grid of Terrain (1 = only useful information)
            //  0 includes detail about decorative blocks
            int[][] levelGrid = model.getMarioSceneObservation(1);

            boolean canJump = model.mayMarioJump();
            boolean onGround = model.isMarioOnGround();
            float[] marioVelocity = model.getMarioFloatVelocity();
            // float[] marioPos = model.getMarioFloatPos();


        }

        // Outputs



    }

}