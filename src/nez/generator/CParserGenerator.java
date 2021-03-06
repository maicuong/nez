package nez.generator;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import nez.NezOption;
import nez.lang.And;
import nez.lang.AnyChar;
import nez.lang.Block;
import nez.lang.ByteChar;
import nez.lang.ByteMap;
import nez.lang.Capture;
import nez.lang.CharMultiByte;
import nez.lang.Choice;
import nez.lang.DefIndent;
import nez.lang.DefSymbol;
import nez.lang.ExistsSymbol;
import nez.lang.Expression;
import nez.lang.Grammar;
import nez.lang.GrammarOptimizer;
import nez.lang.IfFlag;
import nez.lang.IsIndent;
import nez.lang.IsSymbol;
import nez.lang.Link;
import nez.lang.LocalTable;
import nez.lang.New;
import nez.lang.NonTerminal;
import nez.lang.Not;
import nez.lang.OnFlag;
import nez.lang.Option;
import nez.lang.Production;
import nez.lang.Repetition;
import nez.lang.Repetition1;
import nez.lang.Replace;
import nez.lang.Sequence;
import nez.lang.Tagging;

public class CParserGenerator extends ParserGenerator {

	@Override
	public String getDesc() {
		return "a Nez parser generator for C (sample)";
	}

	GrammarOptimizer optimizer = null;
	int predictionCount = 0;

	@Override
	public void generate(Grammar grammar, NezOption option, String fileName) {
		this.setOption(option);
		this.option.setOption("ast", false);
		this.optimizer = new GrammarOptimizer(option);
		this.setOutputFile(fileName);
		makeHeader(grammar);
		for(Production p : grammar.getProductionList()) {
			visitProduction(p);
		}
		makeFooter(grammar);
		file.writeNewLine();
		file.flush();
//		System.out.println("PredictionCount: " + this.predictionCount);
		//FIXME
		//System.out.println("CommonCount: " + optimizer.commonCount);
	}

	@Override
	public void makeHeader(Grammar grammar) {
		this.file.write("// This file is generated by nez/src/nez/x/parsergenerator/CParserGenerator.java");
		this.file.writeNewLine();
		this.file.writeIndent("#include \"libnez/c/libnez.h\"");
		this.file.writeIndent("#include <stdio.h>");
		for(Production r : grammar.getProductionList()) {
			if(!r.getLocalName().startsWith("\"")) {
				this.file.writeIndent("int p" + r.getLocalName() + "(ParsingContext ctx);");
			}
		}
		this.file.writeNewLine();
	}

	@Override
	public void makeFooter(Grammar grammar) {
		this.file.writeIndent("int main(int argc, char* const argv[])");
		this.openBlock();
		this.file.writeIndent("uint64_t start, end, latency;");
		this.file.writeIndent("ParsingContext ctx = nez_CreateParsingContext(argv[1]);");
		this.file.writeIndent("ctx->flags_size = " + flagTable.size() + ";");
		this.file.writeIndent("ctx->flags = (int*)calloc(" + flagTable.size() + ", sizeof(int));");
		this.file.writeIndent("createMemoTable(ctx, " + memoId + ");");
		this.file.writeIndent("for (int i = 0; i < 5; i++)");
		this.openBlock();
		this.file.writeIndent("ctx->cur = ctx->inputs;");
		this.file.writeIndent("start = timer();");
		this.file.writeIndent("if(pFile(ctx))");
		this.openBlock();
		this.file.writeIndent("nez_PrintErrorInfo(\"parse error\");");
		this.closeBlock();
		this.file.writeIndent("else if((ctx->cur - ctx->inputs) != ctx->input_size)");
		this.openBlock();
		this.file.writeIndent("nez_PrintErrorInfo(\"unconsume\");");
		this.closeBlock();
		this.file.writeIndent("else");
		this.openBlock();
		this.file.writeIndent("end = timer();");
		if(this.option.enabledASTConstruction) {
			this.file.writeIndent("ParsingObject po = nez_commitLog(ctx,0);");
			this.file.writeIndent("dump_pego(&po, ctx->inputs, 0);");
		}
		this.file.writeIndent("fprintf(stderr, \"ErapsedTime: %llu msec\\n\", (unsigned long long)end - start);");
		this.file.writeIndent("if(i == 0)");
		this.openBlock();
		this.file.writeIndent("latency = (unsigned long long)end - start;");
		this.closeBlock();
		this.file.writeIndent("else if(latency > ((unsigned long long)end - start))");
		this.openBlock();
		this.file.writeIndent("latency = (unsigned long long)end - start;");
		this.closeBlock();
		this.closeBlock();
		this.closeBlock();
		this.file.writeIndent("nez_log(ctx, argv[1], \"" + grammar.getProductionList().get(0).getGrammarFile().getURN() + "\", "
				+ grammar.getProductionList().size() + ", latency, \"\");");
		this.file.writeIndent("return 0;");
		this.file.writeIndent();
		this.closeBlock();
	}

