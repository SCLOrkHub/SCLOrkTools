SCLOrkClockServer {
	const <syncPort = 4250;
	const <knockPort = 4251;

	classvar instance;

	var clockSyncOSCFunc;
	var wireSerial;
	var wireMap;
	var cohortMap;

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
		cohortMap = IdentityDictionary.new;

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

					},
					'/clockRegister', {

					},
					'/clockChange', {

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

	prStateToArray {
	}

	prArrayToState {
	}
}