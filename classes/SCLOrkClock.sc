SCLOrkClock {
	const historySize = 60;
	const <syncPort = 4249;

	classvar syncStarted;
	classvar <clockMap;
	classvar syncNetAddr;
	classvar timeDiffs;
	classvar timeDiffSum;
	classvar roundTripTimes;
	classvar roundTripTimeSum;
	classvar sumIndex;
	classvar timeDiff;
	classvar changeQueue;
	classvar requestLastSent;
	classvar clockSyncOSCFunc;
	classvar syncTask;
	classvar wire;

	var <name;
	var <tempo;
	var <beatsPerBar;
	var <baseBar;
	var <baseBarBeat;
	var <beatAtLastTempoChange;
	var <timeAtLastTempoChange;
	var <isRunning;
	var <permanent;

	var queue;

	*startSync { | serverName = "sclork-s01.local" |
		if (syncStarted.isNil, {
			syncStarted = true;
			clockMap = IdentityDictionary.new;
			syncNetAddr = NetAddr.new(serverName, SCLOrkClockServer.syncPort);
			timeDiffs = Array.newClear(historySize);
			timeDiffSum = 0.0;
			roundTripTimes = Array.newClear(historySize);
			roundTripTimeSum = 0.0;
			sumIndex = 0;
			timeDiff = 0.0;
			changeQueue = PriorityQueue.new;

			clockSyncOSCFunc = OSCFunc.new({ | msg, time, addr |
				var serverTime = Float.from64Bits(msg[1], msg[2]);
				var diff = time - serverTime;
				var roundTripTime = time - requestLastSent;
				var n;

				if (timeDiffs[sumIndex].notNil, {
					timeDiffSum = timeDiffSum - timeDiffs[sumIndex];
					roundTripTimeSum = roundTripTimeSum - roundTripTimes[sumIndex];
					n = historySize.asFloat;
				}, {
					n = (sumIndex + 1).asFloat;
				});

				timeDiffs[sumIndex] = diff;
				roundTripTimes[sumIndex] = roundTripTime;
				sumIndex = (sumIndex + 1) % historySize;
				timeDiffSum = timeDiffSum + diff;
				roundTripTimeSum = roundTripTimeSum + roundTripTime;
				timeDiff = (timeDiffSum / n) - (roundTripTimeSum / (2.0 * n));
			},
			path: '/clockSyncSet',
			recvPort: syncPort,
			).permanent_(true);

			syncTask = SkipJack.new({
				requestLastSent = Main.elapsedTime;
				syncNetAddr.sendMsg('/clockSyncGet', syncPort);
			},
			dt: 5.0,
			stopTest: { false },
			name: "SCLOrkClock Sync"
			);

			wire = SCLOrkWire.new(4248);
			wire.onConnected = { | wire, status |
				switch (status,
					\connected, {
						// Request curent list of all clocks.
						wire.sendMsg('/clockGetAll');
					},
					\failureTimeout, {
						"*** clock server connection failed.".postln;
						clockMap.clear;  // -- cleanup??
					},
					\disconnected, {
						clockMap.clear;  // -- cleanup??
					}
				);
			};
			wire.onMessageReceived = { | wire, msg |
				switch (msg[0],
					'/clockUpdate', {
						var name = msg[1];
						var applyAtTime = SCLOrkClock.serverToLocalTime(
							Float.from64Bits(msg[2], msg[3]));
						var tempo = Float.from64Bits(msg[4], msg[5]);
						var beatsPerBar = Float.from64Bits(msg[6], msg[7]);
						var baseBarBeat = Float.from64Bits(msg[8], msg[9]);
						var beatAtLastTempoChange = SCLOrkClock.serverToLocalTime(
							Float.from64Bits(msg[10], msg[11]));
						var timeAtLastTempoChange = SCLOrkClock.serverToLocalTime(
							Float.from64Bits(msg[12], msg[13]));
						SystemClock.schedAbs(applyAtTime, {
							var clock = clockMap.at(name);
							if (clock.isNil, {
								clock = super.newCopyArgs(
									name,
									tempo,
									beatsPerBar,
									baseBarBeat).init;
								clockMap.put(name, clock);
							});
							clock.prUpdate(
								tempo,
								beatsPerBar,
								baseBarBeat,
								beatAtLastTempoChange,
								timeAtLastTempoChange);
						});
					}
				);
			};
			wire.knock(serverName, SCLOrkClockServer.knockPort);
		});
	}

	*serverToLocalTime { | serverTime |
		^(timeDiff + serverTime);
	}

	*localToServerTime { | localTime |
		^(localTime - timeDiff);
	}

	*new { | name = \default, tempo = 1.0,
		beatsPerBar = 4.0, serverName = "sclork-s01.local" |
		var clock;

		if (syncStarted.not, {
			SCLOrkClock.startSync(serverName);
		});

		clock = clockMap.at(name);
		if (clock.isNil, {
			var serverTime;
			clock = super.newCopyArgs(name, tempo, beatsPerBar).init;
			serverTime = SCLOrkClock.localToServerTime(clock.timeAtLastTempoChange);
			clockMap.put(name, clock);
			wire.sendMsg(
				'/clockRegister', name,
				serverTime.high32Bits, serverTime.low32Bits,
				tempo.high32Bits, tempo.low32Bits,
				beatsPerBar.high32Bits, beatsPerBar.low32Bits);
		});
		^clock;
	}

	// Moved from instance method .stop because will stop all clocks in the cohort.
	*stopClock { | name |
		wire.sendMsg('/clockStop', name);
	}

	init {
		baseBarBeat = 0.0;
		beatAtLastTempoChange = 0.0;
		timeAtLastTempoChange = Main.elapsedTime;
		baseBar = 0.0;
		isRunning = true;
		queue = PriorityQueue.new;
		permanent = false;
		CmdPeriod.add(this);
	}

	free {
		queue.clear;
		CmdPeriod.remove(this);
	}

	prUpdate { |
		newTempo,
		newBeatsPerBar,
		newBaseBarBeat,
		newBeatAtLastTempoChange,
		newTimeAtLastTempoChange |
		tempo = newTempo;
		beatsPerBar = newBeatsPerBar;
		baseBarBeat = newBaseBarBeat;
		beatAtLastTempoChange = newBeatAtLastTempoChange;
		timeAtLastTempoChange = newTimeAtLastTempoChange;

		// Change in tempo can mean change in timing of items in the
		// queue, re-schedule the next task.
		this.prScheduleTop;
	}

	prStopLocal {

	}

	prScheduleTop {
		var nextBeat = queue.topPriority;
		if (nextBeat.notNil, {
			var nextTime = this.beats2secs(nextBeat);
			SystemClock.schedAbs(nextTime, { this.prAdvance });
		});
	}

	prAdvance {
		var secs = thisThread.seconds;
		var beats = this.beats;
		var topBeat;
		while ({
			topBeat = queue.topPriority;
			topBeat.notNil and: { topBeat <= beats }}, {
			var task = queue.pop;
			var repeat = task.awake(beats, secs, this);
			if (repeat.isNumber, {
				queue.put(beats + repeat, task);
			});
		});

		if (topBeat.notNil, {
			var next = max(this.beats2secs(topBeat) - secs, 0.05);
			^next;
		}, {
			^nil;
		});
	}

	prChangeClock { | applyAtTime |
		var serverApplyAtTime = SCLOrkClock.localToServerTime(applyAtTime);
		var serverTempoChangeTime = SCLOrkClock.localToServerTime(
			timeAtLastTempoChange);
		wire.sendMsg('/clockChange', name,
			serverApplyAtTime.high32Bits, serverApplyAtTime.low32Bits,
			tempo.high32Bits, tempo.low32Bits,
			beatsPerBar.high32Bits, beatsPerBar.low32Bits,
			baseBarBeat.high32Bits, baseBarBeat.low32Bits,
			beatAtLastTempoChange.high32Bits, beatAtLastTempoChange.low32Bits,
			serverTempoChangeTime.high32Bits, serverTempoChangeTime.low32Bits);
	}

	clear {
		queue.clear;
	}

	cmdPeriod {
		if (permanent.not, {
			queue.clear;
		}, {
			this.prScheduleTop;
		});
	}

	tempo_ { | newTempo |
		if (tempo != newTempo, {
			var nextBeat = this.beats.roundUp;
			timeAtLastTempoChange = this.beats2secs(nextBeat);
			beatAtLastTempoChange = nextBeat;
			tempo = newTempo;
			this.prChangeClock(timeAtLastTempoChange);
		});
	}

	beats {
		^this.secs2beats(thisThread.seconds);
	}

	schedAbs { | beats, item |
		queue.put(beats, item);
		this.prScheduleTop;
	}

	sched { | delta, item |
		this.schedAbs(this.beats + delta, item);
	}

	play { | task, quant = 1 |
		this.schedAbs(quant.nextTimeOnGrid(this), task);
	}

	playNextBar { | task |
		this.schedAbs(this.nextBar, task);
	}

	beatDur {
		^(1.0 / tempo);
	}

	beatsPerBar_ { | newBeatsPerBar |
		if (beatsPerBar != newBeatsPerBar, {
			baseBarBeat = this.beats;
			baseBar = this.bar;
			beatsPerBar = newBeatsPerBar;
			this.prChangeClock(timeAtLastTempoChange);
		});
	}

	bar {
		^(baseBar + ((this.beats - baseBarBeat) / beatsPerBar)).trunc;
	}

	nextBar { | beat |
	}

	beatInBar {
	}

	beats2bars { | beats |
	}

	bars2beats { | bars |
	}

	timeToNextBeat { | qunat = 1.0 |
	}

	nextTimeOnGrid { | quant = 1.0, phase = 0 |
		if (quant == 0.0, { ^(this.beats + phase); });
		if (quant < 0.0, { quant = beatsPerBar * quant.neq });
		if (phase < 0.0, { phase = phase % quant });
		^roundUp(this.beats - baseBarBeat - (phase % quant), quant) + baseBarBeat + phase;
	}

	elapsedBeats {
	}

	seconds {
		^thisThread.seconds;
	}

	beats2secs { | beats |
		^(timeAtLastTempoChange + ((beats - beatAtLastTempoChange) / tempo));
	}

	secs2beats { | secs |
		^(beatAtLastTempoChange + (tempo * (secs - timeAtLastTempoChange)));
	}

	setMeterAtBeat { | newBeatsPerBar, beats |
	}

	setTempoAtBeat { | newTempo, beats |
	}

	setTempoAtSec { | newTempo, secs |
	}
}
