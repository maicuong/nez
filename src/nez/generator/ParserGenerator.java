package nez.generator;

import java.util.ArrayList;
import java.util.HashMap;

import nez.ast.Tag;
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
import nez.lang.Typestate;
import nez.util.StringUtils;

public class ParserGenerator extends NezGenerator {
	
	ParserGenerator() {
		super(null);
	}
	
	ParserGenerator(String fileName) {
		super(fileName);
	}

	@Override
	public String getDesc() {
		return "a Nez parser generator for Java (sample)" ;
	}

	@Override
	public void makeHeader() {
		W("/* The following is generated by the Nez Grammar Generator */");
		L("class P").Begin();
	}
	
	@Override
	public void makeFooter() {
		End();
	}

	String _func(Production rule) {
		return rule.getLocalName();
	}

	String  _func(Expression e) {
		return "e" + e.getId();
	}

	String  _ctx() { return "c";}

	String _call(Production p) {
		return _func(p) + "(" + _ctx() + ")";
	}

	String _call(Expression e) {
		ensureFunc(e);
		return "e" + e.getId() + "(" + _ctx() + ")";
	}

	String _not(String expr) { return "!(" + expr + ")"; }

	String _true() { return "true"; }
	String _false() { return "false"; }

	String _left() { return "left"; }
	String _log() { return "log"; }
	
	String _cref(String n) { return _ctx() + "." + n + "()";}
	String _ccall(String n) { return _ctx() + "." + n + "()";}
	String _ccall(String n, String a) { return _ctx() + "." + n + "(" + a + ")";}
	String _ccall(String n, String a, String a2) { return _ctx() + "." + n + "(" + a + "," + a2 + ")";}
	
	String _cleft() { return _cref("left"); }
	String _clog() { return _cref("log"); }
	
	ParserGenerator VarNode(String n, String left) {
		L("Object " + n + " = " + left).Semi();
		return this;
	}
	ParserGenerator Commit(String log, String left) {
		Return(_ctx() + ".commit(" + log + ", " + left + ")");
		return this;
	}
	ParserGenerator Abort(String log, String left) {
		Return(_ctx() + ".abort(" + log + ", " + left + ")");
		return this;
	}

	String _match(int c) {
		return _ccall("byte", ""+c);
	}

	String _match(boolean[] b) {
		return _ccall("byte", ""+b);
	}

	String _match() {
		return _ccall("any");
	}
	
	String _result() { return "result"; }

	String _pos() { return "pos"; }

	String _cpos() { return _cref("pos"); }

	String _eq(String v, String v2) {
		return "(" + v + " == " + v2 + ")";
	}
	
	protected ParserGenerator Save(Expression e) {
		VarInt(_pos(), _cpos());
		if(e.inferTypestate() != Typestate.BooleanType) {
			VarInt(_log(), _clog());
		}
		return this;
	}

	protected ParserGenerator Rollback(Expression e) {
		if(e.inferTypestate() != Typestate.BooleanType) {
			Statement(_ccall("abort", _log()));
		}
		Statement(_ccall("setpos", _pos()));
		return this;
	}

	protected ParserGenerator W(String word) {
		file.write(word);
		return this;
	}

	protected ParserGenerator L() {
		file.writeIndent();
		return this;
	}

	protected ParserGenerator inc() {
		file.incIndent();
		return this;
	}

	protected ParserGenerator dec() {
		file.decIndent();
		return this;
	}

	protected ParserGenerator L(String line) {
		file.writeIndent(line);
		return this;
	}
	
	protected ParserGenerator DefPublicFunc(String name) {
		L("public static boolean ").W(name).W(" (NezContext ").W(_ctx()).W(") ");
		return this;
	}

	protected ParserGenerator DefFunc(String name) {
		L("private static boolean ").W(name).W(" (NezContext ").W(_ctx()).W(") ");
		return this;
	}

	protected ParserGenerator FuncName(Expression e) {
		W("e" + e.getId());
		return this;
	}

	protected ParserGenerator IfThen(String c) {
		L("if (").W(c).W(") ");
		return this;
	}

	protected ParserGenerator IfNotThen(String c) {
		L("if (").W(_not(c)).W(") ");
		return this;
	}
	
	protected ParserGenerator Else() {
		L("else");
		return this;
	}

	protected ParserGenerator Begin() {
		W("{").inc();
		return this;
	}

	protected ParserGenerator End() {
		dec().L("}");
		return this;
	}

	protected ParserGenerator While(String c) {
		L("while (").W(c).W(") ");
		return this;
	}

	protected ParserGenerator Continue() {
		L("continue").Semi();
		return this;
	}

	protected ParserGenerator Break() {
		L("break").Semi();
		return this;
	}

	protected ParserGenerator VarInt(String n, String v) {
		L("int ").W(n).W(" = ").W(v).Semi();
		return this;
	}

	protected ParserGenerator VarBool(String n, String v) {
		L("boolean ").W(n).W(" = ").W(v).Semi();
		return this;
	}

