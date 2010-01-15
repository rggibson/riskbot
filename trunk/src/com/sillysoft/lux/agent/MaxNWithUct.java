/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import java.util.ArrayList;
import com.sillysoft.lux.*;

/**
 * The MaxN algorithm, using UCT as the heuristic and to cut off branching factor.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class MaxNWithUct extends SmartDrafter
{
    /**
     * Factors in to how many roll outs we do, according to how much time we have.
     * The bigger the number, the fewer number of roll outs we'll perform.
     */
//    final protected int MILLSECS_PER_ROLL_OUT = 4;

    /**
     * The evaluation function to use
     */
    protected int m_evalFunc = LIN_REG_NOM_FEATS_WITH_REPS;

    /**
     * The maximum number of picks to consider
     * Set to -1 to do as many in time limit
     */
    final protected int MAX_DEPTH = -1; // board.getNumberOfPlayers()*4 + 1;

    /**
     * Current number of simulations we are allowing
     * This will be set to something else if we are obeying time limit (see above parameter)
     */
    protected int m_numSimulations = 3000;

    /**
     * Need this to run UCT simulations
     */
    protected UCT_Drafter2 m_uctDrafter;

    // Constructor
    public MaxNWithUct()
    {
        super();
    }

    @Override
    public void setPrefs(int ID, Board board)
    {
        super.setPrefs(ID, board);

        m_uctDrafter = (UCT_Drafter2) board.getAgentInstance("UCT_Drafter2");
        m_uctDrafter.setPrefs(ID, board);
    }

    @Override
    public String name()
    {
        return "MaxNDrafter";
    }

    public int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        long alarm = System.currentTimeMillis() + PICK_TIME_IN_MILLIS_PER_UNOWNED_TERR*((int)Math.ceil((double)unownedCountries.size() / board.getNumberOfPlayers()));
        if (MAX_DEPTH != -1)
        {
            // For debugging
            alarm = Long.MAX_VALUE;
        }

        int terr = -1;
        int nextPick = -1;
        int maxDepthThisIteration = 1;

//        if (MAX_PICKS_CONSIDERED == -1)
//        {
//            m_numSimulations = PICK_TIME_IN_MILLIS_PER_UNOWNED_TERR*unownedCountries.size() / MILLSECS_PER_ROLL_OUT;
//        }

        // Synchronize the uct drafter's root node
        m_uctDrafter.updateRoot(draftState);

        assert m_uctDrafter.getRoot().getOwner() == ID : "Error: Root owner is not this player!";

        // Run simulations
        for (int i = 0; i < m_numSimulations; ++i)
        {
            m_uctDrafter.runUctSimulation(m_uctDrafter.getRoot(), draftState);
        }

        // With the time constraint, we are going to iterate over the maximum depth
        do
        {
            nextPick = maxNWithUct(draftState, maxDepthThisIteration, alarm, m_uctDrafter.getRoot());
            maxDepthThisIteration += board.getNumberOfPlayers();

            if (nextPick != -1)
            {
                terr = nextPick;
            }

//            System.out.println("Num picks to consider = " + numPicksToConsider);

            if (unownedCountries.size() < maxDepthThisIteration ||
                    (MAX_DEPTH != -1 && MAX_DEPTH < maxDepthThisIteration))
            {
                // Done
                break;
            }

        }
        while (System.currentTimeMillis() < alarm);

        if (terr == -1)
        {
            // Time ran out before we could find a pick.
            // Just pick the first unowned territory we find (this should really never happen)
            // THIS IS A HACK
            for (Country country : countries)
            {
                if (country.getOwner() == -1)
                {
                    terr = country.getCode();
                    break;
                }
            }
        }

        while (MAX_DEPTH == -1 && System.currentTimeMillis() < alarm)
        {
            Node2 node = m_uctDrafter.getRoot().getChildren().get(terr);
            m_uctDrafter.runUctSimulation(node, node.getDraftState());
        }

//        System.out.println("\n\nMaxNumPicksConsidered = " + (maxNumPicksConsidered - 1) + "\n");

        return terr;
    }

    /**
     * The MaxN algorithm, using UCT as the branch reducing heuristic
     * @param draftState The current state of the draft
     * @param unownedCountries A list of all countries to pick from
     * @param activePlayer The player whose turn it is to pick
     * @param maxNumPicksToConsider The cap for how badly ranked a territory we will consider
     * @param alarm If the current time exceeds the alarm, it is time to stop and return -1.
     * @param firstCall True if this is the first (i.e. non-recursive) call
     * @return The territory to pick, as given by the MaxN algorithm
     */
    private int maxNWithUct(int[] draftState, int depth, long alarm, Node2 node)
    {
        // Rank the available picks
        if (node == null)
        {
            assert node != null : "Error: null node!";
        }

        if (System.currentTimeMillis() >= alarm)
        {
            // Time is up; use pick from last iteration
            return -1;
        }

        for (int i = node.getNumVisits(); i < m_numSimulations; ++i)
        {
            m_uctDrafter.runUctSimulation(node, draftState);
        }

        if (depth <= 0)
        {
            // Leaf node for this iteration.
            node.setMaxNValue(node.getAllValues());
            return -1; // Don't know what move to take from here
        }
        int[] topPicks = m_uctDrafter.getNBestPicks(node, (int)Math.ceil((double)depth / board.getNumberOfPlayers()));

        int bestPick = -1;
        for (int pick : topPicks)
        {
            // Find the value of this pick
            Node2 childNode = node.getChildren().get(pick);
            draftState[pick] = node.getOwner();

            maxNWithUct(draftState, depth - 1, alarm, childNode);

            // Undo the pick
            draftState[pick] = -1;

            if (System.currentTimeMillis() >= alarm)
            {
                // Time is up; use pick from last iteration
                return -1;
            }

            // Is this the new best pick?
            if (bestPick == -1 || childNode.getMaxNValue()[node.getOwner()] >= node.getChildren().get(bestPick).getMaxNValue()[node.getOwner()])
            {
                if (bestPick == -1 || childNode.getMaxNValue()[node.getOwner()] > node.getChildren().get(bestPick).getMaxNValue()[node.getOwner()])
                {
                    // New best pick
                    bestPick = pick;
                }
                else
                {
                    // We have a tie.  Time to break it.
                    int thisSum = 0;
                    int thatSum = 0;
                    for (int i = 0; i < board.getNumberOfPlayers(); ++i)
                    {
                        if (i != node.getOwner())
                        {
                            thisSum += childNode.getMaxNValue()[i];
                            thatSum += node.getChildren().get(bestPick).getMaxNValue()[i];
                        }
                    }

                    if (thisSum > thatSum)
                    {
                        bestPick = pick;
                    }
                }
            }
        }

        // Set our value equal to the value of the best child
        node.setMaxNValue(node.getChildren().get(bestPick).getMaxNValue());

        return bestPick;
    }
}
