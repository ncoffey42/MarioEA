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

public class XORNEAT {
    public static double XOR_INPUT[][] = {
        { 0.0, 0.0 },
        { 1.0, 0.0 },
        { 0.0, 1.0 },
        { 1.0, 1.0 }
    };

    public static double XOR_IDEAL[][] = {
        { 0.0 },
        { 1.0 },
        { 1.0 },
        { 0.0 }
    };

    public static void main(final String args[]) {
        // Create training set
        MLDataSet trainingSet = new BasicMLDataSet(XOR_INPUT, XOR_IDEAL);

        // Create NEAT population
        NEATPopulation pop = new NEATPopulation(2, 1, 1000);
        pop.setInitialConnectionDensity(1.0); // Fully connected
        pop.reset();

        // Create trainer
        final EvolutionaryAlgorithm train = NEATUtil.constructNEATTrainer(pop, new TrainingSetScore(trainingSet));

        // Train until error is less than 0.01
        int epoch = 1;
        while (train.getError() > 0.01) {
            train.iteration();
            System.out.println("Epoch #" + epoch + " Error:" + train.getError());
            epoch++;
        }

        // Obtain the best network
        NEATNetwork network = (NEATNetwork) train.getCODEC().decode(train.getBestGenome());

        // Test the neural network
        System.out.println("Neural Network Results:");
        EncogUtility.evaluate(network, trainingSet);

        // Shutdown Encog
        Encog.getInstance().shutdown();
    }
}
