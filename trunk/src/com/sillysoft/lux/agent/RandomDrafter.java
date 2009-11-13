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
     * The number of wins for each player, for each draft
     */
    private static int[][] finalDraftWins;

    /**
     * After every this many drafts, we save the data to file
     */
    private static final int SAVE_AFTER_EVERY = 1;

    /**
     * The maximum number of games of data we want to save (so that the file
     * doesn't explode in size)
     */
    private static final int MAX_NUM_DRAFTS = 0;

    /**
     * The number of games we run with the same drafting
     */
    private static final int NUM_GAMES_PER_DRAFT = 1;

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
            finalDraftStates = new int[SAVE_AFTER_EVERY][countries.length];
        }
        if (finalDraftWins == null)
        {
            finalDraftWins = new int[SAVE_AFTER_EVERY][board.getNumberOfPlayers()];
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
    public int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        int pick = -1;
        if (numGamesPlayed % NUM_GAMES_PER_DRAFT == 0)
        {
            if (RESERVED_CONTINENT != -1)
            {
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
            }
            else
            {
                pick = unownedCountries.get((int)(Math.random()*unownedCountries.size()));
            }
        }
        else
        {
            // Follow the last draft
            for (Integer terr : unownedCountries)
            {
                if (finalDraftStates[(numGamesPlayed / NUM_GAMES_PER_DRAFT) % SAVE_AFTER_EVERY][terr] == ID)
                {
                    pick = terr;
                    break;
                }
            }
        }

        if (pick == -1)
        {
            assert(false);
        }

        finalDraftStates[(numGamesPlayed / NUM_GAMES_PER_DRAFT) % SAVE_AFTER_EVERY][pick] = ID;

        return pick;
    }

    // Record the data points
    @Override
    public String youWon()
    {
        // Record who won
        if (numGamesPlayed % NUM_GAMES_PER_DRAFT == 0)
        {
            // initialize winning percentages
            for (int i = 0; i < board.getNumberOfPlayers(); ++i)
            {
                finalDraftWins[(numGamesPlayed / NUM_GAMES_PER_DRAFT) % SAVE_AFTER_EVERY][i] = 0;
            }
        }
        for (int i = 0; i < board.getNumberOfPlayers(); ++i)
        {
            if (i == ID)
            {
                finalDraftWins[(numGamesPlayed / NUM_GAMES_PER_DRAFT) % SAVE_AFTER_EVERY][i]++;
                break;
            }
        }

        String message = super.youWon();

        if ((numGamesPlayed / NUM_GAMES_PER_DRAFT) <= MAX_NUM_DRAFTS && numGamesPlayed % (NUM_GAMES_PER_DRAFT * SAVE_AFTER_EVERY) == 0)
        {
            String data = "";

            for (int game = 0; game < SAVE_AFTER_EVERY; ++game)
            {
                int[] draftState = finalDraftStates[game];

                // Number of territories in each continent. for each player
                int[][] numInEachContinent = new int[board.getNumberOfPlayers()][board.getNumberOfContinents()];

                // List of enemy neighbours, for each player
                ArrayList<Integer>[] enemyNeighbours = new ArrayList[board.getNumberOfPlayers()];
                for (int i = 0; i < enemyNeighbours.length; ++i)
                {
                    enemyNeighbours[i] = new ArrayList<Integer>();
                }

                // Number of territories with an enemy neighbour, for each player
                int[] numTerrsWithEnemy = new int[board.getNumberOfPlayers()];

                // Number of friendly neighbours, for each player
                int[] numFriendlyNeighbours = new int[board.getNumberOfPlayers()];

                for (int terr = 0; terr < countries.length; ++terr)
                {
                    int owner = draftState[terr];

                    // Increment continent count
                    numInEachContinent[owner][countries[terr].getContinent()]++;

                    for (int neighbour : countries[terr].getAdjoiningCodeList())
                    {
                        // Add this as an enemy neighbour for the other players, if necessary
                        int neighbourOwner = draftState[neighbour];
                        if (neighbourOwner != owner && !enemyNeighbours[neighbourOwner].contains((Integer) terr))
                        {
                            enemyNeighbours[neighbourOwner].add((Integer) terr);
                        }

                        // Add to the friendly neighbour count
                        if (neighbourOwner == owner)
                        {
                            numFriendlyNeighbours[owner]++;
                        }
                    }

                    // Check if this territory has an enemy
                    for (int neighbour : countries[terr].getAdjoiningCodeList())
                    {
                        int neighbourOwner = draftState[neighbour];
                        if (neighbourOwner != owner)
                        {
                            numTerrsWithEnemy[owner]++;
                            break;
                        }
                    }
                }

                // Record the data points
                for (int player = 0; player < board.getNumberOfPlayers(); ++player)
                {
                    data += "" + player + ","; // Record seat position
                    for (int i = 0; i < numInEachContinent.length; ++i)
                    {
                        int adjustedPlayer = (i + player) % board.getNumberOfPlayers();
                        for (int j = 0; j < numInEachContinent[adjustedPlayer].length; ++j)
                        {
                            data += "" + numInEachContinent[adjustedPlayer][j] + ",";
                        }
                        data += "" + enemyNeighbours[adjustedPlayer].size() + "," + numTerrsWithEnemy[adjustedPlayer] + "," + numFriendlyNeighbours[adjustedPlayer] + ",";
                    }
                    data += "" + finalDraftWins[game][player] + "\n";
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
