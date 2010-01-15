/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.ArrayList;

/**
 *
 * @author Richard
 */
public abstract class Agent
{
    /**
     * A unique id for this agent
     */
    public int m_ID;

    /**
     * Constructor
     * @param ID A unique integer identifying this agent
     */
    public Agent()
    {
        m_ID = -1;
    }

    /**
     * Sets the ID of this agent.
     * @param ID The ID for this agent.
     */
    public void setID(int ID)
    {
        m_ID = ID;
    }

    /**
     * Method for making a selection in the table drafting game
     * @param availableItems The items available for picking
     * @return The picked item
     */
    public abstract Item makePick(ArrayList<Item> availableItems);

    /**
     * Returns the name of this agent
     * @return The name of this agent.
     */
    @Override
    public abstract String toString();
}
