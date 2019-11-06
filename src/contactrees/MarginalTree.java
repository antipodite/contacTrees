package contactrees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import contactrees.MarginalNode;
import contactrees.CFEventList;
import contactrees.CFEventList.Event;
import contactrees.Conversion;
import beast.core.Input;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;


/**
 * A calculation node extracting the marginal tree for a block from the ACG.
 * The resulting marginal trees are the basis for the likelihood computation 
 * of the corresponding block.  
 * 
 * @author Nico Neureiter <nico.neureiter@gmail.com>
 */

public class MarginalTree extends Tree {

	public Input<ConversionGraph> networkInput = new Input<>(
			"network",
			"The conversion graph (network) from which the marginal tree is sampled.",
			Input.Validate.REQUIRED);
	public Input<Block> blockInput = new Input<>(
			"block",
			"The block object containing the moves this marginal tree follows along the conversion graph.",
			Input.Validate.REQUIRED);

	ConversionGraph acg;
	Block block;
	boolean outdated;
	
	public void initAndValidate() {
		acg = networkInput.get();
		block = blockInput.get();
		outdated = true;
	}
	
	@Override
	protected boolean requiresRecalculation() {
		// Check whether recalculation is necessary
		// TODO actually check it!
		boolean outdated = true;
		
		// Recalculate right away if needed
		if (outdated) {
			recalculate();
			outdated = false;
			return true;
		} else 
			return false;
	}
	
	public void recalculate() {
        List<Event> cfEvents = acg.getCFEvents();
        Map<Node, MarginalNode> activeCFlineages = new HashMap<>();
        ArrayList<Conversion> convs = getBlockConversions();
        convs.sort((c1, c2) -> {
            if (c1.height < c2.height)
                return -1;

            if (c1.height > c2.height)
                return 1;

            return 0;
        });
        
        int iConv = 0;
        int nextNonLeafNr = acg.getLeafNodeCount();
        for (int iEvent = 0; iEvent < cfEvents.size(); iEvent++) {
            Event event = cfEvents.get(iEvent);
            Node node = event.getNode();
            
            // Process the current CF-event
            switch (event.getType()) {
                case SAMPLE:
                    // 
                    MarginalNode marginalLeaf = new MarginalNode();
                    marginalLeaf.setHeight(event.getHeight());
                    marginalLeaf.setID(node.getID());
                    marginalLeaf.setNr(node.getNr());
                    marginalLeaf.cfNodeNr = node.getNr();
                    activeCFlineages.put(node, marginalLeaf);
                    break;

                case COALESCENCE:
                    Node left = node.getLeft();
                    Node right = node.getRight();
                    
                    if (activeCFlineages.containsKey(left) && activeCFlineages.containsKey(right)) {

                        Node marginalLeft = activeCFlineages.get(left);
                        Node marginalRight = activeCFlineages.get(right);
                        
                        // Create a new marginal node at the coalescence event
                        MarginalNode marginalNode = new MarginalNode();
                        marginalNode.setNr(nextNonLeafNr++);
                        marginalNode.setHeight(event.getHeight());
                        marginalNode.addChild(marginalLeft);
                        marginalNode.addChild(marginalRight);
                        marginalNode.cfNodeNr = node.getNr();

                        // Remove the old and add the new marginal node to the active lineages.
                        activeCFlineages.remove(left);
                        activeCFlineages.remove(right);
                        activeCFlineages.put(node, marginalNode);

                    } else {
                        // Only one side is active -> no coalescence in marginal tree (i.e. no marginal node)
                            
                        if (activeCFlineages.containsKey(left)) {
                            MarginalNode marginalLeft = activeCFlineages.get(left);
                            activeCFlineages.remove(left);
                            activeCFlineages.put(node, marginalLeft);
                            break;
                        }

                        if (activeCFlineages.containsKey(right)) {
                            MarginalNode marginalRight = activeCFlineages.get(right);
                            activeCFlineages.remove(right);
                            activeCFlineages.put(node, marginalRight);
                            break;
                        }
                    }
                    break;
            }

            // Process all conversion below the next CF-event 
            while (iConv < convs.size() &&
                    (event.node.isRoot() || convs.get(iConv).height < cfEvents.get(iEvent + 1).getHeight())) {
                
                Conversion conv = convs.get(iConv++);
                Node node1 = conv.getNode1();
                Node node2 = conv.getNode2();
                
                if (activeCFlineages.containsKey(node1) && activeCFlineages.containsKey(node2)) {
                    // Both lineages at the conversion are active --> coalescence in the marginal tree
                    
                    MarginalNode left = activeCFlineages.get(node2);
                    MarginalNode right = activeCFlineages.get(node1);
                    
                    // Create a MarginalNode at the point of the conversion
                    MarginalNode convNode = new MarginalNode();
                    convNode.setNr(nextNonLeafNr++);
                    convNode.setHeight(conv.height);
                    convNode.addChild(left);
                    convNode.addChild(right);
    
                    // End active lineages of node1 and node2
                    activeCFlineages.remove(node1);
                    activeCFlineages.remove(node2);
                    
                    // Create new lineage above conversion
                    activeCFlineages.put(node2, convNode);
                    
                } else {
                    // node1 or node2 already moved away (overshadowed by another conversion)
                    
                    if (activeCFlineages.containsKey(node1)) {
                        // node1 passes conversion, but node2 branched away --> CF lineage of node2 is continued by node1
                        MarginalNode marginalNode1 = activeCFlineages.get(node1);
                        activeCFlineages.put(node2, marginalNode1);
                    }    
                    // else: node1 already branched away --> conversion has no effect                        
                }
            }
        }

        // A single active CF lineage should remain:
        setRoot(activeCFlineages.get(acg.getRoot()));
    }

    /**
     * 
     * @return
     */
    public ArrayList<Conversion> getBlockConversions() {
        ArrayList<Conversion> blockConvs = new ArrayList<>();
        ConversionList convList = acg.getConversions();
        
        for (int cID : block.getConversionIDs()) {
            blockConvs.add(convList.get(cID));
        }
        
        return blockConvs;
    }

    @Override
    public String toString() {
        return root.toString();
    }
	
	public void assignFromTree(Tree tree) {
		final Node[] nodes = new Node[tree.getNodeCount()];//tree.getNodesAsArray();
        for (int i = 0; i < tree.getNodeCount(); i++) {
            nodes[i] = newNode();
        }
//        setID(tree.getID());
        //index = tree.index;
        root = nodes[tree.getRoot().getNr()];
        root.assignFrom(nodes, tree.getRoot());
        root.setParent(null);
        nodeCount = tree.getNodeCount();
        internalNodeCount = tree.getInternalNodeCount();
        leafNodeCount = tree.getLeafNodeCount();
        initArrays();
        
	}	
	
}

