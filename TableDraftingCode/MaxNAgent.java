/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author Richard
 */
public class MaxNAgent extends Agent
{
    private final int MAX_DEPTH = 10;

    /**
     * Constructor
     */
    public MaxNAgent()
    {
        super();
    }

    public String toString()
    {
        return "MaxN";
    }

    public Item makePick(ArrayList<Item> availableItems)
    {
        // Set the alarm to go off when time is up
        long alarm = System.currentTimeMillis() + Main.TIME_LIMIT;
        if (MAX_DEPTH != -1)
        {
            alarm = Long.MAX_VALUE;
        }

        Node root = new Node(availableItems, m_ID);

        // Perform iterative deepening MaxN search
        int depth = 1;
        Item pick = null;
        while (depth <= availableItems.size() && System.currentTimeMillis() < alarm && depth <= MAX_DEPTH)
        {
            Item nextPick = maxNSearch(root, availableItems, depth, alarm);

            if (nextPick != null)
            {
                // A good pick
                pick = nextPick;
                depth += Main.numPlayers;
            }
            else
            {
                assert System.currentTimeMillis() >= alarm : "Error: Time actually didn't expire!";
                break;
            }
        }

        return pick;
    }

    /**
     * Performs MaxN search to determine which pick to make next.
     * @param root The starting state
     * @param availableItems The items to choose from
     * @param depth The ply-depth to search to in the tree
     * @param alarm When we must stop searching and exit
     * @return The best looking item to pick, according to the MaxN algorithm
     */
    private Item maxNSearch(Node root, ArrayList<Item> availableItems, int depth, long alarm)
    {
        double[] accumulatingScores = new double[Main.numPlayers];
        return maxNSearch_r(root, availableItems, depth, alarm, m_ID, accumulatingScores);
    }

    /**
     * The recursive portion of the MaxN algorithm.
     * @param node The current node in the search
     * @param availableItems The items available from this node
     * @param depth How much deeper into the tree we will go
     * @param alarm Need to check when time is up
     * @param player The player whose turn it is at this node
     * @param accumulatingScores The values of the items chosen by the players to this point
     * @return The move that player should take from this node
     */
    private Item maxNSearch_r(Node node, ArrayList<Item> availableItems, int depth, long alarm, int player, double[] accumulatingScores)
    {
        if (System.currentTimeMillis() >= alarm)
        {
            // Time is up; use pick from last iteration
            return null;
        }

        if (depth <= 0 || availableItems.isEmpty())
        {
            // Leaf node for this iteration.
            node.setMaxNValue(accumulatingScores);
            for (Item child : node.getChildren().keySet())
            {
                node.getChildren().put(child, null);
            }
            return null; // Don't know what move to take from here
        }

        // Determine value of the top ceil(depth / numPlayers) child nodes
        Item[] topPicks = new Item[(int)Math.ceil((double)depth/Main.numPlayers)];
        HashMap<Item,Node> children = node.getChildren();
        Set<Item> childrenKeys = children.keySet();
        Iterator<Item> iter = childrenKeys.iterator();
        while (iter.hasNext())
        {
            Item child = iter.next();

            // Get the value of picking this item
            double value = child.m_values[player];

            // Find its rank in the current top picks
            int rank = topPicks.length;
            while (rank > 0 && (topPicks[rank-1] == null || topPicks[rank - 1].m_values[player] <= value))
            {
                if (topPicks[rank-1] == null || topPicks[rank-1].m_values[player] < value)
                {
                    rank--;
                }
                else
                {
                    // We have a tie.  Time to break it.
                    double thisSum = 0;
                    double thatSum = 0;
                    for (int i = 0; i < Main.numPlayers; ++i)
                    {
                        if (i != player)
                        {
                            thisSum += child.m_values[i];
                            thatSum += topPicks[rank-1].m_values[i];
                        }
                    }

                    if (thisSum > thatSum)
                    {
                        // This item is better
                        rank--;
                    }
                    else
                    {
                        // The old item is better
                        break;
                    }
                }
            }

            // Adjust the top picks to make room for the new item
            for (int j = topPicks.length - 2; j >= rank; --j)
            {
                topPicks[j+1] = topPicks[j];
            }

            // Put the item into place
            if (rank < topPicks.length)
            {
                topPicks[rank] = child;
            }
        }

        Item bestPick = null;
        for (Item child : topPicks)
        {
            // Find the value of this pick
            accumulatingScores[player] += child.m_values[player];
            availableItems.remove(child);
            Node childNode = new Node(availableItems, (player + 1) % Main.numPlayers);
            node.addChild(child, childNode);

            maxNSearch_r(childNode, availableItems, depth - 1, alarm, (player + 1) % Main.numPlayers, accumulatingScores);

            // Undo the pick
            accumulatingScores[player] -= child.m_values[player];
            availableItems.add(child);

            if (System.currentTimeMillis() >= alarm)
            {
                // Time is up; use pick from last iteration
                return null;
            }

            // Is this the new best pick?
            if (bestPick == null || childNode.getMaxNValue()[player] >= children.get(bestPick).getMaxNValue()[player])
            {
                if (bestPick == null || childNode.getMaxNValue()[player] > children.get(bestPick).getMaxNValue()[player])
                {
                    // New best pick
                    bestPick = child;
                }
                else
                {
                    // We have a tie.  Time to break it (same as KthBest)
                    double thisSum = 0;
                    double thatSum = 0;
                    for (int i = 0; i < Main.numPlayers; ++i)
                    {
                        if (i != player)
                        {
                            thisSum += childNode.getMaxNValue()[i];
                            thatSum += children.get(bestPick).getMaxNValue()[i];
                        }
                    }

                    if (thisSum > thatSum)
                    {
                        bestPick = child;
                    }
                }
            }
        }

        // Set our value equal to the value of the best child
        node.setMaxNValue(children.get(bestPick).getMaxNValue());
        for (Item child : node.getChildren().keySet())
        {
            node.getChildren().put(child, null);
        }

        return bestPick;
    }
}
