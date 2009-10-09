/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import java.util.ArrayList;

/**
 *
 * @author Richard
 */
public class MaxN_MC_Drafter extends SmartDrafter
{    /**
     * The depth in MaxN search where Monte Carlo roll outs take over.
     * TODO: rggibson - This should not be a fixed number.  It sould really just
     * be dependent on how many states we can afford to expand in a MaxN search.
     */
    final int MAX_NODES_TO_EXPAND = 100000;

    /**
     * The number of Monte Carlo roll outs that we average over in MaxNMC, for
     * each leaf node of the MaxN portion of the search.
     */
    final int NUM_MC_ROLL_OUTS = 1;

    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        // Determine the depth to which we can search
        int depth = calculateMaxNSearchDepth(unownedCountries.size(), MAX_NODES_TO_EXPAND);

        // Determine which country is the best to choose
        int bestPick = -1;
        double valueOfBestPick = Double.MIN_VALUE;
        for (int countryIndex = 0; countryIndex < draftState.length; ++countryIndex)
        {
            // Skip over owned countries
            if (draftState[countryIndex] != -1)
            {
                assert(!unownedCountries.contains((Integer) countryIndex));
                continue;
            }

            // Determine the value of picking this country
            assert(unownedCountries.contains((Integer) countryIndex));
            draftState[countryIndex] = ID; // Pick country
            unownedCountries.remove((Integer) countryIndex);

            double[] values = maxNMC(draftState, unownedCountries, (ID + 1) % board.getNumberOfPlayers(), depth, board.getNumberOfPlayers(), NUM_MC_ROLL_OUTS, board);

            assert(draftState[countryIndex] != -1);
            assert(!unownedCountries.contains((Integer) countryIndex));
            draftState[countryIndex] = -1; // Undo pick
            unownedCountries.add(countryIndex);

            // Is country better than the rest we've seen?
            // For now, we break ties by choosing the first country we inspected
            assert(values.length > ID && ID >= 0);
            if (values[ID] > valueOfBestPick)
            {
                // Found new best pick
                bestPick = countryIndex;
                valueOfBestPick = values[ID];
            }
        }

