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
 * This agent is equivalent to EvilPixie except in the draft phase
 * of the game.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public abstract class SmartDrafter extends SmartAgentBase
{
    /**
     * The file to store the heuristic function.
     */
    protected static String HEURISTIC_DATA_FILENAME = "C:\\Users\\Richard\\Documents\\NetBeansProjects\\LuxSDK\\LuxSDK\\objectData\\learnedHeuristic";
//    protected static String HEURISTIC_DATA_FILENAME = "objectData/learnedHeuristic";
    
    /**
     * The name of the agent to use for post draft play
     */
    protected String POST_DRAFT_PLAYER_NAME = "Quo";

    /**
     * The player object that we will use for all post draft play
     */
    protected LuxAgent m_postDraftPlayer;

    /**
     * The Hashtables, one for each territory, that will store the learned
     * heuristic (if we are using the learned one)
     */
    protected static Hashtable<ArrayList<Integer>,double[]>[] m_learnedHeuristic;

    public SmartDrafter()
    {
        super();
    }

    @Override
    public void setPrefs(int ID, Board board )
    {
        // Call SmartAgentBase's setPrefs method
        super.setPrefs(ID, board);

        // Create the post-draft player
        m_postDraftPlayer = board.getAgentInstance(POST_DRAFT_PLAYER_NAME);
        assert(m_postDraftPlayer != null);
        m_postDraftPlayer.setPrefs(ID, board);
        super.setPrefs(ID, board);

        if (m_learnedHeuristic == null)
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
                // Cannot assert here when running RL_Drafter
//                assert(false);
            }
        }
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
        m_postDraftPlayer.fortifyPhase();
    }

    public int moveArmiesIn( int cca, int ccd)
    {
        return m_postDraftPlayer.moveArmiesIn(cca, ccd);
    }

    public void attackPhase()
    {
        m_postDraftPlayer.attackPhase();
    }

    public void placeArmies(int numArmies)
    {
        m_postDraftPlayer.placeArmies(numArmies);
    }

    @Override
    public void placeInitialArmies(int numberOfArmies)
    {
        m_postDraftPlayer.placeInitialArmies(numberOfArmies);
    }

    @Override
    public void cardsPhase(Card[] cards)
    {
        m_postDraftPlayer.cardsPhase(cards);
    }

    @Override
    public String message(String message, Object data)
    {
        return m_postDraftPlayer.message(message, data);
    }

    @Override
    public int pickCountry()
    {
        // First, get current state of the draft
        int[] draftState = new int[countries.length];
        ArrayList<Integer> unownedCountries = new ArrayList<Integer>();
        for (int i = 0; i < draftState.length; ++i)
        {
            draftState[i] = countries[i].getOwner();
            if (draftState[i] == -1)
            {
                unownedCountries.add(i);
            }
        }

        // There is redundency in the parameters here, but it is useful to pass
        // in the unownedCountries so that we don't have to go through and find
        // the available picks every time.
        return getPick(draftState, unownedCountries);
    }

    /**
     * Returns the top ranked picks, in order (i.e. pick at index 0 is best)
     * @param draftState The current state of the draft
     * @param unownedCountries The available picks
     * @param activePlayer The player whose turn it is to pick
     * @param numPicksToConsider The number of top picks to find
     * @return The top picks, which has length numPicksToConsider
     */
    protected int[] getTopPicks(int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer, int numPicksToConsider)
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

    /**
     * The recursive portion of MaxN-MC.
     * @param draftState The current assignment of territories in the draft
     * @param unownedCountries The list of territories to choose from
     * @param player The player whose turn it is
     * @param depth How much further we need to go before MC roll outs take over
     * @param numPlayers The number of players playing in the draft
     * @return The value of this state for each player
     */
    public double[] maxNMC(int[] draftState, ArrayList<Integer> unownedCountries, int player, int depth, int numPlayers, int maxBranching, int numRolls)
    {
        // Evaluate this state if it is terminal (i.e. all territories owned)
        if (unownedCountries.size() == 0)
        {
            return evaluationFunction(draftState, numPlayers, board);
        }

        if (depth > 0)
        {
            // Continue with MaxN portion of the algorithm

            // Determine which territories to consider picking
            ArrayList<Integer> picksToConsider = new ArrayList<Integer>();
            if (maxBranching < unownedCountries.size())
            {
                int[] picks = getTopPicks(draftState, unownedCountries, ID, maxBranching);
                picksToConsider = new ArrayList<Integer>(picks.length);
                for (int i = 0; i < picks.length; ++i)
                {
                    picksToConsider.add(picks[i]);
                }
            }
            else
            {
                for (int pick : unownedCountries)
                {
                    picksToConsider.add(pick);
                }
            }

            double[] valuesOfBestMove = null;
            for (int countryIndex : picksToConsider)
            {
                // Evaluate how good it is to take this country.
                // Note that player + 1 mod numPlayers is the index of the next player to pick
                assert(unownedCountries.contains((Integer) countryIndex));
                assert(unownedCountries.contains(countryIndex));
                draftState[countryIndex] = player; // Pick the country
                unownedCountries.remove((Integer) countryIndex);

                double[] valuesOfThisMove = maxNMC(draftState, unownedCountries, (player + 1) % numPlayers, depth - 1, numPlayers, maxBranching, numRolls);

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
            for (int i = 0; i < numRolls; ++i)
            {
                double[] rollOutValues = monteCarloRollOut(draftState, unownedCountries, player, numPlayers);
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
    private double[] monteCarloRollOut(int[] draftState, ArrayList<Integer> unownedCountries, int player, int numPlayers)
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

        double[] values = monteCarloRollOut(draftState, unownedCountries, (player + 1) % numPlayers, numPlayers);

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
        		playerValue[i] += territoryValueForEvalFunc(j, i, finalDraftState, board);
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
    private static double territoryValueForEvalFunc(int territoryNum, int playerNum, int[] finalDraftState, Board board)
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

    /**
     * Calculate the depth to which we can do a MaxN search
     * @param numUnownedCountries The number of unowned territories
     * @param maxNumNodesToExpand The maximum number of nodes we are allowed to expand in the search
     * @return The depth to search to
     */
    public int calculateMaxNSearchDepth(int numUnownedCountries, int maxBranch, int maxLeaves)
    {
        int numLeaves = 1;
        int branchingFactor = Math.min(maxBranch, numUnownedCountries);
        int depth = -1;
        while (numLeaves < maxLeaves && branchingFactor > 0)
        {
            numLeaves *= branchingFactor;
            depth++;
            if (numUnownedCountries - depth - 1 < maxBranch)
            {
                branchingFactor--;
            }
        }

        return Math.max(0, depth);
    }


    /**
     * The method each SmartDrafter needs to implement for picking countires
     * in the draft.
     * @param draftState Who owns what
     * @param unownedCountries The territories left to be picked
     * @return The index of the territory to pick
     */
    protected abstract int getPick(int[] draftState, ArrayList<Integer> unownedCountries);

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
        // Do nothing by default
        return 0.0;
    }
}

