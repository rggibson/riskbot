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
     * The number of roll outs that we perform for each level of UCT
     */
    private final int MAX_ROLL_OUTS = 3000;
    private final int DS_LENGTH = 42;
    /*
     *  The constant exploration value used in formula
     */
    private final double CONSTANT = 0.05;


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
    		int treeOwner = 0;
    		playerNum = ID; // (ID + 1) % board.getNumberOfPlayers();
    		if (playerNum == 2) {
    			 beforePlayer = 1;
    			 afterPlayer = 0;
    			 treeOwner = 1;
    		} else if (playerNum == 0) {
    			 beforePlayer = 2;
    			 afterPlayer = 1;
    			 treeOwner = -1;
    		} else {
    			 beforePlayer = 0;
    			 afterPlayer = 2;
    			 treeOwner = 0;
    		}
   		
    		int val = -1;
    		if (treeOwner >= 0) {
    			for (int i = 0; i < draftState.length; i++) {
//    				if (draftState[i] != -1) 
//    					System.out.println("found assignment in tree of country " + i + " to player " + draftState[i]);
    				if (draftState[i] == treeOwner) 
    					val = i;
    				
    			}
    		}
//    		System.out.println("First node is " + val + " owner " + treeOwner);
    		tree = addNewNode(null, draftState, val, treeOwner);
     		
     		System.out.println("Creating first tree node for Player " + playerNum);
    	} else {
    		// set tree to current parent
    		moveTreeToParent(draftState);
    	}
    	
//    	int[] pCount = new int[3];
////		System.out.println("Current draft state, with owner " + owner + " choosing " + draftNumber);
//		for (int i = 0; i < draftState.length; i++) {
//			if (draftState[i] == 0) {
//				pCount[0]++;
//			}
//			if (draftState[i] == 1) {
//				pCount[1]++;
//			}
//			if (draftState[i] == 2) {
//				pCount[2]++;
//			}
//			
//		}
//		System.out.println("0 = " + pCount[0]);
//		System.out.println("1 = " + pCount[1]);
//		System.out.println("2 = " + pCount[2]);
    	
    	// search MAX_ROLL_OUTS times
    	for (int count = 0; count < MAX_ROLL_OUTS; count++) {
    		// cycle through children and use formula to make decision on which to select next
    		Node x = findNextMove(tree);
    		//if (count % 500 == 0)
    			//System.out.println(playerNum + " is trying node " + x.getDraftNumber() + " rollout " + count);
    		int[] xds = x.getDraftState();
    		ArrayList<Integer> unowned = new ArrayList();
    		for (int i = 0; i < draftState.length; i++) {
    			if (xds[i] == -1)
    				unowned.add(i);
    		}
    		int nextPlayer = 0;
    		if (x.getOwner() == 0) {
    			nextPlayer = 1;
    		} else if (x.getOwner() == 1) {
    			nextPlayer = 2;
    		} else {
    			nextPlayer = 0;
    		}
    		
    		double[] results = monteCarloRollOut(x.getDraftState(), unowned, nextPlayer);
    		
    		x.setValue(results);
    		propagateValue(x.getParent(), results);
    	}
    	
    	Node pick = findBestMove(tree);
//    	System.out.println("Our pick is " + pick.getOwner() + " draft " + pick.getDraftNumber());
    	if (unownedCountries.size() <= 3) {
    		// this is the last draft pick for this player, 
    		// so reset Tree to null 
    		// and call System.gc and hope for garbage collection
    		System.out.println("Last draft pick, reset tree");
    		tree = null;
    		System.gc();
    	}
    	
    	// check all children and find best value - send as best pick
    	return pick.getDraftNumber();
    		
//        return bestPick;
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
    	//System.out.println("Node " + draftNumber + " has " + countChild + " children");
    	n.setNumVisits(1);
    	double[] values = {0,0,0};
    	n.setValue(values);
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

