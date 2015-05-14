package nez.lang;

import nez.ast.SourcePosition;
import nez.ast.Tag;
import nez.runtime.Instruction;
import nez.runtime.RuntimeCompiler;
import nez.util.UList;
import nez.util.UMap;

public class DefSymbol extends Unary {
	public final Tag tableName;
	public final NameSpace ns;
	
	DefSymbol(SourcePosition s, NameSpace ns, Tag table, Expression inner) {
		super(s, inner);
		this.ns = ns;
		this.tableName = table;
	}

	public final NameSpace getNameSpace() {
		return ns;
	}
	
	public final Tag getTable() {
		return tableName;
	}

	public final String getTableName() {
		return tableName.getName();
	}

	@Override
	public String getPredicate() {
		return "def " + tableName.getName();
	}
	@Override
	public String getInterningKey() {
		return "def " + tableName.getName();
	}
	@Override
	public Expression reshape(Manipulator m) {
		return m.reshapeDefSymbol(this);
	}
	@Override
	public boolean checkAlwaysConsumed(GrammarChecker checker, String startNonTerminal, UList<String> stack) {
		this.inner.checkAlwaysConsumed(checker, startNonTerminal, stack);
		return true;
	}
	@Override void checkPhase1(GrammarChecker checker, String ruleName, UMap<String> visited, int depth) {
		checker.setSymbolExpresion(tableName.getName(), this.inner);
	}
	@Override void checkPhase2(GrammarChecker checker) {
	}
	@Override
	public int inferTypestate(UMap<String> visited) {
		return Typestate.BooleanType;
	}
	@Override
	Expression dupUnary(Expression e) {
		return (this.inner != e) ? Factory.newDefSymbol(this.s, this.ns, this.tableName, e) : this;
	}
	@Override
	public short acceptByte(int ch, int option) {
		return this.inner.acceptByte(ch, option);
	}
	
	// Utilities
	public static boolean checkContextSensitivity(Expression e, UMap<String> visitedMap) {
		if(e.size() > 0) {
			for(int i = 0; i < e.size(); i++) {
				if(checkContextSensitivity(e.get(i), visitedMap)) {
					return true;
				}
			}
			return false;
		}
		if(e instanceof NonTerminal) {
			String un = ((NonTerminal) e).getUniqueName();
			if(visitedMap.get(un) == null) {
				visitedMap.put(un, un);
				return checkContextSensitivity(((NonTerminal) e).getProduction().getExpression(), visitedMap);
			}
			return false;
		}
		if(e instanceof IsIndent || e instanceof IsSymbol) {
			return true;
		}
		return false;
	}
	
	@Override
	public Instruction encode(RuntimeCompiler bc, Instruction next) {
		return bc.encodeDefSymbol(this, next);
	}

	@Override
	protected int pattern(GEP gep) {
		return 1;
	}
	@Override
	protected void examplfy(GEP gep, StringBuilder sb, int p) {
		StringBuilder sb2 = new StringBuilder();
		inner.examplfy(gep, sb2, p);
		String token = sb2.toString();
		gep.addTable(tableName, token);
		sb.append(token);
	}
	
}