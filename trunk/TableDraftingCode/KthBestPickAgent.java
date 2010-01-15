/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.ArrayList;

/**
 *
 * @author Richard
 */
public class KthBestPickAgent extends Agent
{
    private final int MAX_PICKS_CONSIDERED = -1;

    /**
     * Constructor
     */
    public KthBestPickAgent()
    {
        super();
    }

    /**
     * Method for making a selection in the table drafting game
     * @param availableItems The items available for picking
     * @return The picked item
     */
    public Item makePick(ArrayList<Item> availableItems)
    {
        long alarm = System.currentTimeMillis() + Main.TIME_LIMIT * (int)Math.ceil((double)availableItems.size() / Main.numPlayers);
        
        int numItems = availableItems.size();

        if (MAX_PICKS_CONSIDERED != -1)
        {
            // For debugging
            alarm = Long.MAX_VALUE;
        }

        Item item = null;
        Item nextPick = null;
        int maxNumPicksConsidered = 1;

        // With the time constraint, we are going to iterate over the maximum number
        // of picks to consider
        do
        {
            nextPick = kthBestPick(availableItems, m_ID, maxNumPicksConsidered, alarm, true);
            if (nextPick == null && maxNumPicksConsidered > 1)
            {
                Main.kthPickCounts[maxNumPicksConsidered-2]++;
            }
            maxNumPicksConsidered++;

            if (nextPick != null)
            {
                item = nextPick;
            }

            // How many picks do we have left in the draft?
            int numPicksToConsider = (int)Math.ceil((double)availableItems.size() / Main.numPlayers);

//            System.out.println("Num picks to consider = " + numPicksToConsider);

            if (numPicksToConsider < maxNumPicksConsidered ||
                    (MAX_PICKS_CONSIDERED != -1 && MAX_PICKS_CONSIDERED < maxNumPicksConsidered))
            {
                // Done
                if (nextPick != null)
                {
                    Main.kthPickCounts[maxNumPicksConsidered-2]++;
                }
                break;
            }

        }
        while (nextPick != null && System.currentTimeMillis() < alarm);

        if (item == null)
        {
            // Time ran out before we could find a pick.
            // Just pick the first item we find (this should really never happen)
            // THIS IS A HACK
            item = availableItems.get(0);
        }

//        System.out.println("\n\nMaxNumPicksConsidered = " + (maxNumPicksConsidered - 1) + "\n");

        assert availableItems.size() == numItems : "Error: Didn't fix available items list!";

        return item;
    }

    /**
     * Returns the name of this agent
     * @return The name of this agent.
     */
    @Override
    public String toString()
    {
        return "KthBestPick";
    }