	int fid = 0;

	class FailurePoint {
		int id;
		FailurePoint prev;

		public FailurePoint(int label, FailurePoint prev) {
			this.id = label;
			this.prev = prev;
		}
	}

	FailurePoint fLabel;

	private void initFalureJumpPoint() {
		this.fid = 0;
		fLabel = null;
	}

	private void pushFailureJumpPoint() {
		this.fLabel = new FailurePoint(this.fid++, this.fLabel);
	}

	private void popFailureJumpPoint(Production r) {
		this.file.decIndent();
		this.file.writeIndent("CATCH_FAILURE" + this.fLabel.id + ":" + "/* " + " */");
		this.file.incIndent();
		this.fLabel = this.fLabel.prev;
	}

	private void popFailureJumpPoint(Expression e) {
		this.file.decIndent();
		this.file.writeIndent("CATCH_FAILURE" + this.fLabel.id + ":" + "/* " + " */");
		this.file.incIndent();
		this.fLabel = this.fLabel.prev;
	}

	private void jumpFailureJump() {
		this.file.writeIndent("goto CATCH_FAILURE" + this.fLabel.id + ";");
	}

	private void jumpPrevFailureJump() {
		this.file.writeIndent("goto CATCH_FAILURE" + this.fLabel.prev.id + ";");
	}

	private void openBlock() {
		this.file.write(" {");
		this.file.incIndent();
	}

	private void closeBlock() {
		this.file.decIndent();
		this.file.writeIndent("}");
	}

	private void gotoLabel(String label) {
		this.file.writeIndent("goto " + label + ";");
	}

	private void exitLabel(String label) {
		this.file.decIndent();
		this.file.writeIndent(label + ": ;; /* <- this is required for avoiding empty statement */");
		this.file.incIndent();
	}

	private void let(String type, String var, String expr) {
		if(type != null) {
			this.file.writeIndent(type + " " + var + " = " + expr + ";");
		}
		else {
			this.file.writeIndent("" + var + " = " + expr + ";");
		}
	}

	private void memoize(Production rule, int id, String pos) {
		this.file.writeIndent("nez_setMemo(ctx, " + pos + ", " + id + ", 0);");
	}

	private void memoizeFail(Production rule, int id, String pos) {
		this.file.writeIndent("nez_setMemo(ctx, " + pos + ", " + id + ", 1);");
	}

	private void lookup(Production rule, int id) {
		this.file.writeIndent("MemoEntry memo = nez_getMemo(ctx, ctx->cur, " + id + ");");
		this.file.writeIndent("if(memo != NULL)");
		this.openBlock();
		this.file.writeIndent("if(memo->r)");
		this.openBlock();
		this.file.writeIndent("return 1;");
		this.closeBlock();
		this.file.writeIndent("else");
		this.openBlock();
		if(this.option.enabledASTConstruction) {
			this.file.writeIndent("nez_pushDataLog(ctx, LazyLink_T, 0, -1, NULL, memo->left);");
		}
		this.file.writeIndent("ctx->cur = memo->consumed;");
		this.file.writeIndent("return 0;");
		this.closeBlock();
		this.closeBlock();
	}

	private void consume() {
		this.file.writeIndent("ctx->cur++;");
	}

	private void choiceCount() {
		this.file.writeIndent("ctx->choiceCount++;");
	}

	int memoId = 0;

	private Expression getNonTerminalRule(Expression e) {
		while (e instanceof NonTerminal) {
			NonTerminal nterm = (NonTerminal) e;
			e = nterm.deReference();
		}
		return e;
	}

	public int specializeString(Sequence e, int start) {
		int count = 0;
		for(int i = start; i < e.size(); i++) {
			Expression inner = e.get(i);
			if(inner instanceof ByteChar) {
				count++;
			}
		}
		if(count > 1) {
//			for(int i = start; )
		}
		return 0;
	}

