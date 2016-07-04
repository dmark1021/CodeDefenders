package org.codedefenders.singleplayer;

import org.codedefenders.*;

import javax.xml.crypto.Data;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.codedefenders.Constants.AI_DIR;
import static org.codedefenders.Constants.F_SEP;

/**
 * @author Ben Clegg
 * An AI defender. Uses tests generated by EvoSuite to kill mutants.
 */
public class AiDefender extends AiPlayer {

	public AiDefender(Game g) {
		super(g);
		role = Game.Role.DEFENDER;
	}
	public boolean turnHard() {
		//Run all generated tests for class.
		if(game.getTests().isEmpty()) {
			//Add test suite to game if it isn't present.
			GameManager gm = new GameManager();
			Test newTest = gm.submitAiTestFullSuite(game);
			newTest.insert();
			//Run the tests on existing mutants.
			MutationTester.runTestOnAllMutants(game, newTest, new ArrayList<String>());

		}
		//Do nothing else, test is automatically re-run on new mutants by GameManager.
		//TODO: Add equivalence check.
		//Call equivalent only if test suite passes on mutant.
		return true;
	}

	public boolean turnMedium() {
		//Choose all tests which cover modified line(s)?
		//Perhaps just 1 or 2?
		//Perhaps higher chance of equivalence call? May happen due to weaker testing.
		return turnHard();
	}

	public boolean turnEasy() {
		//Choose a random test which covers the modified line(s)?
		//Perhaps just a random test?
		//Perhaps higher chance of equivalence call? May happen due to weaker testing.
		GameManager gm = new GameManager();
		try {
			int tNum = selectTest(GenerationMethod.RANDOM);
			try {
				makeTestFromSuite(gm, tNum);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} catch (Exception e) {
			//Assume no more choices remain.
			//Do nothing.
		}

		return true;
	}

	private int selectTest(GenerationMethod strategy) throws Exception {
		ArrayList<Integer> usedTests = DatabaseAccess.getUsedAiTestsForGame(game);
		int totalTests = getNumberOfTests();
		Exception e = new Exception("No choices remain.");

		if(usedTests.size() == totalTests) {
			throw e;
		}
		int t = -1;

		for (int i = 0; i <= 3; i++) {
			//Try to get test by default strategy.
			if (strategy.equals(GenerationMethod.RANDOM)) {
				t = (int) Math.floor(Math.random() * getNumberOfTests());
			}
			//TODO: Other strategies.

			if ((!usedTests.contains(t)) && (t != -1)) {
				//Strategy found an unused test.
				return t;
			}
		}

		//If standard strategy fails, choose first non-selected test.
		for (int x = 0; x < totalTests; x++) {
			if(!usedTests.contains(x)) {
				//Unused test found.
				return x;
			}
		}

		//Something went wrong.
		throw e;
	}

	private void makeTestFromSuite(GameManager gameManager, int testNum) throws IOException{
		//TODO: Prevent test re-use.
		String testText = getTestText(testNum);
		if(testText.isEmpty()) {
			//TODO: Handle empty test.
		}
		else {
			Test t = gameManager.createTest(game.getId(), game.getClassId(), testText, 1);
			ArrayList<String> messages = new ArrayList<String>();
			MutationTester.runTestOnAllMutants(game, t, messages);
			DatabaseAccess.setAiTestAsUsed(testNum, game);
		}
	}


	/**
	 * Retrieve the lines of the full test suite.
	 * @return test suite
	 */
	private List<String> getTestSuiteLines() {
		String loc = AI_DIR + F_SEP + "tests" + F_SEP + game.getClassName() +
				F_SEP + game.getClassName() + "EvoSuiteTest.java";
		File f = new File(loc);
		return FileManager.readLines(f.toPath());
	}

	/**
	 * Get the text of a test from the full suite.
	 * @param testNumber number of test, starting from 0.
	 * @return text of the test
	 */
	private String getTestText(int testNumber) {
		List<String> suite = getTestSuiteLines();
		String t = "";
		int brOpen = 0; //Number of brace opens.
		int brClose = 0; //Number of brace closes.
		int testCount = -1; //Current test, start at -1 as first test is 0.

		for (String l : suite) {
			if(testCount != testNumber) {
				if(l.contains("import ")) {
					//Add any line with import.
					t += l + "\n";
				}
				else if(l.contains("public class ")) {
					//Class declaration, write correct one in place.
					t += "public class Test" + game.getClassName() + " { \n";
				}
				else if(l.contains("public void test")) {
					//Start of a test.
					testCount ++;
					if(testCount == testNumber) {
						//Test requested. Start tracking braces.
						brOpen ++;
						//t += l + "\n";
						t += "@Test(timeout = 4000) \n";
						t += "public void test() throws Throwable { \n";
					}
				}
			}
			else {
				//Write every line and track braces.
				t += l + "\n";
				if(l.contains("{")) {
					brOpen ++;
				}
				if(l.contains("}")) {
					brClose ++;
					if(brOpen == brClose) {
						//Every opened bracket has been closed.
						//Finish off the file.
						t += "}"; //Close class declaration.
						System.out.println(t);
						return t; //Return the string.
						//No point in wasting CPU time reading rest of file.
					}
				}
			}
		}
		System.out.println(t);
		return "";
	}

	/**
	 * Get number of tests in suite.
	 * @return number of tests.
	 */
	private int getNumberOfTests() {
		int n = 0;
		List<String> suite = getTestSuiteLines();
		for (String l : suite) {
			if (l.contains("@Test")) {
				n ++;
			}
		}
		return n;
	}
}
