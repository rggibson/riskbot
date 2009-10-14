/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import java.util.Hashtable;
import java.util.ArrayList;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

/**
 *
 * @author Richard Gibson, Neesha Desai, Richard Zhao
 */
public class KthBestPickRL_Drafter extends KthBestPickDrafter
{
    /**
     * The file to store the heuristic function.
     */
    private static String HEURISTIC_DATA_FILENAME = "C:\\Users\\Richard\\Documents\\NetBeansProjects\\LuxSDK\\LuxSDK\\objectData\\learnedHeuristic";

    /**
     * The initial values in the heuristic function
     */
    final private double INITIAL_VALUE = 0.5;

    /**
     * The initial number of visits to each value in the heuristic function.
     * Note that this does nothing if we are loading the heuristic from file.
     */
    final private double INITIAL_WEIGHT = 1;

    /**
     * The Hashtables, one for each territory, that will store the learned
     * heuristic
     */
    public static Hashtable<ArrayList<Integer>,double[]>[] m_heuristic;

    /**
     * Keeps track of which states were encountered in the draft, so that we
     * know which to update in the heuristic function once the game is over.
     * Note that this is a bit of a hack.  Since there is no youLose() function,
     * the winner has to take care of all heuristic updates at the end.
     */
    private static ArrayList<int[]>[] m_indicesToUpdate;

    /**
     * Constructor
     */
    public KthBestPickRL_Drafter()
    {
        super();

        assert(HEURISTIC_FUNCTION == KthBestPickDrafter.Heuristic.LEARNED);
    }