	protected ParserGenerator Assign(String n, String v) {
		L(n).W(" = ").W(v).Semi();
		return this;
	}
	
	protected ParserGenerator Semi() {
		W(";");
		return this;
	}
	
	protected ParserGenerator Statement(String expr) {
		L(expr).Semi();
		return this;
	}
	
	protected ParserGenerator Return(String v) {
		L("return ").W(v).Semi();
		return this;
	}
	
	protected ParserGenerator Comment(Object s) {
		W("/* ").W(s.toString()).W(" */");
		return this;
	}

	protected ParserGenerator LComment(Object s) {
		L("// ").W(s.toString());
		return this;
	}
	
	public void writeLinkLogic(Link e) {
		VarNode(_left(), _cleft());
		VarInt(_log(), _clog());
		IfThen(_call(e.get(0))).Begin().Commit(_log(), _left()).End();
		Abort(_log(), _left());
	}

	
	HashMap<String, Object> funcMap = new HashMap<String, Object>();
	
	private void ensureFunc(Expression e) {
		String key = _func(e);
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
		DefFunc(_func(e));
		Begin(); Comment(e);
		if(e instanceof Choice) {
			writeChoiceLogic(e);
		}
		else if(e instanceof Link) {
			writeLinkLogic((Link)e);
		}
		else if(e instanceof Option) {
			writeOptionLogic(e);
		}
		else if(e instanceof Repetition || e instanceof Repetition1) {
			writeRepetitionLogic(e);
		}
		else if(e instanceof Not || e instanceof And) {
			writePredicateLogic(e);
		}
		else {
			visit(e);
			Return(_true());
		}
		End();
		makeFunc();
	}

	private void writeOptionLogic(Expression e) {
		Save(e.get(0));
		IfThen(_call(e.get(0))).Begin().Return(_true()).End();
		Rollback(e.get(0));
		Return(_true());
	}

	private void writeRepetitionLogic(Expression e) {
		While(_true()).Begin();
		Save(e.get(0));
		ensureFunc(e.get(0));
		IfThen(_call(e.get(0))).Begin();
		IfThen(_eq(_pos(), _cpos())).Begin().Break().End();
		Continue();
		End();
		Rollback(e.get(0));
		Break();
		End();
		Return(_true());
	}

	private void writeChoiceLogic(Expression e) {
		Save(e);
		for(Expression s: e) {
			IfThen(_call(s)).Begin().Return(_true()).End();
			Rollback(e);
		}
		Return(_false());;
	}
	
	private void writePredicateLogic(Expression e) {
		Save(e.get(0));
		VarBool(_result(), _call(e.get(0)));
		Rollback(e.get(0));
		Return(_result());
	}

	@Override
	public void visitProduction(Production rule) {
		DefPublicFunc(_func(rule)).Begin();
		visit(rule.getExpression());
		Return(_true());
		End();
		makeFunc();
	}	
	
	public void visitEmpty(Empty e) {

	}

	public void visitFailure(Failure e) {
		Return(_false());
	}

	public void visitNonTerminal(NonTerminal e) {
		IfThen(_not(_call(e.getProduction()))).Begin().Return(_false()).End();
	}
	
	public void visitByteChar(ByteChar e) {
		IfNotThen(_match(e.byteChar)).Begin().Return(_false()).End();
	}

	public void visitByteMap(ByteMap e) {
		IfNotThen(_match(e.byteMap)).Begin().Return(_false()).End();
	}
	
	public void visitAnyChar(AnyChar e) {
		IfNotThen(_match()).Begin().Return(_false()).End();
	}

	public void visitOption(Option e) {
		Statement(_call(e));
	}
	
	public void visitRepetition(Repetition e) {
		Statement(_call(e));
	}
	
	public void visitRepetition1(Repetition1 e) {
		visit(e.get(0));
		Statement(_call(e));
	}

	public void visitAnd(And e) {
		IfNotThen(_call(e)).Begin().Return(_false()).End();
	}
	
	public void visitNot(Not e) {
		IfThen(_call(e)).Begin().Return(_false()).End();
	}
	
	public void visitSequence(Sequence e) {
		for(Expression s: e) {
			visit(s);
		}
	}
	
	public void visitChoice(Choice e) {
		IfNotThen(_call(e)).Begin().Return(_false()).End();
	}
	
	public void visitNew(New e) {
		Statement(_ccall("new"));
	}

	public void visitCapture(Capture e) {
		Statement(_ccall("capture"));
	}

	String _tag(Tag tag) {
		return StringUtils.quoteString('"', tag.getName(), '"');
	}
	public void visitTagging(Tagging e) {
		Statement(_ccall("tag", _tag(e.tag)));
	}
	
	public void visitReplace(Replace e) {
		Statement(_ccall("replace", StringUtils.quoteString('"', e.value, '"')));
	}
	
	public void visitLink(Link e) {
		IfNotThen(_call(e)).Begin().Return(_false()).End();
	}

	@Override
	public void visitUndefined(Expression e) {
		LComment("undefined " + e);
	}

}