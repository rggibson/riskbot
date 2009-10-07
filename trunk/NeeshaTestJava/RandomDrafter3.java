/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import com.sillysoft.lux.util.*;

import java.util.Random;
import java.util.Vector;

/**
 * This agent is equivalent to EvilPixie except in the draft phase
 * of the game.  In the draft phase, this agent instead uses MaxNMC to
 * pick territories.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class RandomDrafter3 extends SmartAgentBase
{
    /**
     * The name of the agent to use for post draft play
     */
    protected final String POST_DRAFT_PLAYER_NAME = "EvilPixie";

    /**
     * The player object that we will use for all post draft play
     */
    protected LuxAgent postDraftPlayer;

    public RandomDrafter3()
    {
        super();
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
        return "RandomDrafter3";
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
    	int rand = -1;
    	Random generator = new Random( );
//    	int count = board.getNumberOfCountries()
    	while (rand == -1) {
    	// find random country number
    		rand = generator.nextInt(board.getNumberOfCountries());
    	// check if country is still available
    		Country[] countries = board.getCountries();
    		if (countries[rand].getOwner() != -1) {
    			rand = -1;
    		}
    	}
    	System.out.println("3 " + rand);
	
        return rand; 
    }

    /**
     * The depth in MaxN search where Monte Carlo roll outs take over.
     * TODO: rggibson - This should not be a fixed number.  It sould really just
     * be dependent on how many states we can afford to expand in a MaxN search.
     */
    final int MC_DEPTH = 5;

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

        // Determine which country is the best to choose
        int bestPick = -1;
        int valueOfBestPick = Integer.MIN_VALUE;
        for (Integer countryIndex : unownedCountries)
        {
            // Determine the value of picking this country
            draftState[countryIndex] = ID; // Pick country
            int[] values = maxNMC_r(draftState, (ID + 1) % board.getNumberOfPlayers(), MC_DEPTH);
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
     * TODO: Let's pass in the unowned countries vector here so that we don't
     * have to recompute it every time.
     * @param draftState The current assignment of territories in the draft
     * @param player The player whose turn it is
     * @param depth How much further we need to go before MC roll outs take over
     * @return The value of this state for each player
     */
    private int[] maxNMC_r(int[] draftState, int player, int depth)
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
            int[] valuesOfBestMove = null;
            for (Integer countryIndex : unownedCountries)
            {
                // Evaluate how good it is to take this country.
                // Note that player + 1 mod numPlayers is the index of the next player to pick
                draftState[countryIndex] = player; // Pick the country
                int[] valuesOfThisMove = maxNMC_r(draftState, (player + 1) % board.getNumberOfPlayers(), depth - 1);
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
            int[] valuesOfNode = new int[board.getNumberOfPlayers()];
            for (int i = 0; i < valuesOfNode.length; ++i)
            {
                valuesOfNode[i] = 0;
            }

            // Get the roll outs and iteratively update the cumulative average
            for (int i = 0; i < NUM_MC_ROLL_OUTS; ++i)
            {
                int[] rollOutValues = monteCarloRollOut(draftState, player, unownedCountries);
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
    private int[] monteCarloRollOut(int[] draftState, int player, Vector<Integer> unownedCountries)
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
        int[] values = monteCarloRollOut(draftState, (player + 1) % board.getNumberOfPlayers(), unownedCountries);
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
    private int[] evaluationFunction(int[] finalDraftState)
    {
        // Check to make sure this is a legal state
        assert(finalDraftState.length == board.getNumberOfCountries());

        // Check to make sure that this is a terminal state
        for (int i = 0; i < finalDraftState.length; ++i)
        {
            assert(finalDraftState[i] != -1);
        }

        // TODO: IMPLEMENT ME!!
        return new int[board.getNumberOfPlayers()];
    }

}
