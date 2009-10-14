/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.io.ObjectInputStream;
import java.io.FileInputStream;

/**
 *
 * @author Richard Gibson, Neesha Desai, Richard Zhao
 */
public class KthBestPickDrafter extends SmartDrafter
{
    /**
     * The file to store the heuristic function.
     */
    protected static String HEURISTIC_DATA_FILENAME = "C:\\Users\\Richard\\Documents\\NetBeansProjects\\LuxSDK\\LuxSDK\\objectData\\learnedHeuristic";

    /**
     * The maximum number of picks that we will consider looking ahead
     * for the kthBestPick algorithm.
     */
    final protected int MAX_NUM_PICKS_CONSIDERED = 5;

    /**
     * The heuristic function to use
     */
    final protected Heuristic HEURISTIC_FUNCTION = Heuristic.LEARNED;

    /**
     * The Hashtables, one for each territory, that will store the learned
     * heuristic (if we are using the learned one)
     */
    protected static Hashtable<ArrayList<Integer>,double[]>[] m_learnedHeuristic;

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

    @Override
    public void setPrefs(int ID, Board board )
    {
        super.setPrefs(ID, board);

        if (HEURISTIC_FUNCTION == Heuristic.LEARNED && m_learnedHeuristic == null)
        {
            // Load the heursitic function up

            // Edit the filename depending on how many players are playing
            int numPlayers = board.getNumberOfPlayers();
            if (!HEURISTIC_DATA_FILENAME.contains("" + numPlayers))
            {
                 HEURISTIC_DATA_FILENAME += "." + numPlayers;
            }

            // Edit the filename depending on the map being used
            if (numCountries == 15 && !HEURISTIC_DATA_FILENAME.contains("15"))
            {
                HEURISTIC_DATA_FILENAME += ".15.dat";
            }
            else if (numCountries == 42 && !HEURISTIC_DATA_FILENAME.contains("42"))
            {
                HEURISTIC_DATA_FILENAME += ".42.dat";
            }

            // Grab it from file
            try
            {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(HEURISTIC_DATA_FILENAME));
                m_learnedHeuristic = (Hashtable<ArrayList<Integer>,double[]>[])in.readObject();
                in.close();
            }
            catch (Exception e)
            {
                assert(false);
            }
        }
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
                // TODO: rggibson - If the current player is the active player,
                // we could just consider picking from our list of better picks
                // to perhaps reduce the amount of recursion.  However, this could
                // end up being both complicated and more work if we want to consider
                // EVERY possible choice from this list.
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
     * Returns the top ranked picks, in order (i.e. pick at index 0 is best)
     * @param draftState The current state of the draft
     * @param unownedCountries The available picks
     * @param activePlayer The player whose turn it is to pick
     * @param numPicksToConsider The number of top picks to find
     * @return The top picks, which has length numPicksToConsider
     */
    private int[] getTopPicks(int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer, int numPicksToConsider)
    {
        int[] topPicks = new int[numPicksToConsider];
        double[] valuesOfTopPicks = new double[numPicksToConsider];
        for (int i = 0; i < numPicksToConsider; ++i)
        {
            // Initialize
            topPicks[i] = -1;
            valuesOfTopPicks[i] = -Double.MAX_VALUE;
        }
        for (int terr = 0; terr < draftState.length; ++terr)
        {
            // Only consider unowned territories
            if (draftState[terr] != -1)
            {
                assert(!unownedCountries.contains((Integer) terr));
                continue;
            }

            // Get the value of this unowned country
            double valueOfTerr = getValueOfTerr(terr, draftState, unownedCountries, activePlayer);

            // Find its rank in the current top picks
            int rank = numPicksToConsider;
            while (rank > 0 && valuesOfTopPicks[rank - 1] < valueOfTerr)
            {
                rank--;
            }

            // Adjust the top picks to make room for the new territory
            for (int j = numPicksToConsider - 2; j >= rank; --j)
            {
                topPicks[j+1] = topPicks[j];
                valuesOfTopPicks[j+1] = valuesOfTopPicks[j];
            }

            // Put the territory into place
            if (rank < numPicksToConsider)
            {
                topPicks[rank] = terr;
                valuesOfTopPicks[rank] = valueOfTerr;
            }
        }

        return topPicks;
    }

    /**
     * Calls the heuristic function to get the value of the passed in territory
     * @param terr The territory whose value we want
     * @param draftState The state of the draft
     * @param unownedCountries The countries still available to be picked
     * @param activePlayer The player whose turn it is to pick
     * @return The value of the passed in territory
     */
    protected double getValueOfTerr(int terr, int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer)
    {
        double valueOfTerr = 0.0;

        switch(HEURISTIC_FUNCTION)
        {
            case MAX_N_MC:
                // Call MaxN-MC to get the rank of each pick
                int depth = MaxN_MC_Drafter.calculateMaxNSearchDepth(unownedCountries.size());
                assert(unownedCountries.contains((Integer) terr));
                draftState[terr] = activePlayer;
                unownedCountries.remove((Integer) terr);

                double[] values = MaxN_MC_Drafter.maxNMC(draftState, unownedCountries, (activePlayer + 1) % board.getNumberOfPlayers(), depth, board.getNumberOfPlayers(), board);

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
     * Retrieves the index into the heuristic function for the current state
     * of the draft.
     * @param terr The territory whose value we need
     * @param draftState The state of the draft
     * @param activePlayer The player whose turn it is to pick
     * @return The index into the heuristic function for retrieving the value of terr
     */
    protected ArrayList<Integer> getTerritoryIndex(int terr, int[] draftState, int activePlayer)
    {
        int numFriends = 0;
        int numEnemies = 0;
        int numFriendsInCont = 0;
        int numEnemiesInCont = 0;
        int[] enemiesInCont = new int[board.getNumberOfPlayers()];

        // Number of friendly and enemy neighbours
        int[] neighbours = countries[terr].getAdjoiningCodeList();
        for (int i = 0; i < neighbours.length; ++i)
        {
            int neighbourTerr = neighbours[i];
            int owner = draftState[neighbourTerr];

            if (owner == activePlayer)
            {
                numFriends++;
            }
            else if (owner >= 0)
            {
                numEnemies++;
            }
        }

        // Get all the countries in the continent other than terr
        int continent = countries[terr].getContinent();
        ArrayList<Integer> terrsInCont = new ArrayList<Integer>();
        for (Country country : countries)
        {
            if (country.getContinent() == continent && country.getCode() != terr)
            {
                terrsInCont.add(country.getCode());
            }
        }

        // Number of friends and enemies in continent
        for (int i = 0; i < terrsInCont.size(); ++i)
        {
            int contTerr = terrsInCont.get(i);
            int owner = draftState[contTerr];

            if (owner == activePlayer)
            {
                numFriendsInCont++;
            }
            else if (owner >= 0)
            {
                numEnemiesInCont++;
                enemiesInCont[owner]++;
            }
        }

        // Max number of enemies in continent owned by one player
        int numEnemyInCont = enemiesInCont[0];
        for (int i = 1; i < enemiesInCont.length; ++i)
        {
            if (enemiesInCont[i] > numEnemyInCont)
            {
                numEnemyInCont = enemiesInCont[i];
            }
        }

        // The index
        ArrayList<Integer> index = new ArrayList<Integer>(5);
        index.add(numFriends);
        index.add(numEnemies);
        index.add(numFriendsInCont);
        index.add(numEnemiesInCont);
        index.add(numEnemyInCont);

        return index;
    }
}
