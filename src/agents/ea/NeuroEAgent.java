package agents.ea;

import org.encog.Encog;
import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.CalculateScore;
import org.encog.ml.MLMethod;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.ea.train.EvolutionaryAlgorithm;
import org.encog.neural.neat.NEATNetwork;
import org.encog.neural.neat.NEATPopulation;
import org.encog.neural.neat.NEATUtil;
import org.encog.persist.EncogDirectoryPersistence;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.helper.GameStatus;
import org.encog.ml.ea.genome.Genome;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import engine.core.MarioWorld;

public class NeuroEAgent implements MarioAgent {

    private NEATNetwork neatNetwork;
    private EvolutionaryAlgorithm trainer;
    private NEATPopulation population;
    private boolean isTrained = false;
    private MarioForwardModel seedModel;

    private static final int POPULATION_SIZE = 300;
    private static final int MAX_GENERATIONS = 1500;

    // Training Levels
    private static final String[] TRAINING_LEVELS = {
            "./levels/original/lvl-1.txt",
            "./levels/original/lvl-2.txt",
            "./levels/original/lvl-3.txt",
            "./levels/original/lvl-4.txt",
            "./levels/original/lvl-5.txt",
            "./levels/original/lvl-6.txt",
            "./levels/original/lvl-7.txt",
            "./levels/original/lvl-8.txt",
            "./levels/original/lvl-9.txt",
            "./levels/original/lvl-10.txt",
    };
    // private String currentLevel;
    private MarioForwardModel currentSim;
    private String currentLevelData;
    double bestAvgFitness = Double.NEGATIVE_INFINITY;
    Genome bestAvgGenome = null;

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {

        if (!isTrained) {
            
            // for (int i = 0; i < TRAINING_LEVELS.length; i++) {
            //     currentLevel = TRAINING_LEVELS[i];
            //     System.out.println("Level " + (i+1) + " Traning...");
            //     // Load the level
            //     currentLevel = PlayNEATMario.getLevel(currentLevel);
            // }

            // Clone initial model for repeated simulations
            // seedModel = model.clone();

            int[][] screenGrid = model.getMarioCompleteObservation();
            int rows = screenGrid.length;
            int cols = screenGrid[0].length;
            int inputSize = (rows * cols) + 1 + 1 + 2; // grid + canJump + onGround + velocity X,Y
            int outputSize = 5; // LEFT, RIGHT, DOWN, SPEED, JUMP

            population = new NEATPopulation(inputSize, outputSize, POPULATION_SIZE);
            population.setInitialConnectionDensity(0.5);
            population.reset();

            trainer = NEATUtil.constructNEATTrainer(population, new CalculateScore() {
                @Override
                public double calculateScore(MLMethod method) {
                    // Evaluate network by simulating game on cloned model
                    NEATNetwork network = (NEATNetwork) method;
                    double sum = 0;

                    for (String levelPath : TRAINING_LEVELS) {

                        String levelData = getLevel(levelPath);
                        MarioWorld world = new MarioWorld(null);
                        world.initializeLevel(levelData, 30 * 1000);
                        MarioForwardModel sim = new MarioForwardModel(world);
                        sum += evaluateNetwork(network, sim);
                    }
                    return sum / TRAINING_LEVELS.length;

                    // Initialize a new world
                    // MarioWorld world = new MarioWorld(null);
                    // world.initializeLevel(currentLevelData, 30 * 1000);

                    // MarioForwardModel sim = new MarioForwardModel(world);

                    // /// This works but trains only on 1 level
                    // // MarioForwardModel sim = currentSim.clone();
                    // // MarioForwardModel sim = new MarioForwardModel(currentLevel);
                    // return evaluateNetwork(network, sim);
                }

                @Override public boolean shouldMinimize() { return false; }
                @Override public boolean requireSingleThreaded() { return false; }
            });

            for (int gen=0; gen<MAX_GENERATIONS; gen++) {
                trainer.iteration();
                System.out.println("Gen " + gen + ", AvgFitness: " + trainer.getBestGenome().getScore());

            }
            // Genome bestOverall = trainer.getBestGenome();


            // Split into 6 epochs of 5 generations cycling through each level. For a total of 30 generations on each level. 
            // Evolve for specified generations
            // int total_gens = 0;
            // double sum = 0;
            // for (int epoch = 0; epoch < 6; epoch++) {

            //     System.out.println("Epoch " + (epoch + 1) + " of 6");
            //     for (int lvl = 0; lvl < TRAINING_LEVELS.length; lvl++) {

            //         // Load the level
            //         System.out.println("Level " + (lvl + 1));
            //         currentLevelData = getLevel(TRAINING_LEVELS[lvl]);

            //         for (int gen = 0; gen < 5; gen++, total_gens++) {
            //             trainer.iteration();
            //             double bestFitness = trainer.getBestGenome().getScore();
            //             Genome currentBest = trainer.getBestGenome();

            //             NEATNetwork bestNetwork = (NEATNetwork) trainer.getCODEC().decode(currentBest);

            //             // Keep track of the best average genome
            //             for (String levelPath: TRAINING_LEVELS) {
            //                 MarioWorld w = new MarioWorld(null);
            //                 w.initializeLevel(getLevel(levelPath), 30 * 1000);
            //                 MarioForwardModel evalSim = new MarioForwardModel(w);
            //                 // Add together score across all levels
            //                 sum += evaluateNetwork(bestNetwork, evalSim);
            //             }
            //             // Calculate the average fitness across all levels
            //             double avgAll = sum / TRAINING_LEVELS.length;
            //             sum = 0;

            //             if (avgAll > bestAvgFitness) {
            //                 bestAvgFitness = avgAll;
            //                 bestAvgGenome = currentBest;
            //             }
            //             // double percent_complete = bestNetwork.getCompletionPercentage();
            //             System.out.println("Gen " + total_gens + ", " + "AvgFitness " + bestAvgFitness + ", Best Fitness: " + bestFitness); //+ ", Completion: " + percent_complete);

            //         }
            //     }
            // }
            // for (int i = 0; i < MAX_GENERATIONS; i++) {
            //     trainer.iteration();
            //     System.out.println("Gen " + i + ", Best Fitness: " + trainer.getBestGenome().getScore());
            // }

            // Decode best network
            neatNetwork = (NEATNetwork) trainer.getCODEC().decode(trainer.getBestGenome());
            // population.setBestGenome(bestGeneralGenome);
            // Save the end population to a file, the best genome will then be selected from that, this is done due to encog persistence saving mechanisms
            // Individual network can't be saved to file. 
            EncogDirectoryPersistence.saveObject(new File("./saved_agents/1500NEAT-marioPopulation.eg"), population);

            isTrained = true;
        }
    }

    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
        // Flatten inputs and query network
        double[] input = extractInputs(model);
        MLData output = neatNetwork.compute(new BasicMLData(input));

