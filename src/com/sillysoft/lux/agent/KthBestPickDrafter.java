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
public class KthBestPickDrafter extends SmartDrafter
{
    /**
     * The maximum number of picks that we will consider looking ahead
     * for the kthBestPick algorithm.
     */
    final protected int MAX_NUM_PICKS_CONSIDERED = 2;

    /**
     * The heuristic function to use
     */
    final protected Heuristic HEURISTIC_FUNCTION = Heuristic.MAX_N_MC;

    /**
     * When use MaxN-MC as the heuristic, this is the max branching factor
     */
    final protected int MAX_BRANCHING_FACTOR = Integer.MAX_VALUE;

    /**
     * When using MaxN-MC as the heuristic, this is the number of MC roll outs
     * to perform at each leaf node
     */
    final protected int NUM_MC_ROLL_OUTS = 1;

    /**
     * When using MaxN-MC as the heuristic, this is the maximum number of leaves
     * to have in the MaxN portion of the search.
     */
    final protected int MAX_NUM_LEAVES = 1;

    /**
     * The opponent models to use
     */
    final OpponentModel[] OPPONENT_MODELS = {   OpponentModel.KTH_BEST_PICK,
                                                OpponentModel.KTH_BEST_PICK,
                                                OpponentModel.KTH_BEST_PICK };

    /**
     * The different types of heuristic functions that kthBestPick will accept
     */
    protected enum Heuristic
    {
        MAX_N_MC,
        DUMB,
        RANDOM,
        LEARNED
    }

    /**
     * The different opponent models that kthBestPick can use
     */
    protected enum OpponentModel
    {
        KTH_BEST_PICK
    }

    // Constructor
    public KthBestPickDrafter()
    {
        super();
    }

    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        // Make sure we have the right number of opponent models
         assert(board.getNumberOfPlayers() == OPPONENT_MODELS.length);

        // Check the Hashtable to see if we've already called the algorithm
        // on this state with this many picks considered
        int terr = -1;

        terr = kthBestPick(draftState, unownedCountries, ID, MAX_NUM_PICKS_CONSIDERED);

        return terr;
    }

    /**
     * The KthBestPick algorithm
     * @param draftState The current state of the draft
     * @param h The heurstic used to rank picks
     * @param m The opponent models of the opponent players
     * @param unownedCountries A list of all countries to pick from
     * @param activePlayer The player whose turn it is to pick
     * @param maxNumPicksToConsider The cap for how badly ranked a territory we will consider
     * @return The territory to pick, as given by the KthBestPick algorithm
     */
    private int kthBestPick(int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer, int maxNumPicksToConsider)
    {
        // How many picks do we have left in the draft?
        int numPicksToConsider = (int)Math.ceil((double)unownedCountries.size() / board.getNumberOfPlayers());

        // We truncate the number of picks we are considering
        numPicksToConsider = Math.min(numPicksToConsider, maxNumPicksToConsider);

        // Now rank the available picks
        // TODO: rggibson - How to handle tie-breaks?
        int[] topPicks = getTopPicks(draftState, unownedCountries, activePlayer, numPicksToConsider);

        for (int k = numPicksToConsider - 1; k >= 0; --k)
        {
            // Consider the pick of rank k
            int pick = topPicks[k];
            ArrayList<Integer> betterPicks = new ArrayList<Integer>();
            for (int i = 0; i < k; ++i)
            {
                betterPicks.add(topPicks[i]);
            }
            boolean makeThisPick = true;

            // We need to keep track of the changes we make to the draft state,
            // so that we can put it back to normal once we exit the below while
            // loop
            ArrayList<Integer> stateAlterations = new ArrayList<Integer>();
            int nextPick = pick;
            int currentPlayer = activePlayer;

            // We also need to keep track of the number of picks we have left
            // to consider
            int numPicksLeft = numPicksToConsider;
            while (betterPicks.size() > 0)
            {
                // We decrement the number of picks left when we pass the original player.
                // This effectively truncates the game after numPicksToConsider many picks
                // for the original player, including the original one we are considering
                if (currentPlayer == ID)
                {
                    numPicksLeft--;
                }

                // Create the "child" state
                assert(draftState[nextPick] == -1);
                assert(unownedCountries.contains(nextPick));
                draftState[nextPick] = currentPlayer;
                unownedCountries.remove((Integer) nextPick);
                stateAlterations.add((Integer) nextPick);
                currentPlayer = (currentPlayer + 1) % board.getNumberOfPlayers();

                // Find the next pick according to the opponent model
                switch(OPPONENT_MODELS[currentPlayer])
                {
                    case KTH_BEST_PICK:
                        // Now get the next pick
                        nextPick = kthBestPick(draftState, unownedCountries, currentPlayer, numPicksLeft);
                        break;

                    default:
                        assert(false);
                        break;
                }

                // Should we continue checking more picks?
                if (currentPlayer == activePlayer)
                {
                    if (betterPicks.contains((Integer) nextPick))
                    {
                        // Good, we can continue
                        betterPicks.remove((Integer) nextPick);
                    }
                    else
                    {
                        // We can terminate early if we are not going to consider
                        // enough picks down the road
                        if (numPicksLeft <= betterPicks.size())
                        {
                            makeThisPick = false;
                            break;
                        }
                    }
                }
                else
                {
                    if (betterPicks.contains((Integer) nextPick))
                    {
                        // Bad, another player picked a better territory
                        makeThisPick = false;
                        break;
                    }
                }
            }

            // Fix the alteration we made to the draft state
            for (Integer terr : stateAlterations)
            {
                assert(draftState[terr] != -1);
                assert(!unownedCountries.contains(terr));
                draftState[terr] = -1;
                unownedCountries.add(terr);
            }

            if (makeThisPick)
            {
                return pick;
            }
        }

        // Should never get here
        assert(false);
        return -1;
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
        double valueOfTerr = 0.0;

        switch(HEURISTIC_FUNCTION)
        {
            case MAX_N_MC:
                // Call MaxN-MC to get the rank of each pick
                int depth = calculateMaxNSearchDepth(unownedCountries.size(), MAX_BRANCHING_FACTOR, MAX_NUM_LEAVES);
                assert(unownedCountries.contains((Integer) terr));
                draftState[terr] = activePlayer;
                unownedCountries.remove((Integer) terr);

                double[] values = maxNMC(draftState, unownedCountries, (activePlayer + 1) % board.getNumberOfPlayers(), depth, board.getNumberOfPlayers(), MAX_BRANCHING_FACTOR, NUM_MC_ROLL_OUTS);

                assert(draftState[terr] != -1);
                assert(!unownedCountries.contains((Integer) terr));
                draftState[terr] = -1;
                unownedCountries.add(terr);

                valueOfTerr = values[activePlayer];
                break;

            case DUMB:
                // Do nothing because we're dumb
                break;

            case RANDOM:
                // Random assign a value between 0 and 1
                valueOfTerr = Math.random();
                break;

            case LEARNED:
                // Call the heuristic function to get the value of the territory
                assert(m_learnedHeuristic != null);
                ArrayList<Integer> index = getTerritoryIndex(terr, draftState, activePlayer);
                assert(m_learnedHeuristic[terr].containsKey(index));
                double[] value = m_learnedHeuristic[terr].get(index);
                valueOfTerr = value[0];
                break;

            default:
                assert(false);
                break;
        }

        return valueOfTerr;
    }
}
