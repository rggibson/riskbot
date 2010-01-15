/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.Arrays;

/**
 *
 * @author Richard
 */
public class Item
{
    /**
     * How much this item is worth to each player
     */
    public int[] m_values;

    /**
     * Constructor
     * @param values The value of this item to each player
     */
    public Item(int[] values)
    {
        m_values = values;
    }

    @Override
    public String toString()
    {
        return Arrays.toString(m_values);
    }
}
