package agents.ea;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioGame;
import engine.core.MarioResult;
import engine.core.MarioTimer;
import engine.helper.GameStatus;
import engine.helper.MarioActions;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

public class Agent implements MarioAgent {
    // EA parameters
    private static final int POPULATION_SIZE = 300;
    private static final int MAX_GENERATIONS = 1;
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.3;
    private static final int TOURNAMENT_SIZE = 4;
    private static final int MAX_ACTIONS_SEQUENCE = 1000; // Maximum action sequence length
    
    // For storing results
    private static final String CSV_FILE_PATH = "evolutionary_mario_results.csv";
    
    // Current state of the EA
    private int currentGeneration = 0;
    private Chromosome[] population = new Chromosome[POPULATION_SIZE];
    private Chromosome bestChromosome;
    private Random random = new Random();
    
    // To measure improvement over generations
    private double[] generationBestFitness = new double[MAX_GENERATIONS];
    private double[] generationAvgFitness = new double[MAX_GENERATIONS];
    
    // Current action being executed from the chromosome
    private int currentActionIndex = 0;
    
    // For real-time play (this will hold our best evolved sequence)
    private boolean[][] actionSequence;
    
    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        // Reset the action counter
        currentActionIndex = 0;
        
        // If we haven't evolved yet, create a population
        if (population[0] == null) {
            initializePopulation();
        }
    }
    
    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
        // If we have an evolved sequence, use it
        if (actionSequence != null && currentActionIndex < actionSequence.length) {
            return actionSequence[currentActionIndex++];
        }
        
        // If no evolved sequence yet or exhausted it, return a default action (move right)
        boolean[] defaultAction = new boolean[MarioActions.numberOfActions()];
        defaultAction[MarioActions.RIGHT.getValue()] = true;
        return defaultAction;
    }
    
    @Override
    public String getAgentName() {
        return "EvolutionaryAgent";
    }
    
    // Initialize population with random chromosomes
    private void initializePopulation() {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            population[i] = new Chromosome(MAX_ACTIONS_SEQUENCE);
            population[i].randomize(random);
        }
    }
    
    // Evolve the population for the specified number of generations
    public void evolve(String levelContent) {
        // Initialize population if not done already
        if (population[0] == null) {
            initializePopulation();
        }
        
        initializeCSV();
        
        for (int gen = 0; gen < MAX_GENERATIONS; gen++) {
            currentGeneration = gen;
            System.out.println("Generation " + (gen + 1) + "/" + MAX_GENERATIONS);
            
            // Evaluate fitness of each chromosome by actually playing the game
            evaluatePopulation(levelContent);
            
            // Sort population by fitness (descending)
            Arrays.sort(population, Comparator.comparing((Chromosome c) -> c.fitness).reversed());
            
            // Store the best chromosome
            bestChromosome = new Chromosome(population[0]);
            
            // Record statistics
            recordGenerationStats(gen);
            
            // Create new population through selection, crossover, and mutation
            Chromosome[] newPopulation = new Chromosome[POPULATION_SIZE];
            
            // Elitism: Keep the best chromosome
            newPopulation[0] = new Chromosome(bestChromosome);
            
            // Generate the rest of the population
            for (int i = 1; i < POPULATION_SIZE; i++) {
                // Selection
                Chromosome parent1 = tournamentSelection();
                Chromosome parent2 = tournamentSelection();
                
                // Crossover
                Chromosome child;
                if (random.nextDouble() < CROSSOVER_RATE) {
                    child = uniformCrossover(parent1, parent2);
                } else {
                    child = new Chromosome(parent1); // Just copy parent1
                }
                
                // Mutation
                if (random.nextDouble() < MUTATION_RATE) {
                    mutate(child);
                }
                
                newPopulation[i] = child;
            }
            
            // Replace old population
            population = newPopulation;
            
            // Log to CSV
            logGenerationToCSV(gen);
        }
        
        // Set the best chromosome for final run
        Arrays.sort(population, Comparator.comparing((Chromosome c) -> c.fitness).reversed());
        bestChromosome = new Chromosome(population[0]);
        
        // Store the evolved action sequence for playback
        actionSequence = bestChromosome.sequence;
        currentActionIndex = 0;
        
        System.out.println("Evolution complete!");
        System.out.println("Best fitness: " + bestChromosome.fitness);
    }
    
    // Evaluate the fitness of each chromosome in the population
    private void evaluatePopulation(String levelContent) {
        Arrays.stream(population).parallel().forEach(chromosome -> {
            MarioResult result = evaluateChromosome(chromosome, levelContent);
            chromosome.fitness = calculateFitness(result);
            chromosome.result = result;
        });

        // Optionally print every 10th chromosome — but outside the parallel block
        for (int i = 0; i < POPULATION_SIZE; i += 10) {
            System.out.println("Evaluated " + (i + 1) + "/" + POPULATION_SIZE +
                            " - Fitness: " + population[i].fitness +
                            " - Completion: " + population[i].result.getCompletionPercentage());
        }
    }
    
    // Run Mario simulation with the given chromosome and return the result
    private MarioResult evaluateChromosome(Chromosome chromosome, String levelContent) {
        MarioGame game = new MarioGame();
        EARunnerAgent runner = new EARunnerAgent(chromosome.sequence);
        return game.runGame(runner, levelContent, 20, 0, false);  // Set visualization to false for speed
    }
    
    // Calculate fitness based on the given criteria
    private double calculateFitness(MarioResult result) {
        double fitness = 0.0;
        double complete_score = 0.0;
        double time_score = 0.0;
        double kill_score = 0.0;
        double coin_score = 0.0;
        double mushroom_score = 0.0;
        double hit_score = 0.0;
        double game_win = 0.0;

        if (result.getGameStatus() == GameStatus.WIN) {
            game_win = 2;
        }
        
        // Completion percentage - most important
        complete_score += (result.getCompletionPercentage() * 4000);
        
        // Time remaining - important
        time_score += (result.getRemainingTime() / 1000) * 10 * game_win;
        
        // Kills - medium importance
        kill_score += result.getKillsTotal() * 1.5;

        hit_score += result.getMarioNumHurts() * 3;
        
        // Mushrooms - medium-high importance
        mushroom_score += result.getNumCollectedMushrooms() * 1.5;
        
        // Coins - medium importance
        coin_score += result.getCurrentCoins() * 1.25;
        
        fitness += complete_score + time_score + kill_score + mushroom_score + coin_score - hit_score;

        return fitness;
    }
    
    // Tournament selection
    private Chromosome tournamentSelection() {
        Chromosome best = population[random.nextInt(POPULATION_SIZE)];
        
        for (int i = 1; i < TOURNAMENT_SIZE; i++) {
            Chromosome contender = population[random.nextInt(POPULATION_SIZE)];
            if (contender.fitness > best.fitness) {
                best = contender;
            }
        }
        
        return best;
    }
    
    // Uniform crossover
    private Chromosome uniformCrossover(Chromosome parent1, Chromosome parent2) {
        Chromosome child = new Chromosome(MAX_ACTIONS_SEQUENCE);
        
        for (int i = 0; i < MAX_ACTIONS_SEQUENCE; i++) {
            // For each position, randomly choose from either parent
            if (random.nextBoolean()) {
                child.sequence[i] = Arrays.copyOf(parent1.sequence[i], MarioActions.numberOfActions());
            } else {
                child.sequence[i] = Arrays.copyOf(parent2.sequence[i], MarioActions.numberOfActions());
            }
        }
        
        return child;
    }
    
    // Mutation operator
    private void mutate(Chromosome chromosome) {
        // Randomly select a few positions to mutate
        int mutationPoints = random.nextInt(MAX_ACTIONS_SEQUENCE / 10) + 1; // Mutate 1-10% of the sequence
        
        for (int i = 0; i < mutationPoints; i++) {
            int position = random.nextInt(MAX_ACTIONS_SEQUENCE);
            int actionIndex = random.nextInt(MarioActions.numberOfActions());
            
            // Flip the action at the position
            chromosome.sequence[position][actionIndex] = !chromosome.sequence[position][actionIndex];
        }
    }
    
    // Record statistics for the current generation
    private void recordGenerationStats(int generation) {
        double totalFitness = 0.0;
        double bestFitness = population[0].fitness;
        
        for (Chromosome c : population) {
            totalFitness += c.fitness;
        }
        
        double avgFitness = totalFitness / POPULATION_SIZE;
        
        generationBestFitness[generation] = bestFitness;
        generationAvgFitness[generation] = avgFitness;
        
        System.out.println("Generation " + (generation + 1) + 
                           " - Best: " + bestFitness +
                           ", Avg: " + avgFitness);
    }
    
    // Initialize CSV file with headers
    private void initializeCSV() {
        try (FileWriter writer = new FileWriter(CSV_FILE_PATH)) {
            writer.append("Generation,BestFitness,AvgFitness,CompletionPercentage,RemainingTime,KillsTotal," +
                          "CollectedMushrooms,CollectedCoins,GameStatus\n");
        } catch (IOException e) {
            System.err.println("Error creating CSV file: " + e.getMessage());
        }
    }
    
    // Log generation statistics to CSV
    private void logGenerationToCSV(int generation) {
        try (FileWriter writer = new FileWriter(CSV_FILE_PATH, true)) {
            // Find the best chromosome
            Chromosome best = population[0];
            MarioResult result = best.result;
            
            writer.append(String.format("%d,%f,%f,%f,%d,%d,%d,%d,%s\n",
                generation + 1,
                generationBestFitness[generation],
                generationAvgFitness[generation],
                result.getCompletionPercentage(),
                result.getRemainingTime(),
                result.getKillsTotal(),
                result.getNumCollectedMushrooms(),
                result.getNumCollectedTileCoins(),
                result.getGameStatus().toString()
            ));
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }
    
    // Chromosome class representing a sequence of actions
    private static class Chromosome {
        boolean[][] sequence; // Sequence of actions
        double fitness = 0.0;
        MarioResult result = null;
        
        public Chromosome(int length) {
            sequence = new boolean[length][MarioActions.numberOfActions()];
        }
        
        public Chromosome(Chromosome other) {
            // Deep copy
            sequence = new boolean[other.sequence.length][MarioActions.numberOfActions()];
            for (int i = 0; i < other.sequence.length; i++) {
                sequence[i] = Arrays.copyOf(other.sequence[i], other.sequence[i].length);
            }
            fitness = other.fitness;
            result = other.result;
        }
        
        public void randomize(Random random) {
            for (int i = 0; i < sequence.length; i++) {
                for (int j = 0; j < sequence[i].length; j++) {
                    sequence[i][j] = random.nextDouble() < 0.2; // 20% chance of action being true
                }
                
                // Don't press left and right at the same time
                if (sequence[i][MarioActions.LEFT.getValue()] && sequence[i][MarioActions.RIGHT.getValue()]) {
                    sequence[i][random.nextBoolean() ? MarioActions.LEFT.getValue() : MarioActions.RIGHT.getValue()] = false;
                }
                
                // Higher chance of moving right
                if (!sequence[i][MarioActions.LEFT.getValue()] && !sequence[i][MarioActions.RIGHT.getValue()] && !sequence[i][MarioActions.JUMP.getValue()]) {
                    sequence[i][MarioActions.RIGHT.getValue()] = random.nextDouble() < 0.7; // 70% chance of moving right
                }
                
                // Occasionally create a high jump sequence
                if (random.nextDouble() < 0.2 && i < sequence.length - 10) { // 5% chance, ensure enough space
                    int jumpDuration = random.nextInt(3) + 5; // Jump held for 5-7 frames
                    
                    for (int j = 0; j < jumpDuration; j++) {
                        if (i + j < sequence.length) {
                            // Press jump and speed for multiple frames
                            sequence[i + j][MarioActions.JUMP.getValue()] = true;
                            sequence[i + j][MarioActions.SPEED.getValue()] = true;
                            sequence[i + j][MarioActions.RIGHT.getValue()] = true; // Keep moving right while jumping
                        }
                    }
                    
                    i += jumpDuration - 1; // Skip ahead to avoid modifying the jump sequence we just created
                }
            }
        }
    }
    
    // Agent that runs a predefined sequence of actions
    private static class EARunnerAgent implements MarioAgent {
        private boolean[][] actionSequence;
        private int currentAction = 0;
        
        public EARunnerAgent(boolean[][] actionSequence) {
            this.actionSequence = actionSequence;
        }
        
        @Override
        public void initialize(MarioForwardModel model, MarioTimer timer) {
            currentAction = 0;
        }
        
        @Override
        public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
            if (currentAction < actionSequence.length) {
                return actionSequence[currentAction++];
            }
            // If we reach the end of the sequence, return default action (move right)
            boolean[] defaultAction = new boolean[MarioActions.numberOfActions()];
            defaultAction[MarioActions.RIGHT.getValue()] = true;
            return defaultAction;
        }
        
        @Override
        public String getAgentName() {
            return "EARunnerAgent";
        }
            
    }
}