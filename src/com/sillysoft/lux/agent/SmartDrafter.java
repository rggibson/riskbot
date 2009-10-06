/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import java.io.*;
import com.sillysoft.lux.*;
import com.sillysoft.lux.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * This agent is equivalent to EvilPixie except in the draft phase
 * of the game.  In the draft phase, this agent instead uses MaxNMC to
 * pick territories.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class SmartDrafter extends SmartAgentBase
{
    /**
     * The name of the agent to use for post draft play
     */
    protected final String POST_DRAFT_PLAYER_NAME = "EvilPixie";

    /**
     * The player object that we will use for all post draft play
     */
    protected LuxAgent postDraftPlayer;

    /**
     * We store all of the calls to kthBestPick along with the returned territory
     */
    protected Hashtable<String, Integer> kthBestPickCalls;

    public SmartDrafter()
    {
        super();

        kthBestPickCalls = new Hashtable<String, Integer>();
    }

    @Override
    public void setPrefs(int ID, Board board )
    {
        // Call SmartAgentBase's setPrefs method
        super.setPrefs(ID, board);

        // Create the post-draft player
        postDraftPlayer = board.getAgentInstance(POST_DRAFT_PLAYER_NAME);
        assert(postDraftPlayer != null);
        postDraftPlayer.setPrefs(ID, board);
    }

    public String name()
    {
        return "SmartDrafter";
    }

    public float version()
    {
        return 1.0f;
    }

    public String description()
    {
        return "SmartDrafter uses a smart drafting technique to draft territories, then follows some other strategy.";
    }

    public String youWon()
    {
        return "Ha Ha.  I out-drafted you.";
    }

    public void fortifyPhase()
    {
        postDraftPlayer.fortifyPhase();
    }

    public int moveArmiesIn( int cca, int ccd)
    {
        return postDraftPlayer.moveArmiesIn(cca, ccd);
    }

    public void attackPhase()
    {
        postDraftPlayer.attackPhase();
    }

    public void placeArmies(int numArmies)
    {
        postDraftPlayer.placeArmies(numArmies);
    }

    @Override
    public void placeInitialArmies(int numberOfArmies)
    {
        postDraftPlayer.placeInitialArmies(numberOfArmies);
    }

    @Override
    public void cardsPhase(Card[] cards)
    {
        postDraftPlayer.cardsPhase(cards);
    }

    @Override
    public String message(String message, Object data)
    {
        return postDraftPlayer.message(message, data);
    }

    /**
     * Uses MaxN with Monte Carlo roll-outs to pick in the draft
     * @return The int corresponding to the territory to pick
     */
    @Override
    public int pickCountry()
    {
        return maxNMC();
//        return kthBestPick();
    }

    /**
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

    /**
     * The MaxN with Monte Carlo roll-outs algorithm for picking territories in
     * the draft.
     * @return The int corresponding to the chosen territory
     */
    private int maxNMC()
    {
        // First, get current state of the draft
        int[] draftState = new int[countries.length];
        Vector<Integer> unownedCountries = new Vector<Integer>();
        for (int i = 0; i < draftState.length; ++i)
        {
            draftState[i] = countries[i].getOwner();
            if (draftState[i] == -1)
            {
                unownedCountries.add(i);
            }
        }

        // Determine the depth to which we can search
        int numNodesToExpand = 1;
        int branchingFactor = unownedCountries.size();
        int depth = -1;
        while (numNodesToExpand < MAX_NODES_TO_EXPAND && branchingFactor > 0)
        {
            numNodesToExpand *= branchingFactor;
            branchingFactor--;
            depth++;
        }

        // Determine which country is the best to choose
        int bestPick = -1;
        double valueOfBestPick = Double.MIN_VALUE;
        for (Integer countryIndex : unownedCountries)
        {
            // Determine the value of picking this country
            draftState[countryIndex] = ID; // Pick country
            double[] values = maxNMC_r(draftState, (ID + 1) % board.getNumberOfPlayers(), depth);
            draftState[countryIndex] = -1; // Undo pick

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
     * The recursive portion of MaxN-MC.
     * TODO: rggibson - Let's pass in the unowned countries vector here so that
     * we don't have to recompute it every time.
     * @param draftState The current assignment of territories in the draft
     * @param player The player whose turn it is
     * @param depth How much further we need to go before MC roll outs take over
     * @return The value of this state for each player
     */
    private double[] maxNMC_r(int[] draftState, int player, int depth)
    {
        // First, find all unowned territories
        Vector<Integer> unownedCountries = new Vector<Integer>();
        for (int i = 0; i < draftState.length; ++i)
        {
            if (draftState[i] == -1)
            {
                unownedCountries.add(i);
            }
        }

        // Evaluate this state if it is terminal (i.e. all territories owned)
        if (unownedCountries.size() == 0)
        {
            return evaluationFunction(draftState);
        }

        if (depth > 0)
        {
            // Continue with MaxN portion of the algorithm
            double[] valuesOfBestMove = null;
            for (Integer countryIndex : unownedCountries)
            {
                // Evaluate how good it is to take this country.
                // Note that player + 1 mod numPlayers is the index of the next player to pick
                draftState[countryIndex] = player; // Pick the country
                double[] valuesOfThisMove = maxNMC_r(draftState, (player + 1) % board.getNumberOfPlayers(), depth - 1);
                draftState[countryIndex] = -1; // Undo the pick

                // For now, in the case of ties, we take the first country we checked.
                // Should we change this to taking a random country among all the best picks?
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
            double[] valuesOfNode = new double[board.getNumberOfPlayers()];
            for (int i = 0; i < valuesOfNode.length; ++i)
            {
                valuesOfNode[i] = 0;
            }

            // Get the roll outs and iteratively update the cumulative average
            for (int i = 0; i < NUM_MC_ROLL_OUTS; ++i)
            {
                double[] rollOutValues = monteCarloRollOut(draftState, player, unownedCountries);
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
    private double[] monteCarloRollOut(int[] draftState, int player, Vector<Integer> unownedCountries)
    {
        // If this is a terminal node, then evaluate
        if (unownedCountries.size() == 0)
        {
            return evaluationFunction(draftState);
        }

        // Otherwise, pick a country at random and evaluate
        int randomCountryIndex = rand.nextInt(unownedCountries.size());
        int randomCountry = unownedCountries.get(randomCountryIndex);
        unownedCountries.remove(randomCountryIndex);
        draftState[randomCountry] = player; // Pick country
        double[] values = monteCarloRollOut(draftState, (player + 1) % board.getNumberOfPlayers(), unownedCountries);
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
    private double[] evaluationFunction(int[] finalDraftState)
    {
        // Check to make sure this is a legal state
        assert(finalDraftState.length == board.getNumberOfCountries());

        // Check to make sure that this is a terminal state
        for (int i = 0; i < finalDraftState.length; i++)
        {
            assert(finalDraftState[i] != -1);
        }

        // MARS evaluation function

        
        double playerValue[] = new double[board.getNumberOfPlayers()];
        double totalValue = 0;

        // Calculate the cumulative values of all territories for each player
        for (int i = 0; i < board.getNumberOfPlayers(); i++)
        {
        	for (int j = 0; j < board.getNumberOfCountries(); j++)
        	{
        		playerValue[i] += territoryValue(j, i, finalDraftState);        		
        	}
        	totalValue = totalValue + playerValue[i];
        }

        // Convert values to probabilities of winning
        for (int i = 0; i < board.getNumberOfPlayers(); i++)
        {
        	playerValue[i] = playerValue[i] / totalValue;
        }
        return playerValue;
    }

    /**
     * Calculates a value for a territory for a particular player
     * @param territoryNum The territory number
     * @param playerNum The player number
     * @return A value for this territory
     */
    private double territoryValue(int territoryNum, int playerNum, int[] finalDraftState)
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
    	List<Country> cyInContinent = new ArrayList<Country>();

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

    /**
     * A helper class for comparing the values of territories
     */
//    private class TerritoryComparator implements Comparator<Integer>
//    {
//        int playerNum;
//        int[] draftState;
//
//        /**
//         * Constructor
//         */
//        public TerritoryComparator(int playerNum, int[] draftState)
//        {
//            this.playerNum = playerNum;
//            this.draftState = draftState;
//        }
//
//        public TerritoryComparator(int playerNum)
//        {
//            this.playerNum = playerNum;
//            this.draftState = null;
//        }
//        
//        /**
//         * Compares the passed in territories according to the territory
//         * evaluation funciton.
//         * @param terr1 The first territory
//         * @param terr2 The second territory
//         * @return -1, 0, or 1 if terr1 is better, equal, or worse in value compared to terr2
//         */
//        public int compare(Integer terr1, Integer terr2)
//        {
//            // TODO: rggibson - Try using RL to learn territory values?
//            double terr1Val = territoryValue(terr1, playerNum, draftState);
//            double terr2Val = territoryValue(terr2, playerNum, draftState);
//
//            if (terr1Val > terr2Val)
//            {
//                return -1;
//            }
//            else if (terr1Val == terr2Val)
//            {
//                return 0;
//            }
//            else
//            {
//                return 1;
//            }
//        }
//    }


    /**
     * The kthBestPick algorithm for drafting territories
     * @return The index of the territory chosen by the kthBestPick algorithm
     */
    private int kthBestPick()
    {
        // Get current state of the draft
        int[] draftState = new int[countries.length];
        Vector<Integer> unownedCountries = new Vector<Integer>();
        for (int i = 0; i < draftState.length; ++i)
        {
            draftState[i] = countries[i].getOwner();
            if (draftState[i] == -1)
            {
                unownedCountries.add(i);
            }
        }

        // Check the Hashtable to see if we've already called the algorithm
        // on this state
        int terr = -1;
        String draftStateIndex = getDraftStateIndex(draftState);
        if (kthBestPickCalls.containsKey(draftStateIndex))
        {
            terr = kthBestPickCalls.get(draftStateIndex);
        }
        else
        {
            terr = kthBestPick_internal(draftState, unownedCountries, ID);
            kthBestPickCalls.put(draftStateIndex, terr);
        }

        return terr;
    }

    /**
     * The maximum number of picks that we will consider looking ahead
     * for the kthBestPick algorithm.
     */
    final int MAX_NUM_PICKS_CONSIDERED = 5;

    /**
     * The rest of the kthBestPick algorithm for drafting territories.
     * @param draftState The current state of the draft (who owns what)
     * @param unownedCountries The countries left to pick
     * @param playerId The index of the player whose turn it is to pick
     * @return The index of the territory chosen by the kthBestPick algorithm.
     */
    private int kthBestPick_internal(int[] draftState, Vector<Integer> unownedCountries, int playerId)
    {
        // How many picks do we have left in the draft?
        int numPicksRemaining = (int)Math.ceil((double)unownedCountries.size() / board.getNumberOfPlayers());

        // We truncate the number of picks we are considering
        // TODO: rggibson - Change this so that we assume the draft ends after numPicksRemaining,
        // rather than just truncating to the same number of picks at each recursive call
        numPicksRemaining = Math.min(numPicksRemaining, MAX_NUM_PICKS_CONSIDERED);

        // Now we need to sort the unowned territories in decreasing order according
        // to the probability of winning given we draft that territory

        // TODO: rggibson - Would it be faster to just find the top numPicksRemainging number
        // of territories rather than sorting the entire array?  It doesn't look like it
        // for the small map, but it could be the case for the classic map.
//        TerritoryComparator comp = new TerritoryComparator(playerId);
//        Integer[] topPicks = new Integer[unownedCountries.size()];
//        for (int i = 0; i < topPicks.length; ++i)
//        {
//            topPicks[i] = unownedCountries.get(i);
//        }
//        Arrays.sort(topPicks, comp);

        // Alternative sorting technique
        int[] topPicks = new int[numPicksRemaining];
        double[] valueOfTopPicks = new double[numPicksRemaining];
        for (int i = 0; i < numPicksRemaining; ++i)
        {
            // Initialize
            topPicks[i] = -1;
            valueOfTopPicks[i] = -Double.MAX_VALUE;
        }
        for (int i = 0; i < unownedCountries.size(); ++i)
        {
            // Get the value of this unowned country
            int terr = unownedCountries.get(i);
            double valueOfTerr = territoryValue(terr, playerId, draftState);

            // Find its rank in the current top picks
            int rank = numPicksRemaining;
            while (rank > 0 && valueOfTopPicks[rank - 1] < valueOfTerr)
            {
                rank--;
            }

            // Adjust the top picks to make room for the new territory
            for (int j = numPicksRemaining - 2; j >= rank; --j)
            {
                topPicks[j+1] = topPicks[j];
                valueOfTopPicks[j+1] = valueOfTopPicks[j];
            }

            // Put the territory into place
            if (rank < numPicksRemaining)
            {
                topPicks[rank] = terr;
                valueOfTopPicks[rank] = valueOfTerr;
            }
        }

        for (int k = numPicksRemaining - 1; k >=0; --k)
        {
            // The pick we are now considering
            Integer terr = topPicks[k];

            // The list of territories we want to get later on if we
            // make this pick
            Vector<Integer> higherRankedTerrs = new Vector<Integer>();
            for (int i = 0; i < k; ++i)
            {
                int higherRankedTerr = topPicks[i];

                // Just a simple check to detect possible bugs with the sorting
                assert(territoryValue(higherRankedTerr, playerId, draftState) >= territoryValue(terr, playerId, draftState));

                higherRankedTerrs.add(higherRankedTerr);
            }

            // Assign the territory to the current player
            assert(draftState[terr] == -1);
            assert(unownedCountries.contains(terr));
            draftState[terr] = playerId;
            unownedCountries.remove(terr);

            // Check if we should make this pick
            boolean makeThisPick = territoriesPickedBy(higherRankedTerrs, playerId, draftState, unownedCountries, (playerId + 1) % board.getNumberOfPlayers());

            // Undo the territory assignment
            assert(draftState[terr] == playerId);
            assert(!unownedCountries.contains(terr));
            draftState[terr] = -1;
            unownedCountries.add(terr);

            if (makeThisPick)
            {
                return terr;
            }
        }

        // Should never make it out here
        assert(false);

        return -1;
    }

    /**
     * Determines whether the original player will draft every country in
     * territories, assuming players play according to kthBestPick.
     * @param territories The territories that must be picked by originalPlayer
     * @param originalPlayer The index of the original player
     * @param draftState The current state of the draft
     * @param unownedCountries Countries still left to pick
     * @param activePlayer The player whose turn it is to pick
     * @return True if all territories are picked by originalPlayer, false otherwise
     */
    private boolean territoriesPickedBy(Vector<Integer> territories, int originalPlayer, int[] draftStateParam, Vector<Integer> unownedCountriesParam, int activePlayer)
    {
        // Clone the state
        int[] draftState = draftStateParam.clone();
        Vector<Integer> unownedCountries = (Vector<Integer>) unownedCountriesParam.clone();

        while (!territories.isEmpty())
        {
            // Figure out the active player's next pick

            // Check the Hashtable to see if we've already called the algorithm
            // on this state
            int pick = -1;
            String draftStateIndex = getDraftStateIndex(draftState);
            if (kthBestPickCalls.containsKey(draftStateIndex))
            {
                pick = kthBestPickCalls.get(draftStateIndex);
            }
            else
            {
                pick = kthBestPick_internal(draftState, unownedCountries, activePlayer);
                kthBestPickCalls.put(draftStateIndex, pick);
            }

            if (territories.contains(pick))
            {
                if (originalPlayer == activePlayer)
                {
                    // This pick is ok here
                    territories.remove((Integer) pick);
                    assert(!territories.contains(pick));
                }
                else
                {
                    // Can't have another player make a pick from territories
                    return false;
                }
            }

            // Now we make the pick
            assert(draftState[pick] == -1);
            assert(unownedCountries.contains(pick));

            draftState[pick] = activePlayer;
            unownedCountries.remove((Integer) pick);
            activePlayer = (activePlayer + 1) % board.getNumberOfPlayers();

            assert(!unownedCountries.contains(pick));
        }

        return true;
    }

    /**
     * Returns the index to map the draftState to in the Hashtable
     * @param draftState The current draft state
     * @return The String to use as an index
     */
    private String getDraftStateIndex(int[] draftState)
    {
        String index = "";

        for (int i = 0; i < draftState.length; i = i + 3)
        {
            int num = draftState[i] + 1;
            if (i + 1 < draftState.length)
            {
                num += (board.getNumberOfPlayers() + 1) * (draftState[i+1] + 1);
            }
            if (i + 2 < draftState.length)
            {
                num += Math.pow(board.getNumberOfPlayers() + 1, 2) * (draftState[i+2] + 1);
            }

            char c = (char)('A' + num);
            index += c;
        }

        return index;
    }
}
