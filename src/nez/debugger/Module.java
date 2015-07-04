package nez.debugger;

import java.util.ArrayList;
import java.util.List;

import nez.util.ConsoleUtils;

public class Module {
	List<Function> funcList;

	public Module() {
		this.funcList = new ArrayList<Function>();
	}

	public DebugVMInstruction getStartPoint() {
		for(Function func : this.funcList) {
			if(func.funcName.equals("File")) {
				return func.get(0).get(0);
			}
		}
		ConsoleUtils.exit(1, "error: StartPoint is not found");
		return null;
	}

	public Function get(int index) {
		return this.funcList.get(index);
	}

	public void append(Function func) {
		this.funcList.add(func);
	}

	public int size() {
		return funcList.size();
	}

	public String stringfy(StringBuilder sb) {
		for(int i = 0; i < size(); i++) {
			this.get(i).stringfy(sb);
		}
		return sb.toString();
	}
}
