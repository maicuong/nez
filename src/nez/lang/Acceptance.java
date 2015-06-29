package nez.lang;

import nez.NezOption;
import nez.ast.Source;
import nez.util.UFlag;

public class Acceptance {
	public final static int TextEOF   = 0;
	public final static int BinaryEOF = 256;
	public final static short Accept = 0;
	public final static short Unconsumed = 1;
	public final static short Reject = 2;
		
	public static boolean predictAnyChar(int option, int ch, boolean k) {
		switch(ch) {
		case 0:
			return UFlag.is(option, NezOption.Binary);
		case Source.BinaryEOF:
			return false;
		}
		return true;
	}

	public static void predictAnyChar(int option, boolean[] dfa) {
		for(int c = 1; c < dfa.length - 1; c++) {
			dfa[c] = true;
		}
		dfa[0] = UFlag.is(option, NezOption.Binary);
		dfa[Source.BinaryEOF] = false;
	}

	public static short acceptUnary(Expression e, int ch, int option) {
		return e.get(0).acceptByte(ch, option);
	}
	
	public static short acceptOption(Expression e, int ch, int option) {
		short r = e.get(0).acceptByte(ch, option);
		return (r == Acceptance.Accept) ? r : Acceptance.Unconsumed;
	}
	
	public static short acceptSequence(Expression e, int ch, int option) {
		for(int i = 0; i < e.size(); i++) {
			short r = e.get(i).acceptByte(ch, option);
			if(r != Acceptance.Unconsumed) {
				return r;
			}
		}
		return Acceptance.Unconsumed;
	}
		
}
