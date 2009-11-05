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

    /**
     * Stores the efforts of UCT search
     */
    protected Hashtable<ArrayList<Integer>,double[]> m_uctTree;

    /**
     * Exploration parameter in UCT (usually known as c)
     */
    protected final double epsilon = 0.25;

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
        super.setPrefs(ID, board);

        if (m_uctTree == null)
        {
            m_uctTree = new Hashtable<ArrayList<Integer>,double[]>();
        }
        else
        {
            m_uctTree.clear();
        }

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
        // When playing fantasy risk, we need to evaluate the match here.
        numGamesPlayed++;
        double[] scores = evaluationFunction(outcomeOfDraft);
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
        int pick = getPick(draftState, unownedCountries);
        outcomeOfDraft[pick] = ID;
        return pick;
    }

    /**
     * Returns the top ranked picks, in order (i.e. pick at index 0 is best)
     * @param draftState The current state of the draft
     * @param unownedCountries The available picks
     * @param activePlayer The player whose turn it is to pick
     * @param numPicksToConsider The number of top picks to find
     * @param selfish Whether or not to use a selfish evaluation
     * @return The top picks, which has length numPicksToConsider
     */
    protected int[] getTopPicks(int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer, int numPicksToConsider, boolean selfish)
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
            double valueOfTerr = getValueOfTerr(terr, draftState, unownedCountries, activePlayer, selfish);

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
     * The recursive portion of MaxN-UCT.
     * @param draftState The current assignment of territories in the draft
     * @param unownedCountries The list of territories to choose from
     * @param player The player whose turn it is
     * @param depth How much further we need to go before MC roll outs take over
     * @param maxBranching The maximum number of branches to expand in the MaxN portion
     * @param numRolls The number of UCT roll outs to perform at each leaf
     * @param selfish Whether or not to evaluate selfishly
     * @return The value of this state for each player
     */
    protected double[] maxN_Uct(int[] draftState, ArrayList<Integer> unownedCountries, int player, int depth, int maxBranching, int numRolls, boolean selfish)
    {
        // Evaluate this state if it is terminal (i.e. all territories owned)
        if (unownedCountries.size() == 0)
        {
            return evaluationFunction(draftState);
        }

        if (depth > 0)
        {
            // Continue with MaxN portion of the algorithm

            // Determine which territories to consider picking
            ArrayList<Integer> picksToConsider = new ArrayList<Integer>();
            if (maxBranching < unownedCountries.size())
            {
                int[] picks = getTopPicks(draftState, unownedCountries, ID, maxBranching, selfish);
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

                double[] valuesOfThisMove = maxN_Uct(draftState, unownedCountries, (player + 1) % board.getNumberOfPlayers(), depth - 1, maxBranching, numRolls, selfish);

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
            // We are in the UCT portion of the algorithm

            ArrayList<Integer> state = new ArrayList<Integer>(draftState.length);
            for (int i = 0; i < draftState.length; ++i)
            {
                state.add(draftState[i]);
            }

            double numVisitsAlready = 0;
            if (m_uctTree.containsKey(state))
            {
                numVisitsAlready = (m_uctTree.get(state))[0];
            }
            for (int i = (int)numVisitsAlready; i < numRolls; ++i)
            {
                uctRollOut(state, unownedCountries, player, true, selfish);
            }

            // Find the value of this node
            double[] valuesOfNode = new double[board.getNumberOfPlayers()];
            assert(m_uctTree.containsKey(state));
            double[] entry = m_uctTree.get(state);
            assert(entry.length == valuesOfNode.length + 1);
            // Assume we are storing the number of visits in the first spot
            for (int i = 0; i < valuesOfNode.length; ++i)
            {
                valuesOfNode[i] = entry[i+1];
            }

            return valuesOfNode;
        }
    }

    /**
     * Carries out a uct roll out from the passed in state.
     * @param state The state of the draft
     * @param unownedCountries The territories to choose from
     * @param player The player whose turn it is
     * @param selfish Whether or not to evaluate selfishly
     * @return The value of this state in the roll out
     */
    protected double[] uctRollOut(ArrayList<Integer> state, ArrayList<Integer> unownedCountries, int player, boolean updateTree, boolean selfish)
    {
        if (unownedCountries.size() == 0)
        {
            // Terminal node
            int[] finalDraftState = new int[state.size()];
            for (int i = 0; i < finalDraftState.length; ++i)
            {
                finalDraftState[i] = state.get(i);
            }
            if (selfish)
            {
                return evaluationFunctionSelfish(finalDraftState);
            }
            else
            {
                return evaluationFunction(finalDraftState);
            }
        }

        double[] stateEntry = null;
        if (m_uctTree.containsKey(state))
        {
            stateEntry = m_uctTree.get(state);
        }
        else
        {
            // The first entry is the number of visits to this state
            stateEntry = new double[board.getNumberOfPlayers() + 1];
        }

        // First, get the number of visits to this state.
        // This is "equal" to the number of visits to each of the children, for
        // the purposes of what we need the number of visits here for
        double numVisits = 0.0;
        for (int terr : unownedCountries)
        {
            state.set(terr, player);
            if (m_uctTree.containsKey(state))
            {
                numVisits += (m_uctTree.get(state))[0];
            }
            state.set(terr, -1);
        }

        // Pick a territory to simulate draft
        ArrayList<Integer> picks = new ArrayList<Integer>();
        double valueOfPick = -Double.MAX_VALUE;
        for (int terr : unownedCountries)
        {
            assert(state.get(terr) == -1);

            // Get the value of this terr
            state.set(terr, player);
            double valueOfTerr = Double.MAX_VALUE;
            if (m_uctTree.containsKey(state))
            {
                double[] terrVals = m_uctTree.get(state);
                valueOfTerr = terrVals[player + 1]; // Because the first entry is the numVisits
                assert(terrVals[0] > 0);
                assert(numVisits > 0);
                valueOfTerr += epsilon * Math.sqrt(Math.log(numVisits) / terrVals[0]);
            }
            state.set(terr, -1);

            if (valueOfTerr > valueOfPick)
            {
                // New best pick
                picks.clear();
                picks.add(terr);
                valueOfPick = valueOfTerr;
            }
            else if (valueOfTerr == valueOfPick)
            {
                // A tie
                picks.add(terr);
            }
        }

        if (picks.isEmpty())
        {
            assert(false);
        }

        // Make the pick
        int pick = picks.get((int)(Math.random()*picks.size()));
        boolean updateTree_r = (updateTree && m_uctTree.containsKey(state)); // Only add the top-most new state to the tree per roll out
        state.set(pick, player);
        unownedCountries.remove((Integer) pick);
        double[] evaluatePick = uctRollOut(state, unownedCountries, (player + 1) % board.getNumberOfPlayers(), updateTree_r, selfish);
        assert(state.get(pick) == player);
        assert(!unownedCountries.contains((Integer) pick));
        unownedCountries.add(pick);
        state.set(pick, -1);

        if (updateTree)
        {
            // Update the tree
            stateEntry[0] = stateEntry[0] + 1;
            assert(evaluatePick.length + 1 == stateEntry.length);
            for (int i = 0; i < evaluatePick.length; ++i)
            {
                stateEntry[i+1] = stateEntry[i+1] + (1.0 / stateEntry[0])*(evaluatePick[i] - stateEntry[i+1]);
            }
            ArrayList<Integer> copyOfState = new ArrayList<Integer>(state.size());
            for (int i = 0; i < state.size(); ++i)
            {
                copyOfState.add(state.get(i));
            }
            m_uctTree.put(copyOfState, stateEntry);
        }

        // And recurse back up the tree
        return evaluatePick;
    }

    /**
     * Our evaluation function for determining how good a final draft state is
     * for each player.  By slefish, we mean that we ignore the evaluation of the
     * other players and just try to maximize our own.
     * @param finalDraftState The final assignment of countries to the players
     * @return An array of length numPlayers denoting how much each player likes this state
     */
    private double[] evaluationFunctionSelfish(int[] finalDraftState)
    {
        // Check to make sure that this is a terminal state
        for (int i = 0; i < finalDraftState.length; i++)
        {
            assert(finalDraftState[i] != -1);
        }

        // Only works on the classic map
        assert(board.getNumberOfCountries() == 42);

        double[] values = new double[board.getNumberOfPlayers()];
        for (int player = 0; player < board.getNumberOfPlayers(); ++player)
        {
            // Figure out how much of each continent this player has,
            // as well as the number of enemy neighbours
            int[] numInCont = new int[board.getNumberOfContinents()];
            ArrayList<Integer> enemyNeighbours = new ArrayList<Integer>();
            for (Country country : countries)
            {
                int terr = country.getCode();
                if (finalDraftState[terr] != player)
                {
                    // Only care about territories we own
                    continue;
                }

                numInCont[country.getContinent()]++;
                for (int enemy : country.getAdjoiningCodeList())
                {
                    if (finalDraftState[enemy] != player && !enemyNeighbours.contains((Integer) enemy))
                    {
                        enemyNeighbours.add((Integer) enemy);
                    }
                }
            }

            values[player] = getMachineLearnedValue(numInCont, enemyNeighbours.size());
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
    protected double[] evaluationFunction(int[] finalDraftState)
    {
        double[] values = evaluationFunctionSelfish(finalDraftState);

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
     * Calculates the (supervised) machine-learned value for the given state.
     * @param numInCont numInCon[i] == number of terrs owned in continent i
     * @param numEnemyNeighbours The number of enemy neighbours
     * @return The value for this state
     */
    protected double getMachineLearnedValue(int[] numInCont, int numEnemyNeighbours)
    {
        // Below is 408200.txt:
        double value = 0.1831 - 0.0047 * numEnemyNeighbours;

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

    /**
     * Calculates a value for a territory for a particular player
     * @param territoryNum The territory number
     * @param playerNum The player number
     * @return A value for this territory
     */
    private double territoryValueForEvalFunc(int territoryNum, int playerNum, int[] finalDraftState)
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
    protected int getGreedyPick(int[] draftState, ArrayList<Integer> unownedCountries, int player, boolean selfish)
    {
        // First, figure out how much of each continent each player has, as well
        // as the enemy neighbours for each player
        int[][] numInCont = new int[board.getNumberOfPlayers()][board.getNumberOfContinents()];
        ArrayList<Integer>[] enemyNeighbours = new ArrayList[board.getNumberOfPlayers()];
        for (int i = 0; i < enemyNeighbours.length; ++i)
        {
            enemyNeighbours[i] = new ArrayList<Integer>();
        }
        ArrayList<Integer> unownedNeighbours = new ArrayList<Integer>();

        for (int terr = 0; terr < draftState.length; ++terr)
        {
            int owner = draftState[terr];
            if (owner == -1)
            {
                // Don't care about unowned terrs
                continue;
            }

            // Increment continent count for the owner
            numInCont[owner][countries[terr].getContinent()]++;

            // Search for additional enemy neighbours to add
            for (int neighbour : countries[terr].getAdjoiningCodeList())
            {
                if (draftState[neighbour] != owner && draftState[neighbour] != -1 && !enemyNeighbours[owner].contains((Integer) neighbour))
                {
                    enemyNeighbours[owner].add((Integer) neighbour);
                }
                else if (owner == player && draftState[neighbour] == -1 && !unownedNeighbours.contains((Integer)neighbour))
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
            int[] numEnemies = new int[board.getNumberOfPlayers()];
            for (int i = 0; i < numEnemies.length; ++i)
            {
                numEnemies[i] = enemyNeighbours[i].size();
            }

            // Any additional enemy neighbours with this pick?
            int[] additionalEnemy = new int[board.getNumberOfPlayers()];
            for (int neighbour : countries[terr].getAdjoiningCodeList())
            {
                int neighbourOwner = draftState[neighbour];
                if (neighbourOwner != -1 && neighbourOwner != player)
                {
                    // A new enemy for the neighbour owner
                    additionalEnemy[neighbourOwner] = 1;
                    if (!enemyNeighbours[player].contains((Integer) neighbour))
                    {
                        // A new enemy for this player also
                        numEnemies[player]++;
                    }
                }
            }

            // Calculate the value of picking this terr
            numInCont[player][countries[terr].getContinent()]++;
            double value = -Double.MAX_VALUE;
            if (selfish)
            {
                value = getMachineLearnedValue(numInCont[player], numEnemies[player]);
            }
            else
            {
                double sum = 0.0;
                for (int i = 0; i < board.getNumberOfPlayers(); ++i)
                {
                    if (i == player)
                    {
                        value = getMachineLearnedValue(numInCont[player], numEnemies[player]);
                        sum += value;
                    }
                    else
                    {
                        sum += getMachineLearnedValue(numInCont[i], numEnemies[i] + additionalEnemy[i]);
                    }
                }
                assert(sum > 0);
                value = value / sum;
            }
            numInCont[player][countries[terr].getContinent()]--;

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
     * @param selfish Whether or not to evaluate selfishly
     * @return The value of the passed in territory
     */
    protected double getValueOfTerr(int terr, int[] draftState, ArrayList<Integer> unownedCountries, int activePlayer, boolean selfish)
    {
        // Do nothing by default
        return 0.0;
    }

    /**
     * Removes all of the entries in the uct tree that are unreachable from this state
     * @param draftState The current state of the draft
     * @param unownedCountries The picks still available
     * @param player The player whose turn it is
     */
    protected void cleanUpUctTree(int[] draftState, int player)
    {
        Hashtable<ArrayList<Integer>,double[]> newUctTree = new Hashtable<ArrayList<Integer>,double[]>();

        ArrayList<Integer> state = new ArrayList<Integer>(draftState.length);
        for (int i = 0; i < draftState.length; ++i)
        {
            state.add(draftState[i]);
        }
        cleanUpUctTree_r(state, player, newUctTree);

        m_uctTree.clear();
        m_uctTree = newUctTree;
    }

    private void cleanUpUctTree_r(ArrayList<Integer> state, int player, Hashtable<ArrayList<Integer>,double[]> newUctTree)
    {
        // Put this state into the new tree, if it was in the old one
        if (!m_uctTree.containsKey(state))
        {
            return;
        }

        ArrayList<Integer> keeperState = new ArrayList<Integer>(state.size());
        for (int i = 0; i < state.size(); ++i)
        {
            keeperState.add(state.get(i));
        }
        newUctTree.put(keeperState, m_uctTree.get(state));
        for (int terr = 0; terr < state.size(); ++terr)
        {
            // Only consider possible picks
            if (state.get(terr) != -1)
            {
                continue;
            }

            state.set(terr, player);
            cleanUpUctTree_r(state, (player + 1) % board.getNumberOfPlayers(), newUctTree);
            state.set(terr, -1);
        }
    }
}

