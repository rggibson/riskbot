/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import com.sillysoft.lux.util.*;

/**
 * This agent is equivalent to EvilPixie except in the draft phase
 * of the game.  In the draft phase, this agent instead uses MaxNMC to
 * pick territories.
 * @author Richard Gibson, Neesha Desai, and Richard Zhao
 */
public class SmartDrafter extends SmartAgentBase
{
    /**
     * The name of the agent to use for post draft play
     */
    protected final String POST_DRAFT_PLAYER_NAME = "EvilPixie";

    /**
     * The player object that we will use for all post draft play
     */
    protected LuxAgent postDraftPlayer;

    public SmartDrafter()
    {
        super();
    }

    @Override
    public void setPrefs(int ID, Board board )
    {
        // Call SmartAgentBase's setPrefs method
        super.setPrefs(ID, board);

        // Create the post-draft player
        postDraftPlayer = board.getAgentInstance(POST_DRAFT_PLAYER_NAME);
        assert(postDraftPlayer != null);
        postDraftPlayer.setPrefs(ID, board);
    }

    public String name()
    {
        return "SmartDrafter";
    }

    public float version()
    {
        return 1.0f;
    }

    public String description()
    {
        return "SmartDrafter uses a smart drafting technique to draft territories, then follows some other strategy.";
    }

    public String youWon()
    {
        return "Ha Ha.  I out-drafted you.";
    }

    public void fortifyPhase()
    {
        postDraftPlayer.fortifyPhase();
    }

    public int moveArmiesIn( int cca, int ccd)
    {
        return postDraftPlayer.moveArmiesIn(cca, ccd);
    }

    public void attackPhase()
    {
        postDraftPlayer.attackPhase();
    }

    public void placeArmies(int numArmies)
    {
        postDraftPlayer.placeArmies(numArmies);
    }

    @Override
    public void placeInitialArmies(int numberOfArmies)
    {
        postDraftPlayer.placeInitialArmies(numberOfArmies);
    }

    @Override
    public void cardsPhase(Card[] cards)
    {
        postDraftPlayer.cardsPhase(cards);
    }

    @Override
    public String message(String message, Object data)
    {
        return postDraftPlayer.message(message, data);
    }

    /**
     * Uses MaxN with Monte Carlo roll-outs to pick in the draft
     * @return The int corresponding to the territory to pick
     */
    @Override
    public int pickCountry()
    {
        return MaxNMC();
    }
   
    final int MC_DEPTH = 15;

    /**
     * The MaxN with Monte Carlo roll-outs algorithm for picking territories in
     * the draft.
     * @return The int corresponding to the chosen territory
     */
    private int MaxNMC()
    {
        // First, get current state of the draft
        int[] draftState = new int[countries.length];
        for (int i = 0; i < draftState.length; ++i)
        {
            draftState[i] = countries[i].getOwner();
        }

        return MaxNMC_r(draftState, ID, MC_DEPTH);
    }

    private int MaxNMC_r(int[] draftState, int player, int depth)
    {
        // TODO: rggibson - IMPLEMENT ME!!

        // For now, just return the first unowned territory
        for (int i = 0; i < draftState.length; ++i)
        {
            if (draftState[i] == -1) // -1 indicates no owner
            {
                return i;
            }
        }

        // Should never get here
        assert(false);
        return -1;
    }

}
