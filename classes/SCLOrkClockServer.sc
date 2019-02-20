SCLOrkClockServer {
	const <syncPort = 4250;
	const <knockPort = 4251;

	classvar instance;

	var clockSyncOSCFunc;
	var wireSerial;
	var wireMap;

	// Need a slick way of soring currentState, PriorityQueue of upcoming states for each clock cohort.
	// Every cohort needs a valid currentState. Next state queue could be empy if there's nothing.
	// When requesting a cohort state, server can "groom" the next state list, meaning that it will
	// use each currentState to determine if the next state has already been applied, then pop it off
	// and replace current state with it if it has, etc etc. Grooming only has to happen when server
	// is requested to report state, because otherwise the state is stored at low cost to the server
	// so doesn't need lots of care. But... should be protected from threading issues with a mutex
	// or something.

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
		wireMap = IdentityDictionary.new;
		cohortStateMap = IdentityDictionary.new;

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
						// groom and then send every item in every queue for every cohort.

					},
					'/clockRegister', {
						// client should report server time, initial tempo, etc.
						// server will either create new priorityqueue with states or look up existing *** applyAtTime needs to be set here
						// either way queue will be groomed and sent to client.
					},
					'/clockChange', {  // report a change scheduled by one of the cohort
						var newState = SCLOrkClockState.newFromMessage(msg);
						// This clock should already be registered.
						var stateQueue = cohortStateMap.at(newState.cohortName);
						if (stateQueue.notNil, {
							// Quickly report this to all clients.
							msg[0] = '/clockUpdate';
							this.prSendAll(msg);

							// Update our state queue for this cohort. - actually there has to be a pair - active state, then any scheduled
							// future states.
							stateQueue.put(newState.applyAtBeat, newState);
						}, {
							"*** clock change requested for unknown cohort named %".format(
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
			var returnPort = msg[1];
			var netAddr = NetAddr.new(addr.ip, returnPort);
			netAddr.sendMsg('/clockSyncSet',time.high32Bits, time.low32Bits);
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

	prGroomQueue { | stateQueue |
		// -- move through states in sorted order by applyAtBeat
	}
}