	public boolean checkByteMap(Choice e) {
		for(int i = 0; i < e.size(); i++) {
			Expression inner = e.get(i);
			if(!(inner instanceof ByteChar || inner instanceof ByteMap)) {
				return false;
			}
		}
		return true;
	}

	private boolean checkString(Sequence e) {
		for(int i = 0; i < e.size(); i++) {
			if(!(e.get(i) instanceof ByteChar)) {
				return false;
			}
		}
		return true;
	}

	public void specializeByteMap(Choice e) {
		boolean[] map = new boolean[256];
		for(int i = 0; i < e.size(); i++) {
			Expression inner = e.get(i);
			if(inner instanceof ByteChar) {
				map[((ByteChar) inner).byteChar] = true;
			}
			else if(inner instanceof ByteMap) {
				boolean[] bmap = ((ByteMap) inner).byteMap;
				for(int j = 0; j < bmap.length; j++) {
					if(bmap[j]) {
						map[j] = true;
					}
				}
			}
		}
		int fid = this.fid++;
		this.file.writeIndent("unsigned long bmap" + fid + "[] = {");
		for(int i = 0; i < map.length - 1; i++) {
			if(map[i]) {
				this.file.write("1, ");
			}
			else {
				this.file.write("0, ");
			}
		}
		if(map[map.length - 1]) {
			this.file.write("1");
		}
		else {
			this.file.write("0");
		}
		this.file.write("};");
		this.file.writeIndent("if(!bmap" + fid + "[(uint8_t)*ctx->cur])");
		this.openBlock();
		this.jumpFailureJump();
		this.closeBlock();
		this.file.writeIndent("ctx->cur++;");
	}

	public void specializeNotByteMap(Choice e) {
		boolean[] map = new boolean[256];
		for(int i = 0; i < e.size(); i++) {
			Expression inner = e.get(i);
			if(inner instanceof ByteChar) {
				map[((ByteChar) inner).byteChar] = true;
			}
			else if(inner instanceof ByteMap) {
				boolean[] bmap = ((ByteMap) inner).byteMap;
				for(int j = 0; j < bmap.length; j++) {
					if(bmap[j]) {
						map[j] = true;
					}
				}
			}
		}
		int fid = this.fid++;
		this.file.writeIndent("unsigned long bmap" + fid + "[] = {");
		for(int i = 0; i < map.length - 1; i++) {
			if(map[i]) {
				this.file.write("1, ");
			}
			else {
				this.file.write("0, ");
			}
		}
		if(map[map.length - 1]) {
			this.file.write("1");
		}
		else {
			this.file.write("0");
		}
		this.file.write("};");
		this.file.writeIndent("if(bmap" + fid + "[(uint8_t)*ctx->cur])");
		this.openBlock();
		this.jumpFailureJump();
		this.closeBlock();
	}

	public void specializeNotString(Sequence e) {
		for(int i = 0; i < e.size(); i++) {
			Expression inner = e.get(i);
			if(inner instanceof ByteChar) {
				this.file.writeIndent("if((int)*(ctx->cur + " + i + ") == " + ((ByteChar) inner).byteChar + ")");
				this.openBlock();
			}
		}
		this.jumpFailureJump();
		for(int i = 0; i < e.size(); i++) {
			this.closeBlock();
		}
	}

	public boolean specializeNot(Not e) {
		Expression inner = e.get(0);
		if(inner instanceof NonTerminal) {
			inner = getNonTerminalRule(inner);
		}
		if(inner instanceof ByteChar) {
			this.file.writeIndent("if((int)*ctx->cur == " + ((ByteChar) inner).byteChar + ")");
			this.openBlock();
			this.jumpFailureJump();
			this.closeBlock();
			return true;
		}
		if(inner instanceof ByteMap) {
			int fid = this.fid++;
			boolean[] map = ((ByteMap) inner).byteMap;
			this.file.writeIndent("unsigned long bmap" + fid + "[] = {");
			for(int i = 0; i < map.length - 1; i++) {
				if(map[i]) {
					this.file.write("1, ");
				}
				else {
					this.file.write("0, ");
				}
			}
			if(map[map.length - 1]) {
				this.file.write("1");
			}
			else {
				this.file.write("0");
			}
			this.file.write("};");
			this.file.writeIndent("if(bmap" + fid + "[(uint8_t)*ctx->cur])");
			this.openBlock();
			this.jumpFailureJump();
			this.closeBlock();
			return true;
		}
		if(inner instanceof Choice) {
			if(checkByteMap((Choice) inner)) {
				specializeNotByteMap((Choice) inner);
				return true;
			}
		}
		if(inner instanceof Sequence) {
			if(checkString((Sequence) inner)) {
				this.file.writeIndent("// Specialize not string");
				specializeNotString((Sequence) inner);
				return true;
			}
		}
		return false;
	}

