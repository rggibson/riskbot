/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import java.util.ArrayList;
//import java.util.Hashtable;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;

/**
 *
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class UCT_Drafter2 extends SmartDrafter
{
    /**
     * The root of the UCT tree
     */
    private Node2 m_root;

    /*
     *  The constant exploration value used in formula
     */
    private final double CONSTANT = 0.25;

    /**
     * Whether or not to use the selfish evaluation function
     */
    private final boolean SELFISH = false;

    /**
     * The number of simulations to run.
     * Set to -1 to abide by the time limit (located in SmartDrafter).
     */
    private final int NUM_SIMULATIONS = 5000;

    /**
     * The evaluation function to use
     */
    protected EvaluationFunction m_evalFunc = EvaluationFunction.LIN_REG_MORE_FEATS;

    /**
     * The transposition table
     */
    //private Hashtable<ArrayList<Integer>,Node2> transposeTable = new Hashtable<ArrayList<Integer>,Node2>();

    public UCT_Drafter2()
    {
        super();
    }

    @Override
    public String name()
    {
        if (SELFISH)
        {
            return "UCT_Drafter2-Selfish";
        }
        return "UCT_Drafter2";
    }

    public Node2 getRoot()
    {
        return m_root;
    }

    /**
     * Decide which country to pick
     * @param draftState Who owns what
     * @param unownedCountries The territories left to be picked
     * @return The index of the territory to pick
     */
    public int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        long alarm = System.currentTimeMillis() + PICK_TIME_IN_MILLIS_PER_UNOWNED_TERR*unownedCountries.size();
        
        updateRoot(draftState);

        if (NUM_SIMULATIONS == -1)
        {
            while (System.currentTimeMillis() < alarm)
            {
                runUctSimulation(m_root, draftState);
            }
        }
        else
        {
           for (int i = 0; i < NUM_SIMULATIONS; ++i)
           {
               runUctSimulation(m_root, draftState);
           }
        }

        int[] bestPick = getNBestPicks(m_root, 1);

        return bestPick[0];
    }

    /**
     * Ranks the top n picks, so that return[0] is the best pick, and so on.
     * @param node The node where we want to make the decision
     * @param n The number of ranked picks to return
     * @return The best picks, from best, to nth best.
     */
    public int[] getNBestPicks(Node2 node, int n)
    {
        assert node != null : "Error: Can't determine what picks to make from a non-visited state!";

        int[] topPicks = new int[n];
        double[] valuesOfTopPicks = new double[n];
        for (int i = 0; i < n; ++i)
        {
            // Initialize
            topPicks[i] = -1;
            valuesOfTopPicks[i] = -Double.MAX_VALUE;
        }

        Set<Integer> possiblePicks = node.getChildren().keySet();

        for (Integer terr : possiblePicks)
        {
            // Get the value of picking this terr
            double value = node.getChildren().get(terr).getValue();

            // Find its rank in the current top picks
            int rank = n;
            while (rank > 0 && valuesOfTopPicks[rank - 1] < value)
            {
                rank--;
            }

            // Adjust the top picks to make room for the new territory
            for (int j = n - 2; j >= rank; --j)
            {
                topPicks[j+1] = topPicks[j];
                valuesOfTopPicks[j+1] = valuesOfTopPicks[j];
            }

            // Put the territory into place
            if (rank < n)
            {
                topPicks[rank] = terr;
                valuesOfTopPicks[rank] = value;
            }
        }

        return topPicks;
    }

    /**
     * Runs a simulation from the root down to a terminal state, following UCT
     * decisions along the way.
     * @param node The current position in the tree that we are running from
     * @param draftState The current state of the draft
     */
    public void runUctSimulation(Node2 node, int[] draftState)
    {
        uctSimulation_r(node, draftState, true);
    }

    /**
     * The recursive portion of the UCT simulation
     * @param node The current node we are at in the simulation
     * @param draftState The current state of the draft (should match node.getDraftState() if node != null)
     * @param addToTreee True if we still want to add a node to the UCT tree
     * @return The evaluation of the final state reached in the simulation
     */
    private double[] uctSimulation_r(Node2 node, int[] draftState, boolean addToTree)
    {
        if (node == null)
        {
            // Time to run randomly to the end
            int[] numPicksByPlayer = new int[board.getNumberOfPlayers()];
            ArrayList<Integer> unownedCountries = new ArrayList<Integer>();
            for (int i = 0; i < draftState.length; ++i)
            {
                if (draftState[i] == -1)
                {
                    unownedCountries.add(i);
                }
                else
                {
                    numPicksByPlayer[draftState[i]]++;
                }
            }
            
            // Whose turn is it to pick next?
            int nextPlayer = (countries.length - unownedCountries.size()) % board.getNumberOfPlayers();

            return monteCarloRollOut(draftState, unownedCountries, nextPlayer);
        }

        // A visit to this node
        node.incrementNumVisits();

        // Find the list of children to choose from
        ArrayList<Integer> picks = new ArrayList<Integer>();
        double valueOfPicks = -Double.MAX_VALUE;
        HashMap<Integer,Node2> children = node.getChildren();
        Set<Integer> childrenKeys = children.keySet();
        Iterator<Integer> iter = childrenKeys.iterator();
        while (iter.hasNext())
        {
            Integer childKey = iter.next();
            Node2 child = children.get(childKey);
            double value = Double.MAX_VALUE;
            if (child != null)
            {
                assert child.getNumVisits() > 0 : "Error: No visits to child node!";
                value = child.getValue() + CONSTANT * Math.sqrt(Math.log(node.getNumVisits()) / child.getNumVisits());
            }
            if (value > valueOfPicks)
            {
                picks.clear();
                picks.add(childKey);
                valueOfPicks = value;
            }
            else if (value == valueOfPicks)
            {
                picks.add(childKey);
            }
        }

        if (picks.isEmpty())
        {
            // Must be a terminal node, as there are no children
            if (SELFISH)
            {
                return evaluationFunctionSelfish(node.getDraftState(), m_evalFunc);
            }
            return evaluationFunction(node.getDraftState(), m_evalFunc);
        }

        // Choose a child key at random
        Integer childKey = picks.get((int)(Math.random()*picks.size()));
        Node2 child = children.get(childKey);

        // Adjust the draft state
        if (draftState[childKey] != -1)
        {
            assert draftState[childKey] == -1 : "Error: Can't pick an already drafted territory!";
        }
        draftState[childKey] = node.getOwner();

        if (child == null && addToTree)
        {
            // Need to add this node to the tree
            // First make sure that the child does not already exist
//            ArrayList<Integer> state = new ArrayList<Integer>();
//            for (int i = 0; i < draftState.length; ++i)
//            {
//                state.add(draftState[i]);
//            }
//            if (transposeTable.containsKey(state))
//            {
//                child = transposeTable.get(state);
//            }
//            else
//            {
                if (node == null)
                {
                    assert false : "Error: Null node!";
                }
                int nextOwner = (node.getOwner() + 1) % board.getNumberOfPlayers();
                child = new Node2(draftState, nextOwner); //, transposeTable);
                addToTree = false;
//            }
            node.addChild(childKey, child);
        }

        // Make sure that the draft states are the same
        if (child != null)
        {
            int[] childDraftState = child.getDraftState();
            for (int i = 0; i < draftState.length; ++i)
            {
                assert childDraftState[i] == draftState[i] : "Error: Draft states are inconsistent!";
            }
        }

        double[] values = uctSimulation_r(child, draftState, addToTree);

        // Undo the changes to the draft state
        assert draftState[childKey] == node.getOwner() : "Error: Something happened to draftState that was unintended.";
        draftState[childKey] = -1;

        // Update the value at this node
        int playerForValue = node.getOwner() - 1;
        if (playerForValue < 0)
        {
            playerForValue = board.getNumberOfPlayers() - 1;
        }
        double target = values[playerForValue];
        node.setValue(node.getValue() + (1.0 / node.getNumVisits())*(target - node.getValue()));

        // Propogate the values back
        return values;
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
            if (SELFISH)
            {
                return evaluationFunctionSelfish(draftState, m_evalFunc);
            }
            return evaluationFunction(draftState, m_evalFunc);
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

    /**
     * Moves the root of the tree down after each other player has made a move
     * @param draftState The current state of the draft.
     */
    public void updateRoot(int[] draftState)
    {
        boolean firstCheck = true;
        for (int player = ID; player != ID || firstCheck || m_root == null; player = (player + 1) % board.getNumberOfPlayers() )
        {
            firstCheck = false;
            if (m_root == null)
            {
                // We haven't seen this state before, so create it
                m_root = new Node2(draftState, ID); //, transposeTable);
                break;
            }

            // Find the move that player took
            int[] oldDraftState = m_root.getDraftState();
            for (int terr = 0; terr < oldDraftState.length; ++terr)
            {
                if (oldDraftState[terr] == -1 && draftState[terr] == player)
                {
                    // Found the move
                    assert m_root.getChildren().containsKey(terr) : "Error: Illegal child!";
                    m_root = m_root.getChildren().get(terr);
                    break;
                }
            }
        }

        System.gc();

        // Index into the transposition table
//        ArrayList<Integer> state = new ArrayList<Integer>();
//        for (int i = 0; i < draftState.length; ++i)
//        {
//            state.add(draftState[i]);
//        }
//
//        // Find the new root
//        if (transposeTable.containsKey(state))
//        {
//            root = transposeTable.get(state);
//        }
//        else
//        {
//            // Create a new root
//            root = new Node2(draftState, ID, transposeTable);
//        }
        
        // Get rid of unneeded stuff from the transposition table
//        transposeTable.clear();
//        populateTransposeTable(root);
    }

    /**
     * Restocks the transposition table
     * @param node The current node to add to the table
     */
//    private void populateTransposeTable(Node2 node)
//    {
//        if (node == null)
//        {
//            // Abort
//            return;
//        }
//
//        // Create the index for the table
//        ArrayList<Integer> state = new ArrayList<Integer>();
//        int[] draftState = node.getDraftState();
//        for (int i = 0; i < draftState.length; ++i)
//        {
//            state.add(draftState[i]);
//        }
//
//        if (transposeTable.containsKey(state))
//        {
//            // Abort: This node has already been added
//            return;
//        }
//        else
//        {
//            // Add this node to the table
//            transposeTable.put(state, node);
//
//            // Add all of its children
//            HashMap<Integer,Node2> children = node.getChildren();
//            Set<Integer> set = children.keySet();
//            Iterator<Integer> iter = set.iterator();
//
//            while (iter.hasNext())
//            {
//                Node2 child = children.get(iter.next());
//                populateTransposeTable(child);
//            }
//        }
//    }
}
