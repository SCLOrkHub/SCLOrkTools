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
			var diag = IdentityDictionary.newFrom([
				\cohortName, cohortName,
				\beat, beat,
				\localTime, localTime,
				\serverTime, serverTime,
				\timeDiff, timeDiff,
				\address, addr
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
}
