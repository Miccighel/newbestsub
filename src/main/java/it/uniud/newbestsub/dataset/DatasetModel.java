package it.uniud.newbestsub.dataset;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.*;

import it.uniud.newbestsub.utils.BestSubsetLogger;
import it.uniud.newbestsub.utils.Formula;
import it.uniud.newbestsub.problem.*;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import org.uma.jmetal.problem.BinaryProblem;
import org.uma.jmetal.problem.Problem;

import org.uma.jmetal.solution.BinarySolution;

import org.uma.jmetal.operator.Operator;
import org.uma.jmetal.operator.CrossoverOperator;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.operator.SelectionOperator;
import org.uma.jmetal.operator.impl.selection.BinaryTournamentSelection;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;

import org.uma.jmetal.util.AlgorithmRunner;
import org.uma.jmetal.util.JMetalLogger;
import org.uma.jmetal.util.binarySet.BinarySet;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;

public class DatasetModel {

    public String[] systemLabels;
    public String[] topicLabels;
    public int numberOfSystems;
    public int systemSize;
    public int numberOfTopics;
    public int topicSize;
    public Map<String, double[]> averagePrecisionsPerSystem = new LinkedHashMap<String, double[]>();
    public Map<String, double[]> averagePrecisionsPerTopic = new LinkedHashMap<String, double[]>();
    public double[] meanAveragePrecisions;
    public Map<String, Map<Double, Integer>> topicsSetsDistribution = new LinkedHashMap<String, Map<Double, Integer>>();

    private Problem problem;
    private Algorithm<List<BestSubsetSolution>> algorithm;
    private CrossoverOperator<BestSubsetSolution> crossover;
    private MutationOperator<BestSubsetSolution> mutation;
    private SelectionOperator<List<BestSubsetSolution>, BestSubsetSolution> selection;

    private BestSubsetLogger logger;

    public DatasetModel() {
        logger = BestSubsetLogger.getInstance();
    }

    public DatasetModel retrieveModel() {
        return this;
    }

    public void loadData(String datasetPath) throws FileNotFoundException, IOException {

        // The parsing phase of the original .csv dataset file starts there.

        CSVReader reader = new CSVReader(new FileReader(datasetPath));
        topicLabels = ((String[]) reader.readNext());

        numberOfTopics = topicLabels.length - 1;

        String[] nextLine;
        double[] averagePrecisions = new double[0];
        while ((nextLine = reader.readNext()) != null) {
            String systemLabel = nextLine[0];
            averagePrecisions = new double[nextLine.length - 1];
            for (int i = 1; i < nextLine.length; i++) {
                averagePrecisions[i - 1] = Double.parseDouble(nextLine[i]);
            }
            this.averagePrecisionsPerSystem.put(systemLabel, averagePrecisions);
        }

        systemLabels = new String[averagePrecisionsPerSystem.entrySet().size()];
        numberOfSystems = averagePrecisionsPerSystem.entrySet().size();
        systemSize = averagePrecisions.length;

        /* averagePrecisionsPerSystem is a <String,double[]> dictionary where, for each entry, the key is the system label
        and the value is an array that contains the AP values of a single system, for each topic. */

        double[][] averagePrecisionsPerSystemAsMatrix = new double[averagePrecisionsPerSystem.entrySet().size()][averagePrecisionsPerSystem.entrySet().iterator().next().getValue().length];

        Iterator iterator = averagePrecisionsPerSystem.entrySet().iterator();
        int counter = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, double[]> singleSystem = (Map.Entry<String, double[]>) iterator.next();
            averagePrecisionsPerSystemAsMatrix[counter] = singleSystem.getValue();
            systemLabels[counter] = singleSystem.getKey();
            counter++;
        }

        /* In the loading phase there is an extensive use of the Map data structure. This has been done to do not lose
        the system and topic labels, which maybe will be useful in the future. */