        return bestPick;
    }

    /**
     * Calculate the depth to which we can do a MaxN search
     * @param numUnownedCountries The number of unowned territories
     * @param maxNumNodesToExpand The maximum number of nodes we are allowed to expand in the search
     * @return The depth to search to
     */
    public static int calculateMaxNSearchDepth(int numUnownedCountries, int maxNumNodesToExpand)
    {
        int numNodesToExpand = 1;
        int branchingFactor = numUnownedCountries;
        int depth = -1;
        while (numNodesToExpand < maxNumNodesToExpand && branchingFactor > 0)
        {
            numNodesToExpand *= branchingFactor;
            branchingFactor--;
            depth++;
        }

        return depth;
    }

    /**
     * The recursive portion of MaxN-MC.
     * @param draftState The current assignment of territories in the draft
     * @param unownedCountries The list of territories to choose from
     * @param player The player whose turn it is
     * @param depth How much further we need to go before MC roll outs take over
     * @param numPlayers The number of players playing in the draft
     * @return The value of this state for each player
     */
    public static double[] maxNMC(int[] draftState, ArrayList<Integer> unownedCountries, int player, int depth, int numPlayers, int numMcRollOuts, Board board)
    {
        // Evaluate this state if it is terminal (i.e. all territories owned)
        if (unownedCountries.size() == 0)
        {
            return evaluationFunction(draftState, numPlayers, board);
        }

        if (depth > 0)
        {
            // Continue with MaxN portion of the algorithm
            double[] valuesOfBestMove = null;
            for (int countryIndex = 0; countryIndex < draftState.length; ++countryIndex)
            {
                // Skip over owned countries
                if (draftState[countryIndex] != -1)
                {
                    assert(!unownedCountries.contains((Integer) countryIndex));
                    continue;
                }

                // Evaluate how good it is to take this country.
                // Note that player + 1 mod numPlayers is the index of the next player to pick
                assert(unownedCountries.contains(countryIndex));
                draftState[countryIndex] = player; // Pick the country
                unownedCountries.remove((Integer) countryIndex);

                double[] valuesOfThisMove = maxNMC(draftState, unownedCountries, (player + 1) % numPlayers, depth - 1, numPlayers, numMcRollOuts, board);
                
                // Undo the pick
                assert(draftState[countryIndex] != -1);
                assert(!unownedCountries.contains((Integer) countryIndex));
                draftState[countryIndex] = -1;
                unownedCountries.add(countryIndex);

                // For now, in the case of ties, we take the first country we checked.
                // TODO: rggibson - A better tie-breaking scheme?
                if (valuesOfBestMove == null || valuesOfBestMove[player] < valuesOfThisMove[player])
                {
                    valuesOfBestMove = valuesOfThisMove;
                }
            }

            assert(valuesOfBestMove != null);
            return valuesOfBestMove;
        }
        else
        {
            // We are in the MC portion of the algorithm

            // This will store the cumulative average of the roll outs
            double[] valuesOfNode = new double[numPlayers];
            for (int i = 0; i < valuesOfNode.length; ++i)
            {
                valuesOfNode[i] = 0.0;
            }

            // Get the roll outs and iteratively update the cumulative average
            for (int i = 0; i < numMcRollOuts; ++i)
            {
                double[] rollOutValues = monteCarloRollOut(draftState, unownedCountries, player, numPlayers, board);
                for (int j = 0; j < valuesOfNode.length; ++j)
                {
                    valuesOfNode[j] += (1.0 / (i + 1))*(rollOutValues[j] - valuesOfNode[j]);
                }
            }

            return valuesOfNode;
        }
    }

    /**
     * From the passed in draft state, randomly picks countries for each player
     * until all countries are owned.  The value of the final state is then
     * calculated.
     * @param draftState The current assignment of countries to players, or unowned
     * @param player The player whose pick it currently is
     * @param unownedCountries The countries available to be picked by the players
     * @return The values of the final state reached via the roll outs.
     */
    private static double[] monteCarloRollOut(int[] draftState, ArrayList<Integer> unownedCountries, int player, int numPlayers, Board board)
    {
        // If this is a terminal node, then evaluate
        if (unownedCountries.size() == 0)
        {
            return evaluationFunction(draftState, numPlayers, board);
        }

        // Otherwise, pick a country at random and evaluate
        int randomCountryIndex = (int) (Math.random()*unownedCountries.size());
        int randomCountry = unownedCountries.get(randomCountryIndex);
        unownedCountries.remove(randomCountryIndex);
        assert(draftState[randomCountry] == -1);
        draftState[randomCountry] = player; // Pick country

        double[] values = monteCarloRollOut(draftState, unownedCountries, (player + 1) % numPlayers, numPlayers, board);

        draftState[randomCountry] = -1; // Undo the pick... is this necessary?
        unownedCountries.add(randomCountry);

        return values;
    }

    /**
     * Our evaluation function for determining how good a final draft state is
     * for each player.
     * @param finalDraftState The final assignment of countries to the players
     * @return An array of length numPlayers denoting how much each player likes this state
     */
    private static double[] evaluationFunction(int[] finalDraftState, int numPlayers, Board board)
    {
        // Check to make sure that this is a terminal state
        for (int i = 0; i < finalDraftState.length; i++)
        {
            assert(finalDraftState[i] != -1);
        }

        // MARS evaluation function


        double playerValue[] = new double[numPlayers];
        double totalValue = 0;

        // Calculate the cumulative values of all territories for each player
        for (int i = 0; i < numPlayers; i++)
        {
        	for (int j = 0; j < finalDraftState.length; j++)
        	{
        		playerValue[i] += territoryValue(j, i, finalDraftState, board);
        	}
        	totalValue = totalValue + playerValue[i];
        }

        // Convert values to probabilities of winning
        for (int i = 0; i < numPlayers; i++)
        {
            if (totalValue > 0)
            {
        	playerValue[i] = playerValue[i] / totalValue;
            }
        }
        return playerValue;
    }

    /**
     * Calculates a value for a territory for a particular player
     * @param territoryNum The territory number
     * @param playerNum The player number
     * @return A value for this territory
     */
    private static double territoryValue(int territoryNum, int playerNum, int[] finalDraftState, Board board)
    {

    	// Constants
    	double Csv=70;
    	double Cfn=1.2;
    	double Cen=-0.3;
    	double Cfnu=0.05;
    	double Cenu=-0.003;
    	double Ccb=0.5;
    	double Coc=20;
    	double Ceoc=-4;

    	// Game information

    	Country country[] = board.getCountries();
    	int curContinent = country[territoryNum].getContinent();

    	// if the player does not own this territory, return 0.0
    	if (finalDraftState[territoryNum] != playerNum)
    		return 0.0;


    	// List of countries in current continent
    	ArrayList<Country> cyInContinent = new ArrayList<Country>();

    	int numBorders = 0;
    	int numOwned[] = new int[board.getNumberOfPlayers()];

    	// Get list of countries in current continent
    	// number of territories owned by each player in the current continent,
    	// number of borders to the current continent
    	for (int i = 0; i < country.length; i++ )
    	{
    		if (country[i].getContinent() == curContinent)
    		{
    			cyInContinent.add(country[i]);
                if (finalDraftState[i] >= 0)
                {
                    numOwned[finalDraftState[i]]++;
                }

    			Country border[] = country[i].getAdjoiningList();

    			for (int j = 0; j < border.length; j++ )
    	    	{
    				if (border[j].getContinent() != curContinent)
    				{
    					numBorders++;
    				}
    	    	}
    		}
    	}


    	// Variables
        int Vb = board.getContinentBonus(curContinent);   // the continent bonus value
        int Vs = cyInContinent.size();                    // size of continent
        int Vnb = numBorders;                             // number of borders to continent
        double Vsv = (double) Vb/ (Vs*Vnb);               // static territory value

        double Vcp = (double) numOwned[playerNum] / cyInContinent.size();       // how much do we own
        int Vfn = country[territoryNum].getNumberPlayerNeighbors(playerNum);    // how many friendly neighbours
        int Vfnu=0;                                                             // how many friendly armies
        int Ven = country[territoryNum].getNumberNotPlayerNeighbors(playerNum); // how many enemy neighbours
        int Venu=0;                                                             // how many enemy armies

        int Vcb = 0;                                                            // how many continents does this territory border
        Country border[] = country[territoryNum].getAdjoiningList();
		for (int j = 0; j < border.length; j++ )
    	{
			if (border[j].getContinent() != curContinent)
			{
				Vcb++;
			}
    	}

		int Voc = 0;   //boolean value: 0 or 1, if we own the whole continent
		if ( numOwned[playerNum] ==  cyInContinent.size() )
		{
			Voc = 1;
		}

        int Veoc = 0;  //boolean value: 0 or 1, if an enemy owns the whole continent
        for (int i = 0; i < board.getNumberOfPlayers(); i++ )
        {
        	if (numOwned[i] == cyInContinent.size() &&
        			i != playerNum)
        	{
        		Veoc = 1;
        		break;
        	}
        }

        double returnVal = Vsv*Csv+Vfn*Cfn+Vfnu*Cfnu+Ven*Cen+Venu*Cenu+Vcb*Ccb+Vb*(Vcp+Voc*Coc+Veoc*Ceoc);

//    	System.err.println(returnVal);
    	return returnVal;

    }

}
