/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import com.sillysoft.lux.util.ContinentIterator;
import com.sillysoft.lux.util.CountryIterator;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

/**
 *
 * @author Richard Gibson, Neesha Desai, Richard Zhao
 */
public class RL_Action_Drafter extends SmartDrafter
{

	/**
	 * The RL parameters  
	 */
	private double ALPHA = 0.2;
	private double EPSILON = 0.02;
	private double GAMMA = 1.0;
	private double LAMBDA = 0.0;

	private double previous_world_value = 0.0;
	private double current_world_value = 0.0;
	private String previous_action = "";

	/**
	 * Abstract Actions
	 */
	private HashMap<String, Double> actions = new HashMap<String, Double>();

	/**
	 * Abstract Binary States
	 */
	private HashMap<String, Boolean> states = new HashMap<String, Boolean>();

	/**
	 * Feature Vector with Q values
	 */
	private HashMap<String, Double> features = new HashMap<String, Double>();


	/**
	 * The initial values in the heuristic function.  Note that this does
	 * nothing if we are loading the heuristic from file.
	 */
	final private double INITIAL_VALUE = 0.5;

	/**
	 * The initial number of visits to each value in the heuristic function.
	 * Note that this does nothing if we are loading the heuristic from file.
	 */
	final private double INITIAL_WEIGHT = 1.0;

	/**
	 * Exploration parameter
	 */
	final private double c = 0.5;

	/**
	 * Keeps track of which states were encountered in the draft, so that we
	 * know which to update in the heuristic function once the game is over.
	 * Note that this is a bit of a hack.  Since there is no youLose() function,
	 * the winner has to take care of all heuristic updates at the end.
	 */
	private static ArrayList<int[]>[] m_indicesToUpdate;

	/**
	 * How often we capture the heuristic function by saving it to file
	 */
	private static final int CAPTURE_AFTER_EVERY = 2500;

	/**
	 * Keep track of the number of games we've played
	 */
	protected static Integer m_numGamesPlayed = 0;


	/**
	 * Learned action values
	 */
	protected static Hashtable<String,Integer> actionValues;


	/**
	 * Constructor
	 */
	public RL_Action_Drafter()
	{
		super();
		/**
		 * The file to store the heuristic function.
		 */
		HEURISTIC_DATA_FILENAME = "E:\\workspace\\Risk\\learnedHeuristic";

	}