//    	System.out.println("MOVE tree owner is now " + tree.getOwner() + " and draftNumber is " + tree.getDraftNumber());
    	int firstMove = 0;
    	int secondMove = 0;
    	int thirdMove = 0;
    	int[] ds2 = tree.getDraftState();
    	for (int i = 0; i < draftState.length; i++) {
    		if (ds2[i] != draftState[i]) {
//    			System.out.println("found difference in draftState");
    			if (draftState[i] == beforePlayer) {
    				thirdMove = i;
//    				System.out.println("happened to before player " + i);
    			}
    			if (draftState[i] == afterPlayer) {
    				secondMove = i;
//    				System.out.println("happened to after player " + i);
    			}
    			if (draftState[i] == playerNum) {
    				firstMove = i;
//    				System.out.println("happened to player " + i);
    			}
    		}
    	}
    	//System.out.println("first move is " + firstMove + " and second move is " + secondMove);
    	Iterator<Node> iter = tree.getChildren().iterator();
    	search:
	    	while (iter.hasNext()) {
	    		Node child = iter.next();
//	    		System.out.println("child draft " + child.getDraftNumber());
	    		if (child.getDraftNumber() == firstMove) {
//	    			System.out.println("FOUND FIRST MOVE CHILD");
	    			Iterator<Node> iter2 = child.getChildren().iterator();
	    	    	while (iter2.hasNext()) {
	    	    		Node child2 = iter2.next();

//	    	    		System.out.println("child2 draft " + child2.getDraftNumber());
	    	    		if (child2.getDraftNumber() == secondMove) {
//	    	    			System.out.println("FOUND SECOND MOVE CHILD");
	    	    			Iterator<Node> iter3 = child2.getChildren().iterator();
	    	    			while (iter3.hasNext()) {
	    	    				Node child3 = iter3.next();

//	    	    	    		System.out.println("child3 draft " + child3.getDraftNumber());
	    	    				if (child3.getDraftNumber() == thirdMove) {
	    	    	    			child3.setParent(null);
//	    	    	    			System.out.println("MOVING TREE");
	    	    	    			tree = child3;
	    	    	    		
	    	    	    			break search;
	    	    				}
	    	    			}
	    	    			// did not find third child - node may not have been created yet
	    	    			// add child node
	    	    			int[] newDraftState = new int[DS_LENGTH];
	    	        		System.arraycopy(child2.getDraftState(), 0, newDraftState, 0, DS_LENGTH);

	    	        		for (int i = 0; i < newDraftState.length; i++) {
	    	        			if (newDraftState[i] != draftState[i] && i != thirdMove) {
	    	        				System.out.println("OH NO - Does not match");
	    	        			}
	    	        		}
	    	        		
//	    	        		int draftNumber = (Integer) l.get(0);
	    	        		newDraftState[thirdMove] = beforePlayer;
//	    	        		System.out.println("parent is " + parent.getOwner() + " new child is " + player);
//	    	        		System.out.println("makeNew " + nextPlayer + " " + draftNumber);
//	    	        		System.out.println("adding new node to search tree for third move");
	    	        		tree =  addNewNode(child2, newDraftState, thirdMove, beforePlayer);
	    	        		tree.setParent(null);
	    	        		break search;
	    	    		}
	    	    	}
	    		}
	    	}

