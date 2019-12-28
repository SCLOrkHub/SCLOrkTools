SCLOrkCDT {
	var diagCallback;
	var diagOSCFunc;
	var cohortPQMap;
	var jitterTask;

	*new { |diagCallback|
		^super.new.init(diagCallback);
	}

	init { |callback|
		diagCallback = callback;
		cohortPQMap = IdentityDictionary.new;

		diagOSCFunc = OSCFunc.new({ |msg, time, addr|
			var cohortName = msg[1].asSymbol;
			var beat = Float.from64Bits(msg[2], msg[3]);
			var localTime = Float.from64Bits(msg[4], msg[5]);
			var serverTime = Float.from64Bits(msg[6], msg[7]);
			var timeDiff = Float.from64Bits(msg[8], msg[9]);
			var bar = Float.from64Bits(msg[10], msg[11]);
			var beatInBar = Float.from64Bits(msg[12], msg[13]);
			var applyAtTime = Float.from64Bits(msg[14], msg[15]);
			var applyAtBeat = Float.from64Bits(msg[16], msg[17]);
			var diag = IdentityDictionary.newFrom([
				\address, addr,
				\cohortName, cohortName,
				\beat, beat,
				\localTime, localTime,
				\serverTime, serverTime,
				\timeDiff, timeDiff,
				\bar, bar,
				\beatInBar, beatInBar,
				\applyAtTime, applyAtTime,
				\applyAtBeat, applyAtBeat
			]);
			diagCallback.value(time, diag);
			if (cohortPQMap.includesKey(cohortName).not, {
				cohortPQMap.put(cohortName, PriorityQueue.new);
			});
			cohortPQMap.at(cohortName).put(time, diag);
		},
		path: '/sclorkClockDiag').permanent_(true);

		jitterTask = SkipJack.new({
			cohortPQMap.keysValuesDo({ |cohortName, pq|
				// For now just pop everything off.
				pq.clear();
			});
		},
		dt: 0.5,
		stopTest: { false },
		name: "SCLOrkCDT Jitter"
		);
	}

	free {
		diagCallback = {};
		jitterTask.stop;
		diagOSCFunc.disable;
	}
}
