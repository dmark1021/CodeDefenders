package org.codedefenders;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Range;
import org.codedefenders.duel.DuelGame;
import org.codedefenders.singleplayer.NoDummyGameException;
import org.codedefenders.util.DB;
import org.codedefenders.util.DatabaseAccess;
import org.codedefenders.util.DatabaseValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ModifierSet;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import edu.emory.mathcs.backport.java.util.Collections;

public class GameClass {

	private static final Logger logger = LoggerFactory.getLogger(GameClass.class);

	private int id;
	private String name; // fully qualified name
	private String alias;
	private String javaFile;
	private String classFile;

	private boolean isMockingEnabled = false;

	private Set<String> additionalImports = new HashSet<String>();
	// Store begin and end line which corresponds to uncoverable non-initializad fields
	private List<Integer> linesOfCompileTimeConstants= new ArrayList<>();
	private List<Integer> linesOfNonCoverableCode = new ArrayList<>();

	private List<Range<Integer>> linesOfMethods = new ArrayList<>();
	private List<Range<Integer>> linesOfMethodSignatures = new ArrayList<>();
	private List<Range<Integer>>  linesOfClosingBrackets = new ArrayList<>();

	public GameClass(int id, String name, String alias, String jFile, String cFile, boolean isMockingEnabled) {
		this(name, alias, jFile, cFile, isMockingEnabled);
		this.id = id;
	}

	public GameClass(String name, String alias, String jFile, String cFile, boolean isMockingEnabled) {
		this.name = name;
		this.alias = alias;
		this.javaFile = jFile;
		this.classFile = cFile;
		this.isMockingEnabled = isMockingEnabled;
		this.additionalImports.addAll(includeAdditionalImportsFromCUT());
		//
		this.linesOfCompileTimeConstants.addAll(getCompileTimeConstants());
		//
		this.linesOfNonCoverableCode.addAll(getLinesOfNonInitializedFields());
		//
		this.linesOfNonCoverableCode.addAll(getLinesOfCompileTimeConstants());
		//
		this.linesOfNonCoverableCode.addAll(getLinesOfMethodSignatures());
		//
		this.linesOfNonCoverableCode.addAll(getUnreachableClosingBracketsForIfStatements());
		//
	}

	// FIXME
	public GameClass(int id, String name, String alias, String jFile, String cFile) {
		this(name, alias, jFile, cFile, false);
		this.id = id;
	}
	
	public GameClass(String name, String alias, String jFile, String cFile) {
		this(name, alias, jFile, cFile, false);
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBaseName() {
		String[] tokens = name.split("\\.");
		return tokens[tokens.length - 1];
	}

	public String getPackage() {
		return (name.contains(".")) ? name.substring(0, name.lastIndexOf('.')) : "";
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public String getAsString() {
		InputStream resourceContent = null;
		String result = "";
		try {
			resourceContent = new FileInputStream(javaFile);
			BufferedReader is = new BufferedReader(new InputStreamReader(resourceContent));
			String line;
			while ((line = is.readLine()) != null) {
				result += line + "\n";
			}

		} catch (FileNotFoundException e) {
			result = "[File Not Found]";
			logger.error("Could not find file " + javaFile);
		} catch (IOException e) {
			result = "[File Not Readable]";
			logger.error("Could not read file " + javaFile);
		}
		return result;

	}

	public boolean insert() {
		logger.debug("Inserting class (Name={}, Alias={}, JavaFile={}, ClassFile={}, RequireMocking={})", name, alias, javaFile, classFile, isMockingEnabled);
		// Attempt to insert game info into database
		Connection conn = DB.getConnection();
		String query = "INSERT INTO classes (Name, Alias, JavaFile, ClassFile, RequireMocking) VALUES (?, ?, ?, ?, ?);";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(name),
				DB.getDBV(alias),
				DB.getDBV(javaFile),
				DB.getDBV(classFile),
				DB.getDBV(isMockingEnabled)};
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		int res = DB.executeUpdateGetKeys(stmt, conn);
		if (res > -1) {
			this.id = res;
			logger.debug("Inserted CUT with ID: " + this.id);
			return true;
		}
		return false;
	}

	public boolean update() {
		logger.debug("Updating class (Name={}, Alias={}, JavaFile={}, ClassFile={}, RequireMocking={})", name, alias, javaFile, classFile, isMockingEnabled);
		// Attempt to update game info into database
		Connection conn = DB.getConnection();
		String query = "UPDATE classes SET Name=?, Alias=?, JavaFile=?, ClassFile=?, RequireMocking=? WHERE Class_ID=?;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(name),
				DB.getDBV(alias),
				DB.getDBV(javaFile),
				DB.getDBV(classFile),
				DB.getDBV(isMockingEnabled),
				DB.getDBV(id)};
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return DB.executeUpdate(stmt, conn);
	}