    /**
     * The KthBestPick algorithm, using UCT as the heuristic
     * @param unownedCountries A list of all countries to pick from
     * @param activePlayer The player whose turn it is to pick
     * @param maxNumPicksToConsider The cap for how badly ranked a territory we will consider
     * @param alarm If the current time exceeds the alarm, it is time to stop and return -1.
     * @param firstCall True if this is the first (i.e. non-recursive) call
     * @return The territory to pick, as given by the KthBestPick algorithm
     */
    private Item kthBestPick(ArrayList<Item> availableItems, int activePlayer, int numPicksToConsider, long alarm, boolean firstCall)
    {
        // Rank the available picks
        Item[] topPicks = getNBestPicks(availableItems, numPicksToConsider, activePlayer);

        int minRank = firstCall ? numPicksToConsider - 1 : 0;

        for (int k = numPicksToConsider - 1; k >= minRank; --k)
        {
            // Alarm check
            if (System.currentTimeMillis() >= alarm)
            {
                return null;
            }

            // Consider the pick of rank k
            Item pick = topPicks[k];
            ArrayList<Item> betterPicks = new ArrayList<Item>();
            for (int i = 0; i < k; ++i)
            {
                betterPicks.add(topPicks[i]);
            }
            boolean makeThisPick = true;

            // We need to keep track of the changes we make to the draft state,
            // so that we can put it back to normal once we exit the below while
            // loop
            ArrayList<Item> stateAlterations = new ArrayList<Item>();
            Item nextPick = pick;
            int currentPlayer = activePlayer;

            // We also need to keep track of the number of picks we have left
            // to consider
            int numPicksLeft = numPicksToConsider;
            while (betterPicks.size() > 0)
            {
                // Alarm check
                if (System.currentTimeMillis() >= alarm)
                {
                    // Fix the alteration we made to the draft state
                    for (Item item : stateAlterations)
                    {
                        assert(!availableItems.contains(item));
                        availableItems.add(item);
                    }
                    return null;
                }

                // We decrement the number of picks left when we pass the original player.
                // This effectively truncates the game after numPicksToConsider many picks
                // for the original player, including the original one we are considering
                if (currentPlayer == m_ID)
                {
                    numPicksLeft--;
                }

                // Create the "child" state
                assert(availableItems.contains(nextPick));
                availableItems.remove(nextPick);
                stateAlterations.add(nextPick);
                currentPlayer = (currentPlayer + 1) % Main.numPlayers;

                // Find the next pick according to the opponent model
//                if (USE_ORACLE_MODELS)
//                {
//                    if (m_oracles[currentPlayer].contains("KthBestPick"))
//                    {
//                        nextPick = kthBestPickWithUct(draftState, unownedCountries, currentPlayer, numPicksLeft, alarm, false, child);
//                    }
//                    else if (m_oracles[currentPlayer].contains("Random"))
//                    {
//                        nextPick = unownedCountries.get((int)(Math.random()*unownedCountries.size()));
//                    }
//                    else if (m_oracles[currentPlayer].contains("Greedy"))
//                    {
//                        // TODO: We can model whether a greedy opponent is selfish or not
//                        nextPick = getGreedyPick(draftState, unownedCountries, currentPlayer, SELFISH, m_evalFunc);
//                    }
//                    else if (m_oracles[currentPlayer].contains("UCT"))
//                    {
//                        for (int i = child.getNumVisits(); i < m_numSimulations; ++i)
//                        {
//                            m_uctDrafter.runUctSimulation(child, draftState);
//                        }
//                        int[] bestPick = m_uctDrafter.getNBestPicks(child, 1);
//                        nextPick = bestPick[0];
//                    }
//                    else if (m_oracles[currentPlayer].contains("Quo"))
//                    {
//                        nextPick = m_quoClone.getPick(draftState, unownedCountries);
//                    }
//                    else
//                    {
//                        assert false : "Unrecognized opponent";
//                    }
//                }
//                else
//                {
                    // Not using oracle models
                    nextPick = kthBestPick(availableItems, currentPlayer, numPicksLeft, alarm, false);
//                }

                if (nextPick == null)
                {
                    if (System.currentTimeMillis() < alarm)
                    {
                        assert(false);
                    }
                    // Fix the alteration we made to the draft state
                    for (Item item : stateAlterations)
                    {
                        assert(!availableItems.contains(item));
                        availableItems.add(item);
                    }
                    return null;
                }

                // Should we continue checking more picks?
                if (currentPlayer == activePlayer)
                {
                    if (betterPicks.contains(nextPick))
                    {
                        // Good, we can continue
                        betterPicks.remove(nextPick);
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
                    if (betterPicks.contains(nextPick))
                    {
                        // Bad, another player picked a better territory
                        makeThisPick = false;
                        break;
                    }
                }
            }

            // Fix the alteration we made to the draft state
            for (Item item : stateAlterations)
            {
                assert(!availableItems.contains(item));
                availableItems.add(item);
            }

            if (makeThisPick)
            {
                return pick;
            }
        }

        // Should only get here if firstCall == true and the one pick failed
        return null;
    }

    /**
     * Finds the n highest-valued items available and sorts them in non-increasing
     * order.  Ties are broken by whichever item has the greatest sum of the values
     * of the item to the other players.
     * @param availableItems The items available for picking
     * @param n The number of high valued items to find
     * @param player The player whose value we care about
     * @return The sorted highest-valued items for the passed in player.
     */
    private Item[] getNBestPicks(ArrayList<Item> availableItems, int n, int player)
    {
        Item[] topPicks = new Item[n];
        for (Item item : availableItems)
        {
            // Get the value of picking this item
            double value = item.m_values[player];

            // Find its rank in the current top picks
            int rank = n;
            while (rank > 0 && (topPicks[rank-1] == null || topPicks[rank - 1].m_values[player] <= value))
            {
                if (topPicks[rank-1] == null || topPicks[rank-1].m_values[player] < value)
                {
                    rank--;
                }
                else
                {
                    // We have a tie.  Time to break it.
                    double thisSum = 0;
                    double thatSum = 0;
                    for (int i = 0; i < Main.numPlayers; ++i)
                    {
                        if (i != player)
                        {
                            thisSum += item.m_values[i];
                            thatSum += topPicks[rank-1].m_values[i];
                        }
                    }

                    if (thisSum > thatSum)
                    {
                        // This item is better
                        rank--;
                    }
                    else
                    {
                        // The old item is better
                        break;
                    }
                }
            }

            // Adjust the top picks to make room for the new item
            for (int j = n - 2; j >= rank; --j)
            {
                topPicks[j+1] = topPicks[j];
            }

            // Put the item into place
            if (rank < n)
            {
                topPicks[rank] = item;
            }
        }

        return topPicks;
    }
}