//    	System.out.println("MOVED tree owner is now " + tree.getOwner() + " and draftNumber is " + tree.getDraftNumber());
    	
    }
    
    /*
     * Find next move by examining children
     */
    private Node findNextMove(Node parent) {
    	//System.out.println("finding next move for " + parent.getDraftNumber());
    	// first see if we've visited all children before - if not, return an unvisited child
//    	System.out.println("findNextMove " + parent.getOwner() + " " + parent.getDraftNumber());
    	int nextPlayer = 0;
    	if (parent.getOwner() == 0) {
    		nextPlayer = 1;
    	} else if (parent.getOwner() == 1) {
    		nextPlayer = 2;
    	} else {
    		nextPlayer = 0;
    	}
    	
    	if (parent.getNumChildren() == 0) {
			return parent;
		}
    	
    	if (parent.getChildren().size() == parent.getNumChildren()) {
    		//System.out.println("All children have been visted at least once, use formula");
        	Iterator<Node> iter = parent.getChildren().iterator();
        	double bestValue = 0;
        	Node bestNode = null;
        	while (iter.hasNext()) {
        		Node child = iter.next();
        		double value = (child.getValue())[child.getOwner()] + CONSTANT * Math.sqrt(Math.log(parent.getNumVisits()) / child.getNumVisits());
//        		System.out.println(child.getDraftNumber() + " value is " + value);
        		if (value > bestValue) {
        			bestValue = value;
        			bestNode = child;
        		}
        		// Q(s,a) + c x sqrt(log(parentvisits)/childvisits)
        		
        	}
        	bestNode.incrementNumVisits();
        	
        	
//        	if (bestNode.getOwner() == nextPlayer) {
//        		System.out.println("find next move best node is being passed with own owner value");
//        	} else if (parent.getOwner() == nextPlayer) {
//        		System.out.println("find next move best node is being passed with parent owner");
//        	} else {
//        		System.out.println("next player equals nothing");
//        	}
//        	System.out.println("Best node owner " + bestNode.getOwner() + " and next player " + nextPlayer);
//        	if (player < 2) {
//        		player++;
//        	} else if (player == 2) {
//        		player = 0;
//        	}
//        	System.out.println("parent " + parent.getOwner() + " and child " + nextPlayer);
        	return findNextMove(bestNode);
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
    		ds2[draftNumber] = nextPlayer;
//    		System.out.println("parent is " + parent.getOwner() + " new child is " + player);
//    		System.out.println("makeNew " + nextPlayer + " " + draftNumber);
    		return addNewNode(parent, ds2, draftNumber, nextPlayer);
    		
    	}
    	
//    	return null;
    }
 
    private Node findBestMove(Node parent) {
    	//System.out.println("finding next move for " + parent.getDraftNumber());
    	// first see if we've visited all children before - if not, return an unvisited child
    	
    		//System.out.println("All children have been visited at least once, use formula");
        	Iterator<Node> iter = parent.getChildren().iterator();
        	double bestValue = 0;
        	Node bestNode = null;
        	while (iter.hasNext()) {
        		Node child = iter.next();
        		double value = (child.getValue())[child.getOwner()]; // + CONSTANT * Math.sqrt(Math.log(parent.getNumVisits()) / child.getNumVisits());
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
    
    
    private void propagateValue(Node node, double[] values) {
    	if (node == null) {
    		return;
    	}
    	Iterator<Node> iter = node.getChildren().iterator();
    	int count = 0;
    	double[] total = {0,0,0};
    	while (iter.hasNext()) {
    		Node child = iter.next();
    		total[0] = total[0] + child.getValue()[0];
    		total[1] = total[1] + child.getValue()[1];
    		total[2] = total[2] + child.getValue()[2];
    		count++;
    	}
    	double[] result = node.getValue();
    	result[0] = result[0] + ( (1 / node.getNumVisits()) * (values[0] - result[0]) );
    	result[1] = result[1] + ( (1 / node.getNumVisits()) * (values[1] - result[1]) );
    	result[2] = result[2] + ( (1 / node.getNumVisits()) * (values[2] - result[2]) );
    	node.setValue(result);
    	propagateValue(node.getParent(), result);
    	
    }
    
    private void propagateValueOld(Node node) {
    	if (node == null) {
    		return;
    	}
    	Iterator<Node> iter = node.getChildren().iterator();
    	int count = 0;
    	double[] total = {0,0,0};
    	while (iter.hasNext()) {
    		Node child = iter.next();
    		total[0] = total[0] + child.getValue()[0];
    		total[1] = total[1] + child.getValue()[1];
    		total[2] = total[2] + child.getValue()[2];
    		count++;
    	}
    	double[] result = new double[3];
    	result[0] = total[0] / count;
    	result[1] = total[1] / count;
    	result[2] = total[2] / count;
    	node.setValue(result);
    	propagateValueOld(node.getParent());
    	
    }
    
    /**
     * From the passed in draft state, randomly picks countries for each player
     * until all countries are owned.  The value of the final state is then
     * calculated.
     * @param draftState The current assignment of countries to players, or unowned
     * @param player The player whose pick it currently is
     * @param unownedCountries The countries available to be picked by the players
     * @return The values of the final state reached via the roll outs.
     */
    public double[] monteCarloRollOut(int[] draftState, ArrayList<Integer> unownedCountries, int player)
    {
        // If this is a terminal node, then evaluate
        if (unownedCountries.size() == 0)
        {
            return evaluationFunction(draftState);
        }

        // Otherwise, pick a country at random and evaluate
        int randomCountryIndex = (int) (Math.random()*unownedCountries.size());
        int randomCountry = unownedCountries.get(randomCountryIndex);
        unownedCountries.remove(randomCountryIndex);
        assert(draftState[randomCountry] == -1);
        draftState[randomCountry] = player; // Pick country

        double[] values = monteCarloRollOut(draftState, unownedCountries, (player + 1) % board.getNumberOfPlayers());

        draftState[randomCountry] = -1; // Undo the pick
        unownedCountries.add(randomCountry);

        return values;
    }
}
