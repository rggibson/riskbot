/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import java.util.ArrayList;
import java.util.*;

/**
 *
 * @author Richard Gibson, Neesha Desai, Richard Zhao
 */
public class UCT_Drafter extends SmartDrafter
{
    /**
     * The maximum number of leaves we want to have on our MaxN portion of the
     * search
     */
    private final int MAX_NUM_LEAVES = 1;

    /**
     * The number of roll outs that we perform for each level of UCT
     */
    private final int MAX_ROLL_OUTS = 5000;
    private final int DS_LENGTH = 42;
    /*
     *  The constant value used in formula
     */
    private final double CONSTANT = 0.3;

    /**
     * A cap on how big the branching factor can be.  When there are more branches,
     * we use the passed in heuristic to reduce the branching down to our max.
     */
    private final int MAX_BRANCHING_FACTOR = 15;

    private Node tree = null;
    
    private int playerNum = 0;
    private int beforePlayer = 0;
    private int afterPlayer = 0;
    
    /**
     * Constructor
     */
    public UCT_Drafter()
    {

    }


    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
    	if (tree == null) {
    		tree = addNewNode(null, draftState, -1, -1);
    		 playerNum = (ID + 1) % board.getNumberOfPlayers();
    		 if (playerNum + 1 == board.getNumberOfPlayers()) {
    			 beforePlayer = playerNum-1;
    			 afterPlayer = 0;
    		 } else if (playerNum == 0) {
    			 beforePlayer = board.getNumberOfPlayers();
    			 afterPlayer = playerNum + 1;
    		 } else {
    			 beforePlayer = playerNum-1;
    			 afterPlayer = playerNum+1;
    		 }

     		System.out.println("Creating first tree node for Player " + playerNum);
    	} else {
    		// set tree to current parent
    		moveTreeToParent(draftState);
    	}
    	
    	
    	// search MAX_ROLL_OUTS times
    	for (int count = 0; count < MAX_ROLL_OUTS; count++) {
    		// cycle through children and use formula to make decision on which to select next
    		Node x = findNextMove(tree, playerNum);
    		if (count % 500 == 0)
    			System.out.println(playerNum + " is trying node " + x.getDraftNumber() + " rollout " + count);
    		int[] xds = x.getDraftState();
    		ArrayList unowned = new ArrayList();
    		for (int i = 0; i < draftState.length; i++) {
    			if (xds[i] == -1)
    				unowned.add(i);
    		}
    		double[] results = monteCarloRollOut(x.getDraftState(), unowned, playerNum, board.getNumberOfPlayers());
    		x.setValue(results[playerNum]);
    		propagateValue(x.getParent());
    	}
    	
    	// check all children and find best value - send as best pick