	public void specializeOptionByteMap(Choice e) {
		boolean[] map = new boolean[256];
		for(int i = 0; i < e.size(); i++) {
			Expression inner = e.get(i);
			if(inner instanceof ByteChar) {
				map[((ByteChar) inner).byteChar] = true;
			}
			else if(inner instanceof ByteMap) {
				boolean[] bmap = ((ByteMap) inner).byteMap;
				for(int j = 0; j < bmap.length; j++) {
					if(bmap[j]) {
						map[j] = true;
					}
				}
			}
		}
		int fid = this.fid++;
		this.file.writeIndent("unsigned long bmap" + fid + "[] = {");
		for(int i = 0; i < map.length - 1; i++) {
			if(map[i]) {
				this.file.write("1, ");
			}
			else {
				this.file.write("0, ");
			}
		}
		if(map[map.length - 1]) {
			this.file.write("1");
		}
		else {
			this.file.write("0");
		}
		this.file.write("};");
		this.file.writeIndent("if(bmap" + fid + "[(uint8_t)*ctx->cur])");
		this.openBlock();
		this.file.writeIndent("ctx->cur++;");
		this.closeBlock();
	}

	public void specializeOptionString(Sequence e) {
		int fid = ++this.fid;
		String label = "EXIT_OPTION" + fid;
		String backtrack = "c" + fid;
		this.let("char*", backtrack, "ctx->cur");
		for(int i = 0; i < e.size(); i++) {
			Expression inner = e.get(i);
			if(inner instanceof ByteChar) {
				this.file.writeIndent("if((int)*(ctx->cur++) == " + ((ByteChar) inner).byteChar + ")");
				this.openBlock();
			}
		}
		this.gotoLabel(label);
		for(int i = 0; i < e.size(); i++) {
			this.closeBlock();
		}
		this.let(null, "ctx->cur", backtrack);
		this.exitLabel(label);
	}

	public boolean specializeOption(Option e) {
		Expression inner = e.get(0);
		if(inner instanceof NonTerminal) {
			inner = getNonTerminalRule(inner);
		}
		if(inner instanceof ByteChar) {
			this.file.writeIndent("if((int)*ctx->cur == " + ((ByteChar) inner).byteChar + ")");
			this.openBlock();
			this.file.writeIndent("ctx->cur++;");
			this.closeBlock();
			return true;
		}
		if(inner instanceof ByteMap) {
			int fid = this.fid++;
			boolean[] map = ((ByteMap) inner).byteMap;
			this.file.writeIndent("unsigned long bmap" + fid + "[] = {");
			for(int i = 0; i < map.length - 1; i++) {
				if(map[i]) {
					this.file.write("1, ");
				}
				else {
					this.file.write("0, ");
				}
			}
			if(map[map.length - 1]) {
				this.file.write("1");
			}
			else {
				this.file.write("0");
			}
			this.file.write("};");
			this.file.writeIndent("if(bmap" + fid + "[(uint8_t)*ctx->cur])");
			this.openBlock();
			this.file.writeIndent("ctx->cur++;");
			this.closeBlock();
			return true;
		}
		if(inner instanceof Choice) {
			if(checkByteMap((Choice) inner)) {
				specializeOptionByteMap((Choice) inner);
				return true;
			}
		}
		if(inner instanceof Sequence) {
			if(checkString((Sequence) inner)) {
				this.file.writeIndent("// specialize option string");
				specializeOptionString((Sequence) inner);
				return true;
			}
		}
		return false;
	}