        String[] labels = new String[topicLabels.length - 1];
        for (int i = 1; i < topicLabels.length; i++) {
            labels[i - 1] = topicLabels[i];
        }
        topicLabels = labels;

        /* The first label is stripped from the topic labels array because it's a fake label. */

        meanAveragePrecisions = new double[averagePrecisionsPerSystem.entrySet().size()];

        boolean[] useColumns = new boolean[numberOfTopics];
        Arrays.fill(useColumns, Boolean.TRUE);

        iterator = averagePrecisionsPerSystem.entrySet().iterator();
        counter = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, double[]> singleSystem = (Map.Entry<String, double[]>) iterator.next();
            meanAveragePrecisions[counter] = Formula.getMean(singleSystem.getValue(), useColumns);
            this.systemSize = (this.systemSize == 0) ? singleSystem.getValue().length : this.systemSize;
            counter++;
        }

    }

    private CorrelationStrategy<double[], double[], Double> getCorrelationStrategy(String chosenCorrelationMethod) {

        CorrelationStrategy<double[], double[], Double> correlationMethod;

        switch (chosenCorrelationMethod) {
            case "Pearson":
                correlationMethod = (firstArray, secondArray) -> {
                    PearsonsCorrelation pcorr = new PearsonsCorrelation();
                    return pcorr.correlation(firstArray, secondArray);
                };
                break;
            case "Kendall":
                correlationMethod = (firstArray, secondArray) -> {
                    KendallsCorrelation pcorr = new KendallsCorrelation();
                    return pcorr.correlation(firstArray, secondArray);
                };
                break;
            default:
                correlationMethod = (firstArray, secondArray) -> {
                    PearsonsCorrelation pcorr = new PearsonsCorrelation();
                    return pcorr.correlation(firstArray, secondArray);

                };
                break;
        }

        return correlationMethod;
    }

    public ImmutablePair<List<BestSubsetSolution>, Long> solve(String chosenCorrelationMethod, String targetToAchieve, int numberOfIterations) {

        CorrelationStrategy<double[], double[], Double> correlationStrategy = getCorrelationStrategy(chosenCorrelationMethod);

        TargetStrategy<BestSubsetSolution, Double> targetStrategy;

        switch (targetToAchieve) {
            case "Best":
                targetStrategy = (solution, correlation) -> {
                    solution.setObjective(0, correlation * -1);
                    solution.setObjective(1, solution.getNumberOfSelectedTopics());
                };
                break;
            case "Worst":
                targetStrategy = (solution, correlation) -> {
                    solution.setObjective(0, correlation);
                    solution.setObjective(1, solution.getNumberOfSelectedTopics() * -1);
                };
                break;
            default:
                targetStrategy = (solution, correlation) -> {
                    solution.setObjective(0, correlation * -1);
                    solution.setObjective(1, solution.getNumberOfSelectedTopics());
                };
                break;
        }

        problem = new BestSubsetProblem(numberOfTopics, averagePrecisionsPerSystem, meanAveragePrecisions, correlationStrategy, targetStrategy);
        crossover = new BinaryPruningCrossover(0.9);
        mutation = new BitFlipMutation(1);
        selection = new BinaryTournamentSelection<BestSubsetSolution>(new RankingAndCrowdingDistanceComparator<BestSubsetSolution>());
        algorithm = new NSGAIIBuilder<BestSubsetSolution>(problem, crossover, mutation)
                .setSelectionOperator(selection)
                .setMaxEvaluations(numberOfIterations)
                .setPopulationSize(averagePrecisionsPerSystem.size())
                .build();

        AlgorithmRunner algorithmRunner = new AlgorithmRunner.Executor(algorithm).execute();
        long computingTime = algorithmRunner.getComputingTime();
        List<BestSubsetSolution> population = algorithm.getResult();

        switch (targetToAchieve) {
            case "Best":
                for (int i = 0; i < population.size(); i++) {
                    BestSubsetSolution solutionToFix = population.get(i);
                    double correlationToFix = solutionToFix.getObjective(0) * -1;
                    solutionToFix.setObjective(0, correlationToFix);
                    population.set(i, solutionToFix);
                }
                break;
            case "Worst":
                for (int i = 0; i < population.size(); i++) {
                    BestSubsetSolution solutionToFix = population.get(i);
                    double cardinalityToFix = solutionToFix.getObjective(1) * -1;
                    solutionToFix.setObjective(1, cardinalityToFix);
                    population.set(i, solutionToFix);
                }
                break;
        }

        for (int i = 0; i < topicLabels.length; i++) {
            Map<Double, Integer> distributionPerCardinalities = new TreeMap<Double, Integer>();
            topicsSetsDistribution.put(topicLabels[i], distributionPerCardinalities);
        }

        for (int i = 0; i < population.size(); i++) {
            BestSubsetSolution solutionToAnalyze = population.get(i);
            boolean[] topicStatus = solutionToAnalyze.getTopicStatus();
            double cardinality = solutionToAnalyze.getObjective(1);
            for (int j = 0; j < topicStatus.length; j++) {
                Map<Double, Integer> distributionsPerCardinalities = topicsSetsDistribution.get(topicLabels[j]);
                int distributionPerCardinality;
                try {
                    distributionPerCardinality = distributionsPerCardinalities.get(cardinality);
                } catch (NullPointerException e) {
                    distributionPerCardinality = 0;
                }
                int newValue = distributionPerCardinality + 1;
                distributionsPerCardinalities.put(cardinality, newValue);
                topicsSetsDistribution.put(topicLabels[j], distributionsPerCardinalities);
            }
        }

        return new ImmutablePair<List<BestSubsetSolution>, Long>(population, computingTime);

    }

    public ImmutableTriple<List<int[]>, int[], double[]> solve(String chosenCorrelationMethod) {

        CorrelationStrategy<double[], double[], Double> correlationStrategy = getCorrelationStrategy(chosenCorrelationMethod);

        List<int[]> variableValues = new LinkedList<int[]>();
        int[] cardinalities = new int[numberOfTopics];
        double[] correlations = new double[numberOfTopics];

        Random generator = new Random();

        for (int currentCardinality = 0; currentCardinality < numberOfTopics; currentCardinality++) {

            Set<Integer> topicToChoose = new HashSet<Integer>();
            while (topicToChoose.size() < currentCardinality + 1) {
                Integer next = generator.nextInt(numberOfTopics) + 1;
                topicToChoose.add(next);
            }

            int[] topicStatus = new int[numberOfTopics];

            Iterator iterator = topicToChoose.iterator();
            while (iterator.hasNext()) {
                int chosenTopic = (int) iterator.next();
                topicStatus[chosenTopic - 1] = 1;
            }

            String toString = "";
            for (int j = 0; j < topicStatus.length; j++) {
                toString += topicStatus[j];
            }

            logger.log("PROBLEM - Evaluating gene: " + toString);
            logger.log("PROBLEM - Number of selected topics: " + currentCardinality);

            double[] meanAveragePrecisionsReduced = new double[averagePrecisionsPerSystem.entrySet().size()];

            iterator = averagePrecisionsPerSystem.entrySet().iterator();
            int counter = 0;
            while (iterator.hasNext()) {
                Map.Entry<String, double[]> singleSystem = (Map.Entry<String, double[]>) iterator.next();
                meanAveragePrecisionsReduced[counter] = Formula.getMean(singleSystem.getValue(), topicStatus);
                counter++;
            }

            double correlation = correlationStrategy.computeCorrelation(meanAveragePrecisionsReduced, meanAveragePrecisions);

            logger.log("PROBLEM - Correlation: " + correlation);

            cardinalities[currentCardinality] = currentCardinality + 1;
            correlations[currentCardinality] = correlation;
            variableValues.add(topicStatus);

        }

        return new ImmutableTriple<List<int[]>, int[], double[]>(variableValues, cardinalities, correlations);

    }

}