    	return (findBestMove(tree)).getDraftNumber();
    		
//        return bestPick;
    }
    
    /*
     * Find the newest parent node in tree
     */
    private Node newParent() {
    	return null;
    }
    
    /*
     * average all the values of the children nodes and propagate up until node has no parent
     */
    private void updateValue(Node n) {
    	
    	if (n.getParent() != null) {
    		updateValue(n.getParent());
    	}
    }

    
    
    private Node addNewNode(Node parent, int[] draftState, int draftNumber, int owner) {
    	Node n = new Node();
    	n.setParent(parent);
    	n.setDraftState(draftState);
    	n.setDraftNumber(draftNumber);
    	int countChild = 0;
    	for (int i = 0; i < draftState.length; i++) {
    		if (draftState[i] == -1) {
    			countChild++;
    		}
    	}
    	n.setNumChildren(countChild);
    	System.out.println("Node " + draftNumber + " has " + countChild + " children");
    	n.setNumVisits(1);
    	n.setValue(0);
    	n.setOwner(owner);
    	if (parent != null) 
    		parent.addChild(n);
    	return n;
    }
    
    /*
     * based on the two changes in draft state since last look (3-player game)
     * move tree forward 2 moves
     */
    private void moveTreeToParent(int[] draftState) {
    	int firstMove = 0;
    	int secondMove = 0;
    	int[] ds2 = tree.getDraftState();
    	for (int i = 0; i < draftState.length; i++) {
    		if (ds2[i] != draftState[i]) {
    			if (draftState[i] == beforePlayer) {
    				secondMove = i;
    			}
    			if (draftState[i] == afterPlayer) {
    				firstMove = i;
    			}
    		}
    	}
    	System.out.println("first move is " + firstMove + " and second move is " + secondMove);
    	Iterator<Node> iter = tree.getChildren().iterator();
    	while (iter.hasNext()) {
    		Node child = iter.next();
    		if (child.getDraftNumber() == firstMove) {
    			Iterator<Node> iter2 = child.getChildren().iterator();
    	    	while (iter.hasNext()) {
    	    		Node child2 = iter.next();
    	    		if (child2.getDraftNumber() == secondMove) {
    	    			child2.setParent(null);
    	    			tree = child2;
    	    		}
    	    		break;
    	    	}
    		}
    	}
    	
    }
    
    /*
     * Find next move by examining children
     */
    private Node findNextMove(Node parent, int player) {
    	System.out.println("finding next move for " + parent.getDraftNumber());
    	// first see if we've visited all children before - if not, return an unvisited child
    	if (parent.getChildren().size() == parent.getNumChildren()) {
    		System.out.println("All children have been visted at least once, use formula");
        	Iterator<Node> iter = parent.getChildren().iterator();
        	double bestValue = 0;
        	Node bestNode = null;
        	while (iter.hasNext()) {
        		Node child = iter.next();
        		double value = child.getValue() + CONSTANT * Math.sqrt(Math.log(parent.getNumVisits()) / child.getNumVisits());
//        		System.out.println(child.getDraftNumber() + " value is " + value);
        		if (value > bestValue) {
        			bestValue = value;
        			bestNode = child;
        		}
        		// Q(s,a) + c x sqrt(log(parentvisits)/childvisits)
        		
        	}
        	bestNode.incrementNumVisits();
        	int nextPlayer = 0;
        	if (player < 2) {
        		player++;
        	} else if (player == 2) {
        		player = 0;
        	}
        
        	return findNextMove(bestNode, nextPlayer);
    	} else {
    		// return unvisited child
    		
    		int[] draftState = new int[DS_LENGTH];
    		System.arraycopy(parent.getDraftState(), 0, draftState, 0, DS_LENGTH);
    		                          //=  Arrays.copyOf(parent.getDraftState(), parent.getDraftState().length);
    		Iterator<Node> iter = parent.getChildren().iterator();
    		while (iter.hasNext()) {
    			Node child = iter.next();
    			draftState[child.getDraftNumber()] = -3;
    		}
    		Set s = new TreeSet();
    		for (int i = 0; i < draftState.length; i++) {
    			if (draftState[i] == -1) {
    				s.add(i);
    			}
    		}
    		List l = new ArrayList(s);
    		Collections.shuffle(l);
    		int[] ds2 =  new int[DS_LENGTH];
    		System.arraycopy(parent.getDraftState(), 0, ds2, 0, DS_LENGTH);
    		
    		int draftNumber = (Integer) l.get(0);
    		ds2[draftNumber] = player;
    		return addNewNode(parent, ds2, draftNumber, player);
    		
    	}
    	
//    	return null;
    }
 
    private Node findBestMove(Node parent) {
    	System.out.println("finding next move for " + parent.getDraftNumber());
    	// first see if we've visited all children before - if not, return an unvisited child
    	
    		System.out.println("All children have been visted at least once, use formula");
        	Iterator<Node> iter = parent.getChildren().iterator();
        	double bestValue = 0;
        	Node bestNode = null;
        	while (iter.hasNext()) {
        		Node child = iter.next();
        		double value = child.getValue() + CONSTANT * Math.sqrt(Math.log(parent.getNumVisits()) / child.getNumVisits());
//        		System.out.println(child.getDraftNumber() + " value is " + value);
        		if (value > bestValue) {
        			bestValue = value;
        			bestNode = child;
        		}
        		// Q(s,a) + c x sqrt(log(parentvisits)/childvisits)
        		
        	}
        	bestNode.incrementNumVisits();
        	return bestNode;
    }
    private void propagateValue(Node node) {
    	if (node == null) {
    		return;
    	}
    	Iterator<Node> iter = node.getChildren().iterator();
    	int count = 0;
    	double total = 0;
    	while (iter.hasNext()) {
    		Node child = iter.next();
    		total = total + child.getValue();
    		count++;
    	}
    	double result = total / count;
    	node.setValue(result);
    	propagateValue(node.getParent());
    	
    }
}
