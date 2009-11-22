/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.util.ContinentIterator;
import com.sillysoft.lux.util.CountryIterator;
import com.sillysoft.lux.util.CountryStack;
import com.sillysoft.lux.*;
import com.sillysoft.lux.util.BoardHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A SmartDrafter version of Quo, so that TurnOrderSwapper can use Quo
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class QuoClone extends SmartDrafter
{
    public int getPick(int[] draftState, ArrayList<Integer> unownedTerritories)
    {
	// our first choice is the continent with the least # of borders that is totally empty
	if (goalCont == -1 || ! playerOwnsContinentCountryClone(-1, goalCont, draftState))
		{
		setGoalToLeastBordersContClone(draftState);
		}

	// so now we have picked a cont...
	return pickCountryInContinentClone(goalCont, draftState, unownedTerritories);
    }

    private boolean playerOwnsContinentCountryClone(int player, int continent, int[] draftState)
    {
	// Loop through all the countries in the desired continent, checking to
	// see if the desired player owns them. If we find a country that he does
	// own, then we return true:
	ContinentIterator iter = new ContinentIterator( continent, countries );
        while (iter.hasNext())
        {
            if (draftState[iter.next().getCode()] == player)
            {
                return true;
            }
        }
	// Otherwise we return false:
	return false;
    }

    protected void setGoalToLeastBordersContClone(int[] draftState)
    {
        goalCont = -1;

        int[] borderSizes = new int[numContinents];
        int smallBorders = 1000000;	// the size of the smallest borders cont

        // first loop through and find the smallest totally empty cont
        for (int i = 0; i < numContinents; i++)
        {
            if (board.getContinentBonus(i) > 0)
            {
                borderSizes[i] = BoardHelper.getContinentSize(i, countries);
                if (borderSizes[i] < smallBorders && playerOwnsContinentClone(-1, i, draftState))
                {
                    smallBorders = borderSizes[i];
                    goalCont = i;
                }
            }
        }

        // if there were no empty conts then next we would like the cont with the least # of borders that is partially empty
        if (goalCont == -1)
        {
            smallBorders = 1000000;
            for (int i = 0; i < numContinents; i++)
            {
                if (board.getContinentBonus(i) > 0)
                {
                    if (borderSizes[i] < smallBorders && playerOwnsContinentCountryClone(-1, i, draftState))
                    {
                        smallBorders = borderSizes[i];
                        goalCont = i;
                    }
                }
            }
        }
        // There is the possibility that no cont will be chosen, if all the continents with
        // open countries are worth 0 or less income
    }

    public boolean playerOwnsContinentClone(int player, int continent, int[] draftState)
    {
        // Loop through all the countries in the desired continent, checking to
        // see if the desired player owns them. If we find a country that he
        // does not own, then we return false:
        ContinentIterator iter = new ContinentIterator(continent, countries);
        while (iter.hasNext())
        {
            if (draftState[iter.next().getCode()] != player)
            {
                return false;
            }
        }

        // Otherwise we return true:
        return true;
    }

    protected int pickCountryInContinentClone(int continent, int[] draftState, ArrayList<Integer> unownedTerritories)
    {
        CountryIterator continentIter = new ContinentIterator(continent, countries);
        while (continentIter.hasNext())
        {
            Country c = continentIter.next();
            if (draftState[c.getCode()] == -1 && getNumberPlayerNeighborsClone(c, draftState, ID) > 0)
            {
                return c.getCode();
            }
        }

        // we neighbor none of them, so pick the open country with the fewest neighbors
        continentIter = new ContinentIterator(continent, countries);
        int bestCode = -1;
        int fewestNeib = 1000000;
        while (continentIter.hasNext())
        {
            Country c = continentIter.next();
            if (draftState[c.getCode()] == -1 && c.getNumberNeighbors() < fewestNeib)
            {
                bestCode = c.getCode();
                fewestNeib = c.getNumberNeighbors();
            }
        }

        if (bestCode == -1)
        {
            // There are no unowned countries in this continent.
            return pickCountryTouchingUsClone(draftState, unownedTerritories);
        }

        return bestCode;
    }

    protected int getNumberPlayerNeighborsClone(Country c, int[] draftState, int player)
    {
        int[] neighbours = c.getAdjoiningCodeList();
        int count = 0;
        for (int neighbour : neighbours)
        {
            if (draftState[neighbour] == player)
            {
                count++;
            }
        }

        return count;
    }

    protected int getNumberNotPlayerNeighborsClone(Country c, int[] draftState, int player)
    {
        int[] neighbours = c.getAdjoiningCodeList();
        int count = 0;
        for (int neighbour : neighbours)
        {
            if (draftState[neighbour] != player)
            {
                count++;
            }
        }

        return count;
    }

    protected int pickCountryTouchingUsClone(int[] draftState, ArrayList<Integer> unownedTerritories)
    {
        int maxTouches = -1;
        int maxCode = -1;	// the country code of the best place so far
        // Loop through all the unowned countries
        for (int openTerr : unownedTerritories)
        {
            if (getNumberPlayerNeighborsClone(countries[openTerr], draftState, ID) > maxTouches && board.getContinentBonus(countries[openTerr].getContinent()) >= 0)
            {
                maxTouches = getNumberPlayerNeighborsClone(countries[openTerr], draftState, ID);
                maxCode = openTerr;
            }
        }

        if (maxTouches < 1)
        {
            // Then no open countries touch any of our countries directly.
            // Do a search outwards to find the closest unowned country to us.

            List ourBorderCountries = new ArrayList();
            for (int terr = 0; terr < draftState.length; ++terr)
            {
                if (draftState[terr] != ID)
                {
                    continue;
                }
                Country open = countries[terr];
                if (getNumberNotPlayerNeighborsClone(open, draftState, ID) > 0)
                {
                    ourBorderCountries.add(open);
                }
            }

            return closestCountryWithOwnerClone(ourBorderCountries, -1, draftState);
        }

        return maxCode;
    }

    public int closestCountryWithOwnerClone(List startingCountryList, int owner, int[] draftState)
    {
        if (startingCountryList.size() < 1)
        {
            return -1;
        }

        int[] startingCodes = new int[startingCountryList.size()];
        for (int i = 0; i < startingCountryList.size(); i++)
        {
            startingCodes[i] = ((Country) startingCountryList.get(i)).getCode();
        }

        // We keep track of which countries we have already seen (so we don't
        // consider the same country twice). We do it with a boolean array, with
        // a true/false value for each of the countries:
        boolean[] haveSeenAlready = new boolean[draftState.length];
        for (int i = 0; i < draftState.length; i++)
        {
            haveSeenAlready[i] = false;
        }

        // Create a Q to store the country-codes and their distance from the
        // start country:
        CountryStack Q = new CountryStack();

        for (int i = 0; i < startingCodes.length; i++)
        {
            haveSeenAlready[startingCodes[i]] = true;
            Q.pushWithValue(countries[startingCodes[i]], 0);
        }

        int testCode = Q.pop();
        int distanceSoFar = 0;

        // Loop over the expand-enqueue until either the correct
        // country is found or there are no more countries left:
        while (true)
        {
            Country[] neighbors = countries[testCode].getAdjoiningList();

            for (int i = 0; i < neighbors.length; i++)
            {
                if (!haveSeenAlready[neighbors[i].getCode()])
                {
                    Q.pushWithValue(neighbors[i], distanceSoFar + 1);
                    haveSeenAlready[neighbors[i].getCode()] = true;
                }
            }

            if (Q.isEmpty())
            {
                return -2;
            }

            distanceSoFar = Q.topValue();
            testCode = Q.pop();

            if (draftState[testCode] == owner)
            {
                return testCode;
            }
        }
    }
}
