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
    private static final int MAX_GENERATIONS = 200;
    private static final double MUTATION_RATE = 0.2;
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
    private double[] generationAvgCompletion = new double[MAX_GENERATIONS];
    
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
        if (random.nextBoolean()) { // 50% chance
            defaultAction[MarioActions.LEFT.getValue()] = true;
        } else {
            defaultAction[MarioActions.RIGHT.getValue()] = true;
        }
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

            //ensureDiversity();
            
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
                // if (random.nextDouble() < CROSSOVER_RATE) {
                //     child = jumpAwareCrossover(parent1, parent2);
                // } else {
                //     child = new Chromosome(parent1); // Just copy parent1
                // }
                
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

        if (result.getCompletionPercentage() > 0.7) {
            complete_score *= 1.75;
        } else if (result.getCompletionPercentage() > 0.5) {
            complete_score *= 1.5;
        } else {
            complete_score *= 0.5;
        }
        
        // Time remaining - important
        time_score += (result.getRemainingTime() / 1000) * 20 * game_win;
        
        // Kills - medium importance
        kill_score += result.getKillsTotal() * 1.5;

        hit_score += result.getMarioNumHurts() * 3;
        
        // Mushrooms - medium-high importance
        mushroom_score += result.getNumCollectedMushrooms() * 1.5;
        
        // Coins - medium importance
        coin_score += result.getCurrentCoins() * 1.25;
        
        fitness += complete_score + time_score + kill_score + mushroom_score + coin_score - hit_score;

        if (game_win == 2) {
            fitness *= 2;
        } else {
            fitness /= 2;
        }

        return fitness;
    }

    // Add this method to your Agent class
