
import java.util.HashMap;
import java.util.ArrayList;

public class Node
{

    /**
     * The player whose decision it is at this node
     */
    private int owner;

    /**
     * The number of times we have visited this state in simulations
     */
    private int numVisits;

    /**
     * The children states of this node
     */
    private HashMap<Item,Node> children;

    /**
     * The value of reaching this state, to (owner - 1) mod numPlayers
     * (because it is that player which plays or doesn't play to reach this state)
     */
    private double value;

    /**
     * The value of this node to each of the players
     */
    private double[] maxNValue;

    /**
     * The state this node represents
     */
//    private int[] draftState;

    /**
     * Constructor
     * @param draftState The state of the draft corresponding to this node.
     */
    public Node(/*int[] draftState, */ ArrayList<Item> availableItems, int owner)
    {
        this.owner = owner;
        numVisits = 0;
        value = 0.0;
        maxNValue = new double[Main.numPlayers];
//        this.draftState = new int[draftState.length];
//        System.arraycopy(draftState, 0, this.draftState, 0, draftState.length);
        children = new HashMap<Item,Node>();

        for (Item item : availableItems)
        {
            children.put(item, null);
        }

        // Create the index into the transpose table
//        ArrayList<Integer> state = new ArrayList<Integer>();
//        for (int i = 0; i < draftState.length; ++i)
//        {
//            state.add(draftState[i]);
//        }
//
//        // Create the list of children
//        for (int i = 0; i < draftState.length; ++i)
//        {
//            if (draftState[i] == -1)
//            {
//                // Check to see if this child has already been created
////                    state.set(i, owner);
////                    if (transposeTable.containsKey(state))
////                    {
////                        children.put(i, transposeTable.get(state));
////                    }
////                    else
////                    {
//                    children.put(i, null);
////                    }
////                    state.set(i, -1);
//            }
//        }

        // Add this state to the transposition table
//            assert !transposeTable.containsKey(state) : "Error: Not allowed to create duplicate Node of same state";
//            transposeTable.put(state, this);
    }

    public void addChild(Item item, Node n) {
        assert children.containsKey(item) : "Error: Not a valid child of this node.";
        assert children.get(item) == null : "Error: Not allowed to replace existing child.";
            children.put(item, n);
    }
    public int getNumChildren() {
            return children.size();
    }
//    public int[] getDraftState() {
//            return draftState;
//    }
//    public void setDraftState(int[] draftState) {
//            this.draftState = draftState;
//    }
    public int getNumVisits() {
            return numVisits;
    }
    public void incrementNumVisits() {
            this.numVisits++;
    }
    public void setNumVisits(int numVisits) {
            this.numVisits = numVisits;
    }
    public HashMap<Item,Node> getChildren() {
            return children;
    }
//	public void setChildren(HashMap children) {
//		this.children = children;
//	}
    public double getValue() {
            return value;
    }
    public void setValue(double value) {
            this.value = value;
    }
    public double[] getMaxNValue()
    {
        return maxNValue;
    }
    public void setMaxNValue (double[] value)
    {
        for (int i = 0; i < value.length; ++i)
        {
            maxNValue[i] = value[i];
        }
    }
    public int getOwner()
    {
        return owner;
    }

}
