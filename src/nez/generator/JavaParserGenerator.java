package nez.generator;

import java.util.ArrayList;
import java.util.HashMap;

import nez.lang.And;
import nez.lang.AnyChar;
import nez.lang.ByteChar;
import nez.lang.ByteMap;
import nez.lang.Capture;
import nez.lang.Choice;
import nez.lang.Empty;
import nez.lang.Expression;
import nez.lang.Failure;
import nez.lang.Link;
import nez.lang.New;
import nez.lang.NonTerminal;
import nez.lang.Not;
import nez.lang.Option;
import nez.lang.Production;
import nez.lang.Repetition;
import nez.lang.Repetition1;
import nez.lang.Replace;
import nez.lang.Sequence;
import nez.lang.Tagging;

public class JavaParserGenerator extends GrammarGenerator {
	
	JavaParserGenerator() {
		super(null);
	}
	
	JavaParserGenerator(String fileName) {
		super(fileName);
	}

	@Override
	public String getDesc() {
		return "a Nez parser generator for Java (sample)" ;
	}

	@Override
	public void makeHeader() {
		file.write("/* The following is generated by the Nez Grammar Generator */");
		file.writeIndent("class P {");
		file.incIndent();
	}
	
	@Override
	public void makeFooter() {
		file.decIndent();
		file.writeIndent("}");
	}

	String name(Production rule) {
		return rule.getLocalName();
	}

	String funcName(Expression e) {
		return "e" + e.getId();
	}
	
	HashMap<String, Object> funcMap = new HashMap<String, Object>();
	
	private void ensureFunc(Expression e) {
		String key = funcName(e);
		if(!funcMap.containsKey(key)) {
			funcMap.put(key, e);
		}
	}

	private void makeFunc() {
		ArrayList<Expression> l = new ArrayList<Expression>(funcMap.size());
		for(String key: funcMap.keySet()) {
			Object o = funcMap.get(key);
			if(o instanceof Expression) {
				l.add((Expression)o);
				funcMap.put(key, key);
			}
		}
		for(Expression e: l) {
			writeFunc(e);
		}
	}
	
	private void writeFunc(Expression e) {
		file.writeIndent("private static boolean ");
		file.write(funcName(e));
		file.write(" (NezContext c) {");
		file.incIndent();
		file.writeIndent("/* " + e + " */");
		if(e instanceof Choice) {
			writeChoiceLogic(e);
		}
		else if(e instanceof Option) {
			writeOptionLogic(e);
		}
		else if(e instanceof Repetition || e instanceof Repetition1) {
			writeRepetitionLogic(e);
		}
		else if(e instanceof Not) {
			writeNotLogic(e);
		}
		else if(e instanceof And) {
			writeAndLogic(e);
		}
		else {
			visit(e);
			file.writeIndent("return true;");
		}
		file.decIndent();
		file.writeIndent("}");
		makeFunc();
	}

	private void writeOptionLogic(Expression e) {
		file.writeIndent("int pos = c.pos();");
		ensureFunc(e.get(0));
		file.writeIndent("if(" + funcName(e.get(0)) + "(c)) return true;");
		file.writeIndent("c.setpos(pos);");
		file.write("return true;");
	}

	private void writeRepetitionLogic(Expression e) {
		file.writeIndent("while (true) {");
		file.incIndent();
		file.writeIndent("int pos = c.pos();");
		ensureFunc(e.get(0));
		file.writeIndent("if (" + funcName(e.get(0)) + "(c)) {");
		file.incIndent();
		file.writeIndent("if ( pos == c.pos() ) break;");
		file.writeIndent("continue;");
		file.decIndent();
		file.writeIndent("}");
		file.writeIndent("c.setpos(pos);");
		file.writeIndent("break;");
		file.decIndent();
		file.writeIndent("}");
		file.writeIndent("return true;");
	}

	private void writeChoiceLogic(Expression e) {
		file.writeIndent("int pos = c.pos();");
		for(Expression s: e) {
			ensureFunc(s);
			file.writeIndent("if(" + funcName(s) + "(c)) return true;");
			file.writeIndent("c.setpos(pos);");
		}
		file.writeIndent("return false;");
	}

	private void writeAndLogic(Expression e) {
		file.writeIndent("int pos = c.pos();");
		ensureFunc(e.get(0));
		file.writeIndent("boolean inner = " + funcName(e.get(0)) + "(c));");
		file.writeIndent("c.setpos(pos);");
		file.writeIndent("return inner;");
	}

	private void writeNotLogic(Expression e) {
		file.writeIndent("int pos = c.pos();");
		ensureFunc(e.get(0));
		file.writeIndent("boolean inner = " + funcName(e.get(0)) + "(c));");
		file.writeIndent("c.setpos(pos);");
		file.writeIndent("return !inner;");
	}

	@Override
	public void visitProduction(Production rule) {
		file.writeIndent("public final static boolean ");
		file.write(name(rule));
		file.write("(NezContext c) {");
		file.incIndent();
		visit(rule.getExpression());
		file.writeIndent("return true;");
		file.decIndent();
		file.writeIndent("}");
		makeFunc();
	}	
	
	public void visitEmpty(Empty e) {

	}

	public void visitFailure(Failure e) {
		file.write("return false;");
	}

	public void visitNonTerminal(NonTerminal e) {
		file.writeIndent("if(!"+ name(e.getProduction()) + "(c)) return false;");
	}
	
	public void visitByteChar(ByteChar e) {
		file.writeIndent("if(!c.byte("+e.byteChar + ")) return false;");
	}

	public void visitByteMap(ByteMap e) {
		file.writeIndent("if(!c.bytemap(TODO)) return false;");
	}
	
	public void visitAnyChar(AnyChar e) {
		file.writeIndent("if(!c.any()) return false;");
	}

	public void visitOption(Option e) {
		ensureFunc(e);
		file.writeIndent(funcName(e)+"(c);"); 
	}
	
	public void visitRepetition(Repetition e) {
		ensureFunc(e);
		file.writeIndent(funcName(e)+"(c);"); 
	}
	
	public void visitRepetition1(Repetition1 e) {
		visit(e.get(0));
		ensureFunc(e);
		file.writeIndent(funcName(e)+"(c);"); 
	}

	public void visitAnd(And e) {
		ensureFunc(e);
		file.writeIndent("if(!" + funcName(e) + "(c)) return false;");
	}
	
	public void visitNot(Not e) {
		ensureFunc(e);
		file.writeIndent("if(!" + funcName(e) + "(c)) return false;");
	}
	
	public void visitSequence(Sequence e) {
		for(Expression s: e) {
			visit(s);
		}
	}
	
	public void visitChoice(Choice e) {
		ensureFunc(e);
		file.writeIndent("if(!" + funcName(e) + "(c)) return false;");
	}
	
	public void visitNew(New e) {
		file.writeIndent("c.new();");
	}

	public void visitCapture(Capture e) {
		file.writeIndent("c.capture();");
	}

	public void visitTagging(Tagging e) {
		file.writeIndent("c.tag(" + e.tag.getName() + ");");
	}
	
	public void visitValue(Replace e) {

	}
	
	public void visitLink(Link e) {
		visit(e.get(0));
	}

	@Override
	public void visitUndefined(Expression e) {
		file.writeIndent("/* undefined " + e + " */");

	}

}