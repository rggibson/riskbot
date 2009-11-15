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
        LIN_REG_NOM_FEATS, // Linear regression with nominal features for continent counts
        LIN_REG_NUM_FEATS, // Linear regression with numeric features for continent counts
        LIN_REG_NUM_FEATS_CONT_BONUS, // Linear regression with numeric features for continent counts, plus a bonus for owning entire continent
        LIN_REG_MORE_FEATS, // Linear regression with mostly numeric features, extras include seat order, friendly neighbour counts, and what the opponents have
        MULTI_PERCEP // An artificial neural network built from Weka (same features as LIN_REG_MORE_FEATS) THIS APPEARS TO BE BROKEN!!
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
//        if (unownedCountries.size() == 1)
//        {
//            pick = unownedCountries.get(0);
//        }
//        else
//        {
            pick = getPick(draftState, unownedCountries);
//        }

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
        // Return values
    	double[] values = new double[board.getNumberOfPlayers()];
    	
    	// Check to make sure that this is a terminal state, and that each player
        // made the correct number of picks.
        int[] numPicksPerPlayer = new int[board.getNumberOfPlayers()];
        for (int i = 0; i < finalDraftState.length; i++)
        {
            //assert(finalDraftState[i] >= 0 && finalDraftState[i] < board.getNumberOfPlayers());
        	assert(finalDraftState[i] < board.getNumberOfPlayers());
        	
        	// if this is not the final draft state, return a 0-vector
        	if (finalDraftState[i] < 0)
        	{
        		System.out.println("Not a final draft state, returning 0-vector");
        		return values;
        	}
        	
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
            if (sum > 0)
            {
                values[i] = values[i] / sum;
            }
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
            // Multilayer Perceptron
            case MULTI_PERCEP:
            {
                int numPlayers = board.getNumberOfPlayers();

                // For each player, calculate how much of each continent,
                // as well as the number of enemy neighbours, number of terrs
                // with an enemy neighbour, and friendly neighbours count
                int[][] numInCont = new int[numPlayers][board.getNumberOfContinents()];
                ArrayList<Integer>[] enemyNeighbours = new ArrayList[numPlayers];
                for (int i = 0; i < enemyNeighbours.length; ++i)
                {
                    enemyNeighbours[i] = new ArrayList<Integer>();
                }
                int[] numTerrsWithEnemy = new int[numPlayers];
                int[] numFriends = new int[numPlayers];
                for (Country country : countries)
                {
                    int terr = country.getCode();
                    int owner = draftState[terr];

                    numInCont[owner][country.getContinent()]++;
                    for (int neighbour : country.getAdjoiningCodeList())
                    {
                        int neighbourOwner = draftState[neighbour];
                        if (neighbourOwner != owner)
                        {
                            if (!enemyNeighbours[owner].contains((Integer) neighbour))
                            {
                                // New enemy neighbour for owner
                                enemyNeighbours[owner].add((Integer) neighbour);
                            }
                        }
                        else
                        {
                            numFriends[owner]++;
                        }
                    }
                    for (int neighbour : country.getAdjoiningCodeList())
                    {
                        if (draftState[neighbour] != owner)
                        {
                            numTerrsWithEnemy[owner]++;
                            break;
                        }
                    }
                }

                // Now the tedious compuation of all the values...
                double[] sigNode = new double[15];
                sigNode[0] = -0.007495805018294353;
                // Seat
                if (player == 0)
                {
                    sigNode[0] += 0.02665943178910042;
                }
                else if (player == 1)
                {
                    sigNode[0] += 0.020520520308640842;
                }
                else
                {
                    sigNode[0] += 0.02584445922195377;
                }
                // Australia
                sigNode[0] += 0.03977672608302195 * numInCont[player][0];
                sigNode[0] += 0.026148766058547716 * numInCont[(player + 1) % numPlayers][0];
                sigNode[0] += -0.06701531925013793 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[0] += 0.031308414221551596 * numInCont[player][1];
                sigNode[0] += 0.029185503998161715 * numInCont[(player + 1) % numPlayers][1];
                sigNode[0] += -0.03001079885546021 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[0] += -0.08337261490246663 * numInCont[player][2];
                sigNode[0] += -0.01855629644105996 * numInCont[(player + 1) % numPlayers][2];
                sigNode[0] += -0.010081766560440965 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[0] += -0.10138919733305596 * numInCont[player][3];
                sigNode[0] += 0.010060635459723917 * numInCont[(player + 1) % numPlayers][3];
                sigNode[0] += -0.052530456303398226 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[0] += -0.05342847823575684 * numInCont[player][4];
                sigNode[0] += -0.07450027839449815 * numInCont[(player + 1) % numPlayers][4];
                sigNode[0] += -0.08625662058071225 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[0] += -0.05790857862920392 * numInCont[player][5];
                sigNode[0] += -0.02937697727473033 * numInCont[(player + 1) % numPlayers][5];
                sigNode[0] += -0.02530335740625531 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[0] += -0.20890702673153638 * enemyNeighbours[player].size();
                sigNode[0] += -0.35391703451621215 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[0] += -0.30085585621796884 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[0] += -0.1203545835183931 * numTerrsWithEnemy[player];
                sigNode[0] += -0.13101160207478277 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[0] += -0.18269112007895277 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[0] += -0.23372844559015496 * numFriends[player];
                sigNode[0] += -0.08062946253925193 * numFriends[(player + 1) % numPlayers];
                sigNode[0] += -0.24142879523947827 * numFriends[(player + 2) % numPlayers];

                sigNode[1] = 0.00614440745097041;
                // Seat
                if (player == 0)
                {
                    sigNode[1] += -0.042883889348736055;
                }
                else if (player == 1)
                {
                    sigNode[1] += 0.03433681491812879;
                }
                else
                {
                    sigNode[1] += -0.01641146166870897;
                }
                // Australia
                sigNode[1] += 0.03840640234927363 * numInCont[player][0];
                sigNode[1] += -0.061422846905285336 * numInCont[(player + 1) % numPlayers][0];
                sigNode[1] += -0.026428311648033206 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[1] += -0.007633610975838743 * numInCont[player][1];
                sigNode[1] += -0.02262221604963264 * numInCont[(player + 1) % numPlayers][1];
                sigNode[1] += -0.03904762550973825 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[1] += -0.14663085775852386 * numInCont[player][2];
                sigNode[1] += -0.06674604921006363 * numInCont[(player + 1) % numPlayers][2];
                sigNode[1] += 0.04077797276558278 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[1] += -0.1360040869671424 * numInCont[player][3];
                sigNode[1] += -0.04212611392317038 * numInCont[(player + 1) % numPlayers][3];
                sigNode[1] += -0.07235614773629646 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[1] += 0.023796498553584976 * numInCont[player][4];
                sigNode[1] += -0.11297719644064631 * numInCont[(player + 1) % numPlayers][4];
                sigNode[1] += -0.10910739237080493 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[1] += -0.030529463960630065 * numInCont[player][5];
                sigNode[1] += -0.08896018158636058 * numInCont[(player + 1) % numPlayers][5];
                sigNode[1] += -0.07886253075193926 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[1] += -0.5121289769753631 * enemyNeighbours[player].size();
                sigNode[1] += -0.6730854420157666 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[1] += -0.5828114366468097 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[1] += -0.31963473038460055 * numTerrsWithEnemy[player];
                sigNode[1] += -0.33419516173076746 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[1] += -0.3425569174628933 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[1] += -0.3378091794229463 * numFriends[player];
                sigNode[1] += -0.17684686452168108 * numFriends[(player + 1) % numPlayers];
                sigNode[1] += -0.43251363356684 * numFriends[(player + 2) % numPlayers];

                sigNode[2] = 0.167032985972557;
                // Seat
                if (player == 0)
                {
                    sigNode[2] += 0.01429680851075489;
                }
                else if (player == 1)
                {
                    sigNode[2] += -0.04071968889635283;
                }
                else
                {
                    sigNode[2] += -0.11256870066308278;
                }
                // Australia
                sigNode[2] += -0.12140670065645591 * numInCont[player][0];
                sigNode[2] += -0.11350902400767203 * numInCont[(player + 1) % numPlayers][0];
                sigNode[2] += -0.3174424514610028 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[2] += -0.2551314244007398 * numInCont[player][1];
                sigNode[2] += -0.0939689945158336 * numInCont[(player + 1) % numPlayers][1];
                sigNode[2] += -0.13341355550052428 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[2] += -0.2504403325088416 * numInCont[player][2];
                sigNode[2] += -0.24922666314890513 * numInCont[(player + 1) % numPlayers][2];
                sigNode[2] += -0.2730827847333513 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[2] += -0.4250621674835631 * numInCont[player][3];
                sigNode[2] += -0.4216264584917516 * numInCont[(player + 1) % numPlayers][3];
                sigNode[2] += -0.38184659689194245 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[2] += -0.2818016873252758 * numInCont[player][4];
                sigNode[2] += -0.2890819275314649 * numInCont[(player + 1) % numPlayers][4];
                sigNode[2] += -0.43356660545086284 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[2] += -0.5891834955086478 * numInCont[player][5];
                sigNode[2] += -0.6330222754565719 * numInCont[(player + 1) % numPlayers][5];
                sigNode[2] += -0.37970824721096036 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[2] += -3.3807776563159573 * enemyNeighbours[player].size();
                sigNode[2] += -3.2516048994210975 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[2] += -3.098716744334666 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[2] += -1.8996677176009007 * numTerrsWithEnemy[player];
                sigNode[2] += -1.6125534290269092 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[2] += -1.8754641194457424 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[2] += -1.6022629183897674 * numFriends[player];
                sigNode[2] += -3.2236379433075193 * numFriends[(player + 1) % numPlayers];
                sigNode[2] += -1.9085329010153638 * numFriends[(player + 2) % numPlayers];

                sigNode[3] = 0.04134733494753009;
                // Seat
                if (player == 0)
                {
                    sigNode[3] += -0.045713505206203355;
                }
                else if (player == 1)
                {
                    sigNode[3] += -0.03364544709347513;
                }
                else
                {
                    sigNode[3] += 0.02836431073866567;
                }
                // Australia
                sigNode[3] += 3.299138435571976E-4 * numInCont[player][0];
                sigNode[3] += -0.020236862602810664 * numInCont[(player + 1) % numPlayers][0];
                sigNode[3] += -0.05919692572132085 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[3] += 0.02034464340679752 * numInCont[player][1];
                sigNode[3] += 0.004753102857534769* numInCont[(player + 1) % numPlayers][1];
                sigNode[3] += 0.009787983212353945 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[3] += -0.04615364312591966 * numInCont[player][2];
                sigNode[3] += -0.03942131399656699 * numInCont[(player + 1) % numPlayers][2];
                sigNode[3] += -0.05305910483232778 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[3] += -0.004144728006437404 * numInCont[player][3];
                sigNode[3] += 0.004063082334757847 * numInCont[(player + 1) % numPlayers][3];
                sigNode[3] += 0.017525855094363045 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[3] += 0.040483268496168186 * numInCont[player][4];
                sigNode[3] += -0.004422948942148155 * numInCont[(player + 1) % numPlayers][4];
                sigNode[3] += -0.05108635992717039 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[3] += -0.03362830220645347 * numInCont[player][5];
                sigNode[3] += -0.04112965628905525 * numInCont[(player + 1) % numPlayers][5];
                sigNode[3] += -0.020415784197735126 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[3] += -0.07726558575047024 * enemyNeighbours[player].size();
                sigNode[3] += -0.07408599088078742 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[3] += -0.1282270929520052 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[3] += -0.09937722031926903 * numTerrsWithEnemy[player];
                sigNode[3] += -0.11451748567456908 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[3] += -0.11330906863160232 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[3] += -0.14728548560615984 * numFriends[player];
                sigNode[3] += -0.061950103106861984 * numFriends[(player + 1) % numPlayers];
                sigNode[3] += -0.06267912562823702 * numFriends[(player + 2) % numPlayers];

                sigNode[4] = -0.030380695641043728;
                // Seat
                if (player == 0)
                {
                    sigNode[4] += -2.2165398848470873E-4;
                }
                else if (player == 1)
                {
                    sigNode[4] += 0.03410794408863304;
                }
                else
                {
                    sigNode[4] += -0.06656519463440946;
                }
                // Australia
                sigNode[4] += -0.017032970222440557 * numInCont[player][0];
                sigNode[4] += -0.019694216048470263 * numInCont[(player + 1) % numPlayers][0];
                sigNode[4] += -0.04221284598334243 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[4] += -0.010684951010433937 * numInCont[player][1];
                sigNode[4] += -0.02447815107204546 * numInCont[(player + 1) % numPlayers][1];
                sigNode[4] += 0.028001748767284167 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[4] += -0.0025722893199184305 * numInCont[player][2];
                sigNode[4] += 0.01965109127074541 * numInCont[(player + 1) % numPlayers][2];
                sigNode[4] += -0.0035059675696700737 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[4] += 0.10720589161178043 * numInCont[player][3];
                sigNode[4] += -0.09748360910864384 * numInCont[(player + 1) % numPlayers][3];
                sigNode[4] += -0.025612558046783564 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[4] += -0.04954898183007297 * numInCont[player][4];
                sigNode[4] += 0.04092594985854934 * numInCont[(player + 1) % numPlayers][4];
                sigNode[4] += -0.0201476585576776 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[4] += -0.07272086611711535 * numInCont[player][5];
                sigNode[4] += 0.0525348589629659 * numInCont[(player + 1) % numPlayers][5];
                sigNode[4] += -0.020594074535596738 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[4] += -0.24201170160723684 * enemyNeighbours[player].size();
                sigNode[4] += -0.012890046991121337 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[4] += -0.14157909274775185 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[4] += -0.062257183133716566 * numTerrsWithEnemy[player];
                sigNode[4] += -0.08855035007944703 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[4] += -0.040002045497484075 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[4] += -0.0020655417016267417 * numFriends[player];
                sigNode[4] += -0.21727241992728513 * numFriends[(player + 1) % numPlayers];
                sigNode[4] += -0.24312922914006074 * numFriends[(player + 2) % numPlayers];

                sigNode[5] = 0.028478869416943622;
                // Seat
                if (player == 0)
                {
                    sigNode[5] += 0.00523195069652951;
                }
                else if (player == 1)
                {
                    sigNode[5] += 0.00523195069652951;
                }
                else
                {
                    sigNode[5] += 0.0030231983892088803;
                }
                // Australia
                sigNode[5] += 9.325724166235827E-4 * numInCont[player][0];
                sigNode[5] += 0.013565182116277143 * numInCont[(player + 1) % numPlayers][0];
                sigNode[5] += 0.019673097512246836 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[5] += 0.021745465796996096 * numInCont[player][1];
                sigNode[5] += -0.023460965294040445 * numInCont[(player + 1) % numPlayers][1];
                sigNode[5] += -0.056321065972758884 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[5] += -0.056321065972758884 * numInCont[player][2];
                sigNode[5] += -0.03422386652923424 * numInCont[(player + 1) % numPlayers][2];
                sigNode[5] += -0.0022359875530551785 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[5] += -0.01662419840144894 * numInCont[player][3];
                sigNode[5] += 0.02658067335704684 * numInCont[(player + 1) % numPlayers][3];
                sigNode[5] += -0.04062160331236936 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[5] += -0.048213944117202666 * numInCont[player][4];
                sigNode[5] += -0.03233249673912448 * numInCont[(player + 1) % numPlayers][4];
                sigNode[5] += -0.030285839897230205 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[5] += -0.026067876197969155 * numInCont[player][5];
                sigNode[5] += -0.06008416001835134 * numInCont[(player + 1) % numPlayers][5];
                sigNode[5] += -0.05599633343767193 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[5] += -0.2541642888714773 * enemyNeighbours[player].size();
                sigNode[5] += -0.3399854459850948 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[5] += -0.299290077488538 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[5] += -0.13628407963437492 * numTerrsWithEnemy[player];
                sigNode[5] += -0.19105765778259373 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[5] += -0.1463835060325342 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[5] += -0.12675203440831143 * numFriends[player];
                sigNode[5] += -0.10195093144340599* numFriends[(player + 1) % numPlayers];
                sigNode[5] += -0.22293659045966566 * numFriends[(player + 2) % numPlayers];

                sigNode[6] = 0.0346637796698109;
                // Seat
                if (player == 0)
                {
                    sigNode[6] += 0.005558483484833126;
                }
                else if (player == 1)
                {
                    sigNode[6] += 0.03802351841541139;
                }
                else
                {
                    sigNode[6] += -0.029660980577792963;
                }
                // Australia
                sigNode[6] += -0.0023427720221000166 * numInCont[player][0];
                sigNode[6] += -0.06843222342966485 * numInCont[(player + 1) % numPlayers][0];
                sigNode[6] += -0.03933582986871949 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[6] += -0.0606005514588058 * numInCont[player][1];
                sigNode[6] += -0.03304576592039899 * numInCont[(player + 1) % numPlayers][1];
                sigNode[6] += -0.04337078699085394 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[6] += -0.07762175450169011 * numInCont[player][2];
                sigNode[6] += -0.03230030752764067 * numInCont[(player + 1) % numPlayers][2];
                sigNode[6] += 0.008408841973261447* numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[6] += -0.11035648689564886 * numInCont[player][3];
                sigNode[6] += -0.04964746979720153 * numInCont[(player + 1) % numPlayers][3];
                sigNode[6] += -0.06522865067703526 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[6] += 0.02368963982176262 * numInCont[player][4];
                sigNode[6] += -0.027127433814240653 * numInCont[(player + 1) % numPlayers][4];
                sigNode[6] += -0.04901242381706142 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[6] += -0.02141739778990781 * numInCont[player][5];
                sigNode[6] += -0.049327586256961556 * numInCont[(player + 1) % numPlayers][5];
                sigNode[6] += -0.050060659418957536 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[6] += -0.3788862020856001 * enemyNeighbours[player].size();
                sigNode[6] += -0.4427288066224397 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[6] += -0.4582577941889027 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[6] += -0.2206519079901255 * numTerrsWithEnemy[player];
                sigNode[6] += -0.2517338965049665 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[6] += -0.2022141266092616 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[6] += -0.25251081661599994 * numFriends[player];
                sigNode[6] += -0.19170381173648532 * numFriends[(player + 1) % numPlayers];
                sigNode[6] += -0.3542047668420444 * numFriends[(player + 2) % numPlayers];

                sigNode[7] = 0.03750489325189818;
                // Seat
                if (player == 0)
                {
                    sigNode[7] += 0.008557666577253676;
                }
                else if (player == 1)
                {
                    sigNode[7] += 0.0030624412128101427;
                }
                else
                {
                    sigNode[7] += 0.07121767636382717;
                }
                // Australia
                sigNode[7] += -0.010981809749478466 * numInCont[player][0];
                sigNode[7] += -0.055916045486288805 * numInCont[(player + 1) % numPlayers][0];
                sigNode[7] += -4.1057737479319124E-4 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[7] += -0.006293179100285858 * numInCont[player][1];
                sigNode[7] += 0.028727277026609793 * numInCont[(player + 1) % numPlayers][1];
                sigNode[7] += -0.05280665146725816 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[7] += 0.05307241898352077 * numInCont[player][2];
                sigNode[7] += 0.009756913126612064 * numInCont[(player + 1) % numPlayers][2];
                sigNode[7] += -0.058155646672825786 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[7] += -0.09214070468342356 * numInCont[player][3];
                sigNode[7] += 0.04590011248445583 * numInCont[(player + 1) % numPlayers][3];
                sigNode[7] += -0.0441519517014915 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[7] += 0.010023529794061023 * numInCont[player][4];
                sigNode[7] += -0.05889569931610457 * numInCont[(player + 1) % numPlayers][4];
                sigNode[7] += 0.029266504331151265 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[7] += 0.005272612198485238 * numInCont[player][5];
                sigNode[7] += -0.06384732327402698 * numInCont[(player + 1) % numPlayers][5];
                sigNode[7] += -0.0673293802129938 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[7] += -0.11036901569561217 * enemyNeighbours[player].size();
                sigNode[7] += -0.2271204150200802 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[7] += -0.20488035213042924 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[7] += -0.11387431561278606 * numTerrsWithEnemy[player];
                sigNode[7] += -0.15596426347849743 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[7] += -0.11493621893271229 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[7] += -0.29780124450432105 * numFriends[player];
                sigNode[7] += -0.015458817772786002 * numFriends[(player + 1) % numPlayers];
                sigNode[7] += -0.03475698408771126 * numFriends[(player + 2) % numPlayers];

                sigNode[8] = 0.2151928975109296;
                // Seat
                if (player == 0)
                {
                    sigNode[8] += 0.04208913555276378;
                }
                else if (player == 1)
                {
                    sigNode[8] += 0.028787835115692925;
                }
                else
                {
                    sigNode[8] += -0.19397464631965694;
                }
                // Australia
                sigNode[8] += -0.19395780191298118 * numInCont[player][0];
                sigNode[8] += -0.13298130181748474 * numInCont[(player + 1) % numPlayers][0];
                sigNode[8] += -0.339779891217095 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[8] += -0.2980804178774181 * numInCont[player][1];
                sigNode[8] += -0.16349808097084004 * numInCont[(player + 1) % numPlayers][1];
                sigNode[8] += -0.16923133599066573 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[8] += -0.31541206671871985 * numInCont[player][2];
                sigNode[8] += -0.36029981864057303 * numInCont[(player + 1) % numPlayers][2];
                sigNode[8] += -0.33807043615958476 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[8] += -0.49172893041626775 * numInCont[player][3];
                sigNode[8] += -0.49172893041626775 * numInCont[(player + 1) % numPlayers][3];
                sigNode[8] += -0.5204266571571052 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[8] += -0.32874233730631297 * numInCont[player][4];
                sigNode[8] += -0.3680965301466492 * numInCont[(player + 1) % numPlayers][4];
                sigNode[8] += -0.48846637161450285 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[8] += -0.6507461479997744 * numInCont[player][5];
                sigNode[8] += -0.9074432847899924 * numInCont[(player + 1) % numPlayers][5];
                sigNode[8] += -0.47708604135232247 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[8] += -4.416813838517807 * enemyNeighbours[player].size();
                sigNode[8] += -4.213351011773428 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[8] += -3.9987280888664807 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[8] += -2.478842907751604 * numTerrsWithEnemy[player];
                sigNode[8] += -2.1203268141842697 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[8] += -2.4846735224918284 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[8] += -2.127545104412884 * numFriends[player];
                sigNode[8] += -4.160774284698532 * numFriends[(player + 1) % numPlayers];
                sigNode[8] += -2.4486902078689248 * numFriends[(player + 2) % numPlayers];

                sigNode[9] = 0.11479234778806544;
                // Seat
                if (player == 0)
                {
                    sigNode[9] += -0.026236707880119687;
                }
                else if (player == 1)
                {
                    sigNode[9] += 0.00872713262641691;
                }
                else
                {
                    sigNode[9] += -0.10733187546688847;
                }
                // Australia
                sigNode[9] += -0.13836071173843603 * numInCont[player][0];
                sigNode[9] += -0.09485672719327928 * numInCont[(player + 1) % numPlayers][0];
                sigNode[9] += -0.2723146595546971 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[9] += -0.28132298576677006 * numInCont[player][1];
                sigNode[9] += -0.17821188558411627 * numInCont[(player + 1) % numPlayers][1];
                sigNode[9] += -0.14217125394576868 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[9] += -0.2656727097545483 * numInCont[player][2];
                sigNode[9] += -0.26685298772796145 * numInCont[(player + 1) % numPlayers][2];
                sigNode[9] += -0.22210067322066696 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[9] += -0.3478262085050718 * numInCont[player][3];
                sigNode[9] += -0.3583355664130087 * numInCont[(player + 1) % numPlayers][3];
                sigNode[9] += -0.3452148715880099 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[9] += -0.21947356443946456 * numInCont[player][4];
                sigNode[9] += -0.2496673889171947 * numInCont[(player + 1) % numPlayers][4];
                sigNode[9] += -0.35461610276439176 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[9] += -0.5499408781130801 * numInCont[player][5];
                sigNode[9] += -0.6023818024435087 * numInCont[(player + 1) % numPlayers][5];
                sigNode[9] += -0.34799175647715014 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[9] += -3.2145539262951033 * enemyNeighbours[player].size();
                sigNode[9] += -3.122981596575009 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[9] += -2.9396731717945275 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[9] += -1.8033870979704596 * numTerrsWithEnemy[player];
                sigNode[9] += -1.5993566654219282 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[9] += -1.7889169235461073 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[9] += -1.5430551518688964 * numFriends[player];
                sigNode[9] += -3.067354677632469 * numFriends[(player + 1) % numPlayers];
                sigNode[9] += -1.8159469751387176 * numFriends[(player + 2) % numPlayers];

                sigNode[10] = 0.02318107582964527;
                // Seat
                if (player == 0)
                {
                    sigNode[10] += -0.01674868474440235;
                }
                else if (player == 1)
                {
                    sigNode[10] += -0.04455977511408726;
                }
                else
                {
                    sigNode[10] += -0.044827344065937524;
                }
                // Australia
                sigNode[10] += 0.048928757910664894 * numInCont[player][0];
                sigNode[10] += -0.04610923070015959 * numInCont[(player + 1) % numPlayers][0];
                sigNode[10] += 0.006108934791408337 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[10] += -0.035900836391524374 * numInCont[player][1];
                sigNode[10] += 0.035560346945334505 * numInCont[(player + 1) % numPlayers][1];
                sigNode[10] += -0.050622626630332054 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[10] += -0.0574166948856441 * numInCont[player][2];
                sigNode[10] += 0.020599759988854147 * numInCont[(player + 1) % numPlayers][2];
                sigNode[10] += -0.04779040913302219 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[10] += 0.0015535645114028776 * numInCont[player][3];
                sigNode[10] += -0.029671374941475148 * numInCont[(player + 1) % numPlayers][3];
                sigNode[10] += -0.03880438879277248 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[10] += -0.02083876935599458 * numInCont[player][4];
                sigNode[10] += -0.025905848018722807 * numInCont[(player + 1) % numPlayers][4];
                sigNode[10] += 0.009554761651564716 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[10] += -0.011888787630024477 * numInCont[player][5];
                sigNode[10] += -0.03069786966629964 * numInCont[(player + 1) % numPlayers][5];
                sigNode[10] += -0.031294281901650726 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[10] += -0.16294957496687842 * enemyNeighbours[player].size();
                sigNode[10] += -0.25821041152932495 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[10] += -0.21999617360783622 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[10] += -0.17039754591558776 * numTerrsWithEnemy[player];
                sigNode[10] += -0.102527841888514 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[10] += -0.12188862948655604 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[10] += -0.15455183929795818 * numFriends[player];
                sigNode[10] += -0.046108411859539136 * numFriends[(player + 1) % numPlayers];
                sigNode[10] += -0.1469470603605063 * numFriends[(player + 2) % numPlayers];

                sigNode[11] = -0.004539680153791329;
                // Seat
                if (player == 0)
                {
                    sigNode[11] += -0.011008344578648712;
                }
                else if (player == 1)
                {
                    sigNode[11] += -0.04348520953730304;
                }
                else
                {
                    sigNode[11] += -0.0022853783529034983;
                }
                // Australia
                sigNode[11] += -0.017350858529281588 * numInCont[player][0];
                sigNode[11] += -0.034893338944226235 * numInCont[(player + 1) % numPlayers][0];
                sigNode[11] += -0.007320731183588527 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[11] += -0.011177835634696225 * numInCont[player][1];
                sigNode[11] += -0.037036693482200934 * numInCont[(player + 1) % numPlayers][1];
                sigNode[11] += -0.04145657234199009 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[11] += -0.014007313963546621 * numInCont[player][2];
                sigNode[11] += -0.02642912015295476 * numInCont[(player + 1) % numPlayers][2];
                sigNode[11] += -0.013414009212890288 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[11] += 0.021670165181276315 * numInCont[player][3];
                sigNode[11] += 0.002606906609428166 * numInCont[(player + 1) % numPlayers][3];
                sigNode[11] += -0.05297797592850818 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[11] += -0.013803280705404333 * numInCont[player][4];
                sigNode[11] += -0.0036128962155562235 * numInCont[(player + 1) % numPlayers][4];
                sigNode[11] += -0.05433838132508801 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[11] += -0.06098880817739927 * numInCont[player][5];
                sigNode[11] += -0.05868075175098298 * numInCont[(player + 1) % numPlayers][5];
                sigNode[11] += 0.026603292491379032 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[11] += -0.075380561694964 * enemyNeighbours[player].size();
                sigNode[11] += -0.12087771171398383 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[11] += -0.12587431623620365 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[11] += -0.07811231812629854 * numTerrsWithEnemy[player];
                sigNode[11] += -0.07912619189899232 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[11] += -0.017345889864388453 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[11] += -0.0769964748920462 * numFriends[player];
                sigNode[11] += -0.022499820470304924 * numFriends[(player + 1) % numPlayers];
                sigNode[11] += -0.14934496885475865 * numFriends[(player + 2) % numPlayers];

                sigNode[12] = -0.011196264563322388;
                // Seat
                if (player == 0)
                {
                    sigNode[12] += 0.014062746518543232;
                }
                else if (player == 1)
                {
                    sigNode[12] += -0.0037707377656038083;
                }
                else
                {
                    sigNode[12] += 3.5217625210657357E-4;
                }
                // Australia
                sigNode[12] += 0.053438998459642485 * numInCont[player][0];
                sigNode[12] += -0.01051187688560942 * numInCont[(player + 1) % numPlayers][0];
                sigNode[12] += 0.055671956621569166 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[12] += 0.010267130720161393 * numInCont[player][1];
                sigNode[12] += 0.01551137267261116 * numInCont[(player + 1) % numPlayers][1];
                sigNode[12] += -0.011741290615267692 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[12] += 0.07785663178764153 * numInCont[player][2];
                sigNode[12] += 0.04239953443455974 * numInCont[(player + 1) % numPlayers][2];
                sigNode[12] += 0.02106056540320324 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[12] += -0.048311575836710044 * numInCont[player][3];
                sigNode[12] += 0.11773280966971073 * numInCont[(player + 1) % numPlayers][3];
                sigNode[12] += 0.006973332681714483 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[12] += 0.03724486422962278 * numInCont[player][4];
                sigNode[12] += 0.05324740104861478 * numInCont[(player + 1) % numPlayers][4];
                sigNode[12] += 0.05540567838438169 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[12] += 0.15048463482285543 * numInCont[player][5];
                sigNode[12] += 0.03186153974133815 * numInCont[(player + 1) % numPlayers][5];
                sigNode[12] += 0.031683961513774825 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[12] += 0.40289966512801884 * enemyNeighbours[player].size();
                sigNode[12] += 0.3628897759798117 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[12] += 0.29372779206148264 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[12] += 0.17309893951413685 * numTerrsWithEnemy[player];
                sigNode[12] += 0.2038623323963771 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[12] += 0.16247011031035852 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[12] += 0.1969856008519785 * numFriends[player];
                sigNode[12] += 0.4789348438555731 * numFriends[(player + 1) % numPlayers];
                sigNode[12] += 0.28422719776690447 * numFriends[(player + 2) % numPlayers];

                sigNode[13] = 0.08001067299901563;
                // Seat
                if (player == 0)
                {
                    sigNode[13] += 0.0340287659914003;
                }
                else if (player == 1)
                {
                    sigNode[13] += -0.023437027722919404;
                }
                else
                {
                    sigNode[13] += -0.03303749298779403;
                }
                // Australia
                sigNode[13] += -0.024754655734327245 * numInCont[player][0];
                sigNode[13] += -0.083069788574124 * numInCont[(player + 1) % numPlayers][0];
                sigNode[13] += -0.07215993345120866 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[13] += -0.09569392500790666 * numInCont[player][1];
                sigNode[13] += -0.05835723353446323 * numInCont[(player + 1) % numPlayers][1];
                sigNode[13] += -0.09523426331574147 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[13] += -0.1028088128661788 * numInCont[player][2];
                sigNode[13] += -0.13289861799304525 * numInCont[(player + 1) % numPlayers][2];
                sigNode[13] += -0.049431342662172764 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[13] += -0.13814517635966897 * numInCont[player][3];
                sigNode[13] += -0.09814861316713286 * numInCont[(player + 1) % numPlayers][3];
                sigNode[13] += -0.12889813931596852 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[13] += -0.05156478483853748 * numInCont[player][4];
                sigNode[13] += -0.09520675651522287 * numInCont[(player + 1) % numPlayers][4];
                sigNode[13] += -0.1539140784129827 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[13] += -0.19144707676147749 * numInCont[player][5];
                sigNode[13] += -0.2487022239374039 * numInCont[(player + 1) % numPlayers][5];
                sigNode[13] += -0.16804907568843808 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[13] += -1.1384162652147995 * enemyNeighbours[player].size();
                sigNode[13] += -1.091994581942755 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[13] += -1.0106890769753794 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[13] += -0.6128536018646459 * numTerrsWithEnemy[player];
                sigNode[13] += -0.5083041676786881 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[13] += -0.5928735029652422 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[13] += -0.4974625693833075 * numFriends[player];
                sigNode[13] += -1.108308807612544 * numFriends[(player + 1) % numPlayers];
                sigNode[13] += -0.5914280447838994 * numFriends[(player + 2) % numPlayers];

                sigNode[14] = 0.0594926410306882;
                // Seat
                if (player == 0)
                {
                    sigNode[14] += 0.006441350563157805;
                }
                else if (player == 1)
                {
                    sigNode[14] += -0.03173526790998279;
                }
                else
                {
                    sigNode[14] += -0.0328975888167486;
                }
                // Australia
                sigNode[14] += 0.01918341579799733 * numInCont[player][0];
                sigNode[14] += 0.014963717277770924 * numInCont[(player + 1) % numPlayers][0];
                sigNode[14] += 0.005375900307045866 * numInCont[(player + 2) % numPlayers][0];
                // South America
                sigNode[14] += -0.034211232239917684 * numInCont[player][1];
                sigNode[14] += -0.04263191649513286 * numInCont[(player + 1) % numPlayers][1];
                sigNode[14] += 0.027203038317282102 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                sigNode[14] += -0.023674928483662927 * numInCont[player][2];
                sigNode[14] += 0.0036638989844569405 * numInCont[(player + 1) % numPlayers][2];
                sigNode[14] += 0.0016060762057289052 * numInCont[(player + 2) % numPlayers][2];
                // North America
                sigNode[14] += -0.10191063953333546 * numInCont[player][3];
                sigNode[14] += -0.04208040097338621 * numInCont[(player + 1) % numPlayers][3];
                sigNode[14] += 0.004682473883420704 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                sigNode[14] += -0.013006432734346509 * numInCont[player][4];
                sigNode[14] += -0.009227081471001167 * numInCont[(player + 1) % numPlayers][4];
                sigNode[14] += -0.007816172917786687 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                sigNode[14] += -0.0033879362361383955 * numInCont[player][5];
                sigNode[14] += -0.02462515906425567 * numInCont[(player + 1) % numPlayers][5];
                sigNode[14] += -0.02953306167874969 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                sigNode[14] += -0.17470986335050728 * enemyNeighbours[player].size();
                sigNode[14] += -0.24533578213602977 * enemyNeighbours[(player + 1) % numPlayers].size();
                sigNode[14] += -0.2645115613851365 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                sigNode[14] += -0.13915419394147263 * numTerrsWithEnemy[player];
                sigNode[14] += -0.19389912380257732 * numTerrsWithEnemy[(player + 1) % numPlayers];
                sigNode[14] += -0.13299013051214528 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                sigNode[14] += -0.21284344072997466 * numFriends[player];
                sigNode[14] += -0.14282587651946899 * numFriends[(player + 1) % numPlayers];
                sigNode[14] += -0.26313803872629915 * numFriends[(player + 2) % numPlayers];

                double value = 0.2418952129415528;
                for (int i = 0; i < 15; ++i)
                {
                    sigNode[i] = 1.0 / (1.0 + Math.exp(-sigNode[i]));
                }
                value += 0.06842052153706206 * sigNode[0];
                value += -0.6598817980057807 * sigNode[1];
                value += -0.2178002305489157 * sigNode[2];
                value += 0.09895477412738435 * sigNode[3];
                value += 0.03533357876814341 * sigNode[4];
                value += -0.750145628638688 * sigNode[5];
                value += 0.11621985618396947 * sigNode[6];
                value += -0.03470353295998997 * sigNode[7];
                value += -0.12467388763657558 * sigNode[8];
                value += 0.0010723498357238911 * sigNode[9];
                value += -0.734972486896377 * sigNode[10];
                value += 0.11822205746603832 * sigNode[11];
                value += -0.46910500380200004 * sigNode[12];
                value += 0.23511318626201855 * sigNode[13];
                value += 0.013885868857258144 * sigNode[14];

                return Math.max(0.0, value);
            }

            // Linear regression with mostly numeric features
            case LIN_REG_MORE_FEATS:
            {
                int numPlayers = board.getNumberOfPlayers();

                // For each player, calculate how much of each continent,
                // as well as the number of enemy neighbours, number of terrs
                // with an enemy neighbour, and friendly neighbours count
                int[][] numInCont = new int[numPlayers][board.getNumberOfContinents()];
                ArrayList<Integer>[] enemyNeighbours = new ArrayList[numPlayers];
                for (int i = 0; i < enemyNeighbours.length; ++i)
                {
                    enemyNeighbours[i] = new ArrayList<Integer>();
                }
                int[] numTerrsWithEnemy = new int[numPlayers];
                int[] numFriends = new int[numPlayers];
                for (Country country : countries)
                {
                    int terr = country.getCode();
                    int owner = draftState[terr];

                    numInCont[owner][country.getContinent()]++;
                    for (int neighbour : country.getAdjoiningCodeList())
                    {
                        int neighbourOwner = draftState[neighbour];
                        if (neighbourOwner != owner)
                        {
                            if (!enemyNeighbours[owner].contains((Integer) neighbour))
                            {
                                // New enemy neighbour for owner
                                enemyNeighbours[owner].add((Integer) neighbour);
                            }
                        }
                        else
                        {
                            numFriends[owner]++;
                        }
                    }
                    for (int neighbour : country.getAdjoiningCodeList())
                    {
                        if (draftState[neighbour] != owner)
                        {
                            numTerrsWithEnemy[owner]++;
                            break;
                        }
                    }
                }

                // Now compute the value
                double value = 26.6227;
                // Seat
                if (player == 1 || player == 0)
                {
                    value += 5.577;
                    if (player == 0)
                    {
                        value += 9.035;
                    }
                }
                // Australia
                value += 2.2505 * numInCont[player][0];
                value += -0.854 * numInCont[(player + 1) % numPlayers][0];
                value += -1.3907 * numInCont[(player + 2) % numPlayers][0];
                // South America
                value += 0.4765 * numInCont[player][1];
                value += 0.0936 * numInCont[(player + 1) % numPlayers][1];
                value += -0.568 * numInCont[(player + 2) % numPlayers][1];
                // Africa
                value += -2.1602 * numInCont[player][2];
                value += 0.6137 * numInCont[(player + 1) % numPlayers][2];
                value += 1.5397 * numInCont[(player + 2) % numPlayers][2];
                // North America
                value += 2.0242 * numInCont[player][3];
                value += -1.0759 * numInCont[(player + 1) % numPlayers][3];
                value += -0.9499 * numInCont[(player + 2) % numPlayers][3];
                // Europe
                value += -1.0675 * numInCont[player][4];
                value += 0.7138 * numInCont[(player + 1) % numPlayers][4];
                value += 0.3479 * numInCont[(player + 2) % numPlayers][4];
                // Asia
                value += -0.7873 * numInCont[player][5];
                value += 0.3612 * numInCont[(player + 1) % numPlayers][5];
                value += 0.4266 * numInCont[(player + 2) % numPlayers][5];
                // Number of enemy neighbours
                value += -1.0463 * enemyNeighbours[player].size();
                value += 0.3218 * enemyNeighbours[(player + 1) % numPlayers].size();
                value += 0.7245 * enemyNeighbours[(player + 2) % numPlayers].size();
                // Number of territories with an enemy neighbour
                value += 0.4221 * numTerrsWithEnemy[player];
                value += -0.4234 * numTerrsWithEnemy[(player + 1) % numPlayers];
                value += 0.0013 * numTerrsWithEnemy[(player + 2) % numPlayers];
                // Number of friendly neighbours, with repitition counted
                value += 0.5093 * numFriends[player];
                value += -0.3158 * numFriends[(player + 1) % numPlayers];
                value += -0.1935 * numFriends[(player + 2) % numPlayers];

                return Math.max(0.0, value);
            }

            // Linear regression with numeric features for the continent counts
            case LIN_REG_NUM_FEATS:
            {
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

                // LIN_REG_NUM_FEAT_CONT_BONUS.txt
                double value = 0.2787;
                value += 0.0447 * numInCont[0]; // Australia
                value += 0.0243 * numInCont[1]; // South America
                value += -0.0099 * numInCont[2]; // Africa
                value += 0.0565 * numInCont[3]; // North America
                value += 0.0166 * numInCont[4]; // Europe
                value += -0.0053 * numInCont[5]; // Asia
                value += -0.011 * enemyNeighbours.size(); // Number of enemy neighbours

                return value;
            }

            // Linear regression with numeric features for the continent counts, plus
            // bonus values for owning a continent.
            case LIN_REG_NUM_FEATS_CONT_BONUS:
            {
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

                // LIN_REG_NUM_FEAT_CONT_BONUS.txt
                double value = 0.4593;
                value += 0.0349 * numInCont[0]; // Australia
                value += 0.013 * numInCont[1]; // South America
                value += -0.0212 * numInCont[2]; // Africa
                value += 0.048 * numInCont[3]; // North America
                value += 0.0055 * numInCont[4]; // Europe
                value += -0.0044 * numInCont[5]; // Asia
                value += -0.0103 * enemyNeighbours.size(); // Number of enemy neighbours
                if (numInCont[0] == 4)
                {
                    value += 0.0062; // Australia bonus
                }
                if (numInCont[1] == 4)
                {
                    value += 0.059; // South America bonus
                }
                if (numInCont[2] == 6)
                {
                    value += 0.1411; // Africa bonus
                }
                if (numInCont[3] == 9)
                {
                    value += -0.0608; // North America bonus... negative??  Oh, Weka...
                }
                if (numInCont[4] == 7)
                {
                    value += 0.1426; // Europe bonus
                }
                if (numInCont[5] < 12)
                {
                    value += -0.0561; // Asia bonus (penalty for not getting it all)
                }

                return value;
            }

            // Linear regression with nominal features for the continent counts
            case LIN_REG_NOM_FEATS:
            {
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

                // LIN_REG_NOM_FEATS.txt
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
            }
                
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