	public void specializeZeroMoreByteMap(Choice e) {
		boolean[] b = new boolean[256];
		for(int i = 0; i < e.size(); i++) {
			Expression inner = e.get(i);
			if(inner instanceof ByteChar) {
				b[((ByteChar) inner).byteChar] = true;
			}
			else if(inner instanceof ByteMap) {
				boolean[] bmap = ((ByteMap) inner).byteMap;
				for(int j = 0; j < bmap.length; j++) {
					if(bmap[j]) {
						b[j] = true;
					}
				}
			}
		}
		this.file.writeIndent("while(1)");
		this.openBlock();
		for(int start = 0; start < 256; start++) {
			if(b[start]) {
				int end = searchEndChar(b, start + 1);
				if(start == end) {
					this.file.writeIndent("if((int)*ctx->cur == " + start + ")");
					this.openBlock();
					this.consume();
					this.file.writeIndent("continue;");
					this.closeBlock();
				}
				else {
					this.file.writeIndent("if(" + start + "<= (int)*ctx->cur" + " && (int)*ctx->cur <= " + end + ")");
					this.openBlock();
					this.consume();
					this.file.writeIndent("continue;");
					this.closeBlock();
					start = end;
				}
			}
		}
		this.file.writeIndent("break;");
		this.closeBlock();
	}

	public boolean specializeRepetition(Repetition e) {
		Expression inner = e.get(0);
		if(inner instanceof NonTerminal) {
			inner = getNonTerminalRule(inner);
		}
		if(inner instanceof ByteChar) {
			this.file.writeIndent("while(1)");
			this.openBlock();
			this.file.writeIndent("if((int)*ctx->cur != " + ((ByteChar) inner).byteChar + ")");
			this.openBlock();
			this.file.writeIndent("break;");
			this.closeBlock();
			this.file.writeIndent("ctx->cur++;");
			this.closeBlock();
			return true;
		}
		if(inner instanceof ByteMap) {
			boolean[] b = ((ByteMap) inner).byteMap;
			this.file.writeIndent("while(1)");
			this.openBlock();
			for(int start = 0; start < 256; start++) {
				if(b[start]) {
					int end = searchEndChar(b, start + 1);
					if(start == end) {
						this.file.writeIndent("if((int)*ctx->cur == " + start + ")");
						this.openBlock();
						this.consume();
						this.file.writeIndent("continue;");
						this.closeBlock();
					}
					else {
						this.file.writeIndent("if(" + start + "<= (int)*ctx->cur" + " && (int)*ctx->cur <= " + end + ")");
						this.openBlock();
						this.consume();
						this.file.writeIndent("continue;");
						this.closeBlock();
						start = end;
					}
				}
			}
			this.file.writeIndent("break;");
			this.closeBlock();
			return true;
		}
		if(inner instanceof Choice) {
			if(checkByteMap((Choice) inner)) {
				this.file.writeIndent("// specialize repeat choice");
				specializeZeroMoreByteMap((Choice) inner);
				return true;
			}
		}
		return false;
	}

	@Override
	public void visitProduction(Production rule) {
		this.initFalureJumpPoint();
		this.file.writeIndent("int p" + rule.getLocalName() + "(ParsingContext ctx)");
		this.openBlock();
		this.pushFailureJumpPoint();
		if(this.option.enabledPackratParsing) {
			lookup(rule, memoId);
			String pos = "c" + this.fid;
			this.let("char*", pos, "ctx->cur");
			Expression e = optimizer.optimize(rule);
			visitExpression(e);
			memoize(rule, memoId, pos);
			this.file.writeIndent("return 0;");
			this.popFailureJumpPoint(rule);
			memoizeFail(rule, memoId, pos);
			this.file.writeIndent("return 1;");
			this.closeBlock();
			this.file.writeNewLine();
			memoId++;
		}
		else {
			String pos = "c" + this.fid;
			this.let("char*", pos, "ctx->cur");
			Expression e = optimizer.optimize(rule);
			visitExpression(e);
			this.file.writeIndent("return 0;");
			this.popFailureJumpPoint(rule);
			this.file.writeIndent("return 1;");
			this.closeBlock();
			this.file.writeNewLine();
		}
	}

	@Override
	public void visitEmpty(Expression e) {
	}

	@Override
	public void visitFailure(Expression e) {
		this.jumpFailureJump();
	}

	boolean inlining = true;
	int dephth = 0;

	@Override
	public void visitNonTerminal(NonTerminal e) {
//		if(!e.getProduction().isRecursive() && dephth < 3 && inlining) {
//			Expression ne = this.getNonTerminalRule(e);
//			dephth++;
//			visit(ne);
//			dephth--;
//			return;
//		}
		this.file.writeIndent("if(p" + e.getLocalName() + "(ctx))");
		this.openBlock();
		this.jumpFailureJump();
		this.closeBlock();
	}

