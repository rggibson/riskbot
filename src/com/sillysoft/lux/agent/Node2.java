package com.sillysoft.lux.agent;

import java.util.HashMap;
//import java.util.Hashtable;
import java.util.ArrayList;

public class Node2 {

        /**
         * The player whose decision it is at this node
         */
        private int owner;

        /**
         * The number of times we have visited this state in simulations
         */
	private int numVisits;

        /**
         * The children states of this node
         */
	private HashMap<Integer,Node2> children;

        /**
         * The value of reaching this state, to (owner - 1) mod numPlayers
         * (because it is that player which plays or doesn't play to reach this state)
         */
	private double value;

        /**
         * We need to back up arrays of values in the MaxN algorithm
         */
        private double[] maxNValue;

        /**
         * Same as value, but for all players
         */
        private double[] allValues;

        /**
         * The state this node represents
         */
	private int[] draftState;

        /**
         * Constructor
         * @param draftState The state of the draft corresponding to this node.
         */
        public Node2(int[] draftState, int owner) //, Hashtable<ArrayList<Integer>,Node2> transposeTable)
        {
            this.owner = owner;
            numVisits = 0;
            value = 0.0;
            maxNValue = new double[3]; // HACK
            allValues = new double[3]; // HACK
            this.draftState = new int[draftState.length];
            System.arraycopy(draftState, 0, this.draftState, 0, draftState.length);
            children = new HashMap<Integer,Node2>();

            // Create the index into the transpose table
            ArrayList<Integer> state = new ArrayList<Integer>();
            for (int i = 0; i < draftState.length; ++i)
            {
                state.add(draftState[i]);
            }

            // Create the list of children
            for (int i = 0; i < draftState.length; ++i)
            {
                if (draftState[i] == -1)
                {
                    // Check to see if this child has already been created
//                    state.set(i, owner);
//                    if (transposeTable.containsKey(state))
//                    {
//                        children.put(i, transposeTable.get(state));
//                    }
//                    else
//                    {
                        children.put(i, null);
//                    }
//                    state.set(i, -1);
                }
            }

            // Add this state to the transposition table
//            assert !transposeTable.containsKey(state) : "Error: Not allowed to create duplicate Node of same state";
//            transposeTable.put(state, this);
        }

	public void addChild(int i, Node2 n) {
            assert children.containsKey(i) : "Error: Not a valid child of this node.";
            assert children.get(i) == null : "Error: Not allowed to replace existing child."; 
		children.put(i, n);
	}
	public int getNumChildren() {
		return children.size();
	}
	public int[] getDraftState() {
		return draftState;
	}
	public void setDraftState(int[] draftState) {
		this.draftState = draftState;
	}
	public int getNumVisits() {
		return numVisits;
	}
	public void incrementNumVisits() {
		this.numVisits++;
	}
	public void setNumVisits(int numVisits) {
		this.numVisits = numVisits;
	}
	public HashMap<Integer,Node2> getChildren() {
		return children;
	}
//	public void setChildren(HashMap children) {
//		this.children = children;
//	}
	public double getValue() {
		return value;
	}
	public void setValue(double value) {
		this.value = value;
	}
	public double[] getMaxNValue() {
		return maxNValue;
	}
	public void setMaxNValue(double[] value) {
            for (int i = 0; i < maxNValue.length; ++i)
            {
                maxNValue[i] = value[i];
            }
	}
	public double[] getAllValues() {
		return allValues;
	}
	public void seAllValues(double[] value) {
            for (int i = 0; i < allValues.length; ++i)
            {
                allValues[i] = value[i];
            }
	}
	public int getOwner()
        {
            return owner;
        }
	
}
