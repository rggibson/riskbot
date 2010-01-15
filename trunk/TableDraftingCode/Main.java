/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * This program runs a table drafting game, where the value of each item in the
 * table is randomly set.  The purpose of this is to compare UCT, Kth, MaxN, and
 * Greedy drafting strategies in a simple drafting game.
 * @author Richard
 */
public class Main
{
    public static final int NUM_ITEMS = 24; // 4, 12, 20, 32, 40, 60
    public static final int NUM_ROUNDS = 1;
    public static final int NUM_VALUES = 100;
    public static int numPlayers = -1; // This is changed in the code
    public static Random rand;
    public static final int RAND_SEED = 1337; // Change this if you like
    public static final long TIME_LIMIT = 133; // Actual time limit = TIME_LIMIT * Math.ceil(numItemsLeft / numPlayers)
    public static int[] kthPickCounts = new int[10];

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        // Load the players
        Agent[] agents = {  new GreedyAgent(),
                            new UctAgent(),
                            new KthBestPickAgent()
                            //new MaxNAgent()
        };
        
        rand = new Random(RAND_SEED);

        numPlayers = agents.length;

        // Lazy factorial calculation
        int[][] orderings = null;
        switch(agents.length)
        {
            case 1:
            {
                int[][] perms = { {0} };
                orderings = perms;
                break;
            }

            case 2:
            {
                int[][] perms = {   {0,1},
                                    {1,0}   };
                orderings = perms;
                break;
            }

            case 3:
            {
                int[][] perms = {   {0,1,2}, {0,2,1}, {1,2,0},
                                    {1,0,2}, {2,0,1}, {2,1,0}   };
                orderings = perms;
                break;
            }

            case 4:
            {
                int[][] perms = {   {0,1,2,3}, {0,2,1,3}, {1,2,0,3},
                                    {1,0,2,3}, {2,0,1,3}, {2,1,0,3},
                                    {0,1,3,2}, {0,2,3,1}, {1,2,3,0},
                                    {1,0,3,2}, {2,0,3,1}, {2,1,3,0},
                                    {0,3,1,2}, {0,3,2,1}, {1,3,2,0},
                                    {1,3,0,2}, {2,3,0,1}, {2,3,1,0},
                                    {3,0,1,2}, {3,0,2,1}, {3,1,2,0},
                                    {3,1,0,2}, {3,2,0,1}, {3,2,1,0}};
                orderings = perms;
                break;
            }

            default:
            {
                assert false : "Error: Unsupported number of players!";
                break;
            }
        }

        System.out.println(Arrays.toString(agents));
        
        for (int round = 0; round < NUM_ROUNDS; ++round)
        {
            // Scoreboard for this round of matches
            double[] scores = new double[agents.length];

            // Create the table for the game
            Item[] items = new Item[NUM_ITEMS];
            for (int i = 0; i < NUM_ITEMS; ++i)
            {
                double[] values = new double[agents.length];
                for (int v = 0; v < values.length; ++v)
                {
                    values[v] = (double)rand.nextInt(NUM_VALUES) / NUM_VALUES;
                }
                items[i] = new Item(values);
            }
//            System.out.println("Items:\n" + Arrays.toString(items));

            for (int game = 0; game < orderings.length; ++game)
            {
                Agent[] orderedAgents = new Agent[agents.length];
                for (int i = 0; i < orderedAgents.length; ++i)
                {
                    orderedAgents[i] = agents[orderings[game][i]];
                    orderedAgents[i].setID(i);
                }

//                System.out.println(Arrays.toString(orderedAgents));

                // Play the game
                ArrayList<Item> availableItems = new ArrayList<Item>();
                for (Item item : items)
                {
                    availableItems.add(item);
                }

                double[] scoresThisMatch = new double[orderedAgents.length];
                int currentPlayer = 0;
                while (!availableItems.isEmpty())
                {
                    Item pick = orderedAgents[currentPlayer].makePick(availableItems);
                    scoresThisMatch[currentPlayer] += pick.m_values[currentPlayer];
    //                System.out.println(orderedAgents[currentPlayer] + ": " + pick);
                    availableItems.remove(pick);
                    currentPlayer++;
                    currentPlayer %= orderedAgents.length;
                }

                // End of game; log results and print if you like
                for (int player = 0; player < agents.length; ++player)
                {
                    for (int orderedPlayer = 0; orderedPlayer < agents.length; ++orderedPlayer)
                    {
                        if (agents[player] == orderedAgents[orderedPlayer])
                        {
                            scores[player] += scoresThisMatch[orderedPlayer];
                            break;
                        }
                    }
                }

    //            System.out.println(Arrays.toString(scoresThisRound));
            }

            System.out.println(Arrays.toString(scores));
            System.out.println("KthPickCounts: " + Arrays.toString(kthPickCounts));
        } // foreach round
    } // main

}
