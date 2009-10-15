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
public class MaxN_MC_Drafter extends SmartDrafter
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
    private final int NUM_MC_ROLL_OUTS = 1;

    /**
     * A cap on how big the branching factor can be.  When there are more branches,
     * we use the passed in heuristic to reduce the branching down to our max.
     */
    private final int MAX_BRANCHING_FACTOR = 15;

    /**
     * Constructor
     */
    public MaxN_MC_Drafter()
    {

    }

    /**
     * Calls the heuristic function to get the value of the passed in territory
     * @param terr The territory whose value we want
     * @param draftState The state of the draft
     * @param unownedCountries The countries still available to be picked
     * @param activePlayer The player whose turn it is to pick
     * @return The value of the passed in territory
     */
    @Override
    protected double getValueOfTerr(int terr, int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer)
    {
        // Call the heuristic function to get the value of the territory
        ArrayList<Integer> index = getTerritoryIndex(terr, draftState, activePlayer);
        assert(m_learnedHeuristic != null);
        assert(m_learnedHeuristic[terr].containsKey(index));
        double[] value = m_learnedHeuristic[terr].get(index);
        return value[0];
    }

    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        // Determine the depth to which we can search
        int depth = calculateMaxNSearchDepth(unownedCountries.size(), MAX_BRANCHING_FACTOR, MAX_NUM_LEAVES);

        // Determine which territories to consider picking
        ArrayList<Integer> picksToConsider = new ArrayList<Integer>();
        if (MAX_BRANCHING_FACTOR < unownedCountries.size())
        {
            int[] picks = getTopPicks(draftState, unownedCountries, ID, MAX_BRANCHING_FACTOR);
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
        for (int countryIndex : picksToConsider)
        {
            // Determine the value of picking this country
            assert(unownedCountries.contains((Integer) countryIndex));
            assert(unownedCountries.contains((Integer) countryIndex));
            draftState[countryIndex] = ID; // Pick country
            unownedCountries.remove((Integer) countryIndex);

            double[] values = maxNMC(draftState, unownedCountries, (ID + 1) % board.getNumberOfPlayers(), depth, board.getNumberOfPlayers(), MAX_BRANCHING_FACTOR, NUM_MC_ROLL_OUTS);

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

        return bestPick;
    }
}
