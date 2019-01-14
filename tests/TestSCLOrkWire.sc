TestSCLOrkWire : UnitTest {

	test_knock_normal_operation {
		var boundWireId = 400;
		var boundWires = Array.new(5);
		var knockWireId = 500;
		var knockWires = Array.new(5);
		var knockPort = 7500;
		var retries = 0;
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { false });
		};

		SCLOrkWire.bind(8000,
			{
				var wireId = boundWireId;
				boundWireId = boundWireId + 1;
				wireId;
			}, { }, { },
			{ | wire |
				boundWires = boundWires.add(wire);
			},
			factory);

		5.do({ |index |
			var wire = SCLOrkWire.new(knockPort, knockWireId,
				netAddrFactory: factory);
			knockPort = knockPort + 1;
			knockWireId = knockWireId + 1;
			wire.knock("127.0.0.1", 8000);
			knockWires = knockWires.add(wire);
		});

		// Wait for bound wires to be created.
		while ({retries < 10 and: { boundWires.size < 5 }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);

		// Wait for each pair to become idle, then validate connection.
		5.do({ | index |
			this.prWaitForIdle(knockWires[index], boundWires[index]);

			this.assertEquals(knockWires[index].selfId, 400 + index);
			this.assertEquals(knockWires[index].connectionState, \connected);

			this.assertEquals(boundWires[index].selfId, 500 + index);
			this.assertEquals(boundWires[index].connectionState, \connected);
		});

		SCLOrkWire.unbind(8000);
	}

	test_knock_connect_disconnect_cycle {
		var knockWire;
		var retries = 0;
		var issueSerial = 10;
		var boundWires = Array.new;
		var boundConnectCalls = 0;
		var boundMessageCalls = 0;
		var knockCalls = 0;
		var knockConnectCalls = 0;
		var knockMessageCalls = 0;
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { false });
		};

		SCLOrkWire.bind(7000,
			{
				var serial = issueSerial;
				issueSerial = issueSerial + 1;
				serial;
			},
			{ | wire, state |
				if (state == \connected, {
					wire.sendMsg('yo');
					boundConnectCalls = boundConnectCalls + 1;
				}, {
					this.assert(state != \failureTimeout);
				});
			},
			{ | wire, message |
				boundMessageCalls = boundMessageCalls + 1;
			},
			{ | wire |
				boundWires = boundWires.add(wire);
				knockCalls = knockCalls + 1;
			},
			factory);

		knockWire = SCLOrkWire.new(7050, 100, netAddrFactory: factory);
		knockWire.onConnected = { | wire, state |
			if (state == \connected, {
				wire.sendMsg('yo');
				knockConnectCalls = knockConnectCalls + 1;
			}, {
				this.assert(state != \failureTimeout);
			});
		};
		knockWire.onMessageReceived = { | wire, message |
			this.assertEquals(knockWire.id, wire.id);
			knockMessageCalls = knockMessageCalls + 1;
		};

		6.do({ | i |
			knockWire.knock("127.0.0.1", 7000);

			// Wait for bound wire to be created.
			retries = 0;
			while ({ retries < 10
				and: ((boundWires.size < (i + 1))
					or: { boundWires[i].connectionState != \connected })}, {
				0.2.wait;
				retries = retries + 1;
			});

			this.assert(retries < 10);
			this.assertEquals(boundWires.size, i + 1);
			this.assertEquals(knockWire.selfId, i + 10);
			this.assertEquals(boundConnectCalls, i + 1);
			this.assertEquals(boundMessageCalls, i + 1);
			this.assertEquals(knockConnectCalls, i + 1);
			this.assertEquals(knockMessageCalls, i + 1);

			if ((i % 2) == 0, {
				// Disconnect from bind wire side.
				boundWires[i].disconnect;
			}, {
				// Disconnect from knock wire side.
				knockWire.disconnect;
			});

			// Wait for both parties to be disconnected.
			retries = 0;
			while ({ retries < 10
				and: { knockWire.connectionState != \disconnected }
				and: { boundWires[i].connectionState != \disconnected }}, {
				0.2.wait;
				retries = retries + 1;
			});

			this.assert(retries < 10);
			this.assertEquals(knockWire.connectionState, \disconnected);
			this.assertEquals(boundWires[i].connectionState, \disconnected);
		});

		SCLOrkWire.unbind(7000);
	}

	test_knock_drop_first_knock {
		var knockWire;
		var retries = 0;
		var droppedFirst = false;
		var boundWires = Array.new(5);
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				if (args[0] == '/wireKnock' and: {
					droppedFirst.not  }, {
					droppedFirst = true;
					true;
				}, {
					false;
				});
			});
		};

		SCLOrkWire.bind(8050, { 10 }, { }, { }, { | wire |
				boundWires = boundWires.add(wire);
		}, factory);

		knockWire = SCLOrkWire.new(7550, 100, netAddrFactory: factory);
		knockWire.knock("127.0.0.1", 8050);
		// Wait for bound wire to be created.
		while ({retries < 10 and: { boundWires.size < 1 }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.prWaitForIdle(knockWire, boundWires[0]);
		this.assertEquals(knockWire.selfId, 10);
		this.assertEquals(knockWire.connectionState, \connected);
		this.assertEquals(boundWires[0].selfId, 100);
		this.assertEquals(boundWires[0].connectionState, \connected);

		SCLOrkWire.unbind(8050);
	}

	test_knock_drop_all_knocks {
		var timedOut = false;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireKnock'
			});
		};

		var wire = SCLOrkWire.new(7050, 3, netAddrFactory: factory);
		var retries = 0;

		wire.onConnected = { | w, state |
			this.assertEquals(w.id, 3);
			timedOut = (state == \failureTimeout);
		};
		wire.knock("127.0.0.1", 8050);

		// Wait for knock to timeout.
		while ({retries < 10 and: { timedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assertEquals(wire.connectionState, \failureTimeout);
	}

	test_knock_drop_all_connection_callbacks {
		var knockTimedOut = false;
		var bindTimedOut = false;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireConnectRequest'
			});
		};
		var boundWires = Array.new(5);
		var wire = SCLOrkWire.new(7551, netAddrFactory: factory);
		var retries = 0;

		SCLOrkWire.bind(8100, { 25 }, { | w, state |
			this.assertEquals(w.id, 25);
			bindTimedOut = (state == \failureTimeout)
		}, { }, { | w |
			boundWires = boundWires.add(w)
		}, factory);

		wire.onConnected = { | w, state |
			knockTimedOut = (state == \failureTimeout)
		};
		wire.knock("127.0.0.1", 8100);

		// Wait for knock to timeout.
		while ({retries < 10 and: { knockTimedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assertEquals(wire.connectionState, \failureTimeout);
		this.assertEquals(boundWires[0].connectionState, \failureTimeout);
	}

	test_connect_normal_operation {
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { false });
		};
		var wireA = SCLOrkWire.new(receivePort: 7666, id: 27,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7667, id: 26,
			netAddrFactory: factory);
		var wireAStates = Array.new(2);
		var wireBStates = Array.new(2);

		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 27);
			wireAStates = wireAStates.add(state);
		};
		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 26);
			wireBStates = wireBStates.add(state);
		};

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
		var wireA = SCLOrkWire.new(receivePort: 7668, id: 7669,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7669, id: 7668,
			netAddrFactory: factory);
		var wireAStates = Array.new(2);
		var wireBStates = Array.new(2);

		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 7669);
			wireAStates = wireAStates.add(state);
		};
		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 7668);
			wireBStates = wireBStates.add(state);
		};

		wireA.connect("127.0.0.1", 7669);

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
		var wireA = SCLOrkWire.new(receivePort: 7670, id: 0,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7671, id: 0,
			netAddrFactory: factory);
		var wireAStates = Array.new(2);
		var wireBStates = Array.new(2);

		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 0);
			wireAStates = wireAStates.add(state);
		};
		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 0);
			wireBStates = wireBStates.add(state);
		};

		wireA.connect("127.0.0.1", 7671);

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
		var wireA = SCLOrkWire.new(receivePort: 7672, id: 102,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7673, id: 107,
			netAddrFactory: factory);
		var wireAStates = Array.new(2);
		var wireBStates = Array.new(2);

		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 102);
			wireAStates = wireAStates.add(state);
		};
		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 107);
			wireBStates = wireBStates.add(state);
		};

		wireA.connect("127.0.0.1", 7673);

		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(wireAStates, [ \connectionRequested, \connected ]);
		this.assertEquals(wireBStates, [ \connectionAccepted, \connected ]);
	}

	test_connect_drop_all_requests {
		var timedOut = false;
		var retries = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireConnectRequest'
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7674, id: 7,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7675, id: 8,
			netAddrFactory: factory);

		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 7);
			timedOut = (state == \failureTimeout);
		};

		wireA.connect("127.0.0.1", 7675);
		// Busywait for timeout.
		while ({retries < 10 and: { timedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assert(timedOut);
		this.assertEquals(wireA.connectionState, \failureTimeout);
		this.assertEquals(wireB.connectionState, \neverConnected);
	}

	test_connect_drop_all_accepts {
		var aTimedOut = false;
		var bTimedOut = false;
		var retries = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireConnectAccept'
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7676, id: 45,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7677, id: 44,
			netAddrFactory: factory);

		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 45);
			aTimedOut = (state == \failureTimeout)
		};
		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 44);
			bTimedOut = (state == \failureTimeout)
		};

		wireA.connect("127.0.0.1", 7677);

		// Busywait for timeout.
		while ({retries < 10 and: {
			aTimedOut.not } and: {
			bTimedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assert(aTimedOut);
		this.assert(bTimedOut);
		this.assertEquals(wireA.connectionState, \failureTimeout);
		this.assertEquals(wireB.connectionState, \failureTimeout);
	}

	test_connect_drop_all_confirms {
		var timedOut = false;
		var retries = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireConnectConfirm'
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7678, id: 45,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7679, id: 62,
			netAddrFactory: factory);

		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 62);
			timedOut = (state == \failureTimeout)
		};

		wireA.connect("127.0.0.1", 7679);
		// Busywait for timeout.
		while ({retries < 10 and: { timedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assert(timedOut);
		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \failureTimeout);
	}

	test_send_normal_operation {
		var aReceivedCount = 0;
		var bReceivedCount = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { false });
		};
		var wireA = SCLOrkWire.new(receivePort: 7680, id: 2,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7681, id: 1,
			netAddrFactory: factory);

		wireA.onMessageReceived = { | w, msg |
			if (w.id == 2
				and: { msg.size == 1 }
				and: { msg[0] == 'testA' }, {
				aReceivedCount = aReceivedCount + 1;
			}, {
				this.assert(false);
			});
		};
		wireB.onMessageReceived = { | w, msg |
			if (w.id == 1
				and: { msg.size == 1 }
				and: { msg[0] == 'testB' }, {
				bReceivedCount = bReceivedCount + 1;
			}, {
				this.assert(false);
			});
		};

		wireA.connect("127.0.0.1", 7681);
		this.prWaitForIdle(wireA, wireB);

		wireB.sendMsg('testA');
		wireA.sendMsg('testB');
		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(aReceivedCount, 1);
		this.assertEquals(bReceivedCount, 1);
	}

	test_send_drop_first_send {
		var bReceivedCount = 0;
		var droppedFirst = false;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				if (args[0] == '/wireSend' and: {
					droppedFirst.not }, {
					droppedFirst = true;
					true;
				}, {
					false;
				});
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7682, id: 3,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7683, id: 2,
			netAddrFactory: factory);

		wireB.onMessageReceived = { | w, msg |
			if (w.id == 2
				and: { msg.size == 1 }
				and: { msg[0] == 'testB' }, {
				bReceivedCount = bReceivedCount + 1;
				"bReceived: %".format(bReceivedCount).postln;
			}, {
				this.assert(false);
			});
		};

		wireA.connect("127.0.0.1", 7683);
		this.prWaitForIdle(wireA, wireB);

		wireA.sendMsg('testB');
		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(bReceivedCount, 1);
	}

	test_send_drop_first_ack {
		var bReceivedCount = 0;
		var droppedFirst = false;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				if (args[0] == '/wireAck' and: {
					droppedFirst.not }, {
					droppedFirst = true;
					true;
				}, {
					false;
				});
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7684, id: 0,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7685, id: 5,
			netAddrFactory: factory);

		wireB.onMessageReceived = { | w, msg |
			if (w.id == 5
				and: { msg.size == 1 }
				and: { msg[0] == 'testB' }, {
				bReceivedCount = bReceivedCount + 1;
				"bReceived: %".format(bReceivedCount).postln;
			}, {
				this.assert(false);
			});
		};

		wireA.connect("127.0.0.1", 7685);
		this.prWaitForIdle(wireA, wireB);

		wireA.sendMsg('testB');
		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(bReceivedCount, 1);
	}

	test_send_drop_all_sends {
		var timedOut = false;
		var retries = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireSend'
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7686, id: 66,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7687, id: 23,
			netAddrFactory: factory);

		wireB.onMessageReceived = { | w, msg |
			this.assert(false, "dropping all sends, so wireB should not receive data");
		};
		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 66);
			timedOut = (state == \failureTimeout)
		};

		wireA.connect("127.0.0.1", 7687);
		this.prWaitForIdle(wireA, wireB);

		wireA.sendMsg('testB');
		// Busy-wait for timeout on A as it never gets an ACK back.
		while ({retries < 10 and: { timedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assert(timedOut);
		this.assertEquals(wireA.connectionState, \failureTimeout);
		this.assertEquals(wireB.connectionState, \connected);
	}

	test_send_drop_all_acks {
		var timedOut = false;
		var retries = 0;
		var bReceivedCount = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireAck'
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7688, id: 45,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7689, id: 7689,
			netAddrFactory: factory);

		wireB.onMessageReceived = { | w, msg |
			if (w.id == 7689
				and: { msg.size == 1 }
				and: { msg[0] == 'testB' }, {
				bReceivedCount = bReceivedCount + 1;
				"bReceived: %".format(bReceivedCount).postln;
			}, {
				this.assert(false);
			});
		};
		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 45);
			timedOut = (state == \failureTimeout);
		};

		wireA.connect("127.0.0.1", 7689);
		this.prWaitForIdle(wireA, wireB);

		wireA.sendMsg('testB');
		// Busy-wait for timeout on A as it never gets an ACK back.
		while ({retries < 10 and: { timedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assert(timedOut);
		this.assertEquals(wireA.connectionState, \failureTimeout);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(bReceivedCount, 1);
	}

	test_send_out_of_order_single {
		var secondSent = false;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				var shouldDrop = false;
				if (args[0] == '/wireSend', {
					if (args[3] == 'sendSecond', {
						secondSent = true;
					}, {
						if (args[3] == 'sendFirst' and: {
							secondSent.not }, {
							shouldDrop = true;
						});
					});
				});
				shouldDrop;
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7690, id: 7,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7691, id: 16,
			netAddrFactory: factory);

		var bMessages = Array.new(2);

		wireB.onMessageReceived = { | w, msg |
			if (w.id == 16, {
				bMessages = bMessages.add(msg[0]);
			}, {
				this.assert(false);
			});
		};

		wireA.connect("127.0.0.1", 7691);
		this.prWaitForIdle(wireA, wireB);

		wireA.sendMsg('sendFirst');
		wireA.sendMsg('sendSecond');
		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		// Validate in-order arrival.
		this.assertEquals(bMessages, ['sendFirst', 'sendSecond']);
	}

	test_send_out_of_order_multiple {
		var fifthSent = false;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				var shouldDrop = false;
				if (args[0] == '/wireSend', {
					if (args[3] == 'aFifth', {
						fifthSent = true;
					}, {
						if (args[3] == 'aSecond' and: {
							fifthSent.not }, {
							shouldDrop = true;
						});
					});
				});
				shouldDrop;
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7692, id: 17,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7693, id: 41,
			netAddrFactory: factory);

		var aMessages = Array.new(10);
		var bMessages = Array.new(10);
		wireA.onMessageReceived = { | w, msg |
			if (w.id == 17, {
				aMessages = aMessages.add(msg[0]);
			}, {
				this.assert(false);
			});
		};
		wireB.onMessageReceived = { | w, msg |
			if (w.id == 41, {
				bMessages = bMessages.add(msg[0]);
			}, {
				this.assert(false);
			});
		};

		wireA.connect("127.0.0.1", 7693);
		this.prWaitForIdle(wireA, wireB);

		wireA.sendMsg('aFirst');
		wireA.sendMsg('aSecond');
		wireB.sendMsg('bFirst');
		wireA.sendMsg('aThird');
		wireB.sendMsg('bSecond');
		wireA.sendMsg('aFourth');
		wireA.sendMsg('aFifth');
		wireB.sendMsg('bThird');
		wireA.sendMsg('aSixth');
		wireB.sendMsg('bFourth');

		this.prWaitForIdle(wireA, wireB);

		this.assertEquals(wireA.connectionState, \connected);
		this.assertEquals(wireB.connectionState, \connected);
		// Validate in-order arrival.
		this.assertEquals(aMessages, ['bFirst', 'bSecond', 'bThird', 'bFourth']);
		this.assertEquals(bMessages, [
			'aFirst', 'aSecond', 'aThird', 'aFourth', 'aFifth', 'aSixth'
		]);
	}

	test_send_failure_with_packets_ahead_queued {
		var timedOut = false;
		var retries = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				^(args[0] == '/wireSend'
					and: { args[3] == 'aSecond' })
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7694, id: 117,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7695, id: 576,
			netAddrFactory: factory);

		var bMessages = [];
		wireB.onMessageReceived = { | w, msg |
			if (w.id == 576, {
				bMessages = bMessages.add(msg[0]);
			}, {
				this.assert(false);
			});
		};
		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 117);
			timedOut = (state == \failureTimeout);
		};

		wireA.connect("127.0.0.1", 7695);
		this.prWaitForIdle(wireA, wireB);

		wireA.sendMsg('aFirst');
		wireA.sendMsg('aSecond');
		wireA.sendMsg('aThird');
		wireA.sendMsg('aFourth');
		wireA.sendMsg('aFifth');
		wireA.sendMsg('aSixth');

		// Busy-wait for timeout on A as it never gets an ACK back for second packet.
		while ({retries < 10 and: { timedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assert(timedOut);
		this.assertEquals(wireA.connectionState, \failureTimeout);
		this.assertEquals(wireB.connectionState, \connected);
		this.assertEquals(bMessages, [ 'aFirst' ]);
	}

	test_disconnect_normal_operation {
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { false });
		};
		var wireA = SCLOrkWire.new(receivePort: 7696, id: 23,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7697, id: 0,
			netAddrFactory: factory);
		var aWasConnected = false;
		var bWasConnected = false;
		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 23);
			if (state == \connected, { aWasConnected = true });
		};
		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 0);
			if (state == \connected, { bWasConnected = true });
		};

		wireA.connect("127.0.0.1", 7697);
		this.prWaitForIdle(wireA, wireB);

		wireB.disconnect;
		this.prWaitForIdle(wireA, wireB);

		this.assert(aWasConnected);
		this.assert(bWasConnected);
		this.assertEquals(wireA.connectionState, \disconnected);
		this.assertEquals(wireB.connectionState, \disconnected);
	}

	test_disconnect_drop_disconnect_request_once {
		var droppedFirst = false;
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				if (args[0] == '/wireDisconnect' and: {
					droppedFirst.not }, {
					droppedFirst = true;
					true;
			}, {
					false;
				});
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7698, id: 123,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7699, id: 124,
			netAddrFactory: factory);
		var aWasConnected = false;
		var bWasConnected = false;
		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 123);
			if (state == \connected, { aWasConnected = true });
		};
		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 124);
			if (state == \connected, { bWasConnected = true });
		};

		wireA.connect("127.0.0.1", 7699);
		this.prWaitForIdle(wireA, wireB);

		wireB.disconnect;
		this.prWaitForIdle(wireA, wireB);

		this.assert(aWasConnected);
		this.assert(bWasConnected);
		this.assertEquals(wireA.connectionState, \disconnected);
		this.assertEquals(wireB.connectionState, \disconnected);
	}

	test_disconnect_drop_disconnect_confirm_once {
		var droppedFirst = false;
		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				if (args[0] == '/wireDisconnectConfirm' and: {
					droppedFirst.not }, {
					droppedFirst = true;
					true;
			}, {
					false;
				});
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7700, id: 27,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7701, id: 23,
			netAddrFactory: factory);
		var aWasConnected = false;
		var bWasConnected = false;
		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 27);
			if (state == \connected, { aWasConnected = true });
		};
		wireB.onConnected = { | w, state |
			this.assertEquals(w.id, 23);
			if (state == \connected, { bWasConnected = true });
		};

		wireA.connect("127.0.0.1", 7701);
		this.prWaitForIdle(wireA, wireB);

		wireA.disconnect;
		this.prWaitForIdle(wireA, wireB);

		this.assert(aWasConnected);
		this.assert(bWasConnected);
		this.assertEquals(wireA.connectionState, \disconnected);
		this.assertEquals(wireB.connectionState, \disconnected);
	}

	test_disconnect_drop_disconnect_request_all {
		var timedOut = false;
		var retries = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireDisconnect'
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7702, id: 16,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7703, id: 17,
			netAddrFactory: factory);

		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 16);
			timedOut = (state == \failureTimeout);
		};

		wireA.connect("127.0.0.1", 7703);
		this.prWaitForIdle(wireA, wireB);

		wireA.disconnect;
		// Busywait for timeout.
		while ({retries < 10 and: { timedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assert(timedOut);
		this.assertEquals(wireA.connectionState, \failureTimeout);
		this.assertEquals(wireB.connectionState, \connected);
	}

	test_disconnect_drop_disconnect_confirm_all {
		var timedOut = false;
		var retries = 0;

		var factory = { | hostname, port |
			SCLOrkFlakyLocalNetAddr.new(port, { | count, args |
				args[0] == '/wireDisconnectConfirm'
			});
		};
		var wireA = SCLOrkWire.new(receivePort: 7704, id: 14,
			netAddrFactory: factory);
		var wireB = SCLOrkWire.new(receivePort: 7705, id: 12,
			netAddrFactory: factory);

		wireA.onConnected = { | w, state |
			this.assertEquals(w.id, 14);
			timedOut = (state == \failureTimeout);
		};

		wireA.connect("127.0.0.1", 7705);
		this.prWaitForIdle(wireA, wireB);

		wireA.disconnect;
		// Busywait for timeout.
		while ({retries < 10 and: { timedOut.not }}, {
			0.2.wait;
			retries = retries + 1;
		});

		this.assert(retries < 10);
		this.assert(timedOut);
		this.assertEquals(wireA.connectionState, \failureTimeout);
		this.assertEquals(wireB.connectionState, \disconnected);
	}

	prWaitForIdle { | wireA, wireB, maxRetries = 10 |
		var retries = 0;
		// Busyloop until both wires are idle or we give up.
		while ({
			(retries < maxRetries).and(
				wireA.isIdle.not.or(
					wireB.isIdle.not))}, {
			retries = retries + 1;
			0.2.wait;
		});
		this.assert(retries < maxRetries,
			"Too many retries waiting for wires to become idle!", false
		);
	}
}

TestSCLOrkWireSendRetry : UnitTest {
	test_success_before_timeout {
		var failCalled = false;
		var flake = SCLOrkFlakyLocalNetAddr.new(7666, { true });
		var sendRetry = SCLOrkWireSendRetry(flake,
			[ '/notGoingAnywhere' ],
			{ flake.sendCount < 2 },
			{ failCalled = true },
			5,
			0.1);
		this.prWaitForIdle(sendRetry);
		this.assert(failCalled.not);
	}

	// TODO: figure out a way to test command-period survival?

	test_timeout_failure {
		var failCalled = false;
		var flake = SCLOrkFlakyLocalNetAddr.new(7666, { true });
		var sendRetry = SCLOrkWireSendRetry(flake,
			[ '/notGoingAnywhere' ],
			{ true },
			{ failCalled = true; },
			3,
			0.1);
		this.prWaitForIdle(sendRetry);
		this.assert(failCalled);
	}

	prWaitForIdle { | sendRetry, maxRetries = 10 |
		var retries = 0;
		while ({ retries < maxRetries and: { sendRetry.isIdle.not }}, {
			retries = retries + 1;
			0.1.wait;
		});
		this.assert(retries < maxRetries,
			"Too many retries wating for sendRetry to become idle!", false
		);
	}
}

// A localhost-only wrapper around a NetAddr that calls a function before
// sending any data, allowing testing code to predictably drop packets.
SCLOrkFlakyLocalNetAddr {
	classvar <>verbose = false;

	var port;
	var shouldDrop;

	var netAddr;
	var <sendCount;

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
			if (verbose, {
				"*** DROPPED: %".format(args).postln;
			});
		});
		sendCount = sendCount + 1;
	}
}