	public String getTestTemplate() {
		StringBuilder sb = new StringBuilder();
		if (!getPackage().isEmpty())
			sb.append(String.format("package %s;%n", getPackage()));
		else
			sb.append(String.format("/* no package name */%n"));
		sb.append(String.format("%n"));
		sb.append(String.format("import static org.junit.Assert.*;%n%n"));

		if (this.isMockingEnabled) {
			sb.append(String.format("import static org.mockito.Mockito.*;%n%n"));
		}

		sb.append(String.format("import org.junit.*;%n"));

		// Additional import are already in the form of 'import X.Y.Z;\n'
		for (String additionalImport : this.additionalImports) {
			sb.append(additionalImport);
		}

		sb.append(String.format("public class Test%s {%n", getBaseName()));
		sb.append(String.format("%c@Test(timeout = 4000)%n", '\t'));
		sb.append(String.format("%cpublic void test() throws Throwable {%n", '\t'));
		sb.append(String.format("%c%c// test here!%n", '\t', '\t'));
		sb.append(String.format("%c}%n", '\t'));
		sb.append(String.format("}"));
		return sb.toString();
	}

	/*
	 * We list all the NON-primitive imports here. We do not perform any
	 * merging.
	 * 
	 * (using *)
	 */
	private Set<String> includeAdditionalImportsFromCUT() {
		Set<String> additionalImports = new HashSet<String>();
		CompilationUnit cu;
		try (FileInputStream in = new FileInputStream(javaFile)) {
			// parse the file
			cu = JavaParser.parse(in);

			// Extract the import declarations from the CUT and add them to additionaImports 
			for(ImportDeclaration declaredImport : cu.getImports()){
				additionalImports.add( declaredImport.toStringWithoutComments() );
			}

		} catch (ParseException | IOException e) {
			// If a java file is not provided, there's no import at all.
			logger.warn("Swallow Exception" + e );
		}
		return additionalImports;
	}

	public String getJavaFile() {
		return javaFile;
	}

	public void setJavaFile(String javaFile) {
		this.javaFile = javaFile;
	}

	public String getClassFile() {
		return classFile;
	}

	public void setClassFile(String classFile) {
		this.classFile = classFile;
	}

	public void setMockingEnabled(boolean isMockingEnabled) {
		this.isMockingEnabled = isMockingEnabled;
	}

	public boolean isMockingEnabled() {
		return this.isMockingEnabled;
	}

	public DuelGame getDummyGame() throws NoDummyGameException {
		DuelGame dg = DatabaseAccess.getAiDummyGameForClass(this.getId());
		return dg;
	}

	private List<Integer> findNonInitializedFieldsByType(TypeDeclaration type) {
		List<Integer> nonInitializedFieldsLines = new ArrayList<>();
		for (BodyDeclaration bd : type.getMembers()) {
			if (bd instanceof FieldDeclaration) {
				FieldDeclaration f = (FieldDeclaration) bd;
				for (VariableDeclarator v : f.getVariables()) {
					if (v.getInit() == null) {
						for( int line = v.getBeginLine(); line <= v.getEndLine(); line++){
							nonInitializedFieldsLines.add( line );
						}
					}
				}
			}
		}
		return nonInitializedFieldsLines;
	}

	// TODO The actual definition of compile-time constants is more complex, but
	// for the moment primitive and string types declared as final are considered compile-time constants.
	private List<Integer> findCompileTimeConstantsByType(TypeDeclaration type) {
		List<Integer> compileTimeConstants = new ArrayList<>();
		for (BodyDeclaration bd : type.getMembers()) {
			if (bd instanceof FieldDeclaration) {
				FieldDeclaration f = (FieldDeclaration) bd;
				if ( ( f.getType() instanceof PrimitiveType ) || ("String".equals( f.getType().toString()))) {
					if ((f.getModifiers() & ModifierSet.FINAL) != 0) {
						for (VariableDeclarator v : f.getVariables()) {
							logger.debug("Found compile-time constant " + v );
							for( int line = v.getBeginLine(); line <= v.getEndLine(); line++ )
							compileTimeConstants.add(line);
						}
					}
				}
			}
		}
		return compileTimeConstants;

	}

