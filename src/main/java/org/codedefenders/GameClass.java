package org.codedefenders;

import org.codedefenders.duel.DuelGame;
import org.codedefenders.singleplayer.NoDummyGameException;
import org.codedefenders.util.DatabaseAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;

public class GameClass {

	private static final Logger logger = LoggerFactory.getLogger(GameClass.class);

	private int id;
	private String name; // fully qualified name
	private String alias;
	private String javaFile;
	private String classFile;

	public GameClass(String name, String alias, String jFile, String cFile) {
		this.name = name;
		this.alias = alias;
		this.javaFile = jFile;
		this.classFile = cFile;
	}

	public GameClass(int id, String name, String alias, String jFile, String cFile) {
		this(name, alias, jFile, cFile);
		this.id = id;
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
		return tokens[tokens.length-1];
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

		logger.debug("Inserting class (Name={}, Alias={}, JavaFile={}, ClassFile={})", name, alias, javaFile, classFile);
		Connection conn = null;
		PreparedStatement stmt = null;
		// Attempt to insert game info into database
		try {
			conn = DatabaseAccess.getConnection();
			stmt = conn.prepareStatement("INSERT INTO classes (Name, Alias, JavaFile, ClassFile) VALUES (?, ?, ?, ?);",
					Statement.RETURN_GENERATED_KEYS);
			stmt.setString(1, name);
			stmt.setString(2, alias);
			stmt.setString(3, javaFile);
			stmt.setString(4, classFile);
			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				this.id = rs.getInt(1);
				logger.debug("Inserted CUT with ID: " + this.id);
				return true;
			}
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DatabaseAccess.cleanup(conn, stmt);
		}

		return false;
	}

	public boolean update() {

		logger.debug("Updating class (Name={}, Alias={}, JavaFile={}, ClassFile={})", name, alias, javaFile, classFile);
		Connection conn = null;
		PreparedStatement stmt = null;

		// Attempt to update game info into database
		try {
			conn = DatabaseAccess.getConnection();
			stmt = conn.prepareStatement("UPDATE classes SET Name=?, Alias=?, JavaFile=?, ClassFile=? WHERE Class_ID=?;");
			stmt.setString(1, name);
			stmt.setString(2, alias);
			stmt.setString(3, javaFile);
			stmt.setString(4, classFile);
			stmt.setInt(5, id);
			stmt.executeUpdate();
			stmt.close();
			conn.close();
			return true;
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DatabaseAccess.cleanup(conn, stmt);
		}
		return false;
	}

	public String getTestTemplate() {
		StringBuilder sb = new StringBuilder();
		if (!getPackage().isEmpty())
			sb.append(String.format("package %s;%n", getPackage()));
		else
			sb.append(String.format("/* no package name */%n"));
		sb.append(String.format("%n"));
		sb.append(String.format("import org.junit.*;%n"));
		sb.append(String.format("import static org.junit.Assert.*;%n%n"));
		sb.append(String.format("public class Test%s {%n", getBaseName()));
		sb.append(String.format("%c@Test(timeout = 4000)%n", '\t'));
		sb.append(String.format("%cpublic void test() throws Throwable {%n", '\t'));
		sb.append(String.format("%c%c// test here!%n", '\t', '\t'));
		sb.append(String.format("%c}%n", '\t'));
		sb.append(String.format("}"));
		return sb.toString();
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

	public DuelGame getDummyGame() throws NoDummyGameException {
		DuelGame dg = DatabaseAccess.getAiDummyGameForClass(this.getId());
		return dg;
	}

	public boolean delete() {
		logger.debug("Deleting class (ID={})", id);
		Connection conn = null;
		PreparedStatement stmt = null;

		try {
			// Attempt to update game info into database
			conn = DatabaseAccess.getConnection();
			stmt = conn.prepareStatement("DELETE FROM classes WHERE Class_ID=?;");
			stmt.setInt(1, id);
			stmt.executeUpdate();
			stmt.close();
			conn.close();
			return true;
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DatabaseAccess.cleanup(conn, stmt);
		}
		return false;
	}
}