package nez.lang;

import java.util.TreeMap;

import nez.ast.SourcePosition;
import nez.runtime.Instruction;
import nez.runtime.RuntimeCompiler;
import nez.util.ConsoleUtils;
import nez.util.StringUtils;
import nez.util.UList;
import nez.util.UMap;

public class Production extends Expression {
	NameSpace  ns;
	String     name;
	String     uname;
	Expression body;
	
	boolean isPublic  = false;
	boolean isInline  = false;
	boolean isRecursive = false;
	int     refCount  = 0;
	boolean isTerminal = false;
	
	Production original;

	public Production(SourcePosition s, NameSpace ns, String name, Expression body) {
		super(s);
		this.ns = ns;
		this.name = name;
		this.uname = ns.uniqueName(name);
		this.body = (body == null) ? Factory.newEmpty(s) : body;
		this.original = this;
	}

	private Production(String name, Production original, Expression body) {
		super(original.s);
		this.ns = original.getNameSpace();
		this.name = name;
		this.uname = ns.uniqueName(name);
		this.body = (body == null) ? Factory.newEmpty(s) : body;
		this.original = original;
	}

	Production newProduction(String localName) {
		return new Production(name, this, this.getExpression());
	}
	
	@Override
	public Expression reshape(Manipulator m) {
		return this;
	}

	public final NameSpace getNameSpace() {
		return this.ns;
	}
	
	public final boolean isPublic() {
		return this.isPublic;
	}

	public final boolean isInline() {
		return this.isInline || (!isPublic && !isRecursive && this.refCount == 1);
	}

	public final boolean isRecursive() {
		return this.isRecursive;
	}
	
	@Override
	public Expression get(int index) {
		return body;
	}
	
	@Override
	public int size() {
		return 1;
	}
	
	public final String getLocalName() {
		return this.name;
	}
	
	public final String getUniqueName() {
		return this.uname;
	}

	public final String getOriginalLocalName() {
		return this.original.getLocalName();
	}
	
	public final Expression getExpression() {
		return this.body;
	}

	final void setExpression(Expression e) {
		this.body = e;
	}

	public int minlen = -1;
	
	@Override
	public final boolean checkAlwaysConsumed(GrammarChecker checker, String startNonTerminal, UList<String> stack) {
		if(stack != null && this.minlen != 0 && stack.size() > 0) {
			for(String n : stack) { // Check Unconsumed Recursion
				if(uname.equals(n)) {
					this.minlen = 0;
					break;
				}
			}
		}
		if(minlen == -1) {
			if(stack == null) {
				stack = new UList<String>(new String[4]);
			}
			if(startNonTerminal == null) {
				startNonTerminal = this.uname;
			}
			stack.add(this.uname);
			this.minlen = this.body.checkAlwaysConsumed(checker, startNonTerminal, stack) ? 1 : 0;
			stack.pop();
		}
		return minlen > 0;
	}
	
	public int transType = Typestate.Undefined;
	
	public final boolean isPurePEG() {
		return this.transType == Typestate.BooleanType;
	}

	@Override
	public int inferTypestate(UMap<String> visited) {
		if(this.transType != Typestate.Undefined) {
			return this.transType;
		}
		if(visited != null) {
			if(visited.hasKey(uname)) {
				this.transType = Typestate.BooleanType;
				return this.transType;
			}
		}
		else {
			visited = new UMap<String>();
		}
		visited.put(uname, uname);
		int t = body.inferTypestate(visited);
		assert(t != Typestate.Undefined);
		if(this.transType == Typestate.Undefined) {
			this.transType = t;
		}
		else {
			assert(transType == t);
		}
		return this.transType;
	}

	@Override
	public Expression checkTypestate(GrammarChecker checker, Typestate c) {
		int t = checkNamingConvention(this.name);
		c.required = this.inferTypestate(null);
		if(t != Typestate.Undefined && c.required != t) {
			checker.reportNotice(s, "invalid naming convention: " + this.name);
		}
		this.body = this.getExpression().checkTypestate(checker, c);
		return this;
	}