private void injectJumpPatterns(Chromosome chromosome, Random random) {
    int position = random.nextInt(MAX_ACTIONS_SEQUENCE - 20); // Ensure enough space
    int patternType = random.nextInt(4); // Choose from 4 different jump patterns
    
    if (patternType == 0) {
        // Short hop (good for small gaps)
        int jumpDuration = 3;
        // First run a bit
        chromosome.sequence[position][MarioActions.RIGHT.getValue()] = true;
        position++;
        
        // Then jump briefly
        for (int i = 0; i < jumpDuration; i++) {
            chromosome.sequence[position + i][MarioActions.RIGHT.getValue()] = true;
            chromosome.sequence[position + i][MarioActions.JUMP.getValue()] = true;
        }
    }
    else if (patternType == 1) {
        // Medium jump (good for medium gaps)
        // Run first to build momentum
        for (int i = 0; i < 2; i++) {
            chromosome.sequence[position + i][MarioActions.RIGHT.getValue()] = true;
            chromosome.sequence[position + i][MarioActions.SPEED.getValue()] = true;
        }
        position += 2;
        
        // Then jump with precise timing
        for (int i = 0; i < 5; i++) {
            chromosome.sequence[position + i][MarioActions.RIGHT.getValue()] = true;
            chromosome.sequence[position + i][MarioActions.JUMP.getValue()] = (i < 4); // Release jump before landing
            chromosome.sequence[position + i][MarioActions.SPEED.getValue()] = true;
        }
    }
    else if (patternType == 2) {
        // Delayed jump (jump after running off edge slightly)
        for (int i = 0; i < 3; i++) {
            chromosome.sequence[position + i][MarioActions.RIGHT.getValue()] = true;
        }
        position += 3;
        
        // Jump after running slightly
        for (int i = 0; i < 5; i++) {
            chromosome.sequence[position + i][MarioActions.RIGHT.getValue()] = true;
            chromosome.sequence[position + i][MarioActions.JUMP.getValue()] = true;
        }
    }
    else {
        // Gap-clearing precision jump
        // Run to build speed
        for (int i = 0; i < 3; i++) {
            chromosome.sequence[position + i][MarioActions.RIGHT.getValue()] = true;
            chromosome.sequence[position + i][MarioActions.SPEED.getValue()] = true;
        }
        position += 3;
        
        // Jump with early release for precise control
        for (int i = 0; i < 4; i++) {
            chromosome.sequence[position + i][MarioActions.RIGHT.getValue()] = true;
            // Only hold jump for first 2-3 frames for more controlled height
            chromosome.sequence[position + i][MarioActions.JUMP.getValue()] = (i < 2 + random.nextInt(2));
            chromosome.sequence[position + i][MarioActions.SPEED.getValue()] = true;
        }
    }
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
    
    private Chromosome jumpAwareCrossover(Chromosome parent1, Chromosome parent2) {
        Chromosome child = new Chromosome(MAX_ACTIONS_SEQUENCE);
        
        // First, identify potential jump sequences in each parent
        // A jump sequence is where JUMP is true for multiple consecutive frames
        boolean[] isJumpSequence1 = findJumpSequences(parent1.sequence);
        boolean[] isJumpSequence2 = findJumpSequences(parent2.sequence);
        
        // Now perform crossover, but preserve jump sequences
        for (int i = 0; i < MAX_ACTIONS_SEQUENCE; i++) {
            if ((isJumpSequence1[i] && parent1.fitness > parent2.fitness) ||
                (isJumpSequence2[i] && parent2.fitness > parent1.fitness)) {
                // Keep jump sequence from fitter parent
                child.sequence[i] = (parent1.fitness > parent2.fitness) ?
                                    Arrays.copyOf(parent1.sequence[i], MarioActions.numberOfActions()) :
                                    Arrays.copyOf(parent2.sequence[i], MarioActions.numberOfActions());
            } else {
                // Regular uniform crossover for non-jump sequences
                child.sequence[i] = random.nextBoolean() ?
                                   Arrays.copyOf(parent1.sequence[i], MarioActions.numberOfActions()) :
                                   Arrays.copyOf(parent2.sequence[i], MarioActions.numberOfActions());
            }
        }
        
        return child;
    }
    
    // Helper method to identify jump sequences
    private boolean[] findJumpSequences(boolean[][] sequence) {
        boolean[] isJumpSequence = new boolean[sequence.length];
        
        // Look for sequences of at least 3 frames where JUMP is pressed
        int jumpCounter = 0;
        for (int i = 0; i < sequence.length; i++) {
            if (sequence[i][MarioActions.JUMP.getValue()]) {
                jumpCounter++;
                
                if (jumpCounter >= 3) {
                    // Mark current and previous frames as part of a jump sequence
                    isJumpSequence[i] = true;
                    isJumpSequence[i-1] = true;
                    isJumpSequence[i-2] = true;
                }
            } else {
                jumpCounter = 0;
            }
        }
        
        return isJumpSequence;
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
    
    //Mutation operator
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

    
    // Add this field to the Agent class


// Modify the recordGenerationStats method to also track average completion
private void recordGenerationStats(int generation) {
    double totalFitness = 0.0;
    double totalCompletion = 0.0;
    double bestFitness = population[0].fitness;
    double bestCompletion = population[0].result.getCompletionPercentage();
    
    for (Chromosome c : population) {
        totalFitness += c.fitness;
        totalCompletion += c.result.getCompletionPercentage();
    }
    
    double avgFitness = totalFitness / POPULATION_SIZE;
    double avgCompletion = totalCompletion / POPULATION_SIZE;
    
    generationBestFitness[generation] = bestFitness;
    generationAvgFitness[generation] = avgFitness;
    generationAvgCompletion[generation] = avgCompletion;
    
    System.out.println("Generation " + (generation + 1) + 
                       " - Best: " + bestFitness +
                       ", Avg: " + avgFitness +
                       ", Best Completion: " + bestCompletion +
                       ", Avg Completion: " + avgCompletion);
}

// Modify the initializeCSV method to include the completion columns
private void initializeCSV() {
    try (FileWriter writer = new FileWriter(CSV_FILE_PATH)) {
        writer.append("Generation,BestFitness,AvgFitness,BestCompletion,AvgCompletion,RemainingTime,KillsTotal," +
                      "CollectedMushrooms,CollectedCoins,GameStatus\n");
    } catch (IOException e) {
        System.err.println("Error creating CSV file: " + e.getMessage());
    }
}

// Modify the logGenerationToCSV method to include the completion data
private void logGenerationToCSV(int generation) {
    try (FileWriter writer = new FileWriter(CSV_FILE_PATH, true)) {
        // Find the best chromosome
        Chromosome best = population[0];
        MarioResult result = best.result;
        
        writer.append(String.format("%d,%f,%f,%f,%f,%d,%d,%d,%d,%s\n",
            generation + 1,
            generationBestFitness[generation],
            generationAvgFitness[generation],
            result.getCompletionPercentage(),
            generationAvgCompletion[generation],
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
                if (random.nextDouble() < 0.3 && i < sequence.length - 10) { // 5% chance, ensure enough space
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

            int numJumpPatterns = random.nextInt(10) + 5; // Add 5-15 jump patterns
    
    for (int i = 0; i < numJumpPatterns; i++) {
        int position = random.nextInt(MAX_ACTIONS_SEQUENCE - 10);
        
        // Choose a jump style
        int jumpStyle = random.nextInt(3);
        
        if (jumpStyle == 0) {
            // Early release jump (shorter)
            int holdFrames = random.nextInt(2) + 1; // Hold jump for 1-2 frames
            
            for (int j = 0; j < holdFrames; j++) {
                sequence[position + j][MarioActions.JUMP.getValue()] = true;
                sequence[position + j][MarioActions.RIGHT.getValue()] = true;
            }
            
            // Continue moving right after releasing jump
            for (int j = holdFrames; j < holdFrames + 2; j++) {
                sequence[position + j][MarioActions.RIGHT.getValue()] = true;
            }
        }
        else if (jumpStyle == 1) {
            // Medium jump
            int holdFrames = random.nextInt(2) + 3; // Hold jump for 3-4 frames
            
            for (int j = 0; j < holdFrames; j++) {
                sequence[position + j][MarioActions.JUMP.getValue()] = true;
                sequence[position + j][MarioActions.RIGHT.getValue()] = true;
            }
        }
        else {
            // Delayed jump (run a bit first)
            for (int j = 0; j < 2; j++) {
                sequence[position + j][MarioActions.RIGHT.getValue()] = true;
            }
            
            for (int j = 2; j < 5; j++) {
                sequence[position + j][MarioActions.JUMP.getValue()] = true;
                sequence[position + j][MarioActions.RIGHT.getValue()] = true;
            }
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
            if (Math.random() < 0.5) { // 50% chance
                defaultAction[MarioActions.LEFT.getValue()] = true;
            } else {
                defaultAction[MarioActions.RIGHT.getValue()] = true;
            }
            return defaultAction;
        }
        
        @Override
        public String getAgentName() {
            return "EARunnerAgent";
        }
            
    }
}