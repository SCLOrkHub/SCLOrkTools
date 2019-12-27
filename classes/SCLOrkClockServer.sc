SCLOrkClockServer {
	const <syncPort = 4250;
	const <knockPort = 4251;

	classvar instance;

	var clockSyncOSCFunc;
	var wireSerial;
	var wireMap;

	var cohortStateMap;

	*new {
		if (instance.isNil, {
			instance = super.new.init;
		});
		^instance;
	}

	init {
		this.prBindClockSync;
		wireSerial = 0;
		wireMap = Dictionary.new;
		cohortStateMap = Dictionary.new;

		SCLOrkWire.bind(
			port: knockPort,
			wireIssueId: {
				wireSerial = wireSerial + 1;
				wireSerial;
			},
			wireOnConnected: { | wire, status |
				switch (status,
					\connected, {
						wireMap.put(wire.id, wire);
					},
					\failureTimeout, {
						wireMap.remove(wire.id);
					},
					\disconnected, {
						wireMap.remove(wire.id);
					}
				);
			},
			wireOnMessageReceived: { | wire, msg |
				switch (msg[0],
					'/clockGetAll', {
						cohortStateMap.values.do({ | item, index |
							this.prGroomState(item);
							this.prSendState(item, wire);
						});
					},
					'/clockCreate', {
						var cohortName = msg[1];
						var cohortState = cohortStateMap.at(cohortName);
						if (cohortState.isNil, {
							var initialState = SCLOrkClockState.newFromMessage(msg);
							cohortState = ();
							cohortState.put(\current, initialState);
							cohortState.put(\stateQueue, PriorityQueue.new);
							cohortStateMap.put(cohortName, cohortState);
							// Re-use message to notify all clients about this new clock.
							msg[0] = '/clockUpdate';
							this.prSendAll(msg);
						}, {
							// Clock already exists, ignore provided state,
							// groom existing state, then send it.
							this.prGroomState(cohortState);
							this.prSendState(cohortState, wire);
						});
					},
					'/clockChange', {
						var newState = SCLOrkClockState.newFromMessage(msg);
						// This clock should already be registered.
						var cohortState = cohortStateMap.at(newState.cohortName);
						if (cohortState.notNil, {
							// Quickly report this to all clients.
							msg[0] = '/clockUpdate';
							this.prSendAll(msg);

							if (newState.applyAtBeat <=
								cohortState.at(\current).applyAtBeat, {
									cohortState.put(\current, newState);
								}, {
									cohortState.at(\stateQueue).put(
										newState.applyAtBeat, newState);
							});

							this.prGroomState(cohortState);
						}, {
							"*** clock change requested for unknown cohort %".format(
								newState.name).postln;
						});
					}
				);
			},
			onKnock: { | wire |
			}
		);
	}

	prBindClockSync {
		clockSyncOSCFunc = OSCFunc.new({ | msg, time, addr |
			var mainTime = Main.elapsedTime;
			var returnPort = msg[1];
			var netAddr = NetAddr.new(addr.ip, returnPort);
			netAddr.sendMsg('/clockSyncSet', mainTime.high32Bits, mainTime.low32Bits);
		},
		path: '/clockSyncGet',
		recvPort: syncPort
		).permanent_(true);
	}

	prSendAll { | msg |
		wireMap.values.do({ | wire, index |
			wire.sendMsg(*msg);
		});
	}

	// Remove any elapsed state changes from scheduled state queue.
	prGroomState { | cohortState |
		var nextBeat;
		while ({
			nextBeat = cohortState.at(\stateQueue).topPriority;
			nextBeat.notNil and: {
				nextBeat <= cohortState.at(\current).secs2beats(
					Main.elapsedTime, 0.0); }}, {
			var currentState = cohortState.at(\stateQueue).pop;
			currentState.applyAtTime = cohortState.at(\current).beats2secs(
				nextBeat);
			cohortState.put(\current, currentState);
		});
	}

	prSendState { | cohortState, wire |
		var msg = cohortState.at(\current).toMessage;
		msg[0] = '/clockUpdate';
		wire.sendMsg(*msg);
		// Queue will be sent out-of-order but doesn't matter, as receiving
		// clocks will re-assemble correct order in their own queues.
		cohortState.at(\stateQueue).do({ | item, index |
			msg = item.toMessage;
			msg[0] = '/clockUpdate';
			wire.sendMsg(*msg);
		});
	}
}