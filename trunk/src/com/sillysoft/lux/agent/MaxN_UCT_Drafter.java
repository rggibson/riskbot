/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import java.util.ArrayList;

/**
 *
 * @author Richard Gibson, Neesha Desai, Richard Zhao
 */
public class MaxN_UCT_Drafter extends SmartDrafter
{
    /**
     * The maximum number of leaves we want to have on our MaxN portion of the
     * search
     */
    private final int MAX_NUM_LEAVES = 1;

    /**
     * The number of Monte Carlo roll outs that we average over in MaxNMC, for
     * each leaf node of the MaxN portion of the search.
     */
    private final int NUM_MC_ROLL_OUTS = 1000;

    /**
     * Should we use the selfish evaluation function?
     */
    private final boolean SELFISH = false;

    /**
     * A cap on how big the branching factor can be.  When there are more branches,
     * we use the passed in heuristic to reduce the branching down to our max.
     */
    private final int MAX_BRANCHING_FACTOR = Integer.MAX_VALUE;

    /**
     * Constructor
     */
    public MaxN_UCT_Drafter()
    {

    }

    @Override
    public String name()
    {
        if (SELFISH)
        {
            return "MaxN_UCT_Drafter-Selfish";
        }
        else
        {
            return "MaxN_UCT_Drafter";
        }
    }

    /**
     * Calls the heuristic function to get the value of the passed in territory
     * @param terr The territory whose value we want
     * @param draftState The state of the draft
     * @param unownedCountries The countries still available to be picked
     * @param activePlayer The player whose turn it is to pick
     * @return The value of the passed in territory
     */
//    @Override
//    protected double getValueOfTerr(int terr, int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer)
//    {
//        // Call the heuristic function to get the value of the territory
//        ArrayList<Integer> index = getTerritoryIndex(terr, draftState, activePlayer);
//        assert(m_learnedHeuristic != null);
//        assert(m_learnedHeuristic[terr].containsKey(index));
//        double[] value = m_learnedHeuristic[terr].get(index);
//        return value[0];
//    }

    @Override
    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        // First, clean up the uct tree
        cleanUpUctTree(draftState, ID);

        // Determine the depth to which we can search
        int depth = calculateMaxNSearchDepth(unownedCountries.size(), MAX_BRANCHING_FACTOR, MAX_NUM_LEAVES);

        // Determine which territories to consider picking
        ArrayList<Integer> picksToConsider = new ArrayList<Integer>();
        if (MAX_BRANCHING_FACTOR < unownedCountries.size())
        {
            int[] picks = getTopPicks(draftState, unownedCountries, ID, MAX_BRANCHING_FACTOR, SELFISH);
            picksToConsider = new ArrayList<Integer>(picks.length);
            for (int i = 0; i < picks.length; ++i)
            {
                picksToConsider.add(picks[i]);
            }
        }
        else
        {
            for (int pick : unownedCountries)
            {
                picksToConsider.add(pick);
            }
        }

        // Determine which territory to pick
        int bestPick = -1;
        double valueOfBestPick = Double.MIN_VALUE;
        if (depth <= 0)
        {
            // Just run UCT right away from the current state
            maxN_Uct(draftState, unownedCountries, ID, depth, MAX_BRANCHING_FACTOR, NUM_MC_ROLL_OUTS, SELFISH);

            ArrayList<Integer> state = new ArrayList<Integer>(draftState.length);
            for (int i = 0; i < draftState.length; ++i)
            {
                state.add(draftState[i]);
            }

            // Check all the children to see which is best
            for (int terr : unownedCountries)
            {
                assert(state.get(terr) == -1);
                state.set(terr, ID);
                assert(m_uctTree.containsKey(state));
                double valueOfTerr = (m_uctTree.get(state))[ID + 1]; // First index is reserved for num visits
                state.set(terr, -1);
                if (valueOfTerr > valueOfBestPick)
                {
                    bestPick = terr;
                    valueOfBestPick = valueOfTerr;
                }
            }
        }
        else
        {
            for (int countryIndex : picksToConsider)
            {
                // Determine the value of picking this country
                assert(unownedCountries.contains((Integer) countryIndex));
                assert(unownedCountries.contains((Integer) countryIndex));
                draftState[countryIndex] = ID; // Pick country
                unownedCountries.remove((Integer) countryIndex);

                double[] values = maxN_Uct(draftState, unownedCountries, (ID + 1) % board.getNumberOfPlayers(), depth - 1, MAX_BRANCHING_FACTOR, NUM_MC_ROLL_OUTS, SELFISH);

                assert(draftState[countryIndex] != -1);
                assert(!unownedCountries.contains((Integer) countryIndex));
                draftState[countryIndex] = -1; // Undo pick
                unownedCountries.add(countryIndex);

                // Is country better than the rest we've seen?
                // For now, we break ties by choosing the first country we inspected
                assert(values.length > ID && ID >= 0);
                if (values[ID] > valueOfBestPick)
                {
                    // Found new best pick
                    bestPick = countryIndex;
                    valueOfBestPick = values[ID];
                }
            }
        }

        return bestPick;
    }
}
