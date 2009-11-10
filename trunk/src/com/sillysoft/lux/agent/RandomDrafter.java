/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.FileWriter;

/**
 * This drafter simply picks territories at random.  His main purpose is to
 * collect data so that we can stick it in to Weka and get an evaluation
 * (or objective) function for the draft.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class RandomDrafter extends SmartDrafter
{
    /**
     * The file to store the heuristic function.
     */
    protected static String DATA_FILENAME = "C:\\Users\\Richard\\Documents\\NetBeansProjects\\LuxSDK\\LuxSDK\\objectData\\randomDraftData6.arff";

    /**
     * Will remember the final state of the draft so that when the full game
     * is over, we can label the data points appropriately as eventual wins or
     * losses.
     */
    private static int[][] finalDraftStates;

    /**
     * After every this many games, we save the data to file
     */
    private static final int SAVE_AFTER_EVERY = 250;

    /**
     * The maximum number of games of data we want to save (so that the file
     * doesn't explode in size)
     */
    private static final int MAX_NUM_GAMES = 0;

    /**
     * Continent which we want to reserve some picks for player 0
     */
    private static final int RESERVED_CONTINENT = -1; // No reserves

    /**
     * Exact number of territories we want player 0 to own from the above cont
     */
     private static final int NUM_RESERVED_TERRS = 0;
     
     /**
      * The reserved territories for player 0
      */
     private static ArrayList<Integer> reservedTerrs;
     
     /**
      * The territories which player 0 is not allowed to pick from
      */
     private static ArrayList<Integer> antiReservedTerrs;

    /**
     * Constructor
     */
    public RandomDrafter()
    {
        super();
    }

    @Override
    public String name()
    {
        return "RandomDrafter";
    }

    @Override
    public void setPrefs(int ID, Board board )
    {
        super.setPrefs(ID, board);

        // Create the final draft state, if necessary
        if (finalDraftStates == null)
        {
            finalDraftStates = new int[SAVE_AFTER_EVERY][countries.length + 1];
        }

        // Initialize reserved territories
        if (reservedTerrs == null)
        {
            reservedTerrs = new ArrayList<Integer>();
        }
        if (antiReservedTerrs == null)
        {
            antiReservedTerrs = new ArrayList<Integer>();
        }
        reservedTerrs.clear();
        antiReservedTerrs.clear();

        if (RESERVED_CONTINENT != -1)
        {
            // Grab all the territories from this continent
            ArrayList<Integer> countriesInCont = new ArrayList<Integer>();
            for (Country country : countries)
            {
                if (country.getContinent() == RESERVED_CONTINENT)
                {
                    countriesInCont.add((Integer) country.getCode());
                }
            }

            // Pick the reserved ones at random
            for (int i = 0; i < NUM_RESERVED_TERRS; ++i)
            {
                if (countriesInCont.isEmpty())
                {
                    assert(false);
                }
                Integer terr = countriesInCont.remove((int)(Math.random()*countriesInCont.size()));
                reservedTerrs.add(terr);
            }
            while (!countriesInCont.isEmpty())
            {
                antiReservedTerrs.add(countriesInCont.remove(0));
            }
        }
    }

    /**
     * Just pick randomly.
     * @param draftState Who owns what
     * @param unownedCountries The territories left to be picked
     * @return The index of the territory to pick
     */
    @Override
    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        int pick = -1;
        if (ID == 0)
        {
            // Special reserved territories
            if (reservedTerrs.isEmpty())
            {
                // Just make sure that we don't pick from the anti reserved list
                ArrayList<Integer> availablePicks = new ArrayList<Integer>();
                for (Integer terr : unownedCountries)
                {
                    if (!antiReservedTerrs.contains(terr))
                    {
                        availablePicks.add(terr);
                    }
                }
                if (availablePicks.isEmpty())
                {
                    assert(false);
                }
                pick = availablePicks.get((int)(Math.random()*availablePicks.size()));
            }
            else
            {
                pick = reservedTerrs.remove(0);
            }
        }
        else
        {
            // Need to check if we have to pick from the anti-reserved list
            int maxNumPicksLeftForZero = unownedCountries.size() / board.getNumberOfPlayers() + 1;
            int numAvailablePicksForZero = unownedCountries.size() - antiReservedTerrs.size();

            if (numAvailablePicksForZero < maxNumPicksLeftForZero)
            {
                assert(false);
            }
            
            if (numAvailablePicksForZero == maxNumPicksLeftForZero)
            {
                // Must choose from anti list
                pick = antiReservedTerrs.remove((int)(Math.random()*antiReservedTerrs.size()));
            }
            else
            {
                // Pick from anywhere
                pick = unownedCountries.get((int)(Math.random()*unownedCountries.size()));
                if (antiReservedTerrs.contains((Integer) pick))
                {
                    antiReservedTerrs.remove((Integer) pick);
                }
            }
        }

        if (pick == -1)
        {
            assert(false);
        }

        finalDraftStates[numGamesPlayed % SAVE_AFTER_EVERY][pick] = ID;

        return pick;
    }

    // Record the data points
    @Override
    public String youWon()
    {
        String message = super.youWon();

        // Record who won
        finalDraftStates[numGamesPlayed % SAVE_AFTER_EVERY][countries.length] = ID;

        if (numGamesPlayed <= MAX_NUM_GAMES && numGamesPlayed % SAVE_AFTER_EVERY == 0)
        {
            String data = "";

            for (int game = 0; game < SAVE_AFTER_EVERY; ++game)
            {
                for (int player = 0; player < board.getNumberOfPlayers(); ++player)
                {
                    if (RESERVED_CONTINENT != -1 && player > 0)
                    {
                        // We only want to record for player 0 when we reserve terrs
                        break;
                    }

                    // Number of territories in each continent
                    int[] numInEachContinent = new int[board.getNumberOfContinents()];
                    for (int i = 0; i < numInEachContinent.length; ++i)
                    {
                        numInEachContinent[i] = 0;
                    }

                    // List of enemy neighbours
                    ArrayList<Integer> enemyNeighbours = new ArrayList<Integer>();

                    // Number of territories with an enemy neighbour
                    int numTerrsWithEnemy = 0;

                    for (int terr = 0; terr < countries.length; ++terr)
                    {
                        if (finalDraftStates[game][terr] != player)
                        {
                            // Only are concerned with territories this player
                            // owned at the end of the draft.
                            continue;
                        }

                        // Increment continent count
                        numInEachContinent[countries[terr].getContinent()]++;

                        // Add any enemy neighbours we haven't seen
                        for (int neighbour : countries[terr].getAdjoiningCodeList())
                        {
                            if (finalDraftStates[game][neighbour] != player &&
                                    !enemyNeighbours.contains((Integer) neighbour))
                            {
                                enemyNeighbours.add((Integer) neighbour);
                            }
                        }

                        // Check if this territory has an enemy
                        for (int neighbour : countries[terr].getAdjoiningCodeList())
                        {
                            if (finalDraftStates[game][neighbour] != player)
                            {
                                numTerrsWithEnemy++;
                                break;
                            }
                        }
                    }

                    // Record the data point
                    for (int i = 0; i < numInEachContinent.length; ++i)
                    {
                        data += "" + numInEachContinent[i] + ",";
                    }
                    data += "" + enemyNeighbours.size() + "," + numTerrsWithEnemy + ",";
                    if (finalDraftStates[game][countries.length] == player)
                    {
                        // A win
                        data += "1\n";
                    }
                    else
                    {
                        // A loss
                        data += "0\n";
                    }
                }
            }

            // Finally, write the data to file if it is time to do so
            try
            {
                PrintWriter out = new PrintWriter(new FileWriter(DATA_FILENAME, true));
                out.print(data);
                out.close();
                data = "";
            }
            catch (Exception e)
            {
                assert(false);
            }
        }

        System.out.println("\n\n\n\nHave now played " + numGamesPlayed + " games\n\n\n\n");

        return message;
    }
}