	public String stringfyByte(int byteChar) {
		char c = (char) byteChar;
		switch (c) {
		case '\n':
			return ("'\\n'");
		case '\t':
			return ("'\\t'");
		case '\r':
			return ("'\\r'");
		case '\'':
			return ("\'\\\'\'");
		case '\\':
			return ("'\\\\'");
		}
		return "\'" + c + "\'";
	}

	@Override
	public void visitByteChar(ByteChar e) {
		this.file.writeIndent("if((int)*ctx->cur != " + e.byteChar + ")");
		this.openBlock();
		this.jumpFailureJump();
		this.closeBlock();
		this.consume();
	}

	private int searchEndChar(boolean[] b, int s) {
		for(; s < 256; s++) {
			if(!b[s]) {
				return s - 1;
			}
		}
		return 255;
	}

	@Override
	public void visitByteMap(ByteMap e) {
		int fid = this.fid++;
		String label = "EXIT_BYTEMAP" + fid;
		boolean b[] = e.byteMap;
		for(int start = 0; start < 256; start++) {
			if(b[start]) {
				int end = searchEndChar(b, start + 1);
				if(start == end) {
					this.file.writeIndent("if((int)*ctx->cur == " + start + ")");
					this.openBlock();
					this.consume();
					this.gotoLabel(label);
					this.closeBlock();
				}
				else {
					this.file.writeIndent("if(" + start + "<= (int)*ctx->cur" + " && (int)*ctx->cur <= " + end + ")");
					this.openBlock();
					this.consume();
					this.gotoLabel(label);
					this.closeBlock();
					start = end;
				}
			}
		}
		this.jumpFailureJump();
		this.exitLabel(label);
//		int fid = this.fid++;
//		boolean[] map = e.byteMap;
//		this.file.writeIndent("unsigned long bmap" + fid + "[] = {");
//		for(int i = 0; i < map.length - 1; i++) {
//			if(map[i]) {
//				this.file.write("1, ");
//			}
//			else {
//				this.file.write("0, ");
//			}
//		}
//		if(map[map.length - 1]) {
//			this.file.write("1");
//		}
//		else {
//			this.file.write("0");
//		}
//		this.file.write("};");
//		this.file.writeIndent("if(!bmap" + fid + "[(uint8_t)*ctx->cur])");
//		this.openBlock();
//		this.jumpFailureJump();
//		this.closeBlock();
//		this.file.writeIndent("ctx->cur++;");
	}

	@Override
	public void visitAnyChar(AnyChar e) {
		this.file.writeIndent("if(*ctx->cur == 0)");
		this.openBlock();
		this.jumpFailureJump();
		this.closeBlock();
		this.consume();
	}

	@Override
	public void visitCharMultiByte(CharMultiByte p) {
		// TODO Auto-generated method stub
		
	}

	
	@Override
	public void visitOption(Option e) {
		if(!specializeOption(e)) {
			this.pushFailureJumpPoint();
			String label = "EXIT_OPTION" + this.fid;
			String backtrack = "c" + this.fid;
			this.let("char*", backtrack, "ctx->cur");
			visitExpression(e.get(0));
			this.gotoLabel(label);
			this.popFailureJumpPoint(e);
			this.let(null, "ctx->cur", backtrack);
			this.exitLabel(label);
		}
	}

	@Override
	public void visitRepetition(Repetition e) {
		if(!specializeRepetition(e)) {
			this.pushFailureJumpPoint();
			String backtrack = "c" + this.fid;
			this.let("char*", backtrack, "ctx->cur");
			this.file.writeIndent("while(1)");
			this.openBlock();
			visitExpression(e.get(0));
			this.let(null, backtrack, "ctx->cur");
			this.closeBlock();
			this.popFailureJumpPoint(e);
			this.let(null, "ctx->cur", backtrack);
		}
	}

	@Override
	public void visitRepetition1(Repetition1 e) {
		visitExpression(e.get(0));
		this.pushFailureJumpPoint();
		String backtrack = "c" + this.fid;
		this.let("char*", backtrack, "ctx->cur");
		this.file.writeIndent("while(1)");
		this.openBlock();
		visitExpression(e.get(0));
		this.let(null, backtrack, "ctx->cur");
		this.closeBlock();
		this.popFailureJumpPoint(e);
		this.let(null, "ctx->cur", backtrack);
	}

