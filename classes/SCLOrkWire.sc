SCLOrkWire {
	classvar bindSemaphore = nil;
	classvar portSerial = 9000;
	classvar portMap = nil;

	var receivePort;
	var targetId;
	var timeout;
	var maxRetries;
	var bufferSize;

	var netAddrFactory;

	var <selfId;
	var connectionRetry;

	var sendSemaphore;
	var sendSerial;
	var sendBuffer;

	var sendAckSerial;

	var receiveSemaphore;
	var receiveSerial;
	var receiveBuffer;

	// \neverConnected, \connected, \failureTimeout
	var <connectionState;
	var <>onConnected;
	var <>onMessageReceived;

	var netAddr;

	var connectRequestOSCFunc;
	var connectAcceptOSCFunc;
	var connectConfirmOSCFunc;
	var sendOSCFunc;
	var ackOSCFunc;
	var disconnectOSCFunc;
	var disconnectConfirmOSCFunc;

	*bind { |
		port = 7666,
		wireIssueId,
		wireOnConnected,
		wireOnMessageReceived,
		onKnock,
		netAddrFactory = nil |
		if (bindSemaphore.isNil, {
			bindSemaphore = Semaphore.new(1);
			portMap = Dictionary.new;
		});

		if (portMap.at(port).isNil, {
			// Check if port is already bound, early out if so.
			var knockOSCFunc = OSCFunc.new({ | msg, time, addr |
				var localPort, wireId, wire;
				var returnPort = msg[1];
				// Allocate a port on this side for the wire endpoint.
				bindSemaphore.wait;
				localPort = portSerial;
				portSerial = portSerial + 1;
				bindSemaphore.signal;

				wireId = wireIssueId.value;
				wire = SCLOrkWire.new(localPort, wireId,
					netAddrFactory: netAddrFactory);
				wire.onConnected = wireOnConnected;
				wire.onMessageReceived = wireOnMessageReceived;
				wire.connect(addr.ip, returnPort);
				onKnock.value(wire);
			},
			path: '/wireKnock',
			recvPort: port
			).permanent_(true);

			portMap.put(port, knockOSCFunc);
		});
	}

	*unbind { | port |
		var knockOSCFunc = portMap.at(port);
		if (knockOSCFunc.notNil, {
			portMap.removeAt(port);
			knockOSCFunc.free;
		});
	}

	*new { |
		receivePort = 7666,
		targetId = 0,
		timeout = 0.2,
		maxRetries = 5,
		bufferSize = 32,
		netAddrFactory = nil |
		^super.newCopyArgs(
			receivePort,
			targetId,
			timeout,
			maxRetries,
			bufferSize,
			netAddrFactory).init;
	}

	init {
		// Support injection of a mock object factory for testing.
		if (netAddrFactory.isNil, {
			netAddrFactory = { | hostname, port |
				NetAddr.new(hostname, port);
			}
		});
		sendSemaphore = Semaphore.new(1);
		sendSerial = 0;
		sendBuffer = Array.newClear(bufferSize);
		sendAckSerial = 0;

		receiveSemaphore = Semaphore.new(1);
		receiveSerial = 0;
		receiveBuffer = Array.newClear(bufferSize);

		connectionState = \neverConnected;
		onConnected = {};
		onMessageReceived = {};

		this.prBindConnectRequest;
		this.prBindConnectAccept;
		this.prBindConnectConfirm;
		this.prBindSend;
		this.prBindAck;
		this.prBindDisconnect;
		this.prBindDisconnectConfirm;
	}

	knock { | hostname, knockPort = 7666 |
		if (connectionState == \neverConnected or: {
			connectionState == \disconnected }, {
			var message = [
				'/wireKnock',
				receivePort,
				targetId ];
			var knockAddr = netAddrFactory.value(hostname, knockPort);
			this.prChangeConnectionState(\knocking);
			connectionRetry = SCLOrkWireSendRetry.new(
				knockAddr,
				message,
				{ connectionState == \knocking },
				{ this.prChangeConnectionState(\failureTimeout) },
				maxRetries,
				timeout);
		});
	}

	connect { | hostname, requestPort = 7666 |
		if (connectionState == \neverConnected or: {
				connectionState == \disconnected }, {
			var message = [
				'/wireConnectRequest',
				receivePort,
				targetId ];
			netAddr = netAddrFactory.value(hostname, requestPort);
			this.prChangeConnectionState(\connectionRequested);
			connectionRetry = SCLOrkWireSendRetry.new(
				netAddr,
				message,
				{ connectionState == \connectionRequested },
				{ this.prChangeConnectionState(\failureTimeout) },
				maxRetries,
				timeout);
		});
	}

	// note completely possible to clobber old data in the send array
	// right now...
	sendMsg { | ... args |
		if (connectionState == \connected, {
			var serial, message;

			sendSemaphore.wait;
			sendSerial = sendSerial + 1;
			serial = sendSerial;
			sendSemaphore.signal;

			message = [
				'/wireSend',
				selfId,
				serial] ++ args;
			sendBuffer.wrapPut(serial, SCLOrkWireSendRetry.new(
				netAddr,
				message,
				{ sendAckSerial < serial },
				{ this.prChangeConnectionState(\failureTimeout) },
				maxRetries,
				timeout));
		});
	}

	// Useful mostly for testing, to avoid having to hard-code waits.
	isIdle {
		^((connectionState == \connected or:
			{ connectionState == \disconnected }
		) and: {
			// Look for sent items waiting to be acknowledged.
			var sendIdle;
			sendSemaphore.wait;
			sendIdle = (sendSerial == sendAckSerial);
			sendSemaphore.signal;
			sendIdle;
		} and: {
			// Look for out-of-order received items.
			var receiveIdle = true;
			receiveSemaphore.wait;
			receiveBuffer.do({ | item, index |
				if (item.notNil, {
					receiveIdle = false;
				});
			});
			receiveSemaphore.signal;
			receiveIdle;
		});
	}

	disconnect {
		if (connectionState == \connected, {
			var message = [
				'/wireDisconnect',
				selfId
			];
			this.prChangeConnectionState(\disconnectRequested);
			connectionRetry = SCLOrkWireSendRetry.new(
				netAddr,
				message,
				{ connectionState == \disconnectRequested },
				{ this.prChangeConnectionState(\failureTimeout) },
				maxRetries,
				timeout);
		});
	}

	free {
		this.disconnect;

		connectRequestOSCFunc.free;
		connectAcceptOSCFunc.free;
		connectConfirmOSCFunc.free;
		sendOSCFunc.free;
		ackOSCFunc.free;
		disconnectOSCFunc.free;
		disconnectConfirmOSCFunc.free;
	}

	prBindConnectRequest {
		connectRequestOSCFunc = OSCFunc.new({ | msg, time, addr |
			var returnPort = msg[1];
			var responderId = msg[2];
			// We may receive duplicate connection requests if the network
			// dropped our response packet. But we don't restart the retry
			// count for a duplicate connection request, so we only start
			// the response retry process once.
			if (connectionState == \neverConnected or: {
				connectionState == \knocking } or: {
				connectionState == \disconnected }, {
				var message;
				this.prChangeConnectionState(\connectionAccepted);
				netAddr = netAddrFactory.value(addr.ip, returnPort);
				selfId = responderId;
				message = [
					'/wireConnectAccept',
					selfId,
					targetId ];
				connectionRetry = SCLOrkWireSendRetry.new(
					netAddr,
					message,
					{ connectionState == \connectionAccepted },
					{ this.prChangeConnectionState(\failureTimeout) },
					maxRetries,
					timeout);
			});
		},
		path: '/wireConnectRequest',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindConnectAccept {
		connectAcceptOSCFunc = OSCFunc.new({ | msg, time, addr |
			var responderId = msg[1];
			var initiatorId = msg[2];
			// We only process messages intended for us as identified by
			// the responderId we provided in the connection initiation.
			if (responderId == targetId, {
				connectionRetry.stop;
				// We take on the provided sender Id as our own.
				selfId = initiatorId;
				this.prChangeConnectionState(\connected);
				netAddr.sendMsg('/wireConnectConfirm', selfId);
			});
		},
		path: '/wireConnectAccept',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindConnectConfirm {
		connectConfirmOSCFunc = OSCFunc.new({ | msg, time, addr |
			var initiatorId = msg[1];
			if (initiatorId == targetId, {
				connectionRetry.stop;
				this.prChangeConnectionState(\connected);
			});
		},
		path: '/wireConnectConfirm',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindSend {
		sendOSCFunc = OSCFunc.new({ | msg, time, addr |
			var senderId = msg[1];
			if (senderId == targetId, {
				var serial = msg[2];

				receiveSemaphore.wait;
				// If serial is next packet to receive
				if (serial <= receiveSerial, {
					// If serial <= receiveSerial this is a duplicate packet
					// no need to notify client of reception, or to buffer.
				}, {
					var messageArray = msg[3..];

					if (serial == (receiveSerial + 1), {
						receiveSerial = serial;

						// In-order packet received, notify.
						receiveSemaphore.signal;
						this.onMessageReceived.value(messageArray);
						receiveSemaphore.wait;

						// See if there were further ahead-of-order buffered
						// messages we can notify on.
						while ({ receiveBuffer.wrapAt(receiveSerial + 1).notNil }, {
							receiveSerial = receiveSerial + 1;
							messageArray = receiveBuffer.wrapAt(receiveSerial);
							receiveBuffer.wrapPut(receiveSerial, nil);

							receiveSemaphore.signal;
							this.onMessageReceived.value(messageArray);
							receiveSemaphore.wait;
						});
					}, {
						// Serial ahead of our next serial, buffer message until
						// we are ready to notify.
						receiveBuffer.wrapPut(serial, messageArray);
					});
				});
				receiveSemaphore.signal;

				netAddr.sendMsg('/wireAck', selfId, serial);
			});
		},
		path: '/wireSend',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindAck {
		ackOSCFunc = OSCFunc.new({ | msg, time, addr |
			var senderId = msg[1];
			if (senderId == targetId, {
				var serial = msg[2];

				if (serial == (sendAckSerial + 1), {
					sendAckSerial = serial;
					if (sendBuffer.wrapAt(serial).notNil, {
						sendBuffer.wrapAt(serial).stop;
						sendBuffer.wrapPut(serial, nil);
					});
				});
			});
		},
		path: '/wireAck',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindDisconnect {
		disconnectOSCFunc = OSCFunc.new({ | msg, time, addr |
			var senderId = msg[1];
			if (senderId == targetId, {
				netAddr.sendMsg('/wireDisconnectConfirm', selfId);
				this.prChangeConnectionState(\disconnected);
			});
		},
		path: '/wireDisconnect',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindDisconnectConfirm {
		disconnectConfirmOSCFunc = OSCFunc.new({ | msg, time, addr |
			var senderId = msg[1];
			if (senderId == targetId, {
				connectionRetry.stop;
				this.prChangeConnectionState(\disconnected);
			});
		},
		path: '/wireDisconnectConfirm',
		recvPort: receivePort
		).permanent_(true);
	}

	prChangeConnectionState { | newState |
		if (connectionState != newState, {
			connectionState = newState;
			this.onConnected.(connectionState);
		});
	}
}

SCLOrkWireSendRetry {
	var netAddr;
	var message;
	var keepGoing;
	var onFail;
	var maxRetries;
	var timeout;

	var retries;
	var nextTime;
	var retryFunction;

	*new { | netAddr, message, keepGoing, onFail, maxRetries, timeout |
		^super.newCopyArgs(netAddr,
			message,
			keepGoing,
			onFail,
			maxRetries,
			timeout
		).init;
	}

	init {
		retries = 0;
		nextTime = Main.elapsedTime + timeout;
		CmdPeriod.add(this);

		netAddr.sendMsg(*message);

		retryFunction = {
			if (retries < maxRetries, {
				if (keepGoing.value, {
					netAddr.sendMsg(*message);
					retries = retries + 1;
					nextTime = Main.elapsedTime + timeout;
					timeout;
				}, {
					// keepGoing returned false, this is a
					// non-error finish state, simply stop
					// the job.
					nextTime = nil;
					nil;
				});
			}, {
				// Too many retries, call the failure function.
				onFail.value;
				nextTime = nil;
				nil;
			});
		};

		SystemClock.schedAbs(nextTime, retryFunction);
	}

	cmdPeriod {
		if (nextTime.notNil, {
			SystemClock.schedAbs(nextTime, retryFunction);
		});
	}

	isIdle {
		^(nextTime.isNil);
	}

	stop {
		nextTime = nil;
		CmdPeriod.remove(this);
	}

	free {
		this.stop;
	}
}