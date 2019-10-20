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
	classvar <timeDiff;
	classvar changeQueue;
	classvar requestLastSent;
	classvar clockSyncOSCFunc;
	classvar syncTask;
	classvar wire;

	var <currentState;
	var stateQueue;

	var <isRunning;
	var <>permanent;

	var queue;

	*startSync { | serverName = "sclork-s01.local" |
		if (syncStarted.isNil, {
			syncStarted = true;
			clockMap = Dictionary.new;
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
				var currentTime = Main.elapsedTime;
				var diff = currentTime - serverTime;
				var roundTripTime = currentTime - requestLastSent;
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

			// SkipJack waits for timeout before executing first time, so
			// avoid situation where clocks created before first time sync
			// have times way off and skew to adjust.
			requestLastSent = Main.elapsedTime;
			syncNetAddr.sendMsg('/clockSyncGet', syncPort);

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
						"*** connected to clock server.".postln;
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
						var state = SCLOrkClockState.newFromMessage(msg);
						var clock = clockMap.at(state.cohortName);
						if (clock.isNil, {
							clock = super.newCopyArgs(state).init;
							clockMap.put(state.cohortName, clock);
						}, {
							clock.prUpdate(state);
						});
					},
				);
			};
			wire.knock(serverName, SCLOrkClockServer.knockPort);
		});
	}

	*serverToLocalTime { | serverTime |
		^(serverTime + timeDiff);
	}

	*localToServerTime { | localTime |
		^(localTime - timeDiff);
	}

	*new { |  // TODO: trivial to support adding initial values for beats and seconds.
		name = \default,
		tempo = 1.0,
		beatsPerBar = 4.0,
		serverName = "sclork-s01.local" |
		var clock;

		if (syncStarted.isNil or: { syncStarted.not }, {
			SCLOrkClock.startSync(serverName);
		});

		clock = clockMap.at(name);
		if (clock.isNil, {
			var msg;
			var state = SCLOrkClockState.new(
				cohortName: name,
				applyAtTime: SCLOrkClock.localToServerTime(Main.elapsedTime),
				tempo: tempo,
				beatsPerBar: beatsPerBar);

			clock = super.new.init;
			clock.prForceState(state);
			clockMap.put(name, clock);

			// Inform server of clock creation.
			msg = state.toMessage;
			msg[0] = '/clockCreate';
			wire.sendMsg(*msg);
		});
		^clock;
	}

	// Moved from instance method .stop because will stop all clocks in the cohort.
	*stopClock { | name |
		wire.sendMsg('/clockStop', name);
	}

	init {
		isRunning = true;
		queue = PriorityQueue.new;
		stateQueue = PriorityQueue.new;
		permanent = false;
		CmdPeriod.add(this);
	}

	free {
		queue.clear;
		CmdPeriod.remove(this);
	}

	prUpdate { | newState |
		// We ignore state changes for states calling for an earlier beat
		// than the current state's starting beat, because we can't
		// reliably compute times for states starting before our current
		// state.
		if (newState.applyAtBeat >= currentState.applyAtBeat, {
			// newState could be for a beat count that is earlier than our
			// current beat count. In that case we clobber the current state
			// with this one, which may require recomputation of scheduling, etc.
			// If newState is for a later beat count, it goes into the stateQueue,
			// and we schedule a task for later to promote it to the current state.
			if (newState.applyAtBeat <= this.beats, {
				newState.applyAtTime = currentState.beats2secs(newState.applyAtBeat);
				currentState = newState;
				// Change in state can mean change in timing of items in the
				// queue, re-schedule the next task.
				this.prScheduleTop;
			}, {
				stateQueue.put(newState.applyAtBeat, newState);
			});

			// If we have a new state that may impact timing of state change schedules,
			// so we reschedule. And if we added a new state it may be the top state
			// change in the queue, so also schedule that.
			this.prScheduleStateChange;
		}, {
			"*** clock % dropping new state at beat %, before current state beat %.".format(
				currentState.cohortName, newState.applyAtBeat, currentState.applyAtBeat
			).postln;
		});
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

	prScheduleStateChange {
		var nextBeat = stateQueue.topPriority;
		if (nextBeat.notNil, {
			var nextTime = this.beats2secs(nextBeat);
			SystemClock.schedAbs(nextTime, { this.prAdvanceState });
		});
	}

	prAdvance {
		var sec = Main.elapsedTime;
		var beat = this.beats;
		var topBeat;
		while ({
			topBeat = queue.topPriority;
			topBeat.notNil and: { topBeat <= beat }}, {
			var task = queue.pop;
			// Little bit of fudging going on here where we are sending
			// the scheduled beat count instead of the actual current
			// beat timing. Some beats can be a bit off due to clock drift
			// updates from the server.
			var repeat = task.awake(topBeat, sec, this);
			if (repeat.isNumber, {
				queue.put(topBeat + repeat, task);
			});
		});

		if (topBeat.notNil, {
			var next = max(this.beats2secs(topBeat) - sec, 0.05);
			^next;
		}, {
			^nil;
		});
	}

	// Coupla key differences - using the stateQueue, always
	// recomputing beats (because state has changed), no task
	// requeuing.
	prAdvanceState {
		var sec = Main.elapsedTime;
		var topBeat;
		while ({
			topBeat = stateQueue.topPriority;
			topBeat.notNil and: { topBeat <= this.beats }}, {
			var applyAtTime = currentState.beats2secs(topBeat);
			currentState = stateQueue.pop;
			currentState.applyAtTime = applyAtTime;

			// Tempo change could mean new timing for tasks, reschedule
			// task processing.
			this.prScheduleTop;
		});

		if (topBeat.notNil, {
			var next = max(this.beats2secs(topBeat) - sec, 0.05);
			^next;
		}, {
			^nil;
		});
	}

	prForceState { | state |
		currentState = state;
	}

	prSendChange { | state |
		var stateMsg = state.toMessage;
		stateMsg[0] = '/clockChange';
		wire.sendMsg(*stateMsg);
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

		// State changes must always happen regardless of if we clear the clock
		// task list or no.
		this.prScheduleStateChange;
	}

	name {
		^currentState.cohortName;
	}

	tempo {
		^currentState.tempo;
	}

	tempo_ { | newTempo |
		if (currentState.tempo != newTempo, {
			var nextBeat = this.beats.roundUp;
			this.setTempoAtBeat(newTempo, nextBeat);
		});
	}

	beats {
		^currentState.secs2beats(Main.elapsedTime, timeDiff);
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
		^currentState.beatDur;
	}

	beatsPerBar {
		^currentState.beatsPerBar;
	}

	beatsPerBar_ { | newBeatsPerBar |
	}

	bar {
		^currentState.beats2bars(this.beats);
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
		if (quant < 0.0, { quant = currentState.beatsPerBar * quant.neq });
		if (phase < 0.0, { phase = phase % quant });
		^roundUp(this.beats - currentState.baseBarBeat - (
			phase % quant), quant) + currentState.baseBarBeat + phase;
	}

	elapsedBeats {
	}

	seconds {
		^Main.elapsedTime;
	}

	beats2secs { | beats |
		^currentState.beats2secs(beats, timeDiff);
	}

	secs2beats { | secs |
		^currentState.secs2beats(secs, timeDiff);
	}

	setMeterAtBeat { | newBeatsPerBar, beats |
	}

	setTempoAtBeat { | newTempo, beats |
		var state = currentState.setTempoAtBeat(newTempo, beats);
		this.prSendChange(state);
	}
}