	// TODO Probably this shall be refactor using some code visitor so we do not
	// reanalyze everything from scratch each time
	public List<Integer> getLinesOfNonInitializedFields() {
		List<Integer> nonInitializedFieldsLines = new ArrayList<>();
		CompilationUnit cu;
		try (FileInputStream in = new FileInputStream(javaFile)) {
			// parse the file
			cu = JavaParser.parse(in);

			for (TypeDeclaration td : cu.getTypes()) {
				// Add the fields for this class;
				nonInitializedFieldsLines.addAll(findNonInitializedFieldsByType(td));

				// We look for FieldDeclaration inside inner classes
				for (BodyDeclaration bd : td.getMembers()) {
					if (bd instanceof TypeDeclaration) {
						nonInitializedFieldsLines.addAll(findNonInitializedFieldsByType((TypeDeclaration) bd));
					}
				}

			}

		} catch (ParseException | IOException e) {
			logger.warn("Swallow exception" + e);
		}
		return nonInitializedFieldsLines;
	}

	// TODO Probably this shall be refactor using some code visitor so we do not
	// reanalyze everything from scratch each time
	public List<Integer> getCompileTimeConstants() {
		List<Integer> compileTimeConstantsLine = new ArrayList<>();
		CompilationUnit cu;
		try (FileInputStream in = new FileInputStream(javaFile)) {
			// parse the file
			cu = JavaParser.parse(in);

			for (TypeDeclaration td : cu.getTypes()) {
				// Static final or static final primitive in the class
				compileTimeConstantsLine.addAll(findCompileTimeConstantsByType(td));

				// We look for FieldDeclaration inside inner classes
				for (BodyDeclaration bd : td.getMembers()) {
					if (bd instanceof TypeDeclaration) {
						compileTimeConstantsLine.addAll(findCompileTimeConstantsByType((TypeDeclaration) bd));
					}
				}

			}

		} catch (ParseException | IOException e) {
			logger.warn("Swallow exception" + e);
		}
		return compileTimeConstantsLine;
	}

	// Lines such as method signature are not coverable
	public List<Integer> getLinesOfMethodSignatures() {
		List<Integer> methodSignatures = new ArrayList<>();
		CompilationUnit cu;
		try (FileInputStream in = new FileInputStream(javaFile)) {
			// parse the file
			cu = JavaParser.parse(in);

			for (TypeDeclaration td : cu.getTypes()) {
				// Static final or static final primitive in the class
				methodSignatures.addAll(findMethodSignaturesByType(td));

				// We look for FieldDeclaration inside inner classes
				for (BodyDeclaration bd : td.getMembers()) {
					if (bd instanceof TypeDeclaration) {
						methodSignatures.addAll(findMethodSignaturesByType((TypeDeclaration) bd));
					}
				}

			}

		} catch (ParseException | IOException e) {
			logger.warn("Swallow exception" + e);
		}

		Collections.sort(methodSignatures);
		return methodSignatures;
	}

