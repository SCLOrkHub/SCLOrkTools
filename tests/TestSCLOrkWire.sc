
// A localhost-only wrapper around a NetAddr that calls a function before
// sending any data, allowing testing code to predictably drop packets.
SCLOrkFlakyLocalNetAddr {
	var port;
	var shouldDrop;

	var netAddr;
	var sendCount;

	*new { | port, shouldDrop |
		^super.newCopyArgs(port, shouldDrop).init;
	}

	init {
		netAddr = NetAddr.new("127.0.0.1", port);
		sendCount = 0;
	}

	sendMsg { | ... args |
		if (shouldDrop.value(sendCount, args).not, {
			netAddr.sendMsg(*args);
		}, {
			"** DROPPED: %".format(args).postln;
		});
		sendCount = sendCount + 1;
	}
}

TestSCLOrkWire : UnitTest {
	test_connect_normal_operation {
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { false });
		};
		var wireA = SCLOrkWire.new(netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7667, netAddrFactory: factory);
		var wireAStates = Array.new(2);
		var wireBStates = Array.new(2);

		wireA.onConnected = { | state | wireAStates = wireAStates.add(state); };
		wireB.onConnected = { | state | wireBStates = wireBStates.add(state); };

		wireA.connect("127.0.0.1", 7667);

		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(wireAStates, [ \connectionRequested, \connected ]);
		this.assertEquals(wireBStates, [ \connectionAccepted, \connected ]);
	}

	test_connect_drop_first_request {
		var droppedFirst = false;
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				if (args[0] == '/wireConnectRequest' and: {
					droppedFirst.not  }, {
					droppedFirst = true;
					true;
				}, {
					false;
				});
			});
		};
		var wireA = SCLOrkWire.new(netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7667, netAddrFactory: factory);
		var wireAStates = Array.new(2);
		var wireBStates = Array.new(2);

		wireA.onConnected = { | state | wireAStates = wireAStates.add(state); };
		wireB.onConnected = { | state | wireBStates = wireBStates.add(state); };

		wireA.connect("127.0.0.1", 7667);

		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(wireAStates, [ \connectionRequested, \connected ]);
		this.assertEquals(wireBStates, [ \connectionAccepted, \connected ]);
	}

	test_connect_drop_first_accept {
		var droppedFirst = false;
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				if (args[0] == '/wireConnectAccept' and: {
					droppedFirst.not  }, {
					droppedFirst = true;
					true;
				}, {
					false;
				});
			});
		};
		var wireA = SCLOrkWire.new(netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7667, netAddrFactory: factory);
		var wireAStates = Array.new(2);
		var wireBStates = Array.new(2);

		wireA.onConnected = { | state | wireAStates = wireAStates.add(state); };
		wireB.onConnected = { | state | wireBStates = wireBStates.add(state); };

		wireA.connect("127.0.0.1", 7667);

		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(wireAStates, [ \connectionRequested, \connected ]);
		this.assertEquals(wireBStates, [ \connectionAccepted, \connected ]);
	}

	test_connect_drop_first_confirm {
		var droppedFirst = false;
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				if (args[0] == '/wireConnectConfirm' and: {
					droppedFirst.not  }, {
					droppedFirst = true;
					true;
				}, {
					false;
				});
			});
		};
		var wireA = SCLOrkWire.new(netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7667, netAddrFactory: factory);
		var wireAStates = Array.new(2);
		var wireBStates = Array.new(2);

		wireA.onConnected = { | state | wireAStates = wireAStates.add(state); };
		wireB.onConnected = { | state | wireBStates = wireBStates.add(state); };

		wireA.connect("127.0.0.1", 7667);

		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(wireAStates, [ \connectionRequested, \connected ]);
		this.assertEquals(wireBStates, [ \connectionAccepted, \connected ]);
	}



	prWaitForIdle { | wireA, wireB, maxRetries = 10 |
		var retries = 0;
		// Busyloop until both wires are idle or we give up.
		while ({
			retries < maxRetries and: {
			wireA.isIdle.not } and: { wireB.isIdle.not }}, {
			retries = retries + 1;
			0.1.wait;
		});
		this.assert(retries < maxRetries,
			"Too many retries waiting for wires to become idle!", false
		);
	}
}