package contactrees.test;

import contactrees.ACGWithMetaDataLogger;
import contactrees.Block;
import contactrees.BlockSet;
import contactrees.CFEventList;
import contactrees.Conversion;
import contactrees.ConversionGraph;
import contactrees.CFEventList.Event;
import contactrees.operators.ACGOperator;
import contactrees.util.Util;
import beast.core.BEASTInterface;
import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Distribution;
import beast.core.Input;
import beast.core.Operator;
import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.evolution.tree.coalescent.PopulationFunction;
import beast.math.Binomial;
import beast.util.Randomizer;
import feast.nexus.NexusBlock;
import feast.nexus.NexusBuilder;
import feast.nexus.TaxaBlock;
import feast.nexus.TreesBlock;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.distribution.BinomialDistribution;

/**
 * @author Nico Neureiter
 */
@Description("Simulates an ARG under the full ClonalOrigin model - can be used"
    + " for chain initialization or for sampler validation.")
public class SimulatorOperator extends Operator {

    final public Input<Distribution> distributionInput = new Input<>(
			"distribution",
			"A distribution which we can directly sample from (simulator).",
			Input.Validate.REQUIRED);

    final public Input<State> stateInput =
            new Input<>("state", "elements of the state space");
    
    Random random;
    @Override
    public void initAndValidate() {
        random = new Random(Randomizer.getSeed()); 
    }
    
    @Override
	public double proposal() {
    	Distribution distribution = distributionInput.get();
    	State state = stateInput.get();
    	
    	clearSampledFlags(distribution);
    	distribution.sample(state, random);
		return Double.POSITIVE_INFINITY;
	}

    public void clearSampledFlags(BEASTInterface obj) {
        if (obj instanceof Distribution)
            ((Distribution) obj).sampledFlag = false;

        for (String inputName : obj.getInputs().keySet()) {
            Input input = obj.getInput(inputName);

            if (input.get() == null)
                continue;

            if (input.get() instanceof List) {
                for (Object el : ((List)input.get())) {
                    if (el instanceof BEASTInterface)
                        clearSampledFlags((BEASTInterface)el);
                }
            } else if (input.get() instanceof BEASTInterface) {
                clearSampledFlags((BEASTInterface)(input.get()));
            }
        }
    }
    
}
