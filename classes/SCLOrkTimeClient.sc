SCLOrkTimeClient {
	const <defaultOscPath = '/setTimeDiff';
	const <defaultOscPort = 7702;
	const historySize = 60;
	// Address of SCLOrkTimeClient instance
	var serverNetAddr;
	var oscPath;
	var oscPort;
	var setTimeBaseOscFunc;

	// Stores when request was issues, for round-trip time computation.
	var requestLastSent;

	// Arrays of size |historySize| for storing the most recent computed
	// values of both the diffs and the round trip times.
	var timeDiffs;
	var roundTripTimes;

	// Rolling sums of both values, to avoid having to recompute the sums
	// each time a new value is added.
	var timeDiffSum;
	var roundTripSum;
	// Current index of the computed sum, used for rolling through the
	// array.
	var sumIndex;

	// Mean values computed from the query.
	var <timeDiff;
	var roundTripTime;

	// Task to call the server periodically and update the mean diff.
	var updateDiffTask;
	var quitTasks;

	*new { | serverNetAddr, oscPath, oscPort |
		^super.newCopyArgs(serverNetAddr, oscPath, oscPort).init;
	}

	init {
		if (serverNetAddr.isNil, {
			serverNetAddr = NetAddr.new("sclork-s01.local",
				SCLOrkTimeServer.defaultOscPort);
		});
		if (oscPath.isNil, { oscPath = defaultOscPath; });
		if (oscPort.isNil, { oscPort = defaultOscPort; });
		timeDiffs = Array.newClear(historySize);
		timeDiffSum = 0.0;
		roundTripTimes = Array.newClear(historySize);
		roundTripSum = 0.0;
		sumIndex = 0;
		timeDiff = 0.0;
		roundTripTime = 0.0;
		requestLastSent = Main.elapsedTime;

		setTimeBaseOscFunc = OSCFunc({ | msg, time, addr |
			var diff, rtt, n;
			diff = Float.from64Bits(msg[1], msg[2]);
			rtt = Main.elapsedTime - requestLastSent;
			// Recompute means.
			if (timeDiffs[sumIndex].notNil, {
				timeDiffSum = timeDiffSum - timeDiffs[sumIndex];
				roundTripSum = roundTripSum - roundTripTimes[sumIndex];
				n = historySize.asFloat;
			}, {
				n = (sumIndex + 1).asFloat;
			});
			timeDiffs[sumIndex] = diff;
			roundTripTimes[sumIndex] = rtt;
			sumIndex = (sumIndex + 1) % historySize;
			timeDiffSum = timeDiffSum + diff;
			roundTripSum = roundTripSum + rtt;
			roundTripTime = roundTripSum / n;
			timeDiff = (timeDiffSum / n) - (roundTripTime / 2.0);
		}, oscPath, recvPort: oscPort).fix;

		quitTasks = false;
		updateDiffTask = SkipJack.new({
			requestLastSent = Main.elapsedTime;
			serverNetAddr.sendMsg(SCLOrkTimeServer.defaultOscPath,
				requestLastSent.high32Bits,
				requestLastSent.low32Bits,
				oscPath,
				oscPort
			);
		},
		dt: 0.5,
		stopTest: { quitTasks },
		name: "TimeBaseClientSync",
		clock: SystemClock,
		autostart: true
		);
	}

	free {
		quitTasks = true;
		setTimeBaseOscFunc.free;
	}
}
