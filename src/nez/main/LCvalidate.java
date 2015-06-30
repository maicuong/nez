package nez.main;

import nez.SourceContext;
import nez.lang.Grammar;
import nez.util.ConsoleUtils;
import nez.util.UList;

class LCvalidate extends Command {
	@Override
	public String getDesc() {
		return "grammar validator";
	}

	@Override
	public void exec(CommandContext config) {
		UList<String> failedInputs = new UList<String>(new String[4]);
		UList<String> unconsumedInputs = new UList<String>(new String[4]);
		config.getNezOption().setOption("ast", false);
		Grammar g = config.getGrammar();
		
		int totalCount = 0, failureCount = 0, unconsumedCount = 0;
		long consumed = 0;
		long time = 0;
		
		while(config.hasInputSource()) {
			SourceContext file = config.nextInputSource();
			totalCount++;
			long t = System.nanoTime();
			boolean result = g.match(file);
			long t2 = System.nanoTime();
			if(!result) {
				ConsoleUtils.println(file.getSyntaxErrorMessage());
				failedInputs.add(file.getResourceName());
				failureCount++;
				continue;
			}
			if(file.hasUnconsumed()) {
				ConsoleUtils.println(file.getUnconsumedMessage());
				unconsumedInputs.add(file.getResourceName());
				unconsumedCount++;
			}
			consumed += file.getPosition();
			time += (t2 - t);
			g.logProfiler();
		}
		if(totalCount > 1){
			Verbose.println(
					totalCount + " files, " +
//					(((double)consumed / (time / 1000 / 1000))) + " KB/s, " + 
					failureCount + " failed, " +
					unconsumedCount + " uncosumed, " +
					(100 - 100.0 * (unconsumedCount+failureCount)/totalCount) + "% passed.");
		}
		if(unconsumedInputs.size() > 0) {
			Verbose.println("unconsumed: " + unconsumedInputs);
		}
		if(failedInputs.size() > 0) {
			ConsoleUtils.exit(1, "failed: " + failedInputs);
		}
	}

}