	@Override
	public void setPrefs(int ID, Board board )
	{
		super.setPrefs(ID, board);

		if (m_indicesToUpdate == null)
		{
			m_indicesToUpdate = new ArrayList[board.getNumberOfPlayers()];
		}


		// RL parameters

		// set of actions
		actions.put("ChooseMostEmpty", 0.0);
		actions.put("ChooseLeastEmpty", 0.0);
		actions.put("ChooseMostMyTerritory", 0.0);
		actions.put("ChooseLeastMyTerritory", 0.0);
		actions.put("ChooseSmallest", 0.0);
		actions.put("ChooseLargest", 0.0);
		actions.put("ChooseMostAccessPoints", 0.0);
		actions.put("ChooseLeastAccessPoints", 0.0);        
		//actions.add("ChooseRandom");

		// set of states
		states.put("SoleOwnerOfContinent", false);
		states.put("InControlOfContinent", false);
		states.put("OpponentControlContinent", false);
		states.put("EmptyContinent", false);

		// feature vector

		Iterator<String> actionIter = actions.keySet().iterator();
		Iterator<String> stateIter = states.keySet().iterator();

		while (actionIter.hasNext())
		{
			String action = actionIter.next();

			while (stateIter.hasNext())
			{
				String state = stateIter.next();

				features.put(action + "+" + state, 0.0); 
			}
		}


		//        if (m_indicesToUpdate[ID] == null)
		//        {
		//            m_indicesToUpdate[ID] = new ArrayList<int[]>();
		//        }
		//
		//        if (m_learnedHeuristic == null)
		//        {
		//            // Construct the heuristic, or grab it from file if it already exists
		//            try
		//            {
		//                ObjectInputStream in = new ObjectInputStream(new FileInputStream(HEURISTIC_DATA_FILENAME));
		//                m_learnedHeuristic = (Hashtable<ArrayList<Integer>,double[]>[])in.readObject();
		//                in.close();
		//            }
		//            catch (FileNotFoundException e)
		//            {
		//                // Create a new heuristic
		//                m_learnedHeuristic = new Hashtable[numCountries];
		//                for (int i = 0; i < numCountries; ++i)
		//                {
		//                    m_learnedHeuristic[i] = new Hashtable<ArrayList<Integer>,double[]>();
		//                }
		//
		//                // First, we will need the number of countries in each continent
		//                int[] numTerrsInCont = new int[board.getNumberOfContinents()];
		//                for (Country terr : countries)
		//                {
		//                    numTerrsInCont[terr.getContinent()]++;
		//                }
		//
		//                for (Country terr : countries)
		//                {
		//                    // Initialize the entries in the heuristic
		//
		//                    // Some numbers we will need for indexing
		//                    int numNeighbours = terr.getNumberNeighbors();
		//                    Country[] neighbours = terr.getAdjoiningList();
		//                    int numNeighboursInCont = 0;
		//                    for (Country neighbour : neighbours)
		//                    {
		//                        if (terr.getContinent() == neighbour.getContinent())
		//                        {
		//                            numNeighboursInCont++;
		//                        }
		//                    }
		//
		//                    // Begin indexing:
		//                    // First, number of friendly neighbours
		//                    for (int numFriends = 0; numFriends <= numNeighbours; ++numFriends)
		//                    {
		//                        // Next, number of enemy neighbours
		//                        for (int numEnemies = 0; numEnemies <= numNeighbours - numFriends; ++numEnemies)
		//                        {
		//                            // Number of territories in continent we own
		//                            int maxNumFriendsInCont = numTerrsInCont[terr.getContinent()] - 1; // Can't include this terr
		//                            maxNumFriendsInCont -= Math.max(0, numEnemies - (numNeighbours - numNeighboursInCont));
		//
		//                            int minNumFriendsInCont = Math.max(0, numFriends - (numNeighbours - numNeighboursInCont));
		//
		//                            for (int numFriendsInCont = minNumFriendsInCont; numFriendsInCont <= maxNumFriendsInCont; ++numFriendsInCont)
		//                            {
		//                                // Number of territories in continent owned by enemies
		//                                int maxNumEnemiesInCont = numTerrsInCont[terr.getContinent()] - 1 - numFriendsInCont;
		//                                int minNumEnemiesInCont = Math.max(0, numEnemies - (numNeighbours - numNeighboursInCont));
		//
		//                                for (int numEnemiesInCont = minNumEnemiesInCont; numEnemiesInCont <= maxNumEnemiesInCont; ++numEnemiesInCont)
		//                                {
		//                                    // Max number of territories in continent owned by one enemy
		//                                    int minNumEnemyInCont = numEnemiesInCont == 0 ? 0 : ((numEnemiesInCont - 1) / (board.getNumberOfPlayers() - 1)) + 1;
		//                                    int maxNumEnemyInCont = numEnemiesInCont;
		//
		//                                    for (int numEnemyInCont = minNumEnemyInCont; numEnemyInCont <= maxNumEnemyInCont; ++numEnemyInCont)
		//                                    {
		//                                        // Entry
		//                                        ArrayList<Integer> index = new ArrayList<Integer>(5);
		//                                        index.add(numFriends);
		//                                        index.add(numEnemies);
		//                                        index.add(numFriendsInCont);
		//                                        index.add(numEnemiesInCont);
		//                                        index.add(numEnemyInCont);
		//
		//                                        double[] entry = { INITIAL_VALUE, INITIAL_WEIGHT};
		//
		//                                        m_learnedHeuristic[terr.getCode()].put(index, entry);
		//                                    }
		//                                }
		//                            }
		//                        }
		//                    }
		//                }
		//            }
		//            catch (Exception e)
		//            {
		//                assert(false);
		//            }
		//        }
	}

