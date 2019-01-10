SCLOrkWire {
	var receivePort;
	var targetId;
	var timeout;
	var maxRetries;
	var bufferSize;

	var netAddrFactory;

	var selfId;
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

	var netAddr;

	var connectRequestOSCFunc;
	var connectAcceptOSCFunc;
	var connectConfirmOSCFunc;
	var sendOSCFunc;
	var ackOSCFunc;
	var disconnectOSCFunc;
	var disconnectConfirmOSCFunc;

	var <>onConnected;
	var <>onMessageReceived;

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
		sendSerial = 1;
		sendBuffer = Array.newClear(bufferSize);
		sendAckSerial = 0;

		receiveSemaphore = Semaphore.new(1);
		receiveSerial = 0;
		receiveBuffer = Array.newClear(bufferSize);

		connectionState = \neverConnected;

		this.prBindConnectRequest;
		this.prBindConnectAccept;
		this.prBindConnectConfirm;
		this.prBindSend;
		this.prBindAck;
		this.prBindDisconnect;
		this.prBindDisconnectConfirm;
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
			serial = sendSerial;
			sendSerial = sendSerial + 1;
			message = [
				'/wireSend',
				selfId,
				serial] ++ args;
			sendBuffer.wrapPut(serial, SCLOrkWireRetry.new(
				netAddr,
				message,
				{
					var ackSerial;
					sendSemaphore.wait;
					ackSerial = sendAckSerial;
					sendSemaphore.signal;
					serial < ackSerial;
				}, {
					this.prChangeConnectionState(\failureTimeout)
				},
				maxRetries,
				timeout));
			sendSemaphore.signal;
		});
	}

	// Useful mostly for testing, to avoid having to hard-code waits.
	isIdle {
		^((connectionState == \connected or:
			{ connectionState == \disconnected }) and: {
			var sendIdle;
			sendSemaphore.wait;
			sendIdle = (sendSerial == (sendAckSerial + 1));
			sendSemaphore.signal;
			sendIdle;
		} and: {
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
	}

	prBindConnectRequest {
		connectRequestOSCFunc = OSCFunc.new({ | msg, time, addr |
			var returnPort = msg[1];
			var responderId = msg[2];
			// We may receive duplicate connection requests if the network
			// dropped our response packet. But we don't restart the retry
			// count for a duplicate connection request, so we only start
			// the response retry process once.
			if (connectionState == \neverConnected, {
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
				if (serial <= (receiveSerial + 1), {
					netAddr.sendMsg('/wireAck', selfId, serial);
					// If this is a novel message we should fire the
					// message received function and increment the serial
					// number.
					if (serial == (receiveSerial + 1), {
						var nextSerial = serial + 1;
						var messageArray = msg[3..];

						receiveSerial = serial;
						this.onMessageRecieved.value(messageArray);
						// Mark this buffer entry as nil, to indicate that
						// we've passed these data on to the client.
						receiveBuffer.wrapPut(serial, nil);
						// There may be other entries we recieved out-of-order,
						// advance through serial buffer until we encounter a
						// nil entry.
						while ({ receiveBuffer.wrapAt(nextSerial).notNil }, {
							receiveSerial = nextSerial;
							netAddr.sendMsg('/wireAck',
								selfId,
								receiveSerial);
							this.onMessageReceived.value(
								receiveBuffer.wrapAt(receiveSerial));
							receiveBuffer.wrapPut(receiveSerial, nil);
							nextSerial = nextSerial + 1;
						});
					});
				}, {
					// We've recieved an out-of-order packet, buffer it
					// until such time we recieve the in-order packet.
					var messageArray = msg[3..];
					receiveBuffer.wrapPut(serial, messageArray);
				});
				receiveSemaphore.signal;
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

				sendSemaphore.wait;
				if (serial == (sendAckSerial + 1), {
					sendAckSerial = serial;
					if (sendBuffer.wrapAt(serial).notNil, {
						sendBuffer.wrapAt(serial).stop;
						sendBuffer.wrapPut(serial, nil);
					});
				});
				sendSemaphore.signal;
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

	stop {
		nextTime = nil;
		CmdPeriod.remove(this);
	}

	free {
		this.stop;
	}
}