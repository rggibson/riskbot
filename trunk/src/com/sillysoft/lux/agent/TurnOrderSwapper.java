/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;

import java.io.BufferedWriter;
//import java.io.File;
//import java.io.FileWriter;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.FileWriter;
//import java.util.Iterator;
import java.util.Arrays;
/**
 * This drafter cycles through all possible turn orders each game.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class TurnOrderSwapper extends SmartDrafter
{
    protected static final String FANTASY_SCORES_DATA_FILENAME = "C:\\Users\\Richard\\Documents\\NetBeansProjects\\LuxSDK\\LuxSDK\\objectData\\fantasyScores.dat";
    protected static final String ACTUAL_SCORES_DATA_FILENAME = "C:\\Users\\Richard\\Documents\\NetBeansProjects\\LuxSDK\\LuxSDK\\objectData\\actualScores.dat";

    protected static final boolean PRINT_FANTASY_SCORES = true;
    protected static final boolean PRINT_ACTUAL_SCORES = true;

    /**
     * The names of the players that will be drafting
     */
    protected static final String[] DRAFTERS = {"RandomDrafter",
                                                "UCT_Drafter2",
                                                "KthBestPickWithUct"  };

    /**
     * The number of orderings we are considering
     */
    protected static int numOrderings;

    /**
     * The current permutation we are working with
     */
    protected static int[] perm;

    /**
     * The scores for each round
     */
    protected static ArrayList<Double[]> fantasyScores = new ArrayList<Double[]>();
    protected static ArrayList<Double[]> actualScores = new ArrayList<Double[]>();

    /**
     * The drafter we are currently using
     */
    protected SmartDrafter m_drafterForThisGame;
    protected Quo m_drafterQuoForThisGame;
    protected Quo2 m_drafterQuo2ForThisGame;

    protected boolean useSmart = true;

    protected static int[] kthPickCounts = new int[10];

    /**
     * Constructor
     */
    public TurnOrderSwapper()
    {
        super();
    }

    @Override
    public void setPrefs(int ID, Board board)
    {
        super.setPrefs(ID, board);

        assert board.getNumberOfPlayers() == DRAFTERS.length : "Error: Wrong number of players!";

        // Determine which player to use for this instance
        // We use the unrank(r) function from the parallel state space
        // search on the gpu paper
        if (perm == null)
        {
            perm = new int[board.getNumberOfPlayers()];
        }
        for (int i = 0; i < perm.length; ++i)
        {
            // Initialize to the identity permutation
            perm[i] = i;
        }

        // Lazy factorial calculation
        switch(board.getNumberOfPlayers())
        {
        case 2:
            numOrderings = 2;
            break;

        case 3:
            numOrderings = 6;
            break;

        case 4:
            numOrderings = 24;
            break;

        case 5:
            numOrderings = 120;
            break;

        case 6:
            numOrderings = 720;
            break;

        default:
            assert false : "Error: Bad number of players!";
        break;
        }

        int r = numGamesPlayed % numOrderings;
        for (int n = board.getNumberOfPlayers(); n > 0; --n)
        {
            int i = n - 1;
            int j = r % n;
            if (i != j)
            {
                int temp = perm[i];
                perm[i] = perm[j];
                perm[j] = temp;
                r = r / n;
            }
        }

        // If we have 3 players and 2 of them are the same, we're going to
        // override all that stuff we just did, since we only need to consider
        // the position of the unique player for 3 orderings
        if (DRAFTERS.length == 3)
        {
            for (int i = 0; i < DRAFTERS.length; ++i)
            {
                for (int j = i + 1; j < DRAFTERS.length; ++j)
                {
                    if (DRAFTERS[i].equals(DRAFTERS[j]))
                    {
                        numOrderings = 3;

                        int k = 0;
                        if (i == 0 && j == 1)
                        {
                            k = 2;
                        }
                        else if (i == 0 && j == 2)
                        {
                            k = 1;
                        }

                        for (int m = 0; m < DRAFTERS.length; ++m)
                        {
                            if (numGamesPlayed % 3 == m)
                            {
                                perm[m] = k;
                            }
                            else if ((numGamesPlayed % 3) == ((m+1) % 3))
                            {
                                perm[m] = (k+1) % 3;
                            }
                            else
                            {
                                perm[m] = (k+2) % 3;
                            }
                        }
                    }
                }
            }
        }

        //System.out.println("At seat " + ID + " for game " + numGamesPlayed + " is " + DRAFTERS[perm[ID]]);
        if (board.getAgentInstance(DRAFTERS[perm[ID]]) instanceof Quo)
        {
            m_drafterQuoForThisGame = (Quo)board.getAgentInstance(DRAFTERS[perm[ID]]);  
            m_drafterQuoForThisGame.setPrefs(ID, board);
            useSmart = false;
        } 
        else if (DRAFTERS[perm[ID]].equals("Quo2"))
        {
            m_drafterQuo2ForThisGame = (Quo2)board.getAgentInstance(DRAFTERS[perm[ID]]);  
            m_drafterQuo2ForThisGame.setPrefs(ID, board);
            useSmart = false;
        }
        else {
            m_drafterForThisGame = (SmartDrafter)board.getAgentInstance(DRAFTERS[perm[ID]]);
            m_drafterForThisGame.setPrefs(ID, board);
        }
        
        System.out.println("At seat " + ID + " for game " + numGamesPlayed + " is " + DRAFTERS[perm[ID]]);

        // Set opponent oracles for KthBestPick
        if (DRAFTERS[perm[ID]].contains("KthBestPick"))
        {
            KthBestPickWithUct kthBestPickDrafter = (KthBestPickWithUct) m_drafterForThisGame;
            if (kthBestPickDrafter.USE_ORACLE_MODELS)
            {
                String[] oracles = new String[board.getNumberOfPlayers()];
                for (int i = 0; i < oracles.length; ++i)
                {
                    oracles[i] = DRAFTERS[perm[i]];
                }
                kthBestPickDrafter.setOracles(DRAFTERS);
            }
        }
    }

    @Override
    public String name()
    {
        return "TurnOrderSwapper";
    }

    @Override
    public int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        if (useSmart)
        {
            int pick = m_drafterForThisGame.getPick(draftState, unownedCountries);
            if (DRAFTERS[perm[ID]].contains("KthBestPick"))
            {
                KthBestPickWithUct kthDrafter = (KthBestPickWithUct) m_drafterForThisGame;
                if (kthDrafter.m_lastPickRank >= 1)
                {
                    kthPickCounts[kthDrafter.m_lastPickRank - 1]++;
                }
            }

            return pick;
        }
        else
        {
            if (board.getAgentInstance(DRAFTERS[perm[ID]]) instanceof Quo)
            {
                return m_drafterQuoForThisGame.pickCountry();
            } 
            else
            {
                return m_drafterQuo2ForThisGame.pickCountry();
            }

        }
    }

    @Override
    public String youWon()
    {
        // Get the scores from this game
        double[] values = evaluationFunction(outcomeOfDraft, FANTASY_RISK_EVAL_FUNC);

        // Store the scores
        if (numGamesPlayed % numOrderings == 0)
        {
            Double[] fantasyScore = new Double[board.getNumberOfPlayers()];
            Double[] actualScore = new Double[board.getNumberOfPlayers()];
            for (int i = 0; i < board.getNumberOfPlayers(); ++i)
            {
                fantasyScore[i] = new Double(0.0);
                actualScore[i] = new Double(0.0);
            }
            fantasyScores.add(fantasyScore);
            actualScores.add(actualScore);
        }
        Double[] currentFanScoresForRound = fantasyScores.get(numGamesPlayed / numOrderings);
        Double[] currentActualScoresForRound = actualScores.get(numGamesPlayed / numOrderings);
        for (int player = 0; player < board.getNumberOfPlayers(); ++player)
        {
            // Get the seat this player is in
            int seat = 0;
            for (int i = 0; i < board.getNumberOfPlayers(); ++i)
            {
                if (perm[i] == player)
                {
                    seat = i;
                    break;
                }
            }
            currentFanScoresForRound[player] += values[seat];
            currentActualScoresForRound[player] += seat == ID ? 1.0 : 0.0;
        }
        fantasyScores.set(numGamesPlayed / numOrderings, currentFanScoresForRound);
        actualScores.set(numGamesPlayed / numOrderings, currentActualScoresForRound);

        if (numGamesPlayed == numOrderings - 1)
        {
            // Add the player names to the top of the file
            String playerNames = "";
            for (int player = 0; player < board.getNumberOfPlayers(); ++player)
            {
                playerNames += "" + DRAFTERS[player] + " ";
            }
            playerNames += "\n";
            if (PRINT_FANTASY_SCORES)
            {
                try
                {
                    PrintWriter out = new PrintWriter(new FileWriter(FANTASY_SCORES_DATA_FILENAME, true));
                    out.print(playerNames);
                    out.close();
                }
                catch (Exception e)
                {
                    assert(false);
                }
            }
            if (PRINT_ACTUAL_SCORES)
            {
                try
                {
                    PrintWriter out = new PrintWriter(new FileWriter(ACTUAL_SCORES_DATA_FILENAME, true));
                    out.print(playerNames);
                    out.close();
                }
                catch (Exception e)
                {
                    assert(false);
                }
            }
        }
        if (numGamesPlayed % numOrderings == numOrderings - 1)
        {
            String fantasyString = "";
            String actualString = "";

            // Print the results
            System.out.println("\n\nFantasy Scores for round " + (numGamesPlayed / numOrderings) + " are:");
            for (int player = 0; player < board.getNumberOfPlayers(); ++player)
            {
                System.out.println(DRAFTERS[player] + ": " + currentFanScoresForRound[player]);
                fantasyString += "" + currentFanScoresForRound[player] + " ";
            }
            System.out.println();

            System.out.println("\n\nActual Scores for round " + (numGamesPlayed / numOrderings) + " are:");
            for (int player = 0; player < board.getNumberOfPlayers(); ++player)
            {
                System.out.println(DRAFTERS[player] + ": " + currentActualScoresForRound[player]);
                actualString += "" + currentActualScoresForRound[player] + " ";
            }
            System.out.println();

            // Calculate averages and confidence intervals
            double[] means = new double[board.getNumberOfPlayers()];
            for (Double[] score : fantasyScores)
            {
                for (int i = 0; i < score.length; ++i)
                {
                    means[i] += score[i];
                }
            }
            for (int i = 0; i < means.length; ++i)
            {
                means[i] /= fantasyScores.size();
            }

            double[] standardErrors = new double[board.getNumberOfPlayers()];
            for (Double[] score : fantasyScores)
            {
                for (int i = 0; i < score.length; ++i)
                {
                    standardErrors[i] += Math.pow(score[i] - means[i], 2);
                }
            }
            for (int i = 0; i < standardErrors.length; ++i)
            {
                standardErrors[i] /= fantasyScores.size();
                if (fantasyScores.size() > 1)
                {
                    standardErrors[i] /= (fantasyScores.size() - 1);
                }
                standardErrors[i] = Math.sqrt(standardErrors[i]);
            }

            System.out.println("Average fantasy winnings with 95% confidence interval:");
            for (int i = 0; i < board.getNumberOfPlayers(); ++i)
            {
                System.out.println(DRAFTERS[i] + ": " + means[i] + " +- " + (1.96 * standardErrors[i]));
                fantasyString += " " + means[i] + "+-" + (1.96 * standardErrors[i]) + " ";
            }
            System.out.println("\n\n");
            fantasyString += "\n";

            means = new double[board.getNumberOfPlayers()];
            for (Double[] score : actualScores)
            {
                for (int i = 0; i < score.length; ++i)
                {
                    means[i] += score[i];
                }
            }
            for (int i = 0; i < means.length; ++i)
            {
                means[i] /= actualScores.size();
            }

            standardErrors = new double[board.getNumberOfPlayers()];
            for (Double[] score : actualScores)
            {
                for (int i = 0; i < score.length; ++i)
                {
                    standardErrors[i] += Math.pow(score[i] - means[i], 2);
                }
            }
            for (int i = 0; i < standardErrors.length; ++i)
            {
                standardErrors[i] /= actualScores.size();
                if (actualScores.size() > 1)
                {
                    standardErrors[i] /= (actualScores.size() - 1);
                }
                standardErrors[i] = Math.sqrt(standardErrors[i]);
            }

            System.out.println("Average actual winnings with 95% confidence interval:");
            for (int i = 0; i < board.getNumberOfPlayers(); ++i)
            {
                System.out.println(DRAFTERS[i] + ": " + means[i] + " +- " + (1.96 * standardErrors[i]));
                actualString += "" + means[i] + "+-" + (1.96 * standardErrors[i]) + " ";
            }
            System.out.println("\n\n");
            actualString += "\n";

            System.out.println("Running count of picks for kthBest = " + Arrays.toString(kthPickCounts));

            if (PRINT_FANTASY_SCORES)
            {
                try
                {
                    PrintWriter out = new PrintWriter(new FileWriter(FANTASY_SCORES_DATA_FILENAME, true));
                    out.print(fantasyString);
                    out.close();
                }
                catch (Exception e)
                {
                    assert(false);
                }
            }
            if (PRINT_ACTUAL_SCORES)
            {
                try
                {
                    PrintWriter out = new PrintWriter(new FileWriter(ACTUAL_SCORES_DATA_FILENAME, true));
                    out.print(actualString);
                    out.close();
                }
                catch (Exception e)
                {
                    assert(false);
                }
            }

        }


        if (numGamesPlayed == 100 *6 +5)
        {
            // Create the file name
            String fileName = "c:\\finalresults.txt";			

            // Save to this file
            try
            {
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(fileName, true));

                // Print the results to file
                bufferedWriter.write("\n\nFantasy Scores for round " + (numGamesPlayed / numOrderings) + " are:"+ "\n");
                for (int player = 0; player < board.getNumberOfPlayers(); ++player)
                {
                    bufferedWriter.write(DRAFTERS[player] + ": " + currentFanScoresForRound[player] + "\n");
                }
                bufferedWriter.write("\n");

                bufferedWriter.write("\n\nActual Scores for round " + (numGamesPlayed / numOrderings) + " are:"+ "\n");
                for (int player = 0; player < board.getNumberOfPlayers(); ++player)
                {
                    bufferedWriter.write(DRAFTERS[player] + ": " + currentActualScoresForRound[player]+ "\n");
                }
                bufferedWriter.write("\n");

                // Calculate averages and confidence intervals
                double[] means = new double[board.getNumberOfPlayers()];
                for (Double[] score : fantasyScores)
                {
                    for (int i = 0; i < score.length; ++i)
                    {
                        means[i] += score[i];
                    }
                }
                for (int i = 0; i < means.length; ++i)
                {
                    means[i] /= fantasyScores.size();
                }

                double[] standardErrors = new double[board.getNumberOfPlayers()];
                for (Double[] score : fantasyScores)
                {
                    for (int i = 0; i < score.length; ++i)
                    {
                        standardErrors[i] += Math.pow(score[i] - means[i], 2);
                    }
                }
                for (int i = 0; i < standardErrors.length; ++i)
                {
                    standardErrors[i] /= fantasyScores.size();
                    if (fantasyScores.size() > 1)
                    {
                        standardErrors[i] /= (fantasyScores.size() - 1);
                    }
                    standardErrors[i] = Math.sqrt(standardErrors[i]);
                }

                bufferedWriter.write("Average fantasy winnings with 95% confidence interval:" + "\n");
                for (int i = 0; i < board.getNumberOfPlayers(); ++i)
                {
                    bufferedWriter.write(DRAFTERS[i] + ": " + means[i] + " +- " + (1.96 * standardErrors[i]) + "\n" );
                }
                bufferedWriter.write("\n\n");

                means = new double[board.getNumberOfPlayers()];
                for (Double[] score : actualScores)
                {
                    for (int i = 0; i < score.length; ++i)
                    {
                        means[i] += score[i];
                    }
                }
                for (int i = 0; i < means.length; ++i)
                {
                    means[i] /= actualScores.size();
                }

                standardErrors = new double[board.getNumberOfPlayers()];
                for (Double[] score : actualScores)
                {
                    for (int i = 0; i < score.length; ++i)
                    {
                        standardErrors[i] += Math.pow(score[i] - means[i], 2);
                    }
                }
                for (int i = 0; i < standardErrors.length; ++i)
                {
                    standardErrors[i] /= actualScores.size();
                    if (actualScores.size() > 1)
                    {
                        standardErrors[i] /= (actualScores.size() - 1);
                    }
                    standardErrors[i] = Math.sqrt(standardErrors[i]);
                }

                bufferedWriter.write("Average actual winnings with 95% confidence interval:" + "\n");
                for (int i = 0; i < board.getNumberOfPlayers(); ++i)
                {
                    bufferedWriter.write(DRAFTERS[i] + ": " + means[i] + " +- " + (1.96 * standardErrors[i]) + "\n" );
                }
                bufferedWriter.write("\n\n");

                bufferedWriter.close();

            }
            catch (Exception e)
            {
                assert(false);
            }
        }

        return super.youWon();
    }

    @Override
    public void placeArmies( int numberOfArmies )
    {
        // RL Drafter needs its own placeArmies function for value update
        if (useSmart)
        {
            m_drafterForThisGame.placeArmies(numberOfArmies);
        }
        else
        {
            super.placeArmies(numberOfArmies);
        }

    }
}
