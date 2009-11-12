/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import java.util.ArrayList;
import com.sillysoft.lux.*;

/**
 * The KthBestPick algorithm, using UCT as the heuristic.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class KthBestPickWithUct extends SmartDrafter
{
    /**
     * Factors in to how many roll outs we do, according to how much time we have.
     * The bigger the number, the fewer number of roll outs we'll perform.
     */
    final protected int MILLSECS_PER_ROLL_OUT = 4;

    /**
     * The evaluation function to use
     */
    protected EvaluationFunction m_evalFunc = EvaluationFunction.LIN_REG_NOM_FEATS;

    /**
     * The maximum number of picks to consider
     * Set to -1 to do as many in time limit
     */
    final protected int MAX_PICKS_CONSIDERED = -1;

    /**
     * Current number of simulations we are allowing
     * This will be set to something else if we are obeying time limit (see above parameter)
     */
    protected int m_numSimulations = 0;

    /**
     * Need this if we are using UCT as heuristic or opponent model
     */
    protected UCT_Drafter2 m_uctDrafter;

    /**
     * True if we assume we know what algorithms our opponents are using.
     * Note that if we set this to true, we need to carefully set the player
     * names in the Lux main menu:
     * UCT_Drafter(2): must contain "UCT"
     * GreedyDrafter: must contain "Greedy"
     * RandomDrafter: must contain "Random"
     * KthBestPickUct: must contain "KthBestPick"
     */
    protected final boolean USE_ORACLE_MODELS = true;

    /**
     * Whether we are selfish or not (we assume the opponents' selfishnesses match
     * our own).
     */
    final boolean SELFISH = false;

    // Constructor
    public KthBestPickWithUct()
    {
        super();
    }

    @Override
    public void setPrefs(int ID, Board board)
    {
        super.setPrefs(ID, board);

        if (SELFISH)
        {
            m_uctDrafter = (UCT_Drafter2) board.getAgentInstance("UCT_Drafter2 - Selfish");
        }
        else
        {
            m_uctDrafter = (UCT_Drafter2) board.getAgentInstance("UCT_Drafter2");
        }
        m_uctDrafter.setPrefs(ID, board);
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

    public int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        long alarm = System.currentTimeMillis() + PICK_TIME_IN_MILLIS_PER_UNOWNED_TERR*unownedCountries.size();
        if (MAX_PICKS_CONSIDERED != -1)
        {
            // For debugging
            alarm = Long.MAX_VALUE;
        }

        int terr = -1;
        int nextPick = -1;
        int maxNumPicksConsidered = 1;

        if (MAX_PICKS_CONSIDERED == -1)
        {
            m_numSimulations = PICK_TIME_IN_MILLIS_PER_UNOWNED_TERR*unownedCountries.size() / MILLSECS_PER_ROLL_OUT;
        }

        // Synchronize the uct drafter's root node
        m_uctDrafter.updateRoot(draftState);

        // Run simulations
        for (int i = 0; i < m_numSimulations; ++i)
        {
            m_uctDrafter.runUctSimulation(m_uctDrafter.getRoot(), draftState);
        }

        // With the time constraint, we are going to iterate over the maximum number
        // of picks to consider
        do
        {
            nextPick = kthBestPickWithUct(draftState, unownedCountries, ID, maxNumPicksConsidered, alarm, true, m_uctDrafter.getRoot());
            maxNumPicksConsidered++;

            if (nextPick != -1)
            {
                terr = nextPick;
            }

            // How many picks do we have left in the draft?
            int numPicksToConsider = (int)Math.ceil((double)unownedCountries.size() / board.getNumberOfPlayers());

//            System.out.println("Num picks to consider = " + numPicksToConsider);

            if (numPicksToConsider < maxNumPicksConsidered ||
                    (MAX_PICKS_CONSIDERED != -1 && MAX_PICKS_CONSIDERED < maxNumPicksConsidered))
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

        while (System.currentTimeMillis() < alarm)
        {
            Node2 node = m_uctDrafter.getRoot().getChildren().get(terr);
            m_uctDrafter.runUctSimulation(node, node.getDraftState());
        }

//        System.out.println("\n\nMaxNumPicksConsidered = " + (maxNumPicksConsidered - 1) + "\n");

        return terr;
    }

    /**
     * The KthBestPick algorithm, using UCT as the heuristic
     * @param draftState The current state of the draft
     * @param unownedCountries A list of all countries to pick from
     * @param activePlayer The player whose turn it is to pick
     * @param maxNumPicksToConsider The cap for how badly ranked a territory we will consider
     * @param alarm If the current time exceeds the alarm, it is time to stop and return -1.
     * @param firstCall True if this is the first (i.e. non-recursive) call
     * @return The territory to pick, as given by the KthBestPick algorithm
     */
    private int kthBestPickWithUct(int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer, int numPicksToConsider, long alarm, boolean firstCall, Node2 node)
    {
        // Rank the available picks
        assert node != null : "Error: null node!";
        for (int i = node.getNumVisits(); i < m_numSimulations; ++i)
        {
            m_uctDrafter.runUctSimulation(node, draftState);
        }
        int[] topPicks = m_uctDrafter.getNBestPicks(node, numPicksToConsider);

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
            Node2 child = node;

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
                assert child.getChildren().containsKey(nextPick) : "Error: Illegal child of current node!";
                Node2 nextChild = child.getChildren().get(nextPick);
                if (child == null)
                {
                    nextChild = new Node2(draftState, currentPlayer);
                    child.addChild(nextPick, nextChild);
                }
                child = nextChild;

                // Find the next pick according to the opponent model
                if (USE_ORACLE_MODELS)
                {
                    String currentPlayerName = board.getPlayerName(currentPlayer);
                    if (currentPlayerName.contains("KthBestPick"))
                    {
                        nextPick = kthBestPickWithUct(draftState, unownedCountries, currentPlayer, numPicksLeft, alarm, false, child);
                    }
                    else if (currentPlayerName.contains("Random"))
                    {
                        nextPick = unownedCountries.get((int)(Math.random()*unownedCountries.size()));
                    }
                    else if (currentPlayerName.contains("Greedy"))
                    {
                        // TODO: We can model whether a greedy opponent is selfish or not
                        nextPick = getGreedyPick(draftState, unownedCountries, currentPlayer, SELFISH, m_evalFunc);
                    }
                    else if (currentPlayerName.contains("UCT"))
                    {
                        for (int i = child.getNumVisits(); i < m_numSimulations; ++i)
                        {
                            m_uctDrafter.runUctSimulation(child, draftState);
                        }
                        int[] bestPick = m_uctDrafter.getNBestPicks(child, 1);
                        nextPick = bestPick[0];
                    }
                    else
                    {
                        assert false : "Unrecognized opponent";
                    }
                }
                else
                {
                    // Not using oracle models
                    nextPick = kthBestPickWithUct(draftState, unownedCountries, currentPlayer, numPicksLeft, alarm, false, child);
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
}