        boolean[] actions = new boolean[5];
        for (int i = 0; i < actions.length; i++) {
            actions[i] = output.getData(i) > 0.5;
        }
        return actions;
    }

    @Override
    public String getAgentName() {
        return "NeuroEAgent";
    }

    private double evaluateNetwork(NEATNetwork network, MarioForwardModel model) {
        double fitness = 0.0;
        // Run a fixed number of ticks or until end
        for (int step = 0; step < 1000 && model.getGameStatus() == GameStatus.RUNNING; step++) {
            double[] inputs = extractInputs(model);
            MLData output = network.compute(new BasicMLData(inputs));
            boolean[] actions = new boolean[5];
            for (int j = 0; j < actions.length; j++) {
                actions[j] = output.getData(j) > 0.5;
            }
            model.advance(actions);
        }

        // 0 if Agent dies, 2 if it wins
        int game_win = 0;

        // If agent wins, add a x2 bonus to fitness
        if (model.getGameStatus() == GameStatus.WIN) {
            game_win = 2;
        }

        // Compute fitness by completion and rewards
        fitness += model.getCompletionPercentage() * 4000.0;
        fitness += (model.getRemainingTime() / 1000) * 10 * game_win;
        fitness += model.getKillsTotal() * 1.5;
        fitness += model.getNumCollectedMushrooms() * 1.5;
        fitness += model.getNumCollectedCoins() * 1.25;
        // Optional penalty for falling too low
        // fitness -= model.getMarioFloatPos()[1] / 10.0;

        return fitness;
    }

    private double[] extractInputs(MarioForwardModel model) {
        int[][] grid = model.getMarioCompleteObservation();
        int rows = grid.length;
        int cols = grid[0].length;
        double[] inputs = new double[rows * cols + 4];

        int idx = 0;
        for (int[] row : grid) {
            for (int cell : row) {
                inputs[idx++] = cell / 100.0;
            }
        }
        inputs[idx++] = model.mayMarioJump() ? 1.0 : 0.0;
        inputs[idx++] = model.isMarioOnGround() ? 1.0 : 0.0;
        inputs[idx++] = model.getMarioFloatVelocity()[0] / 10.0;
        inputs[idx++] = model.getMarioFloatVelocity()[1] / 10.0;

        return inputs;
    }

    public void loadTrainedAgent(String population_path) {


        
        // // Load the best saved NEAT population
        population = (NEATPopulation) EncogDirectoryPersistence.loadObject(new File(population_path));
        // // Create a new trainer with the loaded population
        trainer = NEATUtil.constructNEATTrainer(population, new CalculateScore() {
            @Override
            public double calculateScore(MLMethod method) {
                // Evaluate network by simulating game on cloned model
                NEATNetwork network = (NEATNetwork) method;
                MarioForwardModel sim = seedModel.clone();
                return evaluateNetwork(network, sim);
            }

            @Override
            public boolean shouldMinimize() {
                return false;
            }

            @Override
            public boolean requireSingleThreaded() {
                return false;
            }
        });
        // Load the best genome from the population

        // Load the best saved Mario NEAT agent genome
        // Genome genome = (Genome) EncogDirectoryPersistence.loadObject(new File(agent_genome_path));
        neatNetwork = (NEATNetwork) trainer.getCODEC().decode(population.getBestGenome());
        // Set isTrained to true to avoid retraining
        isTrained = true;


    }

    public static String getLevel(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            System.err.println("Error loading level file: " + e.getMessage());
            return "";
        }
    }
}
