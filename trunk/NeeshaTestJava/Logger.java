package com.sillysoft.lux.agent;

import com.sillysoft.lux.Board;
import com.sillysoft.lux.Country;
import java.io.*;

public class Logger {

	
	

	protected static Board board;
	protected static Country[] countries;
//	static BufferedWriter out;
	static boolean printed = false;
	static int gamesplayed = 0;
	
	public Logger(Board theboard ) {
		this.board = theboard;
		this.countries = board.getCountries();
		
		int numPlayers = board.getNumberOfPlayers();

		
	}

	public static boolean isPrinted() {
		return printed;
	}

	public static void setPrinted(boolean printed) {
		// TODO Auto-generated method stub
		Logger.printed = printed;
	}

	public static int getGamesplayed() {
		return gamesplayed;
	}
	
	public static void setGamesplayed(int totalGames, int winID) {
		// TODO Auto-generated method stub
		Logger.gamesplayed = totalGames;
		
		try{
		    // Create file 
			String filename = "";
			if (useSelf) 
				filename = setNum + "Unordered";
			else
				filename = setNum + "Ordered";
		    FileWriter fstream = new FileWriter("/Users/neeshadesai567/Desktop/Risk Stuff/riskbot/results" + filename + ".txt", true);
		    BufferedWriter out = new BufferedWriter(fstream);
		   
		    out.write(winID + "\n");
			 out.close();

		    }catch (Exception e){//Catch exception if any
		      System.err.println("Error: opening buffer " + e.getMessage());
		    }
		   p0 = 0;
		   p1 = 0;
		   p2 = 0;
		int nextgame = gamesplayed + 1;
		if (nextgame % 2000 == 0) {
			// on a multiple of 1000 games
			setNum++;
			printed = false;
			useSelf = false;
		} else if (nextgame % 1000 == 0) {
			// on a multiple of 1000 games
			//setNum++;
			printed = false;
			useSelf = true;
		}
		
		System.out.println("Starting game " + nextgame);
	}
	
	static int p0 = 0;
	static int p1 = 0;
	static int p2 = 0;
	static int setNum = 0;
	static boolean useSelf = false;
	
	static int[][] pick0 = { {29,14,4,7,41,31,25,9,15,35,11,17,30,34},
		{13, 16,17,2, 20, 23, 26, 27, 28 , 37, 5, 6, 8, 9},
		{0, 10, 11, 14, 15, 19, 24, 28, 29, 36, 41, 5, 6, 7},
		{0, 10, 11, 12, 13, 14, 21, 27, 28, 29, 37, 5, 7, 8},
		{10, 13, 19, 21, 22, 26, 31, 32, 34, 35, 36, 5, 6, 9},
		{0, 10, 16, 19, 22, 26, 28, 3, 37, 39, 4, 41, 7, 9},
		{11, 15, 16, 17, 21, 26, 27, 31, 35, 38, 4, 41, 8, 9}};
	
	static int[][] pick1 = { {39,5,37,10,38,28,24,20,8,36,26,1,6,0},
		{0, 12, 14, 15, 21, 22, 29, 30, 31, 32, 33, 34, 38, 7},
		{1, 12, 13, 21, 22, 23, 27, 3, 30, 32, 34, 35, 37, 38},
		{1, 15, 16, 17, 2, 20, 22, 24, 25, 26, 34, 35, 4, 6},
		{0, 12, 14, 17, 2, 23, 25, 27, 30, 33, 38, 39, 40, 41},
		{11, 12, 14, 15, 17, 18, 21, 24, 29, 30, 32, 35, 36, 2},
		{13, 18, 19, 22, 23, 24, 25, 28, 29, 30, 32, 36, 37, 6}};
	
	static int[][] pick2 = { {32,12,13,33,21,27,18,16,3,23,2,40,22,19},
		{1, 10, 11, 18, 19, 24, 25, 3, 35, 36, 39, 4, 40, 41},
		{16, 17, 18, 2, 20, 25, 26, 31, 33, 39, 4, 40, 8, 9},
		{18, 19, 23, 3, 30, 31, 32, 33, 36, 38, 39, 40, 41, 9},
		{1, 11, 15, 16, 18, 20, 24, 28, 29, 3, 37, 4, 7, 8},
		{1, 13, 2, 20, 23, 25, 27, 31, 33, 34, 38, 40, 5, 8},
		{0, 1, 10, 12, 14, 2, 20, 3, 33, 34, 39, 40, 5, 7}};
	
	public static int getPlayerPick(int playerNum) {
		if (playerNum == 0) {
			return pick0[setNum][p0++];
		}
		if (playerNum == 1) {
			return pick1[setNum][p1++];
		}
		if (playerNum == 2) {
			return pick2[setNum][p2++];
		}
		return -1;
		
	}

	public static void printEval(double[] eval) {
		// TODO Auto-generated method stub
		try{
		    // Create file 
			String filename = "";
			if (useSelf) 
				filename = setNum + "Unordered";
			else
				filename = setNum + "Ordered";
		    FileWriter fstream = new FileWriter("/Users/neeshadesai567/Desktop/Risk Stuff/riskbot/results" + filename + ".txt", true);
		    BufferedWriter out = new BufferedWriter(fstream);
		    for (int i=0; i < eval.length; i++) {
		    	out.write("player " + i + " has value " + eval[i] + "\n");
	        }
//		    out.write(winID + "\n");
			 out.close();

		    }catch (Exception e){//Catch exception if any
		      System.err.println("Error: opening buffer " + e.getMessage());
		    }
		    
		
	}

	public static boolean UseSelf() {
		return useSelf;
	}

	
}
