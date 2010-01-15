/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.ArrayList;

/**
 *
 * @author Richard
 */
public class GreedyAgent extends Agent
{
    /**
     * Constructor
     */
    public GreedyAgent()
    {

    }

    /**
     * Returns the name of this agent.
     * @return The name of this agent.
     */
    public String toString()
    {
        return "Greedy";
    }

    /**
     * Makes a selection from the available picks
     * @param availablePicks The picks available
     * @return The pick selected.
     */
    public Item makePick(ArrayList<Item> availablePicks)
    {
        // Go through the items and select the one that has the most value to us
        Item best = null;
        for (Item item : availablePicks)
        {
            if (best == null || item.m_values[m_ID] > best.m_values[m_ID])
            {
                best = item;
            }
        }

        return best;
    }
}