	public final static int checkNamingConvention(String ruleName) {
		int start = 0;
		if(ruleName.startsWith("~") || ruleName.startsWith("\"")) {
			return Typestate.BooleanType;
		}
		for(;ruleName.charAt(start) == '_'; start++) {
			if(start + 1 == ruleName.length()) {
				return Typestate.BooleanType;
			}
		}
		boolean firstUpperCase = Character.isUpperCase(ruleName.charAt(start));
		for(int i = start+1; i < ruleName.length(); i++) {
			char ch = ruleName.charAt(i);
			if(ch == '!') break; // option
			if(Character.isUpperCase(ch) && !firstUpperCase) {
				return Typestate.OperationType;
			}
			if(Character.isLowerCase(ch) && firstUpperCase) {
				return Typestate.ObjectType;
			}
		}
		return firstUpperCase ? Typestate.BooleanType : Typestate.Undefined;
	}

	public final void removeExpressionFlag(TreeMap<String, String> undefedFlags) {
		this.body = this.body.removeFlag(undefedFlags).intern();
	}
	
	@Override
	public Expression removeFlag(TreeMap<String, String> undefedFlags) {
		if(undefedFlags.size() > 0) {
			StringBuilder sb = new StringBuilder();
			int loc = name.indexOf('!');
			if(loc > 0) {
				sb.append(this.name.substring(0, loc));
			}
			else {
				sb.append(this.name);
			}
			for(String flag: undefedFlags.keySet()) {
				if(ConditionAnlysis.hasReachableFlag(this.body, flag)) {
					sb.append("!");
					sb.append(flag);
				}
			}
			String rName = sb.toString();
			Production rRule = ns.getProduction(rName);
			if(rRule == null) {
				rRule = ns.newRule(rName, Factory.newEmpty(null));
				rRule.body = body.removeFlag(undefedFlags).intern();
			}
			return rRule;
		}
		return this;
	}
	
	@Override
	public String getInterningKey() {
		return this.getUniqueName() + "=";
	}
	
	public final void internRule() {
		this.body = this.body.intern();
	}

	@Override
	public String getPredicate() {
		return this.getUniqueName() + "=";
	}

	private int dfaOption = -1;
	private short[] dfaCache = null;

	@Override
	public short acceptByte(int ch, int option) {
		option = Grammar.mask(option);
		if(option != dfaOption) {
			if(dfaCache == null) {
				dfaCache = new short[257];
			}
			for(int c = 0; c < dfaCache.length; c++) {
				dfaCache[c] = this.body.acceptByte(c, option);
			}
			this.dfaOption = option;
		}
		return dfaCache[ch]; 
	}

	
	@Override
	public Instruction encode(RuntimeCompiler bc, Instruction next) {
		return this.getExpression().encode(bc, next);
	}

	@Override
	protected int pattern(GEP gep) {
		return body.pattern(gep);
	}
	
	@Override
	protected void examplfy(GEP gep, StringBuilder sb, int p) {
		body.examplfy(gep, sb, p);
	}

	public final void dump() {
		StringBuilder sb = new StringBuilder();
		if(this.isPublic) {
			sb.append("public ");
		}
		if(this.isInline()) {
			sb.append("inline ");
		}
		if(this.isRecursive) {
			sb.append("recursive ");
		}
		if(!this.isAlwaysConsumed()) {
			sb.append("unconsumed ");
		}
		boolean[] b = ByteMap.newMap(true);
		for(int c = 0; c < b.length; c++) {
			if(this.acceptByte(c, 0) == Prediction.Reject) {
				b[c] = false;
			}
		}
		sb.append("ref(" + this.refCount + ") ");
		sb.append("accept" + StringUtils.stringfyCharClass(b) + " ");
		
		ConsoleUtils.println(sb.toString());
		ConsoleUtils.println(this.getLocalName() + " = " + this.getExpression());
	}


}