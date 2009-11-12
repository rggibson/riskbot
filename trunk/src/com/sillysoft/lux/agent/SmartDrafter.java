/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * This agent is equivalent to EvilPixie except in the draft phase
 * of the game.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public abstract class SmartDrafter extends SmartAgentBase
{

    /**
     * The different evaluation functions we have implemented
     */
    public enum EvaluationFunction
    {
        LIN_REG_NOM_FEATS
    }

    protected final EvaluationFunction FANTASY_RISK_EVAL_FUNC = EvaluationFunction.LIN_REG_NOM_FEATS;

    /**
     * The name of the agent to use for post draft play
     */
    protected String POST_DRAFT_PLAYER_NAME = "Quo";

    /**
     * The player object that we will use for all post draft play
     */
    protected LuxAgent m_postDraftPlayer;

    /**
     * Stores the resulting selections of the draft
     */
    protected static int[] outcomeOfDraft;

    /**
     * Cumulative results of all the games played
     */
    protected static Hashtable<String,Double> scoreboard;

    /**
     * Number of games played
     */
    protected static int numGamesPlayed = 0;

    /**
     * How much time, in milliseconds, the "thinkers" get to think, which we
     * multiply by the number of unowned territories.
     * Note that each agent needs to handle the time constraints themselves;
     * i.e. the code here is written to trust the agent to abide by the time
     * restriction.
     */
    protected final static int PICK_TIME_IN_MILLIS_PER_UNOWNED_TERR = 500;

    public SmartDrafter()
    {
        super();
    }

    @Override
    public void setPrefs(int ID, Board board )
    {
        // Call SmartAgentBase's setPrefs method
        super.setPrefs(ID, board);

        if (outcomeOfDraft == null)
        {
            outcomeOfDraft = new int[countries.length];
        }
        for (int i = 0; i < outcomeOfDraft.length; ++i)
        {
            outcomeOfDraft[i] = -1;
        }
        
        if (scoreboard == null)
        {
            scoreboard = new Hashtable<String,Double>();
            for (int i = 0; i < board.getNumberOfPlayers(); ++i)
            {
                scoreboard.put(board.getPlayerName(i), 0.0);
            }
        }

        // Create the post-draft player
        m_postDraftPlayer = board.getAgentInstance(POST_DRAFT_PLAYER_NAME);
        assert(m_postDraftPlayer != null);
        m_postDraftPlayer.setPrefs(ID, board);
    }

    public String name()
    {
        return "SmartDrafter";
    }

    public float version()
    {
        return 1.1f;
    }

    public String description()
    {
        return "SmartDrafter uses a smart drafting technique to draft territories, then follows some other strategy.";
    }

    public String youWon()
    {
        // When playing fantasy risk, we need to evaluate the match here.
        numGamesPlayed++;
        double[] scores = evaluationFunction(outcomeOfDraft, FANTASY_RISK_EVAL_FUNC);
        for (int i = 0; i < board.getNumberOfPlayers(); ++i)
        {
            double currentScore = scoreboard.get(board.getPlayerName(i));
            scoreboard.put(board.getPlayerName(i), currentScore + scores[i]);
        }

        System.out.println("\n\nThe current score after " + numGamesPlayed + " games:");
        for (String key : scoreboard.keySet())
        {
            System.out.println(key + ": " + scoreboard.get(key));
        }
        System.out.print("\n\n");

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
        int pick = -1;
        if (unownedCountries.size() == 1)
        {
            pick = unownedCountries.get(0);
        }
        else
        {
            pick = getPick(draftState, unownedCountries);
        }

        assert(pick != -1);

        if (countries[pick].getOwner() != -1)
        {
            assert(false);
        }

        outcomeOfDraft[pick] = ID;
        return pick;
    }

    /**
     * Our evaluation function for determining how good a final draft state is
     * for each player.  By selfish, we mean that we ignore the evaluation of the
     * other players and just try to maximize our own.
     * @param finalDraftState The final assignment of countries to the players
     * @return An array of length numPlayers denoting how much each player likes this state
     */
    protected double[] evaluationFunctionSelfish(int[] finalDraftState, EvaluationFunction eval)
    {
        // Check to make sure that this is a terminal state, and that each player
        // made the correct number of picks.
        int[] numPicksPerPlayer = new int[board.getNumberOfPlayers()];
        for (int i = 0; i < finalDraftState.length; i++)
        {
            assert(finalDraftState[i] >= 0 && finalDraftState[i] < board.getNumberOfPlayers());
            numPicksPerPlayer[finalDraftState[i]]++;
        }

        // Note that this assertion only applies if we are expecting each player
        // to receive the same number of picks; just comment this out if this
        // is not the case
        for (int i = 1; i < numPicksPerPlayer.length; ++i)
        {
            if (numPicksPerPlayer[0] != numPicksPerPlayer[i])
            {
                assert(false);
            }
        }

        // Only works on the classic map
        assert(board.getNumberOfCountries() == 42);

        double[] values = new double[board.getNumberOfPlayers()];
        for (int player = 0; player < board.getNumberOfPlayers(); ++player)
        {

            values[player] = getMachineLearnedValue(finalDraftState, player, eval);
        }

        return values;

        // MARS evaluation function

//
//        double playerValue[] = new double[board.getNumberOfPlayers()];
//        double totalValue = 0;
//
//        // Calculate the cumulative values of all territories for each player
//        for (int i = 0; i < board.getNumberOfPlayers(); i++)
//        {
//        	for (int j = 0; j < finalDraftState.length; j++)
//        	{
//        		playerValue[i] += territoryValueForEvalFunc(j, i, finalDraftState);
//        	}
//        	totalValue = totalValue + playerValue[i];
//        }
//
//        // Convert values to probabilities of winning
//        for (int i = 0; i < board.getNumberOfPlayers(); i++)
//        {
//            if (totalValue > 0)
//            {
//        	playerValue[i] = playerValue[i] / totalValue;
//            }
//        }
//        return playerValue;
    }

    /**
     * Our evaluation function for playing fantasy risk
     * @param finalDraftState The final state of the draft (who owns what)
     * @return The value of the state to each player
     */
    protected double[] evaluationFunction(int[] finalDraftState, EvaluationFunction eval)
    {
        double[] values = evaluationFunctionSelfish(finalDraftState, eval);

        // Normalize the values into winning percentages
        double sum = 0.0;
        for (double value : values)
        {
            assert(value >= 0.0);
            sum += value;
        }
        for (int i = 0; i < values.length; ++i)
        {
            values[i] = values[i] / sum;
        }

        return values;
    }

    /**
     * Calculates the value of the passed in state to the passed in player, using
     * the passed in evaluation function.  Given a new evaluation function, just
     * need to implement a swtich statement case here to get it to work.
     * @param draftState The current state of the draft (typically a final draft state)
     * @param player We only evaluate the state in the view of this player
     * @param eval The evaluation function to execute
     * @return The value for this state to the passed in player
     */
    protected double getMachineLearnedValue(int[] draftState, int player, EvaluationFunction eval)
    {
        switch (eval)
        {
            // Linear regression with nominal features for the continent counts
            case LIN_REG_NOM_FEATS:
                // Figure out how much of each continent this player has,
                // as well as the number of enemy neighbours
                int[] numInCont = new int[board.getNumberOfContinents()];
                ArrayList<Integer> enemyNeighbours = new ArrayList<Integer>();
                for (Country country : countries)
                {
                    int terr = country.getCode();
                    if (draftState[terr] != player)
                    {
                        // Only care about territories we own
                        continue;
                    }

                    numInCont[country.getContinent()]++;
                    for (int enemy : country.getAdjoiningCodeList())
                    {
                        if (draftState[enemy] != player && !enemyNeighbours.contains((Integer) enemy))
                        {
                            enemyNeighbours.add((Integer) enemy);
                        }
                    }
                }

                // Below is 408200.txt:
                double value = 0.1831 - 0.0047 * enemyNeighbours.size();

                // Australia
                switch (numInCont[0])
                {
                    case 0:
                        value += 0.0188;
                        break;
                    case 1:
                        value += 0;
                        break;
                    case 2:
                        value += 0.0188 + 0.0461;
                        break;
                    case 3:
                        value += 0.0188 + 0.0461 + 0.0085;
                        break;
                    case 4:
                        value += 0.0188 + 0.0461 + 0.0085 + 0.0545;
                        break;
                    default:
                        assert(false);
                        break;
                }

                // South America
                switch (numInCont[1])
                {
                    case 0:
                        value += 0.0054 + 0.02 - 0.0215;
                        break;
                    case 1:
                        value += 0.0054;
                        break;
                    case 2:
                        value += 0.0054 + 0.02;
                        break;
                    case 3:
                        value += 0;
                        break;
                    case 4:
                        value += 0.0054 + 0.02 - 0.0215 + 0.0891;
                        break;
                    default:
                        assert(false);
                        break;
                }

                // Africa
                switch (numInCont[2])
                {
                    case 0:
                        value += 0.0013 + 0.0618 + 0.0246 + 0.0187 + 0.0262 - 0.0009;
                        break;
                    case 1:
                        value += 0.0013 + 0.0618 + 0.0246 + 0.0187;
                        break;
                    case 2:
                        value += 0.0013 + 0.0618 + 0.0246;
                        break;
                    case 3:
                        value += 0.0013 + 0.0618;
                        break;
                    case 4:
                        value += 0.0013;
                        break;
                    case 5:
                        value += 0;
                        break;
                    case 6:
                        value += 0.0013 + 0.0618 + 0.0246 + 0.0187 + 0.0262;
                        break;
                    default:
                        assert(false);
                        break;
                }

                // North America
                switch (numInCont[3])
                {
                    case 0:
                        value += 0.0029 + 0.0112 + 0.0078;
                        break;
                    case 1:
                        value += 0;
                        break;
                    case 2:
                        value += 0.0029;
                        break;
                    case 3:
                        value += 0.0029 + 0.0112;
                        break;
                    case 4:
                        value += 0.0029 + 0.0112 + 0.0078 + 0.0472;
                        break;
                    case 5:
                        value += 0.0029 + 0.0112 + 0.0078 + 0.0472 + 0.1249;
                        break;
                    case 6:
                        value += 0.0029 + 0.0112 + 0.0078 + 0.0472 + 0.1249 + 0.0453;
                        break;
                    case 7:
                        value += 0.0029 + 0.0112 + 0.0078 + 0.0472 + 0.1249 + 0.0453 + 0.0343;
                        break;
                    case 8:
                        value += 0.0029 + 0.0112 + 0.0078 + 0.0472 + 0.1249 + 0.0453 + 0.0343 - 0.0007;
                        break;
                    case 9:
                        value += 0.0029 + 0.0112 + 0.0078 + 0.0472 + 0.1249 + 0.0453 + 0.0343 - 0.0007 - 0.0026;
                        break;
                    default:
                        assert(false);
                        break;
                }

                // Europe
                switch (numInCont[4])
                {
                    case 0:
                        value += 0.009 - 0.0012 + 0.0062 + 0.0419 - 0.0303;
                        break;
                    case 1:
                        value += 0.009 - 0.0012 + 0.0062;
                        break;
                    case 2:
                        value += 0.009 - 0.0012;
                        break;
                    case 3:
                        value += 0.009;
                        break;
                    case 4:
                        value += 0;
                        break;
                    case 5:
                        value += 0.009 - 0.0012 + 0.0062 + 0.0419 - 0.0303 + 0.0391;
                        break;
                    case 6:
                        value += 0.009 - 0.0012 + 0.0062 + 0.0419;
                        break;
                    case 7:
                        value += 0.009 - 0.0012 + 0.0062 + 0.0419 - 0.0303 + 0.0391 + 0.0984;
                        break;
                    default:
                        assert(false);
                        break;
                }

                // Asia
                switch (numInCont[3])
                {
                    case 0:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135 + 0.0422 - 0.031 + 0.0296 + 0.0077 + 0.0015 + 0.0075 + 0.0102 + 0.0329;
                        break;
                    case 1:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135 + 0.0422 - 0.031 + 0.0296 + 0.0077 + 0.0015 + 0.0075 + 0.0102;
                        break;
                    case 2:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135 + 0.0422 - 0.031 + 0.0296 + 0.0077 + 0.0015 + 0.0075;
                        break;
                    case 3:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135 + 0.0422 - 0.031 + 0.0296 + 0.0077 + 0.0015;
                        break;
                    case 4:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135 + 0.0422 - 0.031 + 0.0296 + 0.0077;
                        break;
                    case 5:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135 + 0.0422;
                        break;
                    case 6:
                        value += 0.0235 + 0.0163;
                        break;
                    case 7:
                        value += 0;
                        break;
                    case 8:
                        value += 0.0235;
                        break;
                    case 9:
                        value += 0.0235 + 0.0163 + 0.0053;
                        break;
                    case 10:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135 + 0.0422 - 0.031 + 0.0296;
                        break;
                    case 11:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135;
                        break;
                    case 12:
                        value += 0.0235 + 0.0163 + 0.0053 - 0.0135 + 0.0422 - 0.031;
                        break;
                    default:
                        assert(false);
                        break;
                }

                return value;
                
            default:
                assert false : "Error: Unrecognized evaluation function!";
                return 0.0; // Should never get here
        }
    }

    /**
     * Calculates a value for a territory for a particular player
     * @param territoryNum The territory number
     * @param playerNum The player number
     * @return A value for this territory
     */
    protected double territoryValueForEvalFunc(int territoryNum, int playerNum, int[] finalDraftState)
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
     * Finds the pick which increases the "temporary evaluation function" the
     * most.
     * @param draftState The state of the draft
     * @param unownedCountries The territories remaining to be picked
     * @param player The player whose turn it is to pick
     * @param selfish Whether to evaluate selfishly or not
     * @return The greedy pick
     */
    protected int getGreedyPick(int[] draftState, ArrayList<Integer> unownedCountries, int player, boolean selfish, EvaluationFunction eval)
    {
        ArrayList<Integer> unownedNeighbours = new ArrayList<Integer>();

        for (int terr = 0; terr < draftState.length; ++terr)
        {
            int owner = draftState[terr];
            if (owner != player)
            {
                // Don't care about unowned terrs
                continue;
            }

            // Search for additional unowned neighbours to add
            for (int neighbour : countries[terr].getAdjoiningCodeList())
            {
                if (draftState[neighbour] == -1 && !unownedNeighbours.contains((Integer)neighbour))
                {
                    unownedNeighbours.add((Integer)neighbour);
                }
            }
        }

        // Now find the greedy pick
        ArrayList<Integer> picks = new ArrayList<Integer>();
        double valueOfPicks = -Double.MAX_VALUE;
        int tieBreakerValue = Integer.MAX_VALUE;

        for (Integer terr : unownedCountries)
        {
            // Consider picking this terr
            assert draftState[terr] == -1 : "Error: draftState and unownedCountries don't match!";
            draftState[terr] = player;
            double value = -Double.MAX_VALUE;
            if (selfish)
            {
                value = getMachineLearnedValue(draftState, player, eval);
            }
            else
            {
                double sum = 0.0;
                for (int i = 0; i < board.getNumberOfPlayers(); ++i)
                {
                    if (i == player)
                    {
                        value = getMachineLearnedValue(draftState, player, eval);
                        sum += value;
                    }
                    else
                    {
                        sum += getMachineLearnedValue(draftState, i, eval);
                    }
                }
                assert(sum > 0);
                value = value / sum;
            }
            draftState[terr] = -1;

            // Calculate the tie-breaker value, which is the new number of unowned
            // neighbours
            int numUnownedNeighbours = unownedNeighbours.size();
            if (unownedNeighbours.contains((Integer)terr))
            {
                numUnownedNeighbours--;
            }
            for (Integer neighbour : countries[terr].getAdjoiningCodeList())
            {
                if (draftState[neighbour] == -1 && !unownedNeighbours.contains((Integer)neighbour))
                {
                    numUnownedNeighbours++;
                }
            }

            // Is this the new best pick?
            if (value > valueOfPicks || (value == valueOfPicks && numUnownedNeighbours < tieBreakerValue))
            {
                picks.clear();
                picks.add(terr);
                valueOfPicks = value;
                tieBreakerValue = numUnownedNeighbours;
            }
            else if (value == valueOfPicks && numUnownedNeighbours == tieBreakerValue)
            {
                picks.add(terr);
            }
        }

        // Make a pick at random from the best ones found
        assert(picks.size() > 0);
        return picks.get((int)(Math.random()*picks.size()));
    }

    /**
     * The method each SmartDrafter needs to implement for picking countires
     * in the draft.
     * @param draftState Who owns what
     * @param unownedCountries The territories left to be picked
     * @return The index of the territory to pick
     */
    public abstract int getPick(int[] draftState, ArrayList<Integer> unownedCountries);

}

