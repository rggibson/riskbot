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
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
	protected static String HEURISTIC_DATA_FILENAME;

	/**
	 * The RL parameters  
	 */
	private double ALPHA = 0.2;
	private double EPSILON = 0.2;
	private double GAMMA = 1.0;
	private double LAMBDA = 0.0;

	private double previous_world_value = 0.0;
	private double current_world_value = 0.0;
	private String previous_action = "";

	private static boolean initialized = false;
	/**
	 * Abstract Actions
	 */
	private static LinkedHashMap<String, Double> actions = new LinkedHashMap<String, Double>();

	/**
	 * Abstract Binary States
	 */
	private static LinkedHashMap<String, Boolean> states = new LinkedHashMap<String, Boolean>();

	/**
	 * (Previous) Abstract Binary States
	 */
	private static LinkedHashMap<String, Boolean> prevStates = new LinkedHashMap<String, Boolean>();


	/**
	 * Feature Vector with Q values
	 */
	private static LinkedHashMap<String, Double> features = new LinkedHashMap<String, Double>();


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
	private static final int CAPTURE_AFTER_EVERY = 100;

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

		if (initialized == false)
		{

			// set of actions and their current total values from features
			actions.put("ChooseMostEmpty", 0.0);
			actions.put("ChooseLeastEmpty", 0.0);
			actions.put("ChooseMostMyTerritory", 0.0);
			actions.put("ChooseLeastMyTerritory", 0.0);
			actions.put("ChooseSmallest", 0.0);
			actions.put("ChooseLargest", 0.0);
			actions.put("ChooseMostAccessPoints", 0.0);
			actions.put("ChooseLeastAccessPoints", 0.0);        
			//actions.add("ChooseRandom");


			// set of previous states
			prevStates.put("SoleOwnerOfContinent", false);
			prevStates.put("InControlOfContinent", false);
			prevStates.put("OpponentControlContinent", false);
			prevStates.put("EmptyContinent", false);
			prevStates.put("Constant", true);

			// set of states
			states.put("SoleOwnerOfContinent", false);
			states.put("InControlOfContinent", false);
			states.put("OpponentControlContinent", false);
			states.put("EmptyContinent", false);
			states.put("Constant", true);


			// feature vector containing set of features and their weights
			Iterator<String> actionIter = actions.keySet().iterator();

			while (actionIter.hasNext())
			{
				String action = actionIter.next();

				Iterator<String> stateIter = states.keySet().iterator();

				while (stateIter.hasNext())
				{
					String state = stateIter.next();

					features.put(action + "+" + state, 0.0); 
				}
			}

			initialized = true;
			System.out.println("I have been initialized.");
		}



	}

	// Update the heuristic function and save it to file
	@Override
	public String youWon()
	{
		super.youWon();

		String message = "Ha Ha.  I out-drafted you again!";

		m_numGamesPlayed++;


		// Check if we should capture this heuristic function
		if ((m_numGamesPlayed % CAPTURE_AFTER_EVERY) == 0)
		{
			// Create the file name


			//			String fileName = HEURISTIC_DATA_FILENAME.substring(0, HEURISTIC_DATA_FILENAME.length() - 3); // Removes "dat"
			//			fileName += "" + m_numGamesPlayed + ".dat";			
			//
			//			// Check to make sure that the file does not already exist
			//			boolean exists = (new File(fileName)).exists();
			//			assert(!exists);

			// Save the heuristic to this file
			//			try
			//			{
			//				FileOutputStream fos =  new FileOutputStream(fileName);
			//ObjectOutputStream out = new ObjectOutputStream(fos);

			// print out the feature vector
			Iterator<String> actionIter = actions.keySet().iterator();

			while (actionIter.hasNext())
			{
				String action = actionIter.next();

				Iterator<String> stateIter = states.keySet().iterator();

				while (stateIter.hasNext())
				{
					String state = stateIter.next();

					System.out.println(features.get(action + "+" + state).toString());
					//						fos.write(features.get(action + "+" + state).toString().getBytes());
					//						fos.write(" ".getBytes());

				}

				//					fos.write("\n".getBytes());
			}


			//				fos.close();
			//			}
			//			catch (Exception e)
			//			{
			//				assert(false);
			//			}
		}

		return message;
	}

	@Override
	public int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
	{


		int pick = -1;


		System.out.println("===== Start of episode =====");
		// Get reward    	
		// reward is calculated using the evaluation function
		// the difference in value from previous state to current state is the reward
		double[] current_world_values = myOwnEvaluationFunction(draftState);

		current_world_value = current_world_values[0];    	     	 
		double reward = current_world_value - previous_world_value;    	
		previous_world_value = current_world_value;


		System.out.println("reward: " + reward);

		// Update current states
		UpdateCurrentStates(draftState);

		printStateValues();

		// Choose an action according to the epsilon-greedy policy    	
		String action = ChooseAction();

		// Modify the Q-values according to reward
		UpdateQvalue(reward, action);

		printActionValues();

		printFeatureVector();

		previous_action = action;

		// Resolve the action to a concrete country
		pick = ResolveAction(action, draftState);

		// Return the pick    	
		return pick;
	}


	protected void UpdateCurrentStates(int[] draftState)
	{

		prevStates.put("SoleOwnerOfContinent", states.get("SoleOwnerOfContinent"));
		prevStates.put("InControlOfContinent", states.get("InControlOfContinent"));
		prevStates.put("OpponentControlContinent", states.get("OpponentControlContinent"));
		prevStates.put("EmptyContinent", states.get("EmptyContinent"));
		prevStates.put("Constant", true);

		states.put("SoleOwnerOfContinent", false);
		states.put("InControlOfContinent", false);
		states.put("OpponentControlContinent", false);
		states.put("EmptyContinent", false);
		states.put("Constant", true);


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


		Iterator<String> actionIter = actions.keySet().iterator();    		

		// reset all action total values
		while (actionIter.hasNext())
		{
			String action = actionIter.next();
			actions.put(action, Double.valueOf(0.0));

		}

		Iterator<String> featureIter = features.keySet().iterator();

		// calculate the current action values
		while (featureIter.hasNext())
		{
			String feature = featureIter.next();

			actionIter = actions.keySet().iterator();    		

			// sum the values of actions
			while (actionIter.hasNext())
			{	
				String action = actionIter.next();

				if (feature.indexOf(action) != -1) {

					// get the state
					String stateInFeature = feature.substring( feature.indexOf("+")+1 );
					// if state is true

					//System.out.println("@" + stateInFeature + "@");
					if (states.get(stateInFeature) == true)
					{
						double sumValue = actions.get(action).doubleValue();
						double featureValue = features.get(feature).doubleValue();
						actions.put(action, Double.valueOf(sumValue + featureValue));
					}
				}
			}

		}

		// pick from available actions

		// epsilon-greedy
		Random generator = new Random();
		double policyValue = generator.nextDouble();

		System.out.println("policy value: " + policyValue + " and EPSILON: " + EPSILON);


		if (policyValue > EPSILON)
		{
			// exploit
			actionIter = actions.keySet().iterator();    		
			double maxActionValue = 0.0;
			LinkedList<String> maxValuedActions = new LinkedList<String>();

			// choose largest valued action
			while (actionIter.hasNext())
			{
				String action = actionIter.next();

				if (actions.get(action).doubleValue() == maxActionValue)
				{
					if (IsActionAvailable(action))
					{	
						maxValuedActions.add(action);
					}
				}
				else if (actions.get(action).doubleValue() > maxActionValue)
				{
					if (IsActionAvailable(action))
					{	
						maxActionValue = actions.get(action).doubleValue();
						maxValuedActions.clear();
						maxValuedActions.add(action);
					}
				}

			}

			int num = generator.nextInt(maxValuedActions.size());
			System.out.println("random number: " + num);
			chosenAction = maxValuedActions.get(num);

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

		System.out.println("chosen action: " + chosenAction);

		return chosenAction;
	}

	protected Boolean IsActionAvailable(String action)
	{

		// action is always available
		return true;

		//		if (action == "ChooseMostEmpty")
		//		{
		//			return true;
		//		}
		//		else if (action == "ChooseLeastEmpty")
		//		{
		//			return true;
		//		}
		//		else if (action == "ChooseMostMyTerritory")
		//		{
		//			return true;
		//		}
		//		else if (action == "ChooseLeastMyTerritory")
		//		{
		//			return true;
		//		}
		//		else if (action == "ChooseSmallest")
		//		{
		//			return true;
		//		}
		//		else if (action == "ChooseLargest")
		//		{
		//			return true;
		//		}
		//		else if (action == "ChooseMostAccessPoints")
		//		{
		//			return true;
		//		}
		//		else if (action == "ChooseLeastAccessPoints")
		//		{
		//			return true;
		//		}
		//		return false;
	}


	protected void UpdateQvalue(double reward, String action)
	{    	

		// List of active state variables
		ArrayList<String> statesList = new ArrayList<String>();
		Iterator<String> stateIter = states.keySet().iterator();

		while (stateIter.hasNext())
		{
			String state = stateIter.next();
			if (states.get(state) == true)
			{
				statesList.add(state);
			}
		}


		// List of previous active state variables
		ArrayList<String> prevStatesList = new ArrayList<String>();
		Iterator<String> presStateIter = prevStates.keySet().iterator();

		while (presStateIter.hasNext())
		{
			String state = presStateIter.next();
			if (prevStates.get(state) == true)
			{
				prevStatesList.add(state);
			}
		}

		double prevQvalue = 0.0;
		if (previous_action != "")
		{
			for (int i=0; i<prevStatesList.size(); i++)				
			{
				//System.out.println("!" + previous_action  + "!");
				//System.out.println("!" + prevStatesList.get(i) + "!");

				prevQvalue += features.get(previous_action + "+" + prevStatesList.get(i)).doubleValue();
			}
		}

		double Qvalue = 0.0;
		for (int i=0; i<statesList.size(); i++)
		{	
			Qvalue += features.get(action + "+" + statesList.get(i)).doubleValue();	
		}

		// Q(s,a) = Q(s,a) + alpha * delta
		// where delta = [r + gamma * Q(s',a') - Q(s,a)]				
		for (int i=0; i<statesList.size(); i++)
		{
			double delta = reward + GAMMA * Qvalue - prevQvalue;

			System.out.println("GAMMA: " + GAMMA + " prevQvalue: " + prevQvalue + " Qvalue: " + Qvalue);
			double previousFeatureValue = features.get(action + "+" + statesList.get(i));
			features.put(action + "+" + statesList.get(i), previousFeatureValue + ALPHA * delta / statesList.size());			

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
							numBorders[curContinent]++;
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
				if (numEmpty[curContinent] >= mostEmpty)
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
				if (numEmpty[curContinent] <= leastEmpty && numEmpty[curContinent] > 0)
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
				if (numOwned[0][curContinent] >= mostMy && numEmpty[curContinent] > 0)
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
				if (numOwned[0][curContinent] <= leastMy && numEmpty[curContinent] > 0)
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

				if (contiSize <= smallest && numEmpty[curContinent] > 0)
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

				if (contiSize >= largest && numEmpty[curContinent] > 0)
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
				if (numBorders[curContinent] >= mostAccess && numEmpty[curContinent] > 0)
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
				if (numBorders[curContinent] <= leastAccess && numEmpty[curContinent] > 0)
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

		continentIter = new ContinentIterator(continent, countries);		
		while (continentIter.hasNext())
		{
			Country c = continentIter.next();
			if (c.getOwner() == -1 && c.getNumberNeighbors() < fewestNeib)
			{
				bestCode = c.getCode();
				fewestNeib = c.getNumberNeighbors();
			}
			//System.out.println("neighbours: " + c.getNumberNeighbors() + "  fewest" + fewestNeib);

		}

		if (bestCode == -1)
		{
			System.out.println("BUG: This should not happen.");
			System.out.println("continent: " + continent);
		}

		return bestCode;
	}


	public double[] myOwnEvaluationFunction(int[] draft)
	{

		double playerValue[] = new double[board.getNumberOfPlayers()];
		double totalValue = 0;

		// Calculate the cumulative values of all territories for each player
		for (int i = 0; i < board.getNumberOfPlayers(); i++)
		{
			for (int j = 0; j < draft.length; j++)
			{
				playerValue[i] += territoryValueForEvalFunc(j, i, draft);
			}
			totalValue = totalValue + playerValue[i];
		}

		// Convert values to probabilities of winning
		for (int i = 0; i < board.getNumberOfPlayers(); i++)
		{
			if (totalValue > 0)
			{
				playerValue[i] = playerValue[i] / totalValue;
			}
		}
		return playerValue;
	}


	public String name()
	{
		return "RL_Action_Drafter";
	}


	public void printFeatureVector()
	{

		System.out.println("Feature Vector values: ");

		// print out the feature vector
		Iterator<String> actionIter = actions.keySet().iterator();
		while (actionIter.hasNext())
		{
			String action = actionIter.next();

			Iterator<String> stateIter = states.keySet().iterator();

			while (stateIter.hasNext())
			{
				String state = stateIter.next();

				System.out.print(features.get(action + "+" + state).toString() + "  ");

			}
			System.out.println(" ");
		}		
	}

	public void printActionValues()
	{
		System.out.println("Action values: ");
		// print the action values

		Iterator<String> actionIter = actions.keySet().iterator();
		while (actionIter.hasNext())
		{
			String action = actionIter.next();

			System.out.println(action + ": " + actions.get(action).toString() + "  ");
		}
	}

	public void printStateValues()
	{
		System.out.println("States: ");
		// print the state values

		Iterator<String> statesIter = states.keySet().iterator();
		while (statesIter.hasNext())
		{
			String state = statesIter.next();

			System.out.println(state + ": " + states.get(state).toString() + "  ");
		}
	}
}
