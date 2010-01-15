/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;

/**
 *
 * @author Richard
 */
public class UctAgent extends Agent
{
    private final int NUM_SIMULATIONS = -1;

    private final double CONSTANT = 250;

    /**
     * Constructor
     */
    public UctAgent()
    {

    }

    public String toString()
    {
        return "UCT";
    }

    public Item makePick(ArrayList<Item> availableItems)
    {
        long alarm = System.currentTimeMillis() + Main.TIME_LIMIT * (int)Math.ceil((double)availableItems.size() / Main.numPlayers);

        Node root = new Node(availableItems, m_ID);

        if (NUM_SIMULATIONS == -1)
        {
            while (System.currentTimeMillis() < alarm)
            {
                runUctSimulation(root, availableItems);
            }
        }
        else
        {
           for (int i = 0; i < NUM_SIMULATIONS; ++i)
           {
               runUctSimulation(root, availableItems);
           }
        }

        Item bestPick = getBestPick(root);

        return bestPick;
    }

    /**
     * Returns the highest valued item among all of the root's children
     * @param root The root of the UCT tree
     * @return The Item with the highest value, randomly breaking ties
     */
    private Item getBestPick(Node root)
    {
        ArrayList<Item> best = new ArrayList<Item>();
        double bestValue = -Double.MAX_VALUE;
        for (Item item : root.getChildren().keySet())
        {
            Node node = root.getChildren().get(item);
            if (node.getValue() > bestValue)
            {
                bestValue = node.getValue();
                best.clear();
                best.add(item);
            }
            else if (node.getValue() == bestValue)
            {
                best.add(item);
            }
        }

        return best.get(Main.rand.nextInt(best.size()));
    }

    /**
     * Runs a simulation from the root down to a terminal state, following UCT
     * decisions along the way.
     * @param node The current position in the tree that we are running from
     * @param draftState The current state of the draft
     */
    public void runUctSimulation(Node node, ArrayList<Item> availableItems)
    {
        double[] futureFinalScores = new double[Main.numPlayers];
        uctSimulation_r(node, availableItems, futureFinalScores, true);
    }

    /**
     * The recursive portion of the UCT simulation
     * @param node The current node we are at in the simulation
     * @param draftState The current state of the draft (should match node.getDraftState() if node != null)
     * @param addToTreee True if we still want to add a node to the UCT tree
     * @return The evaluation of the final state reached in the simulation
     */
    private void uctSimulation_r(Node node, ArrayList<Item> availableItems, double[] futureFinalScores, boolean addToTree)
    {
        if (node == null)
        {
            // Time to run randomly to the end

            // Whose turn is it to pick next?
            int nextPlayer = (Main.NUM_ITEMS - availableItems.size()) % Main.numPlayers;

            monteCarloRollOut(availableItems, futureFinalScores, nextPlayer);

            return;
        }

        // A visit to this node
        node.incrementNumVisits();

        // Find the list of children to choose from
        ArrayList<Item> picks = new ArrayList<Item>();
        double valueOfPicks = -Double.MAX_VALUE;
        HashMap<Item,Node> children = node.getChildren();
        Set<Item> childrenKeys = children.keySet();
        Iterator<Item> iter = childrenKeys.iterator();
        while (iter.hasNext())
        {
            Item childKey = iter.next();
            Node child = children.get(childKey);
            double value = Double.MAX_VALUE;
            if (child != null)
            {
                assert child.getNumVisits() > 0 : "Error: No visits to child node!";
                value = child.getValue() + CONSTANT * Math.sqrt(Math.log(node.getNumVisits()) / child.getNumVisits());
            }
            if (value > valueOfPicks)
            {
                picks.clear();
                picks.add(childKey);
                valueOfPicks = value;
            }
            else if (value == valueOfPicks)
            {
                picks.add(childKey);
            }
        }

        if (picks.isEmpty())
        {
            // Must be a terminal node, as there are no children
            return;
        }

        // Choose a child key at random
        Item childKey = picks.get(Main.rand.nextInt(picks.size()));
        Node child = children.get(childKey);

        // Adjust the state
        if (!availableItems.contains(childKey))
        {
            assert availableItems.contains(childKey) : "Error: Can't pick an already drafted territory!";
        }
        availableItems.remove(childKey);
        futureFinalScores[node.getOwner()] += childKey.m_values[node.getOwner()];

        if (child == null && addToTree)
        {
            // Need to add this node to the tree
            if (node == null)
            {
                assert false : "Error: Null node!";
            }
            int nextOwner = (node.getOwner() + 1) % Main.numPlayers;
            child = new Node(availableItems, nextOwner);
            addToTree = false;
            node.addChild(childKey, child);
        }

        uctSimulation_r(child, availableItems, futureFinalScores, addToTree);

        // Undo the changes to the draft state
        assert !availableItems.contains(childKey) : "Error: Something happened to draftState that was unintended.";
        availableItems.add(childKey);

        // Update the value at this node
        int playerForValue = node.getOwner() - 1;
        if (playerForValue < 0)
        {
            playerForValue = Main.numPlayers - 1;
        }
        double target = futureFinalScores[playerForValue];
        node.setValue(node.getValue() + (1.0 / node.getNumVisits())*(target - node.getValue()));

        // Propogate the values back
        return;
    }

    /**
     * From the passed in draft state, randomly picks countries for each player
     * until all countries are owned.  The value of the final state is then
     * calculated.
     * @param draftState The current assignment of countries to players, or unowned
     * @param player The player whose pick it currently is
     * @param unownedCountries The countries available to be picked by the players
     * @return The values of the final state reached via the roll outs.
     */
    public void monteCarloRollOut(ArrayList<Item> availableItems, double[] futureFinalScores, int player)
    {
        // If this is a terminal node, then we are done
        if (availableItems.isEmpty())
        {
            return;
        }

        // Otherwise, pick an item at random and evaluate
        int randomItemIndex = Main.rand.nextInt(availableItems.size());
        Item randomItem = availableItems.get(randomItemIndex);
        availableItems.remove(randomItemIndex);

        futureFinalScores[player] += randomItem.m_values[player];

        monteCarloRollOut(availableItems, futureFinalScores, (player + 1) % Main.numPlayers);

        availableItems.add(randomItem);
    }

}