	// Lines such as method signature are not coverable
	// https://tomassetti.me/getting-started-with-javaparser-analyzing-java-code-programmatically/
	// Returns the lines of the "}" which closes if statements without else branches
	public List<Integer> getUnreachableClosingBracketsForIfStatements() {
		final List<Integer> lines = new ArrayList<>();
		try (FileInputStream in = new FileInputStream(javaFile)) {
			new VoidVisitorAdapter<Object>() {
				@Override
				public void visit(IfStmt ifStmt, Object arg) {
					super.visit(ifStmt, arg);
					Statement then = ifStmt.getThenStmt();
					Statement elze = ifStmt.getElseStmt();
					// There might be plenty of empty lines
					if( then instanceof BlockStmt ) {

						List<Statement> thenBlockStmts = ((BlockStmt) then).getStmts();
						if( elze == null ){
							/*
							 * This takes only the non-coverable one, meaning
							 * that if } is on the same line of the last stmt it
							 * is not considered here because it is should be already
							 * considered
							 */
							if( thenBlockStmts.size() > 0 && ( thenBlockStmts.get( thenBlockStmts.size() - 1).getEndLine() < ifStmt.getEndLine())){
								// Add the range
								linesOfClosingBrackets.add( Range.between( then.getBeginLine(), ifStmt.getEndLine()));
								lines.add( ifStmt.getEndLine() );
							}
						}else {
								// Add the range
								linesOfClosingBrackets.add( Range.between( then.getBeginLine(), elze.getBeginLine()));
								lines.add( elze.getBeginLine() );
						}
					}
				}
			}.visit(JavaParser.parse(in), null);
		} catch (ParseException | IOException e) {
			logger.warn("Swallow exception" + e);
		}
		Collections.sort(lines);
		return lines;
	}

	private List<Integer> findMethodSignaturesByType(TypeDeclaration type) {
		List<Integer> methodSignatureLines = new ArrayList<>();
		for (BodyDeclaration bd : type.getMembers()) {
			if (bd instanceof MethodDeclaration) {
				MethodDeclaration md = (MethodDeclaration) bd;
				// Note that md.getEndLine() returns the last line of the
				// method, not of the signature
				if (md.getBody() == null)
					continue;
				// Also note that interfaces have no body !
				for (int line = md.getBeginLine(); line <= md.getBody().getBeginLine(); line++) {
					methodSignatureLines.add(line);
				}

				linesOfMethodSignatures.add( Range.between(md.getBeginLine(), md.getBody().getBeginLine()));
				linesOfMethods.add( Range.between(md.getBeginLine(), md.getEndLine()));
			} else if (bd instanceof ConstructorDeclaration) {
				ConstructorDeclaration cd = (ConstructorDeclaration) bd;
				// System.out.println("GameClass.findMethodSignaturesByType()
				// Found " + cd.getDeclarationAsString() + " "
				// + cd.getBeginLine() + " - " + cd.getbo());
				for (int line = cd.getBeginLine(); line <= cd.getBlock().getBeginLine(); line++) {
					methodSignatureLines.add(line);
				}

				linesOfMethodSignatures.add( Range.between(cd.getBeginLine(), cd.getBlock().getBeginLine()));
				linesOfMethods.add( Range.between(cd.getBeginLine(), cd.getEndLine()));
			}
		}
		return methodSignatureLines;
	}

	public List<Integer> getLinesOfNonCoverableCode() {
		return linesOfNonCoverableCode;
	}

	/**
	 * Return the lines which correspond to Compile Time Constants. Mutation of those lines requries the tests to be recompiled against the mutant
	 * @return
	 */
	public List<Integer> getLinesOfCompileTimeConstants() {
		return linesOfCompileTimeConstants;
	}

	public boolean delete() {
		logger.debug("Deleting class (ID={})", id);
		// Attempt to update game info into database
		Connection conn = DB.getConnection();
		String query = "DELETE FROM classes WHERE Class_ID=?;";
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(id));
		return DB.executeUpdate(stmt, conn);
	}

	/**
	 * Return the lines of the method signature for the method which contains the coveredLine
	 * @param coveredLine
	 * @return
	 */
	public List<Integer> getLinesOfMethodSignaturesFor(Integer coveredLine) {
		List<Integer> lines = new ArrayList<Integer>();
		// Check if the coveredLine belongs to the method
		for( Range<Integer> rMethod : linesOfMethods ){
			// Now that the first line of the method, which belongs to the corresponding signature
			if( rMethod.contains( coveredLine ) ){
				for( Range<Integer> r : linesOfMethodSignatures ){
					if( r.contains( rMethod.getMinimum() ) ){
						for( int i = r.getMinimum(); i <= r.getMaximum(); i++){
							lines.add( i );
						}
					}
				}
			}
		}

		return lines;
	}

	public List<Integer> getLineOfClosingBracketFor(Integer coveredLine) {
		List<Integer> lines = new ArrayList<Integer>();
		for( Range<Integer> r : linesOfClosingBrackets){
			if( r.contains( coveredLine ) ){
				lines.add( r.getMaximum() );
			}
		}

		return lines;
	}

}