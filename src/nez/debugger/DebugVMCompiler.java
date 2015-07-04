package nez.debugger;

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
import nez.lang.IsIndent;
import nez.lang.IsSymbol;
import nez.lang.Link;
import nez.lang.LocalTable;
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
import nez.util.ConsoleUtils;
import nez.vm.Instruction;
import nez.vm.NezEncoder;

public class DebugVMCompiler extends NezEncoder {
	Grammar peg;
	IRBuilder builder;

	public DebugVMCompiler(NezOption option) {
		super(option);
		this.builder = new IRBuilder(new Module());
	}

	public Module compile(Grammar grammar) {
		for(Production p : grammar.getProductionList()) {
			this.encodeProduction(p);
		}
		ConsoleUtils.println(this.builder.getModule().stringfy(new StringBuilder()));
		return this.builder.buildInstructionSequence();
	}

	public Instruction encodeProduction(Production p) {
		this.builder.setFunction(new Function(p.getLocalName()));
		this.builder.setInsertPoint(new BasicBlock());
		BasicBlock fbb = new BasicBlock();
		this.builder.pushFailureJumpPoint(fbb);
		p.encode(this, null, null);
		this.builder.setInsertPoint(this.builder.popFailureJumpPoint());
		this.builder.createIret(p);
		return null;
	}

	@Override
	public Instruction encodeExpression(Expression e, Instruction next, Instruction failjump) {
		return e.encode(this, next, failjump);
	}

	@Override
	public Instruction encodeFail(Expression p) {
		this.builder.createIfail(p);
		return null;
	}

	@Override
	public Instruction encodeAnyChar(AnyChar p, Instruction next, Instruction failjump) {
		this.builder.createIany(p, this.builder.jumpFailureJump());
		this.builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public Instruction encodeByteChar(ByteChar p, Instruction next, Instruction failjump) {
		this.builder.createIchar(p, this.builder.jumpFailureJump());
		this.builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public Instruction encodeByteMap(ByteMap p, Instruction next, Instruction failjump) {
		this.builder.createIcharclass(p, this.builder.jumpFailureJump());
		this.builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public Instruction encodeCharMultiByte(CharMultiByte p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeOption(Option p, Instruction next) {
		BasicBlock fbb = new BasicBlock();
		BasicBlock mergebb = new BasicBlock();
		this.builder.pushFailureJumpPoint(fbb);
		this.builder.createIpush(p);
		p.get(0).encode(this, next, null);
		this.builder.createIpop(p);
		this.builder.createIjump(p, mergebb);
		this.builder.setInsertPoint(this.builder.popFailureJumpPoint());
		this.builder.createIsucc(p);
		this.builder.createIpeek(p);
		this.builder.createIpop(p);
		this.builder.setInsertPoint(mergebb);
		return null;
	}

	@Override
	public Instruction encodeRepetition(Repetition p, Instruction next) {
		BasicBlock topBB = new BasicBlock();
		this.builder.setInsertPoint(topBB);
		BasicBlock fbb = new BasicBlock();
		BasicBlock mergebb = new BasicBlock();
		this.builder.pushFailureJumpPoint(fbb);
		this.builder.createIpush(p);
		p.get(0).encode(this, next, null);
		this.builder.createIpop(p);
		this.builder.createIjump(p, mergebb);
		this.builder.setInsertPoint(this.builder.popFailureJumpPoint());
		this.builder.createIsucc(p);
		this.builder.createIpeek(p);
		this.builder.createIpop(p);
		this.builder.setInsertPoint(mergebb);
		return null;
	}

	@Override
	public Instruction encodeRepetition1(Repetition1 p, Instruction next, Instruction failjump) {
		p.get(0).encode(this, next, failjump);
		BasicBlock topBB = new BasicBlock();
		this.builder.setInsertPoint(topBB);
		BasicBlock fbb = new BasicBlock();
		BasicBlock mergebb = new BasicBlock();
		this.builder.pushFailureJumpPoint(fbb);
		this.builder.createIpush(p);
		p.get(0).encode(this, next, failjump);
		this.builder.createIpop(p);
		this.builder.createIjump(p, mergebb);
		this.builder.setInsertPoint(this.builder.popFailureJumpPoint());
		this.builder.createIsucc(p);
		this.builder.createIpeek(p);
		this.builder.createIpop(p);
		this.builder.setInsertPoint(mergebb);
		return null;
	}

	@Override
	public Instruction encodeAnd(And p, Instruction next, Instruction failjump) {
		this.builder.createIpush(p);
		p.get(0).encode(this, next, failjump);
		this.builder.createIpeek(p);
		this.builder.createIpop(p);
		this.builder.createIiffail(p, this.builder.jumpFailureJump());
		this.builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public Instruction encodeNot(Not p, Instruction next, Instruction failjump) {
		BasicBlock fbb = new BasicBlock();
		this.builder.pushFailureJumpPoint(fbb);
		this.builder.createIpush(p);
		p.get(0).encode(this, next, failjump);
		this.builder.createIfail(p);
		this.builder.createIpeek(p);
		this.builder.createIpop(p);
		this.builder.createIjump(p, this.builder.jumpPrevFailureJump());
		this.builder.setInsertPoint(this.builder.popFailureJumpPoint());
		this.builder.createIsucc(p);
		this.builder.createIpeek(p);
		this.builder.createIpop(p);
		return null;
	}

	@Override
	public Instruction encodeSequence(Sequence p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeChoice(Choice p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeNonTerminal(NonTerminal p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeLink(Link p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeNew(New p, Instruction next) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeCapture(Capture p, Instruction next) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeTagging(Tagging p, Instruction next) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeReplace(Replace p, Instruction next) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeBlock(Block p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeDefSymbol(DefSymbol p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeIsSymbol(IsSymbol p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeDefIndent(DefIndent p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeIsIndent(IsIndent p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeExistsSymbol(ExistsSymbol existsSymbol, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeLocalTable(LocalTable localTable, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instruction encodeExtension(Expression p, Instruction next, Instruction failjump) {
		// TODO Auto-generated method stub
		return null;
	}

}
