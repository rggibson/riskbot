/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import java.util.ArrayList;

/**
 * The drafter makes picks greedily, meaning that it makes the pick that has
 * the biggest gain in the sense of a one-step lookahead.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class GreedyDrafter extends SmartDrafter
{
    /**
     * Whether this drafter evaluates selfishly or not
     */
    private static final boolean SELFISH = false;

    public GreedyDrafter()
    {
        super();
    }

    @Override
    public String name()
    {
        if (SELFISH)
        {
            return "GreedyDrafter-Selfish";
        }
        else
        {
            return "GreedyDrafter";
        }
    }

    /**
     * The drafting method.
     * @param draftState Who owns what
     * @param unownedCountries The territories left to be picked
     * @return The index of the territory to pick
     */
    @Override
    protected int getPick(int[] draftState, ArrayList<Integer> unownedCountries)
    {
        return getGreedyPick(draftState, unownedCountries, ID, SELFISH);
    }
}
