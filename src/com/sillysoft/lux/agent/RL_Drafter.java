/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import java.util.Hashtable;
import java.util.ArrayList;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

/**
 *
 * @author Richard Gibson, Neesha Desai, Richard Zhao
 */
public class RL_Drafter extends SmartDrafter
{
    /**
     * The initial values in the heuristic function.  Note that this does
     * nothing if we are loading the heuristic from file.
     */
    final private double INITIAL_VALUE = 0.5;

    /**
     * The initial number of visits to each value in the heuristic function.
     * Note that this does nothing if we are loading the heuristic from file.
     */
    final private double INITIAL_WEIGHT = 1.0;

    /**
     * Exploration parameter
     */
    final private double c = 0.5;

    /**
     * Keeps track of which states were encountered in the draft, so that we
     * know which to update in the heuristic function once the game is over.
     * Note that this is a bit of a hack.  Since there is no youLose() function,
     * the winner has to take care of all heuristic updates at the end.
     */
    private static ArrayList<int[]>[] m_indicesToUpdate;

    /**
     * How often we capture the heuristic function by saving it to file
     */
    private static final int CAPTURE_AFTER_EVERY = 2500;

    /**
     * Keep track of the number of games we've played
     */
    protected static Integer m_numGamesPlayed = 0;

    /**
     * Constructor
     */
    public RL_Drafter()
    {
        super();
    }

    @Override
    public void setPrefs(int ID, Board board )
    {
        super.setPrefs(ID, board);

        if (m_indicesToUpdate == null)
        {
            m_indicesToUpdate = new ArrayList[board.getNumberOfPlayers()];
        }

        if (m_indicesToUpdate[ID] == null)
        {
            m_indicesToUpdate[ID] = new ArrayList<int[]>();
        }

        if (m_learnedHeuristic == null)
        {
            // Construct the heuristic, or grab it from file if it already exists
            try
            {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(HEURISTIC_DATA_FILENAME));
                m_learnedHeuristic = (Hashtable<ArrayList<Integer>,double[]>[])in.readObject();
                in.close();
            }
            catch (FileNotFoundException e)
            {
                // Create a new heuristic
                m_learnedHeuristic = new Hashtable[numCountries];
                for (int i = 0; i < numCountries; ++i)
                {
                    m_learnedHeuristic[i] = new Hashtable<ArrayList<Integer>,double[]>();
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

                                        m_learnedHeuristic[terr.getCode()].put(index, entry);
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

    // Update the heuristic function and save it to file
    @Override
    public String youWon()
    {
        String message = super.youWon();

        m_numGamesPlayed++;

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

                assert(m_learnedHeuristic[terr].containsKey(index));
                double[] hashTableEntry = m_learnedHeuristic[terr].get(index);
                double oldValue = hashTableEntry[0];
                double numVisits = hashTableEntry[1] + 1;

                // Average of values seen
                hashTableEntry[0] = oldValue + (1.0 / numVisits)*(newValue - oldValue);
                hashTableEntry[1] = numVisits;

                m_learnedHeuristic[terr].put(index, hashTableEntry);
            }

            m_indicesToUpdate[player].clear();
        }

        // Check if we should capture this heuristic function
        if ((m_numGamesPlayed % CAPTURE_AFTER_EVERY) == 0)
        {
            // Create the file name
            String fileName = HEURISTIC_DATA_FILENAME.substring(0, HEURISTIC_DATA_FILENAME.length() - 3); // Removes "dat"
            fileName += "" + m_numGamesPlayed + ".dat";

            // Check to make sure that the file does not already exist
            boolean exists = (new File(fileName)).exists();
            assert(!exists);

            // Save the heuristic to this file
            try
            {
                ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fileName));
                out.writeObject(m_learnedHeuristic);
                out.close();
            }
            catch (Exception e)
            {
                assert(false);
            }
        }

        return message;
    }

    @Override
    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        // Here, we are going to "almost" greedily pick the best looking terr,
        // but with an exploration term, and we always explore never-before tried
        // picks.

        int pick = -1;
        double valueOfPick = -Double.MAX_VALUE;
        ArrayList<Integer> index = null;

        Hashtable<Integer,double[]> values = new Hashtable<Integer,double[]>(unownedCountries.size());
        Hashtable<Integer,ArrayList<Integer>> indexes = new Hashtable<Integer,ArrayList<Integer>>(unownedCountries.size());

        // First, calculate how many times we have been to this state
        int numVisits = 0;
        for (int terr : unownedCountries)
        {
            ArrayList<Integer> tempIndex = getTerritoryIndex(terr, draftState, ID);
            indexes.put(terr, tempIndex);
            assert(m_learnedHeuristic[terr].containsKey(tempIndex));
            double[] terrValue = m_learnedHeuristic[terr].get(tempIndex);
            values.put(terr, terrValue);
            numVisits += (int)(terrValue[1] - INITIAL_WEIGHT);
        }

        // Now find the best valued territory
        for (int terr : unownedCountries)
        {
            double[] terrValue = values.get(terr);
            if (terrValue[1] == INITIAL_WEIGHT)
            {
                // Explore a never-chosen option
                pick = terr;
                valueOfPick = Double.MAX_VALUE;
                index = indexes.get(terr);
            }
            else
            {
                double value = terrValue[0] + c * Math.sqrt(Math.log(numVisits) / (terrValue[1] - INITIAL_WEIGHT));
                if (value > valueOfPick)
                {
                    // Choose this one
                    pick = terr;
                    valueOfPick = value;
                    index = indexes.get(terr);
                }
            }
        }

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