	@Override
	public void visitAnd(And e) {
		this.pushFailureJumpPoint();
		String label = "EXIT_AND" + this.fid;
		String backtrack = "c" + this.fid;
		this.let("char*", backtrack, "ctx->cur");
		visitExpression(e.get(0));
		this.let(null, "ctx->cur", backtrack);
		this.gotoLabel(label);
		this.popFailureJumpPoint(e);
		this.let(null, "ctx->cur", backtrack);
		this.jumpFailureJump();
		this.exitLabel(label);
	}

	@Override
	public void visitNot(Not e) {
		if(!specializeNot(e)) {
			this.pushFailureJumpPoint();
			String backtrack = "c" + this.fid;
			this.let("char*", backtrack, "ctx->cur");
			visitExpression(e.get(0));
			this.let(null, "ctx->cur", backtrack);
			this.jumpPrevFailureJump();
			this.popFailureJumpPoint(e);
			this.let(null, "ctx->cur", backtrack);
		}
	}

	@Override
	public void visitSequence(Sequence e) {
		for(int i = 0; i < e.size(); i++) {
			visitExpression(e.get(i));
		}
	}

	boolean isPrediction = true;
	int justPredictionCount = 0;

	public String formatId(int id) {
		String idStr = Integer.toString(id);
		int len = idStr.length();
		StringBuilder sb = new StringBuilder();
		while (len < 9) {
			sb.append("0");
			len++;
		}
		sb.append(idStr);
		return sb.toString();
	}

