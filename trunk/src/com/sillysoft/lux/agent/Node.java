package com.sillysoft.lux.agent;

import java.util.Set;
import java.util.*;

public class Node {

	int owner;
	Node parent;
	int numVisits;
	HashSet<Node> children = new HashSet();
	double value;
	int[] draftState;
	int numChildren;
	int draftNumber;
	
	public int getOwner() {
		return owner;
	}
	public void setOwner(int owner) {
		this.owner = owner;
	}
	public void addChild(Node n) {
		children.add(n);
	}
	public int getNumChildren() {
		return numChildren;
	}
	public void setNumChildren(int numChildren) {
		this.numChildren = numChildren;
	}
	public int[] getDraftState() {
		return draftState;
	}
	public void setDraftState(int[] draftState) {
		this.draftState = draftState;
	}
	public Node getParent() {
		return parent;
	}
	public void setParent(Node parent) {
		this.parent = parent;
	}
	public int getNumVisits() {
		return numVisits;
	}
	public void incrementNumVisits() {
		this.numVisits++;
	}
	public void setNumVisits(int numVisits) {
		this.numVisits = numVisits;
	}
	public HashSet getChildren() {
		return children;
	}
	public void setChildren(HashSet children) {
		this.children = children;
	}
	public double getValue() {
		return value;
	}
	public void setValue(double value) {
		this.value = value;
	}
	public int getDraftNumber() {
		return draftNumber;
	}
	public void setDraftNumber(int draftNumber) {
		this.draftNumber = draftNumber;
	}
	
	
}