	// Update the heuristic function and save it to file
	@Override
	public String youWon()
	{
		String message = super.youWon();

		m_numGamesPlayed++;

//		// Make the updates to the heuristic function
//		for (int player = 0; player < m_indicesToUpdate.length; ++player)
//		{
//			if (m_indicesToUpdate[player] == null)
//			{
//				// This player is not an RL drafter
//				continue;
//			}
//
//			double newValue = (player == ID) ? 1.0 : 0.0;
//
//			for (int[] entry : m_indicesToUpdate[player])
//			{
//				// Retrieve the index and territory from the entry (see the hack
//				// in getPick())
//				int terr = entry[0];
//				ArrayList<Integer> index = new ArrayList<Integer>(entry.length - 1);
//				for (int i = 0; i < entry.length - 1; ++i)
//				{
//					index.add(entry[i+1]);
//				}
//
//				assert(m_learnedHeuristic[terr].containsKey(index));
//				double[] hashTableEntry = m_learnedHeuristic[terr].get(index);
//				double oldValue = hashTableEntry[0];
//				double numVisits = hashTableEntry[1] + 1;
//
//				// Average of values seen
//				hashTableEntry[0] = oldValue + (1.0 / numVisits)*(newValue - oldValue);
//				hashTableEntry[1] = numVisits;
//
//				m_learnedHeuristic[terr].put(index, hashTableEntry);
//			}
//
//			m_indicesToUpdate[player].clear();
//		}

		// Check if we should capture this heuristic function
		if ((m_numGamesPlayed % CAPTURE_AFTER_EVERY) == 0)
		{
			// Create the file name
			String fileName = HEURISTIC_DATA_FILENAME.substring(0, HEURISTIC_DATA_FILENAME.length() - 3); // Removes "dat"
			fileName += "" + m_numGamesPlayed + ".dat";

			// Check to make sure that the file does not already exist
			boolean exists = (new File(fileName)).exists();
			assert(!exists);

			// Save the heuristic to this file
			try
			{
				ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fileName));
				out.writeObject(m_learnedHeuristic);
				out.close();
			}
			catch (Exception e)
			{
				assert(false);
			}
		}

		return message;
	}

	@Override
	protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
	{


		int pick = -1;


		// Get reward    	
		// reward is calculated using the evaluation function
		// the difference in value from previous state to current state is the reward
		double[] current_world_values = evaluationFunction(draftState, 0, board);

		current_world_value = current_world_values[0];    	     	 
		double reward = current_world_value - previous_world_value;    	
		previous_world_value = current_world_value;

		// Update current states
		UpdateCurrentStates(draftState);

		// Choose an action according to the epsilon-greedy policy    	
		String action = ChooseAction();

		// Modify the Q-values according to reward
		UpdateQvalue(reward, action);

		previous_action = action;

		// Resolve the action to a concrete country
		pick = ResolveAction(action, draftState);

		// Return the pick    	
		return pick;
	}


	protected void UpdateCurrentStates(int[] draftState)
	{

		states.put("SoleOwnerOfContinent", false);
		states.put("InControlOfContinent", false);
		states.put("OpponentControlContinent", false);
		states.put("EmptyContinent", false);


		int numContinents = board.getNumberOfContinents();
		Country country[] = board.getCountries();


		for (int curContinent =0; curContinent< numContinents; curContinent++)
		{
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
					if (draftState[i] >= 0)
					{
						numOwned[draftState[i]]++;
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

			if (numOwned[0] != 0 && numOwned[1] == 0 && numOwned[2] == 0)
			{
				states.put("SoleOwnerOfContinent", true);
			}

			if (numOwned[0] >= cyInContinent.size()/2 )
			{
				states.put("InControlOfContinent", true);
			}

			if (numOwned[1] >= cyInContinent.size()/2 || numOwned[2] >= cyInContinent.size()/2)
			{
				states.put("OpponentControlContinent", true);
			}

			if (numOwned[0] == 0 && numOwned[1] == 0 && numOwned[2] == 0)
			{
				states.put("EmptyContinent", true);
			}

		}        

	}

	protected String ChooseAction()
	{

		String chosenAction = "";

		Iterator<String> featureIter = features.keySet().iterator();

		Iterator<String> actionIter = actions.keySet().iterator();    		


		// reset all action values
		while (actionIter.hasNext())
		{
			String action = actionIter.next();
			features.put(action, Double.valueOf(0.0));

		}

		// calculate the current action values
		while (featureIter.hasNext())
		{
			String feature = featureIter.next();

			actionIter = actions.keySet().iterator();    		

			while (actionIter.hasNext())
			{	

				String action = actionIter.next();

				if (feature.indexOf(action) != -1) {
					double value = actions.get(action).doubleValue();
					double previousValue = features.get(feature).doubleValue();
					features.put(feature, Double.valueOf(value + previousValue));
				}
			}

		}

		// pick from available actions

		// epsilon-greedy
		Random generator = new Random();
		double policyValue = generator.nextDouble();

		if (policyValue > EPSILON)
		{
			// exploit
			actionIter = actions.keySet().iterator();    		
			double maxActionValue = 0.0;
			String maxValuedAction = "";

			// reset all action values
			while (actionIter.hasNext())
			{
				String action = actionIter.next();

				if (actions.get(action).doubleValue() > maxActionValue)
				{
					if (IsActionAvailable(action))
					{	
						maxActionValue = actions.get(action).doubleValue();
						maxValuedAction = action;
					}
				}

			}	

			chosenAction = maxValuedAction;

		}
		else
		{
			// explore
			actionIter = actions.keySet().iterator();    		

			ArrayList<String> actionsList = new ArrayList<String>();


			// reset all action values
			while (actionIter.hasNext())
			{
				String action = actionIter.next();

				if (IsActionAvailable(action))
				{	
					actionsList.add(action);
				}

			}	

			int chosenActionNum = generator.nextInt(actionsList.size());
			chosenAction = actionsList.get(chosenActionNum);

		}

		return chosenAction;
	}

	protected Boolean IsActionAvailable(String action)
	{

		if (action == "ChooseMostEmpty")
		{
			return true;
		}
		else if (action == "ChooseLeastEmpty")
		{
			return true;
		}
		else if (action == "ChooseMostMyTerritory")
		{
			return true;
		}
		else if (action == "ChooseLeastMyTerritory")
		{
			return true;
		}
		else if (action == "ChooseSmallest")
		{
			return true;
		}
		else if (action == "ChooseLargest")
		{
			return true;
		}
		else if (action == "ChooseMostAccessPoints")
		{
			return true;
		}
		else if (action == "ChooseLeastAccessPoints")
		{
			return true;
		}
		return false;
	}


	protected void UpdateQvalue(double reward, String action)
	{    	
		Iterator<String> stateIter = states.keySet().iterator();    		

		ArrayList<String> statesList = new ArrayList<String>();

		while (stateIter.hasNext())
		{
			String state = stateIter.next();
			if (states.get(state) == true)
			{
				statesList.add(state);
			}
		}


		// Q(s,a) = Q(s,a) + alpha[r+gamma * Q(s',a') - Q(s,a)]
		for (int i=0; i<statesList.size(); i++)
		{
			double Qvalue = features.get(action + statesList.get(i)).doubleValue();

			features.put(action + statesList.get(i), Qvalue + ALPHA *(reward/statesList.size()));

		}

	}


	protected int ResolveAction(String action, int[] draftState)
	{

		int pick = -1;


		int numContinents = board.getNumberOfContinents();
		Country country[] = board.getCountries();

		int numBorders[] = new int[numContinents];
		int numOwned[][] = new int[board.getNumberOfPlayers()][numContinents];
		int numEmpty[] = new int[numContinents];

		for (int curContinent =0; curContinent< numContinents; curContinent++)
		{
			// List of countries in current continent
			ArrayList<Country> cyInContinent = new ArrayList<Country>();

			// Get list of countries in current continent
			// number of territories owned by each player in the current continent,
			// number of borders to the current continent
			for (int i = 0; i < country.length; i++ )
			{
				if (country[i].getContinent() == curContinent)
				{
					cyInContinent.add(country[i]);
					if (draftState[i] >= 0)
					{
						numOwned[draftState[i]][curContinent]++;
					}
					else
					{
						numEmpty[curContinent]++;
					}

					Country border[] = country[i].getAdjoiningList();

					for (int j = 0; j < border.length; j++ )
					{
						if (border[j].getContinent() != curContinent)
						{
							numBorders[numContinents]++;
						}
					}
				}
			}                	
		}        


		if (action == "ChooseMostEmpty")
		{
			int chosenContinent = -1;
			int mostEmpty = 0;

			for (int curContinent =0; curContinent< numContinents; curContinent++)
			{

				if (numEmpty[curContinent] > mostEmpty)
				{
					mostEmpty = numEmpty[curContinent];
					chosenContinent = curContinent;
				}

			}

			return pickCountryInContinent(chosenContinent);
		}
		else if (action == "ChooseLeastEmpty")
		{
			int chosenContinent = -1;
			int leastEmpty = 100000;

			for (int curContinent =0; curContinent< numContinents; curContinent++)
			{

				if (numEmpty[curContinent] < leastEmpty)
				{
					leastEmpty = numEmpty[curContinent];
					chosenContinent = curContinent;
				}

			}

			return pickCountryInContinent(chosenContinent);
		}
		else if (action == "ChooseMostMyTerritory")
		{
			int chosenContinent = -1;
			int mostMy = 0;

			for (int curContinent =0; curContinent< numContinents; curContinent++)
			{

				if (numOwned[0][curContinent] > mostMy)
				{
					mostMy = numOwned[0][curContinent];
					chosenContinent = curContinent;
				}

			}

			return pickCountryInContinent(chosenContinent);
		}
		else if (action == "ChooseLeastMyTerritory")
		{
			int chosenContinent = -1;
			int leastMy = 100000;

			for (int curContinent =0; curContinent< numContinents; curContinent++)
			{

				if (numOwned[0][curContinent] < leastMy)
				{
					leastMy = numOwned[0][curContinent];
					chosenContinent = curContinent;
				}

			}

			return pickCountryInContinent(chosenContinent);
		}
		else if (action == "ChooseSmallest")
		{
			int chosenContinent = -1;
			int smallest = 100000;

			for (int curContinent =0; curContinent< numContinents; curContinent++)
			{

				int contiSize = numOwned[0][curContinent]+numOwned[1][curContinent]+numOwned[2][curContinent]+ numEmpty[curContinent];
				if (contiSize < smallest)
				{
					smallest = contiSize;
					chosenContinent = curContinent;
				}

			}

			return pickCountryInContinent(chosenContinent);
		}
		else if (action == "ChooseLargest")
		{
			int chosenContinent = -1;
			int largest = 0;

			for (int curContinent =0; curContinent< numContinents; curContinent++)
			{

				int contiSize = numOwned[0][curContinent]+numOwned[1][curContinent]+numOwned[2][curContinent]+ numEmpty[curContinent];
				if (contiSize > largest)
				{
					largest = contiSize;
					chosenContinent = curContinent;
				}

			}

			return pickCountryInContinent(chosenContinent);
		}
		else if (action == "ChooseMostAccessPoints")
		{
			int chosenContinent = -1;
			int mostAccess = 0;

			for (int curContinent =0; curContinent< numContinents; curContinent++)
			{

				if (numBorders[curContinent] > mostAccess)
				{
					mostAccess = numBorders[curContinent];
					chosenContinent = curContinent;
				}

			}

			return pickCountryInContinent(chosenContinent);
		}
		else if (action == "ChooseLeastAccessPoints")
		{
			int chosenContinent = -1;
			int leastAccess = 100000;

			for (int curContinent =0; curContinent< numContinents; curContinent++)
			{

				if (numBorders[curContinent] < leastAccess)
				{
					leastAccess = numBorders[curContinent];
					chosenContinent = curContinent;
				}

			}

			return pickCountryInContinent(chosenContinent);
		}

		return pick;

	}


	// return an unowned country-code in <continent>, preferably near others we own
	// If there are no countries left in the given continent then pick a country touching us.
	protected int pickCountryInContinent(int continent)
	{
		CountryIterator continentIter = new ContinentIterator(continent, countries);
		while (continentIter.hasNext())
		{
			Country c = continentIter.next();
			if (c.getOwner() == -1 && c.getNumberPlayerNeighbors(ID) > 0)
				return c.getCode();
		}

		// we neighbor none of them, so pick the open country with the fewest neighbors
		continentIter = new ContinentIterator(continent, countries);
		int bestCode = -1;
		int fewestNeib = 1000000;
		while (continentIter.hasNext())
		{
			Country c = continentIter.next();
			if (c.getOwner() == -1 && c.getNumberNeighbors() < fewestNeib)
			{
				bestCode = c.getCode();
				fewestNeib = c.getNumberNeighbors();
			}
		}

		if (bestCode == -1)
		{
			// There are no unowned countries in this continent.
			return pickCountryTouchingUs();
		}

		return bestCode;
	}


}
