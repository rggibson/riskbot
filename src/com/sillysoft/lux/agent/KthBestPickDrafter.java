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
     * The heuristic function to use
     */
    final protected Heuristic HEURISTIC_FUNCTION = Heuristic.RANDOM;

    /**
     * When use MaxN-UCT as the heuristic, this is the max branching factor
     */
    final protected int MAX_BRANCHING_FACTOR = Integer.MAX_VALUE;

    /**
     * When using MaxN-MC as the heuristic, this is the maximum number of leaves
     * to have in the MaxN portion of the search.
     */
    final protected int MAX_NUM_LEAVES = 1;

    /**
     * Factors in to how many roll outs we do, according to how much time we have.
     * The bigger the number, the fewer numbero f roll outs we'll perform.
     */
    final protected int MILLSECS_PER_ROLL_OUT = 6;

    /**
     * Current number of simulations we are allowing
     */
    protected int m_numSimulations;

    /**
     * The opponent models to use
     * TODO: These need to be dependent on the player's name, not ID, if we are
     * sticking with random turn order.
     */
    final OpponentModel[] OPPONENT_MODELS = {   OpponentModel.KTH_BEST_PICK,
                                                OpponentModel.KTH_BEST_PICK,
                                                OpponentModel.KTH_BEST_PICK };

    /**
     * Whether we are selfish or not (we assume the opponents' selfishnesses match
     * our own).
     */
    final boolean SELFISH = false;

    /**
     * The different types of heuristic functions that kthBestPick will accept
     */
    protected enum Heuristic
    {
        MAX_N_UCT,
        DUMB,
        RANDOM,
        LEARNED
    }

    /**
     * The different opponent models that kthBestPick can use
     */
    protected enum OpponentModel
    {
        KTH_BEST_PICK,
        RANDOM,
        UCT,
        GREEDY
    }

    // Constructor
    public KthBestPickDrafter()
    {
        super();
    }

    @Override
    public String name()
    {
        if (SELFISH)
        {
            return "KthBestPickDrafter-Selfish";
        }
        else
        {
            return "KthBestPickDrafter";
        }
    }

    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        long alarm = System.currentTimeMillis() + PICK_TIME_IN_MILLIS_PER_UNOWNED_TERR*unownedCountries.size();

        // Make sure we have the right number of opponent models
        assert(board.getNumberOfPlayers() == OPPONENT_MODELS.length);

        // Clean up the UCT tree if we are using it
        if (HEURISTIC_FUNCTION == Heuristic.MAX_N_UCT)
        {
            cleanUpUctTree(draftState, ID);
        }

        int terr = -1;
        int nextPick = -1;
        int maxNumPicksConsidered = 1;

        // If we are using UCT as the heuristic
        m_numSimulations = PICK_TIME_IN_MILLIS_PER_UNOWNED_TERR*unownedCountries.size() / MILLSECS_PER_ROLL_OUT;

        // With the time constraint, we are going to iterate over the maximum number
        // of picks to consider
        do
        {
            nextPick = kthBestPick(draftState, unownedCountries, ID, maxNumPicksConsidered, alarm, true);
            maxNumPicksConsidered++;

            if (nextPick != -1)
            {
                terr = nextPick;
            }

            // How many picks do we have left in the draft?
            int numPicksToConsider = (int)Math.ceil((double)unownedCountries.size() / board.getNumberOfPlayers());

            System.out.println("Num picks to consider = " + numPicksToConsider);

            if (numPicksToConsider < maxNumPicksConsidered)
            {
                // Done
                break;
            }
                
        }
        while (nextPick != -1 && System.currentTimeMillis() < alarm);

        if (terr == -1)
        {
            assert(false);
        }

        System.out.println("\n\nMaxNumPicksConsidered = " + (maxNumPicksConsidered - 1) + "\n");

        return terr;
    }

    /**
     * The KthBestPick algorithm
     * @param draftState The current state of the draft
     * @param unownedCountries A list of all countries to pick from
     * @param activePlayer The player whose turn it is to pick
     * @param maxNumPicksToConsider The cap for how badly ranked a territory we will consider
     * @param selfish Whether to use a selfish evaluation or not.
     * @param alarm If the current time exceeds the alarm, it is time to stop and return -1.
     * @param firstCall True if this is the first (i.e. non-recursive) call
     * @return The territory to pick, as given by the KthBestPick algorithm
     */
    private int kthBestPick(int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer, int numPicksToConsider, long alarm, boolean firstCall)
    {
        // Rank the available picks
        // TODO: rggibson - How to handle tie-breaks?
        int[] topPicks = getTopPicks(draftState, unownedCountries, activePlayer, numPicksToConsider, SELFISH);
        int minRank = firstCall ? numPicksToConsider - 1 : 0;

        for (int k = numPicksToConsider - 1; k >= minRank; --k)
        {
            // Alarm check
            if (System.currentTimeMillis() >= alarm)
            {
                return -1;
            }

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
                // Alarm check
                if (System.currentTimeMillis() >= alarm)
                {
                    return -1;
                }

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
                        nextPick = kthBestPick(draftState, unownedCountries, currentPlayer, numPicksLeft, alarm, false);
                        break;

                    case RANDOM:
                        nextPick = unownedCountries.get((int)(Math.random()*unownedCountries.size()));
                        break;

                    case GREEDY:
                        // TODO: We can model whether a greedy opponent is selfish or not
                        nextPick = getGreedyPick(draftState, unownedCountries, currentPlayer, SELFISH);
                        break;

                    case UCT:
                        this.maxN_Uct(draftState, unownedCountries, currentPlayer, 0, Integer.MAX_VALUE, m_numSimulations, SELFISH);
                        ArrayList<Integer> picks = new ArrayList<Integer>();
                        double valueOfPicks = -Double.MAX_VALUE;
                        for (int terr : unownedCountries)
                        {
                            double value = getValueOfTerrFromUctTree(terr, draftState, currentPlayer, SELFISH);
                            if (value > valueOfPicks)
                            {
                                // New best pick
                                picks.clear();
                                picks.add(terr);
                                valueOfPicks = value;
                            }
                            else if (value == valueOfPicks)
                            {
                                picks.add(terr);
                            }
                        }
                        assert(!picks.isEmpty());
                        nextPick = picks.get((int)(Math.random()*picks.size()));
                        break;

                    default:
                        assert(false);
                        break;
                }

                if (nextPick == -1)
                {
                    if (System.currentTimeMillis() < alarm)
                    {
                        assert(false);
                    }
                    return -1;
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

        // Should only get here if firstCall == true and the one pick failed
        return -1;
    }

    /**
     * Calls the heuristic function to get the value of the passed in territory
     * @param terr The territory whose value we want
     * @param draftState The state of the draft
     * @param unownedCountries The countries still available to be picked
     * @param activePlayer The player whose turn it is to pick
     * @param selfish Whether or not to use the selfish evaluation function
     * @return The value of the passed in territory
     */
    @Override
    protected double getValueOfTerr(int terr, int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer, boolean selfish)
    {
        double valueOfTerr = 0.0;

        switch(HEURISTIC_FUNCTION)
        {
            case MAX_N_UCT:
                // Call MaxN-MC to get the rank of each pick
                int depth = calculateMaxNSearchDepth(unownedCountries.size(), MAX_BRANCHING_FACTOR, MAX_NUM_LEAVES);
                if (depth <= 0)
                {
                    // Just run UCT right away from the current state
                    maxN_Uct(draftState, unownedCountries, ID, depth, MAX_BRANCHING_FACTOR, m_numSimulations, selfish);

                    valueOfTerr = getValueOfTerrFromUctTree(terr, draftState, activePlayer, selfish);
                }
                else
                {
                    assert(unownedCountries.contains((Integer) terr));
                    draftState[terr] = activePlayer;
                    unownedCountries.remove((Integer) terr);

                    double[] values = maxN_Uct(draftState, unownedCountries, (activePlayer + 1) % board.getNumberOfPlayers(), depth - 1, MAX_BRANCHING_FACTOR, m_numSimulations, selfish);

                    assert(draftState[terr] != -1);
                    assert(!unownedCountries.contains((Integer) terr));
                    draftState[terr] = -1;
                    unownedCountries.add(terr);

                    valueOfTerr = values[activePlayer];
                }
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

    /**
     * Calls up the UCT tree to get the value of making this pick
     * @param terr The territory in question
     * @param draftState The state of the draft
     * @param activePlayer The player whose turn it is to pick
     * @param selfish Whether to evaluate selfishly or not
     * @return The value of making this pick
     */
    protected double getValueOfTerrFromUctTree(int terr, int[] draftState, int activePlayer, boolean selfish)
    {
        ArrayList<Integer> state = new ArrayList<Integer>(draftState.length);
        for (int i = 0; i < draftState.length; ++i)
        {
            state.add(draftState[i]);
        }

        // Now get the value of the territory
        assert(state.get(terr) == -1);
        state.set(terr, ID);
        assert(m_uctTree.containsKey(state));
        return (m_uctTree.get(state))[ID + 1]; // First index is reserved for num visits
    }
}
