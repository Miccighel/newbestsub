package it.uniud.newbestsub.dataset;

import it.uniud.newbestsub.problem.BestSubsetSolution;
import it.uniud.newbestsub.utils.BestSubsetLogger;

import it.uniud.newbestsub.utils.Constants;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import org.uma.jmetal.runner.AbstractAlgorithmRunner;
import org.uma.jmetal.util.SolutionListUtils;
import org.uma.jmetal.util.fileoutput.SolutionListOutput;
import org.uma.jmetal.util.fileoutput.impl.DefaultFileOutputContext;

import java.io.FileNotFoundException;
import java.util.Comparator;
import java.util.List;

public class DatasetView extends AbstractAlgorithmRunner {
    
    public BestSubsetLogger logger;

    public DatasetView() {
        logger = BestSubsetLogger.getInstance();
    }

    public void print(ImmutablePair<List<BestSubsetSolution>,Long> runResult, String outputPath) {

        logger.log("VIEW - Starting to print the result");

        List<BestSubsetSolution> population = runResult.left;
        long computingTime = runResult.right;

        new SolutionListOutput(population)
                .setSeparator(",")
                .setVarFileOutputContext(new DefaultFileOutputContext(Constants.OUTPUT_PATH + outputPath + "-Var.csv"))
                .setFunFileOutputContext(new DefaultFileOutputContext(Constants.OUTPUT_PATH + outputPath + "-Fun.csv"))
                .print();

        logger.log("VIEW - Algorithm computing time: " + computingTime/1000 + " seconds.");
        logger.log("VIEW - Finished to print the result");

    }

    public void print(ImmutableTriple<List<int[]>, int[], double[]> runResult, String outputPath) {

        logger.log("VIEW - Starting to print the result");

        List<int[]> population = runResult.left;
        int[] cardinalities = runResult.middle;
        double[] correlations = runResult.right;

        logger.log("VIEW - Finished to print the result");

    }

}
