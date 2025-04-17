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



public class NeuroEAgent implements MarioAgent {

    private NEATNetwork neatNetwork;
    private EvolutionaryAlgorithm trainer;
    private NEATPopulation population;
    private boolean isTrained = false;
    private MarioForwardModel seedModel;

    private static final int POPULATION_SIZE = 300;
    private static final int MAX_GENERATIONS = 300;

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        if (!isTrained) {
            // Clone initial model for repeated simulations
            seedModel = model.clone();

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

            // Evolve for specified generations
            for (int i = 0; i < MAX_GENERATIONS; i++) {
                trainer.iteration();
                System.out.println("Gen " + i + ", Best Fitness: " + trainer.getBestGenome().getScore());
            }

            // Decode best network
            neatNetwork = (NEATNetwork) trainer.getCODEC().decode(trainer.getBestGenome());
            // Save the end population to a file, the best genome will then be selected from that, this is done due to encog persistence saving mechanisms
            // Individual network can't be saved to file. 
            EncogDirectoryPersistence.saveObject(new File("./saved_agents/best-neat-marioPopulation.eg"), population);

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
        // Compute fitness by completion and rewards
        fitness += model.getCompletionPercentage() * 5000.0;
        fitness += model.getKillsTotal() * 10.0;
        fitness += model.getNumCollectedMushrooms() * 15.0;
        fitness += model.getNumCollectedCoins() * 2.0;
        // Optional penalty for falling too low
        fitness -= model.getMarioFloatPos()[1] / 10.0;

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

        // Load the best saved NEAT population
        population = (NEATPopulation) EncogDirectoryPersistence.loadObject(new File(population_path));
        // Create a new trainer with the loaded population
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
}