	private void showChoiceInfo(Choice e) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.toString() + ",").append(e.size() + ",");
		if(e.predictedCase != null) {
			int notNullSize = 0;
			int notChoiceSize = 0;
			int containsEmpty = 0;
			int subChoiceSize = 0;
			for(int i = 0; i < e.predictedCase.length; i++) {
				if(e.predictedCase[i] != null) {
					notNullSize++;
					if(e.predictedCase[i] instanceof Choice) {
						subChoiceSize += e.predictedCase[i].size();
					}
					else {
						notChoiceSize++;
						if(e.predictedCase[i].isEmpty()) {
							containsEmpty = 1;
						}
					}
				}
			}
			double evaluationValue = (double) (notChoiceSize + subChoiceSize) / (double) notNullSize;
			NumberFormat format = NumberFormat.getInstance();
			format.setMaximumFractionDigits(3);
			sb.append(notNullSize + ",").append(notChoiceSize + ",").append(containsEmpty + ",").append(subChoiceSize + ",")
					.append(format.format(evaluationValue));
		}
		System.out.println(sb.toString());
	}

	@Override
	public void visitChoice(Choice e) {
//		showChoiceInfo(e);
		if((e.predictedCase != null && isPrediction && this.option.enabledPrediction)) {
			predictionCount++;
			justPredictionCount++;
			int fid = this.fid++;
			String label = "EXIT_CHOICE" + fid;
			HashMap<Integer, Expression> m = new HashMap<Integer, Expression>();
			ArrayList<Expression> l = new ArrayList<Expression>();
			this.file.writeIndent("void* jump_table" + formatId(fid) + "[] = {");
			for(int ch = 0; ch < e.predictedCase.length; ch++) {
				Expression pCase = e.predictedCase[ch];
				if(pCase != null) {
					Expression me = m.get(pCase.getId());
					if(me == null) {
						m.put(pCase.getId(), pCase);
						l.add(pCase);
					}
					this.file.write("&&PREDICATE_JUMP" + formatId(fid) + "" + pCase.getId());
				}
				else {
					this.file.write("&&PREDICATE_JUMP" + formatId(fid) + "" + 0);
				}
				if(ch < e.predictedCase.length - 1) {
					this.file.write(", ");
				}
			}
			this.file.write("};");
			this.file.writeIndent("goto *jump_table" + formatId(fid) + "[(uint8_t)*ctx->cur];");
			for(int i = 0; i < l.size(); i++) {
				Expression pe = l.get(i);
				this.exitLabel("PREDICATE_JUMP" + formatId(fid) + "" + pe.getId());
				if(!(pe instanceof Choice)) {
					this.choiceCount();
				}
				else {
					isPrediction = false;
				}
				visitExpression(pe);
				isPrediction = true;
				this.gotoLabel(label);
			}
			this.exitLabel("PREDICATE_JUMP" + formatId(fid) + "" + 0);
			this.jumpFailureJump();
			this.exitLabel(label);
			justPredictionCount--;
		}
		else {
			this.fid++;
			String label = "EXIT_CHOICE" + this.fid;
			String backtrack = "c" + this.fid;
			this.let("char*", backtrack, "ctx->cur");
			for(int i = 0; i < e.size(); i++) {
				this.pushFailureJumpPoint();
				this.choiceCount();
				visitExpression(e.get(i));
				this.gotoLabel(label);
				this.popFailureJumpPoint(e.get(i));
				this.let(null, "ctx->cur", backtrack);
			}
			this.jumpFailureJump();
			this.exitLabel(label);
		}
	}

	Stack<String> markStack = new Stack<String>();

	@Override
	public void visitNew(New e) {
		if(this.option.enabledASTConstruction) {
			this.pushFailureJumpPoint();
			String mark = "mark" + this.fid;
			this.markStack.push(mark);
			this.file.writeIndent("int " + mark + " = nez_markLogStack(ctx);");
			this.file.writeIndent("nez_pushDataLog(ctx, LazyNew_T, ctx->cur - ctx->inputs, -1, NULL, NULL);");
		}
	}

	@Override
	public void visitCapture(Capture e) {
		if(this.option.enabledASTConstruction) {
			String label = "EXIT_CAPTURE" + this.fid++;
			this.file.writeIndent("nez_pushDataLog(ctx, LazyCapture_T, ctx->cur - ctx->inputs, 0, NULL, NULL);");
			this.gotoLabel(label);
			this.popFailureJumpPoint(e);
			this.file.writeIndent("nez_abortLog(ctx, " + this.markStack.pop() + ");");
			this.jumpFailureJump();
			this.exitLabel(label);
		}
	}

	@Override
	public void visitTagging(Tagging e) {
		if(this.option.enabledASTConstruction) {
			this.file.writeIndent("nez_pushDataLog(ctx, LazyTag_T, 0, 0, \"" + e.tag.getName() + "\", NULL);");
		}
	}

	@Override
	public void visitReplace(Replace e) {
		if(this.option.enabledASTConstruction) {
			this.file.writeIndent("nez_pushDataLog(ctx, LazyValue_T, 0, 0, \"" + e.value + "\", NULL);");
		}
	}

	@Override
	public void visitLink(Link e) {
		if(this.option.enabledASTConstruction) {
			this.pushFailureJumpPoint();
			String mark = "mark" + this.fid;
			String label = "EXIT_LINK" + this.fid;
			String po = "ctx->left"; //+ this.fid;
			this.file.writeIndent("int " + mark + " = nez_markLogStack(ctx);");
			visitExpression(e.get(0));
			this.let(null, po, "nez_commitLog(ctx, " + mark + ")");
			this.file.writeIndent("nez_pushDataLog(ctx, LazyLink_T, 0, " + e.index + ", NULL, " + po + ");");
			this.gotoLabel(label);
			this.popFailureJumpPoint(e);
			this.file.writeIndent("nez_abortLog(ctx, " + mark + ");");
			this.jumpFailureJump();
			this.exitLabel(label);
		}
		else {
			visitExpression(e.get(0));
		}
	}

	ArrayList<String> flagTable = new ArrayList<String>();

	public void visitIfFlag(IfFlag e) {
		if(!flagTable.contains(e.getFlagName())) {
			flagTable.add(e.getFlagName());
		}
		if(e.isPredicate()) {
			this.file.writeIndent("if(!ctx->flags[" + flagTable.indexOf(e.getFlagName()) + "])");
			this.openBlock();
			this.jumpFailureJump();
			this.closeBlock();
		}
		else {
			this.file.writeIndent("if(ctx->flags[" + flagTable.indexOf(e.getFlagName()) + "])");
			this.openBlock();
			this.jumpFailureJump();
			this.closeBlock();
		}
	}

	public void visitOnFlag(OnFlag e) {
		if(!flagTable.contains(e.getFlagName())) {
			flagTable.add(e.getFlagName());
		}
		visitExpression(e.get(0));
		if(e.isPositive()) {
			this.file.writeIndent("ctx->flags[" + flagTable.indexOf(e.getFlagName()) + "] = 1;");
		}
		else {
			this.file.writeIndent("ctx->flags[" + flagTable.indexOf(e.getFlagName()) + "] = 0;");
		}
	}

	@Override
	public void visitBlock(Block p) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitDefSymbol(DefSymbol p) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitIsSymbol(IsSymbol p) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitDefIndent(DefIndent p) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitIsIndent(IsIndent p) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitExistsSymbol(ExistsSymbol p) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitLocalTable(LocalTable p) {
		// TODO Auto-generated method stub
		
	}


}