    @Override
    public void setPrefs(int ID, Board board )
    {
        super.setPrefs(ID, board);

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

        if (m_indicesToUpdate == null)
        {
            m_indicesToUpdate = new ArrayList[board.getNumberOfPlayers()];
        }

        if (m_indicesToUpdate[ID] == null)
        {
            m_indicesToUpdate[ID] = new ArrayList<int[]>();
        }

        if (m_heuristic == null)
        {
            // Construct the heuristic, or grab it from file if it already exists
            try
            {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(HEURISTIC_DATA_FILENAME));
                m_heuristic = (Hashtable<ArrayList<Integer>,double[]>[])in.readObject();
                in.close();
            }
            catch (FileNotFoundException e)
            {
                // Create a new heuristic
                m_heuristic = new Hashtable[numCountries];
                for (int i = 0; i < numCountries; ++i)
                {
                    m_heuristic[i] = new Hashtable<ArrayList<Integer>,double[]>();
                }

                // First, we will need the number of countries in each continent
                int[] numTerrsInCont = new int[board.getNumberOfContinents()];
                for (Country terr : countries)
                {
                    numTerrsInCont[terr.getContinent()]++;
                }

                for (Country terr : countries)
                {
                    // Initialize the entries in the heuristic

                    // Some numbers we will need for indexing
                    int numNeighbours = terr.getNumberNeighbors();
                    Country[] neighbours = terr.getAdjoiningList();
                    int numNeighboursInCont = 0;
                    for (Country neighbour : neighbours)
                    {
                        if (terr.getContinent() == neighbour.getContinent())
                        {
                            numNeighboursInCont++;
                        }
                    }

                    // Begin indexing:
                    // First, number of friendly neighbours
                    for (int numFriends = 0; numFriends <= numNeighbours; ++numFriends)
                    {
                        // Next, number of enemy neighbours
                        for (int numEnemies = 0; numEnemies <= numNeighbours - numFriends; ++numEnemies)
                        {
                            // Number of territories in continent we own
                            int maxNumFriendsInCont = numTerrsInCont[terr.getContinent()] - 1; // Can't include this terr
                            maxNumFriendsInCont -= Math.max(0, numEnemies - (numNeighbours - numNeighboursInCont));

                            int minNumFriendsInCont = Math.max(0, numFriends - (numNeighbours - numNeighboursInCont));

                            for (int numFriendsInCont = minNumFriendsInCont; numFriendsInCont <= maxNumFriendsInCont; ++numFriendsInCont)
                            {
                                // Number of territories in continent owned by enemies
                                int maxNumEnemiesInCont = numTerrsInCont[terr.getContinent()] - 1 - numFriendsInCont;
                                int minNumEnemiesInCont = Math.max(0, numEnemies - (numNeighbours - numNeighboursInCont));

                                for (int numEnemiesInCont = minNumEnemiesInCont; numEnemiesInCont <= maxNumEnemiesInCont; ++numEnemiesInCont)
                                {
                                    // Max number of territories in continent owned by one enemy
                                    int minNumEnemyInCont = numEnemiesInCont == 0 ? 0 : ((numEnemiesInCont - 1) / (board.getNumberOfPlayers() - 1)) + 1;
                                    int maxNumEnemyInCont = numEnemiesInCont;

                                    for (int numEnemyInCont = minNumEnemyInCont; numEnemyInCont <= maxNumEnemyInCont; ++numEnemyInCont)
                                    {
                                        // Entry
                                        ArrayList<Integer> index = new ArrayList<Integer>(5);
                                        index.add(numFriends);
                                        index.add(numEnemies);
                                        index.add(numFriendsInCont);
                                        index.add(numEnemiesInCont);
                                        index.add(numEnemyInCont);

                                        double[] entry = { INITIAL_VALUE, INITIAL_WEIGHT};

                                        m_heuristic[terr.getCode()].put(index, entry);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                assert(false);
            }
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
    @Override
    protected double getValueOfTerr(int terr, int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer)
    {
        ArrayList<Integer> index = getTerritoryIndex(terr, draftState, activePlayer);

        if (!m_heuristic[terr].containsKey(index))
        {
            assert(false);
        }
        assert(m_heuristic[terr].containsKey(index));

        double[] value = m_heuristic[terr].get(index);

        return value[0];
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

    // Update the heuristic function and save it to file
    @Override
    public String youWon()
    {
        String message = super.youWon();

        // Make the updates to the heuristic function
        for (int player = 0; player < m_indicesToUpdate.length; ++player)
        {
            if (m_indicesToUpdate[player] == null)
            {
                // This player is not an RL drafter
                continue;
            }

            double newValue = (player == ID) ? 1.0 : 0.0;

            for (int[] entry : m_indicesToUpdate[player])
            {
                // Retrieve the index and territory from the entry (see the hack
                // in getPick())
                int terr = entry[0];
                ArrayList<Integer> index = new ArrayList<Integer>(entry.length - 1);
                for (int i = 0; i < entry.length - 1; ++i)
                {
                    index.add(entry[i+1]);
                }

                assert(m_heuristic[terr].containsKey(index));
                double[] hashTableEntry = m_heuristic[terr].get(index);
                double oldValue = hashTableEntry[0];
                double numVisits = hashTableEntry[1] + 1;

                // Average of values seen
                hashTableEntry[0] = oldValue + (1.0 / numVisits)*(newValue - oldValue);
                hashTableEntry[1] = numVisits;

                m_heuristic[terr].put(index, hashTableEntry);
            }

            m_indicesToUpdate[player].clear();
        }

        // Now save the heuristic function to file
        try
        {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(HEURISTIC_DATA_FILENAME));
            out.writeObject(m_heuristic);
            out.close();
        }
        catch (Exception e)
        {
            assert(false);
        }

        return message;
    }

    @Override
    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        int pick = super.getPick(draftState, unownedCountries);

        // TODO: rggibson - Store the index somewhere so we don't have to recompute it here
        ArrayList<Integer> index = getTerritoryIndex(pick, draftState, ID);

        // Total hack so that we can store the territory with the index
        int[] entry = new int[index.size() + 1];
        entry[0] = pick;
        for (int i = 0; i < index.size(); ++i)
        {
            entry[i+1] = index.get(i);
        }

        m_indicesToUpdate[ID].add(entry);

        return pick;
    }
}
